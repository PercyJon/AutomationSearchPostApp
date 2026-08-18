#!/usr/bin/env bash
set -euo pipefail

# Development-only helper. This script is never packaged into the Android app and never uses
# root/su. Android's production contract still requires the user to enable AccessibilityService.
task_serial="${1:?Usage: scripts/ensure-accessibility.sh <adb-serial> [service-component]}"
task_component="${2:-com.example.douyinautomation/com.example.douyinautomation.automation.DouyinAccessibilityService}"

task_read_enabled() {
  adb -s "$task_serial" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r' | tail -n 1
}

task_enabled="$(task_read_enabled)"
if [[ "$task_enabled" == *"$task_component"* ]]; then
  echo "Accessibility service already enabled: $task_component"
  exit 0
fi

if [[ "${ALLOW_ADB_ACCESSIBILITY:-0}" == "1" ]]; then
  # Preserve any other enabled services instead of overwriting the user's setting.
  task_services="$task_enabled"
  if [[ -z "$task_services" || "$task_services" == "null" ]]; then
    task_services="$task_component"
  else
    task_services="$task_services:$task_component"
  fi
  echo "Attempting development-only ADB accessibility enablement"
  adb -s "$task_serial" shell settings put secure enabled_accessibility_services "$task_services"
  adb -s "$task_serial" shell settings put secure accessibility_enabled 1
  sleep 1
  task_enabled="$(task_read_enabled)"
  if [[ "$task_enabled" == *"$task_component"* ]]; then
    echo "ADB accessibility enablement accepted: $task_component"
    exit 0
  fi
  echo "ADB enablement was not accepted by this device; falling back to Settings." >&2
fi

echo "Opening Android Accessibility settings; confirm the service manually if needed."
adb -s "$task_serial" shell am start -a android.settings.ACCESSIBILITY_SETTINGS >/dev/null
exit 3
