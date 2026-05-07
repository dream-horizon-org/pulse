# Pulse Web SDK — Integration Guide

> Persona for this doc: **a new app team** (React or Next.js) integrating Pulse for the first time. Goal: ship telemetry in under 15 minutes with no surprises.

Package: `@dreamhorizon/pulse-web` (subpath exports: `/react`, `/next`, `/next-config`).

---

## 0. TL;DR

| App type | What you install | Where you wire it | Optional extras |
|---|---|---|---|
| **React (CSR / Vite / CRA)** | `@dreamhorizon/pulse-web` | `<PulseProvider>` at app root + `<PulseRouterEvents />` *(or `useRouterTracking()` once)* under your router | `<PulseErrorBoundary>` (already inside `PulseProvider`) |
| **Next.js (App Router)** | `@dreamhorizon/pulse-web` | `<PulseProvider>` in a `"use client"` boundary inside `app/layout.tsx` + `<PulseRouterEvents />` | `instrumentation.ts` for SSR crashes; `withPulseConfig` in `next.config.js` for source maps |
| **Next.js (Pages Router)** | `@dreamhorizon/pulse-web` | `<PulseProvider>` in `pages/_app.tsx` + `useNextPagesRouterTracking()` once | same as App Router |
| **Vanilla SPA (no React)** | `@dreamhorizon/pulse-web` | `Pulse.init(...)` once on startup + `Pulse.setScreenName(pathname)` on route change | Example: `examples/web-sdk-docs` |

Everything else (sessions, errors, web vitals, network, clicks, rage clicks) is automatic once `PulseProvider` mounts.

---

## 1. Install

```bash
yarn add @dreamhorizon/pulse-web
# or
npm install @dreamhorizon/pulse-web
```

Peers (already in your app, but listed for clarity):

- `react >= 18`
- `react-router-dom >= 6` *(only if using `useRouterTracking`)*
- `next >= 14` *(only for the `/next` and `/next-config` subpaths)*

---

## 2. Get an API key

1. Log into the Pulse dashboard.
2. Create a project → copy the `<projectId>_<key>` API key.
3. **Local dev** keys (prefix `default-project_*` or `Test-*_*`) automatically point the SDK at `http://localhost:4318`. **Production** keys point to the hosted collector. You do **not** configure an endpoint URL.

Store it in:

- Vite: `VITE_PULSE_API_KEY`
- Next.js: `NEXT_PUBLIC_PULSE_API_KEY`

Public env prefixes are required because the SDK runs in the browser. Treat the API key as **public** — for ingestion only, no read access.

---

## 3. React app integration (CSR / Vite / CRA)

### 3.1 Minimal wiring

```tsx
// src/main.tsx  (or index.tsx)
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { PulseProvider, PulseRouterEvents } from "@dreamhorizon/pulse-web/react";
import { PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";
import App from "./App";

createRoot(document.getElementById("root")!).render(
  <PulseProvider
    config={{
      apiKey: import.meta.env.VITE_PULSE_API_KEY,
      serviceName: "my-react-app",
      serviceVersion: __APP_VERSION__,                       // optional
      dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    }}
  >
    <BrowserRouter>
      <PulseRouterEvents />
      <App />
    </BrowserRouter>
  </PulseProvider>
);
```

That's it. After this the SDK starts on mount, captures sessions, errors, web vitals, network, and clicks, and ships OTLP signals to the Pulse collector.

### 3.2 What `PulseProvider` does for you

- Calls `Pulse.init(config)` once on mount (StrictMode-safe).
- Wraps children in `<PulseErrorBoundary>` so React render errors become `device.crash` signals.
- By default **does not** call `Pulse.shutdown()` on unmount (`shutdownOnUnmount` defaults to `false`). Set `shutdownOnUnmount` when you want a full teardown on provider unmount (e.g. strict SPA tests).
- Exposes `usePulse()` to descendants.

### 3.3 Route tracking options (`PulseRouterEvents` or `useRouterTracking`)

