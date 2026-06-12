#!/bin/bash
# Blocks agent from reading .env files, credentials, keys, and other sensitive files.
# Uses fail-closed behavior: if this script crashes, the read is blocked.

json_input=$(cat)
file_path=$(echo "$json_input" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/"file_path"[[:space:]]*:[[:space:]]*"//;s/"$//')

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

# Allow .env.example files -- those are templates, not secrets
if echo "$file_path" | grep -qE '\.env\.example$'; then
  cat << EOF
{
  "permission": "allow"
}
EOF
  exit 0
fi

for pattern in "${BLOCKED_PATTERNS[@]}"; do
  if echo "$file_path" | grep -qE "$pattern"; then
    cat << EOF
{
  "permission": "deny",
  "user_message": "Blocked: reading sensitive file '$file_path'. This file may contain secrets. Use .env.example as reference instead."
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
