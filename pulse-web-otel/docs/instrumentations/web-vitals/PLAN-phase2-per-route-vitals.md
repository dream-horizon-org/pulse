# Web Vitals Phase 2 — Per-Route Vitals

**Status:** Plan only — reply "approve implementation" to start Phase 5.

---

## 1. What Sentry actually does (verified against source)

Researched `getsentry/sentry-javascript` source directly.

| Vital | Sentry behaviour |
|-------|-----------------|
| LCP | Captured once at initial load. **Stopped and finalized when first SPA navigation starts.** No LCP on subsequent routes. |
| CLS | Same — finalized on first SPA navigation, tracking stops. |
| FCP / TTFB | Initial load only. Navigation-timing derived, no re-measurement possible. |
| INP | Continuous — per-interaction spans emitted throughout the session, **not reset per route**. |

Sentry's `startTrackingWebVitals()` is called **once** at SDK init. It is never re-registered after SPA navigations. Source: `packages/browser-utils/src/metrics/browserMetrics.ts`, `lcp.ts`, `cls.ts`.

`reportSoftNavs` **does not exist in any released npm version** of `web-vitals`. It is on an experimental `soft-navs` branch of GoogleChrome/web-vitals only. Chrome Soft Navigations API is still an origin trial (Chrome 139+, not GA). Upgrading to v5 gives nothing here.

**Pulse Phase 1 already matches Sentry** for the flush-on-nav approach.

---

## 2. What we can actually do per-navigation (all browsers, today)

The `web-vitals` library's `Metric` interface has two value fields:

| Field | Meaning |
|-------|---------|
| `value` | Cumulative score from page load to now |
| `delta` | **Change since the last callback invocation** |

`onCLS` and `onINP` accept `{ reportAllChanges: true }`, which makes them fire on **every new entry** (every layout shift cluster, every new worst interaction) rather than only at page hide. Combined with `navigation_id` — a UUID reset at each route change — we can reconstruct per-route scores from the delta stream.

**CLS per route:** `SUM(web_vital.delta WHERE web_vital.name='CLS' AND navigation_id=X)` = CLS for route X. Every layout shift cluster fires a callback with its delta. Each callback is stamped with the `navigation_id` of the active route.

**INP per route:** `MAX(web_vital.value WHERE web_vital.name='INP' AND navigation_id=X)` = worst interaction on route X. Each callback fires when a new worst interaction is detected; by the time `notifySoftNavigation()` flushes, the most recent INP value for the departing route is already exported.

**LCP:** Only meaningful on initial load. Still tracked as-is.

**TTFB / FCP:** Not meaningful on SPA route changes. Still tracked once on initial load.

No experimental APIs. No `web-vitals` version upgrade required. Works in Chrome, Safari, Firefox.

---

## 3. New attributes

Two new attributes added to `src/semconv.ts`:

| Attribute | Type | Values | Source |
|-----------|------|--------|--------|
| `navigation_id` | string (UUID v4) | one UUID per navigation | Generated in `NavigationInstrumentation`, stamped by `GlobalAttributesProcessor` on every span + log |
| `web_vital.context` | string | `pageload` \| `navigation` | Derived from `Metric.navigationType`: `navigate / reload / back-forward / ...` → `pageload`; `soft-navigation` (future, Chrome only) → `navigation` |
| `web_vital.delta` | number | `Metric.delta` | Each incremental value since last callback |

`web_vital.delta` enables per-route CLS aggregation in ClickHouse without any additional SDK logic.

---

## 4. `navigation_id` threading

`GlobalAttributesProcessor` already implements both `SpanProcessor` (`onStart`) and `LogRecordProcessor` (`onEmit`). Adding `navigation_id` to `getCommonAttrs()` propagates it to every span and log record with zero new pipeline code.

`navigation.ts` already calls `sdk.globalAttrsProcessor.setScreenName()` — `setNavigationId()` follows the exact same pattern, called before each `startSpan("screen_load")`.

```
Cold load:
  uuid = crypto.randomUUID()
  globalAttrsProcessor.setNavigationId(uuid)   ← before startSpan
  startSpan("screen_load") → uuid stamped via onStart
  web vitals that fire → uuid stamped via onEmit

SPA navigation (applyRouteChange):
  uuid = crypto.randomUUID()
  globalAttrsProcessor.setNavigationId(uuid)   ← before startSpan
  endActiveSessionSpan(...)
  startSpan("screen_load", start.type:"spa") → new uuid
  startSpan("screen_session") → new uuid
  setScreenName(...)
  [useRouterTracking hook fires → notifySoftNavigation() → forceFlush]
  → buffered CLS/INP logs from old route flush with OLD uuid (set at emit time, not flush time)

BFCache restore:
  uuid = crypto.randomUUID()
  globalAttrsProcessor.setNavigationId(uuid)
  startSpan("screen_load", start.type:"bfcache") → uuid
```

