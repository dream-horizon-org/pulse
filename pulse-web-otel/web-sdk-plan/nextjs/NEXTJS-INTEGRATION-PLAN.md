# Next.js Integration Plan — `@dreamhorizon/pulse-web/next`

## Overview

Add Next.js framework support to the Pulse Web SDK, mirroring the existing
`@dreamhorizon/pulse-web/react` subpath. A Next.js app that installs this
integration gets the same signal coverage as a React + React Router app today:
session tracking, error tracking (manual + auto), and screen name tracking on
every route change.

Signals NOT in scope for this plan (missing for all frameworks, not just Next.js):
network tracking, click tracking, web vitals, navigation timing spans.

---

## Signal & Feature Coverage

### Already built — works in Next.js today (no new work needed)


| Feature                                                                          | How                                                                             |
| -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| Session start / end                                                              | SDK core — framework-agnostic, SSR-safe                                         |
| Manual error reporting (`reportException`, `reportDeviceCrash`, `trackNonFatal`) | SDK core — call from any client component                                       |
| Auto error capture (unhandled JS errors, promise rejections)                     | SDK core — installs on `window`, works in Next.js as-is                         |
| Custom events (`trackEvent`)                                                     | SDK core — framework-agnostic                                                   |
| React error boundary (`PulseErrorBoundary`)                                      | Already in `/react` integration — re-exported from `/next` barrel               |
| `PulseProvider` — SDK init + context                                             | Already has `"use client"` + `typeof window` guards — works in App Router today |


### Building in this plan — Next.js specific gaps


| Feature                        | What                                                                         |
| ------------------------------ | ---------------------------------------------------------------------------- |
| Screen tracking — App Router   | `useNextAppRouterTracking` hook + `<PulseNavigationEvents>` component        |
| Screen tracking — Pages Router | `useNextPagesRouterTracking` hook                                            |
| Server-side crash capture      | `createPulseInstrumentationHandler()` for `instrumentation.ts` (Next.js 15+) |
| Package wiring                 | `@dreamhorizon/pulse-web/next` subpath, types, barrel exports                |
| Tests + demo                   | Unit tests, Next.js demo app, Playwright E2E                                 |


### Not supported — future milestones (missing for all frameworks, not Next.js specific)


| Feature                                                       | When                                                                             |
| ------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Navigation timing spans (`screen_load`, `screen_interactive`) | When `NavigationInstrumentation` milestone lands — Next.js gets it automatically |
| Network tracking                                              | Future milestone                                                                 |
| Click tracking                                                | Future milestone                                                                 |
| Web vitals                                                    | Future milestone                                                                 |


### Not supported — platform limitation (nothing we can do)


| Feature                              | Why                                                                                                                                                 |
| ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Navigation start event in App Router | Next.js doesn't expose one — `usePathname` only fires after navigation completes, not before it starts. Duration timing not possible in App Router. |


### Not doing — conscious design decisions


| Decision                                                   | Reasoning                                                                                                                                                                                                                                                           |
| ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **No auto-detect App vs Pages Router**                     | Dynamic imports bundle both routers regardless of which runs — user picks the right hook in one line and gets full tree-shaking. Auto-detect makes bundle size worse, not better.                                                                                   |
| **No separate npm package `@dreamhorizon/pulse-web-next`** | Subpath export means one package, one version, one publish. No version skew between core and Next.js integration. Bundle size is identical — `next` is an external peer dep, not bundled either way.                                                                |
| **No Pages Router demo app**                               | `useNextPagesRouterTracking` is fully built and unit tested. Pages Router is the legacy model — all new Next.js projects use App Router. The `nextjs-demo` app already proves Next.js wiring end-to-end. A Pages Router demo adds no new signal coverage to verify. |


---

## Current State


