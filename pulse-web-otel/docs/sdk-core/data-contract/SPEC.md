# SDK Core — Data contract — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/sdk-core/data-contract/SPEC.md`

---

## 1. Goal

Define the **shared wire contract** (`pulse.type` catalogue and cross-cutting attributes) every instrumentation must respect. Canonical keys also live in `src/semconv.ts`.

---

## 2. Assumptions

See [`../assumptions/SPEC.md`](../assumptions/SPEC.md). **R8** (`platform = web` / resource) is restated in [`../requirements/SPEC.md`](../requirements/SPEC.md).

---

## 3. Requirements

**R8 — platform=web mandate** and attribute consistency — see [`../requirements/SPEC.md`](../requirements/SPEC.md).

---

## 4. Architectural Design

Resource + global attribute processors merge host config with Pulse-built resource — see [`../architecture-and-bootstrap/SPEC.md`](../architecture-and-bootstrap/SPEC.md) and `src/resource.ts`, `src/processors/global-attrs-processor.ts`.

---

## 5. LLD

### 5.1 `pulse.type` enum

All `pulse.type` values are defined in `PulseWebSemconv.PulseType` (`src/semconv.ts`). The complete enum:

| pulse.type | Signal | Emitter | Notes |
|---|---|---|---|
| `session.start` | Log | `SessionInstrumentation` | New `session.id` assigned |
| `session.end` | Log | `SessionInstrumentation` | Background timeout or explicit shutdown |
| `device.crash` | Log | `ErrorInstrumentation`, `PulseErrorBoundary` | `severityNumber = FATAL` |
| `non_fatal` | Log | `ErrorInstrumentation`, `Pulse.reportException`, `Pulse.trackNonFatal` | `severityNumber = WARN` |
| `http` | Span | `NetworkInstrumentation` | Fetch + XHR |
| `app.click` | Span | `ClicksInstrumentation` | DOM click events |
| `web_vital` | Log | `WebVitalsInstrumentation` | LCP, CLS, FID, INP, FCP, TTFB |
| `screen_load` | Span | `NavigationInstrumentation` | Route entry; carries `tti` |
| `screen_session` | Span | `NavigationInstrumentation` | Dwell / exit; OTLP span → `otel_traces` (not `otel_logs`; attrs applied at `span.end()`) |
| `custom_event` | Log | `Pulse.trackEvent` | Host-app custom events |
| `app.installation.start` | Log | `PulseSDK.emitInstallationStartIfNeeded` | First-ever install only |
| `pulse.user.session.start` | Log | `PulseSDK.setUserId` | User identity transition |
| `pulse.user.session.end` | Log | `PulseSDK.setUserId` | User identity transition |

**`platform = 'web'` mandate:** The OTel Resource attribute `os.name` is hard-coded to `'web'` in `buildMergedResource()`. Every signal inherits this via the resource — it is not a per-signal attribute and cannot be overridden by host config.

### 5.2 Shared attribute table

Every signal emitted by the SDK carries the following attributes. Instrumentations may add signal-specific attributes on top; they must not conflict with these reserved keys.

| Attribute key | Type | Source | Required | Notes |
|---|---|---|---|---|
| `pulse.type` | `string` | `PulseWebSemconv.AttributeKey.PULSE_TYPE` | Yes | See enum above |
| `session.id` | `string` | `PulseGlobalAttributesProcessor` | Yes | UUID per session rotation |
| `user.id` | `string \| null` | `PulseGlobalAttributesProcessor` | No | Persisted in `localStorage` |
| `screen.name` | `string` | `PulseGlobalAttributesProcessor` | No | Set via `Pulse.setScreenName()` |
| `last.screen.name` | `string` | `PulseGlobalAttributesProcessor` | No | Previous screen before transition |
| `installation.id` | `string` | `SessionProvider` / `getOrCreateInstallationId()` | Yes | Stable UUID per browser install |
| `metering.session.id` | `string` | `PulseSDK.init()` / `PulseGlobalAttributesProcessor` | Yes | UUID per SDK init; for billing |
| `pulse.user.<name>` | `string` | `PulseGlobalAttributesProcessor` | No | Custom user properties |
| `os.name` | `string` | OTel Resource (`buildMergedResource`) | Yes | Always `'web'` |
| `os.version` | `string` | OTel Resource (`getOsVersionAsync`) | No | Browser UA / Client Hints |
| `browser.name` | `string` | OTel Resource | No | UA-parsed browser name |
| `app.build_name` | `string` | OTel Resource / config `serviceVersion` | No | App version string |
| `service.name` | `string` | OTel Resource / config `serviceName` | Yes | Identifies the app |
| `project.id` | `string` | OTel Resource / `extractProjectId(apiKey)` | Yes | Extracted from API key prefix |

---

## 6. Test Coverage

[`../test-coverage/SPEC.md`](../test-coverage/SPEC.md) — `m1.test.ts` (resource / global attrs), per-instrumentation SPECs for emitters.

---

## 7. Known Bugs & Gaps

Contract drift vs code (e.g. network `pulse.type` pattern) should be fixed here with code or ADR — track under [`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) when product-visible.

---

## 8. Redundancy & Cleanup Notes

None.

---

## 9. Open Questions

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) §9.
