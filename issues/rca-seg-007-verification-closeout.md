# Issue 007 — Verification & closeout

**Type:** AFK  
**Blocked by:** [004](rca-seg-004-seed-tuning-existing-interactions.md), [005](rca-seg-005-rca-db-audit-dimensions-and-mode.md), [006](rca-seg-006-root-cause-service-test-gaps.md)

## Parent

- PRD: [`prd/rca-segmentation-coverage-prd.md`](../prd/rca-segmentation-coverage-prd.md) (User story 9)
- Plan: Phase 7

## What to build

Run the **agreed verification ladder** after implementation slices land: re-seed if seeds changed, **`RootCauseServiceTest`**, full **`rca-db-audit`**, optional **`rca-e2e.py --db-only`**. Capture the **exact command sequence** in `docs/rca-e2e-test-cases.md` or `prd/rca-segmentation-coverage-prd.md` Further Notes if not already present.

This issue is the **integration proof** slice: no new product code unless a run reveals a gap—then open a **new** issue rather than expanding scope here.

## Acceptance criteria

- [ ] With fresh seed + RCA cache populated, `python3 deploy/scripts/rca-db-audit.py` exits success (or documented known skips are zero).
- [ ] `cd backend/server && mvn -Dtest=RootCauseServiceTest test` passes.
- [ ] Optional: `python3 deploy/scripts/rca-e2e.py --db-only` documented result (pass or skip reason).
- [x] PRD or E2E doc updated with **copy-paste verification** block if anything was missing.

## User stories covered

9 (PRD)

## Resolution (partial — 2026-05-07)

- **AC#4 (doc):** `docs/rca-e2e-test-cases.md` gained a new top-level "Verification ladder (copy-paste)" section right after Prerequisites. It lists the agreed ladder in order — optional re-seed → `RootCauseServiceTest` → optional `rca-generate` → strict `rca-db-audit` → optional `rca-e2e.py --db-only` — with triage hints into the scenarios doc §9 matrix when a rung fails. PRD revision history updated.
- **AC#1, AC#2, AC#3 (infra):** still open. Per the grill-me decision recorded in 005's progress note, these need a clean Docker/ClickHouse stack run to (a) populate the RCA cache, (b) record golden `expected_dimensions` lists for the 15 interactions still without them, (c) wire those lists into `EXPECTATIONS`, then (d) run the full ladder end-to-end. Best handled in a hands-on session — not in this AFK pass.
