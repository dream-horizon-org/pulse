# pulse-web-otel

## What

Browser SDK for Pulse RUM. Captures sessions, JS errors and unhandled promise rejections, OTLP HTTP/fetch/XHR network spans, click and rage-click logs, SPA navigation (`screen_load` / `screen_session`), Core Web Vitals (LCP, INP, CLS, FCP, FID, TTFB), and a tracer-based `interaction` flow built on top of OpenTelemetry JS. All signals carry `platform = 'web'` and a stable `pulse.type`.

## Path / Tech

- Path: `pulse-web-otel/`
- Language: TypeScript (strict), ESM + CJS bundles via tsup
- Runtime: OpenTelemetry JS Web (`sdk-trace-web`, `sdk-logs`, `sdk-metrics`, `instrumentation-fetch`, `instrumentation-xml-http-request`) + `web-vitals`
- Tests: Vitest (`src/__tests__/`), Playwright E2E (`examples/ecommerce-demo/e2e/`, `examples/nextjs-demo/e2e/`)
- Package: `@dreamhorizonorg/pulse-web` (v0.1.0-alpha.1)

## Build / Dev

```bash
cd pulse-web-otel
yarn install
yarn build                                  # tsup → dist/
yarn test                                   # Vitest
yarn workspace ecommerce-demo dev           # React demo :3002
yarn demo:docs                              # vanilla demo :3003
```

E2E:

```bash
yarn workspace ecommerce-demo test:e2e
yarn workspace nextjs-demo test:e2e
```

## Output

OTLP/HTTP to the Pulse Collector:

- Prod: `https://pulse-otel-collector.pulse-ux.com`
- Local dev (api key starts with `default-project`): `http://localhost:4318`
- Wire format selectable (JSON vs protobuf via `ExporterConfig.useProtobuf`); compression off; gRPC :4317 used by the Collector upstream

## Key files

| File | Role |
|---|---|
| `src/sdk.ts` | `Pulse` singleton: `init`, `shutdown`, `setUserId`, etc. |
| `src/config.ts` (+ `src/types/config.ts`) | `PulseWebConfig`, `InstrumentationKeys`, `PulseDataCollectionConsent`, endpoint resolution |
| `src/semconv.ts` | `PulseWebSemconv` — resource keys, attribute keys, `pulse.type` values, log bodies |
| `src/session.ts` | 3-tier session/installation storage, BFCache + clone-tab guards, inactivity rotation |
| `src/feature-gate.ts` | Remote `PulseSdkConfig` → per-instrumentation gate |
| `src/remote-config.ts` | `SdkConfigFetcher`, normalization of dashboard JSON |
| `src/instrumentation-registry.ts` | Discovers and installs instrumentations under gate |
| `src/resource.ts` | OTel Resource with 18 browser attrs |
| `src/before-send.ts` | `beforeSendData` hook resolver |
| `src/exporters.ts` | Trace / log / metric provider builders, OTLP transport, batch wiring |
| `src/instrumentations/*.ts` | clicks, errors, network, navigation, web-vitals, session, interaction |
| `src/integrations/{react,next,next-config}/` | React/Next.js helpers, source-map upload |

## Plan + existing specs

- Plan TOC: `docs/plans/pulse-web-otel/index.md`
- Existing per-feature SPECs (canonical, cite — do not duplicate):
  - `pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md`
  - `pulse-web-otel/docs/instrumentations/clicks/SPEC.md`
  - `pulse-web-otel/docs/instrumentations/errors/SPEC.md`
  - `pulse-web-otel/docs/instrumentations/network/SPEC.md`
  - `pulse-web-otel/docs/instrumentations/screen-signals/SPEC.md`
  - `pulse-web-otel/docs/instrumentations/web-vitals/SPEC.md`
  - `pulse-web-otel/docs/instrumentations/interactions/SPEC.md`
  - `pulse-web-otel/docs/instrumentations/react-integration/SPEC.md`
  - `pulse-web-otel/docs/instrumentations/nextjs-integration/SPEC.md`
  - `pulse-web-otel/docs/instrumentations/integration/SPEC.md`
- PRD: `pulse-web-otel/docs/prd/PRD.md`

## Data contract (summary)

Every signal carries `platform = 'web'` (resource) and a stable `pulse.type` (span / log attribute):

`session.start`, `session.end`, `device.crash`, `non_fatal`, `http` (`network.<status>` for HTTP spans), `app.click`, `web_vital`, `screen_load`, `screen_interactive`, `screen_session`.

See `src/semconv.ts` (`PulseWebSemconv.PulseType` / `.AttributeKey` / `.ResourceKey`) for the authoritative list.
