---
name: web-sdk-instrument
description: New or resumed instrumentation in pulse-web-otel—research, touchpoints matrix, ADR/PLAN-B, implementation, testing. Uses web-sdk-e2e-matrix for DESIGN→E2E matrix; web-sdk-ship for ship-ready gates; optional pulse-web-sdk staged flow (instrumentation + stage 0–8).
---

# Web SDK instrumentation (`web-sdk-instrument`)

Use this for **any new or materially changed instrumentation** in `pulse-web-otel/` (signals under `InstrumentationRegistry`, `PulseFeature`, OTLP logs/traces/metrics, ecommerce-demo E2E)—including **resuming half-finished work** on another branch.

**Simplified invoke:** Cursor agent [pulse-web-sdk](../../agents/pulse-web-sdk.md) — user passes `instrumentation`, `stage` (0–8), optional `branch` / `scope`; agent **confirms** then loads this skill and executes the mapped phases.

**Gap matrix (copy-paste audit):** [reference.md](reference.md) — row-level DONE / PARTIAL / MISSING for docs, code, tests, close-out.

---

## Web SDK rules (load before coding)

Treat repo **Cursor rules** as binding for `pulse-web-otel/` work:

| Rule file | Use for |
|-----------|---------|
| [`.cursor/rules/pulse-web-otel.mdc`](../../rules/pulse-web-otel.mdc) | Semconv, layout + folder placement, `PulseFeature` / `InstrumentationKeys`, public API, consent, Vitest/E2E patterns. |
| [`.cursor/rules/web-sdk.mdc`](../../rules/web-sdk.mdc) | Product contracts, SPEC map, rule stack; use with [pulse-web-sdk](../../agents/pulse-web-sdk.md). |

Re-read the relevant sections when touching a new layer (instrumentation vs exporter vs E2E).

---

## Related skills — ship checklist & quality (use together)

For **any** non-trivial `pulse-web-otel/` change (including finishing half-done instrumentations), **read and follow** [web-sdk-ship](../web-sdk-ship/SKILL.md): contract checklist, test ladder (`yarn test:run`, `e2e:web-sdk-gates`), `test-run-log.md`, regression checklist, **Step 5 pre-merge diff audit**, doc sync, graph hints (`pulse-web-otel/graphify-out/`, `pulse-web-otel/graphify-out/GRAPH_REPORT.md`).

| Skill | When |
|-------|------|
| [web-sdk-ship](../web-sdk-ship/SKILL.md) | **Always** for implementation + test close-out on web SDK (Steps 3–6 include merge-ready diff audit). |
| [web-sdk-e2e-matrix](../web-sdk-e2e-matrix/SKILL.md) | **Before** writing or tightening Playwright specs: read the plan folder `DESIGN.md` (plus PLAN-B, ADR, `PulseWebSemconv`) and produce the **full E2E case checklist**—positive paths, D2b gate-off, consent, flush/timing, lifecycle/edges—then diff against existing `e2e/*.spec.ts` and raise assertions to the same bar. Fold the checklist into PLAN-B’s E2E section (or track gaps explicitly). |
| [grill-me](../grill-me/SKILL.md) | Plan stress-test before locking ADR / after resume. |
| [pr-review](../pr-review/SKILL.md) | Before merge. |
| [deploy-service](../deploy-service/SKILL.md) | Only if you need full Docker stack to reproduce ingest (optional; most instrumentation is unit + Playwright). |

This lifecycle skill owns **phasing and gaps**; **web-sdk-ship** owns **execution + verification + pre-merge audit**—run both for instrumentation work (**web-sdk-ship** is one procedure end-to-end). **web-sdk-e2e-matrix** turns **design artifacts → exhaustive E2E matrix** so Phase 6 does not improvise scenarios.

---

## Principles

