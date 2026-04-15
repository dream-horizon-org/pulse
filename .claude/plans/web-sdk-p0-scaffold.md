# P0 — Scaffold + Ecommerce Demo Harness

## Context
`pulse-web-otel/` does not exist yet. This plan creates the entire directory from scratch: the SDK package shell (stubs only — no real logic yet) and a React+Vite ecommerce demo app that will serve as the manual verification harness for every milestone. The demo exercises all the routes and actions that instrumentations will later instrument.

## Prerequisites
- None. This is the starting point.
- **Node.js ≥ 18.13.0** required (`crypto.randomUUID()` + `CompressionStream` used in M1)
- **Yarn 4.x** (Berry) — `npm install -g yarn@4` or `corepack enable && corepack prepare yarn@4.x.x --activate`

## Spec Docs to Read First
1. `pulse-web-otel/web-sdk-plan/WEB-SDK-AGENT-CONTEXT.md` — file map and package identity
2. `pulse-web-otel/web-sdk-plan/v1/01-foundation/scaffold.md` — full `PulseWebConfig` interface + tooling config
3. `pulse-web-otel/web-sdk-plan/v1/00-setup/dependency-versions.md` — **pinned package versions to use in package.json**

## Files to Create

### SDK Package Root (`pulse-web-otel/`)

| File | What it contains |
|---|---|
| `package.json` | `name: @dreamhorizon/pulse-web`, `version: 0.1.0-alpha.1`, `type: module`, exports map, `workspaces: ["examples/*"]`, `packageManager: yarn@4.x`; use exact versions from `pulse-web-otel/web-sdk-plan/v1/00-setup/dependency-versions.md` |
| `tsconfig.json` | `strict: true`, `moduleResolution: bundler`, `target: ES2020`, `lib: [ES2020, DOM]` |
| `tsup.config.ts` | `entry: src/index.ts`, `format: [esm, cjs]`, `dts: true`, `clean: true` |
| `vitest.config.ts` | `environment: jsdom`, `globals: true` |
| `.yarnrc.yml` | `nodeLinker: node-modules` |
| `README.md` | Dev loop instructions, env vars, link to `pulse-web-otel/web-sdk-plan/v1/MILESTONES.md` |

### SDK Source Stubs (`pulse-web-otel/src/`)

Each file exports a typed stub — just enough for the demo to import and TypeScript to compile.

| File | Stub content |
|---|---|
| `src/index.ts` | `export { PulseWeb } from './sdk'; export type { PulseWebConfig } from './config'; export { PulseDataCollectionConsent } from './config';` |
| `src/config.ts` | Full `PulseWebConfig` interface + `PulseDataCollectionConsent` enum (exact types from scaffold.md) |
| `src/sdk.ts` | `export const PulseWeb = { start(_c: PulseWebConfig) {}, shutdown() {}, setScreenName(_n: string) {}, trackEvent(_n: string, _a?: Record<string,unknown>) {}, reportException(_e: unknown) {} };` |
| `src/session.ts` | `export {}` |
| `src/resource.ts` | `export {}` |
| `src/exporters.ts` | `export {}` |
| `src/consent.ts` | `export {}` |
| `src/remote-config.ts` | `export {}` |
| `src/feature-gate.ts` | `export {}` |
| `src/instrumentation-registry.ts` | `export {}` |
| `src/version.ts` | `export const SDK_VERSION = '__SDK_VERSION__';` |
| `src/utils/ua-parser.ts` | `export {}` |
| `src/utils/compression.ts` | `export {}` |

Create empty dirs (with `.gitkeep`): `src/instrumentations/`, `src/processors/`, `src/persistence/`, `src/integrations/`, `src/__tests__/`

### Ecommerce Demo (`pulse-web-otel/examples/ecommerce-demo/`)

| File | What it contains |
|---|---|
| `package.json` | `name: ecommerce-demo`, `"@dreamhorizon/pulse-web": "workspace:*"`, use exact demo dep versions from `pulse-web-otel/web-sdk-plan/v1/00-setup/dependency-versions.md` (`react@^18.3`, `react-dom@^18.3`, `react-router-dom@^6.26`, `vite@^5.4`, `@vitejs/plugin-react@^4.3`) |
| `vite.config.ts` | `server: { port: 3002 }`, `resolve.alias` pointing to SDK src for HMR |
| `tsconfig.json` | Extends root, `jsx: react-jsx` |
| `index.html` | Standard Vite HTML shell, `<div id="root">` |
| `src/main.tsx` | `ReactDOM.createRoot(...).render(<App />)` |
| `src/App.tsx` | React Router `<BrowserRouter>` with all routes; imports stub `PulseWeb.start()` from `@dreamhorizon/pulse-web` (reads `import.meta.env.VITE_PULSE_*`); calls `PulseWeb.start()` in `useEffect` |
| `src/routes/Home.tsx` | Landing page, "Shop Now" link |
| `src/routes/Products.tsx` | Product grid — calls `useProducts()` which fetches `/api/products.json` |
| `src/routes/ProductDetail.tsx` | Single product — fetches `/api/product-detail.json?id=:id` |
| `src/routes/Cart.tsx` | Cart list — add/remove buttons (click targets) |
| `src/routes/Checkout.tsx` | 3-step checkout; each step calls `PulseWeb.trackEvent('checkout_step_N')` |
| `src/routes/ErrorDemo.tsx` | Button to `throw new Error('demo crash')` + button for `PulseWeb.reportException(...)` |
| `src/hooks/useProducts.ts` | `useSWR('/api/products.json', fetch)` or plain `useEffect` fetch |
| `src/hooks/useCart.ts` | `useState` cart array with add/remove |
| `src/components/ProductCard.tsx` | Card with image, title, "Add to Cart" button — primary click target |
| `src/components/RageClickButton.tsx` | Button labeled "Click fast!" — for rage click testing |
| `public/api/products.json` | Array of 8 mock products `{ id, name, price, category, imageUrl }` |
| `public/api/product-detail.json` | Single product detail `{ id, name, price, description, specs[] }` |
| `public/interaction-config.json` | Stub `[]` — will be populated in M2 |
| `.env.example` | `VITE_PULSE_ENDPOINT_BASE_URL=`, `VITE_PULSE_API_KEY=`, `VITE_PULSE_SERVICE_NAME=ecommerce-demo` |

