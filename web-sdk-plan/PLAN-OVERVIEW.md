# Pulse Web SDK — Project Plan

**Date:** April 2026 | **Status:** Planning Complete

---

## Objective

Bring Pulse observability to web browsers. A customer adds one line of code; Pulse automatically captures errors, performance, network, user interactions, and session replays — the same data Pulse already collects on Android and iOS, over the same pipeline.

---

## What Gets Captured

| Signal | What It Covers |
|---|---|
| Errors & Crashes | Unhandled JS errors, promise rejections |
| Network | All fetch/XHR — URL, status, duration, failures |
| Clicks & Rage Clicks | Every tap with element context; rage click detection |
| Web Vitals | LCP, CLS, INP, FCP, TTFB with attribution |
| Navigation | Page load timing, SPA route changes, time-on-screen |
| Long Tasks | Main thread blocks > 50ms (jank) |
| Resource Timing | JS, CSS, image, font load times |
| Connectivity | Tab visibility, online/offline transitions |
| WebSocket | Connection lifecycle, message counts, errors |
| BFCache | Browser back/forward cache restores |
| **Interactions** | Multi-step journey tracking with APDEX (same config system as mobile) |
| **Session Replay** *(opt-in)* | DOM-level recording with privacy masking |

---

## Delivery Plan

### Phase 1 — Foundation `Weeks 1–2`
Production-grade SDK core. The foundation is built to support everything that follows without retrofits.

| Area | What's Built |
|---|---|
| Core | Init / shutdown lifecycle, session + identity management, OTLP HTTP export, consent |
| Batching | 5s flush, 2048 queue, 512 batch — configurable; `pagehide` force-flush via `sendBeacon` |
| Persistence | IndexedDB signal buffer — failed exports survive tab crash, drained on next load |
| Payload | JSON (default) or Protobuf; gzip compression via browser-native `CompressionStream` |
| Instrumentation registry | `install()` / `uninstall()` contract; every instrumentation togglable at init |
| Session instrumentation | `session.start` / `session.end` signals (separate from session ID management) |
| Shutdown API | Force flush all providers → uninstall all instrumentations → clear state |

**Exit:** A heartbeat span from a test page appears in ClickHouse with `Platform = 'web'`. Shutdown flushes cleanly. Instrumentation toggle verified.

---

### Phase 2 — Framework Integrations `Weeks 2–3`
Idiomatic one-liner integrations for React, Next.js, Vue 3, and CDN/Vanilla JS. Automatic route tracking and error boundaries per framework. This runs early so all subsequent phases are tested in real app environments.

| Framework | Integration |
|---|---|
| React + React Router v6 | `<PulseProvider>`, `<PulseErrorBoundary>` |
| Next.js (App + Pages Router) | Provider with SSR guard |
| Vue 3 / Nuxt 3 | `PulseVuePlugin`, vue-router hook |
| CDN / Vanilla JS | Async `<script>` snippet |

**Exit:** Each framework has a working example app. Route changes tracked automatically. SDK initialises exactly once.

---

### Phase 3 — Auto-Instrumentations `Weeks 3–5` *(parallel with Phase 2)*
All 10 auto-instrumentations. Each independently togglable via remote config without an SDK release. Combined core bundle budget: **< 30 KB gzip**.

**Exit:** All 13 signal types visible in Pulse dashboard under `Platform = 'web'`. No errors on Firefox or Safari.

---

### Phase 4 — Interactions `Weeks 4–5` *(parallel with Phase 3)*
Port the server-driven multi-step journey tracking from mobile to web. No backend or dashboard changes needed — span attributes are identical across platforms.

**Exit:** An interaction funnel span with correct APDEX score appears in the existing Pulse Interactions tab.

---

### Phase 5 — SDK Config (Remote Config) `Weeks 5–6`
Extend the existing server-driven SDK Config system to recognise `pulse_web_js`. Adds web feature flags, sampling rules (by browser, URL, device type), and per-feature remote config to gate instrumentation behaviour without an SDK release.

Placed here — after instrumentations are stable — so there are real signals flowing before remote gating is validated end-to-end.

**Exit:** Web SDK reads config from `/v1/configs/active`. Disabling an instrumentation via server config is confirmed without an SDK update.

---

### Phase 6 — Session Replay `Weeks 6–7`
DOM recording via rrweb. Privacy masking on by default (all inputs masked, sensitive blocks redactable by CSS class). Compressed and chunked for delivery. Separate opt-in import — does not affect core bundle size.

**Exit:** Replay chunks land in ClickHouse. Masked values absent from all events. Final chunk delivered on tab close.

---

### Phase 7 — Backend & UI `Weeks 7–9` *(parallel with Phase 6)*
Make web data visible in existing Pulse dashboards. Most screens need no changes — the data schema is the same, the platform filter already exists.

| Change | Effort |
|---|---|
| CORS headers on ingest endpoints | Small — without this, no data flows |
| `Platform = 'web'` in dashboard filters | Small audit + patches |
| Remote config: web feature flags + web replay config | Medium |
| rrweb session replay player in UI | Large |
| Web Vitals dashboard screen | Large |
| Conditional browser vs. device attribute display | Medium |

