#!/usr/bin/env bash
set -euo pipefail
apk="$(find language-apk -name '*.apk' -print -quit)"
[ -n "$apk" ]
package=com.mochyy.mochymusic.debug
activity="$package/com.mochyy.mochymusic.i18n.LanguageSmokeActivity"
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
start() {
  adb shell am start -W -n "$activity" | tee language-start.txt
  grep -q 'Status: ok' language-start.txt
  sleep 4
}
dump() {
  adb shell uiautomator dump /sdcard/language-window.xml >/dev/null
  adb exec-out cat /sdcard/language-window.xml > language-ui.xml
}
node() {
  python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
wanted = sys.argv[1].casefold()
for node in ET.parse('language-ui.xml').iter('node'):
    if node.get('text', '').casefold() == wanted and node.get('class') != 'android.widget.EditText':
        xy = [int(n) for n in re.findall(r'\d+', node.get('bounds',''))]
        if len(xy) == 4 and xy[2] > xy[0] and xy[3] > xy[1]:
            print((xy[0]+xy[2])//2, (xy[1]+xy[3])//2)
            break
else:
    raise SystemExit(f'Missing text: {sys.argv[1]!r}')
PY
}
assert_text() { dump; node "$1" >/dev/null; }
tap() {
  dump
  local xy
  xy="$(node "$1")"
  adb shell input tap $xy
  sleep 2
}
assert_preferences() { assert_text 'FIXTURE content=ja country=JP lyrics=es youtube=ja/JP'; }
search_and_select() {
  local field="$1" query="$2" label="$3"
  tap "$field"
  adb shell input text "$query"
  sleep 1
  adb shell input keyevent 4 # dismiss IME before clicking a result
  sleep 1
  tap "$label"
  sleep 5
}
force_restart() { adb shell am force-stop "$package"; start; }

echo '=== Device locale follows Indonesian by default ==='
start
assert_text 'SYSTEM id-ID'
assert_text 'Umum'
assert_text 'Bahasa aplikasi'
assert_text 'Bawaan sistem'
assert_text 'Mode offline'
assert_preferences
adb exec-out screencap -p > language-system-id.png

echo '=== Pick English independently of Indonesian device; persist across restart ==='
tap 'Bahasa aplikasi'
assert_text 'Beberapa terjemahan belum lengkap; teks yang belum tersedia ditampilkan dalam bahasa Inggris.'
search_and_select 'Cari bahasa' English English
assert_text 'General'
assert_text 'App language'
assert_text 'Offline mode'
assert_text 'SYSTEM id-ID'
assert_preferences
force_restart
assert_text 'App language'
assert_text 'Offline mode'
assert_preferences
adb exec-out screencap -p > language-english.png

echo '=== Select French, fall back to English rather than Indonesian for missing strings ==='
tap 'App language'
search_and_select 'Search languages' French 'français'
assert_text 'Général'
assert_text "Langue de l'application"
assert_text 'Offline mode'
assert_preferences
force_restart
assert_text "Langue de l'application"
assert_text 'Offline mode'
assert_preferences
adb exec-out screencap -p > language-french-fallback.png

echo '=== Follow device again ==='
tap "Langue de l'application"
tap 'Valeurs par défaut du système'
sleep 5
assert_text 'Bahasa aplikasi'
assert_text 'Bawaan sistem'
assert_text 'Mode offline'
assert_preferences

api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [ "$api" -ge 33 ]; then
  echo '=== Android system per-app language remains the source of truth ==='
  adb shell cmd locale get-app-locales "$package" --user 0 | tee language-platform-default.txt
  grep -q '\[\]' language-platform-default.txt
  adb shell cmd locale set-app-locales "$package" --user 0 --locales es
  sleep 3
  start
  assert_text 'Idioma de la aplicación'
  assert_text 'Offline mode'
  assert_preferences
  adb shell cmd locale get-app-locales "$package" --user 0 | tee language-platform-es.txt
  grep -q 'es' language-platform-es.txt
  # Reset by tapping the in-app system-default row, then confirm the OS setting is empty.
  tap 'Idioma de la aplicación'
  tap 'Predeterminado del sistema'
  sleep 5
  assert_text 'Bahasa aplikasi'
  adb shell cmd locale get-app-locales "$package" --user 0 | tee language-platform-reset.txt
  grep -q '\[\]' language-platform-reset.txt
fi

echo '=== Unsupported device locale falls back to English ==='
set_device_locale zu-ZA '(^|[^[:alpha:]])zu(-r|[-_+])ZA([^[:alpha:]]|$)'
start
assert_text 'SYSTEM zu-ZA'
assert_text 'App language'
assert_text 'Offline mode'
assert_preferences
adb exec-out screencap -p > language-unsupported.png

echo '=== Pick Indonesian on an unsupported-language device; persist and reset ==='
tap 'App language'
search_and_select 'Search languages' Indonesian 'Bahasa Indonesia'
assert_text 'Bahasa aplikasi'
assert_text 'Mode offline'
assert_text 'SYSTEM zu-ZA'
assert_preferences
force_restart
assert_text 'Bahasa aplikasi'
assert_text 'Mode offline'
tap 'Bahasa aplikasi'
tap 'Bawaan sistem'
sleep 5
assert_text 'App language'
assert_text 'Offline mode'
assert_preferences

echo 'Language smoke tests passed.'
