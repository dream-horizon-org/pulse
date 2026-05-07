Status: done

## Parent

- `.scratch/rca-segment-signal-gate/PRD.md`
- Canonical PRD: `prd/rca-segment-signal-gate-prd.md`

## What to build

Update triage and scenario documentation so **Bucket C** and RCA operators describe the **combined signal gate** (**S ≥ threshold**, default **15**) as **product policy** implemented in pulse-server, not audit-only. Touch at least: `docs/rca-audit-failure-buckets.md` (Bucket C narrative), and the RCA segmentation scenarios doc where audit expectations are explained. Add a short note to the **RCA E2E** skill or adjacent docs that after server changes, verification expects **re-seed + root_cause_cache (and screen cache) recompute** before audits. Keep **Jio / strict keyword** docs clearly **orthogonal** to **S**.

## Acceptance criteria

- [x] Failure-bucket doc explains **S** and that db-audit matches server. (`docs/rca-audit-failure-buckets.md` Bucket C rewritten)
- [x] Scenarios or appendix mentions configurable threshold and null-as-zero behavior at a high level. (`docs/rca-segmentation-scenarios.md` "Signal gate" section)
- [x] RCA E2E / local verification doc mentions cache recompute after this feature ships. (`.cursor/skills/rca-e2e/SKILL.md` "Cache recompute after server changes")
- [x] No scope creep into ranking algorithm docs (out of scope per PRD). (touched only Bucket C narrative + a new section; ranking docs untouched)

## Blocked by

- `02-rootcause-config-and-interaction-cache-gate.md`
- `04-rca-db-audit-combined-s-rule.md`

## User stories covered

12, 13, 20

## Comments
