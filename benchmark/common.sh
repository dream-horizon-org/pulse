#!/usr/bin/env bash
# Shared helpers for startup benchmarks (sourced by other scripts).

# Poll logcat this many times (default 72 × 0.25s ≈ 18s) waiting for Pulse init lines.
# Long runs / warm devices may need more than ~9s; raise if you still see -1 rows in CSV.
# Override: export WAIT_FOR_PULSE_INIT_MAX_POLLS=96
WAIT_FOR_PULSE_INIT_MAX_POLLS="${WAIT_FOR_PULSE_INIT_MAX_POLLS:-${WAIT_FOR_T1_MAX_POLLS:-72}}"
# Sleep between logcat polls (seconds)
WAIT_FOR_PULSE_INIT_POLL_SEC="${WAIT_FOR_PULSE_INIT_POLL_SEC:-${WAIT_FOR_T1_POLL_SEC:-0.25}}"

# Poll logcat until PULSE_INIT_T0_MS, PULSE_INIT_T1_MS, and PULSE_INIT_DURATION_MS are all present
# (see OtelDemoApplication.kt) or timeout.
#
# Do NOT use `adb logcat -d -s "otel.demo:D"` — on many devices/ADB versions the tag:priority filter
# drops Debug lines or behaves inconsistently. Dump the buffer and grep for the message text instead.
# Prints: "T0 T1 DURATION" on stdout; exit 0 on success, 1 if timeout.
poll_startup_from_logcat() {
  local LOGS T0 T1 DUR _i
  for ((_i = 1; _i <= WAIT_FOR_PULSE_INIT_MAX_POLLS; _i++)); do
    # Prefer full buffer; fall back if -b all unsupported
    LOGS=$(adb logcat -d -b all 2>/dev/null || adb logcat -d 2>/dev/null)
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

# CSV column 4 = pulse_init_duration_ms (see run_1000_launches_adb.sh).
# Writes a 2-column TSV for Google Sheets: metric <tab> value
# Rows: count (N), min, max, mean, median (p50). Skips invalid / -1 rows.
write_pulse_init_sheets_summary_tsv() {
  local csv_file=$1
  local out_file=$2
  local STATS COUNT MIN MAX AVG P50

  COUNT=$(awk -F',' 'NR>1 && $4 ~ /^[0-9]+$/ && $4 >= 0 {c++} END {print c+0}' "$csv_file")

  STATS=$(awk -F',' 'NR>1 && $4 ~ /^[0-9]+$/ && $4 >= 0 {
    times[++n] = $4
    sum += $4
    if (n == 1 || $4 < min) min = $4
    if (n == 1 || $4 > max) max = $4
  }
  END {
    if (n == 0) exit 1
    avg = sum / n
    printf "%.0f,%.0f,%.0f\n", min, max, avg
  }' "$csv_file") || true

  MIN="" MAX="" AVG=""
  if [ -n "$STATS" ]; then
    IFS=',' read -r MIN MAX AVG <<< "$STATS" || true
  fi

  P50=$(awk -F',' 'NR>1 && $4 ~ /^[0-9]+$/ && $4 >= 0 {print $4}' "$csv_file" | sort -n | awk '{a[i++]=$1} END {if (i > 0) print a[int(i*0.5)]}')

  {
    echo -e "metric\tvalue"
    echo -e "count (N)\t${COUNT}"
    echo -e "min\t${MIN:-}"
    echo -e "max\t${MAX:-}"
    echo -e "mean\t${AVG:-}"
    echo -e "median (p50)\t${P50:-}"
  } > "$out_file"
}

# One Google Sheets paste: per-run columns + aggregate min / mean / median (same on every row).
# CSV columns: run_number, t0, t1, pulse_init_duration_ms, timestamp
# Use out_file=- to write to stdout.
write_pulse_init_sheets_paste_tsv() {
  local csv_file=$1
  local out_file=$2
  local STATS MIN MAX AVG P50 target

  STATS=$(awk -F',' 'NR>1 && $4 ~ /^[0-9]+$/ && $4 >= 0 {
    times[++n] = $4
    sum += $4
    if (n == 1 || $4 < min) min = $4
    if (n == 1 || $4 > max) max = $4
  }
  END {
    if (n == 0) exit 1
    avg = sum / n
    printf "%.0f,%.0f,%.0f\n", min, max, avg
  }' "$csv_file") || true

  MIN="" MAX="" AVG=""
  if [ -n "$STATS" ]; then
    IFS=',' read -r MIN MAX AVG <<< "$STATS" || true
  fi

  P50=$(awk -F',' 'NR>1 && $4 ~ /^[0-9]+$/ && $4 >= 0 {print $4}' "$csv_file" | sort -n | awk '{a[i++]=$1} END {if (i > 0) print a[int(i*0.5)]}')

  if [ "$out_file" = "-" ]; then
    target=/dev/stdout
  else
    target="$out_file"
  fi

  awk -F',' -v min="${MIN:-}" -v mean="${AVG:-}" -v med="${P50:-}" 'BEGIN {
    OFS="\t"
    print "iteration", "timestamp", "pulse_init_duration_ms", "min", "mean", "median (p50)"
  }
  NR > 1 {
    print $1, $5, $4, min, mean, med
  }' "$csv_file" > "$target"
}

# Verify that a device/emulator is connected and reachable.
# Exit 0 if device found, 1 otherwise.
verify_device_connected() {
  local devices
  devices=$(adb devices | grep -E "device$" | wc -l)
  if [ "$devices" -eq 0 ]; then
    echo "ERROR: No connected devices found. Please connect a device or emulator and try again."
    return 1
  fi
  return 0
}

# Clear the logcat buffer to prevent stale markers.
# Stale PULSE_INIT_* markers are the #1 cause of incorrect t0/t1 readings.
cleanup_old_logcat() {
  adb logcat -c
}
