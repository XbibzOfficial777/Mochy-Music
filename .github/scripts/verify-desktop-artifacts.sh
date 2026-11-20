#!/usr/bin/env bash
# Verify that electron-builder produced a real installer for the requested platform.
set -euo pipefail
directory="${1:?missing artifact directory}"
platform="${2:?missing platform}"
arch="${3:?missing architecture}"
cd "$directory"
shopt -s nullglob
case "$platform" in
win32)
    files=(*.exe)
    ((${#files[@]} > 0)) || { echo 'No Windows installer found' >&2; exit 1; }
    for file in "${files[@]}"; do
        magic="$(head -c 2 "$file")"
        test "$magic" = 'MZ' || { echo "Not a Windows executable: $file" >&2; exit 1; }
    done
    ;;
darwin)
    files=(*.dmg *.zip)
    ((${#files[@]} > 0)) || { echo 'No macOS installer found' >&2; exit 1; }
    ;;
linux)
    files=(*.AppImage *.deb)
    ((${#files[@]} > 0)) || { echo 'No Linux installer found' >&2; exit 1; }
    for file in *.AppImage; do
        [[ -f "$file" ]] || continue
        [[ -x "$file" ]] || { echo "AppImage not executable: $file" >&2; exit 1; }
    done
    ;;
*) echo "Unknown platform: $platform" >&2; exit 1 ;;
esac
printf 'Verified %s %s installer(s):\n' "$platform" "$arch"
printf '  %s\n' "${files[@]}"
