Status: in-progress (interaction wiring landed; awaiting graphify update)

## Parent

- `.scratch/rca-segment-signal-gate/PRD.md`
- Canonical PRD: `prd/rca-segment-signal-gate-prd.md`

## What to build

Add **minimum combined delta** (default **15**) to **root-cause configuration** with the same override style as other RCA tunables (e.g. env-backed config file). Wire **interaction** root-cause flow so that after segments are fully built and **deltas** are populated, the list is **filtered in memory** with the shared helper **before** writing **`root_cause_cache`**. Preserve **relative order** of kept segments only; allow **fewer than max** segments and an **empty** list if all drop. Add **debug**-level logging when segments are dropped (reason: below combined signal threshold). Optionally add or extend a **service-level** test if wiring is non-trivial and existing RCA test patterns allow it.

## Acceptance criteria

- [x] Config key exists with default **15**; staging/prod can override without code change.
  - `RootCauseConfig.minCombinedDeltaSignal` (default `15.0`, sentinel `-1.0` = unset, `0.0` = disabled at runtime)
  - `rootcause-default.conf` key `minCombinedDeltaSignal` + env `ROOT_CAUSE_MIN_COMBINED_DELTA_SIGNAL`
  - `MainVerticle.buildRootCauseConfig` reads the key when present (matches `minRiskRatioForIssueAttribution` precedent)
- [x] Interaction RCA cache path applies the gate at the PRD **application point** (post-delta, pre-persist).
  - `RootCauseService.computeAndCache` calls `applySignalGate` on `result.getSegments()` *before* the JSON serialization that feeds `cacheDao.upsert`.
- [x] No extra ClickHouse round-trips for filtering.
  - Filter operates on the in-memory `List<RootCauseSegment>` returned from `runAlgorithm`; no new query specs.
- [x] Debug logging explains dropped segments without spamming info/warn.
  - Per-drop debug line (`label`, `S`, `threshold`); single info line summarizing `kept/total` only when something was dropped.
- [ ] After Java changes, run **graphify update** per repo rules (note in PR if automated check not in CI).

## Decisions captured

- **Sentinel for "unset" raw config:** `< 0`, mirroring `minRiskRatioForIssueAttribution`. `0.0` is preserved as a runtime "gate disabled" signal so operators get instant rollback (PRD User Story 15) without a feature flag.
- **No reference-equality refactor inside `applySignalGate`:** returns the original list when nothing was dropped or the gate is disabled, so the surrounding `result` object isn't rebuilt unnecessarily.
- **Service-level test skipped (AC says optional):** wiring is trivial and `SegmentSignalGateTest` (issue 01) already exercises the formula and filter behavior. `RootCauseConfigTest` covers default / unset / explicit-zero / custom-positive paths.

## Blocked by

- `01-segment-signal-gate-helper-and-tests.md`

## User stories covered

1, 2, 3, 4, 8, 10, 14, 15, 16, 18

## Comments
