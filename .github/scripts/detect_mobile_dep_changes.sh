#!/usr/bin/env bash
# Detect dependency manifest changes in a PR vs merge-base.
# Writes GITHUB_OUTPUT: android_deps_changed, ios_deps_changed, rn_deps_changed, any_deps_changed
set -euo pipefail

BASE_REF="${1:-}"
HEAD_REF="${2:-HEAD}"

if [[ -z "${BASE_REF}" ]]; then
  if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
    BASE_REF="origin/${GITHUB_BASE_REF}"
  else
    BASE_REF="origin/main"
  fi
fi

git fetch origin "${BASE_REF#origin/}" --depth=1 2>/dev/null || true
MERGE_BASE="$(git merge-base "${HEAD_REF}" "${BASE_REF}" 2>/dev/null || echo "${BASE_REF}")"

mapfile -t CHANGED < <(git diff --name-only "${MERGE_BASE}...${HEAD_REF}" 2>/dev/null || true)

android_deps_changed=false
ios_deps_changed=false
rn_deps_changed=false

for f in "${CHANGED[@]}"; do
  case "${f}" in
    pulse-android-otel/gradle/* | pulse-android-otel/settings.gradle.kts | pulse-android-otel/demo-app/gradle/*)
      android_deps_changed=true
      ;;
  esac
  if [[ "${f}" == pulse-android-otel/* ]] && [[ "${f}" == *build.gradle.kts ]]; then
    android_deps_changed=true
  fi
done

for f in "${CHANGED[@]}"; do
  case "${f}" in
    pulse-ios-otel/Package.swift | pulse-ios-otel/Package.resolved | pulse-ios-otel/PulseKit.podspec)
      ios_deps_changed=true
      ;;
  esac
  if [[ "${f}" =~ ^pulse-ios-otel/Examples/.+/Podfile(\.lock)?$ ]]; then
    ios_deps_changed=true
  fi
done

for f in "${CHANGED[@]}"; do
  case "${f}" in
    pulse-react-native-otel/package.json | pulse-react-native-otel/yarn.lock | pulse-react-native-otel/android/build.gradle | pulse-react-native-otel/PulseReactNativeOtel.podspec)
      rn_deps_changed=true
      ;;
    pulse-react-native-otel/example/android/app/build.gradle | pulse-react-native-otel/example/android/*/build.gradle)
      rn_deps_changed=true
      ;;
  esac
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
