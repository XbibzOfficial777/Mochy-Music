#!/usr/bin/env bash
# Uses the tested fixed original-project APK and original-app imports, with self-generated audio.
set -euo pipefail
mkdir -p album-order-evidence
pkg=com.mochyy.mochymusic.debug
appdir=/data/user/0/$pkg
cleanup() {
  timeout 5s adb exec-out screencap -p > album-order-evidence/final.png 2>/dev/null || true
  timeout 8s adb shell uiautomator dump /sdcard/album-ui.xml >/dev/null 2>&1 || true
  timeout 5s adb exec-out cat /sdcard/album-ui.xml > album-order-evidence/final.xml 2>/dev/null || true
  kill "${logpid:-}" 2>/dev/null || true
}
trap cleanup EXIT
adb root >/dev/null; adb wait-for-device
adb shell wm size 640x960
adb shell wm density 240
adb logcat -c
adb logcat -v threadtime > album-order-evidence/logcat.txt 2>&1 & logpid=$!
adb install candidate-apk/app-universal-foss-debug.apk
adb shell dumpsys package "$pkg" | tr -d '\r' > album-order-evidence/package.txt
grep -m1 'versionCode=' album-order-evidence/package.txt
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb shell pm grant "$pkg" android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1 || true
adb shell am force-stop "$pkg"
python3 - <<'PY'
from pathlib import Path
k=b'setupCompleted'; v=b'\x08\x01'; e=b'\x0a'+bytes([len(k)])+k+b'\x12'+bytes([len(v)])+v
Path('album-order-evidence/seed.pb').write_bytes(b'\x0a'+bytes([len(e)])+e)
PY
adb push album-order-evidence/seed.pb /data/local/tmp/album-seed.pb >/dev/null
uid="$(adb shell stat -c %u "$appdir" | tr -d '\r')"; gid="$(adb shell stat -c %g "$appdir" | tr -d '\r')"
adb shell "mkdir -p $appdir/files/datastore $appdir/no_backup && cp /data/local/tmp/album-seed.pb $appdir/files/datastore/settings.preferences_pb && touch $appdir/no_backup/setup_wizard_completed && chown $uid:$gid $appdir/files && chown -R $uid:$gid $appdir/files/datastore $appdir/no_backup && chmod 700 $appdir/files/datastore $appdir/no_backup && chmod 600 $appdir/files/datastore/settings.preferences_pb && restorecon -R $appdir/files $appdir/no_backup"
# Deliberately import tracks in order 2, 3, 1. Metadata says 1, 2, 3.
for n in 2 3 1; do
  title="ZZ Fixture Track 0$n"; filename="fixture-album-0$n.mp3"
  ffmpeg -nostdin -hide_banner -loglevel error -f lavfi -i "sine=frequency=$((250+n*90)):duration=6" \
    -q:a 7 -metadata album='ZZ Fixture Album' -metadata album_artist='Fixture Artist' \
    -metadata artist='Fixture Artist' -metadata title="$title" \
    -metadata track="$n/3" -metadata disc='1/1' "$filename"
  adb push "$filename" "/sdcard/Music/$filename" >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Music/$filename" >/dev/null || true
  id=''
  for attempt in $(seq 1 15); do
    result="$(adb shell content query --uri content://media/external/audio/media --projection _id:_display_name 2>&1 | tr -d '\r' || true)"
    id="$(printf '%s\n' "$result" | grep -F "_display_name=$filename" | sed -n 's/.*_id=\([0-9][0-9]*\).*/\1/p' | head -1 || true)"
    [ -n "$id" ] && break
    if [ "$attempt" = 4 ]; then timeout 30s adb shell content call --method scan_volume --uri content://media --arg external_primary >/dev/null 2>&1 || true; fi
    sleep 2
  done
  [[ "$id" =~ ^[0-9]+$ ]] || { echo "Not indexed: $filename"; exit 1; }
  echo "Import $title as media $id"
  adb shell am start -W -n "$pkg/com.mochyy.mochymusic.MainActivity" -a android.intent.action.SEND -t 'audio/mpeg' \
    --eu android.intent.extra.STREAM "content://media/external/audio/media/$id" --grant-read-uri-permission \
    > "album-order-evidence/import-$n.txt"
  sleep 8
