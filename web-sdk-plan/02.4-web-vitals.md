# 02.4 — Web Vitals Instrumentation

**Goal:** Capture all six Core Web Vitals as OTLP gauge metrics with rating and attribution data. No Android/iOS equivalent — web-exclusive signals.

**File:** `src/instrumentations/web-vitals.ts`
**Android equivalent:** None (web-only)

---

## Signals Produced

### `pulse.type: web_vital` — OTLP Gauge metric, one per vital per page view

| Attribute | Type | Source | Notes |
|---|---|---|---|
| `pulse.type` | string | `"web_vital"` | |
| `metric.name` | string | `"LCP"` \| `"CLS"` \| `"FID"` \| `"INP"` \| `"TTFB"` \| `"FCP"` | |
| `metric.value` | double | Raw value from `web-vitals` | ms for time-based; unitless for CLS |
| `metric.rating` | string | `"good"` \| `"needs-improvement"` \| `"poor"` | |
| `metric.id` | string | Unique ID per metric instance | For deduplication |
| `url.path` | string | `window.location.pathname` | Which page the vital was measured on |
| `url.query` | string | Sanitised query string | |

### LCP-specific attribution attributes (when available)

| Attribute | Type | Source |
|---|---|---|
| `lcp.element` | string | CSS selector of the LCP element |
| `lcp.url` | string | URL of LCP image/video (if applicable) |
| `lcp.load_delay` | double | ms from navigation start to resource load start |
| `lcp.load_time` | double | ms for resource to load |
| `lcp.render_delay` | double | ms from resource load to render |

### INP-specific attribution attributes (when available)

| Attribute | Type | Source |
|---|---|---|
| `inp.interaction_target` | string | CSS selector of interacted element |
| `inp.interaction_type` | string | `"pointer"` \| `"keyboard"` |
| `inp.input_delay` | double | ms between user input and event handler start |
| `inp.processing_time` | double | ms for event handlers to run |
| `inp.presentation_delay` | double | ms from handler end to next frame paint |

---

## Metric Thresholds

| Metric | Good | Needs Improvement | Poor | Unit |
|---|---|---|---|---|
| LCP | ≤ 2500 | 2500–4000 | > 4000 | ms |
| CLS | ≤ 0.1 | 0.1–0.25 | > 0.25 | score |
| FID | ≤ 100 | 100–300 | > 300 | ms |
| INP | ≤ 200 | 200–500 | > 500 | ms |
| TTFB | ≤ 800 | 800–1800 | > 1800 | ms |
| FCP | ≤ 1800 | 1800–3000 | > 3000 | ms |

---

## Implementation

```typescript
// src/instrumentations/web-vitals.ts
import {
  onLCP, onCLS, onFID, onINP, onTTFB, onFCP,
  type LCPMetric, type INPMetric,
} from 'web-vitals/attribution';

export function installWebVitals(gauge: Meter): void {
  const webVitalGauge = gauge.createObservableGauge('pulse.web_vital', {
    description: 'Core Web Vitals measurements',
  });

  const record = (metric: Metric, extra: Record<string, unknown> = {}) => {
    emitMetricRecord({
      'pulse.type':    'web_vital',
      'metric.name':   metric.name,
      'metric.value':  metric.value,
      'metric.rating': metric.rating,
      'metric.id':     metric.id,
      'url.path':      window.location.pathname,
      'url.query':     sanitizeQueryString(window.location.search),
      ...extra,
    });
  };

  onLCP((metric: LCPMetric) => {
    const attr = metric.attribution;
    record(metric, {
      'lcp.element':      attr?.element ?? '',
      'lcp.url':          attr?.url ?? '',
      'lcp.load_delay':   attr?.resourceLoadDelay ?? 0,
      'lcp.load_time':    attr?.resourceLoadDuration ?? 0,
      'lcp.render_delay': attr?.elementRenderDelay ?? 0,
    });
  });

  onINP((metric: INPMetric) => {
    const attr = metric.attribution;
    record(metric, {
      'inp.interaction_target':  attr?.interactionTarget ?? '',
      'inp.interaction_type':    attr?.interactionType ?? '',
      'inp.input_delay':         attr?.inputDelay ?? 0,
      'inp.processing_time':     attr?.processingDuration ?? 0,
      'inp.presentation_delay':  attr?.presentationDelay ?? 0,
    });
  });

  // Remaining vitals — no attribution needed
  onCLS(metric => record(metric));
  onFID(metric => record(metric));
  onTTFB(metric => record(metric));
  onFCP(metric => record(metric));
}
```

**Note:** Import from `web-vitals/attribution` (not plain `web-vitals`) to get the attribution data for LCP and INP.

---

## When Metrics Fire

| Metric | When reported |
|---|---|
| FCP | After first content paint (early in page load) |
| TTFB | After first byte received |
| LCP | Updates until user interaction or page hide — final value on `pagehide` |
| CLS | Updates until page hide — cumulative score |
| FID | On first user interaction (deprecated in favour of INP) |
| INP | Updates throughout session — worst interaction latency |

All metrics fire on `pagehide` with their final values. The `metric.id` ensures deduplication if the same metric fires multiple times.

---

## Edge Cases

| Case | Handling |
|---|---|
| SPA navigation — LCP resets | `web-vitals` handles per-page-view; `url.path` identifies which route |
| Page never receives user interaction | FID and INP never fire — expected |
| BFCache restore | `web-vitals` v3+ correctly handles BFCache restores and reports new LCP |
| Very fast page (FCP < 100ms) | Still emitted — `metric.rating: 'good'` |
| CLS with no layout shifts | Reports 0 — still useful as confirmation of stability |

---

## Testing

### Unit Tests (Vitest)

```typescript
it('maps LCP value to correct rating', () => {
  expect(getRating('LCP', 2000)).toBe('good');
  expect(getRating('LCP', 3000)).toBe('needs-improvement');
  expect(getRating('LCP', 5000)).toBe('poor');
});

it('emits metric.id for deduplication', () => {
  const records = captureMetrics();
  simulateLCP(2500);
  expect(records[0]['metric.id']).toBeTruthy();
});
```

### E2E (Playwright)

```typescript
test('LCP metric emitted on page load', async ({ page }) => {
  await page.goto('/test-page');
  // Interact with page to trigger final LCP report
  await page.click('body');
  const metric = await waitForMetric(receiver, 'LCP');
  expect(metric['metric.value']).toBeGreaterThan(0);
  expect(['good', 'needs-improvement', 'poor']).toContain(metric['metric.rating']);
});
```

---

## Done Criteria

- [ ] All 6 vitals (LCP, CLS, FID, INP, TTFB, FCP) emitted as gauge metrics
- [ ] `metric.rating` correct for each threshold band
- [ ] LCP attribution: `lcp.element`, `lcp.url`, `lcp.load_delay` populated when available
- [ ] INP attribution: `inp.input_delay`, `inp.processing_time`, `inp.presentation_delay` populated
- [ ] `url.path` identifies which route the vital belongs to
- [ ] `metric.id` present for deduplication
- [ ] All unit tests passing
