#!/usr/bin/env bash

################################################################################
# Pulse Init Startup Timing Benchmark for Expo Android
#
# Single control script: for each run it —
#   1. Kills the app + clears logcat
#   2. Runs Maestro flow (via adb)
#   3. Reads PULSE_INIT_* startup markers from logcat
#   4. Appends one row to master CSV
#
# After all runs, aggregate stats are appended to the same CSV.
#
# Maestro flow (default: flows/scroll_nav.yaml). Set MAESTRO_FLOW to override.
#
# Usage:
#   ./pulse-init-benchmark.sh [NUM_RUNS]
#
# Examples:
#   ./pulse-init-benchmark.sh            # 10 runs
#   ./pulse-init-benchmark.sh 100        # 100 runs
#
# Output:
#   pulse_init_results_<N>runs.csv       — one row per run (startup timing)
#   pulse_init_benchmark_<ts>.log        — full run log
#
# Master CSV columns:
#   run_number, pulse_init_t0_ms, pulse_init_t1_ms, pulse_init_duration_ms, timestamp
################################################################################

set -euo pipefail

################################################################################
# Config
################################################################################

PACKAGE_NAME="com.example.expoexample"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMMON_SH="${SCRIPT_DIR}/../common.sh"

NUM_RUNS="${1:-10}"

TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
MASTER_CSV="${SCRIPT_DIR}/pulse_init_results_${NUM_RUNS}runs.csv"
LOG_FILE="${SCRIPT_DIR}/pulse_init_benchmark_${TIMESTAMP}.log"
# Lightweight Maestro flow (home → categories); keeps runs fast for startup timing only.
# Override: MAESTRO_FLOW=/path/to/flow.yaml ./pulse-init-benchmark.sh 20
MAESTRO_FLOW="${MAESTRO_FLOW:-${SCRIPT_DIR}/flows/scroll_nav.yaml}"
START_TIME=$(date +%s)

if [ ! -f "$COMMON_SH" ]; then
    echo "ERROR: common.sh not found at $COMMON_SH"
    exit 1
fi
source "$COMMON_SH"

# Global aggregation arrays
declare -a STARTUP_DURATIONS=()
SUCCESSFUL_RUNS=0
FAILED_RUNS=0

################################################################################
# Colors
################################################################################

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

################################################################################
# Logging helpers
################################################################################

