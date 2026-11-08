#!/usr/bin/env python3
"""Real same-certificate APK update on the connected owned emulator."""
import pathlib, subprocess, sys, os
baseline=pathlib.Path(os.environ.get('BBD_BASELINE_APK','baseline-apk/app-debug.apk'))
app_apk=os.environ.get('BBD_APP_APK','app/build/outputs/apk/debug/app-debug.apk')
test_apk=os.environ.get('BBD_TEST_APK','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
if not baseline.is_file(): sys.exit('Update baseline missing; refusing to skip update verification.')
def adb(*args):
    r=subprocess.run(['adb',*args],capture_output=True,text=True,timeout=180)
    print(r.stdout)
    if r.returncode: sys.exit(r.stderr)
    return r.stdout
adb('install','-r','-t',str(baseline))
adb('install','-r','-t',test_apk)
for name in ['UpdateSeedTest','UpdateVerifyTest']:
    if name=='UpdateVerifyTest': adb('install','-r','-t',app_apk)
    result=adb('shell','am','instrument','-w','-r','-e','class','com.barebonesdownloader.android.'+name,'com.barebonesdownloader.android.test/androidx.test.runner.AndroidJUnitRunner')
    if 'OK (1 test)' not in result: sys.exit('In-place update instrumentation failed: '+name)
print('UPDATE VERIFIED: prior APK preferences, waiting queue and completed history survived adb install -r.')
