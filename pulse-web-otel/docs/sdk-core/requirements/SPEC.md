# SDK Core — Requirements — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/sdk-core/requirements/SPEC.md`

---

## 1. Goal

Enumerate **functional and non-functional requirements (R1–R10)** for the SDK core. Session **implementation** detail and `session.start` / `session.end` contracts are expanded in [`../../instrumentations/session/SPEC.md`](../../instrumentations/session/SPEC.md).

---

## 2. Assumptions

See [`../assumptions/SPEC.md`](../assumptions/SPEC.md).

---

## 3. Requirements

### Functional

**R1 — Init:** `Pulse.init(config)` must be idempotent (double-call is a no-op). Returns a `Promise<void>` that resolves when async bootstrap (OS version resolution, provider wiring) completes.

**R2 — Consent gate:** `dataCollectionState !== ALLOWED` → no signals emitted, no listeners installed. The SDK must be callable (`Pulse.init`) even when consent is `PENDING` or `DENIED`; it simply exits early with no side effects.

**R3 — Feature gate:** Every instrumentation checks `FeatureGate.isEnabled(feature)` before installing event listeners. A remote config can reduce `sessionSampleRate` to 0 to disable a feature without re-deploying.

**R4 — Remote config:** `SdkConfigFetcher.loadCached()` reads `localStorage["pulse_sdk_config"]` synchronously at init. `fetchInBackground()` fires a `fetch` call post-init and persists a new version only if the remote version number differs.

**R5 — Shutdown:** `Pulse.shutdown()` must uninstall all instrumentations, remove the `pagehide` listener, force-flush all providers, and reset the singleton so a subsequent `Pulse.init()` re-bootstraps cleanly.

**R6 — Session:** `SessionProvider` assigns a `session.id` UUID on construction. It rotates the session after `pageHiddenTimeoutMs` of backgrounding (default 30 min). Sessions persist `installationId` and `userId` to `localStorage`. **LLD:** [`../../instrumentations/session/SPEC.md`](../../instrumentations/session/SPEC.md).

**R7 — Public API:** All methods on `Pulse` must silently no-op when called before `init` completes or after `shutdown`. **Surface table:** [`../public-api/SPEC.md`](../public-api/SPEC.md).

**R8 — platform=web mandate:** Every signal emitted by the SDK must carry `platform = 'web'` as an OTel Resource attribute (`os.name = 'web'`). This is set once in `buildMergedResource()` and is not overridable by the host app.

**R9 — Export sampling:** `ExportSamplingGate` evaluates session-level sampling rules at export time (not span-creation time), preserving parent/child span sampling consistency.

**R10 — IndexedDB drain:** On init, if `diskBuffering.enabled !== false`, the SDK replays any buffered OTLP batches from IndexedDB that were written by a previous session that crashed before flushing.

### Non-functional

- **Bundle size:** gated by `size-limit` in CI. No lodash, moment, or Node-only deps.
- **Logging:** All internal logs route through `PulseWebLogger`; consumers can silence via `logLevel: PulseLogLevel.NONE`.
- **Thread safety:** Init is re-entrant safe via `_initializing` guard. Concurrent `init()` calls during async bootstrap return the same in-flight promise.

---

## 4. Architectural Design

Requirements trace to modules in [`../architecture-and-bootstrap/SPEC.md`](../architecture-and-bootstrap/SPEC.md).

---

## 5. LLD

### 5.1 Traceability sketch

| Req | Primary `src/` |
|-----|----------------|
| R1, R5, R7 | `sdk.ts` |
| R2 | `consent.ts`, `sdk.ts` |
| R3, R4, R9 | `feature-gate.ts`, `remote-config.ts`, `sampling/export-sampling-gate.ts` |
| R6 | `session.ts`, `instrumentations/session.ts` |
| R8 | `resource.ts`, `processors/global-attrs-processor.ts` |
| R10 | `persistence/`, `exporters.ts` |

---

## 6. Test Coverage

See [`../test-coverage/SPEC.md`](../test-coverage/SPEC.md).

---

## 7. Known Bugs & Gaps

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md).

---

## 8. Redundancy & Cleanup Notes

None.

---

## 9. Open Questions

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) §9.
