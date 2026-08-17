#!/usr/bin/env bash
set -euo pipefail

task_java_home="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
JAVA_HOME="$task_java_home" ./gradlew --no-daemon \
  :app:lintDebug \
  :app:testDebugUnitTest \
  :app:assembleDebug

if [[ "${RUN_INSTRUMENTATION:-0}" == "1" ]]; then
  : "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the authorized device serial}"
  CONFIRM_DEVICE_TEST="${CONFIRM_DEVICE_TEST:-0}" scripts/run-device-tests.sh "$ANDROID_SERIAL"
fi
