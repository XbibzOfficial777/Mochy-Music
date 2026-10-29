#!/usr/bin/env bash
set -euo pipefail
apk="$(find language-apk -name '*.apk' -print -quit)"
test -n "$apk"
package=com.mochyy.mochymusic
main="$package/com.mochyy.mochymusic.MainActivity"
export APP_PACKAGE="$package"
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
# This is the actual non-debuggable, signed arm64 APK; no fixture is installed.
adb install -r "$apk"
adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb shell am force-stop "$package"
# Seed onboarding completion only on this rooted disposable emulator. DataStore's
# on-disk protocol and the no-backup marker are unchanged in the shipped APK.
python3 - <<'PY2'
from pathlib import Path
key=b'setupCompleted'
value=b'\x08\x01'
entry=b'\x0a'+bytes([len(key)])+key+b'\x12'+bytes([len(value)])+value
Path('language-seeded.pb').write_bytes(b'\x0a'+bytes([len(entry)])+entry)
PY2
adb push language-seeded.pb /data/local/tmp/language-seeded.pb >/dev/null
appdir="/data/user/0/$package"
uid="$(adb shell stat -c %u "$appdir" | tr -d '\r')"
gid="$(adb shell stat -c %g "$appdir" | tr -d '\r')"
[[ "$uid" =~ ^[0-9]+$ && "$gid" =~ ^[0-9]+$ ]]
adb shell "mkdir -p $appdir/files/datastore $appdir/no_backup && \
  cp /data/local/tmp/language-seeded.pb $appdir/files/datastore/settings.preferences_pb && \
  touch $appdir/no_backup/setup_wizard_completed && \
  chown $uid:$gid $appdir/files && \
  chown -R $uid:$gid $appdir/files/datastore $appdir/no_backup && \
  chmod 700 $appdir/files/datastore $appdir/no_backup && \
  chmod 600 $appdir/files/datastore/settings.preferences_pb && \
  restorecon -R $appdir/files $appdir/no_backup"
adb logcat -c

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

adb logcat -d -v threadtime > language-logcat.txt
python3 - <<'PY2'
from pathlib import Path
import os
lines = Path('language-logcat.txt').read_text(errors='replace').splitlines()
package = os.environ['APP_PACKAGE']
for i, line in enumerate(lines):
    if 'FATAL EXCEPTION' in line and any('Process: '+package in x for x in lines[i:i+15]):
        print('\n'.join(lines[i:i+20]))
        raise SystemExit('App fatal exception during signed release UI test')
print('Signed arm64 MainActivity language UI smoke passed without an app fatal exception.')
PY2