| What exists                                                              | Where                                                       |
| ------------------------------------------------------------------------ | ----------------------------------------------------------- |
| React integration                                                        | `src/integrations/react/` → `@dreamhorizon/pulse-web/react` |
| `PulseProvider` with `"use client"` + SSR guards                         | `src/integrations/react/PulseProvider.tsx`                  |
| `useRouterTracking` — hard-coupled to `react-router-dom`'s `useLocation` | `src/integrations/react/useRouterTracking.ts`               |
| `"next"` already listed as external in tsup                              | `tsup.config.ts:17`                                         |
| `./react` subpath export in package.json                                 | `package.json:15-19`                                        |


**The only reason Next.js doesn't work today:** `useRouterTracking` imports
`useLocation` from `react-router-dom`, which doesn't exist in a Next.js project.
Everything else in `PulseProvider` and the SDK core already works in Next.js.

---

## What We're Building

```
src/integrations/next/
├── index.ts                      ← public barrel for @dreamhorizon/pulse-web/next
├── useNextAppRouterTracking.ts   ← App Router hook (usePathname + useSearchParams)
├── useNextPagesRouterTracking.ts ← Pages Router hook (router.events)
└── PulseNavigationEvents.tsx     ← Drop-in <Suspense>-wrapped component for layout.tsx

src/types/next.ts                 ← UseNextAppRouterTrackingOptions, UseNextPagesRouterTrackingOptions

src/__tests__/
├── use-next-app-router-tracking.test.tsx
├── use-next-pages-router-tracking.test.tsx
└── pulse-navigation-events.test.tsx

examples/nextjs-demo/             ← Next.js 14+ App Router demo app
```

### Changes to existing files


| File             | Change                                                                        |
| ---------------- | ----------------------------------------------------------------------------- |
| `tsup.config.ts` | Add `next: "src/integrations/next/index.ts"` entry                            |
| `package.json`   | Add `"./next"` export map; add `next >=14.0.0` to optional `peerDependencies` |


No changes to `PulseProvider.tsx`, `useRouterTracking.ts`, or any SDK core files.

---

## Detailed Task Breakdown

---

### T1 — Package wiring

**Est: 0.5h**

**Files to modify:**

`tsup.config.ts`

```ts
entry: {
  index: "src/index.ts",
  react: "src/integrations/react/index.ts",
  next:  "src/integrations/next/index.ts",   // add
},
external: ["react", "react-dom", "react-router-dom", "next"],  // "next" already there
```

`package.json` — add to `exports`:

```json
"./next": {
  "types": "./dist/next.d.ts",
  "import": "./dist/next.js",
  "require": "./dist/next.cjs"
}
```

`package.json` — add to `peerDependencies` + `peerDependenciesMeta`:

```json
"peerDependencies": {
  "next": ">=14.0.0"
},
"peerDependenciesMeta": {
  "next": { "optional": true }
}
```

`package.json` — add to `devDependencies`:

```json
"next": "^15.0.0"
```

`.size-limit.json` — add entry for the new bundle:

```json
{ "path": "dist/next.js", "limit": "10 kB" }
```

Without this, CI has no size gate on the new subpath. Tune the limit after first build.

**Verify:** `yarn build` produces `dist/next.js`, `dist/next.cjs`, `dist/next.d.ts`.

---

### T2 — App Router hook: `useNextAppRouterTracking`

**Est: 1.5h**

**File:** `src/integrations/next/useNextAppRouterTracking.ts`

**What it does:**

- Uses `usePathname()` + `useSearchParams()` from `next/navigation`
- Calls `PulseWeb.setScreenName(name)` on every pathname change
- Mirrors the exact same options API as `useRouterTracking` for consistency:
`format`, `includeSearch`, `skipInitial` (default `true`)
- StrictMode-safe via `useRef` guard (same pattern as `useRouterTracking`)
- `"use client"` directive at top (required — uses hooks)

**Key difference from `useRouterTracking`:** App Router has no navigation *start*
event. The effect fires only *after* navigation completes (when `usePathname`
triggers a re-render). This is a Next.js platform limitation — document it.

**Implementation sketch:**

