#!/usr/bin/env bash
# =============================================================================
# pulse-upload-errors.sh
# Non-zero exit tells Xcode / CI the Run Script phase failed
readonly PULSE_UPLOAD_EXIT_ERROR=1

pulseUploadExitWithError() {
  local message="$1"
  local code="${2:-$PULSE_UPLOAD_EXIT_ERROR}"
  printf 'Error: %s\n' "$message" >&2
  exit "$code"
}
