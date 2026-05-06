# Ecommerce demo — QA map (routes, SDK wiring, signals)

**Maintain this file** when routes, mock configs, or registry/`sdk.ts` installation changes. The Cursor skill **ecommerce-demo-manual-qa** points here first.

## SDK entry

1. `main.tsx` loads mock SDK / interaction config (see env `VITE_PULSE_MOCK_*`).
2. `App.tsx` wraps the tree in `PulseProvider` with `pulseConfig` from env + query string (`?pulse_consent=`, `?pulse_wv_*`, etc.).
3. `PulseWeb` is exposed on `window` for manual/E2E (`_PulseWebExpose`).
4. `_PulseWebRouterTracking` calls `useRouterTracking({ skipInitial: false })` so **`screen.name` updates on navigation** (pathname). This stamps subsequent signals; it does **not** by itself emit a “navigation” log unless Navigation instrumentation is installed and designed to do so.

## Routes (verify in `src/App.tsx`)

| Route | Screen |
|-------|--------|
| `/` | Home — “Shop Now” uses `PulseWeb.trackEvent('shop_now_click')`; **Web Vitals QA** panel (`WebVitalsManualTriggers`) — CLS toggle box, INP slow-handler button, TTFB reload note |
| `/products` | Products |
| `/products/:id` | Product detail |
| `/cart` | Cart |
| `/checkout` | Checkout |
| `/error-demo` | Error Demo — intentional crashes/rejections (see page copy for M3 errors instrumentation) |

## Documentation already in this folder

- **[MANUAL-WEB-VITALS-DEMO.md](./MANUAL-WEB-VITALS-DEMO.md)** — Web Vitals manual QA, mock JSON, query/env overrides.
- **[MANUAL-PULSEWEB-LIFECYCLE.md](./MANUAL-PULSEWEB-LIFECYCLE.md)** — shutdown, disk buffer, consent, lifecycle.

## Feature → install path (current code)

Remote **`PulseFeature`** names map in `instrumentation-registry.ts` `featureMap`. Installation:

- **`sdk.ts` `installInstrumentations`:** `new InteractionInstrumentation()` → `registry.installAll()` → **`registerAndInstall(this.interactionInstrumentation, InstrumentationKeys.INTERACTIONS)`** (interaction class exists before `installAll`, but **register** runs after the `installAll` batch).
- **`installAll()` (registry):** installs **Session** (if gated), **Web Vitals** (if gated). Other keys (errors, network, clicks, navigation, session replay) are **planned / partial** — read `instrumentation-registry.ts` for the latest; do not claim they run until registered there.

## Typical “what fires?” quick answers

| Human action | Likely SDK path | Notes |
|--------------|-----------------|-------|
| Load app, consent allowed | Session instrumentation | `session.start` / session lifecycle per PLAN |
| Navigate between routes | `useRouterTracking` | Updates **`screen.name`** for later events |
| Click “Shop Now” on Home | `PulseWeb.trackEvent('shop_now_click')` | With **`custom_events`** gate: **custom_event** log. With data collection + **interaction** gate: interaction pipeline. Both can apply — check active mock config for `custom_events` / `interaction` |
| Web vitals (LCP, INP, CLS, FCP, FID, TTFB) | `WebVitalsInstrumentation` | Needs **`web_vitals`** gate + see MANUAL-WEB-VITALS-DEMO for mock; six vitals when installed |
| Error Demo buttons | Future/partial errors instr. | Page text describes `device.crash` / `non_fatal` when M3 errors ship |
| Rage click UI (where mounted) | Interaction module | Fast repeated clicks — see interaction PLAN/E2E |

Always confirm **`pulse.type`** and attrs in `src/semconv.ts` and the relevant `src/instrumentations/*.ts` file.

## Dev overlay

- **Shift+P** or “P” badge: **`PulseDebugPanel`** — session/installation IDs, IndexedDB buffer count, monkey-patched OTLP traffic (dev only).
