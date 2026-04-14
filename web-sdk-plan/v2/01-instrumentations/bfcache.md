# 02.10 — BFCache (Back/Forward Cache) Instrumentation

**Goal:** Detect when a page is restored from the browser's Back/Forward Cache and emit a telemetry signal — enabling debugging of post-BFCache issues (stale data, broken websockets, expired auth tokens) and accurate navigation type attribution.

**File:** `src/instrumentations/bfcache.ts`
**Android equivalent:** None (browser-specific)

---

## Background: What Is BFCache?

Modern browsers (Chrome, Firefox, Safari) keep pages in memory when the user navigates away. On back/forward navigation, the page is instantly restored from the frozen state — no network round-trip, no re-execution of JavaScript.

This is great for performance but breaks several assumptions:
- `pagehide` fired with `event.persisted = true` (page frozen, not destroyed)
- `pageshow` fired with `event.persisted = true` (page restored from cache)
- WebSockets are closed; `setInterval` / `setTimeout` are paused
- In-memory state may be stale; tokens may have expired
- Navigation Timing `type` is `"back_forward"` but RestoreType identifies BFCache specifically

---

## Signals Produced

### `pulse.type: bfcache.restore` — page restored from Back/Forward Cache

| Attribute | Type | Source | Notes |
|---|---|---|---|
| `pulse.type` | string | `"bfcache.restore"` | |
| `navigation.type` | string | `"back_forward_cache"` | Distinguishes from regular back/forward |
| `url.path` | string | `window.location.pathname` | Route being restored |
| `page.title` | string | `document.title` | |
| `bfcache.time_in_cache` | long | `pageshow.timeStamp - pagehide.timeStamp` (ms) | How long page was cached |

### `pulse.type: bfcache.evict` — page evicted from cache without restore

> Rare; only detectable via `window.onunload` in environments that still call it after `pagehide(persisted=true)`. Emit best-effort.

| Attribute | Type | Source |
|---|---|---|
| `pulse.type` | string | `"bfcache.evict"` |
| `url.path` | string | `window.location.pathname` |

---

## Implementation

```typescript
// src/instrumentations/bfcache.ts

export class BFCacheInstrumentation {
  private hiddenAt = 0;

  install(): void {
    window.addEventListener('pagehide', this.onPageHide);
    window.addEventListener('pageshow', this.onPageShow);
  }

  uninstall(): void {
    window.removeEventListener('pagehide', this.onPageHide);
    window.removeEventListener('pageshow', this.onPageShow);
  }

  private onPageHide = (e: PageTransitionEvent): void => {
    if (e.persisted) {
      // Page is being put into BFCache — note the timestamp
      this.hiddenAt = e.timeStamp;
    }
  };

  private onPageShow = (e: PageTransitionEvent): void => {
    if (!e.persisted) return;  // Normal page load — not a BFCache restore

    const timeInCache = this.hiddenAt > 0
      ? Math.round(e.timeStamp - this.hiddenAt)
      : 0;

    emitLogRecord({
      'pulse.type':           'bfcache.restore',
      'navigation.type':      'back_forward_cache',
      'url.path':             window.location.pathname,
      'page.title':           document.title,
      'bfcache.time_in_cache': timeInCache,
    });

    // Reset so subsequent navigations don't use a stale hiddenAt
    this.hiddenAt = 0;

    // Re-arm instrumentations that need refreshing after BFCache restore
    this.onBFCacheRestore();
  };

  private onBFCacheRestore(): void {
    // Notify other instrumentations that page has been restored
    // e.g., navigation instrumentation should reset routeStartTime
    window.dispatchEvent(new CustomEvent('pulse:bfcache-restore'));
  }
}
```

### Integration with Navigation Instrumentation (02.5)

In `NavigationInstrumentation.install()`, listen for the custom event:

```typescript
window.addEventListener('pulse:bfcache-restore', () => {
  // Treat BFCache restore as a new navigation — reset session timer
  this.onRouteChange(window.location.pathname);
});
```

### Integration with `web-vitals` (02.4)

`web-vitals` v3+ handles BFCache automatically — LCP, CLS, and INP are re-measured after `pageshow(persisted=true)`. No extra code needed.

---

## Testing BFCache in Chrome DevTools

