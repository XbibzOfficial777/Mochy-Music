#!/usr/bin/env bash
# Exact signed candidate, no accounts: import two synthetic local audio files and exercise player.
set -euo pipefail
apk="$(find language-apk -name '*.apk' -print -quit)"
test -n "$apk"
: "${RUNTIME_ABI:?}"
package=com.mochyy.mochymusic
main="$package/com.mochyy.mochymusic.MainActivity"
export APP_PACKAGE="$package"
cleanup() {
  adb logcat -d -v threadtime > language-playback-logcat.txt || true
  adb exec-out screencap -p > language-playback-final.png || true
  adb exec-out cat /sdcard/playback-ui.xml > language-playback-final.xml 2>/dev/null || true
  adb shell dumpsys media_session > language-playback-media-final.txt || true
}
trap cleanup EXIT
adb root >/dev/null
adb wait-for-device
adb install -r "$apk"
adb shell dumpsys package "$package" | tr -d '\r' > language-playback-package.txt
grep -F "primaryCpuAbi=$RUNTIME_ABI" language-playback-package.txt
grep -E 'versionCode=79([[:space:]]|$)' language-playback-package.txt
adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb shell pm grant "$package" android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1 || true
adb shell pm grant "$package" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1 || true
adb shell am force-stop "$package"
# Disposable emulator only: completed onboarding, Apple backdrop, default flagship player.
# Preferences DataStore's map entries use string field 5 and bool field 1.
python3 - <<'PY'
from pathlib import Path

def item(key, value):
    key = key.encode()
    entry = b'\x0a' + bytes([len(key)]) + key + b'\x12' + bytes([len(value)]) + value
    return b'\x0a' + bytes([len(entry)]) + entry

def string(value):
    value = value.encode()
    return b'\x2a' + bytes([len(value)]) + value
Path('language-playback-seed.pb').write_bytes(
    item('setupCompleted', b'\x08\x01') + item('playerBackgroundStyle', string('APPLE_MUSIC'))
)
PY
adb push language-playback-seed.pb /data/local/tmp/language-playback-seed.pb >/dev/null
appdir="/data/user/0/$package"
uid="$(adb shell stat -c %u "$appdir" | tr -d '\r')"
gid="$(adb shell stat -c %g "$appdir" | tr -d '\r')"
[[ "$uid" =~ ^[0-9]+$ && "$gid" =~ ^[0-9]+$ ]]
adb shell "mkdir -p $appdir/files/datastore $appdir/no_backup && \
  cp /data/local/tmp/language-playback-seed.pb $appdir/files/datastore/settings.preferences_pb && \
  touch $appdir/no_backup/setup_wizard_completed && \
  chown $uid:$gid $appdir/files && chown -R $uid:$gid $appdir/files/datastore $appdir/no_backup && \
  chmod 700 $appdir/files/datastore $appdir/no_backup && \
  chmod 600 $appdir/files/datastore/settings.preferences_pb && restorecon -R $appdir/files $appdir/no_backup"
# Two small, local PCM recordings; no copyrighted audio or network calls are needed.
python3 - <<'PY'
import math, struct, wave
for suffix, freq in [('A', 330), ('B', 440)]:
    with wave.open(f'language-MotionAudit{suffix}.wav', 'wb') as out:
        out.setnchannels(1); out.setsampwidth(2); out.setframerate(8000)
        second = b''.join(struct.pack('<h', int(2500 * math.sin(2 * math.pi * freq * n / 8000))) for n in range(8000))
        for _ in range(120): out.writeframes(second)
PY
adb shell settings put global animator_duration_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global window_animation_scale 1
adb logcat -c
adb shell am start -W -n "$main" | tee language-playback-start.txt
grep -q 'Status: ok' language-playback-start.txt
sleep 30
for suffix in A B; do
  file="MotionAudit${suffix}.wav"
  adb push "language-$file" "/sdcard/Music/$file" >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE --receiver-include-background -d "file:///sdcard/Music/$file" >/dev/null
  id=''
  for attempt in $(seq 1 15); do
    result="$(adb shell content query --uri content://media/external/audio/media --projection _id:_display_name 2>&1 | tr -d '\r' || true)"
    printf '%s\n' "$result" > "language-playback-media-index-$suffix.txt"
    id="$(printf '%s\n' "$result" | grep -F "_display_name=$file" | sed -n 's/.*_id=\([0-9][0-9]*\).*/\1/p' | head -1 || true)"
    [ -n "$id" ] && break
    # Some API 30 images do not deliver the deprecated scan-file broadcast.
    if [ "$attempt" = 4 ]; then
      timeout 30s adb shell content call --method scan_volume --uri content://media --arg external_primary \
        > "language-playback-rescan-$suffix.txt" 2>&1 || true
    fi
    sleep 2
  done
  if ! [[ "$id" =~ ^[0-9]+$ ]]; then
    echo "Media scanner did not index $file: $result" >&2
    exit 1
  fi
  echo "Synthetic local file $file indexed as media item $id"
  adb shell am start -W -n "$main" -a android.intent.action.SEND -t 'audio/wav' \
    --eu android.intent.extra.STREAM "content://media/external/audio/media/$id" --grant-read-uri-permission \
    | tee "language-playback-import-$suffix.txt"
  sleep 8
done

