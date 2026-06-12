#!/bin/bash
# Blocks shell commands that contain secrets, API keys, or passwords.

json_input=$(cat)
command=$(echo "$json_input" | grep -o '"command"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/"command"[[:space:]]*:[[:space:]]*"//;s/"$//')

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
  if echo "$command" | grep -iqE "$pattern"; then
    cat << EOF
{
  "permission": "deny",
  "user_message": "Blocked: command appears to contain a secret or credential. Use environment variables or .env files instead.",
  "agent_message": "This command was blocked because it contains what appears to be a hardcoded secret matching pattern '$pattern'. Use environment variables (e.g., \$MY_SECRET) or .env files instead of inline credentials."
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
