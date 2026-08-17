#!/usr/bin/env bash
set -euo pipefail

task_serial="${1:-}"
if [[ -z "$task_serial" ]]; then
  task_serial="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
fi

if [[ -z "$task_serial" ]]; then
  echo "No authorized Android device found. Connect a device and accept its ADB prompt."
  exit 1
fi

echo "ADB device: $task_serial"
adb -s "$task_serial" shell getprop ro.product.model
adb -s "$task_serial" shell getprop ro.build.version.release
adb -s "$task_serial" shell getprop ro.build.version.sdk
adb -s "$task_serial" shell wm size
adb -s "$task_serial" shell pm path com.ss.android.ugc.aweme || true

