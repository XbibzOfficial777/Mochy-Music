#!/usr/bin/env bash
set -euo pipefail
apk="$(find language-apk -name '*.apk' -print -quit)"
test -n "$apk"
package=com.mochyy.mochymusic.debug
activity="$package/com.mochyy.mochymusic.i18n.LanguageSmokeActivity"
cleanup() {
  adb logcat -d -v threadtime > language-logcat.txt || true
  adb exec-out screencap -p > language-final.png || true
  adb exec-out cat /sdcard/language-window.xml > language-final.xml 2>/dev/null || true
}
trap cleanup EXIT
adb wait-for-device
timeout 180 bash -c '
  until cfg="$(adb shell cmd activity get-config 2>/dev/null | tr -d "\r")" &&
    printf "%s\n" "$cfg" | grep -qiE "(^|[^[:alpha:]])(id|in)(-r|[-_+])ID([^[:alpha:]]|$)" &&
    [ "$(adb shell getprop sys.boot_completed | tr -d "\r")" = 1 ] &&
    adb shell cmd package list packages >/dev/null 2>&1; do sleep 3; done
  echo "Framework ready: $cfg"
'
adb install -r "$apk"
api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
# Write the exact legacy preference representation before the app's first launch.
seed_legacy() {
  adb shell am force-stop "$package"
  adb shell pm clear "$package" | grep -q Success
  if [ "$api" -ge 33 ]; then
    if [ "${1:-}" = platform-es ]; then
      adb shell cmd locale set-app-locales "$package" --user 0 --locales es
    else
      adb shell "cmd locale set-app-locales $package --user 0 --locales ''"
    fi
  fi
  python3 - <<'PY'
from pathlib import Path
key, text = b'appLanguage', b'fr'
value = b'\x2a' + bytes([len(text)]) + text  # Value.string
entry = b'\x0a' + bytes([len(key)]) + key + b'\x12' + bytes([len(value)]) + value
Path('language-legacy.pb').write_bytes(b'\x0a' + bytes([len(entry)]) + entry)
PY
  adb shell run-as "$package" mkdir -p files/datastore
  adb shell "run-as $package sh -c 'cat > files/datastore/settings.preferences_pb'" < language-legacy.pb
}
start() {
  adb shell am start -W -n "$activity" | tee language-start.txt
  grep -q 'Status: ok' language-start.txt
  sleep 7
}
dump() {
  adb shell uiautomator dump /sdcard/language-window.xml >/dev/null
  adb exec-out cat /sdcard/language-window.xml > language-ui.xml
}
node() {
  python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
wanted = sys.argv[1].casefold()
for n in ET.parse('language-ui.xml').iter('node'):
    if n.get('text', '').casefold() == wanted and n.get('class') != 'android.widget.EditText':
        xy = [int(x) for x in re.findall(r'\d+', n.get('bounds', ''))]
        if len(xy) == 4 and xy[2] > xy[0] and xy[3] > xy[1]:
            print((xy[0]+xy[2])//2, (xy[1]+xy[3])//2)
            break
else:
    raise SystemExit(f'Missing text: {sys.argv[1]!r}')
PY
}
assert_text() { dump; node "$1" >/dev/null; }
tap() { dump; local xy; xy="$(node "$1")"; adb shell input tap $xy; sleep 3; }

echo '=== Existing French appLanguage preference is respected ==='
seed_legacy
start
assert_text "Langue de l'application"
assert_text 'Offline mode'
if [ "$api" -ge 33 ]; then
  adb shell cmd locale get-app-locales "$package" --user 0 | tee language-platform-migrated.txt
  grep -q 'fr' language-platform-migrated.txt
fi
adb exec-out screencap -p > language-migrated-fr.png

echo '=== Reset follows device, including after process restart ==='
tap "Langue de l'application"
tap 'Valeurs par défaut du système'
sleep 6
assert_text 'Bahasa aplikasi'
adb shell am force-stop "$package"
start
assert_text 'Bahasa aplikasi'
assert_text 'Bawaan sistem'
if [ "$api" -ge 33 ]; then
  adb shell cmd locale get-app-locales "$package" --user 0 | tee language-platform-reset.txt
  grep -q '\[\]' language-platform-reset.txt
  echo '=== A pre-existing Android platform override wins over the legacy value ==='
  seed_legacy platform-es
  start
  assert_text 'Idioma de la aplicación'
  adb shell cmd locale get-app-locales "$package" --user 0 | tee language-platform-priority.txt
  grep -q 'es' language-platform-priority.txt
  adb exec-out screencap -p > language-migrated-existing-platform.png
fi

echo 'Legacy language migration and reset checks passed.'
