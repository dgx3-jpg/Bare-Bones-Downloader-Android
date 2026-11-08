#!/usr/bin/env python3
"""Restore an optional Actions secret, then enforce the established public certificate."""
import base64, hashlib, os, pathlib, subprocess, sys
expected = 'fe2db971ead2186305b3fcac03c4b72aee0e535e310a5cebed82d2be578f646b'
key = pathlib.Path.home() / '.android' / 'debug.keystore'
encoded = os.environ.get('BBD_DEBUG_KEYSTORE_BASE64', '')
if encoded:
    key.parent.mkdir(parents=True, exist_ok=True)
    data = base64.b64decode(encoded, validate=True)
    key.write_bytes(data)
    key.chmod(0o600)
if not key.is_file():
    sys.exit('Established debug key is unavailable. Restore BBD_DEBUG_KEYSTORE_BASE64 from its private backup; refusing to generate an incompatible update key.')
result = subprocess.run(['keytool','-exportcert','-keystore',str(key),'-storepass','android','-alias','androiddebugkey'], capture_output=True)
if result.returncode or hashlib.sha256(result.stdout).hexdigest() != expected:
    sys.exit('Debug signing certificate differs from previous APKs. Restore the existing key; no APK will be published.')
print('Verified established debug signing certificate SHA-256: ' + expected)
