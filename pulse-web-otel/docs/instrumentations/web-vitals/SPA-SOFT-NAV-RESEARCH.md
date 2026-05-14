# SPA Web Vitals — Research & Roadmap

## The Problem

Browser web vitals APIs (LCP, CLS, INP, FCP, TTFB) were designed for traditional
page loads where the browser fully reloads a new URL.

In a SPA (React Router, Next.js App Router, Next.js Pages Router), the browser
**never reloads**. The user clicks a link, the URL changes via the History API,
new content renders — but the browser's performance measurement APIs are still
running from the original page load. This means:

- **LCP** fires once and is done — there is no "LCP for /cart"
- **CLS** keeps accumulating — the score on /checkout includes layout shifts
  from /home, /products, and /cart
- **INP** tracks the single worst interaction since the page loaded — not per route
- **TTFB and FCP** are one-time network metrics — meaningless for SPA route changes

---

## What the Industry Does Today

| Tool | Per-route LCP | Per-route CLS | Per-route INP | How |
|---|---|---|---|---|
| **GA4** | No | No | No | `web-vitals` library, hard load only. Enhanced Measurement fires `page_view` on SPA nav but does not re-measure vitals. |
| **PostHog** | No | No | No | `web-vitals` v5.2.0. CLS bleeds across all routes. No delta tracking. History API patching for pageview events only. |
| **Sentry** | No | No | No | Vendors own fork of `web-vitals` (~v3.5.2, no `reportSoftNavs`). `startTrackingWebVitals()` called once at SDK init, never re-registered. LCP + CLS finalized and **stopped** when the first SPA navigation begins (`listenForWebVitalReportEvents` single-fire). INP is continuous per-interaction but not reset per route. Source: `browser-utils/src/metrics/browserMetrics.ts`, `lcp.ts`, `cls.ts`. |
| **Datadog** | Yes | Yes | Yes | Manual approach — re-registers `LCP` PerformanceObserver per route, tracks delta CLS (difference since last route change), slices INP event-timing entries by route boundary. No browser API dependency. |
| **Chrome Soft Navigation API** | Yes | Yes | Yes | Browser-native. Chrome 147+ origin trial (not GA as of 2026). Enables `web-vitals` `reportSoftNavs: true`. Not available in Safari or Firefox. |

**Takeaway:** Google, PostHog, and Sentry are all in the same boat as a basic
implementation. Datadog is the only major tool solving this properly today, via
custom code. Even Google's own GA4 does not give you per-route vitals for SPAs
out of the box.

---

## What Pulse Does Today (Phase 1 — shipped)

**Status: Parity with PostHog and Sentry.**

- All 6 metrics (LCP, INP, CLS, FCP, FID, TTFB) are registered on initial hard page load
- Every SPA route change triggers `Pulse.notifySoftNavigation()` → `forceFlush()`
- This sends any buffered vitals to the collector **immediately** at route change,
  instead of waiting for tab hide
- `screen.name` is stamped on each log record at emit time (when the metric fires),
  so each vital is correctly attributed to the route where it was measured

**What this gives you:**
- No data loss from force-closed tabs mid-session
- TTFB and FCP from the initial load arrive with `screen.name = "/home"` (or
  whatever route was active) — not lost in a buffer
- LCP from a user interaction correctly attributed to the route they were on

**Implementation files:**
- `src/instrumentations/web-vitals.ts` — registers all 6 metrics, flushes on tab hide / BFCache
- `src/sdk.ts` — `Pulse.notifySoftNavigation()` calls `loggerProvider.forceFlush()`
- `src/integrations/react/useRouterTracking.ts` — calls `notifySoftNavigation()` on route change
- `src/integrations/next/useNextAppRouterTracking.ts` — same for Next.js App Router
- `src/integrations/next/useNextPagesRouterTracking.ts` — same for Next.js Pages Router

---

## Known Limitations (as of Phase 1)

1. **LCP is not re-measured per SPA route.** Only the initial page load LCP is
   captured. When a user navigates to `/products`, there is no new LCP measurement
   for that route — just whatever LCP was already measured since the page loaded.

2. **CLS is cumulative.** The CLS value reported for `/cart` includes layout shifts
   that happened on `/home` and `/products`. It is not a fresh score for `/cart`.

3. **INP is a page-lifetime worst-case.** The INP reported is the single slowest
   interaction the user had since the page loaded — not the slowest on the current route.

4. **TTFB and FCP are not meaningful per SPA route.** These measure network latency
   and first paint on the initial load. SPA navigations don't go through the network
   in the same way, so these metrics don't apply to route changes.

---

## Future: Phase 2 Options

### Option B — Datadog-style manual re-observation (all browsers)

Implement per-route vitals without relying on any browser API that isn't widely available.

**How it works:**
1. On each SPA route change, start a new `PerformancObserver` for
   `largest-contentful-paint` entries using the current timestamp as baseline —
   this gives a fresh "LCP for this route"
2. Snapshot the current cumulative CLS score at route change. Report the
   **difference** (delta) as the CLS for the departing route
3. Filter `event-timing` entries (INP source) by timestamp — only include
   interactions that happened after the last route change

**Pros:** Works in Chrome, Safari, and Firefox today. No experimental APIs.

**Cons:** Significant custom code. Need to maintain our own per-route metric state.
Essentially rebuilds what `web-vitals` v5 does internally.

---

### Option C — `web-vitals` soft-navs (progressive enhancement, recommended first)

Use the `web-vitals@soft-navs` npm distribution which adds `{ reportSoftNavs: true }`
support on top of Chrome's Soft Navigation API.

**How it works:**
```ts
import { onLCP, onCLS, onINP } from "web-vitals/soft-navs";

onLCP(emit, { reportSoftNavs: true });
onCLS(emit, { reportSoftNavs: true });
onINP(emit, { reportSoftNavs: true });
```

On Chrome 147+ (origin trial): the browser detects SPA route changes automatically
and the library fires fresh LCP, reset CLS, and scoped INP callbacks per route.

On Safari / Firefox / older Chrome: silently falls back to the existing behavior
(hard-load metrics only) — no errors, no breakage.

**Pros:** Least code. The library handles all complexity. Chrome users immediately
get per-route vitals. Other browsers are unaffected.

**Cons:** Chrome 147+ origin trial only (not GA as of mid-2026). Safari and Firefox
users still get cumulative metrics only. The API may change before GA.

---

## Recommended Path

| Phase | What | When |
|---|---|---|
| Phase 1 (done) | Flush on SPA nav, correct `screen.name` | Shipped |
| Phase 2 (planned) | `reportAllChanges: true` on CLS/INP + `web_vital.delta` + `navigation_id` — per-route aggregation via ClickHouse, all browsers today | See `PLAN-phase2-per-route-vitals.md` |
| Phase 3 (future) | Option C — `web-vitals@soft-navs` + `web_vital.context = "navigation"` once Chrome Soft Nav API reaches GA | After GA |
| Option B (deferred) | Manual re-observation (Datadog-style) for Safari/Firefox true-reset parity | After Phase 3 fails to close cross-browser gap |

**Why Option C is not Phase 2:** `reportSoftNavs` does not exist in any released npm version of `web-vitals` (verified against v5.2.0 changelog and GoogleChrome/web-vitals repo). The `soft-navs` branch is experimental only. Phase 2 delivers per-route CLS/INP aggregation for all browsers using `delta` + `navigation_id` — no browser API dependency.
