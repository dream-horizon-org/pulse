---
name: to-issues-ralph
description: Split a Ralph-ready PRD.md into numbered local issue files at $RALPH_WORK_DIR/issues/NN-slug.md, wrapping /to-issues vertical-slice rules, filtering HITL slices out, and adding a per-issue Eval bash block. Use when preparing a feature for ralph/loop.sh, or when user says "issues for ralph", "ralph-ready issues", or invokes /ship-feature.
---

# To Issues (Ralph)

Adapter over `/to-issues`. Same vertical-slice discipline, local file output, deterministic gate per issue.

## Output contract

Files at `${RALPH_WORK_DIR:-<repo-root>}/issues/NN-slug.md` where:
- `NN` = two-digit dependency order (`01`, `02`, …). Blockers come first.
- `slug` = kebab-case, ≤5 words, drawn from title.
- ONLY AFK slices emitted. HITL slices listed back to user separately.

Each file MUST contain (headings exact):

1. `# <Issue title>` — imperative mood
2. `## Package` — optional for single-package PRDs; required for cross-package (e.g. `pulse-web-otel`, `backend/server`, `pulse-ui`)
3. `## Context` — 2–4 lines, link to PRD section
4. `## Acceptance Criteria` — checkboxes, drawn from PRD § Acceptance Criteria
5. `## Implementation hints` — optional, 1–3 lines, no file paths
6. `## Eval` — fenced bash block, deterministic gate (this is the Ralph stop condition)
7. `## Out of Scope` — what this slice does NOT cover
8. `## Blocked by` — references to other issue filenames, or `None`

`ralph/loop.sh` agent rejects issues missing `## Eval`.

## Process

1. Read the active PRD: `${PRD_PATH:-${RALPH_WORK_DIR:-<repo-root>}/PRD.md}`. For **pulse-web-otel**, if `PRD.md` is a symlink, reading it is enough; otherwise use `prd/<slug>.md` per [`pulse-web-otel/prd/README.md`](../../../pulse-web-otel/prd/README.md). Reject and ask user to run `/to-prd-ralph` if missing or missing § Acceptance Criteria.
2. Follow steps 1–4 of `/to-issues` (explore, draft slices, quiz user on granularity + dependencies + HITL/AFK).
3. Filter to AFK slices only. Print HITL slices back to user as "handle these manually:".
4. Topo-sort AFK slices by dependency. Number `01..NN` — for cross-package, enforce phase order (SDK emit before backend ingest before UI render).
5. For each, write `issues/NN-slug.md` per template above. Include `## Package` if cross-package.
6. For each `## Eval` block:
   - Pull command shape from `ralph/eval-snippets.md` for target package.
   - Scope to the slice's actual files/module/test path.
   - If the issue has `## Package`, the eval must `cd` into that package directory at the start.
   - Confirm runtime <60s where possible.
7. Print summary: file count, dependency chain, total estimated runtime.

## Eval rules

- Test the slice's behavior end-to-end, not just lint. Acceptance criteria → test cases.
- Examples per package: see `ralph/eval-snippets.md`.
- If a slice cannot be deterministically gated (e.g., visual regression, manual verification), it's HITL — do not emit it.

## When NOT to use

- Slice contains HITL decision → keep it in conversation, list to user.
- No PRD present (see `PRD_PATH` / `PRD.md` / `pulse-web-otel/prd/`) → run `/to-prd-ralph` first.
