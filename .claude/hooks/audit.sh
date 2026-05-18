#!/bin/bash
# Append every hook invocation to .claude/hooks/state/audit.log.
# Rotates at 5MB. Pure observer: never blocks.

set +e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/state"
LOG_FILE="$LOG_DIR/audit.log"
MAX_SIZE=5242880  # 5MB

mkdir -p "$LOG_DIR"

json_input=$(cat)
timestamp=$(date '+%Y-%m-%d %H:%M:%S')

if [ -f "$LOG_FILE" ]; then
  size=$(stat -f%z "$LOG_FILE" 2>/dev/null || stat -c%s "$LOG_FILE" 2>/dev/null || echo 0)
  if [ "$size" -gt "$MAX_SIZE" ]; then
    mv "$LOG_FILE" "$LOG_FILE.old"
  fi
fi

printf '[%s] %s\n' "$timestamp" "$json_input" >> "$LOG_FILE"
exit 0