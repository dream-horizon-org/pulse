# Review fix — working log

Package: `@dreamhorizonorg/pulse-web`  
Path: `pulse-web-otel/docs/review-fix.md`

---

## 1. Purpose

**Review fix** lists **TypeScript / packaging work and automated tests** (Vitest, Playwright) still to land. **SPECs, README, publishing guides, and JSDoc** live under `docs/**` and `src/**` and are updated there directly — not tracked as backlog rows here.

**Product register** (blocked, deferred, tradeoffs, open questions, retired IDs): [`known-gaps-tradeoffs-and-plan.md`](./known-gaps-tradeoffs-and-plan.md).

### 1.1 How to use

1. Execute **§3** top-to-bottom unless **§4** batch plan reorders.  
2. When an item ships, append **§5** changelog and add a line under [`known-gaps-tradeoffs-and-plan.md`](./known-gaps-tradeoffs-and-plan.md) **§4.1** if a stable ID retires.  
3. Implementation sketches for **P0:1**, **P2:13**, **P2:12** stay in **§7–§9** below.  
4. Multi-PR batches: fill **§4** and use **§4.4** checklist.

### 1.2 Status values

| Status | Meaning |
|--------|---------|
| `logged` | Captured only; no implementation decision |
| `planned` | Slated for a named batch / milestone in §4 |
| `in progress` | Active branch / PR |
| `done` | Landed; follow SPEC + tests |
| `waived` | Won’t fix; short rationale in Notes |

---

## 2. Source of truth

| Document | Role |
|----------|------|
| [`known-gaps-tradeoffs-and-plan.md`](./known-gaps-tradeoffs-and-plan.md) | Gaps, tradeoffs, open questions, retired id archive |
| [`test-coverage/SPEC.md`](./sdk-core/test-coverage/SPEC.md) | Tests + **§6.6** consumer smoke + **§6.7** RF-DC2–DC4 + **§6.8** RF-SF1 index |
| [`sampling-and-filtering/SPEC.md`](./sdk-core/sampling-and-filtering/SPEC.md) | Export sampling, filters, `SignalFilterProcessor`; Vitest gaps **RF-SF1** in **§3.4** below |
| [`config-and-public-api/SPEC.md`](./sdk-core/config-and-public-api/SPEC.md) | Init, consent, `Pulse.*` public API |

---

## 3. Fix backlog (ordered) — code + tests only

**Stable IDs** when retired: [`known-gaps-tradeoffs-and-plan.md`](./known-gaps-tradeoffs-and-plan.md) **§4.1**.

### 3.1 P2:12 — `PulseDataCollectionConsent` enum vs string union

