# Interactions — Implementation Log

Tracks what has been done, what is in progress, and what is pending.
Fix plan reference: `interaction-fix.md` (do not edit).

---

## ISS-I01 — Click bridge: `app.click` logs feed interaction matcher

**Status:** ✅ Done

**What was done:**
- Created `src/processors/interaction-log-processor.ts` — `InteractionLogProcessor` with Branch A only (click bridge).
- `onEmit`: if `pulse.type === APP_CLICK` → `logRecordBodyAsString` body + `hrTimeToMilliseconds(logRecord.hrTime)` → `instr.trackEvent(body, attrs, timeMs)`.
- `null` instrumentation guard — no throw.
- `sdk.ts`: `interactionLogProcessor` field; inserted in `logProcessors` after `globalAttrsProcessor`; `setInstrumentation(interactionInstrumentation)` after `registerAndInstall`; `setInstrumentation(null)` before `_providerCleanup` in shutdown.
- Unit tests: `src/__tests__/interaction-log-processor.test.ts` — 14 cases covering click bridge (Branch A), marker events (Branch B), neutral types, null guard, lifecycle.
- SDK wiring tests: 2 new cases in `src/__tests__/interactions-sdk-wiring.test.ts` — processor in pipeline, ordering (after global attrs, before filter).
- E2E: 3 new `@click-bridge` tests in `examples/ecommerce-demo/e2e/m2-interactions.spec.ts` — single click, rage click, unrelated click (timeout).

**Files changed:**
- `src/processors/interaction-log-processor.ts` (new)
- `src/sdk.ts`
- `src/__tests__/interaction-log-processor.test.ts` (new)
- `src/__tests__/interactions-sdk-wiring.test.ts` (updated)
- `examples/ecommerce-demo/e2e/m2-interactions.spec.ts` (updated)

---

## ISS-I02 — Marker events exported as span events

**Status:** ✅ Done

**What was done:**
- `interaction-span-builder.ts`: read `MARKER_EVENTS` from props via `toLocalEvents`; second loop after `localEvents` loop calls `span.addEvent` for each marker. Debug log updated with `markerEventCount`.
- Unit tests: 5 new cases in `interactions-span-builder.test.ts` — local+marker combined, markers-only, empty markers, absent markers, error span with markers.

**Files changed:**
- `src/interactions/interaction-span-builder.ts`
- `src/__tests__/interactions-span-builder.test.ts`

---

## ISS-I03 — `addMarkerToAll` chain + Branch B in processor

**Status:** ✅ Done

**What was done:**
- `interaction-log-processor.ts`: Branch B added before Branch A — `DEVICE_CRASH`/`NON_FATAL` → `logRecordBodyAsString` → `instr.addMarkerToAll(body, attrs, timeMs)`. Returns early so crash logs never also hit Branch A.
- `interaction.ts` (`InteractionInstrumentation`): `addMarkerToAll(name, attrs, timestampMs)` — delegates to `this.feature?.addMarkerToAll(...)`.
- `interaction-feature.ts` (`InteractionFeature`): `addMarkerToAll` with gate guards (`interactionsEnabledByConfig`, `PulseFeature.INTERACTION`, `initialized`) → `this.coordinator.addMarkerToAll(name, attrs, timestampMs)`.
- `interaction-coordinator.ts` (`InteractionCoordinator`): `addMarkerToAll` — `toInteractionLocalEvent(name, attrs, timeMs)` → fan-out `tracker.addMarker(ev)` for each tracker.
- Unit tests: 14/14 in `interaction-log-processor.test.ts` (Branch B: 5 cases); 5/5 in `interactions-coordinator.test.ts` (addMarkerToAll fan-out + no-op).
- E2E: 9/9 ecommerce-demo `@marker` tests (chromium/firefox/webkit); 3/3 nextjs-demo `@marker` tests.

**Files changed:**
- `src/processors/interaction-log-processor.ts` (Branch B added)
- `src/instrumentations/interaction.ts` (`addMarkerToAll` added)
- `src/interactions/interaction-feature.ts` (`addMarkerToAll` added)
- `src/interactions/interaction-coordinator.ts` (`addMarkerToAll` added)
- `src/__tests__/interaction-log-processor.test.ts` (updated — Branch B cases)
- `src/__tests__/interactions-coordinator.test.ts` (updated — 2 new marker tests)
- `examples/ecommerce-demo/e2e/m2-interactions.spec.ts` (updated — `@marker` describe)
- `examples/nextjs-demo/e2e/nextjs-demo.spec.ts` (updated — `@M2 interactions marker events` describe)
- `examples/nextjs-demo/e2e/fixture.ts` (updated — `OtlpSpanEvent` interface, `events` on `OtlpSpan`, removed duplicate `findAllSpans`)

---

## ISS-I08 — Unit test: config fetch failure → idle matcher

**Status:** 🔲 Pending

---

## ISS-I09 — Unit test: marker span.addEvent coverage

**Status:** ✅ Done (fixed as part of ISS-I02)

---

## ISS-I10 — Unit tests: marker intake in tracker / matcher

**Status:** 🔲 Pending

---

## ISS-I11 — E2E: real DOM click completes a flow

**Status:** ✅ Done (fixed as part of ISS-I01)

---

## ISS-I12 — E2E: interaction in Next.js demo

**Status:** ✅ Done (fixed as part of ISS-I01)

