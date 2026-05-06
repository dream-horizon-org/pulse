# @dreamhorizon/pulse-web

OpenTelemetry-based web SDK for Pulse RUM telemetry.

Captures:
- session lifecycle
- custom events
- non-fatal and crash signals
- network and browser instrumentation
- interaction spans from backend-provided interaction configs

## Install

```bash
yarn add @dreamhorizon/pulse-web
```

## Quickstart

```ts
import { PulseWeb, PulseDataCollectionConsent, PulseLogLevel } from "@dreamhorizon/pulse-web";

PulseWeb.start({
  apiKey: "your-project_api-key",
  serviceName: "web-app",
  dataCollectionState: PulseDataCollectionConsent.ALLOWED,
  logLevel: PulseLogLevel.NONE,
});

PulseWeb.setScreenName("Home");
PulseWeb.trackEvent("cta_click", { location: "hero" });
```

## Public API

- `PulseWeb.start(config)`
- `PulseWeb.shutdown()`
- `PulseWeb.isInitialized()`
- `PulseWeb.setScreenName(name)`
- `PulseWeb.trackEvent(name, attrs?)`
- `PulseWeb.reportException(error, attrs?)`
- `PulseWeb.reportDeviceCrash(error, attrs?)`
- `PulseWeb.trackNonFatal(name, attrs?)`

## Interaction config contract

Interaction configs are fetched from:
- local/dev: `http://localhost:8080/v1/interaction-configs/`
- prod: Pulse config endpoint

Web runtime now uses backend/Android wire shape directly:
- `id: number`
- `description: string`
- event props use `name` (not `key`)
- operators: `EQUALS | NOTEQUALS | CONTAINS | NOTCONTAINS | STARTSWITH | ENDSWITH`
- `globalBlacklistedEvents` is an array of event objects

## Local development

```bash
# Node >= 18.13
corepack enable
yarn install

# Build SDK
yarn build

# Run demo apps
yarn demo          # React ecommerce-demo → localhost:3002
yarn demo:docs     # Vanilla web-sdk-docs → localhost:3003

# Typecheck + unit tests
yarn lint
yarn test:run
```

## E2E (demo)

```bash
# One-time browser install
cd examples/ecommerce-demo
yarn playwright install --with-deps chromium firefox webkit

# From SDK root
cd ../..
yarn workspace ecommerce-demo e2e:m2-interactions
yarn workspace ecommerce-demo e2e:web-sdk-gates
```

## Useful docs

- Web Vitals (planning): `web-sdk-plan/v2-web-vitals/README.md`
- milestone criteria: `web-sdk-plan/v1/MILESTONES.md`
- interactions verification: `web-sdk-plan/v1/M2-INTERACTIONS-EXIT-VERIFICATION.md`
- interactions coverage matrix: `web-sdk-plan/v1/WEB-SDK Interactions test coverage (M2).csv`
- demo lifecycle notes: `examples/ecommerce-demo/MANUAL-PULSEWEB-LIFECYCLE.md`