log_step()    { echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"; }
log_success() { echo -e "${GREEN}✓${NC} $1" | tee -a "$LOG_FILE"; }
log_warn()    { echo -e "${YELLOW}⚠${NC} $1" | tee -a "$LOG_FILE"; }
log_error()   { echo -e "${RED}✗ ERROR:${NC} $1" | tee -a "$LOG_FILE"; }

################################################################################
# Prerequisites
################################################################################

verify_prerequisites() {
    log_step "Checking prerequisites..."

    if ! verify_device_connected; then
        log_error "No connected Android device/emulator found"
        exit 1
    fi
    log_success "Device connected"

    if ! command -v adb &>/dev/null; then
        log_error "adb not found in PATH"
        exit 1
    fi
    log_success "adb available"

    if ! adb shell pm list packages 2>/dev/null | grep -q "$PACKAGE_NAME"; then
        log_error "App not installed: $PACKAGE_NAME. Install with: adb install -r <app-debug.apk>"
        exit 1
    fi
    log_success "App installed: $PACKAGE_NAME"
}

################################################################################
# Per-launch: kill app, run flow, parse logcat startup markers
################################################################################

run_launch() {
    local run_num=$1
    local logcat_file="${SCRIPT_DIR}/.logcat_tmp_${run_num}.txt"

    # ── 1. Kill app + clear logcat ────────────────────────────────────────────
    adb shell am force-stop "$PACKAGE_NAME" 2>/dev/null || true
    sleep 0.5
    cleanup_old_logcat   # adb logcat -c  (from common.sh)
    sleep 0.1
    cleanup_old_logcat   # double-clear for reliability

    # ── 2. Start background logcat capture ───────────────────────────────────
    adb logcat -v time > "$logcat_file" 2>/dev/null &
    local logcat_pid=$!
    sleep 0.2   # give logcat a moment to attach

    # ── 3. Launch the app with Maestro ──────────────────────────────────────
    #       The flow opens the app and triggers PULSE_INIT_* logging
    if ! maestro test "$MAESTRO_FLOW" \
        >> "$LOG_FILE" 2>&1; then
        kill "$logcat_pid" 2>/dev/null || true
        wait "$logcat_pid" 2>/dev/null || true
        rm -f "$logcat_file"
        log_warn "Run $run_num: Maestro flow failed"
        echo "$run_num,-1,-1,-1,$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$MASTER_CSV"
        FAILED_RUNS=$((FAILED_RUNS + 1))
        return
    fi

    # ── 4. Stop background logcat capture ────────────────────────────────────
    sleep 0.5   # flush any trailing lines
    kill "$logcat_pid" 2>/dev/null || true
    wait "$logcat_pid" 2>/dev/null || true

    # ── 5. Parse PULSE_INIT_* from captured logcat ──────────────────────────
    local t0 t1 dur
    t0=$(grep "PULSE_INIT_T0_MS=" "$logcat_file" | tail -1 | sed -n 's/.*PULSE_INIT_T0_MS=\([0-9][0-9]*\).*/\1/p')
    t1=$(grep "PULSE_INIT_T1_MS=" "$logcat_file" | tail -1 | sed -n 's/.*PULSE_INIT_T1_MS=\([0-9][0-9]*\).*/\1/p')
    dur=$(grep "PULSE_INIT_DURATION_MS=" "$logcat_file" | tail -1 | sed -n 's/.*PULSE_INIT_DURATION_MS=\([0-9][0-9]*\).*/\1/p')

    rm -f "$logcat_file"

    if [ -n "$t0" ] && [ -n "$t1" ] && [ -n "$dur" ]; then
        STARTUP_DURATIONS+=("$dur")
        SUCCESSFUL_RUNS=$((SUCCESSFUL_RUNS + 1))
    else
        log_warn "Run $run_num: startup timing not found in logcat"
        t0=-1; t1=-1; dur=-1
        FAILED_RUNS=$((FAILED_RUNS + 1))
    fi

    # ── 6. Append row to master CSV ──────────────────────────────────────────
    echo "$run_num,$t0,$t1,$dur,$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$MASTER_CSV"
}

################################################################################
# Aggregate stats appended at end of master CSV
################################################################################

append_aggregate_stats() {
    log_step "Calculating aggregate statistics..."

    local _min _max _avg _p50
    awk_stats() {
        # $1 = space-separated numbers; prints min,max,avg
        echo "$1" | tr ' ' '\n' | awk '
            { vals[++n]=$1; sum+=$1; if(n==1||$1<min)min=$1; if(n==1||$1>max)max=$1 }
            END { if(n>0) printf "%.1f,%.1f,%.1f\n", min, max, sum/n; else print "N/A,N/A,N/A" }
        '
    }
    p50() {
        echo "$1" | tr ' ' '\n' | sort -n | awk '{a[i++]=$1} END {if(i>0)print a[int(i*0.5)]; else print "N/A"}'
    }

    {
        echo ""
        echo "## Aggregate Statistics"
        echo "metric,value"
        echo "total_runs,$NUM_RUNS"
        echo "successful_runs,$SUCCESSFUL_RUNS"
        echo "failed_runs,$FAILED_RUNS"
    } >> "$MASTER_CSV"

    if [ ${#STARTUP_DURATIONS[@]} -gt 0 ]; then
        local nums="${STARTUP_DURATIONS[*]}"
        IFS=',' read -r _min _max _avg <<< "$(awk_stats "$nums")"
        _p50=$(p50 "$nums")
        {
            echo "startup_min_ms,$_min"
            echo "startup_max_ms,$_max"
            echo "startup_avg_ms,$_avg"
            echo "startup_p50_ms,$_p50"
        } >> "$MASTER_CSV"
    fi

    local end_time elapsed
    end_time=$(date +%s)
    elapsed=$(( end_time - START_TIME ))
    echo "total_duration_sec,$elapsed" >> "$MASTER_CSV"

    log_success "Stats appended to $MASTER_CSV"
}

################################################################################
# Print terminal summary
################################################################################

print_summary() {
    local end_time elapsed minutes seconds
    end_time=$(date +%s)
    elapsed=$(( end_time - START_TIME ))
    minutes=$(( elapsed / 60 ))
    seconds=$(( elapsed % 60 ))

    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║         Pulse Init Startup Timing Benchmark Complete       ║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${GREEN}Runs:${NC}  ${SUCCESSFUL_RUNS} ok / ${FAILED_RUNS} failed / ${NUM_RUNS} total"

    if [ ${#STARTUP_DURATIONS[@]} -gt 0 ]; then
        local min max avg
        IFS=',' read -r min max avg <<< "$(echo "${STARTUP_DURATIONS[*]}" | tr ' ' '\n' | awk \
            '{s+=$1;if(NR==1||$1<mn)mn=$1;if(NR==1||$1>mx)mx=$1}END{printf "%.0f,%.0f,%.0f",mn,mx,s/NR}')"
        echo -e "${GREEN}Startup:${NC}   min=${min}ms  max=${max}ms  avg=${avg}ms"
    fi

    echo ""
    echo -e "${GREEN}📊 Results CSV:${NC}  $MASTER_CSV"
    echo -e "${GREEN}📝 Log:${NC}  $LOG_FILE"
    echo ""
    echo -e "⏱️  Total: ${minutes}m ${seconds}s"
    echo ""
}

################################################################################
# Main
################################################################################

main() {
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║     Expo Android Pulse Init Startup Timing Benchmark       ║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "  Runs:                 ${NUM_RUNS}"
    echo -e "  Flow:                 flows/scroll_nav.yaml"
    echo -e "  Output CSV:           $MASTER_CSV"
    echo ""

    # Write CSV header
    echo "run_number,pulse_init_t0_ms,pulse_init_t1_ms,pulse_init_duration_ms,timestamp" > "$MASTER_CSV"

    verify_prerequisites
    echo ""

    # Main loop
    for ((i=1; i<=NUM_RUNS; i++)); do
        echo -e "${YELLOW}── Run ${i}/${NUM_RUNS} ──────────────────────────────────────${NC}"
        run_launch "$i"
        log_success "Run $i done → row appended to CSV"
        echo ""
    done

    append_aggregate_stats
    print_summary
}

main "$@"
