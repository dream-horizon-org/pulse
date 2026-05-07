# Manual testing — Web Vitals (ecommerce-demo)

Web Vitals need **two** layers:

1. **Remote SDK config** (`localStorage` via mock JSON or server) — `features[].web_vitals` with `sessionSampleRate: 1` for `pulse_web_js` so `FeatureGate` installs the instrumentation.
2. **Static `Pulse.start` config** — optional **`instrumentations.webVitals.enabled`** from **App.tsx** / env / query params (whole Web Vitals block off only; not from the mock JSON file).

---

## Quick start (mock + readable OTLP)

From `pulse-web-otel/examples/ecommerce-demo`, create or extend `.env.local`:

```bash
VITE_PULSE_MOCK_SDK_CONFIG=true
VITE_PULSE_MOCK_SDK_CONFIG_PATH=/pulse-sdk-config.mock.web-vitals.json
VITE_PULSE_FORMAT=json
VITE_PULSE_COMPRESSION=none
# Optional — faster batches while clicking around (ms)
VITE_PULSE_BATCH_DELAY_MS=2000
```

Then `yarn dev`, open `/`, allow consent, watch collector or DevTools **Network** for `/v1/logs` JSON bodies.

**Why a dedicated mock?** `pulse-sdk-config.mock.whitelist.json` only allows a few log bodies through export filters — **`web_vital` logs are dropped**. `pulse-sdk-config.mock.web-vitals.json` uses an **empty BLACKLIST** so vitals are not filtered at export.

---

## Static instrumentations (SDK config from App)

These merge into `PulseProvider` → `Pulse.init({ instrumentations: { webVitals: … } })`.

| Source | Effect |
|--------|--------|
| `VITE_PULSE_WEB_VITALS_ENABLED=false` | Local opt-out — no Web Vitals instrumentation even if remote gate is on. |
| `?pulse_wv_enabled=0` | Same — force `instrumentations.webVitals.enabled: false`. |
| `?pulse_wv_enabled=1` | Force `enabled: true` only when env does not force-disable (`VITE_PULSE_WEB_VITALS_ENABLED=false` still wins). |

Query overrides are read once at first paint (same `useMemo` as the rest of `pulseConfig`); reload after changing URL.

---

## What to do in the browser

On **Home (`/`)**, the **Manual Web Vitals QA** card includes:

- **CLS** — “Toggle shifting box” changes a box height (layout shift).
- **INP** — “Slow click handler (~70ms)” spin-blocks the main thread so `PerformanceEventTiming` can exceed the INP threshold (same pattern as Playwright E2E).
- **TTFB** — Short explanation + **Hard reload** — TTFB is navigation timing for the document; use throttling or reload to see a new sample (you cannot “click” TTFB after load).

| Vital | Tip |
|-------|-----|
| **LCP** | Load home, wait for paint, **click** on the page (e.g. body or a link). Large hero / images help. Wait for batch delay or switch tab to trigger `visibilitychange` flush. |
| **CLS** | Use the Home QA panel and/or resize window, toggle content. |
| **INP** | Use the Home QA slow-handler button, or long tasks on click. **Chromium** is most predictable for `PerformanceEventTiming`. |
| **FCP / FID / TTFB** | Same install path as LCP/INP/CLS when vitals are on — no separate demo toggles; **TTFB** = throttle network or reload. |

Flush path matches ADR: **`visibilitychange` → hidden** and **`pageshow` (persisted)** call `loggerProvider.forceFlush()` — hide tab or navigate away after interactions.

---

## Other mocks

The default `public/pulse-sdk-config.mock.json` is for **sampling / filter** scenarios; for vitals-first manual work prefer **`pulse-sdk-config.mock.web-vitals.json`**.

See also [MANUAL-PULSEWEB-LIFECYCLE.md](./MANUAL-PULSEWEB-LIFECYCLE.md) for shutdown, disk buffer, and consent.
