#!/usr/bin/env bash
set -euo pipefail

# Usage: ./ralph/once.sh [iterations]
# Default iterations=1. Stops early if Claude prints a completion promise (see ralph/prompt.md).
#
# Live output: Claude's stdout/stderr are streamed with tee (TTY) or only to a log file (non-TTY).
# Logs: ralph/.logs/iter-<n>-<timestamp>.log

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ITERATIONS="${1:-1}"
if ! [[ "$ITERATIONS" =~ ^[0-9]+$ ]] || [[ "$ITERATIONS" -lt 1 ]]; then
  echo "Usage: $0 [iterations]   (positive integer, default 1)" >&2
  exit 1
fi

RALPH_LOG_DIR="$ROOT/ralph/.logs"
mkdir -p "$RALPH_LOG_DIR"

# One line so the shell does not inject stray backslashes/newlines into the prompt.
CLAUDE_PROMPT='/Users/sarthakagarwal/Desktop/Dream11/pulse/.scratch/rca-segment-signal-gate/PRD.md @Users/sarthakagarwal/Desktop/Dream11/pulse/.scratch/rca-segment-signal-gate/issues/ issues/progress.txt ralph/prompt.md Follow the workflow and rules in ralph/prompt.md. The attached prd/, issues/, and issues/progress.txt are your source inputs.'

for ((i = 1; i <= ITERATIONS; i++)); do
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "Ralph iteration $i / $ITERATIONS"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  LOG_FILE="$RALPH_LOG_DIR/iter-${i}-$(date +%Y%m%d-%H%M%S).log"
  echo "Log file: $LOG_FILE" >&2

  # -p/--print: non-interactive — print final response and exit (default is interactive TUI).
  # tee: same stream to terminal (if stdout is a TTY) and to LOG_FILE for promise parsing.
  if [[ -t 1 ]]; then
    claude --print --permission-mode bypassPermissions "$CLAUDE_PROMPT" --allowedTools "Read,Edit,Bash" 2>&1 |
      tee "$LOG_FILE" /dev/tty
  else
    echo "(stdout is not a TTY — writing only to log; tail -f \"$LOG_FILE\" in another window)" >&2
    claude --print --permission-mode bypassPermissions "$CLAUDE_PROMPT" --allowedTools "Read,Edit,Bash" 2>&1 |
      tee "$LOG_FILE"
  fi

  result="$(<"$LOG_FILE")"

  if [[ "$result" == *"<promise>COMPLETE</promise>"* ]] ||
    [[ "$result" == *"<promise>NO MORE TASKS</promise>"* ]]; then
    echo "Stopping: completion promise found in model output."
    exit 0
  fi
done

echo "Finished $ITERATIONS iteration(s) without a completion promise."