1. **Touchpoints before commitment** — enumerate files and layers *before* picking the final design (KPI: no surprise files mid-implementation).
2. **Research before code** — short written artifacts you can link from ADR/DESIGN.
3. **Rejected alternative (when there was a real fork)** — If you seriously considered another mechanism (e.g. metrics vs logs, immediate emit vs buffer), document it and *why* it loses in **`PLAN-A-<mechanism>.md`** and link from ADR. **If there was no credible alternative** (obvious single approach or tiny extension), skip Plan A and record one line in the ADR: why no separate alternative doc was needed—do **not** invent a strawman Plan A.
4. **Grill before lock-in** — run the grill skill on the chosen plan after touchpoints exist, ADR before bulk implementation.
5. **Single-owner lifecycle** — install once per registry lifetime unless `uninstallAll` reset; no double listeners.
6. **Contract hygiene** — `PulseWebSemconv`, `PulseFeature`, `InstrumentationKeys`; enforce via **Web SDK rules** above + [web-sdk-ship](../web-sdk-ship/SKILL.md) checklist.
7. **Implementation sign-off** — **Never** start **Phase 5** (production code under `pulse-web-otel/src/**`, registry, gated backend Java, demo **behavior** wiring, new E2E) until the user **explicitly approves** in the current thread (see Phase 5 gate below). Prior “yes” to research, matrix, or ADR alone is **not** enough.
8. **Self-heal from review** — When human review, [pr-review](../pr-review/SKILL.md), or CI flags a **valid, repeatable** gap (missed gate, wrong E2E pattern, semconv leak), **judge** whether it belongs in repo-wide rules vs this workstream. If it should recur for future instrumentations, **promote** it: append an **atomic** bullet to [reference.md](reference.md) section **F — Durable learnings**, and/or extend the relevant checklist row (A–E). **Do not** paste whole threads—one finding → one line + optional PR link. Skip one-off style nits.

---

## Entry — Gap assessment & resume (always start here)

**When:** Greenfield feature **or** branch with partial implementation (e.g. click instrumentation mid-flight).

**Goal:** Produce a **gap list**—what exists vs what the lifecycle still requires—then execute only the missing phases.

### Steps

1. **Read web SDK rules** — [pulse-web-otel.mdc](../../rules/pulse-web-otel.mdc), [web-sdk.mdc](../../rules/web-sdk.mdc); load [web-sdk-ship](../web-sdk-ship/SKILL.md) for the ship checklist you will run at the end. For delegated work, the [pulse-web-sdk](../../agents/pulse-web-sdk.md) agent should load the same rules + skill map.
2. **Name the feature** — e.g. `CLICK`, `WEB_VITALS`; locate `InstrumentationKeys` / `PulseFeature` / **`docs/instrumentations/<feature>/SPEC.md`** (canonical) plus any branch-local research notes you create under `docs/` or ADRs.
3. **Inventory the branch** — `git diff main --stat` (or `main...HEAD`) for `pulse-web-otel/`, relevant `backend/server/` paths, demo E2E. Read changed files; note TODOs and commented placeholders.
4. **Fill the gap matrix** — Use [reference.md](reference.md) (sections **A–E**, including **E5** handoff when applicable). Mark each row **DONE**, **PARTIAL** (with next action), or **MISSING**.
5. **Reconcile code vs docs** — If code exists but A1–A9 are missing or stale, **create or update docs before adding more code** (prevents orphan implementation). If docs describe behavior the branch does not implement, either implement or amend ADR/PLAN with explicit deferrals.
6. **Order work** — Typical: missing research → matrix → ADR/PLAN sync → **[web-sdk-e2e-matrix](../web-sdk-e2e-matrix/SKILL.md)** checklist (once DESIGN + PLAN-B exist) → implementation gaps → unit tests → implement E2E from checklist → `test-run-log.md` → revalidation → [pr-review](../pr-review/SKILL.md).
7. **Re-grill on resume** — At least a short pass: shutdown, double-install, consent, sampling, contract attrs (use [grill-me](../grill-me/SKILL.md) if decisions were implicit).

### Outputs

