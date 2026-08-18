#!/usr/bin/env bash
set -euo pipefail

task_serial="${1:?Usage: scripts/live-poc-run.sh <adb-serial>}"
task_run_id="$(date +%Y%m%d-%H%M%S)"
task_dir="artifacts/$task_run_id"
mkdir -p "$task_dir"

adb -s "$task_serial" logcat -c
adb -s "$task_serial" shell am start -n com.example.douyinautomation/.MainActivity
adb -s "$task_serial" exec-out screencap -p > "$task_dir/app-launch.png"

if ! ALLOW_ADB_ACCESSIBILITY="${ALLOW_ADB_ACCESSIBILITY:-0}" scripts/ensure-accessibility.sh "$task_serial"; then
  echo "Accessibility is not yet confirmed; enable it in Settings, then return to the app."
fi

cat <<EOF
POC app launched. Enter a test keyword and press Start test after accessibility is confirmed.
By default this script only opens Settings; to attempt the development-only ADB shortcut, use
ALLOW_ADB_ACCESSIBILITY=1. This script never uses root and never sends any messages. When the run
is finished, use the command below to save app logs:

  adb -s $task_serial logcat -d -v threadtime DyinPoc:* *:S > $task_dir/logcat.txt
EOF
