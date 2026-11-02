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
  if grep -Eq '(^|[[:space:]])mInputShown=(true|false)([[:space:]]|$)' <<< "$state"; then
    grep -Eq '(^|[[:space:]])mInputShown=true([[:space:]]|$)' <<< "$state"
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

# The actual Apple background must honour static previews and both animation switches. Compare
# cropped pixels, not whole screenshots (the status bar clock and unrelated controls can change).
adb shell settings put global animator_duration_scale 1
start_fixture backdrop
assert_text 'Preview animated: false'
assert_text 'App motion: true'
capture_specimen() {
  local label="$1" output="$2"
  dump_ui
  adb exec-out screencap -p > "${output}-full.png"
  python3 - "$label" "$output" <<'PY'
import re, sys, xml.etree.ElementTree as ET
from PIL import Image
label, output = sys.argv[1:]
for node in ET.parse('smoke-latest-ui.xml').iter('node'):
    if node.get('content-desc') == label:
        bounds = [int(x) for x in re.findall(r'\d+', node.get('bounds', ''))]
        if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
            Image.open(output + '-full.png').convert('RGB').crop(tuple(bounds)).save(output + '.png')
            break
else:
    raise SystemExit(f'Specimen {label!r} not found')
PY
}
check_pixels() {
  python3 - "$1" "$2" "$3" <<'PY'
from PIL import Image, ImageChops, ImageStat
import sys
left, right = (Image.open(p).convert('RGB') for p in sys.argv[1:3])
if left.size != right.size:
    raise SystemExit(f'Specimen dimensions differ: {left.size} vs {right.size}')
difference = sum(ImageStat.Stat(ImageChops.difference(left, right)).mean) / 3
mode = sys.argv[3]
print(f'{mode}: mean RGB difference = {difference:.4f}')
if mode == 'static' and difference > 0.08:
    raise SystemExit('Expected a static specimen but pixels moved')
if mode == 'animated' and difference < 0.10:
    raise SystemExit('Expected visible movement but pixels did not change')
if mode == 'equivalent' and difference > 1.0:
    raise SystemExit('Cached and original shader renderings differ')
PY
}
# Give the local fixture cover time to decode and its palette to arrive.
sleep 2
capture_specimen 'Cached shader specimen' smoke-latest-cached
capture_specimen 'Original shader specimen' smoke-latest-original
check_pixels smoke-latest-cached.png smoke-latest-original.png equivalent
capture_specimen 'Animated backdrop specimen' smoke-latest-preview-static-a
sleep 2
capture_specimen 'Animated backdrop specimen' smoke-latest-preview-static-b
check_pixels smoke-latest-preview-static-a.png smoke-latest-preview-static-b.png static

tap_node text 'Toggle preview'
assert_text 'Preview animated: true'
sleep 1
capture_specimen 'Animated backdrop specimen' smoke-latest-preview-moving-a
sleep 2
capture_specimen 'Animated backdrop specimen' smoke-latest-preview-moving-b
check_pixels smoke-latest-preview-moving-a.png smoke-latest-preview-moving-b.png animated

tap_node text 'Toggle app motion'
assert_text 'App motion: false'
sleep 1
capture_specimen 'Animated backdrop specimen' smoke-latest-app-off-a
capture_specimen 'Playing bars specimen' smoke-latest-bars-app-off-a
capture_specimen 'Bones specimen' smoke-latest-bones-app-off-a
sleep 2
capture_specimen 'Animated backdrop specimen' smoke-latest-app-off-b
capture_specimen 'Playing bars specimen' smoke-latest-bars-app-off-b
capture_specimen 'Bones specimen' smoke-latest-bones-app-off-b
check_pixels smoke-latest-app-off-a.png smoke-latest-app-off-b.png static
check_pixels smoke-latest-bars-app-off-a.png smoke-latest-bars-app-off-b.png static
check_pixels smoke-latest-bones-app-off-a.png smoke-latest-bones-app-off-b.png static

tap_node text 'Toggle app motion'
assert_text 'App motion: true'
adb shell settings put global animator_duration_scale 0
sleep 2
capture_specimen 'Animated backdrop specimen' smoke-latest-system-off-a
capture_specimen 'Playing bars specimen' smoke-latest-bars-system-off-a
capture_specimen 'Bones specimen' smoke-latest-bones-system-off-a
sleep 2
capture_specimen 'Animated backdrop specimen' smoke-latest-system-off-b
capture_specimen 'Playing bars specimen' smoke-latest-bars-system-off-b
capture_specimen 'Bones specimen' smoke-latest-bones-system-off-b
check_pixels smoke-latest-system-off-a.png smoke-latest-system-off-b.png static
check_pixels smoke-latest-bars-system-off-a.png smoke-latest-bars-system-off-b.png static
check_pixels smoke-latest-bones-system-off-a.png smoke-latest-bones-system-off-b.png static

echo 'Backdrop preview, cached shaders, app/system reduced motion, equalizer and skeleton checks passed.'

# Re-enabling Android motion resumes it; covering home content freezes its bars/bones.
adb shell settings put global animator_duration_scale 1
sleep 2
capture_specimen 'Animated backdrop specimen' smoke-latest-system-on-a
sleep 2
capture_specimen 'Animated backdrop specimen' smoke-latest-system-on-b
check_pixels smoke-latest-system-on-a.png smoke-latest-system-on-b.png animated
tap_node text 'Toggle covered'
assert_text 'Covered: true'
sleep 1
capture_specimen 'Playing bars specimen' smoke-latest-covered-bars-a
capture_specimen 'Bones specimen' smoke-latest-covered-bones-a
sleep 2
capture_specimen 'Playing bars specimen' smoke-latest-covered-bars-b
capture_specimen 'Bones specimen' smoke-latest-covered-bones-b
check_pixels smoke-latest-covered-bars-a.png smoke-latest-covered-bars-b.png static
check_pixels smoke-latest-covered-bones-a.png smoke-latest-covered-bones-b.png static

adb logcat -d -v threadtime > smoke-latest-logcat.txt
python3 - <<'PY'
from pathlib import Path
import os
lines = Path('smoke-latest-logcat.txt').read_text(errors='replace').splitlines()
package = os.environ['APP_PACKAGE']
for i, line in enumerate(lines):
    if 'FATAL EXCEPTION' in line and any(f'Process: {package}' in x for x in lines[i:i+15]):
        print('\n'.join(lines[i:i+20]))
        raise SystemExit('App crash during motion-audit fixtures')
print('Motion-audit fixtures completed without an app fatal exception.')
PY
