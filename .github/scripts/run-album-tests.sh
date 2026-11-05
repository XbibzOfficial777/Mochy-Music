#!/usr/bin/env bash
set -euo pipefail
stage="${1:?baseline or fixed}"
mkdir -p album-ci-results
app="$(find app/build/outputs/apk/universalFoss/debug -name '*.apk' -type f -print -quit)"
testapk="$(find app/build/outputs/apk/androidTest/universalFoss/debug -name '*.apk' -type f -print -quit)"
test -n "$app" && test -n "$testapk"
adb install -r "$app"
adb install -r "$testapk"
adb shell am instrument -w \
  -e class com.mochyy.mochymusic.db.AlbumOrderTest \
  com.mochyy.mochymusic.debug.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "album-ci-results/$stage.txt"
if [[ "$stage" == baseline ]]; then
  grep -q 'FAILURES!!!' "album-ci-results/$stage.txt"
  grep -q 'albumPageAndPlaybackQueueFollowTrackNumbers' "album-ci-results/$stage.txt"
  grep -q 'mediaLibraryAndAlbumMenuFollowTrackNumbers' "album-ci-results/$stage.txt"
elif [[ "$stage" == fixed* ]]; then
  grep -Eq 'OK \(6 tests?\)' "album-ci-results/$stage.txt"
  if grep -q 'FAILURES!!!' "album-ci-results/$stage.txt"; then exit 1; fi
else
  echo "Unknown test stage $stage" >&2; exit 1
fi
