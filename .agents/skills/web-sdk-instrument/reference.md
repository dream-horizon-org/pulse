# Gap matrix — instrumentation readiness

Copy into chat or a scratch doc; set each row to **DONE** | **PARTIAL** | **MISSING**. **PARTIAL** must include the next concrete action.

## A. Planning & docs (required for merge)

| # | Artifact | Where to look | Notes |
|---|----------|---------------|--------|
| A1 | Research notes | Branch scratch / ADR | Capture industry assumptions; fold into **`docs/instrumentations/<feature>/SPEC.md`** before merge. |
| A2 | Wiring research | same | Update when `SdkContext`, exporters, or wiring changed. |
| A3 | Touchpoints matrix | Branch scratch doc | Must list every file you intend to touch. |
| A4 | Rejected / deferred alternative | **`PLAN-A-*.md` only when** you document a **real rejected fork**; otherwise **ADR section** (“Why no Plan A”) for tiny extensions / single obvious approach. **MISSING** only OK if ADR states “no alternative evaluated” with one-line rationale (see lifecycle Principle 3 / Phase 2). |
| A5 | ADR | `ADR-*.md` | Must match implemented signal type and flush story. |
| A6 | Canonical implementation spec | `PLAN-B-*.md` or named plan | Unit matrix + E2E outline; mark deferred items explicitly. |
| A7 | `DESIGN.md` | branch-local (optional) | Router doc while iterating; final truth is **`SPEC.md`**. |
| A8 | Contract parity | `04-contract-parity.md` or section | Web vs Android/RN; web-only attrs. |
| A9 | Feature `SPEC.md` index | `docs/instrumentations/<feature>/SPEC.md` §8–§9 | Reading order + absorbed artefacts. |

## B. Code — SDK

| # | Area | Grep / path hints |
|---|------|-------------------|
| B1 | `semconv.ts` — `PulseType`, bodies, `AttributeKey` | Instrumentation must not hardcode strings. |
| B2 | `types/config.ts` — `InstrumentationConfig` | Feature flags / `enabled` defaults. |
| B3 | `remote-config.ts` — `PulseFeature` + gate | Feature name + default template row if remote. |
| B4 | `instrumentations/<name>.ts` | `install` / `uninstall` symmetry; **SSR guard** (`typeof window === "undefined"` early return); **stored listener refs** for DOM/window cleanup; **no-cancel upstream library** → ADR/PLAN note (emit no-op after shutdown; subscriptions may remain). Full sub-bullets: **Phase 5 step 4** in lifecycle `SKILL.md`. |
| B5 | `instrumentation-registry.ts` | `registerAndInstall` + key; `installAll` idempotency if applicable. |
| B6 | `sdk.ts` / `SdkContext` | Only if new provider or lifecycle hook needed. |
| B7 | React integration | `src/integrations/react/` if clicks/routes need hooks. |

## C. Code — backend (if feature-gated)

| # | Area | Verify |
|---|------|--------|
| C1 | `Features.java` | New enum value for the feature. |
| C2 | `DefaultSdkConfigTemplate.java` | Default row for `pulse_web_js` includes the feature. |
| C3 | `DefaultSdkConfigTemplateTest.java` | Bump expected feature **count** + update **expected feature name list** (common miss). |

## D. Tests & demo

### D0. Demo readiness (check before writing any E2E spec)

| # | Area | Notes |
|---|------|-------|
| D0a | Demo app UI surface | Does `examples/ecommerce-demo/src/` have a page or element that reaches the new code path? If not, add it before writing specs — specs that never reach the instrumented code always pass vacuously. |
| D0b | `.env.test` vars complete | Required: `VITE_PULSE_FORMAT=json`, `VITE_PULSE_COMPRESSION=none`, `VITE_PULSE_BATCH_DELAY_MS=200`, `VITE_PULSE_MOCK_SDK_CONFIG=false`, `VITE_PULSE_MOCK_INTERACTION_CONFIG=false`. Add any feature-specific `VITE_` vars the new instrumentation reads from `import.meta.env`. |
| D0c | `test-sdk-config.ts` coverage | `minimalPulseSdkConfig` features array: gate-off test needs a matching `featureName` entry with `sessionSampleRate: 0`. `demoE2eWhitelistFilterValues`: consent/whitelist tests need new signal patterns if not already covered. Update before writing gate-off or consent tests. |
| D0d | `fixture.ts` extraction helpers | `findAllLogs` for log signals; `findAllSpans` for traces; `findAllMetrics` for metrics. If the new signal type needs a different extraction helper (e.g. **prefix match** when `pulse.type` varies per status), add it to `e2e/fixture.ts` before writing specs. |
| D0e | Resource Timing / Playwright traffic | E2E that assert **PerformanceResourceTiming-sourced** attrs: `page.route`-fulfilled requests often **lack** real Resource Timing entries—probe first or **defer** in ADR/PLAN-B; do not write vacuous assertions. |