- A short **gap summary** in chat or `HANDOFF-*.md`: bullet list of MISSING/PARTIAL rows and the phase that closes each.
- Updated matrix rows as items land (keep docs truthful).

---

## Mandatory gates (non-negotiable for “done”)

These cannot be skipped; if skipped, state **explicit deferral** in ADR or PLAN-B with owner follow-up.

| Gate | Requirement |
|------|-------------|
| **Research & documentation** | A1–A2 exist and are **current** relative to the branch (update if wiring or industry choice changed). Touchpoints matrix A3 matches touched files. |
| **Design record** | A5–A9 consistent: ADR + active PLAN + DESIGN + parity README; **`PLAN-A-*.md` only if** a rejected alternative is documented there—otherwise ADR states why no Plan A (see Principle 3). |
| **Testing** | D1 unit coverage; D2 + **D2b** per [reference.md](reference.md) (assertion floor + gate-off zero-export pattern where applicable); `yarn test:run` + `e2e:web-sdk-gates` green unless documented env skip. |
| **Pre-implementation grill** | [grill-me](../grill-me/SKILL.md) completed **before Phase 5** (after touchpoints + ADR/PLAN exist) **or** ADR/PLAN-B line: `Grill deferred: <named risks + owner>`. Silent skip is not allowed. |
| **Execution log** | D5 `test-run-log.md` updated for gate runs; non-obvious failures include **symptom → cause → fix** (see Phase 8). |
| **Review** | [pr-review](../pr-review/SKILL.md) (or equivalent) before merge. After merge-worthy feedback, apply **Principle 8** (self-heal) so the next run loads the fix as context. |
| **Rules + ship checklist** | Edits comply with [pulse-web-otel.mdc](../../rules/pulse-web-otel.mdc); [web-sdk-ship](../web-sdk-ship/SKILL.md) **Steps 2–6** executed and cited in the done report (Step 5 diff audit required for substantive code unless explicit N/A). |
| **Handoff doc** | If work pauses on a branch: plan-folder `HANDOFF-NEXT-AGENT.md` updated (or N/A with reason). |
| **Implementation start** | **Phase 5 user approval:** no first implementation edit until the user explicitly green-lights implementation after your short recap (Phase 5 gate). |

**Debugging** is not a separate skip—use Phase 8 when tests fail; log findings in `test-run-log.md`.

---

## Phase 0 — Research & orientation

**Goal:** Answer “what do vendors / OTel do?” and “what does this SDK already expose?”

| Output | Purpose |
|--------|---------|
| `01-research-<topic>-ecosystem-and-industry.md` | External patterns, spec constraints, industry defaults (events vs metrics vs spans). |
| `02-research-<topic>-otel-js-browser-and-pulse-sdk.md` | Where the signal plugs in: `SdkContext`, exporters, processors, existing semconv, Android parity hints. |

During greenfield work, store scratch research under a **branch-local** folder or tickets; **merge outcome** must land in **`docs/instrumentations/<feature>/SPEC.md`** (and linked tests).

**Stop condition (three answerable questions — if any answer is guesswork, do more research before Phase 1):**

1. **Signal type:** log, trace, or metric — **which one**, and **why not** the other two for this product signal?
2. **Flush / export:** When does data leave the SDK batch queue? What **user/browser/SDK event** triggers export (timer, `pagehide`, `visibilitychange`, etc.)?
3. **Gate:** Exact **`PulseFeature`** name (or equivalent) and **which SDK** string it applies to (e.g. `pulse_web_js`)?

---

## Phase 1 — Touchpoints matrix (before ADR)

**Goal:** `03-touchpoints-matrix.md` — every likely file: semconv, `types/config.ts`, `feature-gate` / `remote-config`, `instrumentation-registry.ts`, instrumentation class(es), exporters/processors if touched, backend `Features` + default template if remote-gated, Vitest paths, `ecommerce-demo` E2E.

