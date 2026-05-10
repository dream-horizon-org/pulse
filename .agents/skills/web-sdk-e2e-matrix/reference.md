# Reference — E2E matrix template & audit columns

Copy when generating or reviewing specs.

## Matrix columns

| ID | Case name | Type (positive / gate-off / consent / edge / lifecycle) | Preconditions (seed env, route, feature flags) | Steps (high level) | Assertions (pulse.type, attrs, finite checks) | Spec file:test line | Status |

## Gate-off seed snippet (pattern)

```ts
await seedPulseSdkConfig(page, minimalPulseSdkConfig({
  features: [{ featureName: "<PulseFeature.name>", sessionSampleRate: 0, sdks: ["pulse_web_js"] }],
}));
await blockActiveConfigFetch(page);
await page.goto("/");
await otlp.waitForLog("session.start");
otlp.reset();
// interact
// assert findAllLogs(..., "<pulse.type>").length === 0
```

Replace `<PulseFeature.name>` with the exact enum string from `remote-config.ts` (e.g. `click`, `web_vitals`).

## Positive-path log floor (logs)

Per **web-sdk-ship** / lifecycle D2:

- Exact `pulse.type` (string match semconv).
- For numeric attrs: `typeof === "number"` and `Number.isFinite`.
- Truthy `session.id`, `screen.name` on **every** positive-path log record unless ADR exempts.

## Files to touch when adding cases

| Change | File |
|--------|------|
| New or extended scenarios | `examples/ecommerce-demo/e2e/<area>.spec.ts` |
| Gate script | `examples/ecommerce-demo/package.json` → `e2e:web-sdk-gates` |
| Fixture helpers | `examples/ecommerce-demo/e2e/fixture.ts` |
| Seeded features list | `examples/ecommerce-demo/e2e/test-sdk-config.ts` |
| OTLP JSON for decode | `examples/ecommerce-demo/.env.test` |
