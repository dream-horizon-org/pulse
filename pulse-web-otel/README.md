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

## Integrating Pulse Web SDK

### 1. Install

```bash
npm install @dreamhorizon/pulse-web
```

### 2. Wrap your app with PulseProvider

`PulseProvider` from `@dreamhorizon/pulse-web/react` does everything in one shot:
- calls `PulseWeb.start` on mount
- catches React render errors via the built-in `PulseErrorBoundary`
- exposes the SDK via context

**React (CRA / Vite):**

```tsx
import { PulseProvider } from '@dreamhorizon/pulse-web/react'
import { PulseDataCollectionConsent } from '@dreamhorizon/pulse-web'

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
import { PulseProvider as SDKPulseProvider } from '@dreamhorizon/pulse-web/react'

export function PulseProvider({ config, children }: { config: any; children: React.ReactNode }) {
  return <SDKPulseProvider config={config} shutdownOnUnmount={false}>{children}</SDKPulseProvider>
}
```

Then use the wrapper in `layout.tsx`:

```tsx
// app/layout.tsx
import { PulseProvider } from './providers/PulseProvider'
import { PulseDataCollectionConsent } from '@dreamhorizon/pulse-web'

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
| `http` | Every `fetch` / `XMLHttpRequest` |
| `app.click` | User clicks anywhere |
| `web_vital` | LCP, FID, CLS, TTFB, FCP, INP |
| `screen_load` / `screen_interactive` | Navigation timing |
| `device.crash` | Uncaught JS errors + React render errors (via built-in `PulseErrorBoundary`) |

### 3. Screen tracking (Next.js App Router only)

`useRouterTracking` from `@dreamhorizon/pulse-web/react` requires `react-router-dom` — it won't work in Next.js. Add this null-rendering component once inside `<PulseProvider>` in `layout.tsx`:

```tsx
// app/components/PulsePageView.tsx
'use client'
import { usePathname } from 'next/navigation'
import { useEffect } from 'react'
import { PulseWeb } from '@dreamhorizon/pulse-web'

export function PulsePageView() {
  const pathname = usePathname()
  useEffect(() => { PulseWeb.setScreenName(pathname) }, [pathname])
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

For React Router apps, use the built-in hook instead — no extra component needed:

```tsx
import { useRouterTracking } from '@dreamhorizon/pulse-web/react'
useRouterTracking() // inside a component rendered within <BrowserRouter>
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

# Run demo app (examples/ecommerce-demo) on localhost:3002
yarn demo

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

- milestone criteria: `web-sdk-plan/v1/MILESTONES.md`
- interactions verification: `web-sdk-plan/v1/M2-INTERACTIONS-EXIT-VERIFICATION.md`
- interactions coverage matrix: `web-sdk-plan/v1/WEB-SDK Interactions test coverage (M2).csv`
- demo lifecycle notes: `examples/ecommerce-demo/MANUAL-PULSEWEB-LIFECYCLE.md`
