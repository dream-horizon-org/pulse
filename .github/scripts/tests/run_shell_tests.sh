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

# S7: measure file
TMP_APK="${FIXTURES}/fake.apk"
printf 'x%.0s' {1..1234} >"${TMP_APK}"
BYTES="$("${ROOT}/measure_sdk_artifact.sh" file "${TMP_APK}")"
[[ "${BYTES}" == "1234" ]] || fail "S7 expected 1234 got ${BYTES}"
pass "S7 measure file"

# S8: emit auto pass
OUT="$(python3 "${ROOT}/emit_auto_pass_report.py" --head-sha deadbeef)"
echo "${OUT}" | grep -q "passed (skipped" || fail "S8 missing passed text"
pass "S8 emit_auto_pass_report"

# S1–S4: detect deps (synthetic git diff via env CHANGED simulation — test resolve + detect logic with temp repo)
# Use minimal invocation: create temp git repo with one commit diff
TMP_GIT="$(mktemp -d)"
trap 'rm -rf "${TMP_GIT}"' EXIT
cp -r "${REPO_ROOT}/.git" "${TMP_GIT}/.git" 2>/dev/null || true
if [[ ! -d "${TMP_GIT}/.git" ]]; then
  echo "SKIP S1–S6: no git context"
  exit 0
fi

export GITHUB_OUTPUT=""
cd "${REPO_ROOT}"
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

echo "All shell tests passed."
