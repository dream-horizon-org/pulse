#!/usr/bin/env bash

readonly _PULSE_UPLOAD_YELLOW=$'\033[33m'
readonly _PULSE_UPLOAD_RESET=$'\033[0m'

pulseUploadWarn() {
  printf '%bwarning: %s%b\n' "$_PULSE_UPLOAD_YELLOW" "$1" "$_PULSE_UPLOAD_RESET" >&2
}

pulseUploadFormatFileSize() {
  local bytes="$1"
  local kb mb
  kb=$(awk -v b="$bytes" 'BEGIN { printf "%.2f", b/1024.0 }')
  mb=$(awk -v b="$bytes" 'BEGIN { printf "%.2f", b/1024.0/1024.0 }')
  awk -v mb="$mb" -v kb="$kb" -v b="$bytes" '
    BEGIN {
      if (mb >= 1.0) printf "%.2f MB", mb+0
      else if (kb >= 1.0) printf "%.2f KB", kb+0
      else printf "%d bytes", b+0
    }'
}

pulseUploadNormalizeUrl() {
  printf '%s' "${1//localhost/127.0.0.1}"
}

pulseUploadToLower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

pulseUploadDetectFileType() {
  local path="$1"
  local base ext lower ext_lower
  base=$(basename "$path")
  ext="${base##*.}"
  lower=$(pulseUploadToLower "$base")
  ext_lower=$(pulseUploadToLower "$ext")
  if [[ "$lower" == *.dsym ]] || [[ "$ext_lower" == "dsym" ]]; then
    printf 'dsym'
  else
    printf 'unknown'
  fi
}

# Escapes characters so the string is safe inside JSON "..." 
pulseUploadJsonEscape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  s="${s//$'\n'/\\n}"
  s="${s//$'\r'/\\r}"
  printf '%s' "$s"
}

pulseUploadValidateFileType() {
  local file_type
  file_type=$(pulseUploadToLower "$1")
  local file_path="$2"
  if [[ "$file_type" == "dsym" ]]; then
    printf 'dsym'
    return 0
  fi
  if [[ "$file_type" == "unknown" ]]; then
    local detected
    detected=$(pulseUploadDetectFileType "$file_path")
    if [[ "$detected" == "dsym" ]]; then
      printf 'dsym'
      return 0
    fi
    pulseUploadWarn "File type detected as 'unknown' for: $(basename "$file_path")"
    pulseUploadWarn "   Expected: dSYM file (.dSYM extension or directory)"
    pulseUploadWarn "   Upload will proceed but may be rejected by backend."
    pulseUploadWarn "   Fix: Use a dSYM file or set --type=dsym if this is a dSYM file."
    printf 'unknown'
    return 0
  fi
  pulseUploadFailWithUsage "Only 'dsym' type is currently supported. Got: $1."
}

pulseUploadResolvePath() {
  local path="$1"
  if [[ "$path" == /* ]]; then
    printf '%s' "$path"
    return 0
  fi
  local dir base
  dir=$(dirname "$path")
  base=$(basename "$path")
  if [[ "$dir" == "." ]]; then
    printf '%s/%s' "$PWD" "$base"
  else
    (cd "$dir" && printf '%s/%s' "$(pwd)" "$base")
  fi
}

# Sets globals for the file sent to the API:
#   PULSE_UPLOAD_PREPARED_PATH, PULSE_UPLOAD_PREPARED_NAME,
#   PULSE_UPLOAD_PREPARED_SIZE, PULSE_UPLOAD_PREPARED_TEMP (1 = temp zip to delete)
pulseUploadPrepareFileForUpload() {
  local file_path="$1"
  local _ft="$2"

  if [[ ! -e "$file_path" ]]; then
    pulseUploadExitWithError "File or directory not found at: $file_path"
  fi

  if [[ -d "$file_path" ]]; then
    local parent base zip_path
    parent=$(cd "$(dirname "$file_path")" && pwd)
    base=$(basename "$file_path")
    zip_path="${TMPDIR:-/tmp}/${base}.zip"
    rm -f "$zip_path"
    (cd "$parent" && /usr/bin/zip -r -q "$zip_path" "$base") || {
      pulseUploadExitWithError "Failed to create zip archive: zip command exited with status $?"
    }
    if [[ ! -f "$zip_path" ]]; then
      pulseUploadExitWithError "Zip archive was not created"
    fi
    local sz
    sz=$(stat -f%z "$zip_path" 2>/dev/null || stat -c%s "$zip_path" 2>/dev/null)
    if [[ -z "$sz" || "$sz" -eq 0 ]]; then
      rm -f "$zip_path"
      pulseUploadExitWithError "Zip archive is empty: $zip_path"
    fi
    PULSE_UPLOAD_PREPARED_PATH="$zip_path"
    PULSE_UPLOAD_PREPARED_NAME="${base}.zip"
    PULSE_UPLOAD_PREPARED_SIZE="$sz"
    PULSE_UPLOAD_PREPARED_TEMP=1
    return 0
  fi

  if [[ ! -f "$file_path" ]]; then
    pulseUploadExitWithError "File or directory not found at: $file_path"
  fi
  local sz
  sz=$(stat -f%z "$file_path" 2>/dev/null || stat -c%s "$file_path" 2>/dev/null)
  if [[ -z "$sz" || "$sz" -eq 0 ]]; then
    pulseUploadExitWithError "File is empty: $file_path"
  fi
  PULSE_UPLOAD_PREPARED_PATH="$file_path"
  PULSE_UPLOAD_PREPARED_NAME="$(basename "$file_path")"
  PULSE_UPLOAD_PREPARED_SIZE="$sz"
  PULSE_UPLOAD_PREPARED_TEMP=0
}

pulseUploadPrintUsage() {
  cat <<'USAGE_EOF'
Pulse dSYM upload

Usage:
  bash pulse-upload-dsym.sh \\
    -u <url> | --api-url=<url> | --url=<url> \\
    -k <key> | --api-key=<key> \\
    -p <path> | --dsym-path=<path> | --dsym=<path> \\
    -v <version> | --app-version=<version> \\
    -c <code> | --version-code=<code> \\
    [-t dsym | --type=dsym] \\
    [-d | --debug]

Environment variables (optional; CLI flags override when you pass a flag):
  Only these are read from the environment (credentials / default dSYM path):
  PULSE_UPLOAD_API_URL       Same as --api-url
  PULSE_UPLOAD_API_KEY       Same as --api-key
  PULSE_UPLOAD_DSYM_PATH     Same as --dsym-path

  App version, version code, file type, and debug must be set via flags
  (--app-version, --version-code, --type, --debug), not via env.

Required arguments:
  API URL, API key, dSYM path — via env above and/or matching flags.
  App version and version code — flags only (-v / -c).

Optional:
  -t/--type, -d/--debug, -h/--help

Note: Relative dSYM paths resolve against the current working directory.
USAGE_EOF
}

pulseUploadFailWithUsage() {
  local message="$1"
  local code="${2:-$PULSE_UPLOAD_EXIT_ERROR}"
  printf 'Error: %s\n\n' "$message" >&2
  pulseUploadPrintUsage >&2
  exit "$code"
}
