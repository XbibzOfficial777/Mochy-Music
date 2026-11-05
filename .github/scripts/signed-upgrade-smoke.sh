#!/usr/bin/env bash
# Run only on an ephemeral rooted Google APIs emulator, with exact verified signed APK artifacts.
set -euo pipefail
: "${TARGET_ABI:?}" "${RUNTIME_ABI:?}" "${OLD_ABI:?}"
package=com.mochyy.mochymusic
main="$package/com.mochyy.mochymusic.MainActivity"
export APP_PACKAGE="$package"
mapfile -t apks < <(find candidate-apk -maxdepth 1 -name '*.apk' -type f)
test "${#apks[@]}" -eq 1 && test -s old-apk/xbibz6.apk
cleanup() {
  adb logcat -d -v threadtime > upgrade-logcat.txt || true
  adb exec-out screencap -p > upgrade-screen.png || true
  adb exec-out cat /sdcard/upgrade-window.xml > upgrade-ui.xml 2>/dev/null || true
}
trap cleanup EXIT
printf 'Device ABIs: '; adb shell getprop ro.product.cpu.abilist | tr -d '\r'
adb shell getprop ro.product.cpu.abilist | tr -d '\r' | tr ',' '\n' | grep -Fx "$RUNTIME_ABI"
adb root >/dev/null; adb wait-for-device
adb install -r old-apk/xbibz6.apk
adb shell dumpsys package "$package" | tr -d '\r' > upgrade-package-before.txt
grep -F "primaryCpuAbi=$RUNTIME_ABI" upgrade-package-before.txt
grep -E 'versionCode=79([[:space:]]|$)' upgrade-package-before.txt
grep -F 'versionName=8.2.3-xbibz.6' upgrade-package-before.txt
adb shell am force-stop "$package"
python3 .github/scripts/seed_and_check_preferences.py seed upgrade-seed.pb
adb push upgrade-seed.pb /data/local/tmp/upgrade-seed.pb >/dev/null
appdir="/data/user/0/$package"
uid="$(adb shell stat -c %u "$appdir" | tr -d '\r')"
gid="$(adb shell stat -c %g "$appdir" | tr -d '\r')"
[[ "$uid" =~ ^[0-9]+$ && "$gid" =~ ^[0-9]+$ ]]
adb shell "mkdir -p $appdir/files/datastore $appdir/no_backup && \
  cp /data/local/tmp/upgrade-seed.pb $appdir/files/datastore/settings.preferences_pb && \
  touch $appdir/no_backup/setup_wizard_completed && \
  chown $uid:$gid $appdir/files && chown -R $uid:$gid $appdir/files/datastore $appdir/no_backup && \
  chmod 700 $appdir/files/datastore $appdir/no_backup && \
  chmod 600 $appdir/files/datastore/settings.preferences_pb && restorecon -R $appdir/files $appdir/no_backup"
adb install -r "${apks[0]}"
adb shell dumpsys package "$package" | tr -d '\r' > upgrade-package-after.txt
grep -F "primaryCpuAbi=$RUNTIME_ABI" upgrade-package-after.txt
grep -E 'versionCode=80([[:space:]]|$)' upgrade-package-after.txt
grep -F 'versionName=8.3.2-xbibz.7' upgrade-package-after.txt
adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb logcat -c
adb shell am start -W -n "$main" | tee upgrade-start.txt
grep -q 'Status: ok' upgrade-start.txt
sleep 30
adb shell pidof "$package" | grep -q '[0-9]'
adb shell uiautomator dump /sdcard/upgrade-window.xml >/dev/null
adb shell am force-stop "$package"
adb pull "$appdir/files/datastore/settings.preferences_pb" upgrade-after.pb >/dev/null
python3 .github/scripts/seed_and_check_preferences.py check upgrade-after.pb
# Migration is one-time and the user's choices survive a cold restart too.
adb shell am start -W -n "$main" | tee upgrade-restart.txt
grep -q 'Status: ok' upgrade-restart.txt
sleep 15
adb shell pidof "$package" | grep -q '[0-9]'
adb shell am force-stop "$package"
adb pull "$appdir/files/datastore/settings.preferences_pb" upgrade-restarted.pb >/dev/null
python3 .github/scripts/seed_and_check_preferences.py check upgrade-restarted.pb
adb logcat -d -v threadtime > upgrade-logcat.txt
python3 - <<'PY'
from pathlib import Path
import os
lines = Path('upgrade-logcat.txt').read_text(errors='replace').splitlines()
package = os.environ['APP_PACKAGE']
for i, line in enumerate(lines):
    if 'FATAL EXCEPTION' in line and any(f'Process: {package}' in x for x in lines[i:i+15]):
        print('\n'.join(lines[i:i+20])); raise SystemExit('App crash during signed in-place upgrade')
print('Signed .6 -> .7 in-place upgrade, preferences migration and cold restart passed.')
PY
