#!/usr/bin/env python3
"""Build signed release assets locally; private keys never enter GitHub caches."""
import hashlib,json,os,pathlib,shutil,subprocess,sys,zipfile
if subprocess.check_output(['git','status','--porcelain'],text=True).strip(): sys.exit('Commit source changes before building a source-bound release.')
key=pathlib.Path(os.environ.get('BBD_DEBUG_KEYSTORE_PATH',''))
if not key.is_file(): sys.exit('Set BBD_DEBUG_KEYSTORE_PATH to the private permanent keystore.')
head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
root=pathlib.Path('release-build');root.mkdir(exist_ok=True)
def gradle(*args): subprocess.run(['./gradlew','--no-daemon',*args,'-PbbdSourceCommit='+head],check=True)
gradle('assembleDebug','-PbbdVersionCode=8','-PbbdVersionName=0.6.0-update-baseline')
shutil.copyfile('app/build/outputs/apk/debug/app-debug.apk',root/'baseline-app.apk')
gradle('testDebugUnitTest','assembleDebug','assembleDebugAndroidTest')
gradle('lintDebug')
shutil.copyfile('app/build/outputs/apk/debug/app-debug.apk',root/'app-debug.apk')
shutil.copyfile('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',root/'app-debug-androidTest.apk')
files=['app-debug.apk','baseline-app.apk','app-debug-androidTest.apk']
manifest={'source_commit':head,'version_name':'0.6.1','version_code':9,'baseline_version_code':8,
          'certificate_sha256':pathlib.Path('tools/signing-certificate.sha256').read_text().strip(),
          'sha256':{name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in files}}
(root/'release-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
with zipfile.ZipFile(root/'update-verification-apks.zip','w',compression=zipfile.ZIP_DEFLATED) as z:
    for name in files[1:]: z.write(root/name,name)
subprocess.run(['python3','tools/verify-release.py',str(root)],check=True)
print('Release assets ready under release-build; no private key included.')
