# @dreamhorizonorg/pulse-web

OpenTelemetry-based web SDK for Pulse RUM telemetry.

Captures:
- session lifecycle
- custom events
- non-fatal and crash signals
- network and browser instrumentation
- interaction spans from backend-provided interaction configs

## Install

```bash
yarn add @dreamhorizonorg/pulse-web
```

## Integrating Pulse Web SDK

### 1. Install

```bash
npm install @dreamhorizonorg/pulse-web
```

### 2. Wrap your app with PulseProvider

`PulseProvider` from `@dreamhorizonorg/pulse-web/react` does everything in one shot:
- calls `Pulse.init` on mount
- catches React render errors via the built-in `PulseErrorBoundary`
- exposes the SDK via context

**React (CRA / Vite):**

```tsx
import { PulseProvider } from '@dreamhorizonorg/pulse-web/react'
import { PulseDataCollectionConsent } from '@dreamhorizonorg/pulse-web'

const config = {
  apiKey: 'your-project-key',
  serviceName: 'my-app',
  dataCollectionState: PulseDataCollectionConsent.ALLOWED,
}

<PulseProvider config={config}>
  <App />
</PulseProvider>
```

**Next.js App Router:** the compiled SDK dist does not include `"use client"`, so `PulseProvider` cannot be imported directly from a Server Component. Create a thin wrapper first:

```tsx
// app/providers/PulseProvider.tsx
'use client'
import { PulseProvider as SDKPulseProvider } from '@dreamhorizonorg/pulse-web/react'

export function PulseProvider({ config, children }: { config: any; children: React.ReactNode }) {
  return <SDKPulseProvider config={config} shutdownOnUnmount={false}>{children}</SDKPulseProvider>
}
```

Then use the wrapper in `layout.tsx`:

```tsx
// app/layout.tsx
import { PulseProvider } from './providers/PulseProvider'
import { PulseDataCollectionConsent } from '@dreamhorizonorg/pulse-web'

const config = {
  apiKey: process.env.NEXT_PUBLIC_PULSE_API_KEY,
  serviceName: 'my-app',
  dataCollectionState: PulseDataCollectionConsent.ALLOWED,
}

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        <PulseProvider config={config}>
          {children}
        </PulseProvider>
      </body>
    </html>
  )
}
```

After mount, these signals auto-capture with zero extra work:

| Signal | Trigger |
|---|---|
| `session.start` / `session.end` | Tab open / close |
| `network.<status>` (e.g. `network.200`) | Every `fetch` / `XMLHttpRequest` — client span `pulse.type` from HTTP status, not the literal `http` |
| `app.click` | User clicks anywhere |
| `web_vital` | LCP, FID, CLS, TTFB, FCP, INP |
| `screen_load` | Navigation timing (incl. `tti` on initial load when available) |
| `device.crash` | Uncaught JS errors + React render errors (via built-in `PulseErrorBoundary`) |

### 3. Screen tracking (Next.js App Router only)

`useRouterTracking` from `@dreamhorizonorg/pulse-web/react` requires `react-router-dom` — it won't work in Next.js. Add this null-rendering component once inside `<PulseProvider>` in `layout.tsx`:

```tsx
// app/components/PulsePageView.tsx
'use client'
import { usePathname } from 'next/navigation'
import { useEffect } from 'react'
import { Pulse } from '@dreamhorizonorg/pulse-web'

export function PulsePageView() {
  const pathname = usePathname()
  useEffect(() => { Pulse.setScreenName(pathname) }, [pathname])
  return null
}
```

```tsx
// layout.tsx — add inside <PulseProvider>
<PulseProvider config={config}>
  <PulsePageView />
  {children}
</PulseProvider>
```

For React Router apps, import **`useRouterTracking`** or **`<PulseRouterEvents />`** from **`@dreamhorizonorg/pulse-web/react/router`** (not the bare `/react` entry — that path avoids a hard `react-router-dom` dependency for apps that do not use React Router):

```tsx
import { useRouterTracking } from '@dreamhorizonorg/pulse-web/react/router'
useRouterTracking() // inside a component rendered within <BrowserRouter>
```

**Naming note — `beforeSendData`:** RUM docs often say “`beforeSend`”. Pulse
uses the config key **`beforeSendData`** for parity with Android; inner
callbacks still use names like **`beforeSend`** / **`beforeSendSpan`**. See
[`docs/instrumentations/integration/SPEC.md`](docs/instrumentations/integration/SPEC.md) §5.9.

## Public API

- `Pulse.init(config)`
- `Pulse.shutdown()`
- `Pulse.isInitialized()`
- `Pulse.setScreenName(name)`
- `Pulse.trackEvent(name, attrs?)`
- `Pulse.reportException(error, attrs?)`
- `Pulse.reportDeviceCrash(error, attrs?)`
- `Pulse.trackNonFatal(name, attrs?)`

## Interaction config contract

Interaction configs are fetched from:
- **Local/dev** (API keys matching `default-project*_*`): `{collector→8080}/v1/interaction-configs/` with `X-API-KEY`
- **Prod** (any other API key): `https://pulse-otel-collector.pulse-ux.com/config/projects/{projectId}/interaction-config.json` (no `/v1/interaction-configs/` — search DevTools for `interaction-config.json`)

Remote `pulse-config.json` may set `features[].interaction.sessionSampleRate` < 1 for `pulse_web_js`; interaction **spans** stay gated, but the SDK still loads interaction configs when `instrumentations.interactions` is not `enabled: false`.

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

- **PRDs (planning):** [`docs/prd/README.md`](docs/prd/README.md) — feature PRDs under `docs/prd/`; `PRD.md` in that folder symlinks to the active Ralph PRD when applicable
- **Integration entry:** [`docs/instrumentations/integration/SPEC.md`](docs/instrumentations/integration/SPEC.md)
- **SDK core:** [`docs/sdk-core/SPEC.md`](docs/sdk-core/SPEC.md) — topic SPECs under `docs/sdk-core/<topic>/SPEC.md` (e.g. [`data-contract/SPEC.md`](docs/sdk-core/data-contract/SPEC.md), [`architecture-and-bootstrap/SPEC.md`](docs/sdk-core/architecture-and-bootstrap/SPEC.md)); **session:** [`docs/instrumentations/session/SPEC.md`](docs/instrumentations/session/SPEC.md)
- **Per-feature specs:** [`docs/instrumentations/`](docs/instrumentations/)
- **Publishing (maintainers):** [`docs/publishing/SPEC.md`](docs/publishing/SPEC.md) · [`docs/publishing/QUICKSTART.md`](docs/publishing/QUICKSTART.md) · [`docs/publishing/PUBLISHING.md`](docs/publishing/PUBLISHING.md)
