# Issue 003 — RCA discoverability (skill + doc links)

**Type:** AFK  
**Blocked by:** None — can start immediately

## Parent

- PRD: [`prd/rca-segmentation-coverage-prd.md`](../prd/rca-segmentation-coverage-prd.md) (User stories 7, 12)
- Plan: Phase 6

## What to build

Make the **scenarios catalog** and **PRD** discoverable from the **RCA e2e skill** and the **E2E test cases** doc so that when `rca-db-audit` fails, contributors land on label / `dimensions` / `mode` rules and the coverage PRD without searching the repo.

Vertical slice: **documentation + agent skill only** — no backend change required.

## Acceptance criteria

- [x] `.cursor/skills/rca-e2e/SKILL.md` links to `docs/rca-segmentation-scenarios.md` (and optionally `prd/rca-segmentation-coverage-prd.md`) in the pipeline / validation section.
- [x] `docs/rca-e2e-test-cases.md` links to `docs/rca-segmentation-scenarios.md` near the top (Prerequisites or Related).
- [x] **Grill-me:** update **every** repo copy of the rca-e2e skill — today only `.cursor/skills/rca-e2e/SKILL.md` exists under Pulse; if `.claude/skills/rca-e2e/SKILL.md` is added later, keep parity.

## Grill-me notes

002 and 003 stay **separate issues** (per session).

## User stories covered

7, 12 (PRD)
