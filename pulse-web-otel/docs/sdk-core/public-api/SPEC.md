# SDK Core — Public API (`Pulse.*`) — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/sdk-core/public-api/SPEC.md`

---

## 1. Goal

Document the **`Pulse` singleton surface** exported via `src/index.ts`: init lifecycle hooks, identity helpers, and manual signal APIs.

---

## 2. Assumptions

See [`../assumptions/SPEC.md`](../assumptions/SPEC.md).

---

## 3. Requirements

**R7 — Public API** — all methods no-op before init completes or after shutdown — [`../requirements/SPEC.md`](../requirements/SPEC.md).

---

## 4. Architectural Design

`Pulse` delegates to `PulseSDK` singleton — see [`../architecture-and-bootstrap/SPEC.md`](../architecture-and-bootstrap/SPEC.md).

---

## 5. LLD

### 5.1 Public API (`Pulse.*`)

| Method | Description |
|---|---|
| `Pulse.init(config)` | Bootstrap the SDK. Returns `Promise<void>`. |
| `Pulse.shutdown()` | Tear down — uninstall instrumentations, flush, reset singleton. |
| `Pulse.whenReady()` | Resolves when async init finishes, or immediately if already initialized. |
| `Pulse.isInitialized()` | Sync check for whether init has completed. |
| `Pulse.setScreenName(name)` | Update `screen.name` on all subsequent signals. |
| `Pulse.setUserId(id \| null)` | Set / clear user identity. Emits user session transition signals. |
| `Pulse.setUserProperty(key, value)` | Set single user property (`pulse.user.<key>`). |
| `Pulse.setUserProperties(props)` | Batch set user properties. |
| `Pulse.clearUserIdentity()` | Clear persisted user ID and all properties. |
| `Pulse.trackEvent(name, attrs?, timestampMs?)` | Custom event signal (`pulse.type = custom_event`). |
| `Pulse.reportException(error, attrs?)` | Manual non-fatal (`pulse.type = non_fatal`, WARN severity). |
| `Pulse.reportDeviceCrash(error, attrs?)` | Fatal crash signal (`pulse.type = device.crash`, FATAL severity). |
| `Pulse.trackNonFatal(name, attrs?)` | Named non-fatal event (`pulse.type = non_fatal`). |

`pulse.type` / attribute contracts: [`../data-contract/SPEC.md`](../data-contract/SPEC.md). Errors instrumentation: [`../../instrumentations/errors/SPEC.md`](../../instrumentations/errors/SPEC.md).

---

## 6. Test Coverage

[`../test-coverage/SPEC.md`](../test-coverage/SPEC.md) — `sdk-public-methods.test.ts`, `sdk-lifecycle.test.ts`.

---

## 7. Known Bugs & Gaps

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) (capture API naming P0:2, identity API P0:3).

---

## 8. Redundancy & Cleanup Notes

`web-sdk-plan/INTEGRATION.md` public API fragments absorbed here.

---

## 9. Open Questions

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) §9 (`whenReady` semantics).
