# Issue 005 — Strict `rca-db-audit` (dimensions + sort + mode)

**Type:** AFK  
**Blocked by:** [001](rca-seg-001-traceability-matrix-appendix.md), [004](rca-seg-004-seed-tuning-existing-interactions.md) (golden expectations follow stable seed output)

## Parent

- PRD: [`prd/rca-segmentation-coverage-prd.md`](../prd/rca-segmentation-coverage-prd.md) (User stories 3, 4, 5, 13, Testing Decisions)
- Plan: Phase 3 (grill-me: exact `dimensions`, Python sort, selective `mode`; **not** `rca-audit`)

## What to build

Upgrade **`rca-db-audit`** so pre-LLM **`root_cause_cache`** segments are checked against **expected `dimensions` maps** with **exact equality** after a **documented stable sort** of both expected and actual segment lists (same sort key). Optionally retain keywords for failure readability. Assert **`mode` (`HIERARCHICAL` / `FLAT`)** only for a **small allowlist** of interactions once 004 stabilizes cohorts. **Do not** add strict `dimensions` checks to **`rca-audit`** (LLM path).

End-to-end slice: **Python audit → reads CH cache JSON → fails fast** when segmentation or seed drift breaks dimensions or allowlisted mode.

## Acceptance criteria

- [x] Sort helper (or inline logic) documented: exact **string or tuple key** used for ordering segment lists. → `segment_sort_key` in `deploy/scripts/rca-db-audit.py` returns `tuple(sorted(dimensions.items()))` with rationale comment block.
- [x] `EXPECTATIONS` (or successor structure) can express **list of `dimensions` dicts** per interaction; comparison uses post-sort index alignment. → new optional `expected_dimensions` field; strict equality at each post-sort index.
- [x] **Grill-me:** if sorted **actual segment count ≠ expected list length**, audit **fails** (no silent extra segments). → `[COUNT]` FAIL emitted before per-index compare.
- [ ] **Grill-me:** first golden expected lists built by **recording** from a clean `root_cause_cache` run after issue 004, then human review/trim. → **deferred to issue 007** (needs running stack); only `notifications_open` populated (`expected_dimensions: []`, the trivially-correct case).
- [x] `mode` asserted only for the agreed **small set** (listed in issue/PR description or in script comments). → allowlist in script comment + per-row `"mode"` field on `checkout_start` (HIERARCHICAL) and `notifications_open` (FLAT), aligned to scenarios doc row G2.
- [x] `rca-audit.py` unchanged regarding strict `dimensions` parity (confirm no new strict map asserts there). → confirmed; only existing `dims` reads at lines 387/584 (noise/empty checks), no strict map equality added.

## Resolution (2026-05-06)

`deploy/scripts/rca-db-audit.py`:
- New `segment_sort_key(seg)` helper with rationale block (order-independent, deterministic, faithful to comparison target).
- `EXPECTATIONS` schema extended with optional `expected_dimensions` (list of dicts) and `mode` (string) fields. Schema documented in a single header comment.
- `check_segments` now takes `mode` arg; when `expected_mode` is set it asserts equality; when `expected_dimensions` is set it sorts both lists by `segment_sort_key`, fails hard on count mismatch (`[COUNT]`), and asserts exact map equality at each index (`[DIMS#i]`).
- `notifications_open` populated with `expected_dimensions: []` + `mode: FLAT`. `checkout_start` annotated with `mode: HIERARCHICAL` (its `expected_dimensions` golden list deferred to 007).
- All other interactions unchanged in behavior — keyword-based presence + noise-floor checks remain the readability fallback until 007 records their golden dimension lists.

Closing the gate criterion ("count mismatch fails") for the entries that have golden lists today; populating remaining `expected_dimensions` is the body of issue 007.

## User stories covered

3, 4, 5, 13 (PRD)
