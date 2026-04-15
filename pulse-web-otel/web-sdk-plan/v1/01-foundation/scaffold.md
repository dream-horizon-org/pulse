# 01.A — Repo Scaffold & Config Types

**What this covers:** Project scaffolding, dependency setup, and the root config type that drives the entire SDK.

**Files produced:** `package.json`, `tsconfig.json`, `tsup.config.ts`, `vitest.config.ts`, `src/index.ts`, `src/config.ts`

---

## 1. Repo Scaffold

```
pulse-web-otel/
├── src/
│   ├── index.ts                 # Public API surface
│   ├── sdk.ts                   # SDK singleton
│   ├── config.ts                # PulseWebConfig + types
│   ├── session.ts               # Session Provider (identity.md)
│   ├── resource.ts              # OTEL Resource builder (resource.md)
│   ├── exporters.ts             # OTLP exporters + processors (pipeline.md)
│   ├── consent.ts               # DataCollectionConsent guard
│   ├── remoteConfig.ts          # SDK Config fetcher (sdk-config.md)
│   └── utils/
│       ├── ua-parser.ts         # User-agent / Client Hints parsing
│       └── compression.ts       # CompressionStream helper
├── src/instrumentations/        # One file per instrumentation
├── src/integrations/            # React, Next.js, CDN wrappers
├── package.json
├── tsconfig.json
├── tsup.config.ts
└── vitest.config.ts
```

### `package.json` key fields

```json
{
  "name": "@dreamhorizon/pulse-web",
  "version": "0.1.0-alpha.1",
  "type": "module",
  "main": "dist/index.cjs",
  "module": "dist/index.js",
  "types": "dist/index.d.ts"
}
```

### Core dependencies

```
@opentelemetry/sdk-trace-web
@opentelemetry/sdk-logs
@opentelemetry/sdk-metrics
@opentelemetry/resources
@opentelemetry/exporter-trace-otlp-http
@opentelemetry/exporter-logs-otlp-http
@opentelemetry/exporter-metrics-otlp-http
@opentelemetry/core
@opentelemetry/api
@opentelemetry/api-logs
```

---

## 2. Config Types (`src/config.ts`)

The single config object passed to `PulseWeb.start()`. Validates everything the SDK needs to operate.

```typescript
export interface PulseWebConfig {
  // Required
  endpointBaseUrl: string;              // e.g. "https://ingest.pulse.io"
  apiKey: string;                       // Pulse project API key
  serviceName: string;                  // Displayed in dashboards

  // Optional — SDK identity
  serviceVersion?: string;              // App version string, e.g. "1.4.2"

  // Optional — privacy & consent
  dataCollectionState?: PulseDataCollectionConsent;  // default: ALLOWED
  beforeSend?: (signal: unknown) => unknown | null;  // return null to drop signal

  // Optional — custom attributes on every signal
  globalAttributes?: Record<string, string | number | boolean>;

  // Optional — remote config
  configEndpointUrl?: string;           // override /v1/configs/active endpoint

  // Optional — export tuning
  export?: {
    format?: 'json' | 'protobuf';       // default: 'json'
    compression?: 'gzip' | 'none';      // default: 'gzip'
    batch?: {
      scheduledDelayMillis?: number;    // default: 5000
      maxQueueSize?: number;            // default: 2048
      maxExportBatchSize?: number;      // default: 512
    };
  };

  // Optional — persistence
  diskBuffering?: {
    enabled?: boolean;                  // default: false
    maxSizeBytes?: number;              // default: 5_242_880 (5 MB)
    maxAgeMs?: number;                  // default: 86_400_000 (24h)
  };

  // Optional — instrumentation toggles
  instrumentations?: InstrumentationConfig;
}

export enum PulseDataCollectionConsent {
  ALLOWED  = 'ALLOWED',
  DENIED   = 'DENIED',
  PENDING  = 'PENDING',
}

export interface InstrumentationConfig {
  errors?:         { enabled: boolean };
  network?:        { enabled: boolean };
  clicks?:         { enabled: boolean };
  webVitals?:      { enabled: boolean };
  navigation?:     { enabled: boolean };
  session?:        { enabled: boolean; inactivityTimeoutMs?: number };
  interactions?:   { enabled: boolean };
  sessionReplay?:  { enabled: boolean };  // opt-in, default false
}
```

### Config validation

Throw at `start()` time (not silently swallow) for:
- Missing `endpointBaseUrl`, `apiKey`, or `serviceName`
- `endpointBaseUrl` that is not a valid URL

---

## Done Criteria

- [ ] Repo scaffold created with all directories and files
- [ ] `package.json` has correct `name`, `type`, `main`, `module`, `types` fields
- [ ] All OTEL dependencies installable; `pnpm install` succeeds
- [ ] `PulseWebConfig` type covers all options above
- [ ] `PulseDataCollectionConsent` enum exported
- [ ] Missing required config fields throw at `start()` time
- [ ] Defaults applied for all optional fields
