#!/usr/bin/env python3
"""Verify source binding, hashes and the permanent certificate of release APKs."""
import hashlib,json,os,pathlib,re,subprocess,sys,zipfile
root=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else 'signed-apks')
manifest=json.loads((root/'release-manifest.json').read_text())
expected=pathlib.Path('tools/signing-certificate.sha256').read_text().strip()
head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
if manifest['source_commit']!=head: sys.exit('Release source commit differs from checked-out source.')
sdk=pathlib.Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','/workspace/work/android-sdk')))
tools=sdk/'build-tools'/'35.0.0'
archive=root/'update-verification-apks.zip'
if archive.exists():
    with zipfile.ZipFile(archive) as z:
        if sorted(z.namelist())!=['app-debug-androidTest.apk','baseline-app.apk']: sys.exit('Unexpected verification archive entries.')
        z.extractall(root)
for name in ['app-debug.apk','baseline-app.apk','app-debug-androidTest.apk']:
    file=root/name
    if hashlib.sha256(file.read_bytes()).hexdigest()!=manifest['sha256'][name]: sys.exit('Release file hash mismatch: '+name)
    certs=subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(file)],text=True)
    found=re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)',certs)
    if found!=[expected]: sys.exit('Release signer mismatch: '+name)
    badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(file)],text=True)
    package=re.search(r"package: name='([^']+)' versionCode='([0-9]*)'",badging)
    app_id='com.barebonesdownloader.android'
    if not package or package.group(1)!=(app_id+'.test' if name=='app-debug-androidTest.apk' else app_id): sys.exit('Unexpected APK package: '+name)
    if name!='app-debug-androidTest.apk':
        code=manifest['baseline_version_code'] if name=='baseline-app.apk' else manifest['version_code']
        if int(package.group(2))!=code: sys.exit('Unexpected versionCode: '+name)
        tree=subprocess.check_output([str(tools/'aapt'),'dump','xmltree',str(file),'AndroidManifest.xml'],text=True)
        match=re.search(r'android:name[^\n]*"com\.barebonesdownloader\.android\.BUILD_COMMIT"[^\n]*\n\s*A: android:value[^\n]*"([0-9a-f]{40})"',tree)
        if not match or match.group(1)!=head: sys.exit('Signed APK build/source binding mismatch: '+name)
print('Verified release APK hashes, source commit and permanent certificate: '+expected)
