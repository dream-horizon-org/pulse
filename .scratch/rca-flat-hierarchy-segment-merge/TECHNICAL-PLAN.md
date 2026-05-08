# Technical plan: RCA hybrid segment pipeline

**Parent:** [PRD](./PRD.md)

## Goal

Implement the PRD’s unified pipeline for **interaction** and **screen** RCA (both use the same patterns in `RootCauseService` / `ScreenRcaService`): always collect **flat 1D** segments, run **hierarchical** drilling with the existing **similarity threshold**, keep only **2D+** hierarchical materializations for the merge list, **merge + sort + cap**, then apply the existing **combined signal gate** on the **final** order.

## Architecture

### New deep module (name TBD)

Single entry point, e.g. `RcaSegmentMergePolicy.mergeAndCap(...)`, responsible for:

1. **Inputs:** baseline row/map (for baseline problematic rate), list of hierarchical candidate segments (each already a `RootCauseSegment` with metrics), list of flat segments, configured **dimension order** (for flat tie-break), **`maxSegments`**.
2. **Hierarchical tier:** Keep segments whose `dimensions` size is **≥ 2**. Sort by **lift** = `(problematic_count / volume) - (baseline problematic_count / baseline volume)`, with safe handling for zero volume. Tie-break: **more dimensions** first (more specific intersection).
3. **Flat tier:** Sort by **problematic_count** descending. Tie-break: **earlier in dimension order** (lower index in configured order wins).
4. **Merge:** Concatenate **hierarchical tier**, then **flat tier**.
5. **Cap:** Truncate to **`maxSegments`**.
6. **Output:** Final ordered list. Optional: attach **tier** metadata (internal enum or tag on segment) for logging/tests — expose on API only if product asks (PRD allows internal-only).

**No ClickHouse** inside this module — only math and ordering on already-materialized segments.

### `RootCauseService` / `ScreenRcaService` orchestration

1. **Baseline** — unchanged.
2. **Dimension order** — unchanged (`hybridDimensionOrder` config stays as today; do not confuse with **HYBRID** analysis **mode**).
3. **Flat pass:** Reuse `buildFlatSegmentsFromIndex` logic but collect **without** stopping the overall algorithm at `maxSegments` if the PRD’s “collect then global top N” is chosen: implementation choice documented in issue #02 — either collect flat until `maxSegments` **or** collect one top bucket per dimension (up to all dimensions) then let merge+cap drop tail. PRD recommends: collect flat in dimension order with an internal cap aligned to `maxSegments`, then after merge **re-apply** `maxSegments` so hierarchy can displace flat tail.
4. **Hierarchical pass:** Reuse existing `pickFirstDimension` + `buildHierarchyThenFlat` query strategy and **threshold**. Change **materialization** so segments that represent **only** the first single-dimension step are **not** added to the hierarchical candidate list; include **progressive** segments once the cumulative **non–flat-extra** path has **≥ 2** dimensions. **Flat extras** from `collectFlatExtrasFromDimensionIndex` are **1D** — drop from hierarchical output; the **global flat pass** is canonical for 1D (per PRD).
5. **Merge+cap** — call deep module.
6. **`applySignalGate`** — run on the **merged** list (already after cap). Preserves “order of kept segments” semantics in `SegmentSignalGate`.
7. **Mode:** When the hierarchical branch was taken (first dimension picked), set `RootCauseAnalysisMode.HYBRID` (wire `"hybrid"`) because the result can contain **both** 2D+ and 1D segments. When algorithm falls back to flat-only (no first pick), keep **`FLAT`**. Pure hierarchical-only mode goes away for interaction/screen when hybrid is enabled — or keep `HIERARCHICAL` only if we ever return hierarchy **without** flat tier (edge case: should not happen if flat pass always runs). **Decision:** always run flat pass when `totalProblematic > 0`; mode = `HYBRID` if hierarchical candidates non-empty, else `FLAT`.

   - **Cache:** ClickHouse `root_cause_cache.mode` stores wire string; new value `hybrid`. Old rows remain `flat` / `hierarchical`; `fromWireValue` must accept `hybrid` and unknown values stay defensive.

### API / DTOs

- Extend `RootCauseAnalysisMode` with `HYBRID("hybrid")`.
- `RootCauseRestResponse` / Jackson serialization pick up enum automatically.
- **OpenAPI** (if generated from annotations): ensure new enum value appears after backend change.

### `RcaReportEnrichmentService`

- Assign **`serverRank`** in **final merged order** (1-based), not `problematic_count`-only sort.
- Session evidence fetch must **preserve** `serverRank` (already implemented; verify after reorder).

### `pulse_ai`

- `RootCausePayloadSchema.mode`: extend `Literal` with `"hybrid"`.
- Prompts: document that **rank 1** follows **`serverRank`**; **hybrid** means **2D+ cohorts first**, then **1D**; update any text that says `problematic_count` defines server rank.

### `pulse-ui`

- `ScreenRootCauseMode`: add `"hybrid"` where screen RCA displays mode (constants label map).
- Interaction RCA tab is narrative-first; if any TS type mirrors payload `mode`, extend similarly.

### Docs / harness

- Update `docs/rca-http-audit-failure-buckets.md` (**rank 1** / primary cohort semantics under hybrid).
- Re-run / tune `deploy/scripts/rca-audit.py` and `rca-db-audit.py` if expectations reference mode or ordering.

## Implementation sequence (see issues)

1. Merge module + unit tests (isolates ordering).
2. Wire orchestration in `RootCauseService` + `ScreenRcaService`, enum, service tests, controller smoke tests.
3. Enrichment `serverRank` alignment + tests.
4. AI schema + prompts + small tests.
5. UI mode union + docs + audit harness. **Done:** `issues/05-ui-docs-and-regression-harness.md`.

## Risks

- **Query volume:** Extra flat pass when hierarchy already ran — accepted per PRD.
- **Semantic change:** Cached `hierarchical` rows vs new `hybrid` — refresh or tolerate mixed cache until natural recompute; document for deploy.
- **Naming collision:** Existing `RootCauseConfig.isHybridDimensionOrderingEnabled()` vs analysis mode **HYBRID** — code/comments must disambiguate.
