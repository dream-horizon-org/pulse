# SDK Core — Assumptions — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/sdk-core/assumptions/SPEC.md`

---

## 1. Goal

Capture **platform and product assumptions** that bound the Pulse Web SDK core (browser-only delivery, storage expectations, bundled OTel, cross-platform `pulse.type` parity).

---

## 2. Assumptions

- The SDK runs in browser environments only. `window` / `globalThis.window` must be defined; SSR / Node environments receive a no-op init.
- The host app supplies `apiKey` and `dataCollectionState` at init time. Both are required.
- All instrumentations run on the browser main thread. No Web Worker support in v0.1.
- `localStorage` is available for session/user persistence and SDK config cache. A quota of ~5 MB is assumed; the persistence module truncates oldest-first on overflow.
- OTel SDK (traces, logs, metrics) is bundled with the SDK — not expected as a peer dep from the host.
- Android and React Native SDKs share the same `pulse.type` semantic convention; web must not diverge.
- Remote config is fetched in the background after init; the locally cached version from the previous session is used immediately to gate instrumentations.

---

## 3. Requirements

Numbered requirements **R1–R10** live in [`../requirements/SPEC.md`](../requirements/SPEC.md). This document does not restate them.

---

## 4. Architectural Design

**N/A** — assumptions are preconditions, not a component diagram. Bootstrap shape: [`../architecture-and-bootstrap/SPEC.md`](../architecture-and-bootstrap/SPEC.md).

---

## 5. LLD

### 5.1 Downstream consumers

| Consumer | Path |
|----------|------|
| Requirements index | [`../requirements/SPEC.md`](../requirements/SPEC.md) |
| Rollup / map | [`../SPEC.md`](../SPEC.md) |

---

## 6. Test Coverage

Assumptions are validated indirectly via [`../test-coverage/SPEC.md`](../test-coverage/SPEC.md) (SSR abort, consent no-op, `localStorage` usage in integration tests).

---

## 7. Known Bugs & Gaps

No assumptions-specific P0 items. Product-level gaps: [`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md).

---

## 8. Redundancy & Cleanup Notes

None.

---

## 9. Open Questions

See [`../known-gaps-and-open-questions/SPEC.md`](../known-gaps-and-open-questions/SPEC.md) §9.
