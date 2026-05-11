# Errors Instrumentation — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/instrumentations/errors/SPEC.md`

---

## 1. Goal

Capture JavaScript failures as OTLP **log records** aligned with Pulse mobile semantics: fatal crashes (`pulse.type = device.crash`) and recoverable issues (`pulse.type = non_fatal`). Covers automatic listeners (`window.error`, `unhandledrejection`), manual SDK APIs (`Pulse.reportException`, `Pulse.reportDeviceCrash`, `Pulse.trackNonFatal`), optional device-state enrichment, deduplication, and React render errors via `PulseErrorBoundary`.

---

## 2. Assumptions

- **Android / React Native parity:** Web maps to the same `pulse.type` values as Android `CrashReporter` / `NonFatalReporter`: `device.crash` for fatal paths, `non_fatal` for warnings and async failures.
- **Web-only divergence — unhandled promise rejection:** Browsers emit `unhandledrejection`; there is no direct Android analogue for “promise rejection without a caught handler,” but we still classify it as `non_fatal` (WARN), not `device.crash`, matching the PLAN-B decision that sync uncaught exceptions are fatal-class.
- **Web-only divergence — no ANR:** Android ANR has no browser equivalent; not modeled.
- **Cross-origin script errors:** `"Script error."` with empty `filename` is skipped (browser withholds stack).
- **SSR / Node:** `ErrorInstrumentation.install()` returns immediately when `typeof window === "undefined"` — no listeners on the server. Same guard applies to `PulseErrorBoundary` callers only after client hydration (`Pulse.init` must have run).

---

## 3. Requirements

### Functional

**R1 — OTLP logs only:** No separate metric or span family for errors in this instrumentation; signals are `LoggerProvider` emits.

**R2 — `device.crash`:** Uncaught synchronous errors from `window.addEventListener("error", …)` → `severityNumber = FATAL`, `pulse.type = device.crash`.

**R3 — `non_fatal`:** Unhandled promise rejections → `pulse.type = non_fatal`, `severityNumber = WARN`, `non_fatal.is_manual = false`.

**R4 — Manual APIs:** `Pulse.reportException` / `Pulse.trackNonFatal` → `non_fatal`; `Pulse.reportDeviceCrash` → `device.crash` (see `sdk-core` SPEC §5.10).

**R5 — Dedupe:** Fingerprints + 5s sliding window suppress burst duplicates (`DEDUPE_WINDOW_MS = 5000`).

**R6 — Gating:** Installation requires local config key `errors` not disabled and remote `PulseFeature.JS_CRASH` enabled (`InstrumentationRegistry`).

**R7 — Device state:** Optional `battery.percent` and `storage.free` on `device.crash` when APIs exist (`navigator.getBattery`, `navigator.storage.estimate`).

### Non-functional

- Listeners removed on `uninstall()`; battery `levelchange` listener detached to avoid leaks on SDK restart.

---

## 4. Architectural Design

```
InstrumentationRegistry.installAll()
  └─ ErrorInstrumentation (PulseFeature.JS_CRASH)
        ├─ prefetchDeviceState()  → battery + storage async
        ├─ window "error"         → device.crash log
        └─ window "unhandledrejection" → non_fatal log

React layer (optional)
  └─ PulseErrorBoundary.componentDidCatch
        └─ Pulse.reportDeviceCrash(error, { react.component_stack })
```

**Decision (ADR):** Keep log-based model; harden E2E and lifecycle docs rather than introducing spans/metrics for errors.

---

## 5. LLD

### 5.1 Attribute table (canonical)

| Attribute key | Type | Source | Required | Notes |
|---|---|---|---|---|
| `pulse.type` | string | semconv | Yes | `device.crash` or `non_fatal` |
| `exception.type` | string | thrown/reason | Yes | `Error.prototype.name` |
| `exception.message` | string | thrown/reason | Yes | Message string |
| `exception.stacktrace` | string | thrown/reason | Yes | Stack or empty |
| `session.id` | string | global attrs processor | Yes | Per sdk-core |
| `screen.name` | string | global attrs processor | No | Per sdk-core |
| `platform` | string | OTel Resource `os.name` | Yes | Always `web` |
| `error.filename` | string | `ErrorEvent` | For `device.crash` | Script URL |
| `error.lineno` | number | `ErrorEvent` | For `device.crash` | 1-based line |
| `error.colno` | number | `ErrorEvent` | For `device.crash` | Column |
| `url.path` | string | `window.location` | Yes | `pathname` |
| `non_fatal.is_manual` | boolean | SDK | For `non_fatal` | `false` auto, `true` manual |
| `battery.percent` | number | `navigator.getBattery` | No | 0–100 when available |
| `storage.free` | number | `navigator.storage.estimate` | No | Bytes free when available |
| `react.component_stack` | string | React `ErrorInfo` | No | From `PulseErrorBoundary` only |

