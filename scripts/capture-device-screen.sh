#!/usr/bin/env bash
set -euo pipefail

task_serial="${1:?Usage: scripts/capture-device-screen.sh <adb-serial> [destination]}"
task_destination="${2:-artifacts/adb-screen-$(date +%Y%m%d-%H%M%S).png}"

mkdir -p "$(dirname "$task_destination")"
adb -s "$task_serial" exec-out screencap -p > "$task_destination"
echo "$task_destination"

