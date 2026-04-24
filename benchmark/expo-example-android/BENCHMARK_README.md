# Expo Android Benchmarking

Two standalone scripts for measuring different aspects of app performance. They use different Maestro flows: [`flows/scroll_nav.yaml`](flows/scroll_nav.yaml) for fast startup-only runs, and [`flows/intensive_flow.yaml`](flows/intensive_flow.yaml) for full UI load under Flashlight.

---

## Scripts

| Script | Measures | Output |
|---|---|---|
| `pulse-init-benchmark.sh` | Pulse SDK init time (T0→T1) via logcat | `pulse_init_results_<N>runs.csv` |
| `flashlight_metrics.sh` | UI perf — FPS, CPU, RAM, score | `flashlight_metrics_results.csv` |

---

## Build & Install

**Option A — Build locally:**
```bash
cd pulse-react-native-otel/expo-example
npx expo prebuild --platform android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Option B — Download from CI:**
GitHub → Actions → **Android SDK - Build** → pick a successful run → Artifacts → download `pulse-android-demo-debug-apk`, then `adb install -r <path>.apk`

Device requirements: Emulator API 30+ or physical device (Pixel 4a+).

---

## Prerequisites

- `adb` — Android device connected (`adb devices` to verify)
- `maestro` — UI automation (`brew install maestro`)
- `flashlight` — perf profiler (`npm install -g @bamlab/flashlight`) — flashlight only
- `jq` — JSON parsing (`brew install jq`) — flashlight only
- App installed: `com.example.expoexample`

---

## Troubleshooting

| Issue | Solution |
|---|---|
| No connected devices | `adb devices` to verify, or start emulator |
| No PULSE_INIT markers | `adb logcat -c && adb shell am start -n com.example.expoexample/.MainActivity && sleep 2 && adb logcat -d \| grep PULSE_INIT` |
| Logcat timeout | `export WAIT_FOR_PULSE_INIT_MAX_POLLS=120` |
| Maestro flow fails | `maestro studio com.example.expoexample` to verify testIDs |
| Flashlight not found | `npm install -g @bamlab/flashlight` |

### Optional env vars (pulse-init)

```bash
export WAIT_FOR_PULSE_INIT_MAX_POLLS=120  # increase timeout for slow devices (default 72 × 0.25s)
export WAIT_FOR_PULSE_INIT_POLL_SEC=0.5   # adjust poll frequency
export MAESTRO_FLOW=/path/to/flow.yaml    # override default flow (scroll_nav.yaml)
```

---

## Maestro flows

### `scroll_nav.yaml` (used by `pulse-init-benchmark.sh`)

Short path: launch → home → scroll to `category-5` → open `category-0`. Minimizes per-run time while still cold-starting the app and exercising the home list.

### `intensive_flow.yaml` (used by `flashlight_metrics.sh`)

Full e-commerce journey to generate real CPU, memory, and FPS load:

| Phase | Actions |
|---|---|
| 1 | Launch app, scroll category list down and back up |
| 2 | Visit category-1, back, visit category-2, back (navigation re-renders) |
| 3 | Select category-0, scroll product list down and back up |
| 4 | Open product detail, swipe down 1.5s and back up (image load stress) |
| 5 | Add to cart, view bag |
| 6 | Proceed to checkout |
| 7 | Fill shipping details (name, address, email), dismiss keyboard |
| 8 | Continue to payment |
| 9 | Place order |
| 10 | Order confirmation, return to home |
| 11 | Switch tabs: browse → wishlist → home (full re-render cycle) |

Duration: on the order of ~45–55 seconds per run (varies by device and network).

---

## pulse-init-benchmark.sh

Measures how long the Pulse SDK takes to initialize on each cold launch by parsing `PULSE_INIT_T0_MS` and `PULSE_INIT_T1_MS` logcat markers.

### Usage

```bash
./pulse-init-benchmark.sh [NUM_RUNS]