Flush timing is safe: `onEmit` stamps `navigation_id` at `logger.emit()` time. Buffered records from the departing route keep the old UUID when `forceFlush()` runs — no stale attribution.

---

## 5. web-vitals callback changes

```ts
// BEFORE (current)
onCLS(emit);
onINP(emit);

// AFTER
onCLS(emit, { reportAllChanges: true });   // delta per layout-shift cluster
onINP(emit, { reportAllChanges: true });   // fires on each new worst interaction

// unchanged — initial load only, reportAllChanges not meaningful
onLCP(emit);
onFCP(emit);
onFID(emit);
onTTFB(emit);
```

No import change. No version bump. `reportAllChanges` is in web-vitals v4.

---

## 6. ClickHouse queries enabled by this plan

```sql
-- CLS per route (delta sum)
SELECT
  LogAttributes['screen.name'] AS route,
  LogAttributes['navigation_id'] AS nav_id,
  SUM(toFloat64(LogAttributes['web_vital.delta'])) AS cls_for_route
FROM otel.otel_logs
WHERE ProjectId = 'your-project'
  AND LogAttributes['web_vital.name'] = 'CLS'
  AND Timestamp >= now() - INTERVAL 1 HOUR
GROUP BY route, nav_id
ORDER BY cls_for_route DESC
LIMIT 50;

-- INP per route (max per navigation)
SELECT
  LogAttributes['screen.name'] AS route,
  LogAttributes['navigation_id'] AS nav_id,
  MAX(toFloat64(LogAttributes['web_vital.value'])) AS inp_ms
FROM otel.otel_logs
WHERE ProjectId = 'your-project'
  AND LogAttributes['web_vital.name'] = 'INP'
  AND Timestamp >= now() - INTERVAL 1 HOUR
GROUP BY route, nav_id
ORDER BY inp_ms DESC
LIMIT 50;

-- Join vitals to screen_load span
SELECT
  l.LogAttributes['web_vital.name'] AS metric,
  l.LogAttributes['web_vital.value'] AS value,
  l.LogAttributes['screen.name'] AS route,
  t.SpanAttributes['start.type'] AS load_type
FROM otel.otel_logs l
JOIN otel.otel_traces t
  ON l.LogAttributes['navigation_id'] = t.SpanAttributes['navigation_id']
WHERE l.ProjectId = 'your-project'
  AND t.PulseType = 'screen_load'
  AND l.LogAttributes['pulse.type'] = 'web_vital'
  AND l.Timestamp >= now() - INTERVAL 1 HOUR
LIMIT 100;
```

No materialized columns needed. Map access only. No backend schema migration.

---

## 7. Touchpoints (7 files)

| File | Change |
|------|--------|
| `src/semconv.ts` | Add `AttributeKey.NAVIGATION_ID`, `WEB_VITAL_CONTEXT`, `WEB_VITAL_DELTA` |
| `src/processors/global-attrs-processor.ts` | `private _navigationId = ""`; `setNavigationId(id: string): void`; add `navigation_id` to `getCommonAttrs()` |
| `src/instrumentations/navigation.ts` | `setNavigationId(crypto.randomUUID())` before each `startSpan("screen_load")` — 3 sites: cold load, SPA `applyRouteChange`, BFCache restore |
| `src/instrumentations/web-vitals.ts` | Add `reportAllChanges: true` to `onCLS` + `onINP`; emit `web_vital.delta` + `web_vital.context` attrs |
| `src/__tests__/web-vitals-instrumentation.test.ts` | Assert `web_vital.delta`, `web_vital.context`; verify `navigation_id` present on emitted records |
| `src/__tests__/navigation-instrumentation.test.ts` | Verify `setNavigationId` called once per navigation type (cold, SPA, BFCache) |
| `examples/ecommerce-demo/e2e/web-vitals.spec.ts` | Add `navigation_id` truthy + `web_vital.context` ∈ `{pageload,navigation}` + `web_vital.delta` finite assertions on positive paths |

No backend changes. No new `PulseFeature`. No new E2E spec file. No `web-vitals` version bump.

---

## 8. Known limitations

- **LCP per route:** Not possible — browser fires LCP once per page load. No SPA LCP.
- **TTFB / FCP per route:** Network metrics, initial load only.
- **`web_vital.context = "navigation"`:** Only fires when Chrome Soft Navigation API is GA and web-vitals releases `reportSoftNavs`. For now all vitals get `context = "pageload"`. Add as a forward-compatible attribute; update the mapping when the API ships.
- **INP with `reportAllChanges`:** Fires on every new worst interaction, not just page hide — may produce more log records. Acceptable given the value; note in SPEC.
