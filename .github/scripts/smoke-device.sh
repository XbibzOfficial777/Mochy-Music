#!/usr/bin/env bash
# Wait until adb reports a booted emulator. Used by smoke scripts; does not skip tests.
wait_for_emulator() {
  local tries="${1:-45}"
  local i state boot
  for i in $(seq 1 "$tries"); do
    state="$(adb get-state 2>/dev/null | tr -d '\r' || true)"
    if [ "$state" = device ]; then
      boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
      if [ "$boot" = 1 ]; then
        return 0
      fi
    fi
    sleep 2
  done
  echo "Emulator is not online after ${tries} checks (state=${state:-unknown})." >&2
  adb devices -l >&2 || true
  return 1
}
