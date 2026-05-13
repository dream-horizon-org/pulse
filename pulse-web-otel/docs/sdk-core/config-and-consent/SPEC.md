# SDK Core — Configuration and consent — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/sdk-core/config-and-consent/SPEC.md`

---

## 1. Goal

Specify **`PulseWebConfig`** and the **consent gate** that blocks all telemetry when collection is not `ALLOWED`.

---

## 2. Assumptions

See [`../assumptions/SPEC.md`](../assumptions/SPEC.md).

---

## 3. Requirements

**R2 — Consent gate** — see [`../requirements/SPEC.md`](../requirements/SPEC.md). Config validation is covered by **R1** / `validateConfig` in [`../architecture-and-bootstrap/SPEC.md`](../architecture-and-bootstrap/SPEC.md).

---

## 4. Architectural Design

`Pulse.init` → `validateConfig` → `isDataCollectionAllowed` before async bootstrap — see [`../architecture-and-bootstrap/SPEC.md`](../architecture-and-bootstrap/SPEC.md).

---

## 5. LLD

### 5.1 Config surface (`PulseWebConfig`)

```ts
{
  apiKey: string;                        // required — "projectId_secret"
  dataCollectionState: PulseDataCollectionConsent; // required — ALLOWED | DENIED | PENDING
  serviceName?: string;                  // defaults to empty string
  serviceVersion?: string;              // app build version
  globalAttributes?: Record<string, string>;
  resourceAttributes?: Record<string, string>;
  instrumentations?: InstrumentationConfig;
  export?: { format: "protobuf" | "json" };
  logLevel?: PulseLogLevel;
  diskBuffering?: PulseWebDiskBufferingConfig;
  beaconRelayUrl?: string;
  beforeSendData?: PulseWebBeforeSendConfig;
  endpoint?: string;                    // internal override; not in public docs
  pageHiddenTimeoutMs?: number;         // session rotation timeout
}
```

`PulseDataCollectionConsent` enum: `ALLOWED`, `DENIED`, `PENDING`.

### 5.2 Consent gate

`src/consent.ts`: `isDataCollectionAllowed(state)` returns `true` only for `PulseDataCollectionConsent.ALLOWED`. `PENDING` and `DENIED` both return `false`.

The consent check runs twice:

1. In `Pulse.init()` before any async work — prevents session/resource construction.
2. In `Pulse.trackEvent()` for the interaction path — runtime re-check.

If consent changes at runtime the host must unmount and remount `PulseProvider` (or call `shutdown()` then re-`init()`). The SDK does not support live consent flipping.

---

## 6. Test Coverage

[`../test-coverage/SPEC.md`](../test-coverage/SPEC.md) — `sdk-lifecycle.test.ts` (denied/pending), `integration-simplified-init.test.ts`.

---

## 7. Known Bugs & Gaps

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) (e.g. consent API / enum ergonomics).

---

## 8. Redundancy & Cleanup Notes

`web-sdk-plan/INTEGRATION.md` config fragments absorbed here and in [`../public-api/SPEC.md`](../public-api/SPEC.md).

---

## 9. Open Questions

[`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) §9.
