# integrations/react

## 1. Purpose

React-idiomatic helpers: a context provider that runs `Pulse.init()` once, an error boundary that pipes React render errors into `Pulse.reportError()`, and a hook that bridges React Router events into the SDK's screen-signal pipeline.

## 2. Source location

- `pulse-web-otel/src/integrations/react/PulseProvider.tsx`
- `pulse-web-otel/src/integrations/react/PulseErrorBoundary.tsx`
- `pulse-web-otel/src/integrations/react/useRouterTracking.ts`
- `pulse-web-otel/src/integrations/react/PulseRouterEvents.tsx`
- `pulse-web-otel/src/integrations/react/router.ts`
- `pulse-web-otel/src/integrations/react/index.ts`

## 3. Public surface

```tsx
export function PulseProvider(props: { config: PulseWebConfig; children: React.ReactNode }): JSX.Element;
export class PulseErrorBoundary extends React.Component<{ fallback?: ReactNode; children: ReactNode }>;
export function useRouterTracking(opts?: { screenNameMap?: Record<string, string> }): void;
export function PulseRouterEvents(props: { ... }): null;
```

Subpath import: `@dreamhorizonorg/pulse-web/react`.

## 4. Internal design

- `PulseProvider` calls `Pulse.init(config)` inside a `useEffect` with an empty dep array; cleanup calls `Pulse.shutdown()` only in dev / strict-mode re-runs.
- `PulseErrorBoundary` overrides `componentDidCatch(error, info)` and calls `Pulse.reportError(error, { componentStack: info.componentStack, kind: "react" })`.
- `useRouterTracking` subscribes to `react-router` via `useLocation()`; on every location change it dispatches `window.dispatchEvent(new CustomEvent("pulse:route", { detail: { screenName } }))` for `NavigationInstrumentation` to pick up.
- `PulseRouterEvents` is the imperative variant for apps that don't want a hook.

## 5. Dependencies

- `react` (peer)
- `react-router-dom` (peer, optional)
- The core SDK (`../../sdk.ts`)

## 6. Data contracts

No new `pulse.type` values — these helpers feed the existing `screen_load` / `screen_session` pipeline by setting `screen.name`. The error boundary produces `non_fatal` logs (`non_fatal.type = react`).

## 7. Tests

- `src/__tests__/pulse-provider.test.tsx`
- `src/__tests__/pulse-router-events.test.tsx`
- `src/integrations/react/useRouterTracking.test.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/react-integration/SPEC.md`. `PulseProvider` does not re-init on prop changes; consumers needing reconfiguration should call `Pulse.shutdown()` then mount a fresh provider.

## 9. Rebuild recipe

1. `PulseProvider`: `useEffect(() => { Pulse.init(config); return () => Pulse.shutdown(); }, [])`.
2. `PulseErrorBoundary`: class component, forward `error`+`componentStack` to `Pulse.reportError`.
3. `useRouterTracking`: subscribe to `useLocation`, derive screen name via `screenNameMap` or `resolveScreenNameFromUrl`, dispatch `pulse:route`.
4. Export everything from `integrations/react/index.ts` and add a package subpath export.
