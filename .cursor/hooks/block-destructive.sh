#!/bin/bash
# Prompts for confirmation before destructive database or Docker commands.

json_input=$(cat)
command=$(echo "$json_input" | grep -o '"command"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/"command"[[:space:]]*:[[:space:]]*"//;s/"$//')

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
  if echo "$command" | grep -iqE "$pattern"; then
    cat << EOF
{
  "permission": "ask",
  "user_message": "⚠️  Destructive command detected: this command matches '$pattern'. Please confirm you want to proceed.",
  "agent_message": "This command was flagged as potentially destructive (matched: '$pattern'). The user must confirm before execution. Do not retry without explicit user approval."
}
EOF
    exit 0
  fi
done

cat << EOF
{
  "permission": "allow"
}
EOF
