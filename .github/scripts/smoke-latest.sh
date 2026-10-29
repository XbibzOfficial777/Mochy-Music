#!/usr/bin/env bash
# Deterministic debug-only UI regressions. No accounts or third-party services are used.
set -euo pipefail
package="$(cat smoke-package.txt)"
activity="$package/com.mochyy.mochymusic.ui.screens.recap.LatestIssuesSmokeActivity"
export APP_PACKAGE="$package"
cleanup() {
  adb logcat -d -v threadtime > smoke-latest-logcat.txt || true
  adb exec-out screencap -p > smoke-latest-screen.png || true
  adb shell settings put global animator_duration_scale 0 || true
}
trap cleanup EXIT

start_fixture() {
  adb shell am start -S -W -n "$activity" --es screen "$1" | tee "smoke-latest-$1-start.txt"
  grep -q 'Status: ok' "smoke-latest-$1-start.txt"
  sleep 4
}
dump_ui() {
  adb shell uiautomator dump /sdcard/latest-window.xml >/dev/null
  adb exec-out cat /sdcard/latest-window.xml > smoke-latest-ui.xml
}
find_node() {
  python3 - "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
field, wanted = sys.argv[1], sys.argv[2].casefold()
for node in ET.parse('smoke-latest-ui.xml').iter('node'):
    if node.get(field, '').casefold() == wanted:
        bounds = [int(x) for x in re.findall(r'\d+', node.get('bounds', ''))]
        if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
            print((bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)
            break
else:
    raise SystemExit(f'UI node {field}={sys.argv[2]!r} not found in smoke-latest-ui.xml')
PY
}
assert_text() { dump_ui; find_node text "$1" >/dev/null; }
tap_node() {
  dump_ui
  local coords
  coords="$(find_node "$1" "$2")"
  adb shell input tap $coords
  sleep 2
}
ime_visible() {
  local state
  state="$(adb shell dumpsys input_method 2>/dev/null || true)"
  # On Android 15 the IME process can report mIsInputViewShown=true even after the
  # system has hidden it. Prefer the input-method manager's authoritative state.
  if grep -Eq '^[[:space:]]*mInputShown=(true|false)' <<< "$state"; then
    grep -Eq '^[[:space:]]*mInputShown=true' <<< "$state"
  else
    grep -Eq 'mIsInputViewShown=true|isInputViewShown=true' <<< "$state"
  fi
}
require_ime() {
  if ! ime_visible; then
    adb shell dumpsys input_method > smoke-latest-ime.txt || true
    echo 'Expected the on-screen keyboard to be visible.' >&2
    exit 1
  fi
}
require_no_ime() {
  if ime_visible; then
    adb shell dumpsys input_method > smoke-latest-ime.txt || true
    echo 'Expected the on-screen keyboard to be hidden.' >&2
    exit 1
  fi
}

# #150 / #146: the real shared SearchField stays composed below an open sheet.
start_fixture focus
assert_text 'Status visible: true'
# Some emulator images start with navigation bars already hidden. Check that this helper
# preserves the initial state instead of assuming a particular navigation mode.
dump_ui
nav_initial="$(python3 - <<'PY2'
import xml.etree.ElementTree as ET
for n in ET.parse('smoke-latest-ui.xml').iter('node'):
    text = n.get('text', '')
    if text in ('Nav visible: true', 'Nav visible: false'):
        print(text.rsplit(' ', 1)[-1])
        break
else:
    raise SystemExit('Nav visibility label missing')
PY2
)"
tap_node text 'Focus search'
adb shell input text 'Needle123'
sleep 1
assert_text 'Search focus: true'
assert_text 'Query: Needle123'
require_ime
adb exec-out screencap -p > smoke-latest-search-before.png
tap_node text 'Open player'
assert_text 'Search focus: false'
assert_text 'Query: Needle123'
require_no_ime
assert_text 'Status visible: false'
assert_text "Nav visible: $nav_initial"
adb exec-out screencap -p > smoke-latest-search-after.png
# Recreating the sheet while a player editor has focus must not clear the editor or its keyboard.
tap_node text 'Focus editor'
adb shell input text 'Editor456'
sleep 1
assert_text 'Editor focus: true'
require_ime
tap_node text 'Resize sheet'
assert_text 'Sheet bound: 560'
assert_text 'Editor focus: true'
require_ime
assert_text 'Status visible: false'
adb exec-out screencap -p > smoke-latest-editor-resized.png
adb shell input keyevent 4
sleep 1
tap_node text 'Bar off'
assert_text 'Status visible: true'
tap_node text 'Bar on'
assert_text 'Status visible: false'
tap_node text 'Close player'
assert_text 'Status visible: true'
assert_text "Nav visible: $nav_initial"
cp smoke-latest-ui.xml smoke-latest-focus.xml
adb exec-out screencap -p > smoke-latest-focus.png

echo 'Search focus, editor resize, keyboard, and player status bar checks passed.'

# #149: heart state, menu and selection are independent targets on the actual shared row.
start_fixture album
assert_text 'Liked: false'
tap_node content-desc 'Like'
assert_text 'Liked: true'
assert_text 'Row count: 0'
adb exec-out screencap -p > smoke-latest-album-liked.png
tap_node content-desc 'More options'
assert_text 'Menu count: 1'
assert_text 'Row count: 0'
tap_node content-desc 'Remove like'
assert_text 'Liked: false'
tap_node text 'Toggle selection'
assert_text 'Selection tick'
dump_ui
if python3 - <<'PY'
import xml.etree.ElementTree as ET
nodes = list(ET.parse('smoke-latest-ui.xml').iter('node'))
raise SystemExit(0 if any(n.get('content-desc') in ('Like', 'Remove like', 'More options') for n in nodes) else 1)
PY
then
  echo 'Like/menu controls should be hidden in selection mode.' >&2
  exit 1
fi
tap_node text 'Fixture track'
assert_text 'Row count: 1'
cp smoke-latest-ui.xml smoke-latest-album.xml
adb exec-out screencap -p > smoke-latest-album.png

echo 'Album heart, menu, selection and row tap checks passed.'

adb logcat -d -v threadtime > smoke-latest-logcat.txt
python3 - <<'PY'
from pathlib import Path
import os
lines = Path('smoke-latest-logcat.txt').read_text(errors='replace').splitlines()
package = os.environ['APP_PACKAGE']
for i, line in enumerate(lines):
    if 'FATAL EXCEPTION' in line and any(f'Process: {package}' in x for x in lines[i:i+15]):
        print('\n'.join(lines[i:i+20]))
        raise SystemExit('App crash during latest-issues fixtures')
print('Latest-issues UI fixtures completed without an app fatal exception.')
PY
