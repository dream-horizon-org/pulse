#!/bin/bash
# On agent stop, checks which files were modified and runs the appropriate build/lint verification.
# If verification fails, returns a followup_message to auto-continue.

json_input=$(cat)
status=$(echo "$json_input" | grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/"status"[[:space:]]*:[[:space:]]*"//;s/"$//')

# Only verify on successful completion
if [ "$status" != "completed" ]; then
  echo '{}'
  exit 0
fi

changed_files=$(git diff --name-only HEAD 2>/dev/null)
if [ -z "$changed_files" ]; then
  changed_files=$(git diff --name-only 2>/dev/null)
fi

if [ -z "$changed_files" ]; then
  echo '{}'
  exit 0
fi

errors=()

# Check backend changes
if echo "$changed_files" | grep -q "^backend/server/"; then
  if [ -f "backend/server/pom.xml" ]; then
    if ! cd backend/server && mvn compile -q 2>/dev/null; then
      errors+=("Backend: Maven compile failed. Run 'cd backend/server && mvn clean install' to see full errors.")
    fi
    cd - > /dev/null 2>&1
  fi
fi

# Check frontend changes
if echo "$changed_files" | grep -q "^pulse-ui/"; then
  if [ -f "pulse-ui/package.json" ]; then
    if command -v npx &>/dev/null && [ -d "pulse-ui/node_modules" ]; then
      if ! npx --prefix pulse-ui tsc --noEmit 2>/dev/null; then
        errors+=("Frontend: TypeScript check failed. Run 'cd pulse-ui && npx tsc --noEmit' to see errors.")
      fi
    fi
  fi
fi

# Check Python changes
if echo "$changed_files" | grep -q "^pulse_ai/"; then
  if command -v python3 &>/dev/null; then
    py_files=$(echo "$changed_files" | grep "^pulse_ai/.*\.py$")
    for f in $py_files; do
      if [ -f "$f" ] && ! python3 -c "import py_compile; py_compile.compile('$f', doraise=True)" 2>/dev/null; then
        errors+=("Python: Syntax error in $f")
      fi
    done
  fi
fi

# Check if source-of-truth files changed without .cursor/ updates
sot_files=("deploy/docker-compose.yml" "backend/ingestion/clickhouse-otel-schema.sql" "deploy/.env.example" "deploy/scripts/build.sh" "deploy/scripts/start.sh")
sot_changed=false
for f in "${sot_files[@]}"; do
  if echo "$changed_files" | grep -q "^${f}$"; then
    sot_changed=true
    break
  fi
done

if [ "$sot_changed" = true ]; then
  if ! echo "$changed_files" | grep -q "^\.cursor/"; then
    errors+=("Source-of-truth files changed (docker-compose, schema, env, or scripts) but .cursor/ config was not updated. Run /audit-cursor-config to check for stale docs.")
  fi
fi

if [ ${#errors[@]} -gt 0 ]; then
  error_msg=$(printf '%s\\n' "${errors[@]}" | sed 's/"/\\"/g')
  cat << EOF
{
  "followup_message": "Build verification found issues after your changes:\\n${error_msg}\\nPlease fix these errors."
}
EOF
else
  echo '{}'
fi
