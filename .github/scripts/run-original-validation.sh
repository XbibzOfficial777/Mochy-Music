#!/usr/bin/env bash
# android-emulator-runner invokes each input line in a separate shell: keep the
# APK lookup, installation and checks in this single child process.
set -euo pipefail
apk="$(find smoke-apk -name '*.apk' -type f -print -quit)"
test -n "$apk"
build_tools="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
package="$("$build_tools/aapt" dump badging "$apk" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1)"
test -n "$package"
printf '%s' "$package" > smoke-package.txt
adb wait-for-device
adb install -r "$apk"
adb logcat -c
bash .github/scripts/smoke-latest.sh
