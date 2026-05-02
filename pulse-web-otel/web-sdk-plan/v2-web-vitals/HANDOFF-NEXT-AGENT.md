# Handoff: Plan B Web Vitals (OTLP logs) — implementation status

**Canonical plan:** [PLAN-B-logs-events.md](./PLAN-B-logs-events.md) and execution checklist in repo plan `plan_b_web_vitals_execution_81f45a14.plan.md` (Cursor plans dir — do not edit that file unless PM asks).

---

## Copy-paste prompt for the next agent

```
You are continuing Plan B Web Vitals for pulse-web-otel: OTLP **logs** (not metrics), per
pulse-web-otel/web-sdk-plan/v2-web-vitals/PLAN-B-logs-events.md.

Already done in-tree:
- Docs: ADR-web-vitals.md, DESIGN.md, 04-contract-parity.md, README (v2) aligned to Plan B.
- Backend: Features.web_vitals enum; DefaultSdkConfigTemplate row for pulse_web_js;
  DefaultSdkConfigTemplateTest count 22→23 + expected feature list includes web_vitals.
- SDK: semconv (PulseType.WEB_VITAL, LogBody.WEB_VITAL, WEB_VITAL_* AttributeKeys);
  InstrumentationConfig.webVitals { enabled?, fid?, fcp? };
  SdkContext.loggerProvider + PulseWebSDK getter (private _loggerProvider);
  src/instrumentations/web-vitals.ts (onLCP/INP/CLS, optional FID/FCP, visibilitychange + pageshow flush);
  instrumentation-registry registerAndInstall(WebVitalsInstrumentation, WEB_VITALS).
- Unit: src/__tests__/web-vitals-instrumentation.test.ts (mock web-vitals + gate tests); m8.test.ts had two unused @ts-expect-error removed for tsc.
- E2E: examples/ecommerce-demo/e2e/web-vitals.spec.ts (pulse.type assertion); package.json e2e:web-sdk-gates includes it; `.env.test` forces JSON OTLP for fixture decode; `e2e:web-sdk-gates` green (2026-04-30).
- Registry: `installAll()` idempotent (no double web-vitals listeners); unit tests for single-owner + reinstall-after-uninstall.
- test-run-log.md row appended for web vitals gate.

Still verify if needed:
- backend: `mvn test` on DefaultSdkConfigTemplateTest (Java) if not run in your environment.
- graphify: `graphify update .` may fail on very large monorepo graphs (`--no-viz` per CLI message).

E2E note (why session.start timed out before):
- Demo defaulted protobuf export; Playwright fixture JSON-decodes only — use `.env.test` `VITE_PULSE_FORMAT=json` + `VITE_PULSE_COMPRESSION=none`.

Do not reintroduce OTLP metrics for vitals in this track. Keep logs.getLogger("pulse-web-vitals") and navigation_type omission semantics per PLAN-B.
```

---

## File map (quick)

| Area | Path |
|------|------|
| Instrumentation | [pulse-web-otel/src/instrumentations/web-vitals.ts](../../src/instrumentations/web-vitals.ts) |
| Registry | [pulse-web-otel/src/instrumentation-registry.ts](../../src/instrumentation-registry.ts) |
| Semconv | [pulse-web-otel/src/semconv.ts](../../src/semconv.ts) |
| SdkContext / SDK | [pulse-web-otel/src/types/instrumentation-registry.ts](../../src/types/instrumentation-registry.ts), [pulse-web-otel/src/sdk.ts](../../src/sdk.ts) |
| Config | [pulse-web-otel/src/types/config.ts](../../src/types/config.ts) |
| Unit tests | [pulse-web-otel/src/__tests__/web-vitals-instrumentation.test.ts](../../src/__tests__/web-vitals-instrumentation.test.ts) |
| E2E | [pulse-web-otel/examples/ecommerce-demo/e2e/web-vitals.spec.ts](../../examples/ecommerce-demo/e2e/web-vitals.spec.ts) |
| Backend | `backend/server/.../Features.java`, `DefaultSdkConfigTemplate.java`, `DefaultSdkConfigTemplateTest.java` |

---

## Optional test gaps (plan Phase 5)

Not all matrix rows may be covered yet: beforeSendLog drop vitals, sampling regression, global processor stamping vitals end-to-end, broader permutations. Extend `web-vitals-instrumentation.test.ts` or SDK integration tests as needed.
