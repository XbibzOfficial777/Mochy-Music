#!/usr/bin/env bash
# Bound adb calls: adb shell can otherwise wait forever if an emulator drops offline.
ADB_BIN="$(type -P adb)"
adb() {
  local limit=60
  case "${1:-}" in
    get-state|devices|reconnect) limit=5 ;;
    install) limit=180 ;;
    exec-out) limit=45 ;;
    shell)
      limit=30
      case "${2:-}" in
        getprop) limit=10 ;;
        input) limit=20 ;;
        am) limit=90 ;;
        uiautomator|dumpsys) limit=60 ;;
      esac
      ;;
  esac
  timeout --foreground --signal=TERM --kill-after=5s "${limit}s" "$ADB_BIN" "$@"
}

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
