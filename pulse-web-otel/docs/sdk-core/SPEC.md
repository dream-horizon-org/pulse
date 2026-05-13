# SDK Core — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/sdk-core/SPEC.md`

---

## 1. Goal

Define the **non-instrumentation core** of the Pulse Web SDK: bootstrap lifecycle, configuration, consent, remote config and gates, OTLP export pipeline, persistence, and the **`Pulse` public API**. **Session RUM** (`SessionProvider` + `SessionInstrumentation`) is specified in [`../instrumentations/session/SPEC.md`](../instrumentations/session/SPEC.md).

Topic SPECs live in **subfolders** of this directory, each with its own **`SPEC.md`** (same convention as [`../instrumentations/screen-signals/SPEC.md`](../instrumentations/screen-signals/SPEC.md)).

---

## 2. Assumptions

Summary: browser-only v0.1, bundled OTel, `localStorage` for cache/identity, Android/RN `pulse.type` parity, remote config cached then refreshed in background.

Authoritative list: [`assumptions/SPEC.md`](assumptions/SPEC.md).

---

## 3. Requirements

**R1–R10** and NFRs: [`requirements/SPEC.md`](requirements/SPEC.md).

---

## 4. Architectural Design

```
Host App
  │
  ▼
Pulse.init(config)          ← src/sdk.ts
  │
  ├─ validateConfig / consent / SessionProvider / UA / resource
  ├─ remote config cache + FeatureGate + ExportSamplingGate + processors
  ├─ createProviders + drainBufferedOtlpExports + pagehide + registry.installAll
  └─ fetchInBackground
```

Full tree and init step list: [`architecture-and-bootstrap/SPEC.md`](architecture-and-bootstrap/SPEC.md).

---

## 5. LLD

### 5.1 Topic SPEC index

| Topic | SPEC |
|--------|------|
| Assumptions | [`assumptions/SPEC.md`](assumptions/SPEC.md) |
| Requirements (R1–R10) | [`requirements/SPEC.md`](requirements/SPEC.md) |
| Architecture + bootstrap sequence | [`architecture-and-bootstrap/SPEC.md`](architecture-and-bootstrap/SPEC.md) |
| `pulse.type` + shared attributes | [`data-contract/SPEC.md`](data-contract/SPEC.md) |
| Config + consent | [`config-and-consent/SPEC.md`](config-and-consent/SPEC.md) |
| Remote config, feature gate, sampling | [`remote-config-features-and-sampling/SPEC.md`](remote-config-features-and-sampling/SPEC.md) |
| OTLP exporters + persistence | [`exporters-and-persistence/SPEC.md`](exporters-and-persistence/SPEC.md) |
| `Pulse.*` public API | [`public-api/SPEC.md`](public-api/SPEC.md) |
| Session lifecycle + logs | [`../instrumentations/session/SPEC.md`](../instrumentations/session/SPEC.md) |
| Vitest index | [`test-coverage/SPEC.md`](test-coverage/SPEC.md) |
| Known gaps + open questions | [`known-gaps-and-open-questions/SPEC.md`](known-gaps-and-open-questions/SPEC.md) |

### 5.2 Primary `src/` touchpoints

`src/sdk.ts`, `src/config.ts`, `src/consent.ts`, `src/remote-config.ts`, `src/feature-gate.ts`, `src/instrumentation-registry.ts`, `src/exporters.ts`, `src/before-send.ts`, `src/resource.ts`, `src/processors/`, `src/persistence/`, `src/sampling/`, `src/utils/ua-parser.ts`. Session: `src/session.ts`, `src/instrumentations/session.ts` → [`../instrumentations/session/SPEC.md`](../instrumentations/session/SPEC.md).

---

## 6. Test Coverage

[`test-coverage/SPEC.md`](test-coverage/SPEC.md).

---

## 7. Known Bugs & Gaps

[`known-gaps-and-open-questions/SPEC.md`](known-gaps-and-open-questions/SPEC.md).

---

## 8. Redundancy & Cleanup Notes

See [`known-gaps-and-open-questions/SPEC.md`](known-gaps-and-open-questions/SPEC.md) §8.

---

## 9. Open Questions

See [`known-gaps-and-open-questions/SPEC.md`](known-gaps-and-open-questions/SPEC.md) §9.
