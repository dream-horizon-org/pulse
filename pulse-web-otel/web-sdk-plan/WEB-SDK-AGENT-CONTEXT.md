# Pulse Web SDK — Agent Context

> **Read this + `web-sdk-plan/v1/MILESTONES.md` before writing any code.**
> For pinned dependency versions, read `web-sdk-plan/v1/00-setup/dependency-versions.md`.
> Open a phase doc only when actively coding that phase.
> Do NOT load `pulse-web-sdk-plan.md` or `PLAN-OVERVIEW.md` — they are human planning docs, not agent context.

---

## What We're Building

A browser SDK (`@dreamhorizon/pulse-web`) that brings Pulse observability to web apps. One line of code from the customer; Pulse automatically captures errors, performance, network, user interactions, and web vitals — over the same OTLP pipeline already used by the Android and iOS SDKs.

---

## Package Identity

| Field | Value |
|---|---|
| npm package | `@dreamhorizon/pulse-web` |
| Repo location | `pulse-web-otel/` (new dir in monorepo root) |
| V1 version | `0.1.0-alpha.1` |
| V2 version | `0.2.0` |
| OTLP endpoint | `{endpointBaseUrl}/v1/traces` · `/v1/logs` · `/v1/metrics` |
| Auth header | `X-API-KEY: {apiKey}` · `X-Pulse-Metering-Session-ID: {uuid}` (generated per SDK init, mirrors Android) |
| Platform tag | `platform = 'web'` on every signal |

---

## Init telemetry vs Android (no `sdk.init` span)

**Android** does not emit a dedicated **`sdk.init` OTLP trace span** for pipeline smoke tests. Init milestones are **OTel log records** (log body = event name) from `SdkInitializationEvents` in `io.opentelemetry.android.instrumentation.startup`, using the **`otel.initialization.events`** logger scope. Android source of truth for string names: `RumConstants.Events` in `pulse-android-otel/.../RumConstants.kt`.

**Web** also has **no `sdk.init` span.** It uses the same **logger scope** and the same **naming pattern** (`rum.sdk.init.*`) for anything we emit. Implementation: `emitSdkInitializationLogRecords()` in `src/sdk.ts` and string constants in `PulseWebSemconv.RumSdkInit` in `src/semconv.ts`. Emission order: init logs are written **after** `setGlobalLoggerProvider` and **before** `SessionInstrumentation.installAll()` (so **`rum.sdk.init.*` precedes `session.start`** in the real pipeline). For E2E “ready after reload” (no second `session.start`), use **`window.PulseWeb.isInitialized()`** and/or wait for the first export that includes lifecycle traffic.

### `rum.sdk.init.*` milestone log names (Android vs Web)

| Log name | Android (`RumConstants.Events`) | Web (M1) | Notes |
|----------|--------------------------------|----------|--------|
| `rum.sdk.init.started` | Emitted | **Emitted** | First init marker after the log pipeline is available. |
| `rum.sdk.init.config` | **Not yet** (TODO in `SdkInitializationEvents`) | **Not emitted** | Add on both when we record config in an init log. |
| `rum.sdk.init.net.provider` | Emitted | **Not emitted** | Can add on web when we define a single “network stack ready” hook (e.g. fetch / connectivity). |
| `rum.sdk.init.net.monitor` | Emitted | **Not emitted** | Can add when a web network monitor is wired at init. |
| `rum.sdk.init.anr_monitor` | Emitted | **N/A** | ANR is Android-only. |
| `rum.sdk.init.jank_monitor` | Emitted | **Not emitted** | Android jank / slow render. Web may add a later milestone if we align a jank or long-task signal to this name. |
| `rum.sdk.init.crash.reporter` | Emitted | **Not emitted** | Web crash / error reporting is not the same class as Android’s native crash reporter; can add if we want a named “error pipeline ready” log. |
| `rum.sdk.init.span.exporter` | Emitted (attribute `span.exporter`) | **Emitted** (attribute `span.exporter`) | Web value is a short OTLP endpoint summary (traces/logs/metrics paths), not `toString()` of a Java exporter. |

**Extending Web:** add the same way as today’s two events: new keys in `PulseWebSemconv.RumSdkInit` (or a small `emit*` helper), then `loggerProvider.getLogger("otel.initialization.events").emit({ body, attributes })` in `finishStart` once the subsystem is ready. Prefer **matching Android’s literal strings** above so backends and dashboards can share filters.

---

## SDK File Map

