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
- Unit tests: `src/__tests__/interaction-log-processor.test.ts` — 12 cases covering click bridge, marker non-calls, neutral types, null guard, lifecycle.
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

**Status:** 🔲 Pending

---

## ISS-I03 — `addMarkerToAll` chain + Branch B in processor

**Status:** 🔲 Pending (depends on ISS-I01 processor existing)

---

## ISS-I08 — Unit test: config fetch failure → idle matcher

**Status:** 🔲 Pending

---

## ISS-I09 — Unit test: marker span.addEvent coverage

**Status:** 🔲 Pending (depends on ISS-I02)

---

## ISS-I10 — Unit tests: marker intake in tracker / matcher

**Status:** 🔲 Pending (depends on ISS-I03)

---

## ISS-I11 — E2E: real DOM click completes a flow

**Status:** 🔲 Pending (depends on ISS-I01)

---

## ISS-I12 — E2E: interaction in Next.js demo

**Status:** 🔲 Pending

