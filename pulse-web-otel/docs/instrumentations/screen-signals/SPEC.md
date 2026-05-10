# Screen Signals (Navigation) — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/instrumentations/screen-signals/SPEC.md`

---

## 1. Goal

Track **initial page load** and **SPA route transitions** using OTLP **client spans** (`sdk.tracer.startSpan` / `span.end()`), exported to **`otel_traces`**, with `pulse.type` values **`screen_load`** and **`screen_session`**, plus Navigation Timing-derived metrics on loads. Align naming with Android/RN screen analytics while documenting **web-specific** decisions (no separate **`screen_interactive`** signal — **TTI** lives on the **`screen_load`** span attributes).

**Migration note:** Earlier revisions emitted these signals via `LoggerProvider` log records (`otel_logs`). Web now matches Android by emitting **spans only** for `screen_load` / `screen_session` so Pulse Screens analytics (`dataType: TRACES`) can query them without UI/backend UNION changes.

---

## 2. Assumptions

- **`screen_load` / `screen_session`:** Shared conceptual model with Android — route entered vs session scoped to a screen.
- **`screen_interactive` (label):** **React Native** may emit a distinct **`screen_interactive`** span tied to `markContentReady()`. **Web does not** emit a separate `pulse.type = screen_interactive` span — **web-only** clarification (`tti` stamped on **`screen_load`** when Navigation Timing allows).
- **Consent:** Instrumentation skips install when `dataCollectionState === DENIED`.
- **Export shape:** `NavigationInstrumentation` uses **`sdk.tracer`** with **`ROOT_CONTEXT`** and **`SpanKind.INTERNAL`** so screen spans are not parented under unrelated active context. Before **`span.end()`**, spans use **`SpanStatusCode.OK`** (not unset).

---

## 3. Requirements

**R1 — Initial load:** After `load` (or immediately if document already loaded), emit a **`screen_load`** span with Navigation Timing fields where available (`page.load_time`, `ttfb`, `tti`, etc.). Span **`name`** is the literal **`screen_load`** (not the route string).

**R2 — SPA transitions:** Patch `history.pushState` / `replaceState` + listen to `popstate`; on meaningful route change (rate-limited), end the **`screen_session`** span for the previous screen, emit **`screen_load`** for the new screen with **`start.type` = `spa`**, then start a new **`screen_session`** span for dwell time.

**R3 — Screen naming:** `screen.name` comes from `globalAttrsProcessor.getCurrentScreenName()` / `setScreenName` after transitions.

**R4 — Unload:** `pagehide` ends the active **`screen_session`** span for time-on-screen. **`uninstall`** ends any open **`screen_session`** span.

---

## 4. Architectural Design

### Navigation spans via History API + Performance

Patch History API for SPA parity with Android activity transitions; reuse Navigation Timing for cold loads. No dual emission: **do not** also emit log records for the same navigation events.

---

## 5. LLD

### 5.1 Signal matrix

| Signal | `pulse.type` | OTel span `name` | Export |
|---|---|---|---|
| Route entered / cold load | `screen_load` | **`screen_load`** | OTLP span → **`otel_traces`** |
| Screen exit / time-on-screen | `screen_session` | **`screen_session`** | OTLP span → **`otel_traces`** |

### 5.2 Android / Web parity comparison

#### `screen_load`

| Attribute | Android | Web (implemented span) | Notes |
|---|---|---|---|
| **OTLP shape** | span → `otel_traces` | span → `otel_traces` | Aligned |
| **Span `name`** | — | **`screen_load`** (literal) | Not the route string |
| **`SpanStatus`** | OK | **`OK`** before `end()` | Same as interaction spans |
| `pulse.type` | `screen_load` | `screen_load` | Identical |
| `screen.name` | span attr | span attr | Identical |
| `last.screen.name` | span attr | span attr (SPA transition from prior screen) | Funnel / parity |
| `session.id` | span attr | span attr | Identical |
| `start.type` | hot/warm/cold | cold/reload/back_forward/**spa** | Web adds `spa` |
| Cold duration | span duration | **`performance.timeOrigin` → `timeOrigin + loadEventEnd`** (Navigation Timing) | Matches load interval |
| SPA duration | — | **~0** (`startTime` ≈ `endTime`, marker span) | Entry marker, not Nav Timing |
| `platform` | `android` (resource) | `web` (resource) | Same key |
| `page.load_time`, `tti`, `ttfb`, … | — | span attrs where available | Web-only |
| `navigation.type` | — | span attr (`navigate` / `reload` / `back_forward`) | Web-only |

#### `screen_session`

| Attribute | Android | Web (implemented span) | Notes |
|---|---|---|---|
| **OTLP shape** | span → `otel_traces` | span → `otel_traces` | Aligned |
| **Span `name`** | — | **`screen_session`** (literal) | Not the route string |
| **`SpanStatus`** | OK | **`OK`** before `end()` | |
| `pulse.type` | `screen_session` | `screen_session` | Identical |
| `screen.name` | span attr | span attr (screen being exited) | Identical |
| `last.screen.name` | span attr | span attr (screen before the exited screen) | Parity |
| `session.id` | span attr | span attr | Identical |
| Duration | `otel_traces.Duration` | dwell span **`startTime`→`endTime`** | Matches time on screen |
| `session.duration_ms` / `session.duration` | — | span attrs (duplicate ms) | Dashboard convenience |
| `url.path` / `page.title` | — | snapshot for **exited** screen | Avoid post-navigation URL bleed |

### 5.2 `screen_interactive` naming (**web-only** note)

- **No standalone web span** with `pulse.type = screen_interactive` — **`tti`** is attached to **`screen_load`** on initial load when Navigation Timing allows.
- **React Native** retains different semantics for the **same enum string** — do not assume cross-SDK identity of behaviour.

### 5.3 React SPA

- History hook captures React Router / client routers using History API.

### 5.4 Next.js App Router

- Soft navigations use client-side History updates → **`screen_load`**/**`screen_session`** when pathname-backed screen name changes.