1. Open DevTools → Application → Back/Forward Cache
2. Navigate away and back
3. "Test Back/Forward Cache" button forces a BFCache cycle

Chrome's BFCache is blocked by unload listeners, open IndexedDB transactions, active WebSockets, and several other conditions listed at [web.dev/bfcache](https://web.dev/bfcache/).

---

## Edge Cases

| Case | Handling |
|---|---|
| Page has `unload` listener | `unload` blocks BFCache in most browsers; `pagehide.persisted` will be `false` |
| Safari's different BFCache timing | `pageshow` fires correctly; `timeStamp` may be relative to navigation start |
| Multiple BFCache cycles | `hiddenAt` resets after each restore so each cycle is measured independently |
| `hiddenAt === 0` on first pageshow | Means no `pagehide(persisted)` was seen; `time_in_cache` reported as `0` |
| BFCache eviction (memory pressure) | Cannot be reliably detected; omit rather than guess |
| Service Worker + BFCache | Service Workers can coexist with BFCache in Chrome 87+ |

---

## BFCache Eligibility Checklist

Common reasons a page fails to enter BFCache (instrumentation can't fix these, but knowing they happened helps diagnose missing `bfcache.restore` events):

| Blocker | Effect |
|---|---|
| `window.onunload` listener | Completely blocks BFCache in Chrome/Firefox |
| Open WebSocket | Prevents BFCache in most browsers |
| `Cache-Control: no-store` | Prevents BFCache in Safari |
| Active IndexedDB transaction | Prevents BFCache |
| `beforeunload` listener (without `unload`) | Allowed in Chrome 96+; blocked in older versions |

---

## Testing

### Unit Tests (Vitest + JSDOM)

```typescript
it('emits bfcache.restore on pageshow with persisted=true', () => {
  const records = captureLogRecords();
  const inst = new BFCacheInstrumentation();
  inst.install();

  // Simulate page going into cache
  window.dispatchEvent(Object.assign(new PageTransitionEvent('pagehide'), { persisted: true }));
  // Simulate BFCache restore
  window.dispatchEvent(Object.assign(new PageTransitionEvent('pageshow'), { persisted: true }));

  expect(records[0]['pulse.type']).toBe('bfcache.restore');
  expect(records[0]['navigation.type']).toBe('back_forward_cache');
});

it('does not emit on normal pageshow (persisted=false)', () => {
  const records = captureLogRecords();
  const inst = new BFCacheInstrumentation();
  inst.install();

  window.dispatchEvent(Object.assign(new PageTransitionEvent('pageshow'), { persisted: false }));

  expect(records).toHaveLength(0);
});

it('dispatches pulse:bfcache-restore custom event', () => {
  let fired = false;
  window.addEventListener('pulse:bfcache-restore', () => { fired = true; });

  const inst = new BFCacheInstrumentation();
  inst.install();
  window.dispatchEvent(Object.assign(new PageTransitionEvent('pagehide'), { persisted: true }));
  window.dispatchEvent(Object.assign(new PageTransitionEvent('pageshow'), { persisted: true }));

  expect(fired).toBe(true);
});
```

### E2E (Playwright)

> BFCache testing requires a real browser (not jsdom). Use Playwright with Chromium.

```typescript
test('BFCache restore emits bfcache.restore record', async ({ page }) => {
  await page.goto('/test-page');
  await page.goto('about:blank');   // navigate away (page goes into BFCache)
  await page.goBack();              // restore from BFCache

  const record = await waitForLog(receiver, 'bfcache.restore');
  expect(record['navigation.type']).toBe('back_forward_cache');
  expect(record['bfcache.time_in_cache']).toBeGreaterThan(0);
});
```

---

## Done Criteria

- [ ] `bfcache.restore` emitted when `pageshow` fires with `persisted: true`
- [ ] `navigation.type: "back_forward_cache"` distinguishes from regular back/forward
- [ ] `bfcache.time_in_cache` measures time from freeze to restore
- [ ] `pulse:bfcache-restore` custom event fires so other instrumentations can re-arm
- [ ] No emit on normal page loads (`persisted: false`)
- [ ] Navigation instrumentation resets session timer on BFCache restore
- [ ] E2E test passes in real Chromium browser
- [ ] All unit tests passing
