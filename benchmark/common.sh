#!/usr/bin/env bash
# Shared helpers for startup benchmarks (sourced by other scripts).

# Poll logcat this many times (default 36 × 0.25s ≈ 9s) waiting for t1.
# Override: export WAIT_FOR_T1_MAX_POLLS=48
WAIT_FOR_T1_MAX_POLLS="${WAIT_FOR_T1_MAX_POLLS:-36}"
# Sleep between logcat polls (seconds)
WAIT_FOR_T1_POLL_SEC="${WAIT_FOR_T1_POLL_SEC:-0.25}"

# Poll logcat until STARTUP_T0/T1/TOTAL are all present or timeout.
# FrameMetrics (t1) can arrive after onCreate; give the UI time to produce a frame.
# Prints: "T0 T1 TOTAL" on stdout; exit 0 on success, 1 if timeout.
poll_startup_from_logcat() {
  local LOGS T0 T1 TOTAL _i
  for ((_i = 1; _i <= WAIT_FOR_T1_MAX_POLLS; _i++)); do
    LOGS=$(adb logcat -d -s "otel.demo:D" 2>/dev/null)
    T0=$(echo "$LOGS" | grep "STARTUP_T0_MS=" | tail -1 | sed -n 's/.*STARTUP_T0_MS=\([0-9][0-9]*\).*/\1/p')
    T1=$(echo "$LOGS" | grep "STARTUP_T1_MS=" | tail -1 | sed -n 's/.*STARTUP_T1_MS=\([0-9][0-9]*\).*/\1/p')
    TOTAL=$(echo "$LOGS" | grep "STARTUP_TOTAL_MS=" | tail -1 | sed -n 's/.*STARTUP_TOTAL_MS=\([0-9][0-9]*\).*/\1/p')
    if [ -n "$T0" ] && [ -n "$T1" ] && [ -n "$TOTAL" ]; then
      printf '%s %s %s\n' "$T0" "$T1" "$TOTAL"
      return 0
    fi
    sleep "$WAIT_FOR_T1_POLL_SEC"
  done
  return 1
}
