#!/usr/bin/env bash
# Pulse iOS SDK - HTTP Upload Helper
# Builds multipart/form-data metadata JSON for dSYM uploads
pulseUploadBuildMetadataJson() {
  local t="$1"
  local av="$2"
  local vc="$3"
  local fn="$4"
  printf '[{"type":"%s","appVersion":"%s","versionCode":"%s","platform":"ios","fileName":"%s"}]' \
    "$(pulseUploadJsonEscape "$t")" \
    "$(pulseUploadJsonEscape "$av")" \
    "$(pulseUploadJsonEscape "$vc")" \
    "$(pulseUploadJsonEscape "$fn")"
}

pulseUploadPerformUpload() {
  local api_url="$1"
  local api_key="$2"
  local prepared_file_path="$3"
  local app_version="$4"
  local version_code_int="$5"
  local final_file_type="$6"
  local upload_display_name="$7"
  local debug_flag="$8"

  local normalized
  normalized=$(pulseUploadNormalizeUrl "$api_url")

  if [[ ! "$normalized" =~ ^https?://[^/]+ ]]; then
    pulseUploadExitWithError "Invalid API URL: ${api_url}"
  fi

  local metadata_json
  metadata_json=$(pulseUploadBuildMetadataJson "$final_file_type" "$app_version" "$version_code_int" "$upload_display_name")

  local meta_file resp_file http_code
  meta_file=$(mktemp "${TMPDIR:-/tmp}/pulse_upload_meta.XXXXXX") || pulseUploadExitWithError "Failed to create temporary file"
  printf '%s' "$metadata_json" >"$meta_file"

  resp_file=$(mktemp "${TMPDIR:-/tmp}/pulse_upload_resp.XXXXXX") || {
    rm -f "$meta_file"
    pulseUploadExitWithError "Failed to create temporary file"
  }

  # -F adds multipart parts;(curl sets multipart + boundary).
  http_code=$(curl -sS -o "$resp_file" -w '%{http_code}' \
    --max-time 300 \
    -X POST "$normalized" \
    -H "X-API-KEY: ${api_key}" \
    -F "metadata=@${meta_file};type=application/json" \
    -F "fileContent=@${prepared_file_path};type=application/octet-stream;filename=${upload_display_name}") || {
    local ec=$?
    rm -f "$meta_file" "$resp_file"
    printf 'Upload failed: curl error (exit %s)\n' "$ec"
    exit 1
  }

  rm -f "$meta_file"

  local body_text
  body_text=$(cat "$resp_file")
  rm -f "$resp_file"

  if [[ "$http_code" =~ ^2[0-9][0-9]$ ]]; then
    if [[ "$debug_flag" == "1" ]]; then
      printf '\nBackend Response:\n'
      printf '   Status: %s\n' "$http_code"
      if [[ -n "$body_text" ]]; then
        printf '   Response: %s\n' "$body_text"
      fi
    fi
    return 0
  fi

  local err_detail
  err_detail="Upload failed with HTTP ${http_code}: ${body_text:-No error message}"
  printf '   HTTP Status: %s\n' "$http_code"
  printf '   Response: %s\n' "${body_text:-No error message}"
  printf 'Upload failed: %s\n' "$err_detail"
  exit 1
}