**Problem / fix / exit:** See **[§9](#9-p212--pulsedatacollectionconsent-enum-vs-string-union)**.

### 3.2 RF-DC2–RF-DC4 — Data-contract test follow-ups

**Problem:** Vitest / Playwright gaps after RF-DC1 SPEC work — see [`sdk-core/test-coverage/SPEC.md`](./sdk-core/test-coverage/SPEC.md) **§6.7** for the matrix.

**Fix plan:** Implement **RF-DC2** (required); **RF-DC3** / **RF-DC4** optional or waived with changelog note.

**Exit / test:** `yarn test` / Vitest green; Playwright if RF-DC4 not waived.

### 3.3 RF-RC1 — Remote config / `FeatureGate` / `SdkConfigFetcher` Vitest gaps

**Problem:** [`sdk-core/remote-config-features-and-sampling/SPEC.md`](./sdk-core/remote-config-features-and-sampling/SPEC.md) §6.1 now matches code (no version monotonicity, no id-merge for `features`). Several branches are **E2E-covered** or implied by code but lack **Vitest** rows.

**Fix plan (additive tests only — no product change):**

| Sub-id | Scenario | Suggested location |
|--------|-----------|-------------------|
| **RF-RC1a** | `FeatureGate`: config row matches `featureName` but `sdks` omits `pulse_web_js` → `isEnabled(feature)` is `true` | `src/__tests__/m1.test.ts` (extend `M1 — FeatureGate`) |
| **RF-RC1b** | `SdkConfigFetcher.loadCached`: `localStorage` has invalid JSON **or** JSON that fails `isValidSdkConfig` → merged result equals default-shaped config (same as missing key); document whether `setItem` is absent | `m1.test.ts` |
| **RF-RC1c** | `fetchInBackground`: `fetch` resolves `!ok`; or `ok` + body fails `isValidSdkConfig`; or `fetch` throws → no successful persist of bad body | `m1.test.ts` |
| **RF-RC1d** | (Optional) cached `version: 10`, fetch returns valid config `version: 5` → fetcher applies merged v5 (non-monotonic **by design**; locks SPEC behavior) | `m1.test.ts` |

**Exit / test:** `yarn test` green; no change to default product behavior.

**Status:** `logged`

### 3.4 RF-SF1 — Sampling & filtering Vitest gaps (`sampling-and-filtering` SPEC)

**Problem:** [`sdk-core/sampling-and-filtering/SPEC.md`](./sdk-core/sampling-and-filtering/SPEC.md) §6–§7 updated for audit parity (R-SF6 → `signal-filter-processor.test.ts`; R-SF7/R-SF8 metric chain; matrix rows SF-E5–E8). **R-SF8** and several **export-gate** branches still lack focused tests. Index: [`sdk-core/test-coverage/SPEC.md`](./sdk-core/test-coverage/SPEC.md) **§6.8**.

**Code changes:** **None** by default (documented behavior already matches `src/`). Only add production code if a test reveals a bug.

**Fix plan (tests):**

| Sub-id | Scenario | Suggested location |
|--------|-----------|-------------------|
| **RF-SF1a** | `ExportSamplingGate.filterReadableSpans`: BLACKLIST / WHITELIST / `alwaysSend` on span **name** + attrs (parity with log tests) | `export-sampling-gate.test.ts` or `export-sampling-gate-spans.test.ts` |
| **RF-SF1b** | `filterResourceMetrics`: keep/drop by `descriptor.name`; session 0 + `signalsToSample` override for one metric name | `export-sampling-gate.test.ts` (construct minimal `ResourceMetrics` / `ScopeMetrics` fixtures) |
| **RF-SF1c** | Matched `signalsToSample` entry with `sampleRate` such that `sessionRandomDraw >= clamp01(sampleRate)` while `default.sessionSampleRate === 1` → signal **dropped** | `export-sampling-gate.test.ts` (`Math.random` mock) |
| **RF-SF1d** | `SampledSpanExporter` / `SampledLogRecordExporter` / `SampledPushMetricExporter`: filtered batch **empty** → result `SUCCESS`, **spy** on inner `export` **not** called | `sampling-exporters.test.ts` (new) or extend existing |
| **RF-SF1e** | (Optional Playwright) assert OTLP metric payload count when seeded config sets metric-level sampling — only if product wants browser-level lock | `e2e/m1.spec.ts` or waive |

**Exit / test:** `cd pulse-web-otel && yarn test` green; Playwright only if RF-SF1e not waived.

**Status:** `logged`

---

## 4. Batch plan (fill when ready)

Use this section when you want a **single coordinated pass** instead of one-off PRs.

### 4.1 Target milestone / release

_(e.g. `0.2.0`, `1.0.0-rc.1`)_

### 4.2 Batch ordering (dependencies)

| Order | IDs | Rationale |
|-------|-----|-----------|
| 1 | _(e.g. P2:12)_ | _(e.g. types before exporter tests)_ |
| 2 | | |

### 4.3 Out of scope for this batch

Note: waived IDs or deferred to post-GA.

### 4.4 PR checklist (per batch)

- [ ] Code + types + `src/index.ts` exports  
- [ ] Vitest (`src/__tests__`) + Playwright gates if telemetry changes  
- [ ] Normative **SPEC** updates if behaviour or contracts change  
- [ ] Update [`known-gaps-tradeoffs-and-plan.md`](./known-gaps-tradeoffs-and-plan.md) **§4.1** when an id retires  
- [ ] Update **§3** / **§5** in this file

### 4.5 Owners / dates

| Owner | Batch | Target date |
|-------|-------|-------------|
| | | |

---

## 5. Changelog (this file)

| Date | Change |
|------|--------|
| 2026-05-15 | **RF-SF1** — Sampling / export-gate / metrics Vitest backlog after [`sdk-core/sampling-and-filtering/SPEC.md`](./sdk-core/sampling-and-filtering/SPEC.md) audit → **§3.4**; index row [`sdk-core/test-coverage/SPEC.md`](./sdk-core/test-coverage/SPEC.md) **§6.8**. |
| 2026-05-15 | **RF-RC1** — Remote-config SPEC aligned to code (merge + version semantics); Vitest backlog for `FeatureGate` / `SdkConfigFetcher` branches → **§3.3**. |
| 2026-05-15 | **Doc / SPEC hygiene** (P0:4, P0:5, P0:6, P1:10) landed in `docs/**`, `README.md`, `docs/publishing/INTEGRATION.md`, `src/types/config.ts` / `react.ts` JSDoc, `PulseProvider.tsx` — **removed from §3**. This file is **code + tests only**. RF-DC2–4 index → [`sdk-core/test-coverage/SPEC.md`](./sdk-core/test-coverage/SPEC.md) §6.7. Prior **§6** (data-contract audit narrative) removed; RF-DC1 story stays in [`sdk-core/data-contract/SPEC.md`](./sdk-core/data-contract/SPEC.md). |
| 2026-05-14 | **Gaps register** moved from `sdk-core/known-gaps-and-open-questions/SPEC.md` to [`known-gaps-tradeoffs-and-plan.md`](./known-gaps-tradeoffs-and-plan.md) (§1–§4). All sdk-core / instrumentation links updated. |
| 2026-05-14 | Initial log seeded from known-gaps register (legacy `sdk-core/known-gaps-and-open-questions/SPEC.md`); expanded suggested fixes for P0:3, P0:4. |
| 2026-05-14 | Fixed header path to `docs/review-fix.md`. Added **§6** (data contract audit narrative) and §3 rows **RF-DC1**–**RF-DC4** (SPEC done; Vitest/E2E follow-ups). |
| 2026-05-14 | **P2:13** — Feasibility eval (§8): OTel alias approach; no code change. |
| 2026-05-14 | **P0:1** + **P2:13** — Full write-ups in §7–§8; rows removed from §3 log. |
| 2026-05-14 | **P0:3** → **O1** (identity API tradeoff) in gaps register; row removed from §3 here. |
| 2026-05-14 | **§3** replaced log table with **ordered fix backlog** (3.1–3.5). **P1:9** → **O2** in gaps register §2. **P0:5** re-reviewed (component exists). **RF-DC1** removed from backlog (covered §6.1). |
| 2026-05-14 | **§3** — tables removed; only **ready-to-merge** items kept. **P1:7**, **P2:11**, **O2** moved to [`known-gaps-tradeoffs-and-plan.md`](./known-gaps-tradeoffs-and-plan.md). **P2:12** plan moved to **§9**; **§1** gaps register deduped. |

---

## 7. P0:1 — Entry point (`Pulse` singleton vs factory / `init`)

### Problem (P0:1)

Exports center on a **pre-built singleton object** `Pulse`: hosts call `Pulse.init(config)` then `Pulse.trackEvent(...)` (see `src/index.ts`, `src/sdk.ts`). That is valid but **atypical** next to common browser RUM patterns where bootstrap is a **top-level `init()`** (e.g. `@sentry/browser`: `Sentry.init({...})` and the module owns the client). New integrators may (a) think they need a `new` constructor, (b) want types-only imports without pulling the runtime object as the primary symbol, or (c) reach for `init` first from muscle memory.

### Reasoning (P0:1)

- **DX / teachability:** A named `init()` matches how many teams learn RUM; `Pulse.*` stays the stable method surface after init.  
- **Risk:** **Low** if `Pulse.init` remains a thin alias — no breaking migration.  
- **Not** a request for multiple independent SDK instances unless a later ADR adds multi-client support.

### Suggested fix (P0:1) (implementation sketch)

1. **`export async function init(config: PulseWebConfig): Promise<void>`** (exact name TBD) — delegates to today’s `Pulse.init` implementation.  
2. **Optional `export function getPulseClient(): Pulse`** (or `getClient`) — returns the singleton for advanced wiring.  
3. **`Pulse.init`** — same body or forwards to `init`; document as **back-compat alias**.  
4. **Docs:** [`instrumentations/integration/SPEC.md`](./instrumentations/integration/SPEC.md) + package README show **`init()`** as the primary bootstrap line; note that `Pulse.init` remains supported.

**Status:** Not implemented — execution log only. On ship: append [`known-gaps-tradeoffs-and-plan.md`](./known-gaps-tradeoffs-and-plan.md) **§4.1**; strike or archive §7 here and refresh **§5**.

---

## 8. P2:13 — `PulseAttributes` / OTel alignment (feasibility only)

**Verdict:** **Feasible** as a P2 type-only change. Intended outcome: host code can pass **`Attributes`** from `@opentelemetry/api` into `PulseWebConfig.globalAttributes`, `Pulse.trackEvent`, etc., without casts.

| Check | Detail |
|-------|--------|
| **API shape** | OTel `Attributes` / `AttributeValue` (`node_modules/@opentelemetry/api/build/src/common/Attributes.d.ts`) use an index signature and allow `undefined` on values; arrays allow `null \| undefined` members in the element union. Current Pulse types in `src/types/attributes.ts` are a hand-rolled subset — close, but not assignable to OTel’s symbols in TS. |
| **Implementation** | Replace hand-rolled unions with **aliases** to OTel types (keeps **`PulseAttributes`** / **`PulseAttributeValue`** names on the public surface = “Pulse + OTel” in docs). Retain **`PulseAttributePrimitive`** where internal code should stay primitive-only (`src/resource.ts`). |
| **Name collision** | `src/types/remote-config.ts` exports **`interface PulseAttributeValue`** (remote sampling DTO — `name`, `value`, `type`). **`src/processors/signal-filter-processor.ts`** imports that type, not `types/attributes`. Before or with P2:13, **rename** the remote-config interface (e.g. `PulseRemoteAttributeValue`) so “PulseAttributeValue” means one thing in the span-bag module. |
| **Runtime risk** | Low if behavior is unchanged — OTel type is slightly **wider**. Confirm `PulseGlobalAttributesProcessor` / resource merge still drop or stringify anything that must not hit OTLP (existing tests are the gate). |
| **Effort** | One focused PR + `docs/sdk-core/config-and-public-api/SPEC.md` snippet if it duplicates the old hand-rolled definition + changelog here → `done` when shipped. |

---

## 9. P2:12 — `PulseDataCollectionConsent` enum vs string union

### Problem (P2:12)

Hosts and internal config use **`PulseDataCollectionConsent`** (`ALLOWED` / `DENIED` / `PENDING`). Wire literals are stable, but the **enum-only** surface is awkward in modern TS (string unions, exhaustiveness, tree-shaking expectations).

### Reasoning (P2:12)

- **DX:** Accept `type ConsentState = 'ALLOWED' | 'DENIED' | 'PENDING'` (or equivalent) alongside the enum during a deprecation window.  
- **Risk:** **Low** if validation accepts both shapes and serialization stays literal strings.  
- **Product tie-in:** Deprecation warnings / timeline belong with [`known-gaps-tradeoffs-and-plan.md`](./known-gaps-tradeoffs-and-plan.md) **§3 Q1**.

### Suggested fix (P2:12) (implementation sketch)

1. **`src/types/config.ts`** — introduce string-literal union type; widen `PulseWebConfig` / consent fields to `enum | union` (exact names per code review).  
2. **`src/consent.ts`** — normalize to one internal representation for processors.  
3. **`validateConfig`** — accept both enum and string forms; optional dev-only warning when enum is used (gated by Q1 decision).  
4. **Exports** — keep `PulseDataCollectionConsent` exported but mark **@deprecated** in JSDoc with migration line to string union.  
5. **Docs** — `config-and-public-api` SPEC + this file **§5** when landed; update [`known-gaps-tradeoffs-and-plan.md`](./known-gaps-tradeoffs-and-plan.md) **§4.1**.

### Exit / test

`yarn lint` + Vitest paths covering consent / init blocked states; no behavior regression on OTLP resource consent attrs.

**Status:** Not implemented.
