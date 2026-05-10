# Interactions Feature — SPEC.md

Package: `@dreamhorizon/pulse-web`  
File: `pulse-web-otel/docs/instrumentations/interactions/SPEC.md`

---

## 1. Goal

The **interactions feature** is **distinct from raw click instrumentation** (`app.click` logs). It consumes internal click + navigation events (via coordinator hooks) to match **named interaction sequences** defined in remote config, then emits **product spans/logs** for completed sequences (`interaction.*` attributes). **Click heatmaps** are **deferred** — not part of this subsystem.

---

## 2. Assumptions

- **Heatmap:** **Deferred** — see §7 / §9; matrix scenarios captured historically in planning docs only.
- **Mobile parity gap:** Android/RN may expose different named-flow primitives — web relies on DOM click + screen signals only.

---

## 3. Requirements

**R1 — Feature class:** `InteractionFeature` wires coordinator, config fetcher, tracker, matcher, span builder.

**R2 — Remote definitions:** Interaction definitions fetched from Pulse backend using API key / endpoint (see `interactions-config-fetcher`).

**R3 — Gate:** Honour `instrumentations.interactions.enabled` and feature gate.

**R4 — SDK wiring:** `InteractionInstrumentation` registers separately from base registry batch in places — exposes `trackEvent` passthrough for testing.

---

## 4. Architectural Design

### Coordinator / tracker / matcher / builder pipeline

```
InteractionInstrumentation.install()
  └─ InteractionFeature.init()
        ├─ ConfigFetcher → merged definitions
        ├─ Coordinator subscribes to click + navigation internal events
        ├─ Tracker holds partial sequence state per session
        ├─ SequenceMatcher tests events vs configured patterns
        └─ SpanBuilder emits OTel records when a sequence completes
```

---

## 5. LLD

### 5.1 Attributes (completed interaction)

| Attribute key | Type | Source | Required | Notes |
|---|---|---|---|---|
| `pulse.type` | string | feature | Yes | Product-defined — align with backend registry |
| `interaction.id` | string | definition | Yes | Stable id from remote config |
| `interaction.name` | string | definition | Yes | Human label |
| `interaction.duration_ms` | number | coordinator | Yes | Wall time for matched sequence |
| `session.id` | string | session provider | Yes | |
| `screen.name` | string | global attrs | No | Current screen |
| `platform` | string | resource | Yes | `web` |

### 5.2 Algorithm (high level)

1. Normalised click / navigation events enter **Coordinator**.
2. **Tracker** maintains candidates per active definition.
3. **SequenceMatcher** advances state machine; timeout/eviction rules apply.
4. On terminal match, **SpanBuilder** emits telemetry.

### 5.3 React / Next.js

- Runs in browser after **`Pulse.init`**; relies on clicks + navigation instrumentation being active — see **`clicks`** and **`screen-signals`** SPECs.

---

## 6. Test Coverage

Files (each exercises scenarios described in filename):

1. `src/__tests__/interaction-feature.test.ts`
2. `src/__tests__/interaction-instrumentation.test.ts`
3. `src/__tests__/interactions-coordinator.test.ts`
4. `src/__tests__/interactions-tracker.test.ts`
5. `src/__tests__/interactions-config-fetcher.test.ts`
6. `src/__tests__/interactions-sdk-wiring.test.ts`
7. `src/__tests__/interactions-sequence-matcher.test.ts`
8. `src/__tests__/interactions-span-builder.test.ts`
9. `src/__tests__/interactions-events-utils.test.ts`

Plus `interaction-feature-integration.test.ts` for cross-module flows.

---

## 7. Known Bugs & Gaps

### P2: Heatmap deferred

**Heatmap / rage grid** analytics — **deferred** future work (not a runtime bug).

### P0:

No **P0** data-contract defects filed here at synthesis.

---

## 8. Redundancy & Cleanup Notes

Deleted after triple-eval:

| Path |
|---|
| `pulse-web-otel/web-sdk-plan/interactions/INTERACTION-SCENARIO-MATRIX.md` |

---

## 9. Open Questions

1. When remote definitions fail to fetch, should sequences silently disable or retry backoff?