done
sleep 8
# Freeze the disposable DB before opening the album; also records fixture positions if the UI crashes.
adb shell am force-stop "$pkg"
for f in mochy.db mochy.db-wal mochy.db-shm; do adb pull "$appdir/databases/$f" "album-order-evidence/$f" >/dev/null 2>&1 || true; done
python3 - <<'PYCHECK'
import sqlite3
c=sqlite3.connect('album-order-evidence/mochy.db')
q="""SELECT song.title, song_album_map.`index`, song_album_map.rowid FROM song JOIN song_album_map ON song.id=song_album_map.songId JOIN album ON album.id=song_album_map.albumId WHERE album.title=? ORDER BY song_album_map.rowid"""
rows=c.execute(q,('ZZ Fixture Album',)).fetchall()
print('IMPORTED MAP INSERTION ORDER:',rows,flush=True)
assert [(t,i) for t,i,_ in rows]==[(f'ZZ Fixture Track 0{x}',1000+x) for x in [2,3,1]], 'Fixture tags/order do not match the intended baseline'
albumid=c.execute('SELECT id FROM album WHERE title=?',('ZZ Fixture Album',)).fetchone()[0]
q="""SELECT song.title FROM song JOIN song_album_map j ON j.songId=song.id WHERE j.albumId=? ORDER BY j.`index`, j.rowid"""
print('ORDERED JOIN:',c.execute(q,(albumid,)).fetchall(),flush=True)
PYCHECK
adb shell am start -W -n "$pkg/com.mochyy.mochymusic.MainActivity" >/dev/null
sleep 8

dump() {
  timeout 25s adb shell uiautomator dump /sdcard/album-ui.xml >/dev/null
  timeout 10s adb exec-out cat /sdcard/album-ui.xml > album-order-evidence/current.xml
}
summary() {
  python3 - <<'PY'
import xml.etree.ElementTree as ET
for n in ET.parse('album-order-evidence/current.xml').iter('node'):
 t=n.get('text',''); d=n.get('content-desc','')
 if t or d: print(repr(t),repr(d),n.get('bounds'))
PY
}
node() {
  python3 - "$1" "$2" <<'PY'
import re,sys,xml.etree.ElementTree as ET
field,wanted=sys.argv[1:]
for n in ET.parse('album-order-evidence/current.xml').iter('node'):
 if n.get(field,'').casefold()==wanted.casefold():
  b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
  if len(b)==4 and b[2]-b[0]>=12 and b[3]-b[1]>=12 and 65 < (b[1]+b[3])//2 < 920:
   print((b[0]+b[2])//2,(b[1]+b[3])//2); break
else: raise SystemExit(1)
PY
}
tap() { dump; local xy; xy="$(node "$1" "$2")" || { echo "Missing $1=$2"; summary; return 1; }; echo "tap $1=$2 $xy"; adb shell input tap $xy; sleep 4; }
scroll_to() {
  for i in $(seq 1 12); do
    dump; node "$1" "$2" >/dev/null 2>&1 && return 0
    adb shell input swipe 320 760 320 240 500; sleep 1
  done
  echo "Cannot find $1=$2"; summary; return 1
}
dump; cp album-order-evidence/current.xml album-order-evidence/launch.xml
adb exec-out screencap -p > album-order-evidence/launch.png
summary
tap content-desc 'Your Library'
scroll_to text 'Albums'
tap text 'Albums'
scroll_to text 'ZZ Fixture Album'
dump; cp album-order-evidence/current.xml album-order-evidence/album-list.xml
adb exec-out screencap -p > album-order-evidence/album-list.png
tap text 'ZZ Fixture Album'
sleep 5
dump; cp album-order-evidence/current.xml album-order-evidence/album-top.xml
adb exec-out screencap -p > album-order-evidence/album-top.png
summary
# Move past the album header so all three titles are visible together.
adb shell input swipe 320 770 320 230 550; sleep 3
dump; cp album-order-evidence/current.xml album-order-evidence/album-songs.xml
adb exec-out screencap -p > album-order-evidence/album-songs.png
summary
python3 - <<'PY'
import re,xml.etree.ElementTree as ET
nodes=[]
for n in ET.parse('album-order-evidence/album-songs.xml').iter('node'):
 t=n.get('text','')
 if re.fullmatch(r'ZZ Fixture Track 0[123]',t):
  b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
  if len(b)==4 and b[3]>b[1]: nodes.append(((b[1]+b[3])//2,t))
nodes.sort(); print('VISIBLE ORDER:',nodes)
assert len(nodes)==3, 'Could not see all three fixture titles; order is inconclusive'
expected=[f'ZZ Fixture Track 0{x}' for x in [1,2,3]]
assert [t for _,t in nodes] == expected, f'Expected {expected}, got {nodes}'
print('FIXED UI SHOWS CORRECT TRACK ORDER')
PY