### D1+. Unit, integration, E2E

| # | Area | Notes |
|---|------|-------|
| D1 | Vitest: `src/__tests__/*<topic>*.test.ts` or extended existing | Gate, emit, flush, consent, idempotency per PLAN matrix. |
| D2 | E2E: `examples/ecommerce-demo/e2e/*.spec.ts` | Assert `pulse.type` **exact string**; numeric value `typeof "number"` and finite (not only `!= null`); enum field in known set (e.g. `web_vital.rating`); `session.id` truthy; `screen.name` truthy; add PLAN-B attrs. **Gate-off negative path:** see **D2b**. |
| D2b | E2E: feature gate off → **no** export | `seedPulseSdkConfig` + `blockActiveConfigFetch` **before** `goto`; `await otlp.waitForLog("session.start")`; **`otlp.reset()`** to clear captures; interact + wait batch window; assert **zero** matching logs (e.g. `findAllLogs(otlp.captured, …).length === 0`). |
| D3 | `ecommerce-demo/package.json` | **Every new** `e2e/*.spec.ts` must appear in the `e2e:web-sdk-gates` script — otherwise the gate **never runs** that file. |
| D4 | `.env.test` | JSON OTLP for Playwright decode (`VITE_PULSE_FORMAT=json`); see web-sdk-ship Step 3. |
| D5 | Gate log | CI / PR description (optional `pulse-web-otel/progress.txt`) — append command + result. |
| D6 | IDB disk-buffer replay E2E | After a **non-retryable** failed logs export + `reload`, **`findAllLogs` stays empty** → before debugging routing/`fetch`, verify `IdbSignalBuffer.write` → **`enforceMaxSize`** did not delete the row: default **`DEFAULT_DISK_BUFFER_MAX_CACHE_SIZE_BYTES`** in `src/constants/disk-buffer.ts` must match **`docs/sdk-core/config-and-public-api/SPEC.md`** (~10 MiB, same order as `VITE_PULSE_DISK_BUFFER_MAX_SIZE_BYTES=10485760` in `.env.example`). A single JSON OTLP batch exceeds **10 KiB**; cap below batch size ⇒ **silent no replay**. See **§F** row 2026-05-14. |

## E. Close-out

| # | Item | Verify |
|---|------|--------|
| E1 | Cursor rules | Edits match `.cursor/rules/pulse-web-otel.mdc` + `.cursor/rules/web-sdk.mdc`. |
| E2 | Ship checklist | [web-sdk-ship](../web-sdk-ship/SKILL.md) — Steps 1–6 (tests + regression + **Step 5** pre-merge diff audit + docs). |
| E3 | Graphify | `graphify update . --no-viz` in `pulse-web-otel/` after substantive TS changes. |
| E4 | PR review | [pr-review](../pr-review/SKILL.md) before merge. |
| E5 | Handoff | Plan folder `HANDOFF-NEXT-AGENT.md` if pausing / handoff (done vs deferred + next-agent prompt). |

---

## F. Durable learnings (self-heal log)

**Purpose:** After a **valid** review or post-merge finding, add **one line per lesson** so the next instrumentation pass inherits the fix as **system context** (not chat memory). Keep bullets **atomic** (≤500 chars). Prefer this section over duplicating long prose in ADR unless the lesson is feature-specific.

**Judgement:** Promote here when the issue would likely **recur** on another instrumentation PR; keep feature-only notes in that plan’s ADR/PLAN; promote to `.cursor/rules/pulse-web-otel.mdc` only when **repo-wide** contract/lifecycle.