**KPI:** If implementation discovers a new must-touch file, update the matrix and treat it as a planning gap.

**Hard stop:** If a **new file** appears mid-implementation that was **not** on the matrix, **stop**, add the row to `03-touchpoints-matrix.md`, and assess whether it changes ADR/PLAN-B (signal type, flush, gates, tests) **before** continuing. Do not only log the gap and keep coding past it unless the change is trivial and ADR-exempt (document why).

---

## Phase 2 — Alternatives & rejection (Plan A — **only if**)

**When:** Add **`PLAN-A-<mechanism>.md`** only when there is a **real rejected fork** worth naming (the path you did **not** ship—e.g. immediate per-click emit vs buffer, metrics vs logs). That doc is the **rejected** option; ADR + PLAN-B describe the **chosen** path.

**Skip Phase 2 as a file** when no meaningful alternative existed—capture a short **“Why no Plan A”** sentence in **ADR** instead (see Principle 3).

When you **do** write Plan A, record **quantified rejection** where possible:

- Same information density as the winning design? (e.g. single histogram observation vs scalar)
- **Flush / ordering / threading** risks (e.g. `forceFlush` inside hot callbacks)
- Pipeline reuse vs new sink
- Operator ergonomics in ClickHouse / dashboard

---

## Phase 3 — Grill, ADR, canonical plan

1. **Grill** — Invoke [grill-me](../grill-me/SKILL.md) on: signal shape, sampling, shutdown, bfcache/mobile web edges, double-install, contract parity with Android/RN.
2. **ADR** — `ADR-<topic>.md`: decision table; reference phased research + **`PLAN-A-*.md` when it exists**, otherwise the one-line “no Plan A” rationale.
3. **Canonical spec** — `PLAN-B-<topic>.md` (or equivalent): lifecycle diagram, attribute schema, flush rules, unit matrix, **E2E outline** (expand later via [web-sdk-e2e-matrix](../web-sdk-e2e-matrix/SKILL.md) full checklist), optional SQL checks.

**Revalidate after ADR:** Re-read touchpoints matrix; add rows if grill exposed new integration surfaces.

---

## Phase 4 — Design synthesis

| Doc | Role |
|-----|------|
| `DESIGN.md` | 1–2 page router: links to PLAN-B, ADR, research; for fast onboarding. |
| `04-contract-parity.md` (or section) | Web vs mobile/RN envelope; what's web-only. |

---

## Phase 5 — Implementation (typical order)

### Gate — user approval before any implementation edit (required)

**Stop** before the first change that implements behavior or wiring (anything in the numbered list below: `src/semconv.ts` through backend/demo/E2E as listed).

1. Post a **short recap** (a few bullets): ADR / PLAN-B decision in one line, **first files** you will touch (from touchpoints matrix), any **material risks** left from grill or gap list.
2. **Ask explicitly** for permission to start implementation (e.g. “Reply **approve implementation** (or adjust plan) before I edit code.”).
3. **Do not** write or patch production implementation files until the user replies with clear consent (e.g. “approve implementation”, “go ahead with Phase 5”, “proceed with code”).

**Out of scope for this gate:** Phase 0–4 docs only (`DESIGN.md`, matrix, ADR, PLAN-B, research). **In scope:** all items in the list below, including `semconv.ts` / config / instrumentations / registry / `sdk.ts` / backend feature template / demo app code / Playwright specs that assert product behavior.

If the user asked to “run everything” or “stages 0–8”, you still **pause at this boundary** and ask once before touching implementation files.

---

