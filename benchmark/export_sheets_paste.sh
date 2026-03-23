#!/usr/bin/env bash
# Convert benchmark CSV → one tab-separated table for Google Sheets.
#
# Columns: iteration, timestamp, pulse_init_duration_ms, min, mean, median (p50).
# (min/mean/median are aggregates over valid rows; same value on each row for reference lines.)
#
# Usage:
#   ./benchmark/export_sheets_paste.sh path/to/startup_times_100runs_adb.csv
#   ./benchmark/export_sheets_paste.sh path/to/csv.csv -o my_sheet.tsv
#   ./benchmark/export_sheets_paste.sh path/to/csv.csv --summary legacy_metric_value.tsv  # optional
#
# Paste: copy stdout or file → Google Sheets cell A1.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/common.sh"

OUT=""
SUMMARY_OUT=""
CSV=""

usage() {
  echo "Usage: $0 <startup_times_*runs_adb.csv> [-o sheet.tsv] [--summary legacy_summary.tsv]" >&2
  exit 1
}

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      ;;
    -o)
      OUT=${2:-}
      if [ -z "$OUT" ]; then usage; fi
      shift 2
      ;;
    --summary)
      SUMMARY_OUT=${2:-}
      if [ -z "$SUMMARY_OUT" ]; then usage; fi
      shift 2
      ;;
    -*)
      echo "Unknown option: $1" >&2
      usage
      ;;
    *)
      if [ -n "$CSV" ]; then
        echo "Unexpected extra argument: $1" >&2
        usage
      fi
      CSV=$1
      shift
      ;;
  esac
done

if [ -z "${CSV:-}" ]; then
  usage
fi

if [ ! -f "$CSV" ]; then
  echo "Not found: $CSV" >&2
  exit 1
fi

if [ -n "$OUT" ]; then
  write_pulse_init_sheets_paste_tsv "$CSV" "$OUT"
  echo "Wrote: $OUT" >&2
else
  write_pulse_init_sheets_paste_tsv "$CSV" "-"
fi

if [ -n "$SUMMARY_OUT" ]; then
  write_pulse_init_sheets_summary_tsv "$CSV" "$SUMMARY_OUT"
  echo "Wrote: $SUMMARY_OUT (legacy metric/value: count, min, max, mean, median)" >&2
fi

echo "→ Paste into Google Sheets at A1: iteration, timestamp, duration, min, mean, median (p50)." >&2
