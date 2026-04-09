#!/usr/bin/env bash
# Pulse iOS SDK - dSYM Upload Orchestrator
# Ensures strict error handling for reliable uploads
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load internal helper modules
# Note: SC1090 allows linting tools to ignore dynamic paths
source "${SCRIPT_DIR}/pulse-upload-errors.sh"
source "${SCRIPT_DIR}/pulse-upload-utils.sh"
source "${SCRIPT_DIR}/pulse-upload-task.sh"

PULSE_UPLOAD_PREPARED_PATH=""
PULSE_UPLOAD_PREPARED_NAME=""
PULSE_UPLOAD_PREPARED_SIZE=0
PULSE_UPLOAD_PREPARED_TEMP=0

pulseUploadCleanupTemporaryZip() {
  if [[ "${PULSE_UPLOAD_PREPARED_TEMP:-0}" == "1" && -n "${PULSE_UPLOAD_PREPARED_PATH:-}" ]]; then
    rm -f "${PULSE_UPLOAD_PREPARED_PATH}"
  fi
}

parseCommandLineArguments() {
  PULSE_PARSED_API_URL=""
  PULSE_PARSED_API_KEY=""
  PULSE_PARSED_DSYM_PATH=""
  PULSE_PARSED_APP_VERSION=""
  PULSE_PARSED_VERSION_CODE=""
  PULSE_PARSED_FILE_TYPE="unknown"
  PULSE_PARSED_DEBUG=0

  while [[ $# -gt 0 ]]; do
    local arg="$1"
    shift

    if [[ "$arg" == "-h" || "$arg" == "--help" ]]; then
      pulseUploadPrintUsage
      exit 0
    fi

    if [[ "$arg" == *"="* ]]; then
      local key val
      key="${arg%%=*}"
      val="${arg#*=}"
      val="${val#"${val%%[![:space:]]*}"}"
      val="${val%"${val##*[![:space:]]}"}"
      case "$key" in
        --api-url|-u) PULSE_PARSED_API_URL="$val" ;;
        --url) PULSE_PARSED_API_URL="$val" ;;
        --api-key|-k) PULSE_PARSED_API_KEY="$val" ;;
        --dsym-path|-p) PULSE_PARSED_DSYM_PATH="$val" ;;
        --dsym) PULSE_PARSED_DSYM_PATH="$val" ;;
        --app-version|-v) PULSE_PARSED_APP_VERSION="$val" ;;
        --version-code|-c) PULSE_PARSED_VERSION_CODE="$val" ;;
        --type|-t) PULSE_PARSED_FILE_TYPE=$(pulseUploadToLower "$val") ;;
        --debug|-d) PULSE_PARSED_DEBUG=1 ;;
        *)
          pulseUploadFailWithUsage "Unknown argument: $key"
          ;;
      esac
      continue
    fi

    case "$arg" in
      --api-url|-u)
        PULSE_PARSED_API_URL="${1:-}"
        shift || true
        ;;
      --url)
        PULSE_PARSED_API_URL="${1:-}"
        shift || true
        ;;
      --api-key|-k)
        PULSE_PARSED_API_KEY="${1:-}"
        shift || true
        ;;
      --dsym-path|-p)
        PULSE_PARSED_DSYM_PATH="${1:-}"
        shift || true
        ;;
      --dsym)
        PULSE_PARSED_DSYM_PATH="${1:-}"
        shift || true
        ;;
      --app-version|-v)
        PULSE_PARSED_APP_VERSION="${1:-}"
        shift || true
        ;;
      --version-code|-c)
        PULSE_PARSED_VERSION_CODE="${1:-}"
        shift || true
        ;;
      --type|-t)
        PULSE_PARSED_FILE_TYPE=$(pulseUploadToLower "${1:-unknown}")
        shift || true
        ;;
      --debug|-d)
        PULSE_PARSED_DEBUG=1
        ;;
      *)
        local trimmed="${arg#"${arg%%[![:space:]]*}"}"
        trimmed="${trimmed%"${trimmed##*[![:space:]]}"}"
        if [[ -z "$trimmed" ]]; then
          continue
        fi
        if [[ "$trimmed" == --* ]]; then
          pulseUploadFailWithUsage "Unknown argument: $trimmed"
        fi
        continue
        ;;
    esac
  done
}

# -----------------------------------------------------------------------------
# pulseUploadApplyEnvironmentDefaults
# Fills API URL, API key, and dSYM path from env when CLI did not set them.
# Precedence: explicit CLI wins. App version / version code / type / debug: flags only.
# -----------------------------------------------------------------------------
pulseUploadApplyEnvironmentDefaults() {
  if [[ -z "${PULSE_PARSED_API_URL// }" && -n "${PULSE_UPLOAD_API_URL:-}" ]]; then
    PULSE_PARSED_API_URL="$PULSE_UPLOAD_API_URL"
  fi
  if [[ -z "${PULSE_PARSED_API_KEY// }" && -n "${PULSE_UPLOAD_API_KEY:-}" ]]; then
    PULSE_PARSED_API_KEY="$PULSE_UPLOAD_API_KEY"
  fi
  if [[ -z "${PULSE_PARSED_DSYM_PATH// }" && -n "${PULSE_UPLOAD_DSYM_PATH:-}" ]]; then
    PULSE_PARSED_DSYM_PATH="$PULSE_UPLOAD_DSYM_PATH"
  fi
}

