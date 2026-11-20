#!/usr/bin/env bash
# A release requires one installer per desktop OS. Other files are optional.
set -euo pipefail
directory="${1:?missing artifact directory}"
version="${2:?missing release version}"
cd "$directory"
check() {
    local description="$1"
    shift
    local found=()
    for pattern in "$@"; do
        for file in $pattern; do [[ -f "$file" ]] && found+=("$file"); done
    done
    ((${#found[@]} > 0)) || { echo "Missing $description installer" >&2; exit 1; }
    printf 'Found %s: %s\n' "$description" "${found[*]}"
}
check Linux '*.AppImage' '*.deb'
check macOS '*.dmg'
check Windows '*.exe'
version_found=0
for file in *; do
    [[ -f "$file" ]] || continue
    if [[ "$file" == *"$version"* ]]; then version_found=1; fi
done
((version_found == 1)) || { echo "No artifact carries version $version" >&2; exit 1; }
printf 'Desktop release set verified for %s\n' "$version"
