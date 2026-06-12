#!/bin/bash
# Runs the appropriate formatter after agent edits a file.
# Detects file type from the path and runs the matching tool.

json_input=$(cat)
file_path=$(echo "$json_input" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/"file_path"[[:space:]]*:[[:space:]]*"//;s/"$//')

if [ -z "$file_path" ]; then
  exit 0
fi

case "$file_path" in
  *.ts|*.tsx|*.js|*.jsx|*.css|*.json)
    if command -v npx &>/dev/null; then
      if [ -f "pulse-ui/node_modules/.bin/prettier" ]; then
        npx --prefix pulse-ui prettier --write "$file_path" 2>/dev/null
      elif command -v prettier &>/dev/null; then
        prettier --write "$file_path" 2>/dev/null
      fi
    fi
    ;;
  *.py)
    if command -v ruff &>/dev/null; then
      ruff format "$file_path" 2>/dev/null
    elif command -v black &>/dev/null; then
      black --quiet "$file_path" 2>/dev/null
    fi
    ;;
  *.java)
    # Java formatting is handled by Checkstyle at build time.
    # google-java-format can be run if available.
    if command -v google-java-format &>/dev/null; then
      google-java-format --replace "$file_path" 2>/dev/null
    fi
    ;;
  *.md|*.mdc)
    if command -v prettier &>/dev/null; then
      prettier --write --prose-wrap preserve "$file_path" 2>/dev/null
    fi
    ;;
esac

exit 0
