# 02.7 — Resource Timing Instrumentation

**Goal:** Capture every sub-resource load (scripts, stylesheets, images, fonts, XHR/fetch via ResourceTiming) as a log record with full timing breakdown — web-exclusive signal with no Android/iOS equivalent.

**File:** `src/instrumentations/resource-timing.ts`
**Android equivalent:** None (web-only)

---

## Signals Produced

### `pulse.type: resource_load` — one log record per resource

| Attribute | Type | Source | Notes |
|---|---|---|---|
| `pulse.type` | string | `"resource_load"` | |
| `resource.url` | string | `entry.name` (sanitised) | Full URL of the resource |
| `resource.initiator_type` | string | `entry.initiatorType` | `"script"` \| `"link"` \| `"img"` \| `"fetch"` \| `"xmlhttprequest"` \| `"other"` |
| `resource.duration` | long | `entry.duration` (ms, rounded) | Total load time |
| `resource.transfer_size` | long | `entry.transferSize` (bytes) | 0 = cached; `> 0` = transferred |
| `resource.encoded_size` | long | `entry.encodedBodySize` (bytes) | Compressed size on wire |
| `resource.decoded_size` | long | `entry.decodedBodySize` (bytes) | Decompressed body size |
| `resource.dns_time` | long | `domainLookupEnd - domainLookupStart` (ms) | |
| `resource.tcp_time` | long | `connectEnd - connectStart` (ms) | |
| `resource.ttfb` | long | `responseStart - requestStart` (ms) | Time to first byte |
| `resource.cache_hit` | boolean | `transferSize === 0 && decodedBodySize > 0` | True = served from cache |
| `url.path` | string | `window.location.pathname` | Page the resource loaded on |

---

## Implementation

```typescript
// src/instrumentations/resource-timing.ts

export class ResourceTimingInstrumentation {
  private observer?: PerformanceObserver;
  private ignoredPatterns: (string | RegExp)[] = [];

  constructor(private config: { ignoredUrls?: (string | RegExp)[] } = {}) {
    this.ignoredPatterns = config.ignoredUrls ?? [];
  }

  install(): void {
    if (!('PerformanceObserver' in window)) return;

    this.observer = new PerformanceObserver(list => {
      for (const entry of list.getEntries() as PerformanceResourceTiming[]) {
        this.processEntry(entry);
      }
    });

    this.observer.observe({ type: 'resource', buffered: true });
  }

  uninstall(): void {
    this.observer?.disconnect();
  }

  private processEntry(entry: PerformanceResourceTiming): void {
    // Skip OTLP and other internal URLs
    if (this.isIgnored(entry.name)) return;

    // Skip requests already captured by network instrumentation
    // (fetch/xhr timing is richer in 02.2 — avoid double-counting)
    if (
      entry.initiatorType === 'fetch' ||
      entry.initiatorType === 'xmlhttprequest'
    ) return;

    const cacheHit = entry.transferSize === 0 && entry.decodedBodySize > 0;

    emitLogRecord({
      'pulse.type':              'resource_load',
      'resource.url':            sanitizeUrl(entry.name),
      'resource.initiator_type': entry.initiatorType,
      'resource.duration':       Math.round(entry.duration),
      'resource.transfer_size':  entry.transferSize,
      'resource.encoded_size':   entry.encodedBodySize,
      'resource.decoded_size':   entry.decodedBodySize,
      'resource.dns_time':       Math.round(entry.domainLookupEnd - entry.domainLookupStart),
      'resource.tcp_time':       Math.round(entry.connectEnd - entry.connectStart),
      'resource.ttfb':           Math.round(entry.responseStart - entry.requestStart),
      'resource.cache_hit':      cacheHit,
      'url.path':                window.location.pathname,
    });
  }

  private isIgnored(url: string): boolean {
    return this.ignoredPatterns.some(pattern =>
      typeof pattern === 'string' ? url.includes(pattern) : pattern.test(url)
    );
  }
}

function sanitizeUrl(url: string): string {
  try {
    const u = new URL(url);
    u.search = ''; // strip query params (may contain tokens)
    return u.toString();
  } catch {
    return url;
  }
}
```

---

