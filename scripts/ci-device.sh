#!/usr/bin/env bash
# Runs the device walkthrough on the running emulator and collects its screenshots.
set -uo pipefail
status=0
./gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true || status=$?
mkdir -p "app/build/screenshots/$FORM"
adb pull /sdcard/Android/data/dev.kalpane/files/screenshots/. "app/build/screenshots/$FORM/" || true
ls -la "app/build/screenshots/$FORM" || true
exit $status