1. `src/semconv.ts` — `PulseType`, bodies, attribute keys (no magic strings in instrumentations). **Optional attributes (contract):** omit the attribute **key** when the value is `undefined`. Never emit `undefined`, `null`, or `""` for optional keys — ClickHouse / attribute maps treat **missing** keys differently from **empty** strings.
2. `src/types/config.ts` + defaults if new instrumentation config subtree.
3. `remote-config` / `PulseFeature` if backend-gated feature.
4. **`src/instrumentations/<name>.ts`** — all of the following:
   - **SSR / non-browser:** first line of `install()`: `if (typeof window === "undefined") return;` so Next.js/Remix server render never runs browser-only code.
   - **`install` / `uninstall` symmetry:** store **private fields** for every `addEventListener` / `window` / `document` subscription; `uninstall()` removes with the **same function refs**. If the upstream library has **no cancel API** (e.g. web-vitals), document in ADR/PLAN-B: after `shutdown`, **`logger.emit` is a no-op** (no export leak) but **subscriptions may remain** — acceptable if documented.
   - Reuse semconv **optional-attribute** rule from step 1 in emitted log attrs.
5. `instrumentation-registry.ts` — `registerAndInstall(..., InstrumentationKeys.*)`; **single-owner `installAll`:** private `installAllCompleted`; `installAll()` no-op on second call without `uninstallAll()`; `uninstallAll()` resets flag + uninstalls.
6. **OTel API vs SDK (`Logger` vs `LoggerProvider`):** **`Logger`** (`logs.getLogger()`) is the **OTel API** — **`emit` only**, no `flush`. **`LoggerProvider`** is the **OTel SDK** — owns the batch pipeline and exposes **`forceFlush()`**. Lifecycle flushes (e.g. `visibilitychange`, `pageshow`) must call **`sdk.loggerProvider?.forceFlush()`**, not a method on `Logger`. Add **`loggerProvider?: LoggerProvider` to `SdkContext`** + wire from `sdk.ts` when needed.
7. **Backend** (if feature-flagged): `Features.java`, `DefaultSdkConfigTemplate.java`, and **`DefaultSdkConfigTemplateTest.java`** — bump expected feature **count** and update the **expected feature name list** whenever a new `Features` enum row is added (easy to miss; gate tests fail otherwise).

---

## Phase 6 — Testing

**E2E case generation (required for new/changed instrumentation E2E):** Run [web-sdk-e2e-matrix](../web-sdk-e2e-matrix/SKILL.md) against the plan folder—starts from **`DESIGN.md`**, pulls **`PLAN-B`**, **`ADR`**, **`04-contract-parity.md`**, **`PulseWebSemconv`**, **`PulseFeature`** names, and existing specs—outputs **Output A** (full scenario checklist + grill/revalidate) and **Output B** (upgrade partial tests). Implement or extend Playwright from that checklist; keep PLAN-B E2E section aligned with what you ship or defer.

Execute [web-sdk-ship](../web-sdk-ship/SKILL.md): **Step 3** test ladder (focused Vitest → wiring/lifecycle → `e2e:web-sdk-gates` → targeted E2E if needed → append `test-run-log.md`); then **Steps 4–6** (regression, pre-merge diff audit, doc sync).

**Demo readiness (before writing specs):** Before any `e2e/*.spec.ts` is useful, the demo must actually exercise the new signal and the test infrastructure must be wired. See [reference.md](reference.md) **D0a–D0e**:

1. **UI surface** — Does `examples/ecommerce-demo/src/` have a page or element that reaches the new code path? If not, add it first — specs that never reach the code always pass vacuously.
2. **`.env.test`** — Confirm: `VITE_PULSE_FORMAT=json` + `VITE_PULSE_COMPRESSION=none` (fixture JSON-decodes); `VITE_PULSE_BATCH_DELAY_MS=200` (fast flush); `VITE_PULSE_MOCK_SDK_CONFIG=false` + `VITE_PULSE_MOCK_INTERACTION_CONFIG=false` (prevents mock config overwriting seeded config). Add any feature-specific `VITE_` vars the instrumentation reads from `import.meta.env`.
3. **`test-sdk-config.ts`** — Does `minimalPulseSdkConfig` or `demoE2eWhitelistFilterValues` need new feature entries or signal patterns? Update before writing gate-off or consent tests.
4. **`fixture.ts`** — `findAllLogs` / `findAllSpans` / `findAllMetrics` cover standard signal types. If the new signal needs a different extraction helper, add it to `e2e/fixture.ts` first.

