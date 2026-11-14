#!/usr/bin/env bash
# API 37 ships as platforms;android-37.0. There is no platforms;android-37.
# AGP still looks for platforms/android-37, so we symlink after install.
set -euo pipefail
sdkmanager 'platforms;android-37.0' 'platforms;android-36' 'build-tools;36.0.0' 'ndk;27.0.12077973' 'cmake;3.22.1'
root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -n "$root" ] && [ -d "$root/platforms/android-37.0" ] && [ ! -e "$root/platforms/android-37" ]; then
  ln -s android-37.0 "$root/platforms/android-37"
fi
