# 05.2 — Next.js Integration

**Goal:** Support both App Router (Next.js 13+) and Pages Router, handle SSR/SSG safely with client-only guards, and integrate with Next.js's instrumentation hook for zero-config setup.

**File:** `src/integrations/nextjs/index.tsx`
**Package:** `@pulse-sdk/nextjs`

---

## Next.js Routing Modes

| Mode | Router | Navigation detection |
|---|---|---|
| App Router (Next 13+) | `useRouter`, `usePathname` | `usePathname()` hook |
| Pages Router (Next ≤13) | `next/router` | `router.events` |

Both modes are supported. The integration auto-detects which is available.

---

## App Router Integration

### `PulseProvider` for App Router

Place in `app/layout.tsx` (must be a Client Component):

```tsx
// app/layout.tsx
import { PulseProvider } from '@pulse-sdk/nextjs';

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html>
      <body>
        <PulseProvider projectId="proj_abc123">
          {children}
        </PulseProvider>
      </body>
    </html>
  );
}
```

### Route Change Tracking (App Router)

The Next.js `usePathname` hook re-renders the component on every route change, making it the reliable detection mechanism:

```tsx
// src/integrations/nextjs/AppRouterTracker.tsx
'use client';

import { usePathname, useSearchParams } from 'next/navigation';
import { useEffect, useRef } from 'react';
import { usePulse } from '@pulse-sdk/react';

export function AppRouterTracker() {
  const pathname = usePathname();
  const sdk = usePulse();
  const prev = useRef<string | null>(null);

  useEffect(() => {
    if (prev.current !== null && prev.current !== pathname) {
      sdk.navigationInstrumentation?.onRouteChange(pathname);
    }
    prev.current = pathname;
  }, [pathname]);

  return null;  // Render-less component
}
```

Place inside `PulseProvider` in `app/layout.tsx`:

```tsx
<PulseProvider projectId="proj_abc123">
  <AppRouterTracker />
  {children}
</PulseProvider>
```

---

## Pages Router Integration

### `_app.tsx`

```tsx
// pages/_app.tsx
import type { AppProps } from 'next/app';
import { PulseProvider } from '@pulse-sdk/nextjs';

export default function App({ Component, pageProps }: AppProps) {
  return (
    <PulseProvider projectId="proj_abc123">
      <PagesRouterTracker />
      <Component {...pageProps} />
    </PulseProvider>
  );
}
```

### Route Tracking (Pages Router)

```tsx
// src/integrations/nextjs/PagesRouterTracker.tsx
'use client';

import { useRouter } from 'next/router';
import { useEffect } from 'react';
import { usePulse } from '@pulse-sdk/react';

export function PagesRouterTracker() {
  const router = useRouter();
  const sdk = usePulse();

  useEffect(() => {
    const onRouteChangeComplete = (url: string) => {
      const path = url.split('?')[0]; // strip query string
      sdk.navigationInstrumentation?.onRouteChange(path);
    };

    router.events.on('routeChangeComplete', onRouteChangeComplete);
    return () => {
      router.events.off('routeChangeComplete', onRouteChangeComplete);
    };
  }, [router.events]);

  return null;
}
```

---

## SSR Guard

The SDK must **never run on the server**. All SDK code must be wrapped in client-only guards.

```typescript
// src/integrations/nextjs/guards.ts

export function isClient(): boolean {
  return typeof window !== 'undefined';
}

// In PulseProvider:
export function PulseProvider({ projectId, children, options }: PulseProviderProps) {
  // Skip initialization on server side
  if (!isClient()) {
    return <>{children}</>;
  }
  // ... rest of client-side init
}
```

For the `'use client'` directive in Next.js 13+, all Pulse components are already Client Components. However, the SDK must also guard against being imported in Server Components:

```typescript
// package.json exports — separates server and client entrypoints
{
  "exports": {
    ".": {
      "react-server": "./dist/server-stub.js",  // empty stub for RSC
      "default": "./dist/index.js"
    }
  }
}
```

---

## `instrumentation.ts` Hook (Next.js 14+)

Next.js 14+ supports an `instrumentation.ts` file for server-side setup. Pulse doesn't need server-side telemetry (it's browser-focused), but this hook can be used to validate the project ID at build time:

```typescript
// instrumentation.ts (optional)
export function register() {
  // Server-side only — no Pulse SDK init here
  // Use this for server-side OpenTelemetry if needed in the future
}
```

---

## Web Vitals via `_document.tsx` (Pages Router)

Next.js Pages Router has a built-in `reportWebVitals` export that can be used instead of our web-vitals instrumentation (02.4):

```typescript
// pages/_app.tsx — optional, use instead of 02.4 web-vitals instrumentation
export function reportWebVitals(metric: NextWebVitalsMetric) {
  PulseSDK.getInstance()?.recordWebVital(metric);
}
```

---

## Full `@pulse-sdk/nextjs` Package

```typescript
// src/integrations/nextjs/index.ts
export { PulseProvider } from './PulseProvider';
export { AppRouterTracker } from './AppRouterTracker';
export { PagesRouterTracker } from './PagesRouterTracker';
export { usePulse } from '@pulse-sdk/react';  // re-export for convenience
```

---

## Edge Cases

| Case | Handling |
|---|---|
| Server Component imports Pulse | `react-server` export resolves to empty stub |
| `useSearchParams` in App Router (Suspense boundary required) | `AppRouterTracker` wraps `useSearchParams` in `<Suspense>` |
| Next.js Static Generation (SSG) | Pages are static HTML; SDK initializes in browser as normal |
| Middleware (`middleware.ts`) | Middleware runs on Edge Runtime; do not import SDK there |
| `next/image` LCP element | rrweb captures the rendered `<img>`; LCP attribution from web-vitals still works |
| Streaming SSR (React 18 + App Router) | Components hydrate progressively; `PulseProvider` client init still fires once |
| `router.events` deprecated in App Router | Use `usePathname` for App Router; `router.events` for Pages Router only |

---

## Testing

### Unit Tests (Vitest)

```tsx
it('renders children without SDK on server', () => {
  vi.stubGlobal('window', undefined);  // Simulate server
  const { getByText } = render(
    <PulseProvider projectId="proj_test">
      <span>content</span>
    </PulseProvider>
  );
  expect(getByText('content')).toBeDefined();
  vi.unstubAllGlobals();
});

it('tracks route change via usePathname', () => {
  const onRouteChange = vi.fn();
  mockSdk.navigationInstrumentation = { onRouteChange };

  // Simulate pathname change
  const { rerender } = render(<AppRouterTracker />, { wrapper: Providers });
  // Update pathname mock
  mockNextNavigation.pathname = '/new-page';
  rerender(<AppRouterTracker />);

  expect(onRouteChange).toHaveBeenCalledWith('/new-page');
});
```

### E2E (Playwright)

```typescript
test('Next.js page navigation creates screen_session span', async ({ page }) => {
  await page.goto('/next-app');
  await page.click('[href="/about"]');
  await page.waitForURL('/about');

  const span = await waitForSpan(receiver, 'screen_session');
  expect(span['screen.name']).toBe('/next-app');
  expect(span['url.path']).toBe('/next-app');
});
```

---

## Done Criteria

- [ ] `PulseProvider` works in both App Router and Pages Router
- [ ] Route changes tracked via `usePathname` (App Router) and `router.events` (Pages Router)
- [ ] No SDK code runs during SSR (server-side guard active)
- [ ] `react-server` export stub prevents import in Server Components
- [ ] `PulseErrorBoundary` works in Next.js app
- [ ] `usePulse()` hook accessible in Client Components
- [ ] All unit tests passing
