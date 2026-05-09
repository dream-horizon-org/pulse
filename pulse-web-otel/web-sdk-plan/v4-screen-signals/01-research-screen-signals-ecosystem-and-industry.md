# Phase 0a — Screen navigation signals: ecosystem & industry

## What does "screen" mean in RUM?

In mobile RUM (Android/iOS), a **screen** = activity/view lifecycle. When users navigate, every signal on that screen is attributed to it. Web has no native equivalent — URL is the closest proxy, but is high-cardinality (raw URLs are noisy).

**Standard industry approach (Google Analytics, Datadog, New Relic):**
- Emit a **navigation event** (or span) on page/route load with timing data
- Stamp `screen.name` (or `page.name`) on **all subsequent signals** so they inherit the screen context
- Emit a **session event** on navigation away (time on screen)

**Pulse specifics (from v1/navigation.md):**
- **screen_load span**: initial page load + SPA route changes; carries `page.load_time`, `ttfb`, `start.type` (cold/reload/back_forward)
- **screen_interactive span**: time-to-interactive (when DOM is interactive)
- **screen_session span**: time spent on screen; emitted when user navigates away
- `screen.name` resolution: manual override → route pattern → heuristic (strip IDs) → raw pathname

## Signal type: spans vs logs vs metrics

| Option | Pros | Cons | Industry use |
|--------|------|------|--------------|
| **Spans (OTel traces)** | Captures duration + ordering; context propagation ready; Android parity ✅ | Extra wiring; DB query cost higher | Most RUM tools (APM + RUM merge traces) |
| **Logs** | Fast emit; works with log batching; lower cardinality | No timing structure; less queryable | Events-only RUM (GA4, Amplitude) |
| **Metrics** | Aggregation-friendly; low cardinality | Loses individual event data; histogram only | Time-series DBs (Prometheus/Grafana) |

**Decision: Spans** (matching Android + OTel tracing paradigm). Traces are the right semantic for "events with duration and ordering."

- `screen_load` = **span** (load event end - start time)
- `screen_interactive` = **span** (DOM interactive - start time)
- `screen_session` = **span** (time on screen)

All stamped with `pulse.type` + `screen.name` for ClickHouse querying.

## Flush / export lifecycle

### Initial page load (`screen_load` + `screen_interactive`)

1. Browser fires `load` event
2. `PerformanceNavigationTiming` entries are ready (`loadEventEnd`, `domInteractive`)
3. Create both spans from perf data; start times = 0 (page start), end times = navigation timing values
4. Emit via `logger.emit()` → OTLP batch queue
5. **Export trigger**: on next visible/hidden transition OR page close (SDK owns `pagehide`)

### SPA route change (`screen_session` + next screen's `screen_load`)

1. Framework router fires (React Router, Vue Router, Next.js app router)
2. Call `onRouteChange(newPath)` → ends previous session, starts new one
3. Emit previous `screen_session` span (duration = time on old screen)
4. Emit next `screen_load` span (but timing data is zero since SPA nav has no PerformanceNavigationTiming)
5. **Export trigger**: `visibilitychange` hidden + `sdk.loggerProvider?.forceFlush()`

### On exit (`pagehide`)

- Emit final `screen_session` for current screen
- SDK already flushes on `pagehide`; navigation instrumentation just adds the final span

**Key constraint:** Use **`loggerProvider.forceFlush()`** not `Logger.emit()` alone (Logger has no flush API).

## Feature gate & semconv

**Feature name:** `SCREEN_NAVIGATION` (backend `Features.SCREEN_NAVIGATION` enum, `PulseFeature.SCREEN_NAVIGATION` in web SDK)

**`pulse.type` values:**
- `screen_load`
- `screen_interactive`
- `screen_session`

**Semconv attributes (Pulse custom):**
- `screen.name` — screen identifier (manual / pattern / heuristic / pathname)
- `url.path` — full path (web equivalent of `activity.name`)
- `page.title` — document title (web-specific)
- `start.type` — `cold` / `reload` / `back_forward` / `hash` (derived from Navigation Timing API)

---

## Answers to the three stop-condition questions

1. **Signal type:** **Spans** (OTel trace semantics; matches Android; timing-rich)
2. **Flush / export:** **`loggerProvider.forceFlush()`** on `visibilitychange` + `pagehide`; also on SPA navigation away
3. **Gate:** **`PulseFeature.SCREEN_NAVIGATION`** gating backend + SDK; semconv keys in `PulseWebSemconv`
