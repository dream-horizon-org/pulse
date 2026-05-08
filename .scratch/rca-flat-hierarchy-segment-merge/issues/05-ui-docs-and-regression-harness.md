**Triage:** complete

## Parent

[PRD](../PRD.md) · [Technical plan](../TECHNICAL-PLAN.md)

## What to build

Update **UI types/labels** that mirror **`RootCauseRestResponse.mode`**, refresh **RCA audit / failure-bucket docs** for **rank 1** and **`hybrid`**, and run or adjust **`rca-audit.py` / `rca-db-audit.py` / RCA e2e** expectations so CI or manual harness matches the new semantics.

## Acceptance criteria

- [x] `pulse-ui` screen RCA types/constants include **`hybrid`** where `flat | hierarchical` is declared (e.g. `ScreenRootCauseMode`, label map).
- [x] `docs/rca-http-audit-failure-buckets.md` explains **Bucket 2 / primary cohort** under **hybrid** ordering.
- [x] Deploy scripts or docs note **cache mode** value **`hybrid`** for new computations; **rca-db-audit** dimensions/mode checks reviewed for regressions (`checkout_start` → **HYBRID** in `EXPECTATIONS`).
- [x] Optional: extend or re-seed **RCA e2e** scenarios if ordering assertions exist — document what was run (not re-run this slice; harness unchanged aside from db-audit mode pin).

## Blocked by

- ~~`issues/02-backend-rca-unified-pipeline.md`~~ (**complete**)
- `issues/03-enrichment-server-rank-merged-order.md` (complete)
- ~~`issues/04-pulse-ai-rca-contract-and-prompts.md`~~ (**complete**)

## User stories covered

PRD **11–12, 18, 23, 26–27, 31, 38** (docs + UI types + audit alignment).