## Cache Hit Detection

| `transferSize` | `decodedBodySize` | Verdict |
|---|---|---|
| `0` | `> 0` | Cache hit (disk or memory cache) |
| `> 0` | `> 0` | Network transfer |
| `0` | `0` | Opaque cross-origin response (`no-cors`) |

`transferSize` is only available when the resource is same-origin or the server sends `Timing-Allow-Origin` headers. Cross-origin resources without this header report `0` for all timing breakdowns but still report `duration`.

---

## Edge Cases

| Case | Handling |
|---|---|
| Cross-origin without `Timing-Allow-Origin` | `dns_time`, `tcp_time`, `ttfb` will be `0` — emit with whatever is available |
| Service Worker intercept | `entry.workerStart > 0` — `duration` still accurate |
| HTTP/2 multiplexed (shared connection) | `connectEnd - connectStart = 0` — normal; `tcp_time` will be `0` |
| Data URLs (`data:image/...`) | Skip — `entry.name` starts with `data:` |
| Blob URLs | Skip — `entry.name` starts with `blob:` |
| Very large pages (100+ resources) | Buffer overflow handled by browser; `buffered: true` captures up to the buffer limit |
| fetch/XHR duplicates | Explicitly skipped (`initiatorType === 'fetch'` check) to avoid double-counting with 02.2 |

---

## Testing

### Unit Tests (Vitest)

```typescript
it('emits resource_load for script entries', () => {
  const records = captureLogRecords();
  simulateResourceEntry({
    initiatorType: 'script',
    name: 'https://cdn.example.com/app.js',
    duration: 120,
    transferSize: 45000,
    encodedBodySize: 45000,
    decodedBodySize: 120000,
    domainLookupEnd: 10, domainLookupStart: 5,
    connectEnd: 20, connectStart: 10,
    responseStart: 80, requestStart: 22,
  });
  expect(records[0]['pulse.type']).toBe('resource_load');
  expect(records[0]['resource.initiator_type']).toBe('script');
  expect(records[0]['resource.cache_hit']).toBe(false);
  expect(records[0]['resource.dns_time']).toBe(5);
});

it('detects cache hit when transferSize is 0', () => {
  const records = captureLogRecords();
  simulateResourceEntry({
    initiatorType: 'img',
    name: 'https://cdn.example.com/logo.png',
    duration: 2,
    transferSize: 0,
    encodedBodySize: 0,
    decodedBodySize: 8000,
  });
  expect(records[0]['resource.cache_hit']).toBe(true);
});

it('skips fetch initiator type (handled by network instrumentation)', () => {
  const records = captureLogRecords();
  simulateResourceEntry({ initiatorType: 'fetch', name: 'https://api.example.com/data' });
  expect(records).toHaveLength(0);
});

it('strips query params from resource URL', () => {
  const records = captureLogRecords();
  simulateResourceEntry({
    initiatorType: 'script',
    name: 'https://cdn.example.com/app.js?v=abc123',
  });
  expect(records[0]['resource.url']).toBe('https://cdn.example.com/app.js');
});
```

### E2E (Playwright)

```typescript
test('script load emits resource_load record', async ({ page }) => {
  await page.goto('/test-page');
  const record = await waitForLog(receiver, 'resource_load', {
    'resource.initiator_type': 'script',
  });
  expect(record['resource.duration']).toBeGreaterThan(0);
});

test('cached resource has cache_hit true', async ({ page }) => {
  await page.goto('/test-page');
  await page.reload(); // second load — assets should be cached
  const record = await waitForLog(receiver, 'resource_load', {
    'resource.cache_hit': true,
  });
  expect(record).toBeDefined();
});
```

---

## Done Criteria

- [ ] Every non-XHR/fetch sub-resource emits `resource_load` with `duration`, `transfer_size`, `initiator_type`
- [ ] `resource.cache_hit` correctly identifies cached responses
- [ ] DNS, TCP, TTFB breakdown present for same-origin resources
- [ ] Query params stripped from `resource.url`
- [ ] `fetch` and `xmlhttprequest` entries skipped (handled by 02.2)
- [ ] Data URLs and blob URLs skipped
- [ ] All unit tests passing
