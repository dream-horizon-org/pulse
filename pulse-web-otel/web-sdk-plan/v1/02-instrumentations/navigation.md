# 02.5 — Navigation Instrumentation

**Goal:** Track initial page load performance and SPA route changes as spans, matching the Android `ActivitySession`/`screen_load`/`screen_interactive` signal types. Also owns the `screen.name` resolution system — the web equivalent of Android's `ScreenNameExtractor`.

**File:** `src/instrumentations/navigation.ts`
**Android equivalent:** `ActivityInstrumentation`, `FragmentInstrumentation`, `DefaultScreenNameExtractor`, `VisibleScreenTrackerImpl`

---

## `screen.name` — Android vs Web

On Android, `screen.name` is extracted from the **Activity/Fragment class name** (e.g. `ProductDetailActivity`) or a `@RumScreenName("ProductDetail")` annotation. It is deterministic, stable, and low-cardinality.

On web, the URL is the only equivalent — but raw URLs are **high-cardinality and noisy**:

```
Android                          Web (raw)                    Web (normalised)
────────────────────────────     ────────────────────────     ────────────────────────────
ProductDetailActivity     →      /products/123         →      ProductDetail
ProductDetailActivity     →      /products/456         →      ProductDetail
CheckoutActivity          →      /checkout             →      Checkout
@RumScreenName("Cart")    →      /cart                 →      Cart
```

Without normalisation, `screen.name` has thousands of unique values (one per product ID), making dashboards and funnels useless. The solution is **route pattern registration** — mapping URL patterns to human-readable names.

### `screen.name` Resolution Chain

The SDK resolves `screen.name` by trying each step in order, stopping at the first match:

```
1. Manual override  →  pulse.setScreenName('CustomName')           [highest priority]
2. Route patterns   →  { pattern: '/products/:id', name: 'ProductDetail' }
3. Path segments    →  /products/123 → strip trailing ID segments → /products
4. Raw pathname     →  window.location.pathname as-is              [lowest priority / fallback]
```

---

## Signals Produced

### `pulse.type: screen_load` — initial page load span

| Attribute | Type | Source | Android Equivalent |
|---|---|---|---|
| `pulse.type` | string | `"screen_load"` | `screen_load` |
| `screen.name` | string | `window.location.pathname` | `screen.name` |
| `url.path` | string | `window.location.pathname` | replaces `activity.name` |
| `page.title` | string | `document.title` | — |
| `navigation.type` | string | `"navigate"` \| `"reload"` \| `"back_forward"` | — |
| `page.load_time` | long | `loadEventEnd - startTime` (ms) | — |
| `dns.time` | long | `domainLookupEnd - domainLookupStart` (ms) | — |
| `tcp.time` | long | `connectEnd - connectStart` (ms) | — |
| `ttfb` | long | `responseStart - requestStart` (ms) | — |
| `dom.processing_time` | long | `domComplete - domInteractive` (ms) | — |

### `pulse.type: screen_interactive` — time-to-interactive span

| Attribute | Type | Source | Android Equivalent |
|---|---|---|---|
| `pulse.type` | string | `"screen_interactive"` | `screen_interactive` |
| `screen.name` | string | `window.location.pathname` | `screen.name` |
| `url.path` | string | `window.location.pathname` | replaces `activity.name` |
| `tti` | long | `domInteractive - startTime` (ms) | — |

### `pulse.type: screen_session` — time spent on a route

| Attribute | Type | Source | Android Equivalent |
|---|---|---|---|
| `pulse.type` | string | `"screen_session"` | `screen_session` |
| `screen.name` | string | Current URL path | `screen.name` |
| `last.screen.name` | string | Previous route | `last.screen.name` |
| `url.path` | string | `window.location.pathname` | replaces `activity.name` |
| `session.duration` | long | Time on route (ms) | — |

---

## Implementation

