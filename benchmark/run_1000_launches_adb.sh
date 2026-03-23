#!/bin/bash

# Simple ADB script to run NUM_RUNS app launches (default 100) and capture startup times
# This assumes APK is already installed

set -e

# Repo root–agnostic (works on any machine)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/common.sh"

APP_ID="io.opentelemetry.android.demo"
ACTIVITY=".MainActivity"
NUM_RUNS=100
BENCHMARK_DIR="$SCRIPT_DIR"
# Wait after `am start` before polling logcat (default 3s). Override: export POST_LAUNCH_DELAY_SEC=0.5
POST_LAUNCH_DELAY_SEC="${POST_LAUNCH_DELAY_SEC:-6}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

mkdir -p "$BENCHMARK_DIR"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}ADB Startup Time Benchmark - ${NUM_RUNS} Runs${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Step 1: Check device
echo -e "${YELLOW}[Step 1] Checking device connection...${NC}"
if ! adb devices | grep -q "device$"; then
  echo -e "${RED}ERROR: No device/emulator connected${NC}"
  exit 1
fi
DEVICE=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
echo -e "${GREEN}✓ Device: $DEVICE${NC}"
echo ""

# Step 2: Check app is installed
echo -e "${YELLOW}[Step 2] Verifying app is installed...${NC}"
if ! adb shell pm list packages | grep -q "$APP_ID"; then
  echo -e "${RED}ERROR: App not installed. Install with: adb install <apk-path>${NC}"
  exit 1
fi
echo -e "${GREEN}✓ App installed: $APP_ID${NC}"
echo ""

# Step 3: Prepare CSV
CSV_FILE="$BENCHMARK_DIR/startup_times_${NUM_RUNS}runs_adb.csv"
echo "run_number,pulse_init_t0_ms,pulse_init_t1_ms,pulse_init_duration_ms,timestamp" > "$CSV_FILE"
echo -e "${GREEN}✓ CSV file created: $CSV_FILE${NC}"
echo ""

# Step 4: Run launches
echo -e "${YELLOW}[Step 3] Running $NUM_RUNS launches...${NC}"
echo "Progress:"
echo ""

START_BENCHMARK=$(date +%s)

for i in $(seq 1 $NUM_RUNS); do
  # Progress every 10 runs (for 100-run default you get updates at 10, 20, …)
  if [ $((i % 10)) -eq 0 ]; then
    ELAPSED=$(($(date +%s) - START_BENCHMARK))
    RATE=$(( i / ((ELAPSED + 1) / 60) ))  # runs per minute
    ETA=$(( (NUM_RUNS - i) / (RATE + 1) ))
    echo -ne "\r  Run $i/$NUM_RUNS (${ELAPSED}s elapsed, ~${ETA}min remaining, ${RATE} runs/min)"
  fi
  
  # Force stop — give the system time to tear down the process
  adb shell am force-stop "$APP_ID" 2>/dev/null || true
  sleep 0.5

  # Clear logcat twice (some devices need a beat before buffer is empty)
  adb logcat -c 2>/dev/null || true
  sleep 0.15
  adb logcat -c 2>/dev/null || true
  sleep 0.1

  # Start app
  adb shell am start "$APP_ID/$ACTIVITY" > /dev/null 2>&1

  sleep "$POST_LAUNCH_DELAY_SEC"

  # Poll logcat until PULSE_INIT_* (see common.sh / OtelDemoApplication.kt)
  TIMESTAMP=$(date +"%Y-%m-%d %H:%M:%S")
  if line=$(poll_startup_from_logcat); then
    read -r T0 T1 TOTAL <<< "$line"
    echo "$i,$T0,$T1,$TOTAL,$TIMESTAMP" >> "$CSV_FILE"
  else
    # Keep a row per iteration so CSV row count matches NUM_RUNS (-1 = poll timeout / no PULSE_INIT lines)
    echo "$i,-1,-1,-1,$TIMESTAMP" >> "$CSV_FILE"
    echo "  [warn] run $i: logcat poll timeout (no PULSE_INIT_* in time); CSV row uses -1" >&2
  fi

  sleep 0.2
done

echo -ne "\n"
echo ""
echo -e "${GREEN}✓ All $NUM_RUNS launches completed${NC}"
echo ""

# Step 5: Analyze
echo -e "${YELLOW}[Step 4] Analyzing data...${NC}"

STATS=$(awk -F',' 'NR>1 && $4 ~ /^[0-9]+$/ && $4 >= 0 {
  times[++n] = $4
  sum += $4
  if (n == 1 || $4 < min) min = $4
  if (n == 1 || $4 > max) max = $4
}
END {
  if (n == 0) exit 1
  avg = sum / n
  for (i = 1; i <= n; i++) {
    diff = times[i] - avg
    sum_sq += diff * diff
  }
  stddev = sqrt(sum_sq / n)
  printf "%.0f,%.0f,%.0f,%.0f\n", min, max, avg, stddev
}' "$CSV_FILE") || true

if [ -n "$STATS" ]; then
  IFS=',' read -r MIN MAX AVG STDDEV <<< "$STATS"
  
  echo ""
  echo -e "${BLUE}========================================${NC}"
  echo -e "${BLUE}RESULTS${NC}"
  echo -e "${BLUE}========================================${NC}"
  echo ""
  echo "Min:    ${MIN} ms"
  echo "Max:    ${MAX} ms"
  echo "Avg:    ${AVG} ms"
  echo "StdDev: ${STDDEV} ms"
  echo ""
  
  # Percentiles (exclude failed rows: pulse_init_duration_ms == -1)
  P50=$(awk -F',' 'NR>1 && $4 ~ /^[0-9]+$/ && $4 >= 0 {print $4}' "$CSV_FILE" | sort -n | awk '{a[i++]=$1} END {if (i > 0) print a[int(i*0.5)]}')
  P95=$(awk -F',' 'NR>1 && $4 ~ /^[0-9]+$/ && $4 >= 0 {print $4}' "$CSV_FILE" | sort -n | awk '{a[i++]=$1} END {if (i > 0) print a[int(i*0.95)]}')
  P99=$(awk -F',' 'NR>1 && $4 ~ /^[0-9]+$/ && $4 >= 0 {print $4}' "$CSV_FILE" | sort -n | awk '{a[i++]=$1} END {if (i > 0) print a[int(i*0.99)]}')
  
  echo "Percentiles:"
  echo "  P50 (median): ${P50} ms"
  echo "  P95: ${P95} ms"
  echo "  P99: ${P99} ms"
  echo ""
fi

# Google Sheets: one TSV — iteration, timestamp, duration, min, mean, median (p50)
SHEETS_TSV="$BENCHMARK_DIR/startup_times_${NUM_RUNS}runs_sheets.tsv"

write_pulse_init_sheets_paste_tsv "$CSV_FILE" "$SHEETS_TSV"

echo -e "${GREEN}✓ Sheets (paste at A1): ${SHEETS_TSV}${NC}"
echo "   Columns: iteration, timestamp, pulse_init_duration_ms, min, mean, median (p50)."
echo ""

# Generate report
REPORT_FILE="$BENCHMARK_DIR/STARTUP_BENCHMARK_REPORT_ADB.md"
cat > "$REPORT_FILE" << EOF
# Pulse Android Demo - Startup Benchmark Report (ADB)

**Date:** $(date)
**Branch:** $(cd "$REPO_ROOT" && git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)
**Commit:** $(cd "$REPO_ROOT" && git rev-parse --short HEAD 2>/dev/null || echo unknown)

## Test Setup

| Parameter | Value |
|-----------|-------|
| Runs | $NUM_RUNS |
| App | $APP_ID |
| Activity | $ACTIVITY |
| Device | $DEVICE |

## Results

| Metric | Value (ms) |
|--------|-----------|
| Min | $MIN |
| Max | $MAX |
| Average | $AVG |
| Std Dev | $STDDEV |
| P50 | $P50 |
| P95 | $P95 |
| P99 | $P99 |

## Data

CSV: \`$CSV_FILE\`

Sheets (tab-separated for copy-paste at A1): \`$SHEETS_TSV\` — iteration, timestamp, pulse_init_duration_ms, min, mean, median (p50).

View samples:
\`\`\`bash
head -10 "$CSV_FILE"
tail -5 "$CSV_FILE"
\`\`\`

---
Generated: $(date)
EOF

echo -e "${GREEN}✓ Report: $REPORT_FILE${NC}"
echo -e "${GREEN}✓ Data:   $CSV_FILE${NC}"
echo -e "${GREEN}✓ Sheets: $SHEETS_TSV${NC}"
echo ""
