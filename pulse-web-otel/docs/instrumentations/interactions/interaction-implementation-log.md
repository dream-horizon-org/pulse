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

## ISS-I04 — `InteractionContextSpanProcessor`: forward stamp + reverse span→event feed

**Status:** ✅ Done

**What was done:**
- `src/semconv.ts`: Added `NAMES: "pulse.interaction.names"` and `IDS: "pulse.interaction.ids"` under `InteractionAttributeKey`.
- `src/interactions/interaction-coordinator.ts`: Added `getRunningInteractions()` — flat-maps tracker statuses, filters `kind === "ongoing" && interaction === null` (mid-sequence only), maps to `{ id, name }`.
- `src/interactions/interaction-feature.ts`: Added `getRunningInteractions()` with same gate guards as `addMarkerToAll`.
- `src/instrumentations/interaction.ts`: Added `getRunningInteractions()` delegating to `feature`.
- `src/processors/interaction-context-span-processor.ts` (new): `onStart` stamps NAMES/IDS on in-flight spans (skips `pulse.type=interaction`). `onEnd` reverse-feeds `screen_load`, `screen_session`, `network.*` span ends into `trackEvent(pulseType, spanAttrs, timeMs)` — passes **full ended-span attributes** as `PulseAttributes` (not `{}`). Callbacks injected via `setGetRunning` / `setTrackEvent`; nulled on shutdown.
- `src/sdk.ts`: Added `interactionContextSpanProcessor` field; inserted in `spanProcessors` between `globalAttrsProcessor` and `filterProcessor`; wired callbacks after `registerAndInstall`; cleared in `shutdown`.
- `src/__tests__/interaction-context-span-processor.test.ts` (new): 13 cases — 5 `onStart`, 6 `onEnd`, 2 lifecycle.
- `src/__tests__/interactions-coordinator.test.ts`: 4 new `getRunningInteractions()` cases.
- `src/__tests__/interactions-sdk-wiring.test.ts`: 2 new SDK span pipeline cases.
- E2E: 4 new `@M2 interaction-context-span` tests in `ecommerce-demo/e2e/m2-interactions.spec.ts` — all pass (chromium/firefox/webkit). 4 matching tests in `nextjs-demo/e2e/nextjs-demo.spec.ts` — all pass (chromium).

**ADR decisions (locked):**
- `string[]` native — no JSON encoding, no join fallback
- No cap on array length
- `app_start` omitted — no Web span equivalent (installation start is a log)
- Log context stamping deferred

**Files changed:**
- `src/semconv.ts`
- `src/interactions/interaction-coordinator.ts`
- `src/interactions/interaction-feature.ts`
- `src/instrumentations/interaction.ts`
- `src/processors/interaction-context-span-processor.ts` (new)
- `src/sdk.ts`
- `src/__tests__/interaction-context-span-processor.test.ts` (new)
- `src/__tests__/interactions-coordinator.test.ts` (updated)
- `src/__tests__/interactions-sdk-wiring.test.ts` (updated)
- `examples/ecommerce-demo/e2e/m2-interactions.spec.ts` (updated)
- `examples/nextjs-demo/e2e/nextjs-demo.spec.ts` (updated)

**Plan reference:** `Interaction-fix-till-bug4.md` Part 2 (Bug 4).

### What to build

#### A — Semconv (`src/semconv.ts`)
Add under `InteractionAttributeKey`:
- `NAMES: "pulse.interaction.names"` — `string[]` of mid-sequence flow names
- `IDS: "pulse.interaction.ids"` — `string[]` of parallel interaction IDs

#### B — `getRunningInteractions()` delegation chain
**`InteractionCoordinator`**: flat-map `tracker.getStatuses()`, filter `kind === "ongoing" && interaction === null` (mid-sequence only; `interaction !== null` = completed/terminal), map to `{ id: string; name: string }` from `interactionId` + `interactionConfig.name`.

**`InteractionFeature`**: delegate to coordinator with same gate guards as `addMarkerToAll` (`interactionsEnabledByConfig`, `gate.isEnabled(INTERACTION)`, `initialized`). Returns `[]` when gated.

**`InteractionInstrumentation`**: `getRunningInteractions() → this.feature?.getRunningInteractions() ?? []`

