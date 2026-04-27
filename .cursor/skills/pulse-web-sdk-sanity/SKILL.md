# Pulse Web SDK Sanity Workflow

Use this workflow for any non-trivial change in `pulse-web-otel/`.

## Goal
Keep the web SDK production-safe while moving fast.

## Step 1: Scope and contract
1. Identify touched areas: core SDK, instrumentations, exporters, interactions, demo, tests.
2. List contract-sensitive items:
   - `pulse.type`
   - semantic attribute keys
   - public API methods/signatures
   - consent and feature-gate behavior
3. Check Android parity for equivalent paths and record suspected mismatches early.
4. Read graph context:
   - source: `pulse-web-otel/graphify-out/GRAPH_REPORT.md` and `pulse-web-otel/graphify-out/graph.json` (if present)
   - cache: `pulse-web-otel/web-sdk-plan/agent-runtime/graph-cache.md`

## Step 2: Implement safely
1. Prefer adapter-first refactors for lifecycle changes.
2. Keep single-owner lifecycle (install/init/shutdown).
3. Avoid parallel code paths that initialize the same feature twice.

## Step 3: Test ladder
1. Run focused unit tests for changed modules.
2. Run wiring/lifecycle tests (`sdk` + instrumentation registry paths).
3. Run targeted E2E for affected behavior.
4. If cross-browser binaries are missing, report Chromium result plus explicit gap.
5. Append run result to `pulse-web-otel/web-sdk-plan/agent-runtime/test-run-log.md`.

## Step 4: Regression checklist
- No dropped custom events unexpectedly.
- No interaction regressions in timeout/sequence/APDEX paths.
- No listener/timer leaks after shutdown.
- No contract drift in exported attributes.

## Step 5: Documentation sync
1. Update plan/docs/csv if test matrix or behavior changed.
2. Keep descriptions explicit and parse-safe for CSV cells.
3. Refresh graph cache summary after meaningful code changes.

## Output format
When finishing work, report:
1. Files changed
2. Tests run (exact commands)
3. Pass/fail summary
4. Known gaps and next action
