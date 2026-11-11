#!/usr/bin/env bash
# One entry point for the emulator job. Retries the same tests once if adb drops.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=smoke-device.sh
source "$here/smoke-device.sh"

run_smoke() {
  bash "$here/smoke-launch.sh"
  bash "$here/smoke-fixtures.sh"
  bash "$here/smoke-latest.sh"
}

if run_smoke; then
  exit 0
fi
echo 'Smoke failed; waiting for the emulator and running the same tests once more.'
wait_for_emulator 45 || true
run_smoke
