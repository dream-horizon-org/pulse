# Issue 006 — RootCauseServiceTest §9 gaps

**Type:** AFK  
**Blocked by:** [001](rca-seg-001-traceability-matrix-appendix.md) (matrix marks “unit only” rows)

## Parent

- PRD: [`prd/rca-segmentation-coverage-prd.md`](../prd/rca-segmentation-coverage-prd.md) (User stories 8, 14)
- Plan: Phase 4

## What to build

Close **segmentation checklist** gaps that are **poor fit for seeds** by extending **`RootCauseServiceTest`** with Mockito-controlled ClickHouse row sequences: e.g. empty `dimensionOrder`, `pickClosestToTotal` tie behavior, empty segment metrics query, flat-extra `dimensions` semantics, hybrid order helper edge cases already testable in isolation.

End-to-end slice: **`mvn -Dtest=RootCauseServiceTest` passes** with new cases; no Docker required for this issue.

## Acceptance criteria

- [x] **Grill-me:** **every** §9 matrix row cites a **`should*…` method** in issue 001; implement missing tests (or open follow-up issues with IDs if scope explodes) and keep the matrix updated in the same PR.
- [x] New tests use **`should*`** naming and existing Mockito/RxJava patterns in the root-cause service test class.
- [x] `mvn -Dtest=RootCauseServiceTest test` passes locally / CI.

## Resolution (2026-05-06)

Three `ComputePaths` tests added — `shouldReturnEmptySegmentsWhenDimensionOrderIsEmpty` (B3), `shouldPickFirstRowWhenTieOnAbsoluteDistanceToTotal` (D3), `shouldTreatNullDimensionValueAsEmptyString` (D4). New `multiRowTableResponse` helper supports the multi-row tie fixture. Gap markers removed from `docs/rca-segmentation-scenarios.md` Appendix A; gap summary section flipped to "closed". Full project run: 4475 tests, 0 failures (RootCauseServiceTest: 41).

Note on filtered runs: `mvn -Dtest=RootCauseServiceTest test` reports `Tests run: 0` because of the unusual `<include>**/*.java</include>` surefire config (overrides default class-name patterns). `mvn test` runs the class. Filed as a follow-up consideration; not in scope for this issue.

## User stories covered

8 (PRD)

## Further notes

If Java sources under `backend/server` change: run `graphify update .` per repo rules after merge prep.
