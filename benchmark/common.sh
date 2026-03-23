#!/usr/bin/env bash
# Shared helpers for startup benchmarks (sourced by other scripts).

# Poll logcat this many times (default 36 × 0.25s ≈ 9s) waiting for Pulse init lines.
# Override: export WAIT_FOR_PULSE_INIT_MAX_POLLS=48
WAIT_FOR_PULSE_INIT_MAX_POLLS="${WAIT_FOR_PULSE_INIT_MAX_POLLS:-${WAIT_FOR_T1_MAX_POLLS:-36}}"
# Sleep between logcat polls (seconds)
WAIT_FOR_PULSE_INIT_POLL_SEC="${WAIT_FOR_PULSE_INIT_POLL_SEC:-${WAIT_FOR_T1_POLL_SEC:-0.25}}"

# Poll logcat until PULSE_INIT_T0_MS, PULSE_INIT_T1_MS, and PULSE_INIT_DURATION_MS are all present
# (see OtelDemoApplication.kt) or timeout.
# Prints: "T0 T1 DURATION" on stdout; exit 0 on success, 1 if timeout.
poll_startup_from_logcat() {
  local LOGS T0 T1 DUR _i
  for ((_i = 1; _i <= WAIT_FOR_PULSE_INIT_MAX_POLLS; _i++)); do
    LOGS=$(adb logcat -d -s "otel.demo:D" 2>/dev/null)
    T0=$(echo "$LOGS" | grep "PULSE_INIT_T0_MS=" | tail -1 | sed -n 's/.*PULSE_INIT_T0_MS=\([0-9][0-9]*\).*/\1/p')
    T1=$(echo "$LOGS" | grep "PULSE_INIT_T1_MS=" | tail -1 | sed -n 's/.*PULSE_INIT_T1_MS=\([0-9][0-9]*\).*/\1/p')
    DUR=$(echo "$LOGS" | grep "PULSE_INIT_DURATION_MS=" | tail -1 | sed -n 's/.*PULSE_INIT_DURATION_MS=\([0-9][0-9]*\).*/\1/p')
    if [ -n "$T0" ] && [ -n "$T1" ] && [ -n "$DUR" ]; then
      printf '%s %s %s\n' "$T0" "$T1" "$DUR"
      return 0
    fi
    sleep "$WAIT_FOR_PULSE_INIT_POLL_SEC"
  done
  return 1
}