```ts
"use client";
import { useEffect, useRef } from "react";
import { usePathname, useSearchParams } from "next/navigation";
import { PulseWeb } from "../../sdk";
import type { UseNextAppRouterTrackingOptions } from "../../types/next";

export function useNextAppRouterTracking(
  options: UseNextAppRouterTrackingOptions = {},
): void {
  const { format, includeSearch = false, skipInitial = true } = options;
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const prevDependency = useRef<string | null>(null);

  // usePathname() returns null in Pages Router fallback routes and some static
  // optimisation cases. Skip setScreenName entirely when pathname is null.
  const resolvedPathname = pathname ?? null;

  const dependency = resolvedPathname === null
    ? null
    : includeSearch
      ? resolvedPathname + "?" + searchParams.toString()
      : resolvedPathname;

  useEffect(() => {
    if (dependency === null) return; // pathname not yet resolved — skip

    if (prevDependency.current === null) {
      prevDependency.current = dependency;
      if (skipInitial) return;
    } else if (prevDependency.current === dependency) {
      return; // StrictMode no-op
    } else {
      prevDependency.current = dependency;
    }

    const name = format
      ? format({ pathname: resolvedPathname ?? "", search: searchParams.toString(), hash: "" })
      : dependency;

    PulseWeb.setScreenName(name);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dependency]);
}
```

`**format` callback shape** — use a new `PulseNextLocationLike` type in `src/types/next.ts`:

```ts
interface PulseNextLocationLike {
  pathname: string;
  search: string;  // query string without leading "?"
  hash: string;    // always "" in App Router — included for API parity with PulseLocationLike
}
```

**Important note for App Router:** `useSearchParams()` causes this hook to
require a `<Suspense>` boundary above it during SSR prerendering. This is why
`PulseNavigationEvents` (T4) exists — it handles this automatically.

---

### T3 — Pages Router hook: `useNextPagesRouterTracking`

**Est: 1h**

**File:** `src/integrations/next/useNextPagesRouterTracking.ts`

**What it does:**

- Uses `useRouter()` from `next/router` (Pages Router only)
- Subscribes to `router.events.on('routeChangeStart')` and
`router.events.on('routeChangeComplete')` in a `useEffect`
- Unlike App Router, Pages Router gives us `routeChangeStart` — navigation
start time can be captured for future use when NavigationInstrumentation lands
- Calls `PulseWeb.setScreenName(url)` on `routeChangeComplete`
- Same options API: `format`, `includeSearch`, `skipInitial`
- `"use client"` directive at top

**Initial load behaviour:** `routeChangeComplete` does NOT fire on the first page
load — only on subsequent client-side navigations. The initial screen name is
covered by `session.start` (same as React Router). `skipInitial` here skips the
first `routeChangeComplete` event (first client-side nav after load), not the
first paint.

**Why two separate hooks (not auto-detect):**
`next/router` and `next/navigation` are different packages for different routers.
Auto-detecting which one is active at runtime is fragile and error-prone.
Users pick the right hook for their router version.

**Implementation sketch:**

```ts
"use client";
import { useEffect, useRef } from "react";
import { useRouter } from "next/router";
import { PulseWeb } from "../../sdk";
import type { UseNextPagesRouterTrackingOptions } from "../../types/next";

export function useNextPagesRouterTracking(
  options: UseNextPagesRouterTrackingOptions = {},
): void {
  const { format, includeSearch = false, skipInitial = true } = options;
  const router = useRouter();
  const skipInitialRef = useRef(skipInitial);

  useEffect(() => {
    const handleRouteChangeComplete = (url: string): void => {
      if (skipInitialRef.current) {
        skipInitialRef.current = false;
        return;
      }
      const parsedUrl = new URL(url, "http://x");
      const dependency = includeSearch ? url : parsedUrl.pathname;
      const name = format
        ? format({ pathname: parsedUrl.pathname, search: parsedUrl.search.slice(1) })
        : dependency;
      PulseWeb.setScreenName(name);
    };

    router.events.on("routeChangeComplete", handleRouteChangeComplete);
    return () => {
      router.events.off("routeChangeComplete", handleRouteChangeComplete);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
}
```

---

### T4 — `<PulseNavigationEvents>` component

**Est: 0.5h**

**File:** `src/integrations/next/PulseNavigationEvents.tsx`

