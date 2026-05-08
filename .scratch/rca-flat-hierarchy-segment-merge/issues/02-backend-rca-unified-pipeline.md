**Triage:** done

## Parent

[PRD](../PRD.md) · [Technical plan](../TECHNICAL-PLAN.md)

## What to build

End-to-end **interaction + screen** RCA computation changes: after baseline, run a **flat 1D pass** (canonical per-dimension top buckets), run the **existing hierarchical drill** when the first dimension is picked, **exclude 1D hierarchical rows and flat-extras** from the hierarchical candidate set (global flat pass owns 1D), **merge + cap** via the module from `issues/01-merge-sort-cap-module.md`, then **`applySignalGate`** on the **final** list. Add **`RootCauseAnalysisMode.HYBRID`** (`wireValue` `hybrid`) when both tiers contribute; **`FLAT`** when hierarchy does not run. Update **ClickHouse cache** `mode` column semantics for new rows.

## Acceptance criteria

- [x] `RootCauseService` and `ScreenRcaService` both use the same merged outcome policy — **`RcaHybridMergeOutcome`** (`mergeForInteraction` / `mergeForScreen`) delegates to **`RcaSegmentMergePolicy`** + shared **`modeFromHierarchicalTier`** (PRD §Implementation Decisions).
- [x] Hierarchical path **does not** add **single-dimension** materialized segments to the hierarchical tier; **flat extras** path reconciled with global flat pass per PRD.
- [x] **Combined signal gate** runs **after** merge+cap; ordering of kept segments documented in tests (`RootCauseServiceTest` signal-gate case; `SegmentSignalGate` javadoc for screen driver key).
- [x] **`HYBRID`** enum: Jackson + `fromWireValue` recognizes `hybrid`; comments distinguish **`HYBRID`** mode from **`RootCauseConfig.isHybridDimensionOrderingEnabled()`** (dimension reordering).
- [x] `RootCauseServiceTest` / `ScreenRcaServiceTest` cover hierarchy + flat merge, flat-only fallback, **no duplicate** 1D from hierarchy, **mode** = `HYBRID` vs `FLAT`, segment **order** assertions; **`RcaHybridMergeOutcomeTest`** for merge helper.
- [x] `GET` interaction / screen root-cause responses expose **`mode`** (`hybrid` wire value) where applicable; existing JSON shape preserved.

## Blocked by

- `issues/01-merge-sort-cap-module.md` (**complete**)

## User stories covered

PRD **1–5, 10–11, 13–15, 17–18, 24, 27–29, 31–33, 35–37, 39–40** (primary backend slice).
