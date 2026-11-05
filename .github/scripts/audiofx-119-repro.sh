#!/usr/bin/env bash
# Observe issue #119 against the unmodified public official release, not a fork build.
set -euo pipefail
mkdir -p audiofx-repro
pkg=com.mochyy.mochymusic
appdir=/data/user/0/$pkg
cleanup() {
  adb logcat -d -v threadtime > audiofx-repro/logcat.txt 2>/dev/null || true
  adb exec-out screencap -p > audiofx-repro/final.png 2>/dev/null || true
  adb shell uiautomator dump /sdcard/audiofx-ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/audiofx-ui.xml > audiofx-repro/final.xml 2>/dev/null || true
  adb shell cat "$appdir/shared_prefs/audio_lab.xml" > audiofx-repro/final-prefs.xml 2>/dev/null || true
}
trap cleanup EXIT
adb root >/dev/null; adb wait-for-device
adb shell wm size 720x1540
adb shell wm density 280
adb install official.apk
adb shell dumpsys package "$pkg" | tr -d '\r' | grep -m1 'versionCode='
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb shell pm grant "$pkg" android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1 || true
adb shell am force-stop "$pkg"
# Disposable emulator: bypass onboarding only.
python3 - <<'PY'
from pathlib import Path
key=b'setupCompleted'; value=b'\x08\x01'
entry=b'\x0a'+bytes([len(key)])+key+b'\x12'+bytes([len(value)])+value
Path('audiofx-repro/seed.pb').write_bytes(b'\x0a'+bytes([len(entry)])+entry)
PY
adb push audiofx-repro/seed.pb /data/local/tmp/audiofx-seed.pb >/dev/null
uid="$(adb shell stat -c %u "$appdir" | tr -d '\r')"
gid="$(adb shell stat -c %g "$appdir" | tr -d '\r')"
adb shell "mkdir -p $appdir/files/datastore $appdir/no_backup && cp /data/local/tmp/audiofx-seed.pb $appdir/files/datastore/settings.preferences_pb && touch $appdir/no_backup/setup_wizard_completed && chown $uid:$gid $appdir/files && chown -R $uid:$gid $appdir/files/datastore $appdir/no_backup && chmod 700 $appdir/files/datastore $appdir/no_backup && chmod 600 $appdir/files/datastore/settings.preferences_pb && restorecon -R $appdir/files $appdir/no_backup"
adb shell am start -W -n "$pkg/.MainActivity"
sleep 12

dump() {
  adb shell uiautomator dump /sdcard/audiofx-ui.xml >/dev/null
  adb exec-out cat /sdcard/audiofx-ui.xml > audiofx-repro/current.xml
}
summary() {
  python3 - <<'PY'
import xml.etree.ElementTree as ET
for n in ET.parse('audiofx-repro/current.xml').iter('node'):
    text=n.get('text',''); desc=n.get('content-desc','')
    if text or desc: print(repr(text), 'desc='+repr(desc), n.get('bounds'), 'clickable='+n.get('clickable',''), 'checked='+n.get('checked',''))
PY
}
snapshot() {
  dump
  cp audiofx-repro/current.xml "audiofx-repro/$1.xml"
  adb exec-out screencap -p > "audiofx-repro/$1.png"
  adb shell cat "$appdir/shared_prefs/audio_lab.xml" > "audiofx-repro/$1-prefs.xml" 2>/dev/null || true
  echo "=== $1 UI ==="; summary
}
node() {
  python3 - "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
field,wanted=sys.argv[1:]
for n in ET.parse('audiofx-repro/current.xml').iter('node'):
    if n.get(field,'').casefold()==wanted.casefold():
        b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
        if len(b)==4 and b[2]-b[0]>=12 and b[3]-b[1]>=12:
            x=(b[0]+b[2])//2; y=(b[1]+b[3])//2
            if 105 < y < 1440:
                print(x,y); break
else: raise SystemExit(1)
PY
}
tap() {
  dump
  local xy
  xy="$(node "$1" "$2")" || { echo "Missing visible $1=$2"; summary; return 1; }
  echo "tap $1=$2 at $xy"
  adb shell input tap $xy
  sleep 3
}
scroll_to() {
  local field="$1" target="$2" direction="${3:-down}"
  for i in $(seq 1 18); do
    dump
    node "$field" "$target" >/dev/null 2>&1 && return 0
    if [[ "$direction" = down ]]; then adb shell input swipe 350 1230 350 350 500
    else adb shell input swipe 350 350 350 1230 500; fi
    sleep 1
  done
  echo "Cannot scroll to $field=$target"; summary; return 1
}
snapshot launch
# Profile/settings is a tab in the original app.
tap content-desc 'Settings'
scroll_to text 'Open settings'
tap text 'Open settings'
scroll_to text 'Audio Lab'
tap text 'Audio Lab'
scroll_to text 'Audio Effect'
tap text 'Audio Effect'
snapshot audiofx-initial
scroll_to text 'Reverb'
tap text 'Reverb'
sleep 3
snapshot reverb-enabled
python3 - <<'PY'
import json,xml.etree.ElementTree as ET
p={n.get('name'): n.text for n in ET.parse('audiofx-repro/reverb-enabled-prefs.xml').getroot() if n.tag=='string'}
assert json.loads(p['reverb'])['enabled'] is True, 'Reverb did not turn on; cannot test the issue'
PY
scroll_to text 'Playback speed'
snapshot speed-with-reverb-before
# Tap the actual + button and record the on-screen text and saved playback state.
tap text '+'
snapshot speed-with-reverb-after
scroll_to text 'Exponential volume'
snapshot volume-with-reverb-before
tap text 'Exponential volume'
snapshot volume-with-reverb-after
# Turn reverb back off and repeat, providing a same-emulator comparison.
scroll_to text 'Reverb' up
tap text 'Reverb'
sleep 3
snapshot reverb-disabled
python3 - <<'PY'
import json,xml.etree.ElementTree as ET
p={n.get('name'): n.text for n in ET.parse('audiofx-repro/reverb-disabled-prefs.xml').getroot() if n.tag=='string'}
assert json.loads(p['reverb'])['enabled'] is False, 'Reverb did not turn off; comparison is invalid'
PY
scroll_to text 'Playback speed'
snapshot speed-without-reverb-before
tap text '+'
snapshot speed-without-reverb-after
scroll_to text 'Exponential volume'
snapshot volume-without-reverb-before
tap text 'Exponential volume'
snapshot volume-without-reverb-after
python3 - <<'PY'
import json,xml.etree.ElementTree as ET

def pref(name,key):
    root=ET.parse(f'audiofx-repro/{name}-prefs.xml').getroot()
    data=next((n.text for n in root if n.get('name')==key), None)
    return json.loads(data) if data else None
for test in ['speed-with-reverb-before','speed-with-reverb-after','volume-with-reverb-before','volume-with-reverb-after','speed-without-reverb-before','speed-without-reverb-after','volume-without-reverb-before','volume-without-reverb-after']:
    print(test, 'playback=',pref(test,'playback'), 'volume=',pref(test,'volume'))
print('Observations recorded; interpret only if both Reverb states and tap positions are verified.')
PY
