#!/bin/bash
# PreToolUse(Bash). Forces user prompt for destructive DB / Docker / git ops
# via permissionDecision="ask".

set +e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

json_input=$(cat)
command=$(hook_json_field "$json_input" '.tool_input.command')

if [ -z "$command" ]; then
  exit 0
fi

DESTRUCTIVE_PATTERNS=(
  'DROP TABLE'
  'DROP DATABASE'
  'DROP INDEX'
  'TRUNCATE'
  'DELETE FROM'
  'docker.*rm.*-f'
  'docker.*volume.*rm'
  'docker.*system.*prune'
  'reset-databases'
  'push.*--force'
  'push.*-f '
  'git.*reset.*--hard'
  'rm -rf /'
)

for pattern in "${DESTRUCTIVE_PATTERNS[@]}"; do
  if printf '%s' "$command" | grep -iqE "$pattern"; then
    hook_pretool_decision "ask" "Destructive command detected (matched: '$pattern'). Confirm before executing."
  fi
done

exit 0