```
pulse-web-otel/
├── src/
│   ├── index.ts                      # Public API surface
│   ├── sdk.ts                        # Singleton — PulseWeb.start() / shutdown()
│   ├── config.ts                     # PulseWebConfig interface + validation
│   ├── session.ts                    # Session ID + installation ID (3-tier storage)
│   ├── resource.ts                   # OTEL Resource builder (static browser attrs)
│   ├── exporters.ts                  # OTLP exporters + BatchProcessor; beforeSendData / BeforeSend* wrappers
│   ├── before-send.ts                # beforeSendData config types + resolve/validate
│   ├── exporters/before-send-exporters.ts  # BeforeSend span/log/metric exporters (Android parity)
│   ├── consent.ts                    # PulseDataCollectionConsent guard
│   ├── remote-config.ts              # SDK Config fetcher (sampling, feature gates)
│   ├── feature-gate.ts               # Per-instrumentation enable/disable
│   ├── instrumentation-registry.ts   # install() / uninstall() lifecycle
│   ├── version.ts                    # __SDK_VERSION__ placeholder
│   ├── persistence/
│   │   └── indexed-db.ts             # IndexedDB failed-export buffer (default on, Android parity; drain on init)
│   ├── sampling/                     # Export-time session sampling (Android parity)
│   │   ├── export-sampling-gate.ts   # ExportSamplingGate orchestration
│   │   ├── sampling-exporters.ts     # Sampled + MetricsToAdd span/log wrappers (export order)
│   │   ├── metrics-to-add-recorder.ts
│   │   ├── metrics-to-add-apply.ts
│   │   └── sanitize-instrumentation-name.ts
│   ├── types/sampling.ts             # PulseSignalScope
│   ├── utils/sampling-signal-match.ts # pulseSignalConditionMatches (Android matcher)
│   ├── utils/session-sampling-rate.ts # resolveSessionSamplingRate, log body, critical list
│   ├── processors/
│   │   ├── global-attrs-processor.ts # Injects session.id, screen.name, url.path etc.
│   │   └── signal-filter-processor.ts # Attribute drop/add (processor); signal BLACKLIST/WHITELIST in export gate
│   ├── instrumentations/
│   │   ├── session.ts                # session.start / session.end
│   │   ├── errors.ts                 # device.crash + non_fatal
│   │   ├── network.ts                # http spans (fetch + XHR)
│   │   ├── clicks.ts                 # app.click + rage click
│   │   ├── web-vitals.ts             # LCP/CLS/INP/FCP/TTFB metrics
│   │   └── navigation.ts             # screen_load / screen_interactive / screen_session
│   ├── interactions/
│   │   ├── config-fetcher.ts         # CDN config fetch + in-memory cache
│   │   ├── interaction-matcher.ts    # State machine (IDLE→ONGOING→DONE)
│   │   ├── interaction-manager.ts    # N concurrent trackers + fan-out
│   │   └── interaction-span.ts       # APDEX scoring + span output
│   └── integrations/
│       ├── react/                    # PulseProvider, PulseErrorBoundary, useRouterTracking
│       ├── nextjs/                   # PulseNextProvider (App + Pages Router)
│       └── cdn/                     # Async snippet + queue drain
├── src/__tests__/                    # Vitest + JSDOM unit tests
├── examples/                         # react-app, nextjs-app, cdn-vanilla
├── package.json
├── tsconfig.json
├── tsup.config.ts                    # ESM + CJS + UMD; all entry points
├── vitest.config.ts
└── .size-limit.json                  # core < 30 KB; CDN UMD < 80 KB
```

---

## Data Contract — `pulse.type` Values

Every signal must carry these attrs. Deviating breaks the Pulse dashboard.

| `pulse.type` | Signal kind | Required attributes |
|---|---|---|
| `session.start` | log | `session.id`, `installation.id`, `platform='web'` |
| `session.end` | log | `session.id`, `session.duration_ms`, `screens_visited` |
| `device.crash` | log | `exception.type`, `exception.message`, `exception.stacktrace`, `error.filename` |
| `non_fatal` | log | `exception.type`, `exception.message`, `exception.stacktrace`, `non_fatal.is_manual` |
| `http` | span | `http.method`, `http.url`, `http.status_code`, `http.duration`, `net.peer.name` |
| `app.click` | span | `view.target.class_name`, `view.target.id`, `touch.coordinates.x/y`, `rage_click` |
| `web_vital` | metric gauge | `metric.name` (LCP/CLS/INP/FCP/TTFB), `metric.value`, `metric.rating` |
| `screen_load` | span | `screen.name`, `ttfb_ms`, `fcp_ms`, `load.duration_ms` |
| `screen_interactive` | span | `screen.name`, `tti_ms` |
| `screen_session` | span | `screen.name`, `previous_screen.name`, `duration_ms` |