#### C — `InteractionContextSpanProcessor` (new: `src/processors/interaction-context-span-processor.ts`)
Two injected nullable callbacks: `getRunning: (() => { id: string; name: string }[]) | null` and `trackEvent: ((name, attrs, timeMs) => void) | null`. Setters: `setGetRunning` / `setTrackEvent`.

**`onStart`**:
- If `getRunning == null` or `running.length === 0` → return
- Skip spans where `pulse.type === "interaction"` (no self-referential stamp)
- `span.setAttribute(NAMES, running.map(r => r.name))` + `span.setAttribute(IDS, running.map(r => r.id))` — native `string[]`, no encoding, no cap

**`onEnd`**:
- If `trackEvent == null` → return
- Read `pulse.type` from ended span attributes
- Eligible types: `screen_load`, `screen_session`, and any `pulse.type.startsWith("network.")`
- If eligible: `trackEvent(pulseType, {}, Math.round(span.endTime[0] * 1000 + span.endTime[1] / 1_000_000))`
- `pulse.type === interaction` and all other types → no-op

**Lifecycle**: `forceFlush()` + `shutdown()` → `Promise.resolve()`.

#### D — SDK wiring (`src/sdk.ts`)
- New field: `private readonly interactionContextSpanProcessor = new InteractionContextSpanProcessor()`
- `spanProcessors` array: `[this.globalAttrsProcessor, this.interactionContextSpanProcessor, filterProcessor]`
- After `registerAndInstall(interactionInstrumentation)`:
  - `this.interactionContextSpanProcessor.setGetRunning(() => this.interactionInstrumentation!.getRunningInteractions())`
  - `this.interactionContextSpanProcessor.setTrackEvent((name, attrs, timeMs) => this.interactionInstrumentation!.trackEvent(name, attrs, timeMs))`
- Shutdown (before `_providerCleanup()`):
  - `this.interactionContextSpanProcessor.setGetRunning(null)`
  - `this.interactionContextSpanProcessor.setTrackEvent(null)`

### Unit tests

**`src/__tests__/interaction-context-span-processor.test.ts`** (11 cases):

| Case | Expect |
|---|---|
| 0 running flows → `onStart` | no NAMES/IDS attrs |
| 1 mid-sequence → `onStart` | NAMES/IDS set |
| 2 concurrent flows → `onStart` | both flows in arrays |
| Completed flow (interaction ≠ null) | not in running list |
| `onStart` span is `pulse.type=interaction` | no stamp |
| `onEnd` `screen_load` | `trackEvent("screen_load", …)` |
| `onEnd` `network.200` / `network.404` | `trackEvent` called |
| `onEnd` `interaction` | no `trackEvent` |
| `onEnd` ineligible (e.g. `session.start`) | no `trackEvent` |
| `trackEvent` null guard | no throw |
| `getRunning` null guard | no throw |

**`src/__tests__/interactions-coordinator.test.ts`** — 4 new `getRunningInteractions()` cases: empty, mid-sequence only, concurrent, excludes completed.

### E2E

New `@M2 interaction-context-span` describe in `m2-interactions.spec.ts` (4 tests):
1. **Stamp in-flight** — step_1 fired; fetch during flow → network span has `pulse.interaction.names`/`ids`
2. **No stamp after complete** — finish flow; another fetch → network span has no names/ids
3. **Reverse `screen_load`** — config `[step_1, screen_load]`; navigate → interaction completes `is_error=false`
4. **Reverse `network.200`** — config `[step_1, network.200]`; fetch → interaction completes `is_error=false`

Mirror 4 equivalent tests in `nextjs-demo.spec.ts`.

### ADR decisions (locked — do not revisit without discussion)
- `string[]` native — no JSON encoding, no join fallback
- No cap on array length
- `app_start` omitted on Web (no span equivalent; installation start is a log)
- Log context stamping deferred (`log-context-stamp-defer` follow-up)

### Files to change
- `src/semconv.ts`
- `src/interactions/interaction-coordinator.ts` (`getRunningInteractions` added)
- `src/interactions/interaction-feature.ts` (`getRunningInteractions` added)
- `src/instrumentations/interaction.ts` (`getRunningInteractions` added)
- `src/processors/interaction-context-span-processor.ts` (new)
- `src/sdk.ts`
- `src/__tests__/interaction-context-span-processor.test.ts` (new)
- `src/__tests__/interactions-coordinator.test.ts` (updated — `getRunningInteractions` cases)
- `src/__tests__/interactions-sdk-wiring.test.ts` (updated — context processor in span pipeline)
- `examples/ecommerce-demo/e2e/m2-interactions.spec.ts` (updated — `@M2 interaction-context-span`)
- `examples/nextjs-demo/e2e/nextjs-demo.spec.ts` (updated — mirror 4 tests)

