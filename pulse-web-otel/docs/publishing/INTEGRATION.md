# Pulse Web SDK — Integration Guide

> Goal: add Pulse to your app and start seeing signals in the dashboard.

Package: `@dreamhorizonorg/pulse-web`

---

## 1. Install

```bash
npm install @dreamhorizonorg/pulse-web
# or
yarn add @dreamhorizonorg/pulse-web
```

---

## 2. Get an API key

1. Log into the Pulse dashboard → create a project → copy the API key (`<projectId>_<key>`).
2. Store it as an environment variable:
   - Vite: `VITE_PULSE_API_KEY`
   - Next.js: `NEXT_PUBLIC_PULSE_API_KEY`
   - Other bundlers: use whichever public env prefix your bundler requires

> **Local dev:** keys prefixed `default-project_*` automatically point the SDK at `http://localhost:4318`. All other keys point to the hosted collector. No endpoint URL needed — but you can override via `endpoint` in config (e.g. self-hosted collector).

---

## 3. What you get for free after init

Once the SDK starts, these signals fire automatically with no extra code:

| Signal | What it captures |
| --- | --- |
| `session.start` / `session.end` | Session lifecycle |
| `screen_load` | Page / route load timing |
| `web_vital` | LCP, CLS, INP, TTFB, FCP |
| `app.click` | Every click + rage click detection |
| `network.<status>` (e.g. `network.200`) | All `fetch` / XHR — client span `pulse.type` is `network.<HTTP status>` (Android parity), not the literal `http` |
| `non_fatal` | Unhandled JS errors + promise rejections |
| `device.crash` | React render errors (React / Next.js only — via `PulseErrorBoundary`) |

### 3.1 Web Vitals rating tuples (optional)

The package root re-exports **`LCPThresholds`**, **`INPThresholds`**, **`CLSThresholds`**, **`FCPThresholds`**, and **`TTFBThresholds`** from the pinned `web-vitals` major (numeric tuples aligned with **`Metric.rating`** / CrUX buckets). Use them for custom gauges without adding a second `web-vitals` dependency:

```ts
import { LCPThresholds, INPThresholds } from "@dreamhorizonorg/pulse-web";
// e.g. LCPThresholds → [good vs needs-improvement ms, needs-improvement vs poor ms]
```

---

## 4. React (CSR / Vite / CRA)

```tsx
// src/main.tsx
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { PulseProvider } from "@dreamhorizonorg/pulse-web/react";
import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";
import { PulseDataCollectionConsent } from "@dreamhorizonorg/pulse-web";
import App from "./App";

createRoot(document.getElementById("root")!).render(
  <PulseProvider
    config={{
      apiKey: import.meta.env.VITE_PULSE_API_KEY,
      serviceName: "my-react-app",
      dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    }}
  >
    <BrowserRouter>
      <PulseRouterEvents />  {/* calls Pulse.setScreenName on every route change */}
      <App />
    </BrowserRouter>
  </PulseProvider>,
);
```

**Notes:**

- `PulseProvider` calls `Pulse.init()` once on mount (StrictMode-safe) and wraps children in `PulseErrorBoundary`.
- **`shutdownOnUnmount`** defaults **`false`** — the SDK stays alive for the full page when the provider unmounts (e.g. route-level wrappers). Set **`true`** only if you intentionally want `Pulse.shutdown()` when the last provider unmounts (common in tests).
- `PulseRouterEvents` is exported from **`@dreamhorizonorg/pulse-web/react/router`** (and re-exported from **`@dreamhorizonorg/pulse-web/next`** for App Router). It is **not** on the bare **`@dreamhorizonorg/pulse-web/react`** entry so apps without React Router never pull `react-router-dom`.
- If you prefer a hook: `useRouterTracking()` from `@dreamhorizonorg/pulse-web/react/router` is equivalent to rendering `<PulseRouterEvents />`.

---

