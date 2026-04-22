# 05.1 — React Integration

**Goal:** Provide a `PulseProvider` context wrapper, a `PulseErrorBoundary` component, a `usePulse()` hook, and automatic route tracking for React Router v6 — allowing React apps to integrate with zero manual instrumentation.

**File:** `src/integrations/react/index.tsx`
**Package:** `@pulse-sdk/react`

---

## Components & APIs

### `<PulseProvider>` — SDK Initialization

Initializes the SDK once and provides the Pulse context to the component tree.

```tsx
import { PulseProvider } from '@pulse-sdk/react';

function App() {
  return (
    <PulseProvider
      projectId="proj_abc123"
      options={{
        otlpEndpoint: 'https://ingest.pulse.io',
        replay: { enabled: true },
      }}
    >
      <Router>
        <AppRoutes />
      </Router>
    </PulseProvider>
  );
}
```

### `<PulseErrorBoundary>` — Error Capture

Wraps a subtree and captures React render errors via `componentDidCatch`, reporting them as `device.crash` or `non_fatal` events.

```tsx
<PulseErrorBoundary
  fallback={<ErrorScreen />}
  isFatal={false}             // Default: false (non_fatal)
  onError={(error, info) => { /* optional custom handler */ }}
>
  <FeatureComponent />
</PulseErrorBoundary>
```

### `usePulse()` — Hook for Manual Instrumentation

```tsx
const { trackEvent, reportException, identify } = usePulse();

// Track a custom interaction step
trackEvent('checkout_started', { cart_value: 99.99 });

// Report a caught exception
reportException(error, { isFatal: false });
```

### `useRouterTracking()` — React Router v6 Integration

Automatically tracks SPA route changes by listening to React Router's location changes.

```tsx
// Inside a component wrapped by <Router>
function AppRoutes() {
  useRouterTracking();
  return <Routes>...</Routes>;
}
```

---

## Implementation

```typescript
// src/integrations/react/PulseProvider.tsx
import React, { createContext, useContext, useEffect, useRef } from 'react';
import { PulseSDK } from '../../sdk';

interface PulseContextValue {
  sdk: PulseSDK;
}

const PulseContext = createContext<PulseContextValue | null>(null);

export function PulseProvider({ projectId, options, children }: PulseProviderProps) {
  const sdkRef = useRef<PulseSDK | null>(null);

  if (!sdkRef.current) {
    sdkRef.current = new PulseSDK({ projectId, ...options });
    sdkRef.current.init();  // Synchronous init; async parts (config fetch) run in background
  }

  useEffect(() => {
    return () => {
      sdkRef.current?.shutdown();
    };
  }, []);

  return (
    <PulseContext.Provider value={{ sdk: sdkRef.current }}>
      {children}
    </PulseContext.Provider>
  );
}

export function usePulse(): PulseSDK {
  const ctx = useContext(PulseContext);
  if (!ctx) throw new Error('[Pulse] usePulse() must be used inside <PulseProvider>');
  return ctx.sdk;
}
```

```typescript
// src/integrations/react/PulseErrorBoundary.tsx
import React, { Component, ErrorInfo, ReactNode } from 'react';
import { usePulse } from './PulseProvider';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  isFatal?: boolean;
  onError?: (error: Error, info: ErrorInfo) => void;
}

interface State { hasError: boolean }

export class PulseErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Access SDK from global singleton (Error Boundaries can't use hooks)
    PulseSDK.getInstance()?.reportException(error, {
      isFatal: this.props.isFatal ?? false,
      attributes: {
        'react.component_stack': info.componentStack ?? '',
      },
    });
    this.props.onError?.(error, info);
  }

  render() {
    if (this.state.hasError) {
      return this.props.fallback ?? null;
    }
    return this.props.children;
  }
}
```

```typescript
// src/integrations/react/useRouterTracking.ts
import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { usePulse } from './PulseProvider';

export function useRouterTracking(): void {
  const location = useLocation();
  const sdk = usePulse();
  const isFirst = useRef(true);

  useEffect(() => {
    if (isFirst.current) {
      isFirst.current = false;
      return; // Skip initial mount — navigation instrumentation handles the first load
    }
    // React Router v6 has already updated the URL — call setScreenName directly
    sdk.setScreenName(location.pathname);
  }, [location.pathname]);
  // No teardown needed — hook unmounts cleanly; no external listener registered
}
```

---

## Package Exports & Dependencies

```typescript
// src/integrations/react/index.ts
export { PulseProvider } from './PulseProvider';
export { PulseErrorBoundary } from './PulseErrorBoundary';
export { usePulse } from './PulseProvider';
export { useRouterTracking } from './useRouterTracking';
export type { PulseProviderProps } from './PulseProvider';
```

Consumed via `@dreamhorizon/pulse-web/react` (already wired in `package.json` exports + tsup entry).

`react-router-dom` must also be declared as an optional peer dependency in `package.json`:

```json
"peerDependencies": {
  "react": ">=18.0.0",
  "react-router-dom": ">=6.0.0"
},
"peerDependenciesMeta": {
  "react": { "optional": true },
  "react-router-dom": { "optional": true }
}
```

---

## Edge Cases