Global attributes (`session.id`, `screen.name`, resource) are applied by processors — same contract as `sdk-core` SPEC §5.2.

### 5.2 React `PulseErrorBoundary`

- **Behaviour:** `componentDidCatch` calls `Pulse.reportDeviceCrash(error, { "react.component_stack": info.componentStack })`.
- **Requires:** `Pulse.init()` completed (`isInitialized()`); otherwise SDK no-ops.
- **Integration:** Wrap subtrees that should report render-phase failures; does not replace global `window.error` for non-React throws.

### 5.3 Next.js / SSR edge case

- Server components and SSR passes have **no `window`** — instrumentation does not register listeners during SSR.
- React errors during SSR are **not** captured by this browser instrumentation; host apps should rely on server-side error reporting separately. Client hydration enables `PulseErrorBoundary` + global handlers.

### 5.4 React SPA behaviour

- Global handlers attach to `window` after client `Pulse.init`. Same-origin JS errors and promise rejections surface as logs with full attributes.
- **Next.js App Router / Pages Router (client):** Once hydrated, behaviour matches SPA; route transitions do not re-install instrumentation — listeners remain for the tab lifetime.

---


## 6. Test Coverage

### `src/__tests__/error-instrumentation-device-state.test.ts`

Scenarios (from file header and bodies):

- **Battery:** `navigator.getBattery()` resolves → `battery.percent` on emitted `device.crash` log; `levelchange` updates cached percent; `uninstall()` removes listener.
- **Storage:** `navigator.storage.estimate()` resolves → `storage.free` stamped on crash path.
- **Degradation:** missing `getBattery`, rejected `getBattery`, rejected `estimate` — no throw; logs still emit without optional attrs.
- **Rejections:** non-Error reasons (string, null, object) coerced to `Error` for `non_fatal` path.
- **Dedupe / fingerprinting:** see `src/__tests__/m3.test.ts` for burst/dedupe scenarios alongside core contract tests.

### `src/__tests__/m3.test.ts` (related contract coverage)

- TC1 — uncaught JS error → `device.crash` (FATAL, lineno/colno, no `non_fatal.is_manual`).
- TC2 — unhandled rejection → `non_fatal` (WARN, `is_manual=false`).
- TC3 — manual `reportException` / `reportDeviceCrash` paths.
- TC15/TC16 — string / undefined rejection coercion.
- Battery omitted paths still emit `device.crash` when APIs unavailable.

---

## 7. Known Bugs & Gaps

### P0 (data contract — none identified)

No known **P0** issues (wrong/missing ClickHouse attributes or silent drops) in the current `errors.ts` / boundary path beyond documented cross-origin silence.

### Other gaps

- **Severity taxonomy vs Android:** Unhandled rejection is `non_fatal` on web; some teams expect “crash” semantics — documented as intentional in PLAN-B.
- **React 18 Strict Mode:** Double mount/unmount in dev may interact with boundary reset patterns; not exhaustively E2E’d.

---

## 8. Redundancy & Cleanup Notes

Files absorbed into this SPEC and **deleted** (triple-eval):

| Deleted path |
|---|
| `pulse-web-otel/web-sdk-plan/v1-errors/DESIGN.md` |
| `pulse-web-otel/web-sdk-plan/v1-errors/ADR-errors.md` |
| `pulse-web-otel/web-sdk-plan/v1-errors/01-research-errors-ecosystem-and-industry.md` |
| `pulse-web-otel/web-sdk-plan/v1-errors/02-research-errors-otel-js-browser-and-pulse-sdk.md` |
| `pulse-web-otel/web-sdk-plan/v1-errors/03-touchpoints-matrix.md` |
| `pulse-web-otel/web-sdk-plan/v1-errors/04-contract-parity.md` |
| `pulse-web-otel/web-sdk-plan/v1-errors/PLAN-B-errors-log-signals.md` |
| `pulse-web-otel/web-sdk-plan/v1-errors/HANDOFF-NEXT-AGENT.md` |
| `pulse-web-otel/web-sdk-plan/v1-errors/README.md` |
| `pulse-web-otel/web-sdk-plan/v1/02-instrumentations/errors.md` |

---

## 9. Open Questions

1. Should unhandled promise rejection ever escalate to `device.crash` for parity with certain native crash reporters?
2. Should cross-origin errors emit a scrubbed `device.crash` with empty stack vs silence entirely?
