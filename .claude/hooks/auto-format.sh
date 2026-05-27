#!/bin/bash
# PostToolUse(Edit|Write|MultiEdit). Runs the appropriate formatter on the
# edited file. Best-effort: missing formatters are silently skipped.

set +e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

json_input=$(cat)
file_path=$(hook_json_field "$json_input" '.tool_input.file_path')

if [ -z "$file_path" ] || [ ! -f "$file_path" ]; then
  exit 0
fi

# Only format files inside the current repo. Without this guard the hook
# happily reformats anything in /tmp, $HOME, etc.
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
if [ -z "$REPO_ROOT" ]; then
  exit 0
fi
abs_path="$(cd "$(dirname "$file_path")" 2>/dev/null && pwd)/$(basename "$file_path")"
case "$abs_path" in
  "$REPO_ROOT"/*) ;;
  *) exit 0 ;;
esac

case "$file_path" in
  *.ts|*.tsx|*.js|*.jsx|*.css|*.json)
    if [ -f "pulse-ui/node_modules/.bin/prettier" ]; then
      pulse-ui/node_modules/.bin/prettier --write "$file_path" >/dev/null 2>&1
    elif command -v prettier >/dev/null 2>&1; then
      prettier --write "$file_path" >/dev/null 2>&1
    fi
    ;;
  *.py)
    if command -v ruff >/dev/null 2>&1; then
      ruff format "$file_path" >/dev/null 2>&1
    elif command -v black >/dev/null 2>&1; then
      black --quiet "$file_path" >/dev/null 2>&1
    fi
    ;;
  *.java)
    # Checkstyle runs at build time; google-java-format is best-effort.
    if command -v google-java-format >/dev/null 2>&1; then
      google-java-format --replace "$file_path" >/dev/null 2>&1
    fi
    ;;
  *.md|*.mdc)
    if command -v prettier >/dev/null 2>&1; then
      prettier --write --prose-wrap preserve "$file_path" >/dev/null 2>&1
    fi
    ;;
esac

exit 0