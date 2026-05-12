# core/sdk-bootstrap

## 1. Purpose

Single, idempotent entry point that wires OpenTelemetry providers, builds the OTel `Resource`, fetches the remote `PulseSdkConfig`, constructs the `FeatureGate`, and asks the `InstrumentationRegistry` to install everything. Matches the Android `PulseSDK` public surface.

## 2. Source location

- `pulse-web-otel/src/sdk.ts` — `class PulseSDK` (singleton), exported as `Pulse` from `src/index.ts`
- `pulse-web-otel/src/instrumentation-registry.ts` — registry of `PulseInstrumentation` objects
- `pulse-web-otel/src/version.ts` — `SDK_VERSION`

## 3. Public surface

Exports (from `src/index.ts`):

```ts
export { Pulse } from "./sdk";
export type {
  PulseWebConfig, PulseWebDiskBufferingConfig, InstrumentationConfig,
  PulseBeforeSendResult, PulseExportSignal,
  PulseWebBeforeSendCallbacks, PulseWebBeforeSendConfig,
} from "./config";
export { PulseDataCollectionConsent, PulseLogLevel } from "./config";
export { SDK_VERSION } from "./version";
export { PulseWebSemconv } from "./semconv";
```

`Pulse` methods (see `sdk.ts`): `init(config)`, `shutdown()`, `setUserId(id)`, `clearUserId()`, `setUserProperties(props)`, `getSessionId()`, `getInstallationId()`, `setConsent(state)`, `flush()`. `init()` is idempotent and rejects re-entry while initializing.

## 4. Internal design

`init()` order:

1. Validate config (`validateConfig`); resolve endpoint via `resolveEndpointBaseUrl(apiKey, endpoint)`.
2. Resolve identity: `getOrCreateInstallationId()`, restore persisted userId/properties.
3. Build merged Resource (`buildMergedResource`) — UA parser + screen + timezone + project id.
4. Fetch remote SDK config (`SdkConfigFetcher`) with fallback to `DEFAULT_SDK_CONFIG`; construct `FeatureGate`.
5. Construct `ExportSamplingGate` (one random draw per init), then `createProviders` to build trace / log / metric providers with OTLP exporters wrapped by sampling → metrics-to-add → beforeSend → batch.
6. Install global attr processor, signal-filter processor, optional log lifecycle debug.
7. Drain previous IndexedDB buffer (`drainBufferedOtlpExports`) before letting new exports flow.
8. `InstrumentationRegistry.installAll()` — each instrumentation checks `gate.isEnabled(feature)` and local `instrumentations[key].enabled`.
9. Emit OTel init events (`SdkInitializationEvents` parity).

`shutdown()` reverses: `uninstallAll`, `forceFlush`, `shutdown` on each provider.

## 5. Dependencies

- `@opentelemetry/api`, `@opentelemetry/api-logs`, `@opentelemetry/sdk-trace-web`, `@opentelemetry/sdk-logs`, `@opentelemetry/sdk-metrics`
- Internal: `config`, `session`, `resource`, `remote-config`, `feature-gate`, `exporters`, `instrumentation-registry`, `processors/*`, `sampling/export-sampling-gate`, `persistence/drain-buffered-exports`

## 6. Data contracts

The bootstrap itself emits init OTLP logs (`otel.initialization.events`, see `PulseWebSemconv.AttributeKey.SPAN_EXPORTER`). Every later signal carries the resource attributes set here (`platform = 'web'`, `service.name`, `service.version`, `installation.id`, `project.id`, etc.).

## 7. Tests

- `src/__tests__/sdk-lifecycle.test.ts`
- `src/__tests__/sdk-public-methods.test.ts`
- `src/__tests__/integration-simplified-init.test.ts`
- `src/__tests__/m1.test.ts`
- E2E: `examples/ecommerce-demo/e2e/m1.spec.ts`

## 8. History / decisions

- Canonical SPEC: `pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md`
- Endpoint resolution and disk-buffer defaults mirror Android (`PulseSDKInternal`, `DiskBufferingConfigurationSpec`).

## 9. Rebuild recipe

1. Implement `validateConfig` and `resolveEndpointBaseUrl` in `config.ts`.
2. In `sdk.ts`, create singleton with `_initialized` / `_initializing` / `_shuttingDown` guards.
3. Run the 9-step `init()` sequence above; never throw — log via `PulseWebLogger` and degrade.
4. Wire `InstrumentationRegistry` with a `featureMap: InstrumentationKey → PulseFeatureName`.
5. Provide `shutdown()` that reverses order and resets the singleton.
