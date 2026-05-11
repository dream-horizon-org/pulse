# Web SDK Documentation Consolidation

## Problem Statement

`pulse-web-otel` has accumulated 87+ planning/design documents spread across `web-sdk-plan/` subfolders, `docs/`, `examples/`, and `graphify-out/`. Each instrumentation's knowledge is fragmented across ADRs, multiple PLAN-*.md variants, research files, handoff notes, and a DESIGN.md. There is no single authoritative reference for any instrumentation. This makes onboarding, debugging, and further development slow — every contributor must reconstruct the full picture from 8-12 scattered files per feature.

## Solution

Create one canonical `SPEC.md` per instrumentation/feature under `docs/instrumentations/<name>/SPEC.md`. Each SPEC.md synthesises all scattered docs + current implementation into a single holy-grail reference. After synthesis, old docs are triple-evaluated and removed. The result is a clean, authoritative `docs/instrumentations/` tree that replaces the entire `web-sdk-plan/` folder.

## User Stories

1. As a developer picking up an instrumentation for the first time, I can read one file and understand the goal, design decisions, signal contract, framework quirks, test coverage, and open bugs — without hunting across 10 files.
2. As an agent (Ralph or Claude) working on a new feature, I can read `docs/instrumentations/<name>/SPEC.md` and immediately know what exists, what is tested, and what is missing.
3. As a reviewer, I can verify a PR against a single authoritative spec that reflects the current implementation — not outdated planning artefacts.
4. As a team member maintaining the SDK, I can update one file when an instrumentation changes instead of patching multiple scattered files.
5. As a developer integrating the SDK with Next.js or React, I can find exact framework-specific behaviour (App Router vs Pages Router, SSR edge cases) in the instrumentation's SPEC.md section 5.
6. As a developer onboarding a host app, I read **one** [`docs/instrumentations/integration/SPEC.md`](../instrumentations/integration/SPEC.md) for install paths, `Pulse.init`, consent, and framework entrypoints — aligned with current exports — with pointers to sdk-core and framework SPECs for depth.
7. As an agent or Cursor skill (`.cursor/rules/`, `.cursor/skills/`, `.cursor/agents/`), I find all instrumentation references point to `docs/instrumentations/` — no stale `web-sdk-plan/` paths.

## Implementation Decisions

