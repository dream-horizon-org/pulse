#!/usr/bin/env bash
# Blocks destructive shell commands before execution.
# Receives JSON on stdin from Claude Code PreToolUse hook.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('command',''))" 2>/dev/null)

BLOCKED_PATTERNS=(
  "rm -rf"
  "git push --force"
  "git push -f "
  "git reset --hard"
  "reset-databases"
  "docker system prune"
  "DROP TABLE"
  "DROP DATABASE"
  "TRUNCATE TABLE"
)

for pattern in "${BLOCKED_PATTERNS[@]}"; do
  if echo "$COMMAND" | grep -qi "$pattern"; then
    python3 -c "
import json, sys
print(json.dumps({
  'hookSpecificOutput': {
    'hookEventName': 'PreToolUse',
    'permissionDecision': 'deny',
    'permissionDecisionReason': 'Blocked: \"$pattern\" requires explicit user confirmation. Please ask the user before proceeding.'
  }
}))
"
    exit 0
  fi
done

exit 0
