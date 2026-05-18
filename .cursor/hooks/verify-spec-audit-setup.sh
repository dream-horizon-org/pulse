#!/usr/bin/env bash
# Smoke-check SPEC audit hooks + index + agent definition. Prints PASS or FAIL.
set -euo pipefail

HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HOOK_DIR/../.." && pwd)"
cd "$REPO_ROOT"
FAIL=0

die() {
  echo "FAIL: $*" >&2
  FAIL=1
}

grep -q 'pulse-web-otel-spec-audit-queue.jsonl' .gitignore || die ".gitignore missing queue file entry"
grep -q 'pulse-web-otel/.spec-audit/' .gitignore || die ".gitignore missing .spec-audit/ entry"

test -f .agents/agents/pulse-web-sdk.md || die ".agents/agents/pulse-web-sdk.md missing"
test -f .agents/agents/web-otel-spec-audit-orchestrator.md || die ".agents/agents/web-otel-spec-audit-orchestrator.md missing"
test -L .claude/agents/web-otel-spec-audit-orchestrator.md || die ".claude/agents/web-otel-spec-audit-orchestrator.md symlink missing"
test -L .cursor/agents/web-otel-spec-audit-orchestrator.md || die ".cursor/agents/web-otel-spec-audit-orchestrator.md symlink missing"

test -f .cursor/skills/web-otel-spec-implementation-audit/audit-index.json || die "audit-index.json missing"
node -e "JSON.parse(require('fs').readFileSync('.cursor/skills/web-otel-spec-implementation-audit/audit-index.json','utf8'));" || die "audit-index.json invalid JSON"

test -x .cursor/hooks/queue-web-otel-spec-audit.sh || die "queue-web-otel-spec-audit.sh not executable"

instr="$(node "$HOOK_DIR/resolve-spec-audit-instrumentation.mjs" "$REPO_ROOT" "pulse-web-otel/src/instrumentations/errors.ts" 2>/dev/null)" || true
[[ "$instr" == "errors" ]] || die "resolver expected errors got '${instr:-empty}'"

instr="$(node "$HOOK_DIR/resolve-spec-audit-instrumentation.mjs" "$REPO_ROOT" "pulse-web-otel/src/instrumentations/session.ts" 2>/dev/null)" || true
[[ "$instr" == "session" ]] || die "resolver expected session got '${instr:-empty}'"

test -f pulse-web-otel/docs/sdk-core/SPEC.md || die "pulse-web-otel/docs/sdk-core/SPEC.md missing"
test -f pulse-web-otel/docs/instrumentations/session/SPEC.md || die "pulse-web-otel/docs/instrumentations/session/SPEC.md missing"
grep -q '"spec_path": "pulse-web-otel/docs/sdk-core/SPEC.md"' .cursor/skills/web-otel-spec-implementation-audit/audit-index.json || die "audit-index sdk-core spec_path not docs/sdk-core"

if [[ "$FAIL" -eq 0 ]]; then
  echo "PASS: web-otel SPEC audit setup OK"
  exit 0
fi
exit 1
