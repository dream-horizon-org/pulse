#!/bin/bash
# Injects dynamic context at session start: git branch, running Docker services, recent changes.

json_input=$(cat)

context_lines=()

git_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
if [ -n "$git_branch" ]; then
  context_lines+=("Current git branch: $git_branch")
  dirty_count=$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')
  if [ "$dirty_count" -gt 0 ]; then
    context_lines+=("Uncommitted changes: $dirty_count files modified")
  fi
fi

if command -v docker &>/dev/null; then
  running=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -i pulse | tr '\n' ', ' | sed 's/,$//')
  if [ -n "$running" ]; then
    context_lines+=("Running Pulse Docker services: $running")
  else
    context_lines+=("No Pulse Docker services currently running")
  fi
fi

recent_commits=$(git log --oneline -3 2>/dev/null | tr '\n' '; ' | sed 's/;$//')
if [ -n "$recent_commits" ]; then
  context_lines+=("Recent commits: $recent_commits")
fi

additional_context=""
for line in "${context_lines[@]}"; do
  additional_context+="$line\n"
done

cat << EOF
{
  "additional_context": "$(echo -e "$additional_context" | sed 's/"/\\"/g' | tr '\n' ' ')",
  "continue": true
}
EOF
