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

## Run 1000 launches (after install)

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

CSV is written under `benchmark/` (see script header for exact filename).

### Waiting for Pulse init logs

The demo logs **`PULSE_INIT_T0_MS`**, **`PULSE_INIT_T1_MS`**, and **`PULSE_INIT_DURATION_MS`** from `OtelDemoApplication` (around `PulseSDK.initialize`). Scripts poll logcat every **0.25s** until all three appear (default **36** polls ≈ **9s** max).

Optional env vars:

```bash
export WAIT_FOR_PULSE_INIT_MAX_POLLS=48   # longer cap (~12s at 0.25s poll)
export WAIT_FOR_PULSE_INIT_POLL_SEC=0.3   # slower polling
# Legacy aliases still work:
export WAIT_FOR_T1_MAX_POLLS=48
export WAIT_FOR_T1_POLL_SEC=0.3
./benchmark/run_1000_launches_adb.sh
```
