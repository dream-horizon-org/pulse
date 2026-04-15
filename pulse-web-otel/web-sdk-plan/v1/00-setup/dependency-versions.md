# Dependency Versions — `@dreamhorizon/pulse-web`

Pinned versions for the initial scaffold. Update when upgrading OTel packages (all `@opentelemetry/*` must be bumped together — they share the same release cycle).

## Node / Runtime Requirements

| Requirement | Version | Why |
|---|---|---|
| Node.js | ≥ 18.13.0 | `crypto.randomUUID()` (Session IDs), `CompressionStream` (gzip) |
| Yarn | 4.x (Berry) | workspace protocol + PnP-compat `nodeLinker: node-modules` |
| TypeScript | ^5.6.0 | `moduleResolution: bundler` support |

## OTel JS Packages

All `@opentelemetry/*` must stay on the **same minor** version within each versioning track:
- **Stable track** (`1.x`): `api`, `core`, `resources`, `sdk-trace-web`, `sdk-metrics`
- **Preview track** (`0.x`): `api-logs`, `sdk-logs`, exporters, instrumentation packages

```json
"@opentelemetry/api":                          "^1.9.0",
"@opentelemetry/api-logs":                     "^0.53.0",
"@opentelemetry/core":                         "^1.26.0",
"@opentelemetry/resources":                    "^1.26.0",
"@opentelemetry/sdk-trace-web":                "^1.26.0",
"@opentelemetry/sdk-logs":                     "^0.53.0",
"@opentelemetry/sdk-metrics":                  "^1.26.0",
"@opentelemetry/exporter-trace-otlp-http":     "^0.53.0",
"@opentelemetry/exporter-logs-otlp-http":      "^0.53.0",
"@opentelemetry/exporter-metrics-otlp-http":   "^0.53.0",
"@opentelemetry/instrumentation":              "^0.53.0",
"@opentelemetry/instrumentation-fetch":        "^0.53.0",
"@opentelemetry/instrumentation-xml-http-request": "^0.53.0"
```

> **Version mapping:** OTel JS uses a two-track versioning system. The `1.26.x` stable releases correspond to the `0.53.x` preview releases — they are released together. Always upgrade both tracks simultaneously.

## Other SDK Dependencies

```json
"web-vitals": "^4.2.0"
```

## Dev Dependencies

```json
"tsup":                       "^8.3.0",
"typescript":                 "^5.6.0",
"vitest":                     "^2.1.0",
"@vitest/coverage-v8":        "^2.1.0",
"jsdom":                      "^25.0.0",
"@size-limit/preset-small-lib": "^11.1.0",
"size-limit":                 "^11.1.0",
"@playwright/test":           "^1.47.0"
```

## E2E Test Dependencies (Playwright — ecommerce-demo only)

Playwright belongs in the **demo's** `package.json`, not the SDK root.
The SDK is a library; E2E tests are the demo app's concern.

```json
// examples/ecommerce-demo/package.json devDependencies:
"@playwright/test": "^1.47.0"
```

```bash
# After yarn install, download browser binaries once (from ecommerce-demo/):
cd examples/ecommerce-demo && yarn playwright install --with-deps chromium firefox webkit
```

The E2E suite lives in `pulse-web-otel/examples/ecommerce-demo/e2e/`.
Test env config: `examples/ecommerce-demo/.env.test` (fast flush, no gzip, fake OTLP host).

## Ecommerce Demo Dev Dependencies

```json
"react":                      "^18.3.0",
"react-dom":                  "^18.3.0",
"react-router-dom":           "^6.26.0",
"vite":                       "^5.4.0",
"@vitejs/plugin-react":       "^4.3.0",
"typescript":                 "^5.6.0",
"@types/react":               "^18.3.0",
"@types/react-dom":           "^18.3.0"
```

## How to Check for Newer Versions

```bash
# Check current OTel JS releases
npm view @opentelemetry/api versions --json | tail -1
npm view @opentelemetry/sdk-trace-web dist-tags.latest

# Check all OTel package versions in sync
npm view @opentelemetry/sdk-trace-web version
npm view @opentelemetry/exporter-trace-otlp-http version
# Both should have the same minor (1.26.x and 0.53.x respectively)
```
