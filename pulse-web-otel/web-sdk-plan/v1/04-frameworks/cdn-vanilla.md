# 05.4 — CDN / Vanilla JS Integration

**Goal:** Deliver the SDK as a single UMD script that loads asynchronously via a snippet, queues API calls made before the script loads, and drains the queue after initialization — enabling zero-build-tool integration for any website.

**File:** `src/integrations/cdn/index.ts` + CDN async snippet
**Build output:** `dist/pulse.min.js` — hosted on CloudFront

---

## Async Snippet

This snippet is pasted into the `<head>` of the user's HTML page. It is **non-blocking** — the SDK loads after the page, but API calls made before load are queued and replayed.

```html
<!-- Pulse SDK Async Snippet (v1) -->
<script>
(function(w, d, s, n) {
  w[n] = w[n] || function() {
    (w[n].q = w[n].q || []).push(arguments);
  };
  w[n].q = w[n].q || [];

  var script = d.createElement(s);
  script.async = true;
  script.src = 'https://cdn.pulse.io/sdk/v1/pulse.min.js';
  d.head.appendChild(script);
})(window, document, 'script', 'pulse');

// Initialize SDK
pulse('init', 'proj_abc123', {
  otlpEndpoint: 'https://ingest.pulse.io',
});

// These calls are queued until the SDK loads:
pulse('identify', { userId: 'user_123' });
pulse('trackEvent', 'page_viewed');
</script>
```

---

## Queue Drain Mechanism

```
Before SDK loads:
  pulse('init', ...) → pushes to window.pulse.q = [['init', ...], ...]

After SDK loads:
  SDK reads window.pulse.q
  Replays all queued calls in order
  Replaces window.pulse with real API
```

### SDK-Side Queue Drain

```typescript
// src/integrations/cdn/index.ts

function installCdnApi(): void {
  // Read any calls made before we loaded
  const queue: IArguments[] = (window as any).pulse?.q ?? [];

  // Replace stub with real implementation
  (window as any).pulse = function pulseApi(command: string, ...args: unknown[]) {
    handleCommand(command, args);
  };

  // Drain the queue
  for (const call of queue) {
    handleCommand(call[0] as string, Array.from(call).slice(1));
  }
}

function handleCommand(command: string, args: unknown[]): void {
  switch (command) {
    case 'init':
      initSDK(args[0] as string, args[1] as SDKConfig);
      break;
    case 'identify':
      PulseSDK.getInstance()?.identify(args[0] as Record<string, unknown>);
      break;
    case 'trackEvent':
      PulseSDK.getInstance()?.trackEvent(args[0] as string, args[1] as Record<string, unknown>);
      break;
    case 'setUser':
      PulseSDK.getInstance()?.setUser(args[0] as string);
      break;
    case 'flush':
      PulseSDK.getInstance()?.flush();
      break;
    default:
      console.warn(`[Pulse] Unknown command: ${command}`);
  }
}

// Auto-install when script loads
installCdnApi();
```

---

## Global API Reference

After the SDK loads, `window.pulse` is a function with these commands:

```typescript
// Initialize (required first)
pulse('init', projectId: string, options?: SDKConfig): void

// User identification  
pulse('setUser', userId: string): void
pulse('identify', traits: Record<string, string | number | boolean>): void

// Interaction tracking
pulse('trackEvent', eventName: string, attributes?: Record<string, unknown>): void

// Manual error reporting
pulse('reportException', error: Error, isFatal?: boolean): void

// Force flush all pending telemetry
pulse('flush'): void
```

---

## CDN Versioning

| URL Pattern | Usage |
|---|---|
| `cdn.pulse.io/sdk/v1/pulse.min.js` | Stable v1 — auto-updated within v1 (patch/minor) |
| `cdn.pulse.io/sdk/v1.2.3/pulse.min.js` | Pinned version — no auto-updates |
| `cdn.pulse.io/sdk/latest/pulse.min.js` | Always latest (not recommended for production) |

CloudFront distribution:
- `Cache-Control: public, max-age=3600` on the version-specific URLs
- `Cache-Control: no-cache` on the `v1/` floating pointer

---

## Bundle Config (tsup)

