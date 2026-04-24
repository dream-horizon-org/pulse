#!/usr/bin/env bash
# Flashlight Metrics Benchmark Script
# Each run is an independent cold start — Flashlight kills the app before running.
# One row per run in the CSV.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"
CSV_FILE="$SCRIPT_DIR/flashlight_metrics_results.csv"
FLOW_FILE="$SCRIPT_DIR/flows/intensive_flow.yaml"

source "$SCRIPT_DIR/../common.sh"

NUM_RUNS="${1:-10}"

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

echo "Flashlight Metrics Benchmark Script"
echo "===================================="
echo "Runs: $NUM_RUNS"
echo "Flow: $FLOW_FILE"
echo "CSV Output: $CSV_FILE"
echo ""

if ! verify_device_connected; then
  exit 1
fi

mkdir -p "$RESULTS_DIR"

# Write CSV header (overwrite mode)
cat > "$CSV_FILE" <<'CSVHEADER'
run_number,fps_avg,fps_min,ram_avg_mb,ram_peak_mb,cpu_avg_pct,cpu_peak_pct,duration_ms,score,status,timestamp
CSVHEADER

SUCCESS_SCORES=()
SUCCESS_FPS=()
SUCCESS_RAM=()
SUCCESS_CPU=()

for ((run = 1; run <= NUM_RUNS; run++)); do
  echo "[Run $run/$NUM_RUNS] Starting Flashlight test..."

  RESULTS_FILE="$RESULTS_DIR/flashlight_metrics_$run.json"

  # Flashlight kills the app before each run by default (no --skipRestart)
  if flashlight test \
    --bundleId com.example.expoexample \
    --testCommand "maestro test $FLOW_FILE" \
    --resultsFilePath "$RESULTS_FILE" \
    --iterationCount 1 \
    2>&1; then

    # Parse the single iteration from JSON
    METRIC=$(jq -c '.iterations[0] | {
      duration: .time,
      status: .status,
      fps_avg: (if (.measures | length) > 0 then (([.measures[].fps] | add) / ([.measures[].fps] | length)) else 0 end),
      fps_min: (if (.measures | length) > 0 then ([.measures[].fps] | min) else 0 end),
      ram_avg: (if (.measures | length) > 0 then (([.measures[].ram] | add) / ([.measures[].ram] | length)) else 0 end),
      ram_peak: (if (.measures | length) > 0 then ([.measures[].ram] | max) else 0 end),
      cpu_avg: (if (.measures | length) > 0 then ((([.measures[] | .cpu.perName | to_entries | map(.value) | add] | add) / (([.measures[] | .cpu.perName | to_entries | map(.value) | add] | length) // 1)) // 0) else 0 end),
      cpu_peak: (if (.measures | length) > 0 then ([.measures[] | .cpu.perName | to_entries | map(.value) | add] | max) else 0 end)
    }' "$RESULTS_FILE")

    DURATION=$(echo "$METRIC" | jq -r '.duration')
    STATUS=$(echo "$METRIC"   | jq -r '.status')
    FPS_AVG=$(echo "$METRIC"  | jq -r '.fps_avg')
    FPS_MIN=$(echo "$METRIC"  | jq -r '.fps_min')
    RAM_AVG=$(echo "$METRIC"  | jq -r '.ram_avg')
    RAM_PEAK=$(echo "$METRIC" | jq -r '.ram_peak')
    CPU_AVG=$(echo "$METRIC"  | jq -r '.cpu_avg')
    CPU_PEAK=$(echo "$METRIC" | jq -r '.cpu_peak')

    FPS_AVG=$([ "$FPS_AVG"   = "null" ] && echo "N/A" || echo "$FPS_AVG")
    FPS_MIN=$([ "$FPS_MIN"   = "null" ] && echo "N/A" || echo "$FPS_MIN")
    RAM_AVG=$([ "$RAM_AVG"   = "null" ] && echo "N/A" || echo "$RAM_AVG")
    RAM_PEAK=$([ "$RAM_PEAK" = "null" ] && echo "N/A" || echo "$RAM_PEAK")
    CPU_AVG=$([ "$CPU_AVG"   = "null" ] && echo "N/A" || echo "$CPU_AVG")
    CPU_PEAK=$([ "$CPU_PEAK" = "null" ] && echo "N/A" || echo "$CPU_PEAK")
    DURATION=$([ "$DURATION" = "null" ] && echo "N/A" || printf "%.0f" "$DURATION")

    SCORE="N/A"
    if [ "$STATUS" = "SUCCESS" ] && [ "$CPU_AVG" != "N/A" ] && [ "$FPS_AVG" != "N/A" ]; then
      SCORE=$(awk -v cpu="$CPU_AVG" -v fps="$FPS_AVG" 'BEGIN {
        cpu_score = -0.31666666666667 * cpu + 116
        if (cpu_score < 0) cpu_score = 0
        if (cpu_score > 100) cpu_score = 100
        fps_score = fps * 100 / 60
        if (fps_score < 0) fps_score = 0
        if (fps_score > 100) fps_score = 100
        score = (cpu_score + fps_score) / 2
        if (score < 0) score = 0
        printf "%.0f\n", score
      }')
      SUCCESS_SCORES+=("$SCORE")
      SUCCESS_FPS+=("$FPS_AVG")
      SUCCESS_RAM+=("$RAM_AVG")
      SUCCESS_CPU+=("$CPU_AVG")
    fi

    ROW_TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
    FPS_AVG_VAL=$([ "$FPS_AVG"   = "N/A" ] && echo "N/A" || printf "%.2f" "$FPS_AVG")
    FPS_MIN_VAL=$([ "$FPS_MIN"   = "N/A" ] && echo "N/A" || printf "%.2f" "$FPS_MIN")
    RAM_AVG_VAL=$([ "$RAM_AVG"   = "N/A" ] && echo "N/A" || printf "%.2f" "$RAM_AVG")
    RAM_PEAK_VAL=$([ "$RAM_PEAK" = "N/A" ] && echo "N/A" || printf "%.2f" "$RAM_PEAK")
    CPU_AVG_VAL=$([ "$CPU_AVG"   = "N/A" ] && echo "N/A" || printf "%.2f" "$CPU_AVG")
    CPU_PEAK_VAL=$([ "$CPU_PEAK" = "N/A" ] && echo "N/A" || printf "%.2f" "$CPU_PEAK")

    echo "$run,$FPS_AVG_VAL,$FPS_MIN_VAL,$RAM_AVG_VAL,$RAM_PEAK_VAL,$CPU_AVG_VAL,$CPU_PEAK_VAL,$DURATION,$SCORE,$STATUS,$ROW_TIMESTAMP" >> "$CSV_FILE"
    echo -e "${GREEN}[Run $run/$NUM_RUNS] SUCCESS${NC}"

  else
    echo -e "${RED}[Run $run/$NUM_RUNS] FAILED${NC}"
    ROW_TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
    echo "$run,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,FAILED,$ROW_TIMESTAMP" >> "$CSV_FILE"
  fi
done

# Aggregates
if [ ${#SUCCESS_SCORES[@]} -gt 0 ]; then
  echo ""
  echo "Calculating aggregates from ${#SUCCESS_SCORES[@]} successful runs..."

  SCORE_SUM=0; FPS_SUM=0; RAM_SUM=0; CPU_SUM=0

  for v in "${SUCCESS_SCORES[@]}"; do SCORE_SUM=$(awk "BEGIN {print $SCORE_SUM + $v}"); done
  for v in "${SUCCESS_FPS[@]}";    do FPS_SUM=$(awk   "BEGIN {print $FPS_SUM   + $v}"); done
  for v in "${SUCCESS_RAM[@]}";    do RAM_SUM=$(awk   "BEGIN {print $RAM_SUM   + $v}"); done
  for v in "${SUCCESS_CPU[@]}";    do CPU_SUM=$(awk   "BEGIN {print $CPU_SUM   + $v}"); done

  COUNT=${#SUCCESS_SCORES[@]}
  AVG_SCORE=$(awk "BEGIN {printf \"%.0f\", $SCORE_SUM / $COUNT}")
  AVG_FPS=$(awk "BEGIN {printf \"%.2f\", $FPS_SUM / $COUNT}")
  AVG_RAM=$(awk "BEGIN {printf \"%.2f\", $RAM_SUM / $COUNT}")
  AVG_CPU=$(awk "BEGIN {printf \"%.2f\", $CPU_SUM / $COUNT}")
  {
    echo ""
    echo "## Aggregate Statistics"
    echo "metric,value"
    echo "total_runs,$NUM_RUNS"
    echo "successful_runs,$COUNT"
    echo "mean_score,$AVG_SCORE"
    echo "mean_fps_avg,$AVG_FPS"
    echo "mean_ram_avg_mb,$AVG_RAM"
    echo "mean_cpu_avg_pct,$AVG_CPU"
  } >> "$CSV_FILE"
else
  echo -e "${RED}No successful runs to aggregate${NC}"
fi

echo ""
echo -e "${GREEN}Benchmark complete!${NC}"
echo "Results: $CSV_FILE"
