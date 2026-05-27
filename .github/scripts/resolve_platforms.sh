#!/usr/bin/env bash
# Resolve which platform size jobs to run from path-filter + dep-change flags.
# Env: paths_android, paths_ios, paths_rn (true/false)
#      android_deps_changed, ios_deps_changed, rn_deps_changed (true/false)
# Writes GITHUB_OUTPUT: run_android, run_ios, run_rn
set -euo pipefail

paths_android="${paths_android:-false}"
paths_ios="${paths_ios:-false}"
paths_rn="${paths_rn:-false}"
android_deps="${android_deps_changed:-false}"
ios_deps="${ios_deps_changed:-false}"
rn_deps="${rn_deps_changed:-false}"

run_android=false
run_ios=false
run_rn=false

if [[ "${paths_android}" == true ]] && [[ "${android_deps}" == true ]]; then
  run_android=true
fi
if [[ "${paths_ios}" == true ]] && [[ "${ios_deps}" == true ]]; then
  run_ios=true
fi

# RN: native Android + iOS when RN manifests change; also rn example APK job
if [[ "${paths_rn}" == true ]] && [[ "${rn_deps}" == true ]]; then
  run_rn=true
  run_android=true
  run_ios=true
fi

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "run_android=${run_android}"
    echo "run_ios=${run_ios}"
    echo "run_rn=${run_rn}"
  } >>"${GITHUB_OUTPUT}"
fi

echo "run_android=${run_android}"
echo "run_ios=${run_ios}"
echo "run_rn=${run_rn}"
