# core/resource-attrs

## 1. Purpose

Build the OpenTelemetry `Resource` attached to every span, log, and metric — the static identity of the browser session for ClickHouse materialized columns (`ProjectId`, `Platform`, `AppVersion`, …).

## 2. Source location

- `pulse-web-otel/src/resource.ts` — `buildResource`, `buildMergedResource`, `extractProjectId`, `computeAspectRatio`
- `pulse-web-otel/src/utils/ua-parser.ts` — `parseUserAgent`, `getOsVersionAsync`
- `pulse-web-otel/src/version.ts` — `SDK_VERSION`

## 3. Public surface

```ts
export function buildResource(config: PulseWebConfig, osVersion: string): Resource;
export function buildMergedResource(config: PulseWebConfig): Promise<Resource>;
export function extractProjectId(apiKey: string): string;
export function computeAspectRatio(w: number, h: number): string;
```

Not re-exported from `src/index.ts` — internal to the SDK.

## 4. Internal design

`buildResource` writes 18 attributes, keys from `PulseWebSemconv.ResourceKey`:

- Identity: `service.name`, `service.version`, `app.build_name`, `installation.id`, `project.id`
- Platform: `platform = "web"`, `telemetry.sdk.name`, `rum.sdk.name`, `rum.sdk.version` (= `SDK_VERSION`)
- Browser: `browser.name`, `browser.version`, `browser.language`
- OS: `os.name`, `os.version`
- Device: `device.type`, `screen.resolution`, `screen.aspect_ratio`, `screen.color_depth`
- Env: `network.online`, `timezone`

`extractProjectId(apiKey)` returns everything before the last underscore (`default-project_devkey01` ⇒ `default-project`). `computeAspectRatio` reduces width/height by gcd.

`buildMergedResource` awaits the async UA-Client-Hints OS version, then merges with the synchronous resource.

## 5. Dependencies

- `@opentelemetry/resources` (`resourceFromAttributes`)
- `utils/ua-parser.ts`, `session.ts` (installation id), `semconv.ts`

## 6. Data contracts

All keys above are stable resource attributes. The collector maps `ResourceAttributes['project.id']` → ClickHouse `ProjectId`, `ResourceAttributes['os.name']` → `Platform`, `ResourceAttributes['app.build_name']` → `AppVersion` — see project `CLAUDE.md` § ClickHouse Schema.

## 7. Tests

- `src/__tests__/sdk-lifecycle.test.ts` asserts resource attributes
- `src/__tests__/screen-name-resolution.test.ts` indirectly checks UA parser

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md` § resource. `service.version` falls back to `"0.0.0"` instead of `SDK_VERSION` because `app.build_name` already encodes SDK version for stack-trace matching.

## 9. Rebuild recipe

1. Wire UA parser (sync subset + async client-hints).
2. Implement `buildResource` with the 18 keys above.
3. Provide `buildMergedResource` async wrapper that fetches OS version then calls `buildResource`.
4. Test that `service.name` falls back to `window.location.hostname` when not provided.
