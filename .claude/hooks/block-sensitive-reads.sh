#!/bin/bash
# PreToolUse(Read). Denies reads of .env / credentials / private keys.
# Allows .env.example (templates).

set +e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

json_input=$(cat)
file_path=$(hook_json_field "$json_input" '.tool_input.file_path')

if [ -z "$file_path" ]; then
  exit 0
fi

# .env.example is a template, allow.
if printf '%s' "$file_path" | grep -qE '\.env\.example$'; then
  exit 0
fi

BLOCKED_PATTERNS=(
  '\.env$'
  '\.env\.local$'
  '\.env\.production$'
  '\.env\.staging$'
  'credentials\.json'
  'service-account.*\.json'
  '\.pem$'
  '\.key$'
  '\.p12$'
  '\.pfx$'
  'id_rsa'
  'id_ed25519'
  '\.keystore$'
  '\.jks$'
)

for pattern in "${BLOCKED_PATTERNS[@]}"; do
  if printf '%s' "$file_path" | grep -qE "$pattern"; then
    hook_pretool_decision "deny" "Blocked: reading sensitive file '$file_path'. This file may contain secrets. Use .env.example as reference instead."
  fi
done

exit 0
