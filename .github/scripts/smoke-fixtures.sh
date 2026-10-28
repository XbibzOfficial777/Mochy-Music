#!/usr/bin/env bash
# Debug-only, synthetic UI fixtures: no account, real song, or third-party API key required.
set -euo pipefail
package="$(cat smoke-package.txt)"
activity="$package/com.mochyy.mochymusic.ui.screens.recap.IssueSmokeActivity"
export APP_PACKAGE="$package"
trap 'adb logcat -d -v threadtime > smoke-fixtures-logcat.txt || true; adb exec-out screencap -p > smoke-fixtures-screen.png || true' EXIT

start_fixture() {
  adb shell am start -W -n "$activity" --es screen "$1" | tee "smoke-$1-start.txt"
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
  if [[ "$state" == *"mInputShown=true"* || "$state" == *"mIsInputViewShown=true"* || "$state" == *"isInputViewShown=true"* ]]; then
    adb shell input keyevent 4
    sleep 1
  fi
}

# The quality picker must expose 4K and preserve the selected choice.
start_fixture quality
read -r width height < <(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
adb shell input swipe "$((width/2))" "$((height*3/4))" "$((width/2))" "$((height*2/5))" 450
sleep 2
tap_node text '2160p (4K)'
dump_ui
find_node text 'Selected: 2160p (4K)' >/dev/null

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
# A session key must not be persisted to app files or shared preferences.
if adb shell "run-as '$package' sh -c 'grep -aR -l dummySecretForCI files shared_prefs 2>/dev/null || true'" | grep -q .; then
  echo 'The session key was unexpectedly persisted.' >&2
  exit 1
fi

# Export a long dark-paper receipt through Android's own document picker.
start_fixture receipt
tap_node text 'Light paper'
dump_ui
find_node text 'Dark paper' >/dev/null
tap_node text 'Save PNG'
dump_ui
cp smoke-ui.xml smoke-document-picker.xml
# DocumentsUI presents the Save action; the picker chooses a user-visible location.
coords="$(find_node text 'Save')"
adb shell input tap $coords
sleep 5
receipt="$(adb shell 'find /sdcard/Download /sdcard/Documents -name "Mochy-receipt-*.png" 2>/dev/null || true' | tr -d '\r' | tail -1)"
if [ -z "$receipt" ]; then
  echo 'Receipt PNG was not found after the document picker returned.' >&2
  exit 1
fi
adb pull "$receipt" smoke-receipt.png >/dev/null
python3 - <<'PY'
import struct
from pathlib import Path
p = Path('smoke-receipt.png').read_bytes()
if p[:8] != b'\x89PNG\r\n\x1a\n' or p[12:16] != b'IHDR':
    raise SystemExit('Receipt is not a valid PNG')
w, h = struct.unpack('>II', p[16:24])
print(f'Exported long receipt: {w} x {h} pixels, {len(p)} bytes')
if w < 200 or h < 1400 or h <= w or len(p) < 10000:
    raise SystemExit('Receipt export is unexpectedly small or incomplete')
from PIL import Image
image = Image.open('smoke-receipt.png').convert('RGBA')
image.thumbnail((360, 1200))
rgba = list(image.getdata())
opaque = [px for px in rgba if px[3] > 200]
if not opaque or sum(max(px[:3]) < 90 for px in opaque) < len(opaque) * 0.5:
    raise SystemExit('The selected dark paper did not appear in the exported image')
bottom = list(image.crop((0, image.height * 3 // 4, image.width, image.height)).getdata())
if sum(px[3] > 150 and min(px[:3]) > 140 for px in bottom) < 25:
    raise SystemExit('The bottom of the full receipt is missing its light text')
print('Dark style and content near the end of the receipt are present.')
PY

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
print('Quality, AI configuration and receipt export fixtures passed without an app fatal exception.')
PY
