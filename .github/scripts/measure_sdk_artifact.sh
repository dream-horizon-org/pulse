#!/usr/bin/env bash
# Measure artifact size in bytes.
# Usage: measure_sdk_artifact.sh <mode> <path>
#   mode: file | ios_bundle
#   file: path to APK
#   ios_bundle: pulse-ios-otel/build directory — sums du -sb for *.xcframework dirs
set -euo pipefail

MODE="${1:?mode required (file|ios_bundle)}"
TARGET="${2:?path required}"

file_size() {
  local f="$1"
  if [[ ! -f "${f}" ]]; then
    echo "::error::Artifact not found: ${f}" >&2
    exit 1
  fi
  if [[ "$(uname -s)" == Darwin ]]; then
    stat -f%z "${f}"
  else
    stat -c%s "${f}"
  fi
}

ios_bundle_size() {
  local build_dir="$1"
  if [[ ! -d "${build_dir}" ]]; then
    echo "::error::iOS build dir not found: ${build_dir}" >&2
    exit 1
  fi
  local total=0
  local d
  shopt -s nullglob
  for d in "${build_dir}"/*.xcframework; do
    local n
    n="$(du -sb "${d}" | awk '{print $1}')"
    total=$((total + n))
  done
  shopt -u nullglob
  if [[ "${total}" -eq 0 ]]; then
    echo "::error::No .xcframework directories under ${build_dir}" >&2
    exit 1
  fi
  echo "${total}"
}

case "${MODE}" in
  file) file_size "${TARGET}" ;;
  ios_bundle) ios_bundle_size "${TARGET}" ;;
  *)
    echo "::error::Unknown mode ${MODE}" >&2
    exit 1
    ;;
esac
