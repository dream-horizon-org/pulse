# Issue 001 — §9 traceability matrix appendix

**Type:** AFK  
**Blocked by:** None — can start immediately

## Parent

- PRD: [`prd/rca-segmentation-coverage-prd.md`](../prd/rca-segmentation-coverage-prd.md) (User stories 1, 11)
- Plan: RCA segmentation follow-through — Phase 1

## What to build

Append a **coverage / traceability matrix** to the end of the RCA segmentation **scenarios** document. Each row maps one **§9 checklist item** from the segmentation algorithm doc to **who owns proof**: unit test (`RootCauseServiceTest`), ClickHouse db-audit (`rca-db-audit` interaction name or N/A), and **seed profile** (existing ecommerce interaction block or “unit only”). Include optional **ScenarioID** slug and **Notes** (e.g. borderline, LLM-only).

This is documentation-only but **unblocks** seed tuning, strict db-audit expectations, and unit-test backlog by making gaps visible.

## Acceptance criteria

- [x] Appendix exists at the end of `docs/rca-segmentation-scenarios.md` with columns: ScenarioID (optional), §9 reference, RootCauseService behavior note, RootCauseServiceTest, rca-db-audit, Seed / unit-only, Notes.
- [x] Every **§9 checklist row** from `docs/rca-segmentation.md` has at least one matrix row (gates, mode/order, threshold, flat, hierarchy, invariants).
- [x] Matrix explicitly marks **rca-audit** as non-owner for strict `dimensions` (per PRD / grill-me).
- [x] **Grill-me:** **`RootCauseServiceTest` column** — every row lists a **concrete `should*…` test method** (or nested class + method) — no blank cells; **004 is blocked until this is done.**

## Grill-me notes

Full matrix completeness is a **hard gate** before seed work (issue 004).

## User stories covered

1, 11 (PRD)
