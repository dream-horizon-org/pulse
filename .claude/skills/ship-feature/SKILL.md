---
name: ship-feature
description: End-to-end harness for shipping a feature in Pulse autonomously — discover, specify, slice, loop, review. Composes /prototype, /grill-with-docs, /to-prd-ralph, /to-issues-ralph, and ralph/loop.sh into a single gated pipeline. Use when user says "ship a feature", "build feature X end-to-end", "run the pipeline", or wants the full agentic flow against a target package.
---

# Ship Feature (Pulse harness)

Spec-driven, Ralph-executed pipeline for shipping ONE feature in ONE or MORE Pulse packages.

Inspired by Spec-Kit (gated workflow), BMAD (sharded story files), OpenSpec (delta scope). Constitution = `CLAUDE.md` + `.claude/rules/*.md`.

## Quick start

Single-package:
```
/ship-feature pulse-web-otel "Emit screen_load + screen_interactive on SPA route changes"
```

Cross-package:
```
/ship-feature "End-to-end web crashes: SDK emits → backend ingests → UI renders"
```

(No package arg for cross-package; will set RALPH_WORK_DIR=$PWD automatically.)

## Pipeline (5 gated stages)

| # | Stage    | Skill / tool          | Output                              | Gate        |
|---|----------|-----------------------|-------------------------------------|-------------|
| 1 | Discover | /prototype, /grill-with-docs (optional) | shared mental model | user OK     |
| 2 | Specify  | /to-prd-ralph         | `$WD/PRD.md` or `$WD/prd/<slug>.md` (Web SDK) | user review |
| 3 | Slice    | /to-issues-ralph      | `$WD/issues/NN-*.md`                | user review |
| 4 | Loop     | `./ralph/loop.sh`     | commits, `progress.txt`, `.logs/`   | exit 0      |
| 5 | Review   | /review               | review notes on branch              | user opens PR |

`$WD` = `RALPH_WORK_DIR`, defaults to `<repo>/<package>`.

## Process

1. **Confirm target package(s)**:
   - Single-package: from `pulse-web-otel | pulse-ui | backend/server | backend/pulse-alerts-cron | pulse-react-native-otel | pulse-android-otel | pulse_ai`. Ask if ambiguous.
   - Cross-package: confirm feature touches ≥2 packages and identify them (e.g. web SDK + backend + UI).
2. **Set work dir**:
   - Single-package: `RALPH_WORK_DIR=<repo>/<package>`
   - Cross-package: `RALPH_WORK_DIR=$PWD` (monorepo root)
3. **Stage 1 — Discover**: ask user "prototype first? grill-with-docs first?". If yes, invoke that skill. If no, skip.
4. **Stage 2 — Specify**: invoke `/to-prd-ralph`. Show PRD. Iterate until user approves. Pre-flight check: active PRD has `## Global Eval`. For **pulse-web-otel**, confirm `prd/<slug>.md` exists and `PRD.md` symlinks to it (or use `PRD_PATH`).
5. **Stage 3 — Slice**: invoke `/to-issues-ralph`. Show issue list with dependency chain. Iterate until user approves. Pre-flight: every issue has `## Eval`.
6. **Stage 4 — Loop**: print exact commands for user to run:
   
   Single-package:
   ```
   RALPH_WORK_DIR=$PWD/<package> ./ralph/loop.sh --dry-run
   RALPH_WORK_DIR=$PWD/<package> ./ralph/loop.sh
   ```
   
   Cross-package (monorepo root):
   ```
   RALPH_WORK_DIR=$PWD ./ralph/loop.sh --dry-run
   RALPH_WORK_DIR=$PWD ./ralph/loop.sh
   ```
   
   If issue count >10, recommend worktree + `--bypass`:
   ```
   git worktree add ../pulse-ralph-<feat> main
   cd ../pulse-ralph-<feat>
   RALPH_WORK_DIR=$PWD/<package> /full/path/ralph/loop.sh --bypass    # single-package
   # or
   RALPH_WORK_DIR=$PWD /full/path/ralph/loop.sh --bypass              # cross-package
   ```
7. **Stage 5 — Review**: when loop exits 0, run `/review` on the resulting branch, then user opens PR via `gh pr create`.

## Stop conditions

- Stage 2 pre-flight fails (no Global Eval) → re-run `/to-prd-ralph`.
- Stage 3 pre-flight fails (issue without Eval) → re-run `/to-issues-ralph`.
- Stage 4 exits 1 (`EVAL_FAILED`) → human reads `ralph/.logs/iter-N-*.log`, decides: fix prompt, fix issue, or hand off.
- Stage 4 exits 2 (Global Eval regression) → revert last commit, retry.
- Stage 4 exits 3 (max iters) → triage remaining `issues/*.md`, decide whether to extend or stop.

## Targeting rules (Pulse-specific)

| Package                        | Fit       | Notes |
|--------------------------------|-----------|-------|
| `pulse-web-otel`               | best      | greenfield, has WEB-SDK-AGENT-CONTEXT.md |
| `pulse-ui`                     | good      | screen scaffolding; avoid for global state |
| `backend/server`               | weak      | only new domain folders; legacy chokes Ralph |
| `backend/pulse-alerts-cron`    | medium    | small surface, well-templated |
| `pulse-react-native-otel`      | poor      | needs human review per iter |
| `pulse-android-otel`           | poor      | same |
| `pulse_ai`                     | medium    | small scope only |

## Cross-package mode

For features spanning ≥2 packages, the same 5-stage pipeline but with:
1. No package argument; `/ship-feature "description"` triggers cross-package detection.
2. RALPH_WORK_DIR=$PWD (monorepo root) — PRD and issues at the root.
3. Each issue declares `## Package` so Claude loads the right CLAUDE.md.
4. Issues numbered in phase order (SDK → backend → UI) enforced by file order rule.
5. Global Eval is optional — a no-op is valid; per-issue evals are the real gates.

## When NOT to use

- Single-file fix → just edit.
- HITL-heavy or security-sensitive paths → use plain `/to-prd` + manual implementation.
- Production hotfix → too slow, page someone instead.
