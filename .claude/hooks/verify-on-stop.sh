#!/bin/bash
# Stop hook. Compiles touched modules and asks Claude to fix any errors before
# stopping. Honors stop_hook_active to avoid infinite loops.
#
# Output contract:
#   {}                                         => allow stop
#   {"decision":"block","reason":"..."}        => Claude continues, sees reason

set +e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

json_input=$(cat)
stop_active=$(hook_json_field "$json_input" '.stop_hook_active')

# Loop guard: if Claude is already in a stop-hook continuation, do not fire again.
if [ "$stop_active" = "true" ]; then
  echo '{}'
  exit 0
fi

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
if [ -z "$REPO_ROOT" ]; then
  echo '{}'
  exit 0
fi
cd "$REPO_ROOT" || { echo '{}'; exit 0; }

changed_files=$(git diff --name-only HEAD 2>/dev/null)
if [ -z "$changed_files" ]; then
  changed_files=$(git diff --name-only 2>/dev/null)
fi

if [ -z "$changed_files" ]; then
  echo '{}'
  exit 0
fi

errors=()

# Backend (Java) compile check
if printf '%s\n' "$changed_files" | grep -q "^backend/server/"; then
  if [ -f "backend/server/pom.xml" ]; then
    if ! ( cd backend/server && mvn compile -q >/dev/null 2>&1 ); then
      errors+=("Backend: Maven compile failed. Run 'cd backend/server && mvn clean install' for full errors.")
    fi
  fi
fi

# Frontend (TypeScript) check
if printf '%s\n' "$changed_files" | grep -q "^pulse-ui/"; then
  if [ -d "pulse-ui/node_modules" ] && [ -f "pulse-ui/node_modules/.bin/tsc" ]; then
    if ! ( cd pulse-ui && ./node_modules/.bin/tsc --noEmit >/dev/null 2>&1 ); then
      errors+=("Frontend: TypeScript check failed. Run 'cd pulse-ui && npx tsc --noEmit' for errors.")
    fi
  fi
fi

# Python syntax check
if printf '%s\n' "$changed_files" | grep -q "^pulse_ai/"; then
  if command -v python3 >/dev/null 2>&1; then
    while IFS= read -r f; do
      [ -z "$f" ] && continue
      [ -f "$f" ] || continue
      if ! python3 -c "import py_compile; py_compile.compile('$f', doraise=True)" >/dev/null 2>&1; then
        errors+=("Python: Syntax error in $f")
      fi
    done < <(printf '%s\n' "$changed_files" | grep '^pulse_ai/.*\.py$')
  fi
fi

# Source-of-truth drift: deploy/schema files changed without .cursor/ docs update
sot_files=("deploy/docker-compose.yml" "deploy/.env.example" "deploy/scripts/build.sh" "deploy/scripts/start.sh" "deploy/scripts/common.sh")
sot_changed=false
for f in "${sot_files[@]}"; do
  if printf '%s\n' "$changed_files" | grep -qx "$f"; then
    sot_changed=true
    break
  fi
done
if [ "$sot_changed" = false ] && printf '%s\n' "$changed_files" | grep -q "^backend/db/dev/clickhouse/"; then
  sot_changed=true
fi
if [ "$sot_changed" = true ] && ! printf '%s\n' "$changed_files" | grep -q "^\.cursor/\|^\.claude/"; then
  errors+=("Source-of-truth files changed (compose/schema/env/scripts) but .cursor/ or .claude/ docs were not updated. Run /audit-cursor-config.")
fi

if [ ${#errors[@]} -eq 0 ]; then
  echo '{}'
  exit 0
fi

reason="Build verification found issues after your changes:"
for e in "${errors[@]}"; do
  reason+=$'\n- '"$e"
done
reason+=$'\nPlease fix these errors.'

if command -v jq >/dev/null 2>&1; then
  jq -nc --arg r "$reason" '{decision:"block",reason:$r}'
else
  python3 -c "
import json, sys
print(json.dumps({'decision':'block','reason':'''$reason'''}))
"
fi
exit 0