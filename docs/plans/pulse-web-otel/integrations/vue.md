# integrations/vue

## 1. Purpose

Vue integration for `@dreamhorizonorg/pulse-web` — currently **not yet implemented** in `pulse-web-otel/src/integrations/`. This file documents the planned shape so the next iteration can land it without rediscovery.

## 2. Source location

- Planned: `pulse-web-otel/src/integrations/vue/`
  - `installPulse.ts` — Vue plugin (`app.use(installPulse, config)`)
  - `useRouterTracking.ts` — `vue-router` bridge
  - `PulseErrorBoundary.vue` (or `errorHandler.ts`) — `app.config.errorHandler` wiring
  - `index.ts` — barrel
- Planned subpath export: `@dreamhorizonorg/pulse-web/vue`

No source files exist today; do **not** import this path.

## 3. Public surface (proposed)

```ts
export const PulseVuePlugin: Plugin<PulseWebConfig>;
// Usage: app.use(PulseVuePlugin, config);

export function useRouterTracking(opts?: { screenNameMap?: Record<string, string> }): void;

// Optional standalone wirer for apps that don't use vue-router
export function installVueErrorHandler(app: App): () => void;
```

## 4. Internal design (proposed)

- Plugin entry calls `Pulse.init(config)` once; multiple `app.use(...)` calls become no-ops (the SDK is already idempotent).
- `installVueErrorHandler` wraps `app.config.errorHandler` to call `Pulse.reportError(err, { kind: "vue", componentStack })` (Vue 3 supplies the component instance and trace string as args 2/3).
- `useRouterTracking` reads `vue-router`'s `useRoute()` / `router.afterEach()` and dispatches `pulse:route` exactly like the React/Next versions.

## 5. Dependencies (proposed)

- `vue` ^3 (peer)
- `vue-router` ^4 (peer, optional)
- Internal: core SDK

## 6. Data contracts

Same as React integration — no new `pulse.type`. Errors emit `non_fatal` with `non_fatal.type = vue`; route changes feed `screen_load` / `screen_session`.

## 7. Tests (proposed)

- `src/__tests__/pulse-vue-plugin.test.ts` (jest-environment-jsdom + `@vue/test-utils`)
- E2E demo: `examples/vue-demo/` (proposed)

## 8. History / decisions

No SPEC yet. The Pulse Web SDK PRD (`pulse-web-otel/docs/prd/PRD.md`) lists Vue as a follow-on framework after React and Next.js. Mirror the React integration's API so cross-framework parity stays simple.

## 9. Rebuild recipe

1. Mirror `integrations/react/` structure under `integrations/vue/`.
2. Vue 3 plugin form: `{ install(app, config) { Pulse.init(config); installVueErrorHandler(app); } }`.
3. Hook subscribes to `router.afterEach` and dispatches `pulse:route`.
4. Add subpath export in `pulse-web-otel/package.json#exports` for `./vue`.
5. Add Vitest coverage and a `vue-demo` example workspace; copy the Playwright `screen-navigation.spec.ts` pattern.
