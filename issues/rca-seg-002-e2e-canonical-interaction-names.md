# Issue 002 — E2E doc canonical interaction names

**Type:** AFK  
**Blocked by:** None — can start immediately

## Parent

- PRD: [`prd/rca-segmentation-coverage-prd.md`](../prd/rca-segmentation-coverage-prd.md) (User story 6)
- Plan: Phase 5 (doc mapping locked)

## What to build

Eliminate **doc vs seed vs audit** drift for human readers by adding a **canonical name mapping** table and aligning `docs/rca-e2e-test-cases.md` so commands and examples use the same interaction strings as seed and `rca-db-audit` (e.g. `cart_checkout` → `checkout_start`, `user_profile_load` → `profile_update`). **No** full rename of MySQL rows or seed identifiers in this issue.

End-to-end: a reader opens the E2E doc, copies an interaction name into `rca-db-audit` / seed scripts, and it **matches**.

## Acceptance criteria

- [x] `docs/rca-e2e-test-cases.md` includes a visible **“Canonical interaction names (seed / audit)”** table with at least `checkout_start` / `profile_update` mappings.
- [x] Section titles or opening notes for previously mismatched names point to the canonical row (either retitled or clearly noted). §1.6 now titled `checkout_start` (legacy `cart_checkout` noted); §1.12 now titled `profile_update` (legacy `user_profile_load` noted); appendix Seed Volume Summary uses canonical names with legacy in parentheses.
- [x] Prerequisites or runbook text that cited old names are updated so copy-paste works. All `Run RCA on …` rows in §1.6 / §1.12 use canonical names; doc now also links scenarios + PRD at the top for landing context.

## User stories covered

6 (PRD)
