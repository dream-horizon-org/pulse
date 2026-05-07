# SDK lifecycle (Web)

**Implementation:** `pulse-web-otel/src/sdk.ts`

## Start (`Pulse.init`)

- Validates configuration (API key, OTLP endpoint, project identity where required).
- Builds the OTel `WebTracerProvider`, exporters, and resource attributes (session, platform = `web`, etc.).
- Registers instrumentations based on **consent** and **`PulseFeature`** flags so disabled features do not subscribe listeners.

## Runtime

- Session and identity updates (`setUserId`, screen name, etc.) flow through the public API on `Pulse`.
- Batched export follows OTel Web SDK + Pulse defaults (flush on shutdown / visibility when configured).

## Shutdown

- Call `Pulse.shutdown()` (or the documented teardown path) so exporters flush and listeners unregister — avoids duplicates on hot reload in SPAs.

## Verification

- Unit tests under `pulse-web-otel/src/__tests__/`
- E2E gates: `pulse-web-otel/examples/ecommerce-demo` Playwright suites per [`../MILESTONES.md`](../MILESTONES.md)
