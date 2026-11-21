#!/usr/bin/env bash
# One entry point for the emulator job. Retries the same tests once if adb drops.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=smoke-device.sh
source "$here/smoke-device.sh"

run_smoke() {
  # `set -e` is suppressed inside a function used as an if condition, so propagate
  # each child failure explicitly instead of accidentally masking it with a later pass.
  bash "$here/smoke-launch.sh" || return $?
  bash "$here/smoke-fixtures.sh" || return $?
  bash "$here/smoke-latest.sh" || return $?
}

if run_smoke; then
  exit 0
fi
echo 'Smoke failed; reconnecting and waiting for the emulator before one retry.'
adb reconnect || true
wait_for_emulator 45 || true
run_smoke