```typescript
// src/instrumentations/navigation.ts

export class NavigationInstrumentation {
  private currentRoute = window.location.pathname;
  private routeStartTime = performance.now();
  private lastRoute = '';

  install(): void {
    // 1. Initial page load
    this.capturePageLoad();

    // 2. SPA route changes via History API
    this.patchHistoryApi();
    window.addEventListener('popstate', () => this.onRouteChange());
  }

  // Called by framework integrations (React Router, Next.js, Vue Router)
  // when they detect a route change more accurately than History patching
  onRouteChange(newPath = window.location.pathname): void {
    this.endCurrentSession();
    this.lastRoute     = this.currentRoute;
    this.currentRoute  = newPath;
    this.routeStartTime = performance.now();
  }

  private capturePageLoad(): void {
    const waitForLoad = () => {
      const nav = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming;
      if (!nav || nav.loadEventEnd === 0) {
        // Not ready yet — wait for load event
        window.addEventListener('load', () => this.capturePageLoad(), { once: true });
        return;
      }

      createSpan('page_load', {
        startTime: nav.startTime,
        endTime:   nav.loadEventEnd,
        attributes: {
          'pulse.type':         'screen_load',
          'screen.name':        window.location.pathname,
          'url.path':           window.location.pathname,
          'page.title':         document.title,
          'navigation.type':    nav.type,
          'page.load_time':     Math.round(nav.loadEventEnd - nav.startTime),
          'dns.time':           Math.round(nav.domainLookupEnd - nav.domainLookupStart),
          'tcp.time':           Math.round(nav.connectEnd - nav.connectStart),
          'ttfb':               Math.round(nav.responseStart - nav.requestStart),
          'dom.processing_time': Math.round(nav.domComplete - nav.domInteractive),
        },
      });

      createSpan('page_interactive', {
        startTime: nav.startTime,
        endTime:   nav.domInteractive,
        attributes: {
          'pulse.type':  'screen_interactive',
          'screen.name': window.location.pathname,
          'url.path':    window.location.pathname,
          'tti':         Math.round(nav.domInteractive - nav.startTime),
        },
      });
    };

    if (document.readyState === 'complete') {
      waitForLoad();
    } else {
      window.addEventListener('load', waitForLoad, { once: true });
    }
  }

  private endCurrentSession(): void {
    const duration = performance.now() - this.routeStartTime;
    if (duration < 100) return; // ignore sub-100ms accidental navigations

    createSpan('screen_session', {
      startTime: this.routeStartTime,
      endTime:   performance.now(),
      attributes: {
        'pulse.type':        'screen_session',
        'screen.name':       this.currentRoute,
        'last.screen.name':  this.lastRoute,
        'url.path':          this.currentRoute,
        'session.duration':  Math.round(duration),
      },
    });
  }

  /** Public API — allows app code to set a friendly screen name for the current route */
  setScreenName(name: string): void {
    this.manualScreenName = name;
    this.currentRoute = name;
    // Update the global attribute so all subsequent spans carry the new name
    globalAttributeStore.set('screen.name', name);
  }

  /** Resolve screen.name using the 4-step fallback chain */
  resolveScreenName(pathname: string): string {
    // 1. Manual override (highest priority)
    if (this.manualScreenName) return this.manualScreenName;

    // 2. Route pattern match
    const matched = this.matchRoutePattern(pathname);
    if (matched) return matched;

    // 3. Path segment heuristic — strip trailing numeric/UUID/hash segments
    const heuristic = stripDynamicSegments(pathname);
    if (heuristic && heuristic !== '/') return heuristic;

    // 4. Raw pathname fallback
    return pathname;
  }

  private matchRoutePattern(pathname: string): string | null {
    for (const { pattern, name } of this.routePatterns) {
      if (matchesPattern(pattern, pathname)) return name;
    }
    return null;
  }

  private patchHistoryApi(): void {
    const _push    = history.pushState.bind(history);
    const _replace = history.replaceState.bind(history);

    history.pushState = (...args) => {
      _push(...args);
      this.onRouteChange();
    };
    history.replaceState = (...args) => {
      _replace(...args);
      // replaceState doesn't create a new session — just update the route name
      this.currentRoute = window.location.pathname;
    };
  }
}
```

---

## `screen.name` — Full Implementation

### Route Pattern Config

Configured at SDK init:

```typescript
PulseSDK.init({
  projectId: 'proj_abc123',
  routePatterns: [
    { pattern: '/products/:id',            name: 'ProductDetail' },
    { pattern: '/products/:id/reviews',    name: 'ProductReviews' },
    { pattern: '/orders/:orderId',         name: 'OrderDetail' },
    { pattern: '/users/:userId/cart',      name: 'UserCart' },
    { pattern: '/checkout',               name: 'Checkout' },
    { pattern: '/search',                 name: 'Search' },
  ],
});
```

