#!/usr/bin/env bash
set -euo pipefail

task_serial="${1:?Usage: scripts/live-poc-run.sh <adb-serial>}"
task_run_id="$(date +%Y%m%d-%H%M%S)"
task_dir="artifacts/$task_run_id"
mkdir -p "$task_dir"

adb -s "$task_serial" logcat -c
adb -s "$task_serial" shell am start -n com.example.douyinautomation/.MainActivity
adb -s "$task_serial" exec-out screencap -p > "$task_dir/app-launch.png"

cat <<EOF
POC app launched. Manually enable “Douyin automation diagnostics” in Android Accessibility settings,
open the POC, enter a test keyword, and press Start test. This script does not enable accessibility
or send any messages. When the run is finished, use the command below to save app logs:

  adb -s $task_serial logcat -d -v threadtime DyinPoc:* *:S > $task_dir/logcat.txt
EOF

