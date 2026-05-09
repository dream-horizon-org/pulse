#!/bin/bash
# Ralph loop for Cursor CLI — autonomous issue-driven coding loop.
#
# Pre-reqs:
#   1. PRD.md            at $RALPH_WORK_DIR
#   2. issues/*.md       at $RALPH_WORK_DIR (each with ## Eval blocks)
#   3. ralph/prompt.md   iteration instructions
#   4. cursor CLI on PATH (Cursor Agent)
#
# Differences from ralph.sh:
#   - Uses `cursor agent` instead of `claude`
#   - Tool compatibility: Read, Edit, Write, Bash (core Cursor tools)
#   - No Agent tool (Cursor agents are local, can't spawn sub-agents)
#   - Per-issue evaluation via bash Eval blocks
#
# Usage:
#   ./ralph/ralph-cursor.sh                # default (iter cap = 2 * issues)
#   ./ralph/ralph-cursor.sh --max-iters 30
#   ./ralph/ralph-cursor.sh --dry-run
#   ./ralph/ralph-cursor.sh --bypass       # bypassPermissions
#
# Exit codes:
#   0  COMPLETE or NO MORE TASKS
#   1  EVAL_FAILED (per-issue gate)
#   2  GLOBAL EVAL failed
#   3  Hit max iterations with work remaining
#  64+ Pre-flight / config errors

set -uo pipefail

# ───────────────────────── config ─────────────────────────
RALPH_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$RALPH_DIR/.." && pwd)"
WORK_DIR="${RALPH_WORK_DIR:-$REPO_ROOT}"
PRD="$WORK_DIR/PRD.md"
ISSUES_DIR="$WORK_DIR/issues"
DONE_DIR="$ISSUES_DIR/done"
PROGRESS="$WORK_DIR/progress-cursor.txt"
PROMPT_FILE="$RALPH_DIR/prompt.md"
LOG_DIR="$RALPH_DIR/.logs-cursor"

# Cursor agent supported tools: Read, Edit, Write, Bash, Glob, Grep
# (No MCP, No WebFetch, No Agent spawning)
ALLOWED_TOOLS="Read,Edit,Write,Bash,Glob,Grep"
PERM_MODE="acceptEdits"

MAX_ITERS=""
DRY_RUN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --max-iters) MAX_ITERS="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --bypass) PERM_MODE="bypassPermissions"; shift ;;
    *) echo "unknown flag: $1" >&2; exit 1 ;;
  esac
done

# ───────────────────────── pre-flight: files ─────────────────────────
[[ -f "$PRD" ]] || { echo "PRD.md not found at $PRD" >&2; exit 64; }
[[ -d "$ISSUES_DIR" ]] || { echo "issues/ not found at $ISSUES_DIR" >&2; exit 64; }
[[ -f "$PROMPT_FILE" ]] || { echo "prompt.md not found at $PROMPT_FILE" >&2; exit 64; }
mkdir -p "$LOG_DIR" "$DONE_DIR"

# ───────────────────────── pre-flight: tooling ─────────────────────────
command -v cursor >/dev/null || { echo "cursor CLI not on PATH" >&2; exit 127; }

if [[ "$PERM_MODE" == "bypassPermissions" ]]; then
  echo "⚠️  bypassPermissions enabled — confirm you are in a worktree or container." >&2
fi

