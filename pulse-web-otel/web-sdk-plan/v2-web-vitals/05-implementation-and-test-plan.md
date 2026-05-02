# Implementation and test plan: Web Vitals (phased)

**Status:** Planning artifact (Phase F).  
**Tracks:** [ADR-web-vitals.md](./ADR-web-vitals.md).

---

## Phase 0 — Backend config alignment (can parallelize with Phase 1)

**Goal:** Remote feature name `web_vitals` round-trips with pulse-server.

| Task | Verification |
|------|----------------|
| Add `web_vitals` to [`Features.java`](../../../backend/server/src/main/java/org/dreamhorizon/pulseserver/service/configs/models/Features.java) | Compile; enum serialization tests |
| Add default feature row for `Sdk.pulse_web_js` in [`DefaultSdkConfigTemplate`](../../../backend/server/src/main/java/org/dreamhorizon/pulseserver/service/configs/DefaultSdkConfigTemplate.java) | `DefaultSdkConfigTemplateTest` — update expected feature count/list |
| Adjust any controller tests that assert exhaustive feature lists | `mvn test` scoped to config module |

**Exit criteria:** New projects receive `web_vitals` for web JS SDK in default JSON.

---

## Phase 1 — SDK core instrumentation

**Goal:** `WebVitalsInstrumentation` registered and emitting histograms per ADR.

| Task | Detail |
|------|--------|
| Semconv | Add `PulseType.WEB_VITAL` (or `WEB_VITAL` string), attribute keys for `web_vital.name` / `rating` in [`semconv.ts`](../../src/semconv.ts). |
| Instrumentation | New module: subscribe `onLCP`, `onINP`, `onCLS` (+ optional FID); `metrics.getMeter`; record with `pulse.type` + `web_vital.name`. |
| Registry | In [`instrumentation-registry.ts`](../../src/instrumentation-registry.ts) `installAll()`, register web vitals with `InstrumentationKeys.WEB_VITALS`. |

### Unit tests (Vitest)

| Suite | Cases |
|-------|--------|
| Gate **off** | Remote config includes `{ featureName: "web_vitals", sessionSampleRate: 1, sdks: [...] }` disabled path — instrumentation **not** installed (mock gate). |
| Config **off** | `instrumentations: { webVitals: { enabled: false } }` — not installed. |
| Consent | `dataCollectionState: DENIED` — `start()` no-op (existing pattern); **no** meter calls. |
| Single owner | Second `start()` does nothing; vitals not double-subscribed. |
| `uninstall` | After `shutdown()`, assert listeners cleaned (spy on `web-vitals` cancel handles if exposed). |
| Attribute shape | Exported metric attributes contain `pulse.type`, `web_vital.name`, and global attrs when meter is mocked. |

Use **mock `Meter`** or in-memory `MeterProvider` + reader where feasible.

**Exit criteria:** Unit tests green; no E2E yet.

---

## Phase 2 — Export path and privacy

**Goal:** OTLP batches include vitals; hooks behave.

| Task | Detail |
|------|--------|
| Before-send | Integration test: `beforeSendMetric` receives `ResourceMetrics`; return dropped payload → no export. |
| Sampling | When `ExportSamplingGate` drops session, vitals drop with other signals (existing behavior — regression test). |
| Global attrs | Verify `getMetricGlobalAttrs` stamps session/screen on vital data points (may use existing processor tests pattern). |

**Exit criteria:** Snapshot or structural test on exported metric descriptor/data point attributes (stable fields only).

---

## Phase 3 — Demo app + E2E

**Goal:** Browser verification and regression gates.

| Task | Detail |
|------|--------|
| Demo | Ensure ecommerce-demo loads SDK with vitals enabled; optional tiny UI to force layout for CLS/LCP. |
| Playwright | New spec or extend gates: wait for OTLP request to `/v1/metrics` (or intercept) and assert payload contains expected metric names / attrs **or** assert SDK instrument installed via `window` diagnostic hook if exposed only in test build. |
| Required command | `yarn workspace ecommerce-demo e2e:web-sdk-gates` must pass; add `e2e:web-vitals` script if separate file. |

**Exit criteria:** Chromium green minimum; document WebKit/Firefox gaps in [`agent-runtime/test-run-log.md`](../agent-runtime/test-run-log.md).

---

## Phase 4 — Edge and permutation matrix

Document table-driven tests (implementation should use parameterized tests where possible):

| Dimension | Values |
|-----------|--------|
| Feature gate | on / off |
| `instrumentations.webVitals.enabled` | true / false |
| Consent | ALLOWED / DENIED |
| `beforeSendMetric` | pass-through / drop all |
| Shutdown timing | flush mid-metric vs idle |
| Navigation | SPA route change mid-session (vitals still reported) |

**Negative:** Invalid remote config (malformed `features` array) — SDK should not crash; vitals follow same recovery as other features.

---

## Phase 5 — Ops verification (optional P0.5)

| Task | Detail |
|------|--------|
| Local stack | [`deploy-service`](../../../.cursor/skills/deploy-service/SKILL.md): Collector + ClickHouse — sample SQL to count metrics with `pulse.type` / metric name prefix. |

---

## Definition of done (MVP)

- [ ] Backend `Features.web_vitals` + default template row.
- [ ] SDK instrumentation + registry + semconv.
- [ ] Unit coverage for Phases 1–2.
- [ ] E2E gate extended or new spec for vitals path.
- [ ] [04-contract-parity.md](./04-contract-parity.md) matches shipped attributes.
- [ ] Append pass/fail to [`test-run-log.md`](../agent-runtime/test-run-log.md).

---

## Post-merge

- Run `graphify update .` from repo root per workspace rule after code changes.
- Link this folder from [`README.md`](../../README.md) “Useful docs”.
