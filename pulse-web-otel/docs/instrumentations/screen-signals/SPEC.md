# Screen Signals (Navigation) — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/instrumentations/screen-signals/SPEC.md`

---

## 1. Goal

Track **initial page load** and **SPA route transitions** using OTLP **log records** (`LoggerProvider`) with `pulse.type` values **`screen_load`** and **`screen_session`**, plus Navigation Timing-derived metrics on loads. Align naming with Android/RN screen analytics while documenting **web-specific** decisions (no separate **`screen_interactive`** log — **TTI** lives on **`screen_load`**).

---

## 2. Assumptions

- **`screen_load` / `screen_session`:** Shared conceptual model with Android — route entered vs session scoped to a screen.
- **`screen_interactive` (label):** **React Native** may emit a distinct **`screen_interactive`** span tied to `markContentReady()`. **Web does not** emit a separate `pulse.type = screen_interactive` log — **web-only** clarification per ADR / FINAL-PLAN amendment (`tti` stamped on **`screen_load`** instead).
- **Consent:** Instrumentation skips install when `dataCollectionState === DENIED`.
- **Logs not spans:** Current `NavigationInstrumentation` uses **`logs.emit`** (body `screen_load` / `screen_session`), not `Tracer` spans — signal family is OTLP logs.

---

## 3. Requirements

**R1 — Initial load:** After `load` (or immediately if document already loaded), emit **`screen_load`** with Navigation Timing fields where available (`page.load_time`, `ttfb`, `tti`, etc.).

**R2 — SPA transitions:** Patch `history.pushState` / `replaceState` + listen to `popstate`; on meaningful route change (rate-limited), emit **`screen_session`** for previous screen then **`screen_load`** for new screen with `start_type: spa`.

**R3 — Screen naming:** `screen.name` comes from `globalAttrsProcessor.getCurrentScreenName()` / `setScreenName` after transitions.

**R4 — Unload:** `pagehide` emits final **`screen_session`** for time-on-screen.

---

## 4. Architectural Design

### Plan B — navigation logs via History API + Performance

Patch History API for SPA parity with Android activity transitions; reuse Navigation Timing for cold loads.

**TDD-MANDATE note:** Implementation prioritises **`screen_load`** + **`screen_session`** logs; separate **`screen_interactive`** web log was **explicitly rejected** (ADR) to avoid duplicating Web Vitals.

---

## 5. LLD

### 5.1 Signal matrix (implementation)

| Signal | `pulse.type` | Shape | Key attributes |
|---|---|---|---|
| Route entered / cold load | `screen_load` | OTLP **log** | `screen.name`, `session.id`, `page.load_time`, `tti`, `start_type`, timing breakdowns, `url.path`, `page.title`, `navigation.type` |
| Screen exit / time-on-screen | `screen_session` | OTLP **log** | `screen.name`, `session.id`, `session.duration_ms`, doc attrs |

### 5.2 `screen_interactive` naming (**web-only** note)

- **No standalone web log** with `pulse.type = screen_interactive` — **`tti`** is attached to **`screen_load`** on initial load when Navigation Timing allows.
- **React Native** retains different semantics for the **same enum string** — do not assume cross-SDK identity of behaviour.

### 5.3 React SPA

- History hook captures React Router / client routers using History API.
- **Hash routing:** depends on whether app mutates `history` — standard History patch applies when `pushState` used.

### 5.4 Next.js App Router

- Soft navigations use client-side History updates → **`screen_load`**/`screen_session` emitted when pathname-backed screen name changes (via global screen name pipeline + hooks from Next integration).

### 5.5 Next.js Pages Router

- `routeChangeComplete` flows through client History events — same instrumentation once screen name updates.

### 5.6 Initial vs SPA

- **Initial:** rich Navigation Timing on **`screen_load`**.
- **SPA:** lighter **`screen_load`** (`start_type: spa`) without full paint timing replicate.

---

## 6. Test Coverage

### `src/__tests__/navigation-instrumentation.test.ts`

- Initial **`screen_load`** contains timing attrs when mocked PerformanceNavigationTiming present.
- SPA navigation emits **`screen_session`** then **`screen_load`**.
- **`screen_interactive`** **not** present in emitted pulse.types on web (explicit assertion).
- Consent denied → instrumentation not installed.

### `src/__tests__/screen-name-resolution.test.ts`

- Screen name resolution paths for global attrs processor (referenced from navigation).

---

## 7. Known Bugs & Gaps

### P0: (data contract — none identified)

No **P0** navigation signal gaps confirmed at synthesis.

### Other gaps

- Hash-only routers without History API mutation may not trigger patches — document host requirement.

---

## 8. Redundancy & Cleanup Notes

**Canonical contract:** this SPEC plus `src/instrumentations/navigation.ts`.

`web-sdk-plan/v4-screen-signals/` is **retained** as the design archive (`FINAL-PLAN.md`, ADR, touchpoints). The **Amendment** at the top of `FINAL-PLAN.md` overrides older sections that still describe three separate web signals including `screen_interactive` — web implementation follows the amendment (single `screen_load` with `tti`, no separate `screen_interactive` log).

| Path | Role |
|---|---|
| `pulse-web-otel/web-sdk-plan/v4-screen-signals/` | Locked plan + ADR + amendment (historical + authoritative amendment) |
| `pulse-web-otel/docs/NAVIGATION-INSTRUMENTATION.md` | Removed from tree; navigation detail lives in this SPEC |
| `pulse-web-otel/web-sdk-plan/v1/02-instrumentations/navigation.md` | Superseded v1 one-pager (still in repo for history) |

---

## 9. Open Questions

1. Should SPA `screen_load` include synthetic timing estimates?
