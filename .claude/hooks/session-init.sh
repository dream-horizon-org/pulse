#!/bin/bash
# SessionStart hook. Injects branch / running services / recent commits via
# hookSpecificOutput.additionalContext.

set +e
json_input=$(cat 2>/dev/null)

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
if [ -n "$REPO_ROOT" ]; then
  cd "$REPO_ROOT" || true
fi

context_lines=()

git_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
if [ -n "$git_branch" ]; then
  context_lines+=("Current git branch: $git_branch")
  dirty_count=$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')
  if [ "$dirty_count" -gt 0 ]; then
    context_lines+=("Uncommitted changes: $dirty_count files modified")
  fi
fi

if command -v docker >/dev/null 2>&1; then
  running=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -i pulse | tr '\n' ',' | sed 's/,$//;s/,/, /g')
  if [ -n "$running" ]; then
    context_lines+=("Running Pulse Docker services: $running")
  else
    context_lines+=("No Pulse Docker services currently running")
  fi
fi

recent_commits=$(git log --oneline -3 2>/dev/null | tr '\n' ';' | sed 's/;$//;s/;/; /g')
if [ -n "$recent_commits" ]; then
  context_lines+=("Recent commits: $recent_commits")
fi

if [ ${#context_lines[@]} -eq 0 ]; then
  exit 0
fi

joined=""
for line in "${context_lines[@]}"; do
  joined+="${line}"$'\n'
done

if command -v jq >/dev/null 2>&1; then
  jq -nc --arg c "$joined" \
    '{hookSpecificOutput:{hookEventName:"SessionStart",additionalContext:$c}}'
else
  python3 -c "
import json
print(json.dumps({'hookSpecificOutput':{'hookEventName':'SessionStart','additionalContext':'''$joined'''}}))
"
fi
exit 0