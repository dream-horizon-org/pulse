# integrations/nextjs

## 1. Purpose

Next.js helpers covering both the App Router and the legacy Pages Router, plus a `next.config` wrapper that uploads source maps to the Pulse backend for symbolicated stack traces.

## 2. Source location

- `pulse-web-otel/src/integrations/next/index.ts` — barrel
- `pulse-web-otel/src/integrations/next/instrumentation.ts` — Next.js `instrumentation.ts` hook
- `pulse-web-otel/src/integrations/next/useNextAppRouterTracking.ts` — App Router hook
- `pulse-web-otel/src/integrations/next/useNextPagesRouterTracking.ts` — Pages Router hook
- `pulse-web-otel/src/integrations/next/PulseRouterEvents.tsx` — imperative variant
- `pulse-web-otel/src/integrations/next-config/with-pulse-config.ts` — `withPulseConfig(nextConfig)`
- `pulse-web-otel/src/integrations/next-config/upload-source-maps.ts` — webpack plugin

## 3. Public surface

```ts
// "@dreamhorizonorg/pulse-web/next"
export function useNextAppRouterTracking(opts?: { screenNameMap?: ... }): void;
export function useNextPagesRouterTracking(opts?: { screenNameMap?: ... }): void;
export function PulseRouterEvents(props: { ... }): null;

// "@dreamhorizonorg/pulse-web/next-config"
export function withPulseConfig(nextConfig: NextConfig, opts: {
  apiKey: string;
  uploadSourceMaps?: boolean;
  sourceMapEndpoint?: string;
  release?: string;
}): NextConfig;
```

## 4. Internal design

- **App Router** hook subscribes to `usePathname()` / `useSearchParams()` and dispatches `pulse:route` on change.
- **Pages Router** hook subscribes to `next/router` events (`routeChangeComplete`).
- `instrumentation.ts` exists to ensure the SDK boots on Next's client entry; server-side is a no-op (`typeof window === "undefined"` guard inside `Pulse.init`).
- `withPulseConfig`:
  - Merges into `nextConfig.webpack(...)`.
  - Injects `upload-source-maps` webpack plugin in production builds.
  - The plugin walks the build output, finds `.map` files, and POSTs them to `sourceMapEndpoint` keyed by `(release, app.build_name)` so the Pulse backend can symbolicate `device.crash` logs.

## 5. Dependencies

- `next` (peer)
- `react`, `react-dom` (peer via next)
- Internal: core SDK + `react-integration` helpers

## 6. Data contracts

No new `pulse.type`; source maps lift `exception.stacktrace` quality on `device.crash` / `non_fatal` logs. The `release` argument becomes `app.build_name` if `Pulse.init` is configured with the same string.

## 7. Tests

- `src/__tests__/use-next-pages-router-tracking.test.tsx`
- `src/integrations/next/useNextAppRouterTracking.test.ts`
- `src/__tests__/with-pulse-config.test.ts`
- E2E: `examples/nextjs-demo/e2e/nextjs-demo.spec.ts`, `nextjs-demo.ch.spec.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/nextjs-integration/SPEC.md`. Two router hooks intentionally — the App Router hook can't poll the legacy `router.events` API.

## 9. Rebuild recipe

1. Implement the two router hooks; share the `pulse:route` dispatch pattern with React integration.
2. Build `with-pulse-config` as a pure `NextConfig → NextConfig` wrapper; only mutate `webpack` when `uploadSourceMaps !== false`.
3. Webpack plugin must be no-op in dev (`webpack.options.mode !== "production"`).
4. Document `@dreamhorizonorg/pulse-web/next` and `/next-config` subpath exports in `package.json#exports`.
