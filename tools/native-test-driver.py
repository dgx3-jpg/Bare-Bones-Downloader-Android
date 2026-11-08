#!/usr/bin/env python3
"""Run real Android instrumentation and preserve its results without Gradle UTP collection."""
import os, pathlib, queue, subprocess, sys, threading, time, xml.etree.ElementTree as ET

class Results:
    def __init__(self):
        self.fields={}; self.active=None; self.cases={}; self.started={}; self.expected=0; self.finished=False; self.problem=None
    def line(self,line):
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key,sep,value=line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            if sep: self.fields[key]=value; self.active=key
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code=int(line.rsplit(':',1)[1]); f=self.fields; self.fields={}; self.active=None
            if f.get('numtests','').isdigit(): self.expected=int(f['numtests'])
            key=(f.get('class',''),f.get('test',''))
            if not key[1]: return
            if code==1: self.started[key]=time.monotonic(); return
            self.cases[key]=(code,f.get('stack') or f.get('stream',''),max(0,time.monotonic()-self.started.get(key,time.monotonic())))
        elif line.startswith('INSTRUMENTATION_CODE: '):
            self.finished=True
            if int(line.rsplit(':',1)[1]) != -1: self.problem=line
            self.active=None
        elif line.startswith(('INSTRUMENTATION_RESULT: ','INSTRUMENTATION_FAILED: ')):
            if line.startswith('INSTRUMENTATION_FAILED: '): self.problem=line
            self.active=None
        elif self.active: self.fields[self.active]+='\n'+line
    def write(self,path):
        suite=ET.Element('testsuite',name='NativeIntegrationTest')
        for (cls,name),(code,detail,elapsed) in self.cases.items():
            case=ET.SubElement(suite,'testcase',classname=cls,name=name,time=f'{elapsed:.3f}')
            if code in (-3,-4): ET.SubElement(case,'skipped',message='Explicitly skipped / assumption not met').text=detail
            elif code!=0: ET.SubElement(case,'failure',message=detail.splitlines()[0] if detail else str(code)).text=detail
        if not self.finished or self.expected<=0 or self.expected!=len(self.cases) or self.problem:
            case=ET.SubElement(suite,'testcase',classname='NativeDriver',name='complete_instrumentation_report')
            ET.SubElement(case,'failure',message=self.problem or 'Incomplete instrumentation output').text=f'Expected {self.expected}, received {len(self.cases)}; finished={self.finished}'
        suite.set('tests',str(len(suite))); suite.set('failures',str(len(suite.findall('testcase/failure')))); suite.set('errors','0'); suite.set('skipped',str(len(suite.findall('testcase/skipped'))))
        path.parent.mkdir(parents=True,exist_ok=True); ET.ElementTree(suite).write(path,encoding='utf-8',xml_declaration=True)
        print('NATIVE RESULT:',suite.attrib,flush=True)
        return int(suite.get('failures'))==0

def main():
    output=pathlib.Path('app/build/outputs/native-test-results/TEST-native.xml')
    results=Results()
    if len(sys.argv)>2 and sys.argv[1]=='--parse-log':
        for line in pathlib.Path(sys.argv[2]).read_text().splitlines(): results.line(line)
        return 0 if results.write(output) else 1
    try:
        for apk in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
            subprocess.run(['adb','install','-r','-t',apk],check=True,timeout=180)
        command=['adb','shell','am','instrument','-w','-r','-e','class','com.barebonesdownloader.android.NativeIntegrationTest',
                 '-e','timeout_msec','180000','-e','live',sys.argv[1] if len(sys.argv)>1 else 'false',
                 '-e','tiktok',sys.argv[2] if len(sys.argv)>2 else 'false',
                 'com.barebonesdownloader.android.test/androidx.test.runner.AndroidJUnitRunner']
        process=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,errors='replace')
        lines=queue.Queue()
        def read():
            try:
                for line in process.stdout: lines.put(line)
            finally: lines.put(None)
        threading.Thread(target=read,daemon=True).start()
        deadline=time.monotonic()+720
        with open('native-runner.log','w') as log:
            while time.monotonic()<deadline:
                try: line=lines.get(timeout=1)
                except queue.Empty: continue
                if line is None: break
                log.write(line);log.flush();print(line,end='',flush=True);results.line(line.rstrip('\r\n'))
                if results.finished: break
            else: results.problem='Instrumentation exceeded its 12-minute deadline'
        # The runner already sent its final report. Bound transport cleanup rather than
        # waiting for UTP's post-test ActivityManager/file-collection calls indefinitely.
        try: process.wait(timeout=5)
        except subprocess.TimeoutExpired: process.terminate()
    except Exception as error: results.problem=f'{type(error).__name__}: {error}'
    return 0 if results.write(output) else 1

if __name__=='__main__': sys.exit(main())
