# SDK Core — Test coverage — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/sdk-core/test-coverage/SPEC.md`

---

## 1. Goal

Index **Vitest coverage** for SDK core modules (lifecycle, config, resource, remote config, session provider hooks, pagehide, integration init).

---

## 2. Assumptions

Tests run in jsdom / Vitest with OTLP fakes — see package `CLAUDE.md`.

---

## 3. Requirements

Maps to **R1–R10** verification — [`../requirements/SPEC.md`](../requirements/SPEC.md).

---

## 4. Architectural Design

**N/A** — this file is a coverage index only.

---

## 5. LLD

### 5.1 `src/__tests__/sdk-lifecycle.test.ts`

Tests for SDK singleton lifecycle, shutdown guards, restart cycles, and the race condition between `shutdown()` and `finishInit()`:

- `shouldInitializeSuccessfully` — `Pulse.init()` completes and `isInitialized()` returns true
- `shouldBeIdempotentOnDoubleInit` — second `init()` call is a no-op
- `shouldNoOpWhenConsentIsDenied` — `DENIED` state → `isInitialized()` false
- `shouldNoOpWhenConsentIsPending` — `PENDING` state → `isInitialized()` false
- `shouldShutdownAndResetState` — after `shutdown()`, `isInitialized()` returns false
- `shouldAllowReinitAfterShutdown` — shutdown then re-init works cleanly
- `shouldReturnSamePromiseOnConcurrentInit` — concurrent calls during async bootstrap return same promise
- `shouldAbortInitInSSR` — no `window` → init aborts without error
- `shouldHandleShutdownRaceWithFinishInit` — shutdown called before finishInit async chain settles

### 5.2 `src/__tests__/sdk-public-methods.test.ts`

Unit tests for public SDK methods covering previously-uncovered code paths:

- `trackEvent` — correct `pulse.type = custom_event`, correct attributes emitted
- `reportException` — correct `pulse.type = non_fatal`, `SeverityNumber.WARN`, non-Error coercion
- `reportDeviceCrash` — correct `pulse.type = device.crash`, `SeverityNumber.FATAL`, stack trace
- `trackNonFatal` — correct `pulse.type = non_fatal`, `non_fatal.is_manual = true`
- `setUserProperties` — merge semantics, null removes key
- `clearUserIdentity` — clears persisted userId + properties
- `setScreenName` — no-op before init; updates globalAttrsProcessor after init
- All methods are no-op before `init()` completes

### 5.3 `src/__tests__/m1.test.ts`

Foundation tests — validates M1 milestone contracts:

- `getOrCreateInstallationId` — creates UUID on first call, returns same on repeat
- `wasNewInstallation` — true first time, false thereafter
- `SessionProvider` — session ID assigned, rotation on backgrounding
- `validateConfig` — throws on missing `apiKey`, passes on valid config
- `isLocalEnvironment` / `resolveEndpointBaseUrl` — local dev key detection
- `buildResource` / `extractProjectId` — resource attribute correctness
- `SdkConfigFetcher.loadCached` — default config when no cache, parsed config from valid cache
- `resolveConfigUrl` — local vs prod URL resolution
- `FeatureGate.isEnabled` — default true when no config, disabled when `sessionSampleRate = 0`
- `PulseGlobalAttributesProcessor` — session ID, screen name, user ID stamped on all signals
- `SessionInstrumentation` — `session.start` emitted on install

### 5.4 `src/__tests__/m3.test.ts`

Error instrumentation / `device.crash` and `non_fatal` contract tests (cross-links errors SPEC).

### 5.5 `src/__tests__/m8.test.ts`

TC 8.x — `pagehide` listener lifecycle:

- Registration count: exactly one `pagehide` listener after init
- BFCache guard: `event.persisted = true` → no flush called
- `forceFlush` called on `pagehide` when `persisted = false`
- `shutdown()` removes the listener
- Restart (shutdown + re-init) rebalances listener count to one
- SSR guard: `window` undefined → no listener registration
- Post-shutdown: pagehide fires after shutdown → no-op (no double flush)

### 5.6 `src/__tests__/integration-simplified-init.test.ts`

Config surface tests — verifies Web SDK matches Android's minimal public API:

- `apiKey` required — throws without it
- `dataCollectionState` required at init
- `diskBuffering.enabled` defaults on (Android parity)
- `beforeSendData` shape validation (function vs object vs invalid)
- `diskBuffering.maxAgeMs` and `maxCacheSizeBytes` positive-finite validation
- `globalAttributes` and `resourceAttributes` accepted without error

---

## 6. Test Coverage

This document **is** §6 content for the sdk-core spec set; see subsections in §5.

---

## 7. Known Bugs & Gaps

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md).

---

## 8. Redundancy & Cleanup Notes

Prior `web-sdk-plan/v1/01-foundation/README.md` test pointers absorbed here.

---

## 9. Open Questions

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) §9.