Patterns use `:param` syntax (same as React Router, Express, Vue Router). Matching is prefix-exact: `/products/:id` matches `/products/123` but not `/products/123/reviews`.

### Pattern Matching Implementation

```typescript
interface RoutePattern {
  pattern: string;   // e.g. '/products/:id'
  name: string;      // e.g. 'ProductDetail'
}

function matchesPattern(pattern: string, pathname: string): boolean {
  // Convert '/products/:id' → regex: ^/products/[^/]+$
  const regexStr = pattern
    .replace(/:[^/]+/g, '[^/]+')   // :param → [^/]+
    .replace(/\*/g, '.*');          // * → .*

  return new RegExp(`^${regexStr}$`).test(pathname);
}
```

### Step 3 Heuristic — Strip Dynamic Segments

When no route pattern matches, strip segments that look like IDs:

```typescript
function stripDynamicSegments(pathname: string): string {
  const segments = pathname.split('/').filter(Boolean);

  const cleanSegments = segments.filter(segment => {
    // Drop segments that are purely numeric: 123, 456789
    if (/^\d+$/.test(segment)) return false;
    // Drop UUID segments: 550e8400-e29b-41d4-a716-446655440000
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(segment)) return false;
    // Drop short hash-like segments: a3f2b1, abc123 (looks like a DB ID)
    if (/^[a-f0-9]{6,24}$/.test(segment)) return false;
    return true;
  });

  return '/' + cleanSegments.join('/');
}
```

**Examples:**

| Raw pathname | After heuristic |
|---|---|
| `/products/123` | `/products` |
| `/orders/550e8400-e29b-41d4-a716-446655440000` | `/orders` |
| `/users/42/settings` | `/users/settings` |
| `/checkout/confirm` | `/checkout/confirm` ← no dynamic segment, unchanged |
| `/search?q=shoes` | `/search` ← query string handled separately |

### Manual Override API

```typescript
// In a React component after a route render:
const { setScreenName } = usePulse();

useEffect(() => {
  setScreenName('ProductDetail'); // Overrides URL-derived name for current route
}, [productId]);
```

Or in plain JS:

```javascript
pulse('setScreenName', 'ProductDetail');
```

The manual override is **cleared on the next navigation** so it doesn't bleed into subsequent routes.

### `screen.name` as a Global Attribute

`screen.name` and `last.screen.name` are stamped on **every span and log** via `PulseGlobalAttributesProcessor` — identical to Android's `ScreenAttributesSpanProcessor`. Every click, network request, error, and long task automatically carries the current screen name.

```typescript
// src/foundation/global-attributes-processor.ts

export class PulseGlobalAttributesProcessor implements SpanProcessor, LogRecordProcessor {
  onStart(span: Span): void {
    span.setAttributes({
      'screen.name':      globalAttributeStore.get('screen.name') ?? '',
      'last.screen.name': globalAttributeStore.get('last.screen.name') ?? '',
      'session.id':       getSessionId(),
      // ... other global attributes from 01-foundation
    });
  }
}
```

When `NavigationInstrumentation.onRouteChange()` fires:
1. `last.screen.name` ← current `screen.name`
2. `screen.name` ← `resolveScreenName(newPathname)`
3. `manualScreenName` ← cleared
4. Both values updated in `globalAttributeStore`

---

## Edge Cases

| Case | Handling |
|---|---|
| `loadEventEnd === 0` at time of reading | Wait for `window.load` event before reading Navigation Timing |
| BFCache restore | Handled in doc 02.10 — `navigation.type: 'back_forward_cache'` |
| `replaceState` for URL cleanup (e.g. removing auth tokens) | Updates `currentRoute` but doesn't create a new session |
| Sub-100ms navigations (e.g. hash changes) | Ignore — not a meaningful session |
| Tab hidden during navigation | `screen_session` ends on `pagehide` via `endCurrentSession()` |
| Framework router fires before `pushState` | Framework integrations call `onRouteChange()` directly, bypassing the patch |
| No `routePatterns` configured | Falls through to heuristic (strip IDs) then raw pathname |
| Route pattern has trailing slash mismatch | Normalise both pattern and pathname with `pathname.replace(/\/$/, '')` before match |
| `/products/123` matches both `/products/:id` and `/products/:id/reviews` is wrong | Patterns evaluated in config order — put more specific patterns first |
| `setScreenName()` called but user navigates | Manual override cleared in `onRouteChange()` — does not persist to next route |
| Query string in pathname (e.g. `/search?q=shoes`) | Heuristic strips query string; `screen.name` is `/search` |
| Hash routes (`/#/products/123`) | `window.location.pathname` = `/`; use `window.location.hash` for hash-based SPAs (opt-in) |
| UUID in middle of path (`/a/550e8400/b`) | Heuristic strips UUID segment → `/a/b` |