```typescript
// tsup.config.ts — UMD build for CDN
export default defineConfig([
  // UMD build for CDN
  {
    entry: { 'pulse': 'src/integrations/cdn/index.ts' },
    format: ['iife'],
    globalName: 'PulseSDK',
    outDir: 'dist',
    minify: true,
    sourcemap: true,
    target: 'es2018',   // IE11 not supported; but ES2018 covers 97%+ of browsers
    noExternal: [/.*/],  // Bundle all dependencies into single file
  },
]);
```

---

## Subresource Integrity (SRI)

For security, publish the SRI hash alongside each release so users can pin it:

```html
<script
  async
  src="https://cdn.pulse.io/sdk/v1.2.3/pulse.min.js"
  integrity="sha384-abc123..."
  crossorigin="anonymous"
></script>
```

Generate SRI hash during CI:

```bash
openssl dgst -sha384 -binary dist/pulse.min.js | openssl base64 -A
```

---

## Edge Cases

| Case | Handling |
|---|---|
| `pulse('init', ...)` called multiple times | SDK init is idempotent; second call is ignored with a warning |
| Queue drained before `init` command | `handleCommand('trackEvent', ...)` before init is a no-op; logs a warning |
| Script blocked by ad blocker | SDK fails to load; `window.pulse.q` is never drained — data silently dropped |
| `window.pulse` already defined | Check for conflict on load; warn if `window.pulse` exists and isn't our stub |
| Content Security Policy (CSP) blocks CDN | User must add `https://cdn.pulse.io` to their CSP `script-src` directive |
| User has `async` attribute but page is heavy | `installCdnApi()` runs after DOMContentLoaded; buffered events still captured via `buffered: true` in PerformanceObserver |
| Script served over HTTP (not HTTPS) | `sendBeacon` and `fetch` require same-origin or CORS; SDK warns and may fail to send |

---

## Testing

### Unit Tests (Vitest)

```typescript
it('queues commands before SDK loads', () => {
  // Set up stub (as the snippet does)
  (window as any).pulse = function() {
    ((window as any).pulse.q = (window as any).pulse.q || []).push(arguments);
  };
  (window as any).pulse.q = [];

  (window as any).pulse('trackEvent', 'cart_viewed', { value: 99 });

  expect((window as any).pulse.q).toHaveLength(1);
  expect((window as any).pulse.q[0][0]).toBe('trackEvent');
});

it('drains queue on installCdnApi()', () => {
  (window as any).pulse = { q: [['trackEvent', 'cart_viewed', {}]] };

  const trackSpy = vi.fn();
  vi.spyOn(PulseSDK.prototype, 'trackEvent').mockImplementation(trackSpy);
  // Simulate init being in the queue before trackEvent
  (window as any).pulse.q.unshift(['init', 'proj_test', {}]);

  installCdnApi();

  expect(trackSpy).toHaveBeenCalledWith('cart_viewed', {});
});

it('handles unknown commands without throwing', () => {
  expect(() => handleCommand('unknownCommand', [])).not.toThrow();
});
```

### E2E (Playwright)

```typescript
test('CDN snippet initializes SDK and captures clicks', async ({ page }) => {
  await page.goto('/cdn-test.html');  // Page with async snippet
  await page.click('button');
  await page.waitForTimeout(1500);

  const log = await waitForLog(receiver, 'app.click');
  expect(log).toBeDefined();
});

test('commands queued before SDK load are executed', async ({ page }) => {
  // Page calls pulse('trackEvent', 'page_viewed') in <head> before script loads
  await page.goto('/cdn-test-prequeue.html');
  const span = await waitForLog(receiver, 'interaction');
  // or verify a queued event fired
});
```

---

## Done Criteria

- [ ] Async snippet is non-blocking (`async` attribute)
- [ ] `window.pulse` stub queues commands before SDK loads
- [ ] Queue drained in correct order after `installCdnApi()` runs
- [ ] `pulse('init', ...)` is idempotent
- [ ] All public commands (`init`, `trackEvent`, `identify`, `setUser`, `flush`, `reportException`) handled
- [ ] Unknown commands log a warning and do not throw
- [ ] SRI hash generated and published with each release
- [ ] CDN URL versioning documented (`v1/` vs pinned)
- [ ] All unit tests passing