---

## Test coverage expansion — INT-P batch (all remaining gaps)

**Status:** ✅ Done

**What was done:**

### E2E — nextjs parity batch-2 (14 cases, INT-P13/14/20/23/27-32/36-39)
New describe `"@M2 interactions — Next.js parity batch-2"` in `nextjs-demo.spec.ts`. 14/14 passing (chromium).
- Added `emitEventAt(page, name, ts, props?)` helper for timestamp-override tests (P28, P37, P38)
- Extended `makeParityInteractionConfig` to support `isBlacklisted` on individual events (P23)
- Added `expectNoInteractionSpansNx`, `setUserIdNx` helpers

### E2E — unit-parity (INT-P09/P35/P41) in both ecommerce + nextjs
New describe `"@M2 interactions — unit-parity E2E"` in `m2-interactions.spec.ts` — 3/3 chromium.
New describe `"@M2 interactions — Next.js unit-parity E2E"` in `nextjs-demo.spec.ts` — 3/3 chromium.
- INT-P09: span.events carry timestamps matching emitted event times
- INT-P35: empty definitions → no span
- INT-P41: error span forces user_category=Poor + apdex_score=0
- INT-P16 (remote gate OFF) and INT-P17 (local disabled) remain unit-only; E2E infra doesn't expose a gate-disable hook at test time.

### Unit tests — unautomated cases (INT-P24/25/26/33/34/42)
- **INT-P24** (`interaction-feature.test.ts`): trackEvent before init → no-op
- **INT-P25** (`interaction-feature.test.ts`): trackEvent after shutdown → no-op
- **INT-P33** (`interaction-feature.test.ts`): shutdown mid-partial → no throw, fetcher+coordinator torn down, subsequent trackEvent dropped
- **INT-P26** (`interactions-tracker.test.ts`): second step alone (first never fired) → no terminal
- **INT-P34** (`interactions-coordinator.test.ts`): setConfigs mid-flight → old trackers destroyed, no spurious terminal
- **INT-P42** (`interactions-span-builder.test.ts`): missing CONFIG_ID → span attribute is `""`

All 898 unit tests pass (62 files).

**Files changed:**
- `examples/nextjs-demo/e2e/nextjs-demo.spec.ts` (batch-2 + unit-parity describe + new helpers)
- `examples/ecommerce-demo/e2e/m2-interactions.spec.ts` (unit-parity describe)
- `src/__tests__/interaction-feature.test.ts` (INT-P24, P25, P33)
- `src/__tests__/interactions-tracker.test.ts` (INT-P26)
- `src/__tests__/interactions-coordinator.test.ts` (INT-P34)
- `src/__tests__/interactions-span-builder.test.ts` (INT-P42)

---

## ISS-I08 — Unit test: config fetch failure → idle matcher

**Status:** ✅ Done. Added 1 test `"config fetch returns empty → trackEvent is no-op (INT-E1)"` to `interaction-feature.test.ts`. Verifies: gate-enabled + fetcher returns `[]` → `setConfigs([])` + `trackEvent` still delegates to coordinator.

---

## ISS-I09 — Unit test: marker span.addEvent coverage

**Status:** ✅ Done (fixed as part of ISS-I02)

---

## ISS-I10 — Unit tests: marker intake in tracker / matcher

**Status:** ✅ Done. Added 4 tracker tests in new describe `"InteractionTracker — marker events"` in `interactions-tracker.test.ts` (mid-flow marker included, pre-flow marker excluded, multiple markers, no markers); added 5 matcher tests in new describe `"buildPulseInteraction — marker slicing"` in `interactions-sequence-matcher.test.ts` (within window, outside window, boundary inclusion, empty, error-flow). Files changed: `interactions-tracker.test.ts`, `interactions-sequence-matcher.test.ts`, `interaction-feature.test.ts`.

---

## ISS-I11 — E2E: real DOM click completes a flow

**Status:** ✅ Done (fixed as part of ISS-I01)

---

## ISS-I12 — E2E: interaction in Next.js demo

**Status:** ✅ Done (fixed as part of ISS-I01)

