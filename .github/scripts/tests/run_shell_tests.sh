#!/usr/bin/env bash
# Shell tests S1–S8 for mobile SDK size scripts.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURES="$(cd "$(dirname "${BASH_SOURCE[0]}")/fixtures" && pwd)"
REPO_ROOT="$(cd "${ROOT}/../.." && pwd)"
cd "${REPO_ROOT}"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

pass() {
  echo "PASS: $*"
}

detect_out() {
  local fixture="$1"
  DETECT_CHANGED_FILES_FILE="${fixture}" GITHUB_OUTPUT="" bash "${ROOT}/detect_mobile_dep_changes.sh"
}

# S1: android gradle manifest
OUT="$(detect_out "${FIXTURES}/changed-android-gradle.txt")"
echo "${OUT}" | grep -q 'android_deps_changed=true' || fail "S1 android"
echo "${OUT}" | grep -q 'any_deps_changed=true' || fail "S1 any"
pass "S1 detect android gradle"

# S2: RN yarn.lock
OUT="$(detect_out "${FIXTURES}/changed-rn-yarn.txt")"
echo "${OUT}" | grep -q 'rn_deps_changed=true' || fail "S2 rn"
pass "S2 detect RN yarn.lock"

# S3: kotlin-only — no deps
OUT="$(detect_out "${FIXTURES}/changed-kotlin-only.txt")"
echo "${OUT}" | grep -q 'any_deps_changed=false' || fail "S3 any false"
pass "S3 detect kotlin-only"

# S4: iOS Podfile.lock
OUT="$(detect_out "${FIXTURES}/changed-ios-podfile.txt")"
echo "${OUT}" | grep -q 'ios_deps_changed=true' || fail "S4 ios"
pass "S4 detect iOS Podfile"

# S5/S6: resolve_platforms
export paths_android=true paths_ios=false paths_rn=true
export android_deps_changed=false ios_deps_changed=false rn_deps_changed=true
OUT="$(bash "${ROOT}/resolve_platforms.sh")"
echo "${OUT}" | grep -q 'run_android=true' || fail "S5 run_android"
echo "${OUT}" | grep -q 'run_ios=true' || fail "S5 run_ios"
echo "${OUT}" | grep -q 'run_rn=true' || fail "S5 run_rn"
pass "S5 resolve RN deps"

export paths_android=true paths_ios=false paths_rn=false
export android_deps_changed=true ios_deps_changed=false rn_deps_changed=false
OUT="$(bash "${ROOT}/resolve_platforms.sh")"
echo "${OUT}" | grep -q 'run_android=true' || fail "S6 run_android only"
echo "${OUT}" | grep -q 'run_ios=false' || fail "S6 no ios"
pass "S6 resolve android only"

# S7: measure file
TMP_APK="${FIXTURES}/fake.apk"
printf 'x%.0s' {1..1234} >"${TMP_APK}"
BYTES="$("${ROOT}/measure_sdk_artifact.sh" file "${TMP_APK}")"
[[ "${BYTES}" == "1234" ]] || fail "S7 expected 1234 got ${BYTES}"
pass "S7 measure file"

# S7b: ios_bundle (mock xcframework tree)
TMP_IOS="$(mktemp -d)"
trap 'rm -rf "${TMP_IOS}"' EXIT
mkdir -p "${TMP_IOS}/PulseKit.xcframework/ios-arm64"
printf 'y%.0s' {1..500} >"${TMP_IOS}/PulseKit.xcframework/ios-arm64/lib.a"
IOS_BYTES="$("${ROOT}/measure_sdk_artifact.sh" ios_bundle "${TMP_IOS}")"
[[ "${IOS_BYTES}" == "500" ]] || fail "S7b ios_bundle expected 500 got ${IOS_BYTES}"
pass "S7b measure ios_bundle"

# S8: emit auto pass
OUT="$(python3 "${ROOT}/emit_auto_pass_report.py" --head-sha deadbeef)"
echo "${OUT}" | grep -q "passed (skipped" || fail "S8 missing passed text"
pass "S8 emit_auto_pass_report"

echo "All shell tests passed."
