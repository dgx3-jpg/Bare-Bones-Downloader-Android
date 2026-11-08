#!/usr/bin/env bash
set -u
adb logcat -c
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest || exit $?
python3 tools/check-update.py || exit $?
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.barebonesdownloader.android.NativeIntegrationTest -Pandroid.testInstrumentationRunnerArguments.live="${1:-false}" -Pandroid.testInstrumentationRunnerArguments.tiktok="${2:-false}"
result=$?
for theme in light dark; do
  adb pull "/sdcard/Download/BBD-native-test-theme-${theme}-scaled.png" "theme-${theme}-scaled.png" || true
  adb shell rm -f "/sdcard/Download/BBD-native-test-theme-${theme}-scaled.png"
done
adb logcat -d -s BBD_INTEGRATION:I AndroidRuntime:E '*:S' > native-logcat.txt
exit "$result"
