#!/bin/bash
# Writes all hook events to an audit log for traceability.
# Log location: .cursor/hooks/state/audit.log

json_input=$(cat)
timestamp=$(date '+%Y-%m-%d %H:%M:%S')

LOG_DIR=".cursor/hooks/state"
LOG_FILE="$LOG_DIR/audit.log"
MAX_SIZE=5242880  # 5MB

mkdir -p "$LOG_DIR"

# Rotate if log exceeds max size
if [ -f "$LOG_FILE" ] && [ "$(stat -f%z "$LOG_FILE" 2>/dev/null || stat -c%s "$LOG_FILE" 2>/dev/null)" -gt "$MAX_SIZE" ]; then
  mv "$LOG_FILE" "$LOG_FILE.old"
fi

echo "[$timestamp] $json_input" >> "$LOG_FILE"

exit 0
