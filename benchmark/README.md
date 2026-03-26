# Android demo startup benchmarks

Minimal layout: **`run_1000_launches_adb.sh`** (uses **`common.sh`**) after you build/install the demo APK yourself. Generated CSV / report files are listed in `.gitignore` by default.

## Why automated build sometimes fails here

- **Gradle needs the internet** the first time (and after cache wipe) to download the Gradle distribution and dependencies (`services.gradle.org`, Maven repos).
- **Cursor/agent sandboxes** often block that traffic, so `./gradlew` fails with `HttpURLConnection` / wrapper download errors — that is **not** your project being broken.

## Correct debug APK path

The demo module is named `pulse-android-demo`, so Gradle outputs:

`pulse-android-otel/demo-app/build/outputs/apk/debug/pulse-android-demo-debug.apk`

Scripts that look only for `app-debug.apk` will **never** find it.

## Option A — Build locally (clean install)

The demo is its **own** Gradle root (`rootProject.name = pulse-android-demo`). From **`demo-app/`** use task names **without** `:demo-app:` (that subproject does not exist there).

```bash
cd pulse-android-otel/demo-app
touch local.properties   # optional; add rum.access.token if you use real RUM
./gradlew clean assembleDebug   # not :demo-app:assembleDebug
adb uninstall io.opentelemetry.android.demo 2>/dev/null || true
adb install -r build/outputs/apk/debug/pulse-android-demo-debug.apk
```

## Option B — Download a CI-built APK

The **Android SDK - Build** workflow uploads the debug APK as an artifact:

1. Open GitHub → **Actions** → **Android SDK - Build** (run on `main` / your branch after push).
2. Open a successful run → **Artifacts** → download **`pulse-android-demo-debug-apk`** (zip contains the `.apk`).
3. Install:

   ```bash
   adb install -r /path/to/pulse-android-demo-debug.apk
   ```

## Run benchmark launches (default 100; after install)

Run from the **monorepo root** (`pulse/`), not from `demo-app/`:

```bash
cd /path/to/pulse   # repo root
chmod +x benchmark/run_1000_launches_adb.sh
./benchmark/run_1000_launches_adb.sh
```

If you are still inside `pulse-android-otel/demo-app`:

```bash
chmod +x ../../benchmark/run_1000_launches_adb.sh
../../benchmark/run_1000_launches_adb.sh
```

CSV is written under `benchmark/` — default filename `startup_times_100runs_adb.csv` (set `NUM_RUNS` in the script to change run count and filename).

### Paste into Google Sheets (one TSV)

The benchmark still writes the raw **`startup_times_*runs_adb.csv`** first. For Sheets, use **`startup_times_*runs_sheets.tsv`**: one table, paste at **A1**.

| Column                     | Contents                                             |
| -------------------------- | ---------------------------------------------------- |
| **iteration**              | Run number (from CSV)                                |
| **timestamp**              | Row timestamp (from CSV)                             |
| **pulse_init_duration_ms** | Per-run duration                                     |
| **min**                    | Minimum duration over valid runs (same on every row) |
| **mean**                   | Average over valid runs                              |
| **median (p50)**           | Median over valid runs                               |

Rows with **`-1`** (timeouts) stay in the table; **min / mean / median** ignore `-1` when computing aggregates.

Regenerate from an existing CSV:

```bash
./benchmark/export_sheets_paste.sh benchmark/startup_times_100runs_adb.csv -o benchmark/my_sheet.tsv
# optional: also write legacy metric/value summary (count, min, max, mean, median)
./benchmark/export_sheets_paste.sh benchmark/startup_times_100runs_adb.csv -o benchmark/my_sheet.tsv --summary benchmark/my_summary.tsv
```

### Waiting for Pulse init logs

The demo logs **`PULSE_INIT_T0_MS`**, **`PULSE_INIT_T1_MS`**, and **`PULSE_INIT_DURATION_MS`** from `OtelDemoApplication` (around `PulseSDK.initialize`) with tag **`otel.demo`**. Scripts dump `adb logcat -d` and grep for those strings (avoid `logcat -s tag:D`, which is unreliable on some devices). Poll every **0.25s** until all three appear (default **72** polls ≈ **18s** max).

If a run **times out**, the script still appends a CSV row with **`-1`** for the three ms columns so row count stays **NUM_RUNS**; stats ignore `-1` rows. Increase wait: `export WAIT_FOR_PULSE_INIT_MAX_POLLS=120`.

If the CSV stays empty, confirm logs: `adb logcat -c && adb shell am start -n io.opentelemetry.android.demo/.MainActivity && sleep 2 && adb logcat -d | grep PULSE_INIT`

Optional env vars:

```bash
export POST_LAUNCH_DELAY_SEC=0.8   # wait after `am start` before polling logcat (default 3s)
export WAIT_FOR_PULSE_INIT_MAX_POLLS=48   # longer cap (~12s at 0.25s poll)
export WAIT_FOR_PULSE_INIT_POLL_SEC=0.3   # slower polling
# Legacy aliases still work:
export WAIT_FOR_T1_MAX_POLLS=48
export WAIT_FOR_T1_POLL_SEC=0.3
./benchmark/run_1000_launches_adb.sh
```
