#!/usr/bin/env bash
set -euo pipefail

task_serial="${1:?Usage: CONFIRM_DEVICE_TEST=1 scripts/run-device-tests.sh <adb-serial>}"
if [[ "${CONFIRM_DEVICE_TEST:-0}" != "1" ]]; then
  echo "Refusing device tests without CONFIRM_DEVICE_TEST=1."
  echo "This script installs APKs with adb -r and runs am instrument; it does not run Gradle connected tests or uninstall packages."
  exit 2
fi

task_java_home="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
JAVA_HOME="$task_java_home" ./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest

task_app_apk="app/build/outputs/apk/debug/app-debug.apk"
task_test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
adb -s "$task_serial" install -r "$task_app_apk"
adb -s "$task_serial" install -r "$task_test_apk"

mkdir -p outputs
adb -s "$task_serial" shell am instrument -w -r \
  -e class com.example.douyinautomation.AutomationAppIntegrationTest \
  com.example.douyinautomation.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "outputs/m0-device-instrumentation.txt"