**Why it exists:**
`useSearchParams()` from `next/navigation` forces the component using it into a
Suspense boundary during prerendering. Without this wrapper, users get a Next.js
build warning / hydration mismatch if they add `useNextAppRouterTracking` directly
to `layout.tsx` without a `<Suspense>` boundary.

**What it does:**

- A `"use client"` component that calls `useNextAppRouterTracking` internally
- Renders `null` (invisible, no DOM output)
- Wraps itself in `<Suspense fallback={null}>`
- Accepts the same options as `useNextAppRouterTracking`

**Usage in `app/layout.tsx`:**

```tsx
import { PulseProvider } from "@dreamhorizon/pulse-web/react";
import { PulseNavigationEvents } from "@dreamhorizon/pulse-web/next";

export default function RootLayout({ children }) {
  return (
    <html>
      <body>
        <PulseProvider config={pulseConfig}>
          <PulseNavigationEvents />
          {children}
        </PulseProvider>
      </body>
    </html>
  );
}
```

**Implementation:**

```tsx
"use client";
import { Suspense } from "react";
import { useNextAppRouterTracking } from "./useNextAppRouterTracking";
import type { UseNextAppRouterTrackingOptions } from "../../types/next";

function NavigationEventsInner(options: UseNextAppRouterTrackingOptions) {
  useNextAppRouterTracking(options);
  return null;
}

export function PulseNavigationEvents(props: UseNextAppRouterTrackingOptions) {
  return (
    <Suspense fallback={null}>
      <NavigationEventsInner {...props} />
    </Suspense>
  );
}
```

---

### T5 — Types

**Est: 0.5h**

**File:** `src/types/next.ts`

```ts
export interface PulseNextLocationLike {
  pathname: string;
  search: string;  // query string without leading "?"
  hash: string;    // always "" in App Router; populated in Pages Router if applicable
}

export interface UseNextAppRouterTrackingOptions {
  format?: (location: PulseNextLocationLike) => string;
  includeSearch?: boolean;
  skipInitial?: boolean;
}

export interface UseNextPagesRouterTrackingOptions {
  format?: (location: PulseNextLocationLike) => string;
  includeSearch?: boolean;
  skipInitial?: boolean;
}
```

**File:** `src/integrations/next/index.ts` (public barrel)

```ts
export {
  useNextAppRouterTracking,
  type UseNextAppRouterTrackingOptions,
} from "./useNextAppRouterTracking";

export {
  useNextPagesRouterTracking,
  type UseNextPagesRouterTrackingOptions,
} from "./useNextPagesRouterTracking";

export { PulseNavigationEvents } from "./PulseNavigationEvents";

// Re-export React integration so users only need one import path for Next.js
export { PulseProvider, usePulse } from "../react/PulseProvider";
export type { PulseProviderProps, PulseContextValue } from "../react/PulseProvider";
export { PulseErrorBoundary } from "../react/PulseErrorBoundary";
export type { PulseErrorBoundaryProps } from "../react/PulseErrorBoundary";
```

---

### T6 — Server-side error hook (Next.js 15+ optional)

**Est: 1h**

**File:** `src/integrations/next/instrumentation.ts`

**What it does:**
Provides a `createPulseInstrumentationHandler()` helper that users call in their
`instrumentation.ts` file. Maps `onRequestError` context to a `reportDeviceCrash`
call with server-side metadata.

**Usage in user's `instrumentation.ts`:**

```ts
import { createPulseInstrumentationHandler } from "@dreamhorizon/pulse-web/next";

export function register() {
  // guard: only run in Node.js runtime, not Edge
  if (process.env.NEXT_RUNTIME === "nodejs") {
    // SDK init for server-side
  }
}

export const onRequestError = createPulseInstrumentationHandler({
  apiKey: "your-api-key",       // same apiKey as PulseWebConfig
  collectorEndpoint: "https://your-collector/v1/logs",
});
```

**Implementation:**

