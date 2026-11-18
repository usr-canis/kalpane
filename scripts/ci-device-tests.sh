#!/usr/bin/env bash
set -uo pipefail

# emulator-runner executes each YAML script line in a separate shell. Keep exit
# status and cleanup here, and retain the APK until screenshots have been pulled.
collect() {
  local destination="app/build/device-validation/$1"
  mkdir -p "$destination"
  cp -R app/build/reports/androidTests "$destination/reports" 2>/dev/null || true
  cp -R app/build/outputs/androidTest-results "$destination/results" 2>/dev/null || true
  adb pull /sdcard/Android/data/dev.kalpane/files/screenshots "$destination/screenshots" || true
}
trap 'adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true' EXIT
status=0
./gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --stacktrace || status=$?
collect standard
if [ "$status" -ne 0 ]; then exit "$status"; fi
adb shell settings put system font_scale 2.0
./gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.class=dev.kalpane.LayoutCaptureTest --stacktrace || status=$?
collect large-font
exit "$status"
