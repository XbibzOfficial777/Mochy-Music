#!/usr/bin/env bash
# Runs on a clean x86_64 emulator. This is only a smoke test, not proof of crash-free playback.
set -euo pipefail
mapfile -t apks < <(find smoke-apk -name '*.apk' -type f)
if [ "${#apks[@]}" -ne 1 ]; then
  echo "Expected one universal test APK, found ${#apks[@]}" >&2
  exit 1
fi
adb wait-for-device
adb install -r "${apks[0]}"
adb logcat -c
adb shell am force-stop com.mochyy.mochymusic
adb shell am start -W -n com.mochyy.mochymusic/.MainActivity | tee smoke-start.txt
# Give startup, dependency initialization and the first composition time to fail visibly.
sleep 30
adb logcat -d -v threadtime > smoke-logcat.txt
adb exec-out screencap -p > smoke-launch.png
if ! adb shell pidof com.mochyy.mochymusic | grep -q '[0-9]'; then
  echo 'The app process is not running after launch.' >&2
  exit 1
fi
python3 - <<'PY'
from pathlib import Path
text = Path('smoke-logcat.txt').read_text(errors='replace')
lines = text.splitlines()
for i, line in enumerate(lines):
    if 'FATAL EXCEPTION' in line and any('Process: com.mochyy.mochymusic' in x for x in lines[i:i+15]):
        print('\n'.join(lines[i:i+20]))
        raise SystemExit('App crash found in emulator logcat')
print('App remained running; no app FATAL EXCEPTION detected during launch.')
PY
