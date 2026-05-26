#!/usr/bin/env bash
# Detect dependency manifest changes in a PR vs merge-base.
# Writes GITHUB_OUTPUT: android_deps_changed, ios_deps_changed, rn_deps_changed, any_deps_changed
#
# Optional: DETECT_CHANGED_FILES_FILE=/path — newline-separated paths (for tests / offline use)
set -euo pipefail

BASE_REF="${1:-}"
HEAD_REF="${2:-HEAD}"

android_deps_changed=false
ios_deps_changed=false
rn_deps_changed=false

mark_android_dep() { android_deps_changed=true; }
mark_ios_dep() { ios_deps_changed=true; }
mark_rn_dep() { rn_deps_changed=true; }

classify_changed_file() {
  local f="$1"
  [[ -z "${f}" ]] && return 0

  case "${f}" in
    pulse-android-otel/gradle/* | pulse-android-otel/settings.gradle.kts | pulse-android-otel/demo-app/gradle/*)
      mark_android_dep
      ;;
    pulse-android-otel/gradle.properties | pulse-android-otel/demo-app/gradle.properties)
      mark_android_dep
      ;;
    pulse-ios-otel/Package.swift | pulse-ios-otel/Package.resolved | pulse-ios-otel/PulseKit.podspec)
      mark_ios_dep
      ;;
    pulse-react-native-otel/package.json | pulse-react-native-otel/yarn.lock | pulse-react-native-otel/android/build.gradle | pulse-react-native-otel/PulseReactNativeOtel.podspec)
      mark_rn_dep
      ;;
    pulse-react-native-otel/example/android/app/build.gradle | pulse-react-native-otel/example/android/*/build.gradle)
      mark_rn_dep
      ;;
  esac

  if [[ "${f}" == pulse-android-otel/* ]] && [[ "${f}" == *build.gradle.kts ]]; then
    mark_android_dep
  fi
  if [[ "${f}" == pulse-android-otel/* ]] && [[ "${f}" == *gradle.properties ]]; then
    mark_android_dep
  fi
  if [[ "${f}" == pulse-android-otel/* ]] && [[ "${f}" == */libs.versions.toml ]]; then
    mark_android_dep
  fi
  if [[ "${f}" =~ ^pulse-ios-otel/Examples/.+/Podfile(\.lock)?$ ]]; then
    mark_ios_dep
  fi
}

read_changed_files() {
  local src="$1"
  CHANGED=()
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ -z "${line//[[:space:]]/}" ]] && continue
    CHANGED+=("${line}")
  done <"${src}"
}

if [[ -n "${DETECT_CHANGED_FILES_FILE:-}" ]]; then
  read_changed_files "${DETECT_CHANGED_FILES_FILE}"
else
  if [[ -z "${BASE_REF}" ]]; then
    if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
      BASE_REF="origin/${GITHUB_BASE_REF}"
    else
      BASE_REF="origin/main"
    fi
  fi
  git fetch origin "${BASE_REF#origin/}" --depth=1 2>/dev/null || true
  MERGE_BASE="$(git merge-base "${HEAD_REF}" "${BASE_REF}" 2>/dev/null || echo "${BASE_REF}")"
  TMP_CHANGED="$(mktemp)"
  git diff --name-only "${MERGE_BASE}...${HEAD_REF}" 2>/dev/null >"${TMP_CHANGED}" || true
  read_changed_files "${TMP_CHANGED}"
  rm -f "${TMP_CHANGED}"
fi

for f in "${CHANGED[@]}"; do
  classify_changed_file "${f}"
done

any_deps_changed=false
if [[ "${android_deps_changed}" == true ]] || [[ "${ios_deps_changed}" == true ]] || [[ "${rn_deps_changed}" == true ]]; then
  any_deps_changed=true
fi

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "android_deps_changed=${android_deps_changed}"
    echo "ios_deps_changed=${ios_deps_changed}"
    echo "rn_deps_changed=${rn_deps_changed}"
    echo "any_deps_changed=${any_deps_changed}"
  } >>"${GITHUB_OUTPUT}"
fi

echo "android_deps_changed=${android_deps_changed}"
echo "ios_deps_changed=${ios_deps_changed}"
echo "rn_deps_changed=${rn_deps_changed}"
echo "any_deps_changed=${any_deps_changed}"
