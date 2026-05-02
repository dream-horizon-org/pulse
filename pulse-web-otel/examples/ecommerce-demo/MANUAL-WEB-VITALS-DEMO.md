# Manual testing — Web Vitals (ecommerce-demo)

Web Vitals need **two** layers:

1. **Remote SDK config** (`localStorage` via mock JSON or server) — `features[].web_vitals` with `sessionSampleRate: 1` for `pulse_web_js` so `FeatureGate` installs the instrumentation.
2. **Static `PulseWeb.start` config** — `instrumentations.webVitals` (FID/FCP opt-ins, `enabled`) comes from **App.tsx** / env / query params (not from the mock JSON file).

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
# Optional — opt-in extra vitals (see table below)
# VITE_PULSE_WEB_VITALS_FID=true
# VITE_PULSE_WEB_VITALS_FCP=true
```

Then `yarn dev`, open `/`, allow consent, watch collector or DevTools **Network** for `/v1/logs` JSON bodies.

**Why a dedicated mock?** `pulse-sdk-config.mock.whitelist.json` only allows a few log bodies through export filters — **`web_vital` logs are dropped**. `pulse-sdk-config.mock.web-vitals.json` uses an **empty BLACKLIST** so vitals are not filtered at export.

---

## Static instrumentations (SDK config from App)

These merge into `PulseProvider` → `PulseWeb.start({ instrumentations: { webVitals: … } })`.

| Source | Effect |
|--------|--------|
| `VITE_PULSE_WEB_VITALS_FID=true` | Register `onFID` (deprecated CWV; default off in SDK). |
| `VITE_PULSE_WEB_VITALS_FCP=true` | Register `onFCP`. |
| `VITE_PULSE_WEB_VITALS_ENABLED=false` | Local opt-out — no Web Vitals callbacks even if remote gate is on. |
| `?pulse_wv_fid=1` | Same as FID env (no rebuild). |
| `?pulse_wv_fcp=1` | Same as FCP env. |
| `?pulse_wv_enabled=0` | Force `enabled: false` for quick “vitals off” checks. |

Query overrides are read once at first paint (same `useMemo` as the rest of `pulseConfig`); reload after changing URL.

---

## What to do in the browser

| Vital | Tip |
|-------|-----|
| **LCP** | Load home, wait for paint, **click** on the page (e.g. body or a link). Large hero / images help. Wait for batch delay or switch tab to trigger `visibilitychange` flush. |
| **CLS** | Trigger layout shifts (resize window, toggle content). |
| **INP** | Needs **input** with enough processing time; synthetic clicks in automation use a spin-loop — in manual Chrome, click slowly or use heavy UI. **Chromium** is most predictable for `PerformanceEventTiming`. |
| **FID / FCP** | Only after enabling via env or `?pulse_wv_fid=1` / `?pulse_wv_fcp=1`. |

Flush path matches ADR: **`visibilitychange` → hidden** and **`pageshow` (persisted)** call `loggerProvider.forceFlush()` — hide tab or navigate away after interactions.

---

## Other mocks

The default `public/pulse-sdk-config.mock.json` is for **sampling / filter** scenarios; for vitals-first manual work prefer **`pulse-sdk-config.mock.web-vitals.json`**.

See also [MANUAL-PULSEWEB-LIFECYCLE.md](./MANUAL-PULSEWEB-LIFECYCLE.md) for shutdown, disk buffer, and consent.
