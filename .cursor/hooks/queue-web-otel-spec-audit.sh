#!/usr/bin/env bash
# When afterFileEdit touches pulse-web-otel/src/ OR SPEC/docs under
# pulse-web-otel/docs/sdk-core/ or pulse-web-otel/docs/instrumentations/:
# resolve instrumentation id, append one JSON line to
# .cursor/pulse-web-otel-spec-audit-queue.jsonl, and echo a stderr nudge for
# the Hooks channel (self-filter; no hooks.json matcher).
set -euo pipefail

json_input=$(cat)
file_path=$(echo "$json_input" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/"file_path"[[:space:]]*:[[:space:]]*"//;s/"$//')

if [[ -z "$file_path" ]]; then
  exit 0
fi

HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HOOK_DIR/../.." && pwd)"
QUEUE="$REPO_ROOT/.cursor/pulse-web-otel-spec-audit-queue.jsonl"

instr=""
case "$file_path" in
  pulse-web-otel/docs/sdk-core/*)
    instr=sdk-core
    ;;
  pulse-web-otel/docs/instrumentations/*)
    rest="${file_path#pulse-web-otel/docs/instrumentations/}"
    seg="${rest%%/*}"
    case "$seg" in
      session|clicks|errors|integration|interactions|network|nextjs-integration|react-integration|screen-signals|web-vitals)
        instr="$seg"
        ;;
    esac
    ;;
esac

if [[ -z "$instr" ]] && [[ "$file_path" == pulse-web-otel/src/* ]]; then
  if ! instr="$(node "$HOOK_DIR/resolve-spec-audit-instrumentation.mjs" "$REPO_ROOT" "$file_path" 2>/dev/null)"; then
    exit 0
  fi
fi

if [[ -z "$instr" ]]; then
  exit 0
fi

ts="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
node -e '
const fs = require("fs");
const line = JSON.stringify({
  ts: process.argv[1],
  file_path: process.argv[2],
  instrumentation_id: process.argv[3],
}) + "\n";
fs.appendFileSync(process.argv[4], line, "utf8");
' "$ts" "$file_path" "$instr" "$QUEUE"

echo "SPEC audit queued: instrumentation_id=${instr} file=${file_path}" >&2

exit 0