```ts
import type { Instrumentation } from "next";

interface PulseInstrumentationConfig {
  apiKey: string;             // same apiKey as PulseWebConfig
  collectorEndpoint: string;  // full OTLP logs endpoint, e.g. https://collector/v1/logs
}

export function createPulseInstrumentationHandler(
  config: PulseInstrumentationConfig,
): Instrumentation.onRequestError {
  return (err, request, context) => {
    // Use err.digest as canonical error ID — actual Error instance may be
    // wrapped by React for RSC errors
    const error = err instanceof Error ? err : new Error(String(err));
    // Next.js onRequestError passes request.path (not request.url)
    const attrs: Record<string, string> = {
      "server.route": context.routePath ?? "",
      "server.router_kind": context.routerKind,
      "server.route_type": context.routeType,
      "server.request_path": request.path ?? "",
      "server.request_method": request.method ?? "",
    };
    if ((err as { digest?: string }).digest) {
      attrs["error.digest"] = (err as { digest: string }).digest;
    }

    // Ship via fetch directly — PulseWeb singleton isn't available server-side
    // This sends a single log record as OTLP JSON to the collector
    void sendServerCrashSignal(config, error, attrs);
  };
}
```

**Note:** Server-side signal sending uses a raw `fetch` OTLP call — the browser
SDK singleton (`PulseWeb`) is not available in the Node.js server runtime.
The helper constructs a minimal OTLP log record and ships it directly.

**Compatibility:** Next.js 15.0.0+ only (stable `onRequestError`). Document this.

---

### T7 — Unit tests

**Est: 2h**

**File:** `src/__tests__/use-next-app-router-tracking.test.tsx`

Mock `next/navigation` module:

```ts
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(),
  useSearchParams: vi.fn(() => new URLSearchParams()),
}));
```

Test cases:

- `setScreenName` called with correct pathname on route change
- `skipInitial: true` (default) → no call on first render
- `skipInitial: false` → call on first render
- `includeSearch: true` → pathname + search passed to `setScreenName`
- `format` callback invoked with `PulseNextLocationLike` shape
- StrictMode double-effect → only one `setScreenName` call per unique pathname
- Same pathname re-rendered → no duplicate call
- `pathname` changes from `/home` to `/cart` → correct `setScreenName` call

---

**File:** `src/__tests__/use-next-pages-router-tracking.test.tsx`

Mock `next/router`:

```ts
vi.mock("next/router", () => ({
  useRouter: vi.fn(() => ({
    events: {
      on: vi.fn(),
      off: vi.fn(),
    },
  })),
}));
```

Test cases:

- Subscribes to `routeChangeComplete` on mount
- Unsubscribes on unmount (cleanup called)
- `setScreenName` called with URL on `routeChangeComplete`
- `skipInitial: true` → first `routeChangeComplete` skipped
- `skipInitial: false` → first `routeChangeComplete` triggers `setScreenName`
- `includeSearch: false` → query string stripped before `setScreenName`
- `format` callback receives correct `PulseNextLocationLike`

---

**File:** `src/__tests__/pulse-navigation-events.test.tsx`

Test cases:

- Renders `null` (no DOM output)
- Wraps inner component in `<Suspense>` (no thrown suspense error in render)
- Passes options through to `useNextAppRouterTracking`

---

**File:** `src/__tests__/package-exports.test.tsx` — extend existing test

Add assertions:

```ts
// @dreamhorizon/pulse-web/next exports
expect(nextExports).toHaveProperty("useNextAppRouterTracking");
expect(nextExports).toHaveProperty("useNextPagesRouterTracking");
expect(nextExports).toHaveProperty("PulseNavigationEvents");
expect(nextExports).toHaveProperty("PulseProvider");
expect(nextExports).toHaveProperty("usePulse");
```

---

### T8 — Next.js demo app

**Est: 1.5h**

**Location:** `examples/nextjs-demo/`

**Stack:** Next.js 15 App Router, TypeScript

**Key files:**

`app/layout.tsx`

