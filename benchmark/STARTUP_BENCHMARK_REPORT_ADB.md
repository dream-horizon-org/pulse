# Pulse Android Demo - Startup Benchmark Report (ADB)

**Date:** Mon Mar 23 15:30:44 IST 2026
**Branch:** temp/perf-benchmarking
**Commit:** 079c1b005

## Test Setup

| Parameter | Value |
|-----------|-------|
| Runs | 100 |
| App | io.opentelemetry.android.demo |
| Activity | .MainActivity |
| Device | OR7TRS8TB669BIBI |

## Results

| Metric | Value (ms) |
|--------|-----------|
| Min | 905 |
| Max | 1362 |
| Average | 941 |
| Std Dev | 84 |
| P50 | 922 |
| P95 | 1032 |
| P99 | 1362 |

## Data

CSV: `/Users/shruti-pathak/Code/pulse/benchmark/startup_times_100runs_adb.csv`

Sheets (tab-separated for copy-paste at A1): `/Users/shruti-pathak/Code/pulse/benchmark/startup_times_100runs_sheets.tsv` — iteration, timestamp, pulse_init_duration_ms, min, mean, median (p50).

View samples:
```bash
head -10 "/Users/shruti-pathak/Code/pulse/benchmark/startup_times_100runs_adb.csv"
tail -5 "/Users/shruti-pathak/Code/pulse/benchmark/startup_times_100runs_adb.csv"
```

---
Generated: Mon Mar 23 15:30:44 IST 2026
