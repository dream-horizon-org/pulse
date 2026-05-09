# ADR — Screen navigation signals (web: `screen_load`, `screen_session`)

## Decision

Adopt **OTel log records** (`logger.emit`) for web screen navigation with:

- **`screen_load`** — initial full page load **and** SPA route changes; carries Navigation Timing (`page.load_time`, `ttfb`, `start.type`, **`tti`** on initial load when `PerformanceNavigationTiming` is available), plus `url.path` / `page.title` where applicable.
- **`screen_session`** — time on the **previous** screen when navigating away, and a final session on **`pagehide`** when leaving the document.

**Do not emit** a separate web log with **`pulse.type = screen_interactive`**.

**Rationale:** Industry peers (PostHog, Sentry, typical RUM) do not ship a parallel first-class “screen interactive” **event** next to page/view load; Pulse already captures **Web Vitals** (`web_vital`) for Core Web Vitals. A second log duplicated timing intent. **`tti`** remains useful on **`screen_load`** for dashboards that filter `pulse.type = screen_load`. **React Native** keeps **`screen_interactive`** as a **span** bounded by **`markContentReady()`** — different semantics; do not conflate with web.

**Feature gate:** `PulseFeature.SCREEN_NAVIGATION` / backend `Features.screen_navigation` for `pulse_web_js`.

**Span emission:** via `logger.emit()` → OTLP batch queue → existing flush on **`pagehide`** / visibility (SDK-owned).

## Rejected alternative

See [PLAN-A-metrics-histogram.md](./PLAN-A-metrics-histogram.md) — metrics lose per-event context, break Android parity, and complicate ClickHouse queries.

## Flush / lifecycle strategy

### Initial page load

1. Wait for `load` event (or emit immediately if document already loaded).
2. Emit **one** `screen_load` log with Navigation Timing attributes (**including `tti` when present**).
3. Flush via next `visibilitychange` or `pagehide` (SDK owns final flush).

### SPA route change

1. Emit **`screen_session`** for the **previous** screen (duration attrs).
2. Emit **`screen_load`** for the **new** screen (`start.type: spa`).
3. No separate `screen_interactive` on SPA (unchanged from earlier grill).

### Tab close / page unload

- Navigation instrumentation emits final **`screen_session`** on **`pagehide`**; session instrumentation / SDK still **`forceFlush`**.

## Grill (Phase 3) — summary

### Double install / singleton

- `InstrumentationRegistry` private flag `installAllCompleted` prevents duplicate listeners
- `uninstall()` removes all event listeners + clears state

### Consent & feature gate

- Not installed if `PulseFeature.SCREEN_NAVIGATION` is false (backend controls)
- Not installed if consent is revoked
- Off-path E2E: seed config with `screen_navigation` sample rate `0` → zero navigation logs exported

### SSR / non-browser

- `typeof window === "undefined"` → no-op install (Next.js server render safe)

### Cross-platform note

- **`screen.name`** + **`last.screen.name`** stamped via `PulseGlobalAttributesProcessor` where applicable.
- **Android/iOS native:** `screen_load` / `screen_session` style signals; **RN:** optional **`screen_interactive` span** — web **logs** only **`screen_load`** + **`screen_session`** as above.

### Framework integration

- React Router: history patch + `popstate`; `useRouterTracking()` updates screen name.
- Next.js: use `useRouter()` / pathname for screen name where applicable.

### Optional attributes

- Omit attribute keys when value is undefined (ClickHouse treats missing != empty string)

## Implementation note (v1 web SDK)

Signals are **OTLP logs** via `Logger.emit()` with `pulse.type` and timing attributes. They are **not** separate OpenTelemetry Span API objects in the browser exporter path v1; duration for **`screen_session`** is carried as **`session.duration_ms`** / **`session.duration`** on log attributes.

## Handoff notes

1. **Web vitals** — remain the **`web_vital`** pipeline (LCP, INP, CLS, …).
2. **UI Screens tab** — query `screen_load`, `screen_session`, filter `platform = web`.
3. **Session-to-screen correlation** — `screen.name` / `last.screen.name` via global processor.
