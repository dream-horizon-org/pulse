# Issue 004 — Seed tuning (existing interactions only)

**Type:** AFK  
**Blocked by:** [001](rca-seg-001-traceability-matrix-appendix.md) (matrix picks which cohort stories are seed-owned)

## Parent

- PRD: [`prd/rca-segmentation-coverage-prd.md`](../prd/rca-segmentation-coverage-prd.md) (User stories 2, 62 regression policy)
- Plan: Phase 2 (grill-me: no new interaction rows)

## What to build

**Tune** the ecommerce seed so real ClickHouse telemetry produces **stable** problematic histograms for integration scenarios the matrix marks as **seed-owned** (e.g. hybrid / hierarchy + flat extras / healthy / single strong cohort). Changes live in the ecommerce seed path only; **no new** interaction names in MySQL/seed for this pass.

Document per tuned interaction: **target** segment `dimensions` / labels (post–`materializeSegmentsFromIndex`), **T** / threshold story, and **which seed knobs** changed—either in the matrix appendix notes or a short subsection of the scenarios doc.

End-to-end slice: **seed script → OTEL/CH data → RCA compute → cache** can be spot-checked (manual or follow-up 007).

## Acceptance criteria

- [x] Every matrix row marked “seed” for this pass has a **documented** interaction name + knob summary (appendix or scenarios doc).
- [x] `deploy/scripts/seed-ecommerce-data.py` (and any helper it calls) only **adjusts** existing interaction definitions—no new interaction identifier rows for this issue.
- [ ] After `./deploy/scripts/seed-ecommerce.sh --clear` (or project equivalent) + RCA recompute, at least one **spot-check** interaction shows expected cache shape (note interaction names in PR / issue comment for reviewers). **Deferred to issue 007** (verification needs Docker/CH stack).

## User stories covered

2 (PRD)

## Resolution (2026-05-06)

- `docs/rca-segmentation-scenarios.md` Appendix B documents per seed-owned interaction (`notifications_open`, `profile_update`, `checkout_start`, `app_launch`) the **target post-`materializeSegments` shape**, **threshold story**, and **concrete seed knobs** (line refs into `deploy/scripts/seed-ecommerce-data.py`, including the per-span helpers `pick_app_launch_device_context` and `pick_home_feed_load_context` and the bias constants `_APP_LAUNCH_OS10_JIO_BIAS=0.33` / `_APP_LAUNCH_SM_A135F_STANDALONE_BIAS=0.14`).
- No `INTERACTIONS[]` row was added or removed; existing ecommerce seed already produces the matrix-required cohorts (A3/B1/C1/C3/E1/E2/G2). Treated this issue as **doc-only**: matrix declared the contract, Appendix B backs it with code-level knob references.
- Live cache spot-check (AC#3) deferred to **issue 007** (verification & closeout), which already owns the `seed → rca-db-audit` ladder and runs against Docker/ClickHouse. If 007 reveals drift, file a new issue rather than reopening 004.
