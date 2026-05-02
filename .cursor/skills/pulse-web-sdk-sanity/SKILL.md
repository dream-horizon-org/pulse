---
name: pulse-web-sdk-sanity
description: Single canonical workflow for pulse-web-otel—scope, contract, safe implementation, test ladder, regression checklist, pre-merge diff audit (P0/P1/P2), test-run-log, and doc sync.
disable-model-invocation: true
---

# Pulse Web SDK Sanity Workflow

Use this workflow for **any non-trivial change** in `pulse-web-otel/`. It combines the former **test + regression** path and the **pre-merge diff audit** (contract, lifecycle, gates, E2E pitfalls) into **one** procedure.

## Relationship to other tools

| Artifact | Role |
|----------|------|
| [web-sdk-guardian](../../agents/web-sdk-guardian.md) | Cursor **agent** — loads this skill + rules for substantive web SDK tasks. |
| [web-sdk-instrumentation-lifecycle](../web-sdk-instrumentation-lifecycle/SKILL.md) | Instrumentation **projects** — research, ADR/PLAN, gap matrix; **close-out** still runs **this** skill (Steps 3–6). |

**Order before merge:** Steps **1 → 2** (while coding) → **3** tests (must be green) → **4** regression bullets → **5** diff audit (P0/P1/P2 or explicit clean) → **6** docs/graph → [pr-review](../pr-review/SKILL.md). Do not skip **Step 3** and claim Step 5 alone is enough.

## Rules (read first)

- [`.cursor/rules/pulse-web-otel.mdc`](../../rules/pulse-web-otel.mdc)
- [`.cursor/rules/web-sdk.mdc`](../../rules/web-sdk.mdc)
- [`.cursor/rules/pulse-web-otel-structure.mdc`](../../rules/pulse-web-otel-structure.mdc)

## Goal

Keep the web SDK production-safe while moving fast—**one** checklist from first touch to merge-ready.

---

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

---

## Step 2: Implement safely

1. Prefer adapter-first refactors for lifecycle changes.
2. Keep single-owner lifecycle (install/init/shutdown).
3. Avoid parallel code paths that initialize the same feature twice.
4. **Contract hygiene:** use `PulseWebSemconv` for keys and `pulse.type` values; `PulseFeature` + `InstrumentationKeys` for gates and registry keys; `PulseAttributes` for optional attribute bags on public APIs. No single-letter aliases (`K`/`T`/`B`) for semconv tables — see `.cursor/rules/pulse-web-otel.mdc`.

---

## Step 3: Test ladder

1. Run focused unit tests for changed modules.
2. Run wiring/lifecycle tests (`sdk` + instrumentation registry paths).
3. **Required E2E gate:** `yarn workspace ecommerce-demo e2e:web-sdk-gates` (Chromium: `e2e/m1.spec.ts` + `e2e/m2-interactions.spec.ts` — M2 edge cases live in `m2-interactions.spec.ts`).
4. Run any extra targeted E2E for behavior not covered by the gate.
5. If cross-browser binaries are missing, report Chromium result plus explicit gap.
6. Append run result to `pulse-web-otel/web-sdk-plan/agent-runtime/test-run-log.md`.

If Step 5 (audit) or PR review exposes doubt, **re-run** `yarn test:run` and/or `e2e:web-sdk-gates` before merge.

---

## Step 4: Regression checklist

- No dropped custom events unexpectedly.
- No interaction regressions in timeout/sequence/APDEX paths.
- No listener/timer leaks after shutdown.
- No contract drift in exported attributes.

---

## Step 5: Pre-merge diff review (required for substantive changes)

After **Step 3 is green**, walk this list on the **diff** (or touched modules if the change is tiny). Record each issue as **P0** (block merge) / **P1** / **P2** with `path:line` and a fix hint—or one line **“Step 5: no findings”** if clean.

### 5.1 Data contract

- [ ] Grep diff/changed files for ad-hoc `"pulse.type"` / log bodies / attribute keys **outside** `PulseWebSemconv` (and interactions semconv where applicable).
- [ ] Public surface: `src/index.ts`, package exports — accidental breaking changes?
- [ ] `PulseFeature` ↔ `InstrumentationKeys` ↔ `instrumentation-registry.ts` — every gated feature mapped; no orphan keys.

### 5.2 Lifecycle & single ownership

- [ ] New DOM / `window` / `document` listeners, `PerformanceObserver`, timers → **removed** on `uninstall` / `shutdown` path.
- [ ] `InstrumentationRegistry`: no double subscription if `installAll` can run twice (idempotency pattern where used).
- [ ] Async init (`start` / `finishStart`): no signals after shutdown or before `_initialized` where forbidden.

### 5.3 Consent & feature gates

- [ ] DENIED / PENDING consent: zero OTLP and no listener leaks (align with existing E2E patterns).
- [ ] Gate off: instrumentation not installed; no stale global providers left half-configured.

### 5.4 Exporters, demo, E2E

- [ ] Playwright fixture decodes **JSON** OTLP — confirm `examples/ecommerce-demo/.env.test` keeps `VITE_PULSE_FORMAT=json` (and compression story) if E2E reads `captured` bodies.
- [ ] New behavior covered by Vitest; user-visible flows covered by E2E or explicitly deferred with PLAN/ADR note.
- [ ] `e2e:web-sdk-gates` includes new spec file if added (`package.json` script).

### 5.5 Android parity (one pass)

- [ ] For each new/changed **product** signal, note Android equivalent behavior: **match**, **documented divergence**, or **TODO**.

### Optional deep dives (if those areas changed)

- **Interactions:** focused greps in `src/interactions/` for sequence/timeout/APDEX assumptions.
- **Graph context:** `pulse-web-otel/graphify-out/GRAPH_REPORT.md` for blast radius.

**Trivial doc-only** edits under `web-sdk-plan/` with no code: Step 5 can be **N/A**—state that in the close-out report.

---

## Step 6: Documentation sync

1. Update plan/docs/csv if test matrix or behavior changed.
2. Keep descriptions explicit and parse-safe for CSV cells.
3. Refresh graph cache summary after meaningful code changes.

---

## Output format

When finishing work, report:

1. Files changed  
2. Tests run (exact commands)  
3. Pass/fail summary  
4. **Step 5 summary:** P0/P1/P2 table or “no findings” / N/A  
5. Known gaps and next action  

For PRs or large diffs, you may paste the structured block:

```markdown
## Web SDK sanity — <branch / PR>

### Findings (Step 5)
| Sev | Location | Issue | Suggested fix |
|-----|----------|-------|----------------|
| P0 | ... | ... | ... |

### Checklist
- Contract: pass / gaps
- Lifecycle: pass / gaps
- Gates/consent: pass / gaps
- Demo/E2E: pass / gaps
- Parity: ...

### Commands run
- ...
```