## Key Implementation Notes

- `package.json` exports map must have both `import` and `require` entries for `.` so TypeScript resolves correctly:
  ```json
  "exports": {
    ".": {
      "import": "./dist/index.js",
      "require": "./dist/index.cjs",
      "types": "./dist/index.d.ts"
    }
  }
  ```
- SDK root `package.json` scripts:
  ```json
  "scripts": {
    "build":      "tsup",
    "test":       "vitest",
    "lint":       "tsc --noEmit",
    "size-limit": "size-limit"
  }
  ```
- Demo `examples/ecommerce-demo/package.json` scripts:
  ```json
  "scripts": {
    "dev":  "vite",
    "build": "tsc && vite build",
    "e2e":  "playwright test --config e2e/playwright.config.ts"
  }
  ```
  And add `"@playwright/test": "^1.47.0"` to its `devDependencies`.
- Demo's `vite.config.ts` should add `optimizeDeps.include: ['@dreamhorizon/pulse-web']` so Vite pre-bundles the workspace package
- `App.tsx` must wrap routes in `<Suspense>` for lazy loading (React Router v6)
- `ErrorDemo.tsx` must wrap the throw in a class `ErrorBoundary` component (hook-based boundaries don't exist) — mark it `// TODO: replace with PulseErrorBoundary in M2`

### E2E Test Hooks in Demo Components
The Playwright E2E tests use `data-testid` attributes to locate elements. Add these to the demo:

| Component | `data-testid` | Used by |
|---|---|---|
| `Checkout.tsx` step 1 "Next" button | `checkout-step-1-next` | m2.spec.ts |
| `Checkout.tsx` step 2 "Next" button | `checkout-step-2-next` | m2.spec.ts |
| `Checkout.tsx` step 3 "Confirm" button | `checkout-step-3-confirm` | m2.spec.ts |
| `ErrorDemo.tsx` uncaught throw button | `throw-uncaught` | m3.spec.ts |
| `ErrorDemo.tsx` Promise.reject button | `throw-promise` | m3.spec.ts |
| `ErrorDemo.tsx` React render throw button | `throw-render-error` | m2.spec.ts + m3.spec.ts |
| `ProductCard.tsx` root element | `product-card` | m3.spec.ts |
| `RageClickButton.tsx` button | `rage-click-button` | m3.spec.ts |

### App.tsx E2E hooks
```typescript
// In App.tsx — expose PulseWeb on window for shutdown test + read test env vars
useEffect(() => {
  PulseWeb.start({
    endpointBaseUrl: import.meta.env.VITE_PULSE_ENDPOINT_BASE_URL,
    apiKey: import.meta.env.VITE_PULSE_API_KEY,
    serviceName: import.meta.env.VITE_PULSE_SERVICE_NAME,
    export: {
      compression: (import.meta.env.VITE_PULSE_COMPRESSION as 'gzip' | 'none') ?? 'gzip',
      batch: {
        scheduledDelayMillis: import.meta.env.VITE_PULSE_BATCH_DELAY_MS
          ? Number(import.meta.env.VITE_PULSE_BATCH_DELAY_MS)
          : 5000,
      },
    },
  });
  // Expose for E2E shutdown test
  (window as unknown as Record<string, unknown>).PulseWeb = PulseWeb;
}, []);
```

### Consent query param (for consent E2E test)
In App.tsx, read `?pulse_consent=denied` before calling `PulseWeb.start()`:
```typescript
const consent = new URLSearchParams(window.location.search).get('pulse_consent');
// Pass to PulseWeb.start() if consent === 'denied' → PulseDataCollectionConsent.DENIED
```

## Done Criteria
- [ ] `cd pulse-web-otel && yarn install` completes with no errors
- [ ] `yarn build` produces `dist/index.js`, `dist/index.cjs`, `dist/index.d.ts`
- [ ] `yarn workspace ecommerce-demo dev` starts Vite at `http://localhost:3002`
- [ ] All 6 routes render without console errors
- [ ] `/products` renders 8 product cards (fetched from `/api/products.json`)
- [ ] `/checkout` buttons call `PulseWeb.trackEvent` (no-op stub, no crash)
- [ ] `/error-demo` throw button shows error boundary fallback
- [ ] TypeScript: `yarn workspace ecommerce-demo tsc --noEmit` passes

## Verification
```bash
cd pulse-web-otel
yarn install
yarn build
yarn workspace ecommerce-demo dev
# Open http://localhost:3002 — navigate all 6 routes, no console errors
```