dump() {
  adb shell uiautomator dump /sdcard/playback-ui.xml >/dev/null
  adb exec-out cat /sdcard/playback-ui.xml > language-playback-ui.xml
  if grep -q "Pixel Launcher isn't responding" language-playback-ui.xml; then
    xy="$(python3 - <<'PY'
import re, xml.etree.ElementTree as ET
for n in ET.parse('language-playback-ui.xml').iter('node'):
    if n.get('resource-id') == 'android:id/aerr_close':
        b = list(map(int, re.findall(r'\d+', n.get('bounds', ''))))
        print((b[0]+b[2])//2, (b[1]+b[3])//2); break
else: raise SystemExit('Cannot dismiss unrelated launcher ANR')
PY
)"
    adb shell input tap $xy; sleep 3
    adb shell uiautomator dump /sdcard/playback-ui.xml >/dev/null
    adb exec-out cat /sdcard/playback-ui.xml > language-playback-ui.xml
  fi
}
node() {
  python3 - "$1" "$2" "${3:-first}" <<'PY'
import re, sys, xml.etree.ElementTree as ET
field, wanted, order = sys.argv[1], sys.argv[2].casefold(), sys.argv[3]
found=[]
for n in ET.parse('language-playback-ui.xml').iter('node'):
    if n.get(field, '').casefold() == wanted:
        b=list(map(int, re.findall(r'\d+', n.get('bounds', ''))))
        if len(b)==4 and b[2]>b[0] and b[3]>b[1]:
            found.append(((b[0]+b[2])//2,(b[1]+b[3])//2))
if not found: raise SystemExit(f'Missing node {field}={wanted!r}')
print(*max(found,key=lambda p:p[1]) if order=='bottom' else found[0])
PY
}
tap() { dump; xy="$(node "$1" "$2" "${3:-first}")"; adb shell input tap $xy; sleep 3; }
scroll_to() {
  for i in 1 2 3 4 5; do
    dump
    node "$1" "$2" >/dev/null 2>&1 && return 0
    adb shell input swipe 170 540 170 200 400; sleep 2
  done
  echo "Could not find $1=$2" >&2; return 1
}

check_session() {
  python3 - "$1" "$2" "$3" <<'PY'
from pathlib import Path
import re, sys
path, expected_title, expected_state = sys.argv[1:]
text=Path(path).read_text(errors='replace')
blocks=re.split(r'\n(?=    [^\s])', text)
blocks=[b for b in blocks if re.search(r'\n\s+package=com\.mochyy\.mochymusic\s*\n', b)]
if len(blocks) != 1: raise SystemExit(f'Expected exactly one app MediaSession, found {len(blocks)} in {path}')
block=blocks[0]
if expected_title not in block: raise SystemExit(f'{path}: expected title {expected_title!r}')
name, number={'playing': ('PLAYING', '3'), 'paused': ('PAUSED', '2')}[expected_state]
if not re.search(r'state=PlaybackState\s*\{state=(?:' + name + r'\(' + number + r'\)|' + number + r')(?=[,\s])', block):
    raise SystemExit(f'{path}: expected {name} state')
print(f'{path}: {expected_title} is {name}')
PY
}

# A track change can briefly report BUFFERING, especially on a loaded CI emulator.
# Require the requested title and stable state within a bounded interval.
await_session() {
  local output="$1" title="$2" state="$3"
  for attempt in $(seq 1 15); do
    adb shell dumpsys media_session > "$output"
    if check_session "$output" "$title" "$state" >/dev/null 2>&1; then
      check_session "$output" "$title" "$state"
      return 0
    fi
    sleep 1
  done
  check_session "$output" "$title" "$state"
}

echo '=== Open the real library and play the synthetic two-track queue ==='
tap content-desc 'Your Library'
scroll_to text 'Songs'
tap text 'Songs'
scroll_to text 'MotionAuditA'
tap text 'MotionAuditA'
sleep 5
await_session language-playback-first.txt MotionAuditA playing
# Tap the mini player's title (bottom-most matching label), then exercise a real queue skip.
tap text 'MotionAuditA' bottom
sleep 3
dump
if grep -q 'Viewing full screen' language-playback-ui.xml; then
  xy="$(node text 'Got it')"; adb shell input tap $xy; sleep 2
fi
adb exec-out screencap -p > language-playback-player-open.png
# input key events exercise the real MediaSession; the standalone `media` binary
# is not installed on all Google APIs emulator images.
adb shell input keyevent 87  # KEYCODE_MEDIA_NEXT
sleep 2
await_session language-playback-next.txt MotionAuditB playing
# System reduced motion: the hand-stepped carousel still completes a previous-track change.
adb shell settings put global animator_duration_scale 0
adb shell input keyevent 88  # KEYCODE_MEDIA_PREVIOUS
sleep 1
adb shell dumpsys media_session > language-playback-previous.txt
# A late previous command can restart the current track instead of changing it.
if ! grep -Fq 'MotionAuditA' language-playback-previous.txt; then
  adb shell input keyevent 88  # KEYCODE_MEDIA_PREVIOUS
  sleep 1
  adb shell dumpsys media_session > language-playback-previous.txt
fi
await_session language-playback-previous.txt MotionAuditA playing
adb exec-out screencap -p > language-playback-reduced.png
adb shell input keyevent 127  # KEYCODE_MEDIA_PAUSE
sleep 2
await_session language-playback-paused.txt MotionAuditA paused
adb shell input keyevent 126  # KEYCODE_MEDIA_PLAY
sleep 2
await_session language-playback-resumed.txt MotionAuditA playing
adb logcat -d -v threadtime > language-playback-logcat.txt
python3 - <<'PY'
from pathlib import Path
import os
lines = Path('language-playback-logcat.txt').read_text(errors='replace').splitlines()
package = os.environ['APP_PACKAGE']
for i, line in enumerate(lines):
    if 'FATAL EXCEPTION' in line and any(f'Process: {package}' in x for x in lines[i:i+15]):
        print('\n'.join(lines[i:i+20])); raise SystemExit('App crash during signed playback test')
print('Signed local playback, player, queue skip and reduced-motion check passed.')
PY
