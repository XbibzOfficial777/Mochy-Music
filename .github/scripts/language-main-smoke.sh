#!/usr/bin/env bash
set -euo pipefail
apk="$(find language-apk -name '*.apk' -print -quit)"
test -n "$apk"
package=com.mochyy.mochymusic.debug
fixture="$package/com.mochyy.mochymusic.i18n.LanguageSmokeActivity"
main="$package/com.mochyy.mochymusic.MainActivity"
cleanup() {
  adb logcat -d -v threadtime > language-logcat.txt || true
  adb exec-out screencap -p > language-final.png || true
  adb exec-out cat /sdcard/language-window.xml > language-final.xml 2>/dev/null || true
}
trap cleanup EXIT
# This is a rooted, ephemeral Google APIs emulator. Change the locale after the
# runner has booted it, then wait for a *new* system_server and an unlocked user.
# sys.boot_completed alone stays 1 during a framework restart and is not sufficient.
set_device_locale() {
  local tag="$1" pattern="$2" oldpid
  adb root >/dev/null
  adb wait-for-device
  oldpid="$(adb shell pidof system_server | tr -d '\r')"
  test -n "$oldpid"
  adb shell setprop persist.sys.locale "$tag"
  adb shell setprop ctl.restart zygote || true
  timeout 180 bash -c '
    old="$1"; pattern="$2"
    while :; do
      pid="$(adb shell pidof system_server 2>/dev/null | tr -d "\r")"
      cfg="$(adb shell cmd activity get-config 2>/dev/null | tr -d "\r")"
      state="$(adb shell am get-started-user-state 0 2>/dev/null | tr -d "\r")"
      if [ -n "$pid" ] && [ "$pid" != "$old" ] &&
        printf "%s\n" "$cfg" | grep -qiE "$pattern" &&
        printf "%s\n" "$state" | grep -q RUNNING_UNLOCKED &&
        adb shell cmd package list packages >/dev/null 2>&1; then
          echo "New framework $pid, $state: $cfg"
          sleep 5
          exit 0
      fi
      sleep 3
    done
  ' _ "$oldpid" "$pattern"
}
set_device_locale id-ID '(^|[^[:alpha:]])(id|in)(-r|[-_+])ID([^[:alpha:]]|$)'
adb install -r "$apk"
adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
# Seed the normal DataStore with deterministic content preferences using the debug-only fixture.
adb shell am start -W -n "$fixture" | tee language-start.txt
grep -q 'Status: ok' language-start.txt
sleep 8
adb shell am force-stop "$package"
# Skip onboarding only in this temporary emulator. Append a valid protobuf map entry
# setupCompleted=true to the app's existing preferences file; no release source is changed.
adb exec-out run-as "$package" cat files/datastore/settings.preferences_pb > language-prefs-before.pb
python3 - <<'PY'
from pathlib import Path
old = Path('language-prefs-before.pb').read_bytes()
assert old, 'expected existing DataStore preferences'
key = b'setupCompleted'
value = b'\x08\x01'  # Value.boolean = true
entry = b'\x0a' + bytes([len(key)]) + key + b'\x12' + bytes([len(value)]) + value
Path('language-prefs-seeded.pb').write_bytes(old + b'\x0a' + bytes([len(entry)]) + entry)
PY
adb shell "run-as $package sh -c 'cat > files/datastore/settings.preferences_pb'" < language-prefs-seeded.pb
adb shell run-as "$package" mkdir -p no_backup
adb shell run-as "$package" touch no_backup/setup_wizard_completed

start_main() {
  adb shell am start -W -n "$main" | tee language-main-start.txt
  grep -q 'Status: ok' language-main-start.txt
  sleep 30
}
dump() {
  adb shell uiautomator dump /sdcard/language-window.xml >/dev/null
  adb exec-out cat /sdcard/language-window.xml > language-ui.xml
}
node() {
  python3 - "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
field, wanted = sys.argv[1], sys.argv[2].casefold()
for n in ET.parse('language-ui.xml').iter('node'):
    if n.get(field, '').casefold() == wanted and n.get('class') != 'android.widget.EditText':
        xy = [int(x) for x in re.findall(r'\d+', n.get('bounds', ''))]
        if len(xy) == 4 and xy[2] > xy[0] and xy[3] > xy[1]:
            print((xy[0]+xy[2])//2, (xy[1]+xy[3])//2)
            break
else:
    raise SystemExit(f'Missing node {field}={sys.argv[2]!r}')
PY
}
tap() {
  dump
  local xy
  xy="$(node "$1" "$2")"
  adb shell input tap $xy
  sleep 3
}
assert_node() { dump; node "$1" "$2" >/dev/null; }
scroll_to() {
  local field="$1" text="$2"
  for i in 1 2 3 4 5 6 7; do
    dump
    if node "$field" "$text" >/dev/null 2>&1; then return 0; fi
    adb shell input swipe 160 490 160 180 500
    sleep 2
  done
  echo "Could not find after scrolling: $field=$text" >&2
  return 1
}
open_general_indonesian() {
  tap content-desc 'Pengaturan'
  scroll_to text 'Open settings'
  tap text 'Open settings'
  scroll_to text 'Umum'
  tap text 'Umum'
  assert_node text 'Bahasa aplikasi'
}
open_general_english() {
  tap content-desc 'Settings'
  scroll_to text 'Open settings'
  tap text 'Open settings'
  scroll_to text 'General'
  tap text 'General'
  assert_node text 'App language'
}

echo '=== Launch actual MainActivity in Indonesian ==='
start_main
assert_node content-desc 'Pengaturan'
adb exec-out screencap -p > language-main-home-id.png
open_general_indonesian
assert_node text 'Mode offline'
adb exec-out screencap -p > language-main-general-id.png

echo '=== Choose English from the real MainActivity ==='
tap text 'Bahasa aplikasi'
tap text 'Cari bahasa'
adb shell input text English
sleep 1
adb shell input keyevent 4
sleep 1
tap text English
sleep 6
assert_node text 'App language'
assert_node text 'Offline mode'
adb exec-out screencap -p > language-main-general-en.png

echo '=== Restart the real app and confirm the choice persists ==='
adb shell am force-stop "$package"
start_main
assert_node content-desc 'Settings'
open_general_english
assert_node text 'English'
adb exec-out screencap -p > language-main-persist-en.png

echo '=== Restore system language from the real app ==='
tap text 'App language'
tap text 'System default'
sleep 6
assert_node text 'Bahasa aplikasi'
assert_node text 'Bawaan sistem'
adb exec-out screencap -p > language-main-restored-id.png

echo 'Actual MainActivity language UI smoke passed.'
