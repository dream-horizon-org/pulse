# Touchpoints matrix — Error instrumentation rerun

| Layer | File | Purpose | Rerun action |
|-------|------|---------|--------------|
| SDK instrumentation | `src/instrumentations/errors.ts` | unhandled errors + rejections capture | Reviewed lifecycle + contract behavior |
| SDK manual API | `src/sdk.ts` | `reportException` / `reportDeviceCrash` contract | Reviewed manual/non-manual parity |
| SDK registry | `src/instrumentation-registry.ts` | gate and instrumentation install wiring | Reviewed `errors` -> `js_crash` mapping |
| Semconv | `src/semconv.ts` | canonical keys and pulse types | Verified keys used in E2E assertions |
| Remote config | `src/remote-config.ts` + `src/types/remote-config.ts` | feature name for gate-off seeds | Used `js_crash` in gate-off test |
| Unit tests | `src/__tests__/m3.test.ts` | instrumentation behavior coverage | Reviewed; no change in this rerun |
| Demo route | `examples/ecommerce-demo/src/routes/ErrorDemo.tsx` | error trigger UI | Added string/undefined/dedupe burst actions |
| E2E fixture | `examples/ecommerce-demo/e2e/fixture.ts` | OTLP capture helpers | Reused existing helper surface |
| E2E config helper | `examples/ecommerce-demo/e2e/test-sdk-config.ts` | seed + block active config | Reused for gate-off scenario |
| Error E2E | `examples/ecommerce-demo/e2e/m3-errors.spec.ts` | contract + lifecycle edge coverage | Hardened and expanded |
| Gates script | `examples/ecommerce-demo/package.json` | gate-run spec list | Added `m3-errors.spec.ts` |
| E2E env wiring | `examples/ecommerce-demo/.env.test` | fixture-compatible OTLP encoding | Set JSON + no compression |
| Runtime test log | `web-sdk-plan/agent-runtime/test-run-log.md` | append-only run history | Appended rerun entries |
| Legacy plan doc | `web-sdk-plan/v1/02-instrumentations/errors.md` | historical monolithic notes | Kept as legacy reference |

