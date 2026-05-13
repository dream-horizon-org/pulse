# Host Application Integration — SPEC.md

Package: `@dreamhorizonorg/pulse-web` (`pulse-web-otel/package.json`)  
File: `pulse-web-otel/docs/instrumentations/integration/SPEC.md`

---

## 1. Goal

Single **entry-point guide** for shipping Pulse Web in a browser application: install the npm package, choose the correct **`exports` subpath**, call **`Pulse.init`** with required consent + API key, optionally wrap React/Next layers, and deep-link to **`sdk-core`**, **`react-integration`**, and **`nextjs-integration`** SPECs for implementation detail.

---

## 2. Assumptions

- **Browser bundle:** Default `@dreamhorizonorg/pulse-web` targets browsers (`window` present). SSR-only contexts skip browser initialization.
- **Consent-first:** Without **`PulseDataCollectionConsent.ALLOWED`**, the SDK installs **no** collectors — legal baseline.

---

## 3. Requirements

**R1 — Install:** Add `@dreamhorizonorg/pulse-web` from npm (workspace / tarball per internal publishing docs).

**R2 — Choose entry:**

| Need | Import |
|---|---|
| Vanilla / framework-agnostic | `@dreamhorizonorg/pulse-web` → `Pulse.init` |
| React root provider | `@dreamhorizonorg/pulse-web/react` |
| React Router v6 screen names | `@dreamhorizonorg/pulse-web/react/router` |
| Next.js client helpers | `@dreamhorizonorg/pulse-web/next` |
| `next.config` wrapper + maps | `@dreamhorizonorg/pulse-web/next-config` |

**R3 — Minimal config:** Provide **`apiKey`** + **`dataCollectionState`** — see **`sdk-core`** [`config-and-consent/SPEC.md`](../../sdk-core/config-and-consent/SPEC.md) and [`public-api/SPEC.md`](../../sdk-core/public-api/SPEC.md) for full `PulseWebConfig` / surface.

**R4 — Shutdown:** Long-lived SPAs usually omit teardown; tests may call **`Pulse.shutdown()`** via provider prop — see **`react-integration`** SPEC.

**R5 — Domain allowlist (ops):** After integrating, the host application's origin must be added to the Pulse S3 CORS allowlist (`pulse-otel-config` bucket) before the SDK can fetch remote config in a browser. Without this, `OPTIONS` preflight to `/config/*` returns 403 and the SDK cannot load feature gates or remote sampling config.

```bash
# Run once per new customer domain — update existing rule, do not replace
aws s3api put-bucket-cors \
  --bucket pulse-otel-config \
  --cors-configuration '{
    "CORSRules": [{
      "AllowedOrigins": [
        "https://*.pulse-ux.com",
        "https://*.amplifyapp.com",
        "https://<customer-domain>",
        "http://localhost:3000"
      ],
      "AllowedMethods": ["GET", "HEAD"],
      "AllowedHeaders": ["*"],
      "MaxAgeSeconds": 7200
    }]
  }'
```

> Note: this is a manual step until project registration in the Pulse backend automates it.

---

## 4. Architectural Design

```
npm install @dreamhorizonorg/pulse-web

Vanilla
  import { Pulse } from "@dreamhorizonorg/pulse-web";
  await Pulse.init({ apiKey, dataCollectionState, ... });

React
  import { PulseProvider } from "@dreamhorizonorg/pulse-web/react";
  <PulseProvider config={...}>{app}</PulseProvider>

React Router
  import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";

Next.js (client)
  import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/next";
  // App Router: wraps useNextAppRouterTracking

Next.js (build)
  const { withPulseConfig } = require("@dreamhorizonorg/pulse-web/next-config");
```

---

## 5. LLD

### 5.1 `package.json` exports (truth)

See **`pulse-web-otel/package.json`** → **`exports`**. Canonical keys:

- **`"."`** — core SDK (`Pulse`, instrumentations via init).
- **`"./react"`** — `PulseProvider`, `usePulse`, `PulseErrorBoundary`.
- **`"./react/router"`** — `useRouterTracking`, `PulseRouterEvents` (peer: `react-router-dom`).
- **`"./next"`** — Next client/server helpers (`PulseRouterEvents`, App/Pages hooks, `createPulseInstrumentationHandler`).
- **`"./next-config"`** — `withPulseConfig`, source-map upload utilities.

Published **`types`** + **`import`** / **`require`** pairs resolve to `dist/*`.

### 5.2 `Pulse.init` contract

- **Required:** `apiKey`, `dataCollectionState`.
- **Async:** returns **`Promise<void>`** — await before relying on telemetry (`Pulse.whenReady()`).
- **Singleton:** double init no-op — details in **`sdk-core`** [`architecture-and-bootstrap/SPEC.md`](../../sdk-core/architecture-and-bootstrap/SPEC.md).

### 5.3 Consent + gates

- **`dataCollectionState`:** `ALLOWED` \| `DENIED` \| `PENDING` — only **`ALLOWED`** enables collectors.
- **Remote config / feature gates:** fetched post-init — **`sdk-core`** [`remote-config-features-and-sampling/SPEC.md`](../../sdk-core/remote-config-features-and-sampling/SPEC.md).

### 5.4 React (`PulseProvider`)

- Wrap the tree once at root — **`react-integration`** SPEC §5.

### 5.5 Next.js

- **Runtime:** `@dreamhorizonorg/pulse-web/next` — **`nextjs-integration`** SPEC §5.
- **Build:** `@dreamhorizonorg/pulse-web/next-config` — source maps §5.3–5.4 there.

### 5.6 Platform CORS configuration

The SDK fetches remote config from `pulse-otel-config` S3 via CloudFront (`/config/*`). The bucket is private (OAC) with `AllowedOrigins` scoped to known domains. A new integration requires the host app origin to be added to the CORS rule — see **R5** above. Failure symptom: `OPTIONS /config/projects/<id>/pulse-config.json` returns 403; SDK falls back to defaults silently.

### 5.7 SSR / Node `instrumentation.ts`

- Server **`onRequestError`** helper ships logs separately — does **not** replace browser **`Pulse.init`** for RUM.

### 5.7 Developer ergonomics / API critique

**Canonical punch list:** [`pulse-web-otel/docs/sdk-core/known-gaps-and-open-questions/SPEC.md`](../../sdk-core/known-gaps-and-open-questions/SPEC.md) (P0/P1/P2 naming and surface-area gaps). This integration guide intentionally **does not** duplicate that list.

---

## 6. Test Coverage

Integration is validated indirectly via:

- Core lifecycle tests — **`sdk-core`** [`test-coverage/SPEC.md`](../../sdk-core/test-coverage/SPEC.md).
- React provider/router tests — **`react-integration`** SPEC §6.
- Next hooks/config tests — **`nextjs-integration`** SPEC §6.

---

## 7. Known Bugs & Gaps

### P0:

Follow **`sdk-core`** SPEC §7 — **P0** items affect emitted telemetry globally.

### Other gaps

- Workspace demos may alias the package name — external CRA/Vite apps must use published **`exports`** as-is.

---

## 8. Redundancy & Cleanup Notes

Legacy planning **`web-sdk-plan/INTEGRATION.md`** had **no** standalone file in-repo at consolidation time; scattered integration guidance now lives here plus framework SPECs. **`agent-runtime/`** notebooks were removed separately per PRD (non-instrumentation).

---

## 9. Open Questions

1. Publish scoped README excerpt mirroring §5.1 for npmjs.com landing?