- **Output location:** `pulse-web-otel/docs/instrumentations/<name>/SPEC.md` — flat, predictable, no version prefix.
- **File name:** `SPEC.md` (not `DESIGN.md`) to signal it is both spec and implementation truth, not just a pre-build design.
- **9 mandatory sections** per SPEC.md: Goal, Assumptions/Research/Parity, Requirements (PRD), Architectural Design, LLD (signals + attributes + framework behaviour), Test Coverage, Known Bugs & Gaps, Redundancy & Cleanup Notes, Open Questions.
- **Triple-evaluation before deletion:** Pass 1 — is all content captured? Pass 2 — any missed detail (scan old doc line-by-line)? Pass 3 — final confirm. Only then delete.
- **web-sdk-plan/ disposition:** All subfolders absorbed into SPEC.md files and removed. `web-sdk-plan/INTEGRATION.md` content absorbed into **`docs/instrumentations/integration/SPEC.md`** (canonical host-app integration guide); sdk-core SPEC focuses on lifecycle and contracts — see integration SPEC for entrypoints and wiring. `agent-runtime/` folder (graph-cache.md, test-run-log.md) removed — not instrumentation knowledge.
- **docs/ disposition:** `API-CRITIQUE.md` absorbed into sdk-core SPEC §7 (gaps). `NAVIGATION-INSTRUMENTATION.md` absorbed into screen-signals SPEC.
- **examples/ docs:** `DEMO-QA-MAP.md`, `MANUAL-*.md`, `QA-CONTEXT-REFRESH-LOG.md` absorbed into their respective instrumentation SPECs §6 (test coverage) and §7 (gaps). The files are removed post-absorption.
- **graphify-out/ :** `GRAPH_REPORT.md` is a generated knowledge graph — keep it, do not remove.
- **CLAUDE.md references:** Any reference to `web-sdk-plan/` paths in `pulse-web-otel/CLAUDE.md` must be updated to point to `docs/instrumentations/` after cleanup issue runs. The file also has unresolved merge conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`) — resolve them in issue 10.
- **Cross-repo reference update (issue 10 scope):** After all SPEC.md files are written, scan and update every file in `.cursor/rules/`, `.cursor/skills/`, `.cursor/agents/`, and all `CLAUDE.md` files in the repo that reference any `web-sdk-plan/` path. Update each to point to the equivalent `docs/instrumentations/<name>/SPEC.md`. `.claude/` is a symlink tree to `.cursor/` — editing `.cursor/` files covers both.
- **No source code changes** to SDK `src/` in documentation issues (01–09, integration SPEC, and prep for cleanup). **`10-cleanup-references.md`** touches only: `pulse-web-otel/CLAUDE.md`, `.cursor/rules/*.mdc`, `.cursor/skills/**`, `.cursor/agents/**`, and root `CLAUDE.md` if needed.
- **Issue eval:** structural check only — verify SPEC.md was created at correct path with all 9 sections present. No yarn build/test required per issue (no source changes).
- **Global eval:** structural check across all **10** `SPEC.md` files under `docs/instrumentations/` + `yarn build` to catch any accidental breakage.

## Testing Decisions

- Issue evals are structural (bash grep/find checks) — they verify the doc artefact, not code.
- Global eval runs `yarn build` as a safety net (no source changes expected but guard against accidental edits).
- No `yarn test --run` in Global Eval — this is a docs-only change; full test suite is a separate gate.
- Each issue's eval is idempotent: re-runnable if the issue is re-attempted.

## Acceptance Criteria

- [ ] `docs/instrumentations/` contains exactly **10** subdirectories: `sdk-core`, `errors`, `clicks`, `web-vitals`, `network`, `screen-signals`, `interactions`, `react-integration`, `nextjs-integration`, **`integration`**
- [ ] Each subdirectory contains exactly one `SPEC.md`
- [ ] Each SPEC.md has all 9 sections: `## 1. Goal`, `## 2. Assumptions`, `## 3. Requirements`, `## 4. Architectural Design`, `## 5. LLD`, `## 6. Test Coverage`, `## 7. Known Bugs & Gaps`, `## 8. Redundancy & Cleanup Notes`, `## 9. Open Questions`
- [ ] Each SPEC.md section is non-empty (no section has only a heading with no content)
- [ ] Each SPEC.md §5 (LLD) explicitly covers: signal type, all required attributes, React SPA behaviour, Next.js App Router behaviour, Next.js Pages Router behaviour
- [ ] Each SPEC.md §7 prefixes P0 bugs clearly with `P0:` label
- [ ] `web-sdk-plan/` folder is empty or removed entirely
- [ ] `docs/API-CRITIQUE.md` and `docs/NAVIGATION-INSTRUMENTATION.md` are removed
- [ ] `examples/ecommerce-demo/DEMO-QA-MAP.md`, `MANUAL-*.md`, `QA-CONTEXT-REFRESH-LOG.md` are removed
- [ ] `graphify-out/GRAPH_REPORT.md` is **kept** (generated output — not removed)
- [ ] `pulse-web-otel/CLAUDE.md` has no merge conflict markers and no references to deleted file paths
- [ ] All `.cursor/rules/*.mdc` files contain no references to `web-sdk-plan/` paths
- [ ] All `.cursor/skills/**` files contain no references to `web-sdk-plan/` paths
- [ ] All `.cursor/agents/**` files contain no references to `web-sdk-plan/` paths
- [ ] `yarn build` exits 0 after all issues complete

## Out of Scope

- Source code changes to any instrumentation
- Adding new test coverage (gaps are documented in SPEC.md §6, not fixed here)
- Fixing bugs discovered during synthesis (bugs are documented in SPEC.md §7 §P0, not fixed here — separate work)
- `WEB-SDK-AGENT-CONTEXT.md` — does not exist on disk (CLAUDE.md references it but it is absent); no action needed
- `graphify-out/GRAPH_REPORT.md` — generated output, kept as-is
- `CHANGELOG.md`, `README.md` — operational consumer docs; not rewritten by instrumentation consolidation
- **Issue 12:** [`docs/publishing/SPEC.md`](../publishing/SPEC.md) plus runbooks at [`docs/publishing/PUBLISHING.md`](../publishing/PUBLISHING.md) and [`docs/publishing/QUICKSTART.md`](../publishing/QUICKSTART.md) — no publishing markdown at `pulse-web-otel/` root (issue checklist lives alongside those publishing docs in the same folder when filed)
- `examples/ecommerce-demo/` source code — only its doc files are removed
- `examples/web-sdk-docs/README.md` — usage guide, not planning doc; untouched

## Global Eval

```bash
#!/usr/bin/env bash
set -euo pipefail

DOCS_ROOT="pulse-web-otel/docs/instrumentations"
EXPECTED_DIRS="sdk-core errors clicks web-vitals network screen-signals interactions react-integration nextjs-integration integration"
REQUIRED_SECTIONS="## 1. Goal ## 2. Assumptions ## 3. Requirements ## 4. Architectural Design ## 5. LLD ## 6. Test Coverage ## 7. Known Bugs ## 8. Redundancy ## 9. Open Questions"

fail=0

# 1. All 10 directories exist
for dir in $EXPECTED_DIRS; do
  if [ ! -f "$DOCS_ROOT/$dir/SPEC.md" ]; then
    echo "MISSING: $DOCS_ROOT/$dir/SPEC.md"
    fail=1
  fi
done

# 2. Each SPEC.md has all required sections
for dir in $EXPECTED_DIRS; do
  spec="$DOCS_ROOT/$dir/SPEC.md"
  [ -f "$spec" ] || continue
  for section in "## 1. Goal" "## 2. Assumptions" "## 3. Requirements" "## 4. Architectural Design" "## 5. LLD" "## 6. Test Coverage" "## 7. Known Bugs" "## 8. Redundancy" "## 9. Open Questions"; do
    if ! grep -q "$section" "$spec"; then
      echo "MISSING SECTION '$section' in $spec"
      fail=1
    fi
  done
done

# 3. web-sdk-plan/ is gone or empty
if [ -d "pulse-web-otel/web-sdk-plan" ]; then
  remaining=$(find pulse-web-otel/web-sdk-plan -type f | wc -l | tr -d ' ')
  if [ "$remaining" -gt 0 ]; then
    echo "web-sdk-plan/ still has $remaining files — cleanup incomplete"
    fail=1
  fi
fi

# 4. Old docs removed (graphify-out/GRAPH_REPORT.md is KEPT — not checked here)
for old in "pulse-web-otel/docs/API-CRITIQUE.md" \
           "pulse-web-otel/docs/NAVIGATION-INSTRUMENTATION.md"; do
  if [ -f "$old" ]; then
    echo "OLD DOC NOT REMOVED: $old"
    fail=1
  fi
done

# 5. pulse-web-otel/CLAUDE.md: no merge conflicts, no stale web-sdk-plan refs
if grep -qE "^(<<<<<<<|=======|>>>>>>>)" "pulse-web-otel/CLAUDE.md" 2>/dev/null; then
  echo "pulse-web-otel/CLAUDE.md has unresolved merge conflict markers"
  fail=1
fi
if grep -q "web-sdk-plan" "pulse-web-otel/CLAUDE.md" 2>/dev/null; then
  echo "pulse-web-otel/CLAUDE.md still references web-sdk-plan/ — update needed"
  fail=1
fi

# 6. .cursor/ skills/rules/agents: no stale web-sdk-plan refs
stale=$(grep -rl "web-sdk-plan" .cursor/rules/ .cursor/skills/ .cursor/agents/ 2>/dev/null | tr '\n' ' ')
if [ -n "$stale" ]; then
  echo "Stale web-sdk-plan/ refs in .cursor/: $stale"
  fail=1
fi

# 7. Build still passes
cd pulse-web-otel && yarn build 2>&1 | tail -5
build_exit=$?
cd ..
if [ $build_exit -ne 0 ]; then
  echo "yarn build FAILED"
  fail=1
fi

exit $fail
```

## Publishing docs (issue 12)

Canonical npm operational spec: [`docs/publishing/SPEC.md`](../publishing/SPEC.md). Long runbook: [`docs/publishing/PUBLISHING.md`](../publishing/PUBLISHING.md). Quickstart: [`docs/publishing/QUICKSTART.md`](../publishing/QUICKSTART.md). Naming: npm package `@dreamhorizonorg/pulse-web`, repo folder `pulse-web-otel/`.

## Further Notes

**Issue execution order matters:** sdk-core must complete first (it defines the shared data contract table that all other SPECs reference in §5). Each issue agent should read `docs/instrumentations/sdk-core/SPEC.md` before writing its own §5.

**SPEC.md §5 attribute table format** (enforce consistency across all 10 docs):
```
| Attribute key | Type | Source | Required | Notes |
|---|---|---|---|---|
| pulse.type | string | semconv | yes | always "X" for this instrumentation |
```

**P0 bug definition for §7:** A P0 bug is one that causes incorrect or missing data in ClickHouse — wrong attribute value, missing signal, duplicate emission, or silent swallowing of an error. Anything that breaks the data contract is P0.

**Issue 10 (cleanup) special rule:** Before deleting any file, log its path and the SPEC.md section where its content was absorbed. The commit message for issue 10 must list every deleted file.

**Issue 10 also covers `.cursor/` + `.claude/` reference hygiene:**
1. Grep every `.cursor/rules/*.mdc`, `.cursor/skills/**/*.md`, `.cursor/agents/**/*.md` for `web-sdk-plan/` occurrences.
2. For each hit, replace the stale path with the equivalent `docs/instrumentations/<name>/SPEC.md` anchor.
3. Resolve the unresolved merge conflict in `pulse-web-otel/CLAUDE.md` — take the HEAD version for the `screen_load`/`screen_interactive` note (the newer version references `navigation.ts` which is the current implementation).
4. `graphify-out/GRAPH_REPORT.md` is **not touched** — it is kept as-is.
