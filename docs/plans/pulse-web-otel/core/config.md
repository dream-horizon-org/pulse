# core/config

## 1. Purpose

Defines the public configuration surface (`PulseWebConfig`), validates it at `Pulse.init()` time, and resolves the OTLP endpoint based on the API key.

## 2. Source location

- `pulse-web-otel/src/config.ts` — re-exports + `validateConfig`, `resolveEndpointBaseUrl`, `isLocalEnvironment`
- `pulse-web-otel/src/types/config.ts` — `PulseWebConfig`, `InstrumentationKeys`, `InstrumentationConfig`, `PulseDataCollectionConsent`, `PulseWebDiskBufferingConfig`
- `pulse-web-otel/src/pulse-log-level.ts` — `PulseLogLevel`
- `pulse-web-otel/src/before-send.ts` — `validateBeforeSendConfig`

## 3. Public surface

```ts
export { PulseDataCollectionConsent, InstrumentationKeys } from "./types/config";
export type {
  InstrumentationKey, InstrumentationConfig,
  PulseWebDiskBufferingConfig, PulseWebConfig,
} from "./types/config";
export { PulseLogLevel } from "./pulse-log-level";
export function validateConfig(config: PulseWebConfig): void;
export function isLocalEnvironment(apiKey: string): boolean;
export function resolveEndpointBaseUrl(apiKey: string, provided?: string): string;
export const PULSE_PROD_ENDPOINT_URL = "https://pulse-otel-collector.pulse-ux.com";
```

`PulseWebConfig` includes (see `types/config.ts` for exact shape): `apiKey` (required), `serviceName`, `serviceVersion`, `endpoint`, `consent`, `instrumentations` (per-key `{ enabled, ... }`), `diskBuffering`, `beforeSendData`, `logLevel`, `useProtobuf`.

## 4. Internal design

- `validateConfig` throws on missing `apiKey`, invalid `beforeSendData`, non-positive `diskBuffering.maxAgeMs` / `maxCacheSizeBytes`.
- `isLocalEnvironment` matches `^default-project.*_.*` — covers `default-project_devkey01` and project slugs like `default-project-lottery-<id>_<secret>`.
- `resolveEndpointBaseUrl(apiKey, provided)` returns `provided` if set, else `http://localhost:4318` for local, else `PULSE_PROD_ENDPOINT_URL`.

## 5. Dependencies

None beyond `before-send.ts` for nested validation.

## 6. Data contracts

Indirect: `apiKey` is parsed by `resource.ts` (`extractProjectId`) to produce the `project.id` resource attribute on every signal.

## 7. Tests

- `src/__tests__/merge-pulse-sdk-config.test.ts`
- `src/__tests__/with-pulse-config.test.ts` (Next.js wrapper)
- `src/__tests__/sdk-lifecycle.test.ts` covers endpoint resolution paths.

## 8. History / decisions

- Endpoint resolution mirrors Android `PulseEndpointUtils.getBaseUrl()`.
- Compression and wire-format toggles were intentionally kept off the public surface (compression hardcoded off in `exporters.ts`).

## 9. Rebuild recipe

1. Define `PulseWebConfig` interface in `types/config.ts`, with `InstrumentationKeys` enum.
2. Implement `validateConfig` to throw `Error("[Pulse] ...")` with explicit field names.
3. Implement `resolveEndpointBaseUrl` exactly as above to keep dev/prod symmetry with mobile SDKs.
