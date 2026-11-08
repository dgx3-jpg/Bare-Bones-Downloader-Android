#!/usr/bin/env python3
"""CI uses an isolated test key unless a permanent signing secret is supplied."""
import base64, hashlib, os, pathlib, subprocess, sys
expected=pathlib.Path('tools/signing-certificate.sha256').read_text().strip()
key=pathlib.Path(os.environ['BBD_DEBUG_KEYSTORE_PATH'])
key.parent.mkdir(parents=True,exist_ok=True)
encoded=os.environ.get('BBD_DEBUG_KEYSTORE_BASE64','')
mode='verification'
if encoded:
    key.write_bytes(base64.b64decode(encoded,validate=True));key.chmod(0o600);mode='permanent'
elif not key.is_file():
    subprocess.run(['keytool','-genkeypair','-keystore',str(key),'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Verification Only','-keyalg','RSA','-keysize','2048','-validity','7'],check=True,capture_output=True)
result=subprocess.run(['keytool','-exportcert','-keystore',str(key),'-storepass','android','-alias','androiddebugkey'],capture_output=True,check=True)
fingerprint=hashlib.sha256(result.stdout).hexdigest()
if mode=='permanent' and fingerprint!=expected: sys.exit('Permanent signing certificate mismatch; refusing publication.')
print('Signing mode: '+mode+'; certificate SHA-256: '+fingerprint)
if os.environ.get('GITHUB_OUTPUT'):
    with open(os.environ['GITHUB_OUTPUT'],'a') as output: output.write('mode='+mode+'\n')