./pulse-init-benchmark.sh       # 10 runs (default)
./pulse-init-benchmark.sh 100   # 100 runs

# Override the Maestro flow (default: flows/scroll_nav.yaml)
MAESTRO_FLOW=/path/to/flow.yaml ./pulse-init-benchmark.sh 20
```

### What it does per run

```
1. Force-stop app + clear logcat (double-clear for reliability)
2. Start background logcat capture
3. Run Maestro `flows/scroll_nav.yaml` (launches app → triggers Pulse init logging)
4. Parse PULSE_INIT_T0_MS, PULSE_INIT_T1_MS, PULSE_INIT_DURATION_MS from logcat
5. Append one CSV row
```

### Logcat markers

Injected by the Pulse Expo config plugin ([`pulse-react-native-otel/plugin/src/utils.ts`](../../pulse-react-native-otel/plugin/src/utils.ts)) into `MainApplication.kt`:

```
PULSE_INIT_T0_MS=1776775075441     ← System.currentTimeMillis() at SDK init start
PULSE_INIT_T1_MS=1776775075703     ← System.currentTimeMillis() at SDK init end
PULSE_INIT_DURATION_MS=262         ← T1 - T0
```

### CSV output

**File:** `pulse_init_results_<N>runs.csv` — overwritten each invocation.

| Column | Type | Description |
|---|---|---|
| `run_number` | int | Run index (1 to NUM_RUNS) |
| `pulse_init_t0_ms` | long | SDK init start (epoch ms) |
| `pulse_init_t1_ms` | long | SDK init end (epoch ms) |
| `pulse_init_duration_ms` | int | T1 − T0 in ms. `-1` if not found |
| `timestamp` | ISO 8601 | Row timestamp |

**Example:**
```
run_number,pulse_init_t0_ms,pulse_init_t1_ms,pulse_init_duration_ms,timestamp
1,1776775075441,1776775075703,262,2026-04-23T10:00:01Z
2,1776775131104,1776775131365,261,2026-04-23T10:01:45Z
3,-1,-1,-1,2026-04-23T10:03:30Z
```

**Aggregate section** (appended at end):
```
## Aggregate Statistics
metric,value
total_runs,100
successful_runs,97
failed_runs,3
startup_min_ms,226.0
startup_max_ms,360.0
startup_avg_ms,276.3
startup_p50_ms,270
total_duration_sec,540
```

### Output files

| File | Description |
|---|---|
| `pulse_init_results_<N>runs.csv` | Master CSV + aggregates |
| `pulse_init_benchmark_<ts>.log` | Full run log |

---

## flashlight_metrics.sh

Runs Flashlight end-to-end per run, parses the raw JSON output, computes a performance score using the official Flashlight algorithm, and exports to CSV.

### Usage

```bash
./flashlight_metrics.sh [NUM_RUNS]

