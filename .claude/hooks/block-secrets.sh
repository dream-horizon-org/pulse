#!/usr/bin/env bash
# Prevents committing .env files or echoing secrets.
# Receives JSON on stdin from Claude Code PreToolUse hook.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('command',''))" 2>/dev/null)

# Block git add/commit on .env files
if echo "$COMMAND" | grep -qE "git (add|commit).*\.env[^.]"; then
  python3 -c "
import json
print(json.dumps({
  'hookSpecificOutput': {
    'hookEventName': 'PreToolUse',
    'permissionDecision': 'deny',
    'permissionDecisionReason': 'Blocked: .env files must never be committed. Use .env.example as template.'
  }
}))
"
  exit 0
fi

# Block force-push to main
if echo "$COMMAND" | grep -qE "git push.*(--force|-f).*(main|master)"; then
  python3 -c "
import json
print(json.dumps({
  'hookSpecificOutput': {
    'hookEventName': 'PreToolUse',
    'permissionDecision': 'deny',
    'permissionDecisionReason': 'Blocked: Force-pushing to main/master is not allowed.'
  }
}))
"
  exit 0
fi

exit 0
