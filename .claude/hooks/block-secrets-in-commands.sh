#!/bin/bash
# PreToolUse(Bash). Denies commands containing apparent secrets/keys.
# Fail-open: any unexpected error => allow (exit 0).

set +e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

json_input=$(cat)
command=$(hook_json_field "$json_input" '.tool_input.command')

if [ -z "$command" ]; then
  exit 0
fi

SECRET_PATTERNS=(
  'GOOGLE_API_KEY=[^$]'
  'AWS_SECRET_ACCESS_KEY=[^$]'
  'AWS_ACCESS_KEY_ID=[^$]'
  'JWT_SECRET=[^$]'
  'SLACK.*TOKEN=[^$]'
  'password=[^$]'
  'PASSWORD=[^$]'
  'api[_-]?key=[^$]'
  'secret=[^$]'
  'BEGIN RSA PRIVATE KEY'
  'BEGIN PRIVATE KEY'
  'xoxb-'
  'xapp-'
  'sk-[a-zA-Z0-9]{20,}'
  'ghp_[a-zA-Z0-9]{36}'
  'gho_[a-zA-Z0-9]{36}'
)

for pattern in "${SECRET_PATTERNS[@]}"; do
  if printf '%s' "$command" | grep -iqE "$pattern"; then
    hook_pretool_decision "deny" "Blocked: command appears to contain a secret matching pattern '$pattern'. Use environment variables (\$MY_SECRET) or .env files instead of inline credentials."
  fi
done

exit 0