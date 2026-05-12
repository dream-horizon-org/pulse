# pulse-web-otel — plan index

This plan documents the Pulse Web SDK (`pulse-web-otel/`) sub-component by sub-component. Each file follows the standard 9-section handbook (purpose, source, public surface, internal design, deps, data contract, tests, history, rebuild recipe). The canonical, line-by-line SPECs already live in `pulse-web-otel/docs/instrumentations/*/SPEC.md`; the files here summarise and cross-link.

## Reading order

1. Brief: `../../components/pulse-web-otel.md`
2. Core lifecycle: `core/sdk-bootstrap.md` → `core/config.md` → `core/consent.md` → `core/session.md` → `core/resource-attrs.md` → `core/semconv.md`
3. Remote control: `core/feature-gate.md` → `core/remote-config.md`
4. Instrumentations: `instrumentations/click.md` → `error.md` → `network.md` → `navigation.md` → `screen-signals.md` → `web-vitals.md` → `interaction.md`
5. Pipeline: `pipeline/processors.md` → `pipeline/exporters.md` → `pipeline/sampling.md` → `pipeline/persistence.md` → `pipeline/before-send.md`
6. Framework integrations: `integrations/react.md` → `integrations/nextjs.md` → `integrations/vue.md`

## Files

### core/

| File | Topic |
|---|---|
| `core/sdk-bootstrap.md` | `Pulse.init()` sequence, singleton, shutdown |
| `core/config.md` | `PulseWebConfig`, validation, endpoint resolution |
| `core/consent.md` | `PulseDataCollectionConsent` and `isDataCollectionAllowed` |
| `core/session.md` | Session + installation lifecycle, BFCache, clone-tab |
| `core/resource-attrs.md` | Resource builder (18 browser attrs) |
| `core/semconv.md` | `PulseWebSemconv` keys + `pulse.type` enum |
| `core/feature-gate.md` | Local-config × remote gate decision |
| `core/remote-config.md` | `SdkConfigFetcher`, normalization, cache |

### instrumentations/

| File | `pulse.type` |
|---|---|
| `instrumentations/click.md` | `app.click` |
| `instrumentations/error.md` | `device.crash`, `non_fatal` |
| `instrumentations/network.md` | `http` (`network.<status>`) |
| `instrumentations/navigation.md` | `screen_load`, `screen_session` |
| `instrumentations/screen-signals.md` | `screen_load`, `screen_interactive`, `screen_session` |
| `instrumentations/web-vitals.md` | `web_vital` |
| `instrumentations/interaction.md` | `interaction` |

### pipeline/

| File | Topic |
|---|---|
| `pipeline/processors.md` | Global attrs + signal-filter processors |
| `pipeline/exporters.md` | Trace/log/metric providers, OTLP transports |
| `pipeline/sampling.md` | Export-time session sampling gate |
| `pipeline/persistence.md` | IndexedDB disk buffer + replay |
| `pipeline/before-send.md` | `beforeSendData` hook resolver |

### integrations/

| File | Topic |
|---|---|
| `integrations/react.md` | `PulseProvider`, `PulseErrorBoundary`, router tracking |
| `integrations/nextjs.md` | App / Pages router hooks, `withPulseConfig`, source-map upload |
| `integrations/vue.md` | Vue integration status |