---

## Global Attributes (injected on every signal by `global-attrs-processor.ts`)

`session.id` · `installation.id` · `screen.name` · `url.path` · `page.url` · `browser.name` · `browser.version` · `os.name` · `os.version` · `device.type` · `network.connection.type` · `rum.sdk.version` · `project.id` · `platform='web'`

---

## Ground rule — parity with Android SDK

**Core product logic should mimic `pulse-android-otel` as closely as browser constraints allow.** That includes sampling (session draw + export-time behavior), remote config interpretation, signal filtering/add/drop semantics, and session identity semantics. When `MILESTONES.md` or phase docs disagree with Android, **treat Android as the source of truth** unless there is an explicit, documented browser-only exception (e.g. CORS, `fetch({ keepalive })` on unload, no `Context` for rule matching — then document the smallest intentional delta).

Primary Android references: `pulse-sampling/` (e.g. `PulseSamplingSignalProcessors`), `pulse-android-sdk-internal` exporter wiring, session + semconv modules.

**Session sampling rules (web vs Android):** `web-sdk-plan/SAMPLING-RULES-WEB-PARITY.md` — which `sampling.rules[].name` values are implemented on web, which still only match `navigator.userAgent`, and how the Pulse dashboard should author rules for `pulse_web_js`.

**Remote config at runtime:** `SdkConfigFetcher.loadCached()` (plus merge) feeds `FeatureGate` and `ExportSamplingGate` at `PulseWeb.start()`. **`fetchInBackground()` persists newer JSON to storage but does not rebuild those gates** — sampling and feature flags stay as at cold start until a **full page reload** (documented M1 scope).

**`beforeSendData` (Android parity):** Public config key matches Android. Wired at export via `BeforeSend*Exporter` in `src/exporters/before-send-exporters.ts`; types `PulseWebBeforeSendConfig` / `PulseWebBeforeSendCallbacks` in `src/before-send.ts`. Semantics + main-thread contract → `web-sdk-plan/v1/01-foundation/before-send-web-android-parity.md`.

---

## Key Decisions

- **Session replay is opt-in** — rrweb adds ~50 KB; not imported by default
- **npm-first, CDN secondary** — modern apps use npm; CDN supports legacy/snippet use
- **Same monorepo** as Android/iOS SDK — shared semconv, CI infra
- **Start at `0.1.0-alpha`** — API can break before GA; no `1.0.0` pressure
- **CORS must be verified in Phase 1** — browser blocks OTLP if backend missing `Access-Control-Allow-Origin`
- **`sendBeacon` on `pagehide`** — ensures signals flush before tab closes

---

## How to Navigate the Plan

| Need | Go to |
|---|---|
| Current milestone status + exit criteria | `web-sdk-plan/v1/MILESTONES.md` — checkboxes per milestone |
| Verification queries (ClickHouse SQL) | `web-sdk-plan/v1/MILESTONES.md` — inside each milestone's testing scope |
| Foundation detail | `web-sdk-plan/v1/01-foundation/index.md` |
| Instrumentation specs | `web-sdk-plan/v1/02-instrumentations/<signal>.md` |
| Interactions spec | `web-sdk-plan/v1/03-interactions/` · M2 parity plan: `v1/03-interactions/IMPLEMENTATION-PLAN-M2-ANDROID-PARITY.md` |
| Framework integrations | `web-sdk-plan/v1/04-frameworks/` |
| Build / distribution | `web-sdk-plan/v1/05-build-distribution/` |
| V2 features | `web-sdk-plan/v2/` |
| Phase dependencies | `web-sdk-plan/00-orchestrator.md` |
| Session sampling rules (web vs Android, dashboard) | `web-sdk-plan/SAMPLING-RULES-WEB-PARITY.md` |
| `metricsToAdd` (product + backend + Web gap) | `web-sdk-plan/METRICS-TO-ADD.md` |
| `metricsToAdd` Web implementation plan | `web-sdk-plan/METRICS-TO-ADD-WEB-PLAN.md` |
| `beforeSendData` / export hooks parity, main-thread contract, export order vs Android | `web-sdk-plan/v1/01-foundation/before-send-web-android-parity.md` |