## 5. Next.js — App Router (Next 13+ / 14 / 15)

**Step 1 — client provider** (`app/pulse-provider.tsx`):

```tsx
"use client";

import { PulseProvider, PulseRouterEvents } from "@dreamhorizonorg/pulse-web/next";
import { PulseDataCollectionConsent } from "@dreamhorizonorg/pulse-web";

export function PulseClientProvider({ children }: { children: React.ReactNode }) {
  // `shutdownOnUnmount` defaults false — keeps Pulse running for the full page when this client subtree unmounts.
  return (
    <PulseProvider
      config={{
        apiKey: process.env.NEXT_PUBLIC_PULSE_API_KEY!,
        serviceName: "my-nextjs-app",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
      }}
    >
      <PulseRouterEvents />  {/* wrapped in Suspense — safe in root layout */}
      {children}
    </PulseProvider>
  );
}
```

**Step 2 — mount in layout** (`app/layout.tsx`):

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

**Step 3 (optional) — capture SSR / server errors** (`instrumentation.ts` at project root):

```ts
import { createPulseInstrumentationHandler } from "@dreamhorizonorg/pulse-web/next";

export const onRequestError = createPulseInstrumentationHandler({
  apiKey: process.env.PULSE_API_KEY!,   // server-side env — NOT NEXT_PUBLIC_*
  collectorEndpoint: "https://pulse-otel-collector.pulse-ux.com/v1/logs",
  serviceName: "my-nextjs-app",
});
```

**Step 4 (optional) — deobfuscated stack traces in production** (`next.config.js`):

```js
const { withPulseConfig } = require("@dreamhorizonorg/pulse-web/next-config");

module.exports = withPulseConfig(
  { /* your existing next config */ },
  {
    apiKey: process.env.PULSE_API_KEY,
    appVersion: process.env.npm_package_version,
  },
);
```

This uploads source maps to Pulse after each production build and removes them from public output. No-ops in dev.

---

## 6. Next.js — Pages Router

```tsx
// pages/_app.tsx
import type { AppProps } from "next/app";
import { PulseProvider, useNextPagesRouterTracking } from "@dreamhorizonorg/pulse-web/next";
import { PulseDataCollectionConsent } from "@dreamhorizonorg/pulse-web";

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

SSR crash capture and source maps — same as App Router (steps 3 & 4 above).

---

## 7. Vue / Svelte / Angular / Vanilla JS

No framework-specific integration exists. Use `Pulse.init()` directly — all auto-capture works the same.

```ts
import { Pulse, PulseDataCollectionConsent } from "@dreamhorizonorg/pulse-web";

// Call once on app startup — guard against SSR
if (typeof window !== "undefined") {
  Pulse.init({
    apiKey: import.meta.env.VITE_PULSE_API_KEY,
    serviceName: "my-app",
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
  });

  Pulse.setScreenName(window.location.pathname);
}
```

Wire screen tracking into your router's navigation hook:

```ts
// Vue Router
router.afterEach((to) => Pulse.setScreenName(to.path));

// Angular Router
router.events
  .pipe(filter(e => e instanceof NavigationEnd))
  .subscribe(e => Pulse.setScreenName(e.urlAfterRedirects));

// SvelteKit
afterNavigate(({ to }) => Pulse.setScreenName(to?.url.pathname ?? "/"));
```

Wire fatal errors into your framework's error handler:

```ts
// Vue
app.config.errorHandler = (err) => Pulse.reportDeviceCrash(err);
```

---

## 8. Verify the integration

1. Open DevTools → Network → filter `/v1/`.
2. You should see POSTs to `/v1/traces`, `/v1/logs`, `/v1/metrics` shortly after page load.
3. In the Pulse dashboard a `session.start` signal should appear within ~30s.

If nothing shows up: add `logLevel: PulseLogLevel.DEBUG` to your config and check the console for `rum.sdk.init.*` messages.