### 5.5 Next.js Pages Router

- `routeChangeComplete` flows through client History events — same instrumentation once screen name updates.

### 5.6 Initial vs SPA

- **Initial:** Navigation Timing on **`screen_load`** span duration and attrs.
- **SPA:** lighter **`screen_load`** (`start.type: spa`), marker duration (~0).

---

## 6. Test Coverage

### `src/__tests__/navigation-instrumentation.test.ts`

- Hoisted **`mockTracer` / mock span** shape (`startSpan`, `setAttributes`, `setStatus`, `end`).
- **`screen_load`** / **`screen_session`** span names and `pulse.type` attrs.
- **`screen_interactive`** **not** present on web spans (explicit scan).
- Consent denied → instrumentation not installed.

### `src/__tests__/screen-name-resolution.test.ts`

- Screen name resolution paths for global attrs processor (referenced from navigation).

### `examples/ecommerce-demo/e2e/screen-navigation.spec.ts`

- Playwright OTLP capture: **`waitForSpan("screen_load" | "screen_session")`**, **`findAllSpans`**, gate-off asserts zero **`screen_load`** spans.

---

## 7. Known Bugs & Gaps

### Resolved: logs vs traces for screen signals

Previously `navigation.ts` used **`logger.emit()`** → **`otel_logs`**. Web screen rows were invisible to Screens analytics (`otel_traces`-only queries). **Resolved:** spans via **`sdk.tracer`** → **`otel_traces`**.

### P1: Hash-only routers

Routers that do not mutate `history` may not trigger patches — document host requirement.

---

## 8. Verification

**Unit**

```bash
cd pulse-web-otel && yarn test:run src/__tests__/navigation-instrumentation.test.ts
```

**E2E (ecommerce-demo)**

```bash
cd pulse-web-otel/examples/ecommerce-demo && yarn e2e --grep "@ScreenNav" --project=chromium
```

**ClickHouse (`otel_traces`, not `otel_logs`):**

```sql
SELECT
  SpanName,
  SpanAttributes['pulse.type'] AS pulse_type,
  SpanAttributes['screen.name'] AS screen_name,
  Duration / 1e6 AS duration_ms
FROM otel.otel_traces
WHERE ProjectId = 'your-project'
  AND PulseType IN ('screen_load', 'screen_session')
  AND Timestamp >= now() - INTERVAL 1 HOUR
LIMIT 50;
```

---

## 9. Redundancy & Cleanup Notes

**Canonical contract:** this SPEC plus `src/instrumentations/navigation.ts`.

`web-sdk-plan/v4-screen-signals/` is **retained** as the design archive (`FINAL-PLAN.md`, ADR, touchpoints). The **Amendment** at the top of `FINAL-PLAN.md` overrides older sections that still describe three separate web signals including `screen_interactive` — web implementation follows the amendment (single `screen_load` with `tti`, no separate `screen_interactive` web signal).

| Path | Role |
|---|---|
| `pulse-web-otel/web-sdk-plan/v4-screen-signals/` | Locked plan + ADR + amendment (historical + authoritative amendment) |
| `pulse-web-otel/web-sdk-plan/v1/02-instrumentations/navigation.md` | Superseded v1 one-pager (still in repo for history) |

---

## 10. Open Questions

1. Should SPA `screen_load` include synthetic timing estimates? (Probably no — emit with available attrs only.)
2. **Resolved:** Logs vs spans — **spans** implemented for web/Android parity and Screens analytics.