**Unit:** Vitest next to changed modules; mock browser globals; use `vi.hoisted` for `vi.mock` factories.

**Integration:** registry + gate + `installAll` idempotency; consent off-path.

**E2E:** `yarn workspace ecommerce-demo e2e:web-sdk-gates` (Chromium); add or extend `e2e/*.spec.ts` for behavior not in M1/M2.

**Gates script (required):** Any **new** Playwright file must be appended to the `e2e:web-sdk-gates` script in `examples/ecommerce-demo/package.json`. If it is **not** listed, `yarn e2e:web-sdk-gates` **never runs** that spec and broken tests ship silently. See [reference.md](reference.md) **D3**.

### Minimum E2E assertion set (positive path — OTLP log signals)

For each **positive-path** log signal test, assert at least:

| Check | Why |
|-------|-----|
| `pulse.type` | Exact string (e.g. `web_vital`) — ClickHouse / dashboard filter key |
| Numeric value | `typeof value === "number"` and finite — not merely `!= null` |
| Enum field when present | e.g. `web_vital.rating` ∈ `good` \| `needs-improvement` \| `poor` (or PLAN-B set for the signal) |
| `session.id` | Truthy on the log record (or same export batch, per spec) |
| `screen.name` | Truthy — proves `GlobalAttributesProcessor` stamped the record |

Add further attrs from PLAN-B / contract parity. See [reference.md](reference.md) **D2** / **D2b**.

**Gate-disabled E2E (assert zero exports after gate off):** `seedPulseSdkConfig` + `blockActiveConfigFetch` **before** `page.goto`; `await otlp.waitForLog("session.start")` to prove SDK is live; **`otlp.reset()`** to drop pre-interaction captures; perform interaction + batch waits; assert **no** matching log records (e.g. `findAllLogs(...).length === 0`). Without `reset`, earlier gated logs pollute the assertion.

**Append-only log:** `CI / PR description (optional local `pulse-web-otel/progress.txt`)` — command, browser, pass/fail, **root cause + fix** if anything non-obvious failed.

---

## Phase 7 — Revalidation

- [ ] [web-sdk-e2e-matrix](../web-sdk-e2e-matrix/SKILL.md) checklist: every **P0** scenario covered in Vitest/E2E or explicitly deferred in ADR/PLAN-B.
- [ ] PLAN-B unit matrix rows addressed or explicitly deferred with rationale.
- [ ] [web-sdk-ship](../web-sdk-ship/SKILL.md) **Steps 4–6** — regression checklist, **Step 5** pre-merge diff audit, documentation sync (no listener leaks, no contract drift).
- [ ] Docs index (`README.md` in plan folder) points to active PLAN variant.
- [ ] **Handoff:** update branch `HANDOFF*.md` / PR draft when pausing — point readers at **`docs/instrumentations/<feature>/SPEC.md`** for truth.
- [ ] `graphify update . --no-viz` **from `pulse-web-otel/` only** after substantive code changes. Full-repo `graphify update .` often **OOMs** building HTML viz — **always** pass `--no-viz` when updating from the web SDK package root.

---

## Phase 8 — Debug / fix playbook (try first)