# -----------------------------------------------------------------------------
# runPulseUploadDsym — main entry point.
# -----------------------------------------------------------------------------
runPulseUploadDsym() {
  parseCommandLineArguments "$@"
  pulseUploadApplyEnvironmentDefaults

  if [[ -z "${PULSE_PARSED_API_URL// }" ]]; then
    pulseUploadFailWithUsage "--api-url is required (flag -u/--api-url or env PULSE_UPLOAD_API_URL)"
  fi
  if [[ -z "${PULSE_PARSED_API_KEY// }" ]]; then
    pulseUploadFailWithUsage "--api-key is required (flag -k/--api-key or env PULSE_UPLOAD_API_KEY)"
  fi

  local api_url api_key
  api_url="${PULSE_PARSED_API_URL#"${PULSE_PARSED_API_URL%%[![:space:]]*}"}"
  api_url="${api_url%"${api_url##*[![:space:]]}"}"
  api_key="${PULSE_PARSED_API_KEY#"${PULSE_PARSED_API_KEY%%[![:space:]]*}"}"
  api_key="${api_key%"${api_key##*[![:space:]]}"}"

  local scheme_check
  scheme_check=$(pulseUploadNormalizeUrl "$api_url")
  if [[ ! "$scheme_check" =~ ^https?://[^/]+ ]]; then
    pulseUploadFailWithUsage "API URL must be a valid HTTP or HTTPS URL"
  fi

  if [[ -z "${PULSE_PARSED_DSYM_PATH// }" ]]; then
    pulseUploadFailWithUsage "--dsym-path is required (flag -p/--dsym or env PULSE_UPLOAD_DSYM_PATH)"
  fi
  if [[ -z "${PULSE_PARSED_APP_VERSION// }" ]]; then
    pulseUploadFailWithUsage "--app-version is required (flag -v / --app-version only; not read from env)"
  fi

  # Single check: must be non-empty AND match positive integer (1, 2, … not 0).
  if [[ -z "${PULSE_PARSED_VERSION_CODE}" ]] || ! [[ "$PULSE_PARSED_VERSION_CODE" =~ ^[1-9][0-9]*$ ]]; then
    pulseUploadFailWithUsage "--version-code is required and must be a positive integer (flag -c / --version-code only; not read from env)"
  fi

  # trimmed_dsym: remove accidental leading/trailing spaces from user/env path so
  local trimmed_dsym file_path_resolved
  trimmed_dsym="${PULSE_PARSED_DSYM_PATH#"${PULSE_PARSED_DSYM_PATH%%[![:space:]]*}"}"
  trimmed_dsym="${trimmed_dsym%"${trimmed_dsym##*[![:space:]]}"}"
  file_path_resolved=$(pulseUploadResolvePath "$trimmed_dsym")

  local final_type
  final_type=$(pulseUploadValidateFileType "$PULSE_PARSED_FILE_TYPE" "$file_path_resolved")

  pulseUploadPrepareFileForUpload "$file_path_resolved" "$final_type"
  trap pulseUploadCleanupTemporaryZip EXIT

  local sz_human
  sz_human=$(pulseUploadFormatFileSize "$PULSE_UPLOAD_PREPARED_SIZE")

  printf '\nUploading to Pulse backend...\n'
  printf '   File: %s (%s)' "$PULSE_UPLOAD_PREPARED_NAME" "$sz_human"
  printf '\n'
  printf '   Version: %s (code: %s)\n' "$PULSE_PARSED_APP_VERSION" "$PULSE_PARSED_VERSION_CODE"

  if [[ "$PULSE_PARSED_DEBUG" == "1" ]]; then
    printf '\nDebug Info:\n'
    printf '   API URL: %s\n' "$api_url"
    printf '   File Path: %s\n' "$file_path_resolved"
    printf '   Platform: ios, Type: %s\n' "$final_type"
  fi

  pulseUploadPerformUpload "$api_url" "$api_key" "$PULSE_UPLOAD_PREPARED_PATH" \
    "$PULSE_PARSED_APP_VERSION" "$PULSE_PARSED_VERSION_CODE" "$final_type" \
    "$PULSE_UPLOAD_PREPARED_NAME" "$PULSE_PARSED_DEBUG"

  pulseUploadCleanupTemporaryZip
  trap - EXIT

  printf 'Upload successful\n'
}

runPulseUploadDsym "$@"
