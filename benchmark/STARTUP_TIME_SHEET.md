# Pulse Android Demo App - Startup Time Measurements

# Main Branch Performance Baseline

## Device Classification Analysis

### Current Device: Pixel_3a_API_35 (Android 14 Emulator)

| Aspect            | Classification  | Details               |
| ----------------- | --------------- | --------------------- |
| **Device Type**   | ❌ NOT Low-End  | Pixel 3a is mid-range |
| **API Level**     | 35 (Android 14) | Modern                |
| **Architecture**  | arm64-v8a       | 64-bit                |
| **RAM Allocated** | 2048 MB         | Moderate              |
| **Emulator CPU**  | 1-2 cores       | Limited emulation     |

### Low-End Device Specs (for comparison)

- API 21-24 (Android 5.0-7.0)
- 1 GB - 2 GB RAM
- Single-core or dual-core CPU
- Cortex-A53 or older

### Verdict

**Pixel_3a_API_35 is a MID-RANGE device emulator, NOT low-end.**

For true low-end testing, would need:

- API 21-24 emulator
- Smaller heap size (512 MB - 1 GB)
- Or physical low-end device (Redmi 7, Samsung A10, etc.)

---

## Startup Time Measurements - Main Branch

### Run 1: Cold Start (Fresh App Kill)

| Metric           | Value        | Notes               |
| ---------------- | ------------ | ------------------- |
| **TotalTime**    | 2,395 ms     | Time to first frame |
| **WaitTime**     | 2,398 ms     | Total launch wait   |
| **Launch State** | COLD         | App force-stopped   |
| **Activity**     | MainActivity | Entry point         |
| **Status**       | ✅ PASS      | < 3000 ms           |

### Device Classification

- Expected cold start on mid-range: 2-4 seconds ✅ Meets expectation
- On low-end device: Would expect 5-10+ seconds

---

## Warm Start Measurements

| Test Case              | Time (ms) | Expected (Low-End) | Status  |
| ---------------------- | --------- | ------------------ | ------- |
| Warm Launch            | TBD       | 1-3 sec            | Pending |
| Resume from Background | TBD       | 0.5-1.5 sec        | Pending |

---

## Performance Baseline (Main Branch)

### Startup Performance

```
Cold Start:  2,395 ms  ✅ Good
Warm Start:  TBD       ⏳ Pending
Avg Startup: ~2.4 sec
```

### Device Capability

- **Current**: Mid-range emulator
- **Recommended for Low-End Testing**: API 21-24 emulator or low-end physical device

---

## Flashlight Installation & Test Status

### ❌ Not Yet Installed

Flashlight requires:

1. npm/yarn installed
2. Node.js 14+
3. Running on macOS/Linux
4. Android device/emulator connected

### Installation Steps (To be run)

```bash
npm install -g @shopify/flashlight
flashlight init /Users/shruti-pathak/Code/pulse/pulse-android-otel/demo-app
flashlight run --scenario cold_launch
```

### Test Scenarios (Not Yet Run)

- [ ] Cold Launch
- [ ] Warm Launch
- [ ] Scroll Stress (30s)
- [ ] Navigation Flow
- [ ] Idle → Resume
- [ ] Stress Interaction

---

## Summary

| Item                 | Status     | Value              |
| -------------------- | ---------- | ------------------ |
| Device               | Mid-Range  | Pixel_3a API35     |
| Cold Start Measured  | ✅ Done    | 2,395 ms           |
| Warm Start Measured  | ⏳ Pending | -                  |
| Flashlight Installed | ❌ No      | Needs setup        |
| Scenarios Tested     | ❌ No      | 0/6                |
| Performance Grade    | B+         | Good for mid-range |

---

## Next Actions Required

1. ✅ Cold startup time: **2,395 ms** (MEASURED)
2. ⏳ Install Flashlight CLI
3. ⏳ Run 6 test scenarios with Flashlight
4. ⏳ Generate detailed FPS/CPU/Memory reports
5. (Optional) Use low-end device emulator for true low-end baseline

---

**Report Generated:** 2026-03-19
**Branch:** main
**Device:** Pixel_3a_API_35 (Mid-Range)
