**Triage:** done

## Parent

[PRD](../PRD.md) · [Technical plan](../TECHNICAL-PLAN.md)

## What to build

Align **`serverRank`** assignment in **RCA report enrichment** with the **post-merge** segment order from the backend (hybrid ordering: **2D+ first**, then **1D**, after cap and consistent with what **`GET /root-cause`** returns). Ensure **session evidence** enrichment still **preserves** ranks.

## Acceptance criteria

- [x] `RcaReportEnrichmentService` assigns **1-based `serverRank`** in **final merged list order**, not legacy `problematic_count`-only sort.
- [x] `RcaReportEnrichmentServiceTest` covers merged-order ranks and preservation across concurrent session fetch paths.
- [x] No reintroduction of **AI-only volume filtering** that drops segments present on the API.

## Blocked by

- `issues/02-backend-rca-unified-pipeline.md`

## User stories covered

PRD **8, 18, 19**.
