#!/usr/bin/env bash
# Bump files for create-release-react-native-pr workflow.
# Env (required): RN_VERSION
# Env (optional): ANDROID_VERSION, IOS_VERSION
# Env (optional): BUMP_ANDROID_SDK, BUMP_IOS_SDK — "true" to also bump monorepo SDK roots.
# When ANDROID_VERSION is set: android/build.gradle, example app build.gradle, and
# plugin/src/androidBuildConstants.ts (PULSE_DREAMHORIZON_OKHTTP_INSTR_VERSION) are updated.
set -euo pipefail

# GNU sed (Linux/CI): sed -i <expr> file. BSD sed (macOS): sed -i '' <expr> file.
sed_inplace() {
  if sed --version >/dev/null 2>&1; then
    sed -i "$@"
  else
    sed -i '' "$@"
  fi
}

REPO_ROOT="${GITHUB_WORKSPACE:-$(git rev-parse --show-toplevel 2>/dev/null || true)}"
if [[ ! -d "$REPO_ROOT/pulse-react-native-otel" ]]; then
  echo "Expected monorepo layout: pulse-react-native-otel/ under repo root."
  exit 1
fi

if [[ -z "${RN_VERSION:-}" ]]; then
  echo "RN_VERSION is required."
  exit 1
fi

is_true() {
  case "${1-}" in true|True|TRUE) return 0 ;; *) return 1 ;; esac
}

if is_true "${BUMP_ANDROID_SDK:-false}" && [[ -z "${ANDROID_VERSION:-}" ]]; then
  echo "bump_android_sdk is true but android_version is empty."
  exit 1
fi

if is_true "${BUMP_IOS_SDK:-false}" && [[ -z "${IOS_VERSION:-}" ]]; then
  echo "bump_ios_sdk is true but ios_version is empty."
  exit 1
fi

RN_ROOT="$REPO_ROOT/pulse-react-native-otel"

RN_ROOT="$RN_ROOT" RN_VERSION="$RN_VERSION" node <<'NODE'
const fs = require('fs');
const path = require('path');
const root = process.env.RN_ROOT;
const p = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
p.version = process.env.RN_VERSION;
fs.writeFileSync(path.join(root, 'package.json'), JSON.stringify(p, null, 2) + '\n');
NODE

if [[ -n "${ANDROID_VERSION:-}" ]]; then
  sed_inplace "s/^def pulse_version = \".*\"/def pulse_version = \"${ANDROID_VERSION}\"/" \
    "$RN_ROOT/android/build.gradle"
  sed_inplace "s|implementation(\"org.dreamhorizon.instrumentation:okhttp3-library:[^\"]*\")|implementation(\"org.dreamhorizon.instrumentation:okhttp3-library:${ANDROID_VERSION}\")|" \
    "$RN_ROOT/example/android/app/build.gradle"
  sed_inplace "s|byteBuddy(\"org.dreamhorizon.instrumentation:okhttp3-agent:[^\"]*\")|byteBuddy(\"org.dreamhorizon.instrumentation:okhttp3-agent:${ANDROID_VERSION}\")|" \
    "$RN_ROOT/example/android/app/build.gradle"
  sed_inplace "s|^export const PULSE_DREAMHORIZON_OKHTTP_INSTR_VERSION = '[^']*';|export const PULSE_DREAMHORIZON_OKHTTP_INSTR_VERSION = '${ANDROID_VERSION}';|" \
    "$RN_ROOT/plugin/src/androidBuildConstants.ts"
fi

if [[ -n "${IOS_VERSION:-}" ]]; then
  sed_inplace "s|s\\.dependency 'PulseKit', '[^']*'|s.dependency 'PulseKit', '${IOS_VERSION}'|" \
    "$RN_ROOT/PulseReactNativeOtel.podspec"
fi

if is_true "${BUMP_ANDROID_SDK:-false}"; then
  ANDROID_SDK_BASE="${ANDROID_VERSION}"
  if [[ "$ANDROID_VERSION" == *-alpha ]]; then
    ANDROID_SDK_BASE="${ANDROID_VERSION%-alpha}"
  fi
  sed_inplace "s/^version=.*/version=${ANDROID_SDK_BASE}/" \
    "$REPO_ROOT/pulse-android-otel/gradle.properties"
fi

if is_true "${BUMP_IOS_SDK:-false}"; then
  sed_inplace "s/^  spec\\.version = .*/  spec.version = \"${IOS_VERSION}\"/" \
    "$REPO_ROOT/pulse-ios-otel/PulseKit.podspec"
fi

echo "Bump complete."