| Symptom | Likely cause | First fix |
|---------|--------------|-----------|
| E2E `waitForLog` / empty `captured` | Fixture JSON-decodes bodies; demo defaults **protobuf** | `examples/ecommerce-demo/.env.test`: `VITE_PULSE_FORMAT=json`, `VITE_PULSE_COMPRESSION=none` |
| `screen.name` mismatch vs path | `GlobalAttributesProcessor` **`:id` normalization** | Expect `/segment/:id` not stripped parent path |
| Duplicate `onLCP` / double instrumentation | Second `installAll()` without guard | **`InstrumentationRegistry`:** private `installAllCompleted = false`; `installAll()` returns immediately if already `true`; set `true` after successful install path; **`uninstallAll()`** clears flag + uninstalls so a new lifecycle can call `installAll()` again. |
| INP (or similar) `expect(…).toBeDefined()` fails in **headless** Playwright | Real clicks finish under ~5ms; `PerformanceEventTiming` / INP often needs **≥40ms** interaction | One-shot **spin loop ~70ms** inside `page.evaluate` before the click; after interaction, `waitForTimeout(300)` (or batch window) before simulating `visibilitychange` flush if needed. **Also** in `beforeEach` / each test: `test.skip(browserName !== "chromium", "PerformanceEventTiming / INP — Chromium only")` (or `test.info().project.name !== "chromium"`) — without skip, the spec **times out or is meaningless on Firefox/WebKit** when engineers copy the pattern. |
| Simulated `visibilitychange` does not flush | `Object.defineProperty(document, "visibilityState", { value: "hidden" })` does not behave like a real visibility transition | Use **getter** form: `{ get: () => "hidden", configurable: true }`, then `document.dispatchEvent(new Event("visibilitychange"))` separately. |
| OPTIONS / active-config weirdness | Overlapping `page.route` vs fixture stub | Prefer fixture’s `attachDefaultSdkConfigStub`; avoid redundant blocks |
| `forceFlush` runs but logs still not on wire | **`Logger`** has no flush — wrong object | Use **`sdk.loggerProvider?.forceFlush()`** only; **`Logger`** = OTel API (`emit` only); **`LoggerProvider`** = OTel SDK (owns pipeline + `forceFlush`). |

**Narrative examples:** append-only `CI / PR description (optional local `pulse-web-otel/progress.txt`)` — e.g. 2026-04-30 (protobuf / `screen.name`), 2026-05-02 (INP / `PerformanceEventTiming`). **Do not treat this skill as the exhaustive list of dates**; new rows document themselves in that file.

---

## Naming template (new topic)

Replace `<slug>` with kebab-case topic (e.g. `web-vitals`, `resource-timing`):

```
(branch-local planning folder OR docs/instrumentations/<slug>/ — consolidate to SPEC.md before merge)
  01-research-<slug>-ecosystem-and-industry.md
  02-research-<slug>-otel-js-browser-and-pulse-sdk.md
  03-touchpoints-matrix.md
  PLAN-A-<alternative-mechanism>.md        # optional — only when documenting a rejected fork (see Phase 2)
  ADR-<slug>.md
  PLAN-B-<slug>-<signal-family>.md          # canonical implementation spec
  DESIGN.md
  04-contract-parity.md
  README.md                                 # reading order + active plan pointer
  HANDOFF-NEXT-AGENT.md                     # optional: status + prompt for next agent when pausing / handoff
```

---

## Done report (copy into PR / chat)

0a. Gap matrix: all rows **DONE** or **explicitly deferred** in ADR/PLAN with rationale (link or paste summary). E2E: [web-sdk-e2e-matrix](../web-sdk-e2e-matrix/SKILL.md) checklist addressed or deferrals noted.
0b. **Pre-implementation grill:** [grill-me](../grill-me/SKILL.md) done before Phase 5, or one-line `Grill deferred: <risks + owner>` in ADR/PLAN-B.
0c. **Handoff doc:** `HANDOFF-NEXT-AGENT.md` updated if pausing mid-branch (or N/A).
0d. **Implementation approval:** user explicitly approved Phase 5 before first implementation edit (or N/A — docs-only / Phase 4-only work).
0e. **Self-heal:** if post-review lessons apply to future runs, new row in [reference.md](reference.md) **section F** (or N/A).
1. Summary of decision (one line) + link to ADR + PLAN-B.
2. Files changed (grouped: SDK, demo, backend, docs).
3. Tests run (exact commands + result).
4. `test-run-log.md` updated (Y/N).
5. Known limitations / follow-ups (e.g. upstream library has no cancel API).