```tsx
import { PulseProvider } from "@dreamhorizon/pulse-web/react";
import { PulseNavigationEvents } from "@dreamhorizon/pulse-web/next";

const pulseConfig = {
  apiKey: process.env.NEXT_PUBLIC_PULSE_API_KEY!,
  dataCollectionState: PulseDataCollectionConsent.ALLOWED,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <PulseProvider config={pulseConfig}>
          <PulseNavigationEvents />
          {children}
        </PulseProvider>
      </body>
    </html>
  );
}
```

`app/page.tsx`, `app/products/page.tsx`, `app/cart/page.tsx` — simple pages
to demo navigation screen tracking.

`app/error-demo/page.tsx` — button that throws + one that calls
`PulseWeb.reportException()` manually.

`.env.example`:

```
NEXT_PUBLIC_PULSE_API_KEY=default-project_devkey01
```

`package.json` — add `nextjs-demo` to root workspaces. Script:

```json
"demo:next": "yarn workspace nextjs-demo dev"
```

**Port:** 3003 (avoids conflict with ecommerce-demo on 3002).

---

### T9 — E2E tests

**Est: 1.5h**

**Location:** `examples/nextjs-demo/e2e/`

**Stack:** Playwright (same as ecommerce-demo)

Test cases:

- `session.start` log record emitted on first page load
- `screen.name` updates on navigation (Home → Products → Cart)
- `device.crash` emitted when error boundary catches a thrown error
- `non_fatal` emitted on manual `reportException()` call
- Session persists across page navigations (same `session.id`)

Use same `ch-fixture.ts` pattern as `ecommerce-demo/e2e/` to query ClickHouse
for emitted signals.

**Fixture wiring:** `ecommerce-demo` fixtures use env vars and port 3002. The
Next.js demo runs on port 3003 — create a separate `fixture.ts` and
`playwright.config.ts` for `nextjs-demo/e2e/` with `baseURL: http://localhost:3003`.
Do not reuse the ecommerce-demo fixture directly.

---

## Known Limitations (document in README)


| Limitation                                         | Detail                                                                                                                                                                                                                                                          |
| -------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| No navigation start event (App Router)             | `useNextAppRouterTracking` fires *after* navigation completes. Can't measure navigation duration. `NavigationInstrumentation` timing spans are not possible in App Router until Next.js adds a client navigation API.                                           |
| Pages Router only in `pages/` directory            | `useNextPagesRouterTracking` uses `next/router` which is Pages Router only. Do not use in App Router.                                                                                                                                                           |
| Server-side error hook (T6) requires Next.js 15+   | `onRequestError` was experimental before 15.0.0.                                                                                                                                                                                                                |
| `useNextAppRouterTracking` requires `<Suspense>`   | Use `<PulseNavigationEvents>` instead of the raw hook in layouts.                                                                                                                                                                                               |
| `instrumentation.ts` client-side init not possible | `register()` is server-only. Client-side SDK init must happen via `<PulseProvider>` in a `"use client"` component.                                                                                                                                              |
| `usePathname` hydration mismatch with rewrites     | When Next.js rewrites change the visible URL, the server renders the source path but the client sees the rewritten path. Screen name may reflect the internal pathname, not the user-visible URL. Workaround: use a `format` callback to normalise route names. |


---

## Delivery Order

```
T1 (wiring) → T5 (types) → T2 (App Router hook) → T4 (NavigationEvents) → T7 (tests)
                                                                                ↓
                                                              T3 (Pages Router hook)
                                                              T6 (server error hook)
                                                              T8 (demo app)
                                                              T9 (E2E)
```

**Shippable checkpoint after T1 + T5 + T2 + T4 + T7:** App Router support done,
tested, exported. Pages Router and server errors can ship in a follow-up.

---

## Total Estimate


| Task                                 | Est |
| ------------------------------------ | --- |
| T1 — Package wiring                  |     |
| T2 — App Router hook                 |     |
| T3 — Pages Router hook               |     |
| T4 — PulseNavigationEvents component |     |
| T5 — Types + barrel                  |     |
| T6 — Server-side error hook          |     |
| T7 — Unit tests                      |     |
| T8 — Next.js demo app                |     |
| T9 — E2E tests                       |     |
| **Total**                            |     |


