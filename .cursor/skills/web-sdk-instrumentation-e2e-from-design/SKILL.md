---
name: web-sdk-instrumentation-e2e-from-design
description: Reads pulse-web-otel instrumentation DESIGN.md (and plan folder) to produce a comprehensive E2E test matrix—positive, negative, edge, gate-off, consent—and grill/revalidate coverage gaps; also audits existing Playwright specs and upgrades assertions to the same bar. Use when adding or reviewing E2E for a Web SDK instrumentation, or when the user asks for exhaustive e2e cases from design docs.
disable-model-invocation: true
---

# Web SDK instrumentation — E2E matrix from DESIGN

Use for **`pulse-web-otel/`** instrumentations registered via `InstrumentationRegistry` (OTLP logs/traces/metrics, `ecommerce-demo` Playwright).

## Relationship

| Artifact | Role |
|----------|------|
| [web-sdk-instrumentation-lifecycle](../web-sdk-instrumentation-lifecycle/SKILL.md) | **Phase 6** mandates this skill before implementing instrumentation E2E; Phase 7 revalidation closes checklist gaps. |
| [pulse-web-sdk-sanity](../../../pulse-web-otel/.cursor/skills/pulse-web-sdk-sanity/SKILL.md) (repo path: `.cursor/skills/pulse-web-sdk-sanity`) | Test ladder, D2/D2b assertion floor, `test-run-log.md`, Step 5 audit |
| Plan folder `pulse-web-otel/web-sdk-plan/<milestone>-<slug>/` | `DESIGN.md`, `PLAN-B-*.md`, `ADR-*.md`, `04-contract-parity.md` |

## Inputs (read in order)

1. **`DESIGN.md`** in the instrumentation’s plan folder — entrypoint and links.
2. **`PLAN-B-*.md`** (or equivalent) — canonical signal shape, flush rules, gate names, **minimum unit/E2E matrix** if present.
3. **`ADR-*.md`** — decisions that imply tests (e.g. logs vs metrics, deferred behaviors).
4. **`04-contract-parity.md`** — web vs Android; divergence → explicit test or documented skip.
5. **`src/semconv.ts`** (`PulseWebSemconv`) — exact `pulse.type`, bodies, attribute keys (no invention).
6. **`src/remote-config.ts`** — `PulseFeature` string for gate-off seeds (must match exactly).
7. **`examples/ecommerce-demo/e2e/fixture.ts`** — `findAllLogs` / `waitForLog` / helpers; **JSON OTLP** requires `.env.test` `VITE_PULSE_FORMAT=json`.
8. **Existing specs** — `examples/ecommerce-demo/e2e/*.spec.ts` that mention the instrumentation or `pulse.type`.

## Output A — comprehensive case list (before writing code)

Produce a **numbered checklist** grouped by category. Each item is one **atomic** Playwright scenario (or clearly marked “unit-only / Vitest”).

### Categories (force coverage)

| Category | Include |
|----------|---------|
| **Positive** | Each distinct `pulse.type` / body / metric name; each rating or enum variant; minimal contract per sanity skill (exact `pulse.type`, finite numeric where applicable, truthy `session.id` + `screen.name` on **every** positive-path log assertion unless ADR says otherwise). |
| **Gate-off (D2b)** | Seeded `minimalPulseSdkConfig` with feature `sessionSampleRate: 0` for the **`PulseFeature` name**; `blockActiveConfigFetch`; `page.goto` **after** seed; `waitForLog("session.start")`; **`otlp.reset()`**; interaction; assert **zero** matching exports (skill requires reset). |
| **Consent** | `DENIED` / `PENDING` if product requires — reuse patterns from `e2e/m1.spec.ts`. |
| **Flush / timing** | PLAN-B events (`visibilitychange`, `pagehide`, batch delay); Playwright pitfalls from `pulse-web-sdk-sanity` Phase 8 (getter `visibilityState`, INP spin-loop, Chromium-only skips). |
| **Lifecycle** | No duplicate signals after double `installAll` idempotency if relevant; uninstall/shutdown no leak (often Vitest). |
| **Edge** | BFCache `persisted=true`, empty capture, protobuf vs JSON misconfig (document in spec comment), optional attrs omitted vs empty string. |
| **Negative** | Wrong `pulse.type` absent; gate off; filtered/dropped bodies if sampling/filter applies. |

### Grill pass (mandatory)

Answer **yes/no** with **where the test lives** or **explicit deferral in ADR**:

1. Could `getAttr` pass with **string** where we need **number**? → use `typeof === "number"` + `Number.isFinite`.
2. Gate-off polluted by earlier captures? → `otlp.reset()` after proof-of-life log.
3. New spec file listed in **`examples/ecommerce-demo/package.json`** → `e2e:web-sdk-gates` script (lifecycle **[reference.md D3](../../web-sdk-instrumentation-lifecycle/reference.md)**).
4. Demo UI actually reaches the code path? (D0a — no vacuous pass.)

### Revalidate (second pass)

- Collapse duplicates; mark **P0** if missing D2b or positive-path **session.id** / **screen.name** where logs are asserted.
- Cross-check **`PulseFeature` ↔ `featureName`** in seeded config (e.g. `click` not `clicks`).

## Output B — review existing E2E

For each relevant `e2e/*.spec.ts`:

1. Map tests → cases from Output A — mark **covered / partial / missing**.
2. **Upgrade** partial tests: add missing attrs, numeric finiteness, reset pattern, `getAttr` keys from semconv.
3. Note **chromium-only** skips where `PerformanceEventTiming` / platform APIs require it.

## Commands (after matrix agreed)

```bash
cd pulse-web-otel && yarn test:run
cd pulse-web-otel && yarn workspace ecommerce-demo e2e:web-sdk-gates
```

Append **`pulse-web-otel/web-sdk-plan/agent-runtime/test-run-log.md`** after green gates.

## Anti-patterns

- Asserting only `toBeDefined()` for numeric contract attrs on positive paths.
- Gate-off without **`otlp.reset()`** after `session.start`.
- Adding `e2e/foo.spec.ts` without appending to **`e2e:web-sdk-gates`** in **`package.json`**.

## Optional deep dive

For a printable matrix template (copy-paste table), see [reference.md](reference.md).
