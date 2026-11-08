#!/usr/bin/env bash
set -u
timeout 20s adb logcat -c
if [[ -d signed-apks ]]; then
  export BBD_APP_APK="$PWD/signed-apks/app-debug.apk"
  export BBD_TEST_APK="$PWD/signed-apks/app-debug-androidTest.apk"
  export BBD_BASELINE_APK="$PWD/signed-apks/baseline-app.apk"
else
  commit=$(git rev-parse HEAD)
  ./gradlew --no-daemon assembleDebug -PbbdVersionCode=8 -PbbdVersionName=0.6.0-update-baseline -PbbdSourceCommit="$commit" || exit $?
  mkdir -p baseline-apk
  cp app/build/outputs/apk/debug/app-debug.apk baseline-apk/app-debug.apk
  ./gradlew --no-daemon assembleDebug assembleDebugAndroidTest -PbbdSourceCommit="$commit" || exit $?
fi
python3 tools/check-update.py || exit $?
python3 tools/native-test-driver.py "${1:-false}" "${2:-false}"
result=$?
for theme in light dark; do
  timeout 30s adb exec-out run-as com.barebonesdownloader.android cat "cache/BBD-native-test-theme-${theme}-scaled.png" > "theme-${theme}-scaled.png" || result=1
  python3 - "theme-${theme}-scaled.png" <<'PYIMAGE' || result=1
import pathlib, sys
path=pathlib.Path(sys.argv[1])
if not path.is_file() or path.read_bytes()[:8] != bytes([137,80,78,71,13,10,26,10]):
    path.unlink(missing_ok=True)
    sys.exit('No valid exported PNG: '+str(path))
PYIMAGE
done
timeout 20s adb logcat -d -s BBD_INTEGRATION:I AndroidRuntime:E '*:S' > native-logcat.txt || true
exit "$result"
