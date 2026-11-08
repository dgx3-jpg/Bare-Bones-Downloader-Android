#!/usr/bin/env bash
set -u
adb logcat -c
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.barebonesdownloader.android.NativeIntegrationTest -Pandroid.testInstrumentationRunnerArguments.live="${1:-false}"
result=$?
adb logcat -d -s BBD_INTEGRATION:I AndroidRuntime:E '*:S' > native-logcat.txt
exit "$result"
