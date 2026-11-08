#!/usr/bin/env python3
"""Real same-certificate APK update on the connected owned emulator."""
import pathlib, subprocess, sys, os
if os.environ.get("BBD_VERIFY_ONLY") == "true":
    print("UPDATE CHECK SKIPPED: verification-only key; established signing key unavailable.")
    sys.exit(0)
baseline=pathlib.Path('baseline-apk/app-debug.apk')
if not baseline.is_file():
    print('UPDATE CHECK SKIPPED: baseline artifact unavailable; see release checklist.')
    sys.exit(0)
def adb(*args):
    r=subprocess.run(['adb',*args],capture_output=True,text=True)
    print(r.stdout)
    if r.returncode: sys.exit(r.stderr)
    return r.stdout
adb('install','-r','-t',str(baseline))
adb('install','-r','-t','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
for name in ['UpdateSeedTest','UpdateVerifyTest']:
    if name=='UpdateVerifyTest': adb('install','-r','-t','app/build/outputs/apk/debug/app-debug.apk')
    result=adb('shell','am','instrument','-w','-r','-e','class','com.barebonesdownloader.android.'+name,'com.barebonesdownloader.android.test/androidx.test.runner.AndroidJUnitRunner')
    if 'OK (1 test)' not in result: sys.exit('In-place update instrumentation failed: '+name)
print('UPDATE VERIFIED: prior APK preferences, waiting queue and completed history survived adb install -r.')