---

## Testing

### Unit Tests (Vitest + JSDOM)

```typescript
it('creates screen_session span on pushState', () => {
  const spans = captureSpans();
  history.pushState({}, '', '/new-page');
  expect(spans.find(s => s['pulse.type'] === 'screen_session')).toBeDefined();
  expect(spans[0]['screen.name']).toBe('/');
});

it('resolves screen.name from route pattern', () => {
  const inst = new NavigationInstrumentation({
    routePatterns: [{ pattern: '/products/:id', name: 'ProductDetail' }],
  });
  expect(inst.resolveScreenName('/products/123')).toBe('ProductDetail');
  expect(inst.resolveScreenName('/products/456')).toBe('ProductDetail');
});

it('falls back to heuristic when no pattern matches', () => {
  const inst = new NavigationInstrumentation({ routePatterns: [] });
  expect(inst.resolveScreenName('/orders/12345')).toBe('/orders');
  expect(inst.resolveScreenName('/users/550e8400-e29b-41d4-a716-446655440000')).toBe('/users');
});

it('uses raw pathname when heuristic produces root only', () => {
  const inst = new NavigationInstrumentation({ routePatterns: [] });
  expect(inst.resolveScreenName('/checkout')).toBe('/checkout'); // no ID to strip
});

it('manual setScreenName overrides route pattern', () => {
  const inst = new NavigationInstrumentation({
    routePatterns: [{ pattern: '/products/:id', name: 'ProductDetail' }],
  });
  inst.setScreenName('FeaturedProduct');
  expect(inst.resolveScreenName('/products/123')).toBe('FeaturedProduct');
});

it('clears manual override after navigation', () => {
  const inst = new NavigationInstrumentation({ routePatterns: [] });
  inst.install();
  inst.setScreenName('Override');
  history.pushState({}, '', '/new-page');
  expect(inst.resolveScreenName('/new-page')).not.toBe('Override');
});

it('ignores sub-100ms navigations', () => {
  const spans = captureSpans();
  // Immediately navigate again
  history.pushState({}, '', '/a');
  history.pushState({}, '', '/b');
  // Only the second session (from /a) should be long enough
  const sessions = spans.filter(s => s['pulse.type'] === 'screen_session');
  expect(sessions.length).toBeLessThanOrEqual(1);
});
```

### E2E (Playwright)

```typescript
test('page load creates screen_load span with timing data', async ({ page }) => {
  await page.goto('/test-page');
  const span = await waitForSpan(receiver, 'screen_load');
  expect(span['page.load_time']).toBeGreaterThan(0);
  expect(span['ttfb']).toBeGreaterThanOrEqual(0);
  expect(span['navigation.type']).toBe('navigate');
});

test('SPA navigation creates screen_session span', async ({ page }) => {
  await page.goto('/test-page');
  await page.click('[data-route="/about"]');
  const span = await waitForSpan(receiver, 'screen_session');
  expect(span['screen.name']).toBe('/test-page');
  expect(span['last.screen.name']).toBe('');
});
```

---

## Done Criteria

- [ ] `screen_load` span emitted with `page.load_time`, `ttfb`, `dns.time`, `tcp.time`, `dom.processing_time`
- [ ] `screen_interactive` span emitted with `tti`
- [ ] `navigation.type` correct (`navigate` / `reload` / `back_forward`)
- [ ] `pushState()` triggers a new `screen_session` and ends the previous one
- [ ] `screen.name` updates on every route change
- [ ] `last.screen.name` correctly set on second and subsequent navigations
- [ ] Sub-100ms navigations do not emit sessions
- [ ] `routePatterns` config maps `/products/:id` → `ProductDetail`
- [ ] `matchesPattern()` correctly handles `:param` wildcards
- [ ] Heuristic strips numeric, UUID, and hex ID segments from pathnames
- [ ] `setScreenName()` overrides `screen.name` for current route only
- [ ] Manual override cleared on next navigation
- [ ] `screen.name` and `last.screen.name` stamped on all spans/logs via global processor
- [ ] All unit tests passing
