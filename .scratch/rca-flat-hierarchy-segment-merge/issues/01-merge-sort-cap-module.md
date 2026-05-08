**Triage:** done

## Parent

[PRD](../PRD.md) · [Technical plan](../TECHNICAL-PLAN.md)

## What to build

Introduce a **deep module** (no ClickHouse) that implements **tier classification**, **within-tier sorting**, **merge (hierarchical 2D+ then flat 1D)**, and **`maxSegments` cap**. Input is **already materialized** `RootCauseSegment` lists plus baseline metrics for rate/lift and configured **dimension order** for flat tie-breaks.

## Acceptance criteria

- [x] Module API is narrow (merge + cap + optional tier tagging) and **unit-tested without** `RootCauseService`.
- [x] **Hierarchical** inputs: only segments with **`dimensions` size ≥ 2** participate in the hierarchical tier; sorting primary key = **problematic rate − baseline problematic rate**; tie-break = **larger dimension count**.
- [x] **Flat** tier: sort by **problematic_count** desc; tie-break = **dimension order index** (first configured dimension wins).
- [x] **Merge** = hierarchical tier list **then** flat tier list; **truncate** to **`maxSegments`**.
- [x] Tests cover: empty hierarchy, empty flat, ties, cap cuts **only flat tail**, cap cuts **hierarchy** when huge, zero-volume safety for rates.

## Blocked by

None — can start immediately.

## User stories covered

PRD **16, 21, 9, 22** (and supports **5, 6, 7, 15**).
