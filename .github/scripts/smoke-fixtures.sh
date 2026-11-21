#!/usr/bin/env bash
# Debug-only, synthetic UI fixtures: no account, real song, or third-party API key required.
set -euo pipefail
# shellcheck source=smoke-device.sh
source "$(dirname "$0")/smoke-device.sh"
wait_for_emulator 45
package="$(cat smoke-package.txt)"
activity="$package/com.mochyy.mochymusic.ui.screens.recap.IssueSmokeActivity"
export APP_PACKAGE="$package"
cleanup() {
  adb logcat -d -v threadtime > smoke-fixtures-logcat.txt || true
  adb exec-out screencap -p > smoke-fixtures-screen.png || true
  # Never leak a small-screen override into the other smoke fixtures, even on failure.
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
}
trap cleanup EXIT

start_fixture() {
  wait_for_emulator 20
  # Force a fresh activity: Android can otherwise deliver a new intent to the
  # top-most fixture instance, whose onCreate would keep showing the old screen.
  adb shell am start -S -W -n "$activity" --es screen "$1" | tee "smoke-$1-start.txt"
  grep -q 'Status: ok' "smoke-$1-start.txt"
  sleep 4
}
dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb exec-out cat /sdcard/window.xml > smoke-ui.xml
}
find_node() {
  python3 - "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
field, wanted = sys.argv[1], sys.argv[2].casefold()
for node in ET.parse('smoke-ui.xml').iter('node'):
    if node.get(field, '').casefold() == wanted:
        bounds = [int(x) for x in re.findall(r'\d+', node.get('bounds', ''))]
        if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
            print((bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)
            break
else:
    raise SystemExit(f'UI node {field}={sys.argv[2]!r} not found in smoke-ui.xml')
PY
}
tap_node() {
  local coords
  dump_ui
  coords="$(find_node "$1" "$2")"
  # Coordinates are two integers generated from the XML bounds, never shell input from the user.
  adb shell input tap $coords
  sleep 2
}
hide_keyboard_if_visible() {
  # uiautomator dump covers the app window, not the separate IME window. Ask the input-method
  # service instead, otherwise taps on obscured fields can accidentally keep typing in the first.
  local state
  state="$(adb shell dumpsys input_method 2>/dev/null || true)"
  # Android 15 may keep a stale mIsInputViewShown=true after hiding the IME;
  # prefer the input-method manager's mInputShown flag when it is present.
  if grep -Eq '(^|[[:space:]])mInputShown=(true|false)([[:space:]]|$)' <<< "$state"; then
    if grep -Eq '(^|[[:space:]])mInputShown=true([[:space:]]|$)' <<< "$state"; then
      adb shell input keyevent 4
      sleep 1
    fi
  elif grep -Eq 'mIsInputViewShown=true|isInputViewShown=true' <<< "$state"; then
    adb shell input keyevent 4
    sleep 1
  fi
}

# Equalizer used to force-close when lab cards sampled glassRoot while it was recording.
start_fixture equalizer
dump_ui
find_node text 'Equalizer smoke' >/dev/null
find_node text 'Graph' >/dev/null
adb logcat -d -v threadtime > smoke-equalizer-logcat.txt
python3 - <<'PY'
from pathlib import Path
import os
package = os.environ['APP_PACKAGE']
lines = Path('smoke-equalizer-logcat.txt').read_text(errors='replace').splitlines()
for i, line in enumerate(lines):
    if 'FATAL EXCEPTION' in line and any(f'Process: {package}' in x for x in lines[i:i+15]):
        print('\n'.join(lines[i:i+20]))
        raise SystemExit('App crash while opening the Equalizer smoke fixture')
print('Equalizer smoke stayed up with Graph/Bands/AutoEQ visible.')
PY

# The quality picker must expose 4K and preserve the selected choice.
start_fixture quality
tap_node text '2160p (4K)'
dump_ui
find_node text 'Selected: 2160p (4K)' >/dev/null

# The full list must also be scrollable on a compact 320 x 480 dp screen. The first and last
# quality choices both need to remain reachable, including with the new 4K entries.
adb shell am force-stop "$package"
adb shell wm size 640x960
adb shell wm density 320
start_fixture quality
dump_ui
cp smoke-ui.xml smoke-quality-compact-before.xml
read -r sx sy ey < <(python3 - <<'PY_SCROLL'
import re, xml.etree.ElementTree as ET
for node in ET.parse('smoke-ui.xml').iter('node'):
    if node.get('scrollable') == 'true':
        b = [int(x) for x in re.findall(r'\d+', node.get('bounds', ''))]
        if len(b) == 4 and b[3] - b[1] > 80:
            # Stay well clear of the Android gesture-navigation area at the bottom.
            height = b[3] - b[1]
            print((b[0] + b[2]) // 2, b[1] + 3 * height // 4, b[1] + height // 4)
            break
else:
    raise SystemExit('Video quality dialog does not expose a scrollable list')
PY_SCROLL
)
echo "Compact quality swipe: x=$sx, y=$sy to y=$ey"
adb shell input swipe "$sx" "$sy" "$sx" "$ey" 400
sleep 1
dump_ui
cp smoke-ui.xml smoke-quality-compact-after-first.xml
adb shell input swipe "$sx" "$sy" "$sx" "$ey" 400
sleep 1
tap_node text '144p'
dump_ui
find_node text 'Selected: 144p' >/dev/null
adb shell wm size reset
adb shell wm density reset
sleep 2

# The disabled upstream AI card now has an editable, session-only configuration. No network call.
start_fixture ai
tap_node content-desc 'OpenRouter API key'
adb shell input text 'dummySecretForCI'
hide_keyboard_if_visible
tap_node content-desc 'Model ID (for example openai/gpt-4o-mini)'
adb shell input text 'example/model'
hide_keyboard_if_visible
tap_node text 'Continue to language and translate'
dump_ui
find_node text 'AI configured: example/model' >/dev/null
# A session key must not be persisted. First prove the scanner can actually read private app files.
scan_key() {
  adb shell "run-as '$package' sh -c 'grep -aR -l dummySecretForCI files shared_prefs 2>/dev/null || true'"
}
adb shell "run-as '$package' sh -c 'mkdir -p files; printf dummySecretForCI > files/ci-key-scanner-probe'"
if ! scan_key | grep -q 'ci-key-scanner-probe'; then
  echo 'The private-file key scanner is not working.' >&2
  exit 1
fi
adb shell run-as "$package" rm files/ci-key-scanner-probe
if scan_key | grep -q .; then
  echo 'The session key was unexpectedly persisted.' >&2
  exit 1
fi

# The recap receipt is a long, scrollable story page; confirm its header and artist list render.
start_fixture receipt
dump_ui
find_node text "Let's see your bill" >/dev/null
find_node text 'MOCHY' >/dev/null
find_node text 'Top Artist' >/dev/null
find_node text '1x Fixture artist 1' >/dev/null
find_node text '10x Fixture artist 10' >/dev/null
if ! grep -q 'scrollable="true"' smoke-ui.xml; then
  echo 'The recap receipt did not expose a scrollable content area.' >&2
  exit 1
fi

adb logcat -d -v threadtime > smoke-fixtures-logcat.txt
python3 - <<'PY'
from pathlib import Path
import os
lines = Path('smoke-fixtures-logcat.txt').read_text(errors='replace').splitlines()
package = os.environ['APP_PACKAGE']
for i, line in enumerate(lines):
    if 'FATAL EXCEPTION' in line and any(f'Process: {package}' in x for x in lines[i:i+15]):
        print('\n'.join(lines[i:i+20]))
        raise SystemExit('App crash during UI smoke fixtures')
print('Equalizer, quality, AI configuration, and recap receipt UI fixtures passed without an app fatal exception.')
PY