| Date (YYYY-MM-DD) | Source (PR / reviewer) | Lesson (imperative, testable) |
|-------------------|--------------------------|-------------------------------|
| *example* | *#123* | *Always assert `screen.name` on new log E2E positives.* |
| 2026-05-14 | SPEC audit iter1 + interactions SPEC | Document interactions as **`pulse.interaction.*`** span attrs + literal **`pulse.type = interaction`**; **`pulse.interaction.complete_time`** is **nanoseconds** — never `interaction.id` / `duration_ms` shorthand in SPECs or queries. |
| 2026-05-14 | SPEC audit iter6–7 + `sdk.ts` shutdown | Teardown SPECs and edge-flow Mermaid must follow **`PulseSDK.shutdown()`** order: **uninstall / session shutdown before `forceFlush`**, then clear provider refs and flags; document **`ProviderBundle.cleanup`** even when **`exporters.ts`** implements it as a no-op. |
| 2026-05-13 | SPEC audit iter9 deep sweep | **`audit-index.json`** `sdk-core` row points at rollup **`docs/sdk-core/SPEC.md` only** — a declared **deep** sweep must **also** walk **`docs/sdk-core/*/SPEC.md`** (or add per-topic index rows) so matrices + LLD in topic files get the same audit treatment as instrumentations. |
| 2026-05-14 | SPEC audit iter12 sdk-core topics | **`docs/sdk-core/data-contract/SPEC.md` §5.1** `pulse.type` rows must match **`semconv.ts`** + emitters: network spans use **`network.<statusCode>`** from `networkPulseType()` in `network-http.ts`, not `http`; installation uses **`pulse.app.installation.start`**, not `app.installation.start`. |
| 2026-05-14 | SPEC audit iter12 sdk-core topics | Do not conflate **`Pulse.init()`** (empty `apiKey` → warn + fulfilled promise, skips `validateConfig`) with **`validateConfig()`** (throws on missing `apiKey`) — matrix rows must name which entrypoint. |
| 2026-05-14 | SPEC audit iter14 instrumentations | **§6.1 Tests** column: cite concrete **`src/__tests__/*.ts(x)`** paths or mark **`missing`** / **`gap`** — never substitute requirement ids (**SR3**, **R3**), prose-only refs (**assumptions §2**, **source comments**), or vague **“uninstall path”** without a file. |
| 2026-05-14 | SPEC audit iter14 react-integration | **`pulse-router-events.test.tsx`** exercises **Next** `integrations/next/PulseRouterEvents`; **`react-integration`** SPEC matrix must point **`react/router`** coverage at **`use-router-tracking.test.tsx`** (or a dedicated React test), not the Next file. |
| 2026-05-14 | E2E catalogue sweep (`docs/sdk-core/test-coverage/SPEC.md` §6.4) | **`examples/nextjs-demo/e2e`** proves App Router + errors + CH subset only — treat **`screen.name` on logs** vs **`screen_load`/`screen_session` spans** as an explicit parity gap until Next Playwright waits on trace spans; port **`@M1`/`@M4`/`@WebVitals`/`@M2`** groups or document **won’t E2E**. |
| 2026-05-14 | M1 disk-buffer replay E2E + code review (`m1.spec.ts` / `indexed-db.ts`) | **IDB replay empty after failed export:** `IdbSignalBuffer.write` always runs **`enforceMaxSize`** after `add`; if **`DEFAULT_DISK_BUFFER_MAX_CACHE_SIZE_BYTES`** is below one OTLP JSON batch, the new row is **deleted immediately** — `drainBufferedOtlpExports` then finds nothing. Keep default aligned with SPEC + `.env.example` (**~10 MiB**, `10 * 1024 * 1024`, not `10 * 1024`). |

*(Append new rows at the bottom; do not delete history without archival note.)*

---

## Resume checklist (half-done branch)

1. `git fetch` && checkout branch; `git log -5 --oneline`; `git diff main --stat -- pulse-web-otel/ backend/server/src/...Features... backend/server/...DefaultSdkConfigTemplate...` (adjust paths).
2. Fill gap matrix above; sort **MISSING** + **PARTIAL** by dependency (docs before code drift; semconv before instrumentation).
3. Re-read **implemented** files against PLAN-B / ADR — if code diverged from docs, **update docs first** or open ADR amendment.
4. Run smallest failing test, then `yarn test:run`, then `e2e:web-sdk-gates`.
5. Update `test-run-log.md`; PR description lists gaps closed vs deferred.