**Exit:** Web crashes, sessions, and interactions visible in all existing dashboards. rrweb replay plays back. Web flags configurable from the UI.

---

### Phase 8 — Build, Distribution & Testing `Weeks 9–11`
Production npm package (`@dreamhorizon/pulse-web`) + CDN artifact. Automated publish on release tag. Full test suite: unit (Vitest), E2E (Playwright on Chrome/Firefox/WebKit), BrowserStack on iPhone Safari and Chrome Android.

**Exit:** `npm install @dreamhorizon/pulse-web` works. CDN URL live. CI green. Bundle < 30 KB gzip enforced.

---

## Instrumentations & Signals

### Auto-Instrumentations — 10 modules, 13 signal types

| # | Instrumentation | Signal(s) Produced | Signal Kind |
|---|---|---|---|
| 02.1 | Errors | `device.crash`, `non_fatal` | Log |
| 02.2 | Network (Fetch / XHR) | `http` | Span |
| 02.3 | Clicks & Rage Clicks | `app.click` | Log |
| 02.4 | Web Vitals | `web_vital` (LCP, CLS, INP, FCP, TTFB) | Metric |
| 02.5 | Navigation | `screen_load`, `screen_interactive`, `screen_session` | Span |
| 02.6 | Long Tasks | `app.jank.slow` | Log |
| 02.7 | Resource Timing | `resource_load` | Span |
| 02.8 | Visibility & Online | `app.visibility`, `network.change` | Log |
| 02.9 | WebSocket | `websocket` | Span |
| 02.10 | BFCache | `bfcache.restore` | Span |

### Opt-In Features

| Feature | Signal Produced | Signal Kind |
|---|---|---|
| Interactions | `interaction` (with APDEX score) | Span |
| Session Replay | `session_replay` (compressed DOM chunks) | Log |

### Global Attributes on Every Signal

Every signal — regardless of instrumentation — automatically carries:

`session.id` · `screen.name` · `url.path` · `browser.name` · `browser.version` · `os.name` · `device.type` · `network.connection.type` · `installation.id` · `project.id` · `platform` · `rum.sdk.version`

---

## Key Decisions

| Decision | Rationale |
|---|---|
| OpenTelemetry (OTLP) | Same wire format as mobile — zero new backend pipeline |
| Framework integrations in Phase 2 (early) | Real app context for testing all subsequent instrumentation work |
| Session Replay opt-in only | rrweb adds ~50 KB — keeps default bundle lean |
| Interactions in v1 | High funnel value; same config system as mobile; no new backend work |
| Version starts at `0.1.0-alpha` | Freedom for breaking API changes before GA |

---

## Top Risks

| Risk | Mitigation |
|---|---|
| CORS not configured → zero data flows | First backend task; unblocks all testing |
| rrweb bundle too large for some customers | Separate import; core bundle unaffected |
| `screen.name` high cardinality without route config | Heuristic fallback; documented setup guide |

---

## Open Points

### Metrics

| # | Question | Options |
|---|---|---|
| M1 | Custom metric recording API (`PulseWeb.trackMetric`) — include in v1 or post-v1? | v1 adds cross-platform parity with iOS; post-v1 keeps scope lean |
| M2 | Web Vitals output format — are they emitted as proper OTEL gauge metrics (→ `otel_metrics_gauge`) or as spans/logs? | Must confirm before 02.4 implementation; wrong format breaks Web Vitals dashboard |
| M3 | Memory gauge (`performance.memory.usedJSHeapSize`) — worth capturing given it's Chrome-only and requires COOP/COEP headers? | Opt-in, low priority candidate |
| M4 | Derived metrics via SDK Config (server-side signal-to-metric rules, same as mobile) — v1 or post-v1? | Post-v1 recommended; unblock after signal pipeline is stable |

---

### Screen Name

`screen.name` is the web equivalent of Android's `ActivityName`. Raw URLs are high-cardinality; the SDK resolves them via a 4-step chain: manual override → route pattern config → heuristic (strip IDs) → raw pathname.

| # | Question | Options |
|---|---|---|
| S1 | Hash-based routing (`/#/products/123`) — listen to `hashchange` events as route changes? | Opt-in flag `enableHashRouting: true`; off by default |
| S2 | URL-not-changing navigation (tabs, wizard steps, modals) — expose a `startView(name)` API that creates a session boundary without a URL change? | Required for SPAs that don't reflect all screens in the URL |
| S3 | Query-param routing (`/search?tab=flights` vs `?tab=hotels`) — track `?key=value` changes as route changes? | Opt-in config `queryParamRoutingKey: 'tab'` |
| S4 | Should `routePatterns` config be required for go-live, or is the heuristic fallback acceptable for v1? | Heuristic covers most cases; patterns are needed for clean dashboard grouping |

---


## Deferred

- **Click Heatmap** — data contract complete (normalised coordinates in every click event); UI visualisation deferred.
- **Metrics gaps** — detail in `deferred-metrics.md`.
