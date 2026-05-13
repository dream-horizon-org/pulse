#!/usr/bin/env bash
# Run all ADK eval sets. Invoke from repo root or any cwd.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PYTHONPATH="${REPO_ROOT}:${PYTHONPATH:-}"
if [[ -f "${REPO_ROOT}/pulse_ai/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${REPO_ROOT}/pulse_ai/.env"
  set +a
fi
cd "${REPO_ROOT}"

echo "=== EM agent evals ==="
adk eval pulse_ai/adk_eval_app pulse_ai/eval/em.evalset.json \
  --config_file_path pulse_ai/eval/eval_config_em.json "$@"

echo ""
echo "=== RCA agent evals ==="
adk eval pulse_ai/adk_eval_rca_app pulse_ai/eval/rca.evalset.json \
  --config_file_path pulse_ai/eval/eval_config_rca.json "$@"