./flashlight_metrics.sh      # 10 runs (default)
./flashlight_metrics.sh 50   # 50 runs
```

Each run = one independent cold start. Flashlight force-stops the app before running (`am force-stop` + 3s wait), then drives the Maestro flow and writes one JSON file.

### What it does per run

```
1. flashlight test → force-stops app → drives Maestro intensive_flow.yaml → writes JSON
2. jq parses JSON  → extracts metrics from iterations[0].measures[]
3. awk computes    → official Flashlight score
4. Append one CSV row
```

### JSON structure

Flashlight writes `results/flashlight_metrics_<N>.json`:

```json
{
  "name": "Results",
  "status": "SUCCESS",
  "iterations": [
    {
      "time": 22757.58,
      "status": "SUCCESS",
      "measures": [
        {
          "fps": 17.22,
          "ram": 89.28,
          "cpu": {
            "perName": { "UI Thread": 50.0, "RenderThread": 14.0, "mqt_v_js": 28.0 },
            "perCore": { "0": 32.0, "3": 50.0 }
          },
          "time": 505
        }
      ]
    }
  ]
}
```

Each `measure` is a ~500ms polling snapshot. A typical run produces ~90–110 measures.

### Metric extraction (jq)

```
fps_avg   = average of all measures[].fps
fps_min   = minimum of all measures[].fps
ram_avg   = average of all measures[].ram  (MB)
ram_peak  = maximum of all measures[].ram  (MB)
cpu_avg   = average of total CPU per measure
cpu_peak  = maximum of total CPU per measure
duration  = iterations[0].time  (ms)
```

**CPU note:** `cpu.perName` lists every thread independently. Total CPU per measure = sum of all thread values. This can exceed 100% on multi-core devices (e.g., 4 threads × 50% = 200%). This is expected — the scoring formula is calibrated for this multi-thread total.

### Score calculation

Sourced from [`bamlab/flashlight`](https://github.com/bamlab/flashlight):
- Score formula: [`getScore.ts`](https://github.com/bamlab/flashlight/blob/main/packages/core/reporter/src/reporting/getScore.ts)
- CPU averaging: [`averageIterations.ts`](https://github.com/bamlab/flashlight/blob/main/packages/core/reporter/src/reporting/averageIterations.ts)

```
cpu_score = clamp(0, 100,  -0.31666666666667 × cpu_avg + 116)
fps_score = clamp(0, 100,  fps_avg × 100 / 60)
score     = round(max(0, (cpu_score + fps_score) / 2))
```

**CPU score calibration:**

| Total CPU % | CPU Score |
|---|---|
| 50% | 100 |
| 200% | 50 |
| 300% | 15 |

**Score examples:**

| Scenario | cpu_avg | fps_avg | score |
|---|---|---|---|
| Smooth | 80% | 58 | **94** |
| Heavy CPU | 250% | 55 | **64** |
| Janky | 120% | 30 | **64** |
| Poor | 300% | 20 | **27** |

**Excluded from score:**
- RAM — intentionally excluded (matches official Flashlight tool)
- Thread-lock penalty — not present in basic JSON output, treated as 0

### CSV output

**File:** `flashlight_metrics_results.csv` — overwritten each invocation.

| Column | Type | Description |
|---|---|---|
| `run_number` | int | Run index (1 to NUM_RUNS) |
| `fps_avg` | float | Average FPS across all measures |
| `fps_min` | float | Minimum FPS (worst frame) |
| `ram_avg_mb` | float | Average RAM in MB |
| `ram_peak_mb` | float | Peak RAM in MB |
| `cpu_avg_pct` | float | Average total CPU % (can exceed 100) |
| `cpu_peak_pct` | float | Peak total CPU % |
| `duration_ms` | int | Run duration in ms |
| `score` | int | Flashlight performance score (0–100) |
| `status` | string | `SUCCESS` or `FAILED` |
| `timestamp` | string | Row timestamp |

**Example:**
```
run_number,fps_avg,fps_min,ram_avg_mb,ram_peak_mb,cpu_avg_pct,cpu_peak_pct,duration_ms,score,status,timestamp
1,54.32,17.22,142.50,176.19,28.69,265.87,216618,94,SUCCESS,2026-04-23 10:00:01
2,51.80,6.98,138.20,172.34,35.73,258.13,218594,92,SUCCESS,2026-04-23 10:04:15
3,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,FAILED,2026-04-23 10:08:30
```

**Aggregate section** (appended at end):
```
## Aggregate Statistics
metric,value
total_runs,10
successful_runs,9
mean_score,93
mean_fps_avg,53.10
mean_ram_avg_mb,144.30
mean_cpu_avg_pct,29.47
```

### Output files

| File | Description |
|---|---|
| `flashlight_metrics_results.csv` | Master CSV + aggregates |
| `results/flashlight_metrics_<N>.json` | Raw Flashlight JSON per run |
