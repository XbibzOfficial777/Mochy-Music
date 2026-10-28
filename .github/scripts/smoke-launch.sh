#!/usr/bin/env bash
# Runs on a clean x86_64 emulator. This is only a smoke test, not proof of crash-free playback.
set -euo pipefail
mapfile -t apks < <(find smoke-apk -name '*.apk' -type f)
if [ "${#apks[@]}" -ne 1 ]; then
  echo "Expected one universal test APK, found ${#apks[@]}" >&2
  exit 1
fi
build_tools="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
package="$("$build_tools/aapt" dump badging "${apks[0]}" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1)"
test -n "$package"
export APP_PACKAGE="$package"
adb wait-for-device
adb install -r "${apks[0]}"
adb logcat -c
adb shell am force-stop "$package"
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$package" | tee smoke-start.txt
grep -q 'Status: ok' smoke-start.txt
# Give startup, dependency initialization and the first composition time to fail visibly.
sleep 30
adb logcat -d -v threadtime > smoke-logcat.txt
adb exec-out screencap -p > smoke-launch.png
if ! adb shell pidof "$package" | grep -q '[0-9]'; then
  echo 'The app process is not running after launch.' >&2
  exit 1
fi
python3 - <<'PY'
from pathlib import Path
import os
package = os.environ['APP_PACKAGE']
lines = Path('smoke-logcat.txt').read_text(errors='replace').splitlines()
for i, line in enumerate(lines):
    if 'FATAL EXCEPTION' in line and any(f'Process: {package}' in x for x in lines[i:i+15]):
        print('\n'.join(lines[i:i+20]))
        raise SystemExit('App crash found in emulator logcat')
print(f'{package} remained running; no app FATAL EXCEPTION detected during launch.')
PY
