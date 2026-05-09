#!/usr/bin/env bash
# Quick inventory for updating DEMO-QA-MAP.md after demo or SDK routing changes.
# Run from repo root: bash .cursor/skills/ecommerce-demo-manual-qa/scripts/refresh-demo-context.sh

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
DEMO="$ROOT/pulse-web-otel/examples/ecommerce-demo"

echo "=== $(date -u +%Y-%m-%dT%H:%M:%SZ) demo QA context refresh ==="
echo "--- Routes (App.tsx <Route) ---"
grep -n '<Route' "$DEMO/src/App.tsx" || true

echo "--- Pulse / pulse config knobs (grep App.tsx) ---"
grep -nE 'pulse_|VITE_PULSE|Pulse\.|window\.Pulse|consent' "$DEMO/src/App.tsx" | head -80 || true

echo "--- SDK installInstrumentations (sdk.ts) ---"
grep -nA45 'installInstrumentations' "$ROOT/pulse-web-otel/src/sdk.ts" | head -55 || true

echo "--- InstrumentationRegistry installAll ---"
grep -nA40 'installAll' "$ROOT/pulse-web-otel/src/instrumentation-registry.ts" | head -45 || true

echo "Done. Merge highlights into pulse-web-otel/examples/ecommerce-demo/DEMO-QA-MAP.md"
