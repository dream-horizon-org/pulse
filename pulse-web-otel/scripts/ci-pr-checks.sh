#!/usr/bin/env bash
# Pulse Web SDK PR gate — lint, unit tests, ecommerce e2e:web-sdk-gates, nextjs-demo e2e.
# Run from pulse-web-otel/:  yarn ci:pr-checks
# CI: .github/workflows/web-sdk-checks.yml (paths: src/**, examples/**)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export CI="${CI:-true}"

step() {
  echo ""
  echo "======================================================================"
  echo "==> $*"
  echo "======================================================================"
}

step "yarn lint"
yarn lint

step "yarn test:run"
yarn test:run

step "yarn e2e:web-sdk-gates"
yarn e2e:web-sdk-gates

step "nextjs-demo e2e (chromium)"
yarn workspace nextjs-demo e2e --project=chromium

echo ""
echo "All Web SDK PR checks passed."