# ───────────────────────── pre-flight: validate issues ─────────────────────────
preflight_issues() {
  local errs=0 f
  for f in "$ISSUES_DIR"/*.md; do
    [[ -f "$f" ]] || continue
    if ! grep -q "^## Eval$" "$f"; then
      echo "  ✗ $f — missing '## Eval' heading" >&2
      ((errs++))
    elif ! awk '/^## Eval/{flag=1;next} flag && /^```bash$/{cap=1;next} flag && /^```$/{exit} cap{found=1} END{exit (found?0:1)}' "$f"; then
      echo "  ✗ $f — '## Eval' has no fenced bash block" >&2
      ((errs++))
    fi
  done
  return $errs
}

preflight_package_fields() {
  if [[ "$WORK_DIR" == "$REPO_ROOT" ]]; then
    for f in "$ISSUES_DIR"/*.md; do
      [[ -f "$f" ]] || continue
      grep -q '^## Package' "$f" || echo "  ⚠  $f — missing '## Package' (cross-package run)" >&2
    done
  fi
}

echo "── pre-flight: validating issues/*.md ──"
if ! preflight_issues; then
  echo "fix the issues above and retry" >&2
  exit 70
fi
echo "  ✓ all issues have valid Eval blocks"

echo "── pre-flight: validating Package fields ──"
preflight_package_fields
echo "  ✓ pre-flight complete"

ISSUE_COUNT=$(find "$ISSUES_DIR" -maxdepth 1 -name "*.md" -type f | wc -l | tr -d ' ')
[[ "$ISSUE_COUNT" -gt 0 ]] || { echo "no open issues in $ISSUES_DIR" >&2; exit 67; }

[[ -z "$MAX_ITERS" ]] && MAX_ITERS=$((ISSUE_COUNT * 2))

# ───────────────────────── progress.txt init ─────────────────────────
if [[ ! -f "$PROGRESS" ]]; then
  {
    echo "# Ralph loop (Cursor) — started $(date -u +%FT%TZ)"
    echo "# Repo:        $WORK_DIR"
    echo "# PRD:         $PRD"
    echo "# Prompt:      $PROMPT_FILE"
    echo "# Issue count: $ISSUE_COUNT"
    echo "# Max iters:   $MAX_ITERS"
    echo "# Perm mode:   $PERM_MODE"
    echo "# CLI:         cursor agent"
    echo "# Tools:       $ALLOWED_TOOLS"
    echo "# Git head:    $(git -C "$WORK_DIR" rev-parse --short HEAD 2>/dev/null || echo n/a)"
    echo ""
  } > "$PROGRESS"
fi

# ───────────────────────── global eval ─────────────────────────
extract_global_eval() {
  awk '/^## Global Eval/{flag=1;next} flag && /^```bash$/{cap=1;next} flag && /^```$/{cap=0;flag=0} cap{print}' "$PRD"
}

run_global_eval() {
  local out
  out=$(extract_global_eval | bash 2>&1)
  local rc=$?
  echo "$out"
  return $rc
}

# ───────────────────────── per-issue eval ─────────────────────────
extract_issue_eval() {
  local f="$1"
  awk '/^## Eval/{flag=1;next} flag && /^```bash$/{cap=1;next} flag && /^```$/{cap=0;flag=0} cap{print}' "$f"
}

run_issue_eval() {
  local f="$1" out
  out=$(extract_issue_eval "$f" | bash 2>&1)
  local rc=$?
  echo "$out"
  return $rc
}

# ───────────────────────── issue picking ─────────────────────────
pick_next_issue() {
  # Find lowest-numbered issue that is not in done/ and has no blockers
  local issue lowest_num lowest_file
  for issue in $(ls -1 "$ISSUES_DIR"/*.md 2>/dev/null | xargs -I {} basename {} | sort); do
    [[ -f "$DONE_DIR/$issue" ]] && continue
    lowest_file="$issue"
    break
  done
  [[ -n "$lowest_file" ]] && echo "$ISSUES_DIR/$lowest_file"
}

# ───────────────────────── build prompt for iteration ─────────────────────────
build_prompt() {
  local iter="$1" max_iters="$2" open_count="$3"
  local issue_file="$4"
  local issue_num issue_title

  issue_num=$(basename "$issue_file" | cut -d- -f1)
  issue_title=$(grep "^# " "$issue_file" | head -1 | sed 's/^# //')

  cat <<'PROMPT_START'
You are Ralph, an autonomous coding agent for Pulse (pulse-web-otel).

**Your task this iteration:**

1. Read the current issue file (passed as context below).
2. Understand the acceptance criteria and implementation requirements.
3. Write tests first (TDD): create test skeletons if not present.
4. Implement to pass tests.
5. Run the issue's Eval block (bash) to verify correctness.
6. If Eval passes: commit changes with a clear message.
7. If Eval fails: fix implementation, re-run tests, retry Eval.

**Constraints:**
- Use only: Read, Edit, Write, Bash, Glob, Grep tools (Cursor agent tools).
- TDD mandatory: write tests before implementation.
- No spawning sub-agents (Cursor CLI limitation).
- Commit each issue; don't batch.
- If unsure, ask clarifying questions.

PROMPT_START

  echo "---"
  echo "## Current issue ($(date -u +%FT%TZ))"
  echo ""
  echo "**Issue $issue_num / $open_count open** (iteration $iter / $max_iters)"
  echo ""
  cat "$issue_file"
}

# ───────────────────────── main loop ─────────────────────────────
[[ $DRY_RUN -eq 1 ]] && {
  echo "dry-run mode: preflight passed, issues ready"
  echo "to run: ./ralph/ralph-cursor.sh"
  exit 0
}

echo "── ralph loop starting (Cursor CLI) ──"
echo ""

iter=0
while [[ $iter -lt $MAX_ITERS ]]; do
  ((iter++))

  issue_file=$(pick_next_issue)
  [[ -n "$issue_file" ]] || {
    echo "[$(date -u +%FT%TZ)] all issues complete" | tee -a "$PROGRESS"
    exit 0
  }

  open_count=$(find "$ISSUES_DIR" -maxdepth 1 -name "*.md" -not -path "$DONE_DIR/*" -type f | wc -l)
  issue_num=$(basename "$issue_file" | cut -d- -f1)

  echo ""
  echo "════════════════════════════════════════════"
  echo "ITERATION $iter / $MAX_ITERS"
  echo "ISSUE $issue_num ($(basename "$issue_file"))"
  echo "════════════════════════════════════════════"

  TS=$(date +%Y%m%d-%H%M%S)
  LOG_FILE="$LOG_DIR/iter-${iter}-${issue_num}-${TS}.log"
  mkdir -p "$(dirname "$LOG_FILE")"
  echo "log: $LOG_FILE"

  # Invoke Cursor agent with iteration prompt + issue context
  build_prompt "$iter" "$MAX_ITERS" "$open_count" "$issue_file" |
    (cd "$WORK_DIR" && cursor agent 2>&1) |
    tee "$LOG_FILE"

  result="$(<"$LOG_FILE")"

  # Run issue's Eval block
  echo ""
  echo "── running issue Eval block ──"
  if run_issue_eval "$issue_file" >> "$LOG_FILE" 2>&1; then
    echo "✓ Eval passed"
    mv "$issue_file" "$DONE_DIR/"
    echo "[$(date -u +%FT%TZ)] issue $issue_num PASSED (iter $iter)" | tee -a "$PROGRESS"

    # Run global Eval after each issue
    echo ""
    echo "── running global Eval ──"
    if ! run_global_eval >> "$LOG_FILE" 2>&1; then
      echo "✗ Global Eval FAILED (regression)" | tee -a "$PROGRESS"
      exit 2
    fi
    echo "✓ Global Eval passed"
  else
    echo "✗ Eval FAILED" | tee -a "$PROGRESS"
    echo "[$(date -u +%FT%TZ)] issue $issue_num FAILED (iter $iter)" | tee -a "$PROGRESS"
    exit 1
  fi
done

echo "[$(date -u +%FT%TZ)] hit max iterations ($MAX_ITERS) with work remaining" | tee -a "$PROGRESS"
exit 3