For React Router, use whichever style your app prefers:

- Component form (recommended for consistency with Next docs):

```tsx
import { PulseRouterEvents } from "@dreamhorizon/pulse-web/react";

<BrowserRouter>
  <PulseRouterEvents includeSearch={false} />
  <App />
</BrowserRouter>;
```

- Hook form (equivalent behavior):

```tsx
import { useRouterTracking } from "@dreamhorizon/pulse-web/react";

function RouterTracking() {
  useRouterTracking();
  return null;
}
```

`PulseRouterEvents` is a thin wrapper around `useRouterTracking`.

- On every `react-router` location change, calls `Pulse.setScreenName(pathname)`.
- All subsequent signals carry that `screen.name`.
- Pass `{ includeSearch: true }` to also fire on `?query` changes, or `{ format: ({ pathname }) => mapToScreenName(pathname) }` to canonicalise (e.g. `/products/123` → `products/detail`).

---

## 4. Next.js integration

### 4.1 App Router (Next 13+ / 14 / 15)

**`app/pulse-provider.tsx`** — must be a client component:

```tsx
"use client";

import { PulseProvider, PulseRouterEvents } from "@dreamhorizon/pulse-web/next";
import { PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";
import { type ReactNode } from "react";

export function PulseClientProvider({ children }: { children: ReactNode }) {
  return (
    <PulseProvider
      config={{
        apiKey: process.env.NEXT_PUBLIC_PULSE_API_KEY!,
        serviceName: "my-nextjs-app",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
      }}
    >
      <PulseRouterEvents />
      {children}
    </PulseProvider>
  );
}
```

**`app/layout.tsx`**:

```tsx
import { PulseClientProvider } from "./pulse-provider";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <PulseClientProvider>{children}</PulseClientProvider>
      </body>
    </html>
  );
}
```

`<PulseRouterEvents />` wraps `useNextAppRouterTracking` in a `<Suspense>` boundary, so `useSearchParams()` will not opt the layout into client-side rendering.

### 4.2 Pages Router

**`pages/_app.tsx`**:

```tsx
import type { AppProps } from "next/app";
import { PulseProvider, useNextPagesRouterTracking } from "@dreamhorizon/pulse-web/next";
import { PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";

function RouterTracking() {
  useNextPagesRouterTracking();
  return null;
}

export default function MyApp({ Component, pageProps }: AppProps) {
  return (
    <PulseProvider
      config={{
        apiKey: process.env.NEXT_PUBLIC_PULSE_API_KEY!,
        serviceName: "my-nextjs-app",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
      }}
    >
      <RouterTracking />
      <Component {...pageProps} />
    </PulseProvider>
  );
}
```

### 4.3 SSR / RSC crashes — `instrumentation.ts`

```ts
// instrumentation.ts (project root)
import { createPulseInstrumentationHandler } from "@dreamhorizon/pulse-web/next";

export const onRequestError = createPulseInstrumentationHandler({
  apiKey: process.env.PULSE_API_KEY!,                        // server-side env, NOT NEXT_PUBLIC_*
  collectorEndpoint: "https://pulse-otel-collector.pulse-ux.com/v1/logs",
  serviceName: "my-nextjs-app",
});
```

Edge-safe: uses `fetch` only.

### 4.4 Source maps for production stack traces — `next.config.js`

```js
const { withPulseConfig } = require("@dreamhorizon/pulse-web/next-config");

module.exports = withPulseConfig(
  { /* your existing next config */ },
  {
    apiKey: process.env.PULSE_API_KEY,
    appVersion: process.env.npm_package_version,
  },
);
```

Effect:
- Sets `productionBrowserSourceMaps: true`.
- Hooks the client webpack build to upload `.js.map` files to Pulse.
- Deletes `.js.map` from public output after upload (`deleteAfterUpload: true` by default).
- No-ops in dev (`disabled` defaults to `NODE_ENV !== "production"`).

### 4.5 Vanilla SPA integration (manual routing)

For plain JavaScript apps without React/Next providers:

```ts
import { Pulse, PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";

Pulse.init({
  apiKey: import.meta.env.VITE_PULSE_API_KEY,
  serviceName: "my-vanilla-app",
  dataCollectionState: PulseDataCollectionConsent.ALLOWED,
  export: { format: "protobuf" },
});

Pulse.setScreenName(window.location.pathname);
```

When your router changes URL (History API / hash router), call `Pulse.setScreenName(nextPath)` after each navigation.

---

## 5. Identity, custom events, manual errors

Once initialised, use `Pulse` from anywhere — or `usePulse()` inside React:

```ts
import { Pulse } from "@dreamhorizon/pulse-web";

// Login
Pulse.setUserId("user-123");
Pulse.setUserProperties({ plan: "pro", cohort: "beta" });

// Logout
Pulse.clearUserIdentity();

// Custom event
Pulse.trackEvent("checkout.completed", { orderId: "o-42", revenue: 1299 });

// Manual non-fatal
Pulse.reportException(err, { route: "/checkout" });
```

`setUserId` automatically emits `pulse.user.session.start` / `pulse.user.session.end` on transitions, persists to `localStorage`, and stamps `user.id` on every subsequent signal.

---

## 6. Consent

Pulse will not emit anything unless `dataCollectionState === ALLOWED`.

```ts
import { PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";

// Banner not yet answered:
dataCollectionState: PulseDataCollectionConsent.PENDING

// User accepted:
dataCollectionState: PulseDataCollectionConsent.ALLOWED

// User rejected:
dataCollectionState: PulseDataCollectionConsent.DENIED
```

If consent flips at runtime, unmount and remount `PulseProvider` with the new value.

---

## 7. Common knobs you actually use

```ts
{
  apiKey: "...",
  dataCollectionState: PulseDataCollectionConsent.ALLOWED,

  serviceName: "my-app",
  serviceVersion: "2.4.1",
  globalAttributes: { tenant: "acme", region: "us-east" },

  // Disable specific instrumentations
  instrumentations: {
    network:   { enabled: true, captureQueryParams: false },
    clicks:    { enabled: true, rage: { enabled: true } },
    webVitals: { enabled: true },
    navigation:{ enabled: true },
    errors:    { enabled: true },
    session:   { enabled: true },
  },

  // Wire format
  export: { format: "protobuf" },                            // or "json" for DevTools-readable

  // SDK self-logging
  logLevel: PulseLogLevel.DEBUG,                             // dev-only

  // Privacy redaction at export time — generic hook receives OTLP batch items (`PulseExportSignal`: span | log | metrics bundle); typed hooks `beforeSendSpan` / `beforeSendLog` / `beforeSendMetric` are also available (exported types).
  beforeSendData: { /* beforeSend / beforeSendSpan / … */ },
}
```

---

## 8. Verifying the integration

1. Open DevTools → Network → filter on `/v1/`.
2. You should see POSTs to `/v1/traces`, `/v1/logs`, `/v1/metrics` shortly after page load.
3. In the Pulse dashboard, a `session.start` signal should appear within ~30s.
4. Click around — you should see `app.click` and `screen_load` rows under your `serviceName`.

If nothing shows up: check `logLevel: PulseLogLevel.DEBUG` and look at the console for `rum.sdk.init.*` messages.

---

## 9. Troubleshooting one-liners

| Symptom | Cause |
|---|---|
| `usePulse() must be called inside <PulseProvider>` | Hook is being used outside the provider tree. |
| No signals in DevTools | Consent is `PENDING`/`DENIED`, or running in SSR (no `window`). |
| Source maps not deobfuscated | `withPulseConfig` not wired or `appVersion` mismatch with runtime `serviceVersion`. |
| Duplicate route changes in StrictMode | You're using `useRouterTracking` correctly — it's idempotent; you should not see them in the dashboard. |
| `next/navigation` errors during build | `<PulseRouterEvents />` already handles this; do not call `useNextAppRouterTracking` outside a `<Suspense>` boundary yourself. |
