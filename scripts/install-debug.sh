#!/usr/bin/env bash
set -euo pipefail

task_serial="${1:?Usage: scripts/install-debug.sh <adb-serial>}"
task_apk="app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$task_apk" ]]; then
  echo "Debug APK is missing; run scripts/build-test.sh first."
  exit 1
fi

adb -s "$task_serial" install -r "$task_apk"
adb -s "$task_serial" shell am start -n com.example.douyinautomation/.MainActivity

