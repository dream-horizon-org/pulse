#!/bin/bash

# Simple ADB script to run 1000 app launches and capture startup times
# This assumes APK is already installed

set -e

# Repo root–agnostic (works on any machine)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/common.sh"

APP_ID="io.opentelemetry.android.demo"
ACTIVITY=".MainActivity"
NUM_RUNS=1000
BENCHMARK_DIR="$SCRIPT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

mkdir -p "$BENCHMARK_DIR"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}ADB Startup Time Benchmark - 1000 Runs${NC}"
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
echo "run_number,t0_onCreate_ms,t1_firstFrame_ms,total_startup_ms,timestamp" > "$CSV_FILE"
echo -e "${GREEN}✓ CSV file created: $CSV_FILE${NC}"
echo ""

# Step 4: Run 1000 launches
echo -e "${YELLOW}[Step 3] Running $NUM_RUNS launches...${NC}"
echo "Progress:"
echo ""

START_BENCHMARK=$(date +%s)

for i in $(seq 1 $NUM_RUNS); do
  # Progress every 100 runs
  if [ $((i % 100)) -eq 0 ]; then
    ELAPSED=$(($(date +%s) - START_BENCHMARK))
    RATE=$(( i / ((ELAPSED + 1) / 60) ))  # runs per minute
    ETA=$(( (NUM_RUNS - i) / (RATE + 1) ))
    echo -ne "\r  Run $i/$NUM_RUNS (${ELAPSED}s elapsed, ~${ETA}min remaining, ${RATE} runs/min)"
  fi
  
  # Force stop
  adb shell am force-stop "$APP_ID" 2>/dev/null || true
  sleep 0.3
  
  # Clear logcat
  adb logcat -c 2>/dev/null || true
  sleep 0.1
  
  # Start app
  adb shell am start "$APP_ID/$ACTIVITY" > /dev/null 2>&1
  
  # Poll logcat until t0+t1+total (see common.sh; waits up to ~9s for FrameMetrics / t1)
  if line=$(poll_startup_from_logcat); then
    read -r T0 T1 TOTAL <<< "$line"
    TIMESTAMP=$(date +"%Y-%m-%d %H:%M:%S")
    echo "$i,$T0,$T1,$TOTAL,$TIMESTAMP" >> "$CSV_FILE"
  fi
  
  sleep 0.2
done

echo -ne "\n"
echo ""
echo -e "${GREEN}✓ All $NUM_RUNS launches completed${NC}"
echo ""

# Step 5: Analyze
echo -e "${YELLOW}[Step 4] Analyzing data...${NC}"

STATS=$(awk -F',' 'NR>1 {
  times[NR] = $4
  sum += $4
  if (NR == 2 || $4 < min) min = $4
  if (NR == 2 || $4 > max) max = $4
}
END {
  n = NR - 1
  if (n == 0) exit 1
  avg = sum / n
  
  for (i in times) {
    diff = times[i] - avg
    sum_sq += diff * diff
  }
  stddev = sqrt(sum_sq / n)
  
  printf "%.0f,%.0f,%.0f,%.0f\n", min, max, avg, stddev
}' "$CSV_FILE")

if [ $? -eq 0 ]; then
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
  
  # Percentiles
  P50=$(awk -F',' 'NR>1 {print $4}' "$CSV_FILE" | sort -n | awk '{a[i++]=$1} END {print a[int(i*0.5)]}')
  P95=$(awk -F',' 'NR>1 {print $4}' "$CSV_FILE" | sort -n | awk '{a[i++]=$1} END {print a[int(i*0.95)]}')
  P99=$(awk -F',' 'NR>1 {print $4}' "$CSV_FILE" | sort -n | awk '{a[i++]=$1} END {print a[int(i*0.99)]}')
  
  echo "Percentiles:"
  echo "  P50 (median): ${P50} ms"
  echo "  P95: ${P95} ms"
  echo "  P99: ${P99} ms"
  echo ""
fi

# Generate report
REPORT_FILE="$BENCHMARK_DIR/STARTUP_BENCHMARK_REPORT_ADB.md"
cat > "$REPORT_FILE" << EOF
# Pulse Android Demo - Startup Benchmark Report (ADB)

**Date:** $(date)
**Branch:** $(cd /Users/shruti-pathak/Code/pulse && git rev-parse --abbrev-ref HEAD)
**Commit:** $(cd /Users/shruti-pathak/Code/pulse && git rev-parse --short HEAD)

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
echo ""