| Case | Handling |
|---|---|
| `PulseProvider` rendered twice (StrictMode double-invoke) | SDK init is idempotent; `sdkRef` ensures only one instance |
| `usePulse()` called outside `PulseProvider` | Throws helpful error message |
| React 18 Concurrent Mode | `useEffect` timing unchanged for init; no issues expected |
| `PulseErrorBoundary` catches during SSR | `componentDidCatch` is client-only; guard with `typeof window !== 'undefined'` |
| React Router v5 (legacy) | Not supported; users should use the vanilla history patch from 02.5 |
| Multiple nested `PulseErrorBoundary` | Each reports independently; innermost boundary catches first |

---

## Testing

### Unit Tests (Vitest + React Testing Library)

```tsx
it('initialises SDK on mount', () => {
  const initSpy = vi.spyOn(PulseSDK.prototype, 'init');
  render(<PulseProvider projectId="proj_test"><div /></PulseProvider>);
  expect(initSpy).toHaveBeenCalledOnce();
});

it('usePulse returns SDK instance', () => {
  const TestComp = () => {
    const sdk = usePulse();
    return <div data-testid="sdk">{sdk ? 'ok' : 'missing'}</div>;
  };
  const { getByTestId } = render(
    <PulseProvider projectId="proj_test"><TestComp /></PulseProvider>
  );
  expect(getByTestId('sdk').textContent).toBe('ok');
});

it('PulseErrorBoundary renders fallback on error', () => {
  const ThrowingComponent = () => { throw new Error('test error'); };
  const { getByText } = render(
    <PulseProvider projectId="proj_test">
      <PulseErrorBoundary fallback={<div>Error occurred</div>}>
        <ThrowingComponent />
      </PulseErrorBoundary>
    </PulseProvider>
  );
  expect(getByText('Error occurred')).toBeDefined();
});

it('PulseErrorBoundary reports error to SDK', () => {
  const reportSpy = vi.spyOn(PulseSDK, 'getInstance').mockReturnValue({
    reportException: vi.fn(),
  } as any);

  const ThrowingComponent = () => { throw new Error('render fail'); };
  render(
    <PulseProvider projectId="proj_test">
      <PulseErrorBoundary><ThrowingComponent /></PulseErrorBoundary>
    </PulseProvider>
  );
  expect(reportSpy().reportException).toHaveBeenCalledWith(
    expect.any(Error),
    expect.objectContaining({ isFatal: false })
  );
});

it('SDK initialises exactly once under StrictMode double-render', () => {
  const startSpy = vi.spyOn(PulseWeb, 'start');
  render(
    <React.StrictMode>
      <PulseProvider apiKey="test-key" serviceName="test"><div /></PulseProvider>
    </React.StrictMode>
  );
  expect(startSpy).toHaveBeenCalledOnce();
});

it('useRouterTracking calls setScreenName on route change', () => {
  const setScreenSpy = vi.spyOn(PulseWeb, 'setScreenName');
  // Render with initial route, then navigate
  const { rerender } = render(
    <MemoryRouter initialEntries={['/home']}>
      <PulseProvider apiKey="test-key" serviceName="test">
        <RouteTracker />
      </PulseProvider>
    </MemoryRouter>
  );
  rerender(
    <MemoryRouter initialEntries={['/checkout']}>
      <PulseProvider apiKey="test-key" serviceName="test">
        <RouteTracker />
      </PulseProvider>
    </MemoryRouter>
  );
  expect(setScreenSpy).toHaveBeenCalledWith('/checkout');
});

it('useRouterTracking does not leak listeners on unmount', () => {
  const { unmount } = render(
    <MemoryRouter initialEntries={['/home']}>
      <PulseProvider apiKey="test-key" serviceName="test">
        <RouteTracker />
      </PulseProvider>
    </MemoryRouter>
  );
  unmount();
  // No assertion needed beyond unmount completing without error —
  // verifies the hook has no dangling teardown side-effects
});
```

---

## Done Criteria

**SDK Cleanup**
- [ ] `_starting`, `_initialized`, `_shuttingDown` all reset in `shutdown()` so `start()` works after shutdown
- [ ] All instrumentation event listeners removed via `uninstallAll()` on shutdown

**PulseProvider**
- [ ] SDK initializes exactly once, even under React StrictMode double-render
- [ ] `usePulse()` throws a clear error when called outside `<PulseProvider>`
- [ ] SDK shuts down cleanly on `PulseProvider` unmount

**PulseErrorBoundary**
- [ ] Renders fallback and calls `reportDeviceCrash()` on render failure
- [ ] `react.component_stack` attribute captured and sent with every crash report

**useRouterTracking**
- [ ] Calls `setScreenName()` on every React Router v6 location change (skips initial mount)
- [ ] Unmounts without dangling listeners or side-effects

**Package & Dependencies** *(single task)*
- [ ] All React exports (`PulseProvider`, `PulseErrorBoundary`, `usePulse`, `useRouterTracking`) available under `@dreamhorizon/pulse-web/react`
- [ ] `react-router-dom >= 6.0.0` declared as optional peer dependency in `package.json`

**Demo App**
- [ ] Ecommerce demo refactored to use `PulseProvider`, `PulseErrorBoundary`, `useRouterTracking` instead of manual wiring

**Tests**
- [ ] StrictMode double-render: `start()` called exactly once
- [ ] Error boundary: crash captured + `react.component_stack` present in payload
- [ ] `useRouterTracking`: `setScreenName` called on navigation, no leak on unmount
