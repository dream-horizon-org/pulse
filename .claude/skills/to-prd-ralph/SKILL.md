---
name: to-prd-ralph
description: Generate a Ralph-loop-ready PRD at $RALPH_WORK_DIR (default repo root), wrapping the /to-prd content template and adding Global Eval bash block plus acceptance criteria mapping. Use when preparing a feature for autonomous execution via ralph/loop.sh, or when user says "PRD for ralph", "ralph-ready PRD", or invokes /ship-feature. For pulse-web-otel, write under prd/<slug>.md (see pulse-web-otel/prd/README.md).
---

# To PRD (Ralph)

Adapter over `/to-prd`. Same content discipline, different destination + extra structure.

## Output contract

A single markdown file — NOT the issue tracker.

- **Default / most packages:** `${RALPH_WORK_DIR:-<repo-root>}/PRD.md`
- **`pulse-web-otel`:** `${RALPH_WORK_DIR}/prd/<kebab-feature-slug>.md` — then `ln -sf prd/<slug>.md PRD.md` in that package so `ralph/loop.sh` resolves the PRD (or document `PRD_PATH` for one-off runs). Index new PRDs in [`pulse-web-otel/prd/README.md`](../../../pulse-web-otel/prd/README.md).

The file MUST contain (in this order, headings exact):

1. `# <Feature title>`
2. `## Problem Statement`        ← per /to-prd
3. `## Solution`                 ← per /to-prd
4. `## User Stories`             ← per /to-prd, numbered
5. `## Implementation Decisions` ← per /to-prd
6. `## Testing Decisions`        ← per /to-prd
7. `## Acceptance Criteria`      ← NEW: flat checklist, every item observable
8. `## Out of Scope`             ← per /to-prd
9. `## Global Eval`              ← NEW: fenced bash block, regression gate
10. `## Further Notes`           ← optional

`ralph/loop.sh` pre-flight rejects PRDs missing `## Global Eval`.

## Process

1. Follow steps 1–2 of `/to-prd` (explore repo, sketch modules, confirm modules with user).
2. Determine target package (or confirm cross-package): 
   - Single package: `pulse-web-otel | pulse-ui | backend/server | backend/pulse-alerts-cron | pulse-react-native-otel | pulse-android-otel | pulse_ai`.
   - Cross-package (≥2 packages): PRD goes at `RALPH_WORK_DIR=$PWD` (monorepo root), issues numbered in phase order (SDK → backend → UI), each issue declares `## Package`.
3. Write the PRD using the template above to the path from **Output contract** (package root `PRD.md`, or `pulse-web-otel/prd/<slug>.md` + symlink `PRD.md` when targeting the Web SDK).
4. Insert `## Global Eval` from `ralph/eval-snippets.md`:
   - Single-package: pull recipe matching the package.
   - Cross-package: use either a no-op (per-issue evals are the real gates) or a multi-package recipe (all packages must pass together). Keep it under 2 min runtime.
5. Show full PRD to user, ask: "looks right? any criterion ambiguous?". Iterate until approved.

## Acceptance Criteria rules

- Every criterion observable: testable, demoable, or measurable. No "feels right".
- Each user story → ≥1 criterion. Coverage explicit.
- These criteria are the source `/to-issues-ralph` draws from when writing per-issue Eval blocks.

## Global Eval rules

- Pull command shape from `ralph/eval-snippets.md` for the package.
- Scope to changed package — never run cross-monorepo verify in the loop.
- Slow but deterministic > fast and flaky. Compile errors and type checks are good.

## When NOT to use

- HITL-heavy work (architecture review, security paths) → use plain `/to-prd`.
- Single-file fix → skip PRD entirely, just edit.
