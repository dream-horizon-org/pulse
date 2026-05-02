# Plan B — Web Vitals via OTLP Logs (Industry Standard)

**Approach:** Emit web vitals as OTLP log records — same pipeline as clicks, sessions, errors.  
**Status:** Recommended over [PLAN-A-metrics-histogram.md](./PLAN-A-metrics-histogram.md).  
**Reference:** PostHog web vitals — https://github.com/PostHog/posthog-js/blob/main/packages/browser/src/extensions/web-vitals/index.ts

---

## ADR deviation

[ADR-web-vitals.md](./ADR-web-vitals.md) D1/D2 specified OTLP **metrics** (histograms). D6 explicitly said "no duplicate log emission in MVP". This plan deviates from those decisions. The ADR must be updated when this plan is accepted.

**Reason for deviation:**

| ADR decision | Why overridden |
|---|---|
| D1 — metrics as primary signal | Metrics histogram requires `forceFlush()` inside every callback to avoid flush-ordering race. Log pipeline has no ordering dependency. |
| D2 — histogram per vital | `reportAllChanges: false` means one observation per page load. Histogram adds no aggregation value — logs store exact scalar, percentiles computed at query time with identical accuracy. |
| D6 — no log emission | Plan B *replaces* metrics, not duplicates. Logs are the primary (and only) signal path. |

---

## Why logs beat histograms

Both approaches use `reportAllChanges: false` — one observation per page load, final value only.

| | Plan A (Histogram) | Plan B (Logs) |
|--|-------------------|---------------|
| Flush ordering problem | Yes — needs `forceFlush` inside every callback | No — log queues into `BatchLogRecordProcessor`, exported on next 5s batch or pagehide |
| Percentile accuracy | Exact (`Sum/Count` when `Count=1`) | Exact (`toFloat64(Attributes['web_vital.value'])`) |
| Industry standard | No | Yes — PostHog, GA4, Datadog all store vitals as events |
| Pipeline | Metrics (`MeterProvider` + histogram) | Logs (already proven for sessions, clicks, errors) |
| `SdkContext` change needed | `getMeter` not on `SdkContext` — use global | `logger` already on `SdkContext` and globally set |

**PostHog uses the same semantics** — `reportAllChanges: false`, fires on `pagehide`, one value per vital. Plan B emits one log record per vital (vs PostHog's single batched event) for cleaner ClickHouse filtering.

---

## Event lifecycle (end to end)

### Step 1 — LCP finalises

```
User navigates to /products
t=1.8s  hero image loads  → browser: LCP candidate (1800ms, buffered internally)
t=4.2s  user clicks       → LCP locked. web-vitals fires onLCP callback.
metric = { name: "LCP", value: 1800, rating: "good", navigationType: "navigate" }
```

`reportAllChanges: false` (default) — `onLCP` fires **once**. Final value only.

If the user never interacts (passive read), callback fires on `pagehide` instead.

### Step 2 — Instrumentation emits a log record

```ts
onLCP((metric) => {
  logger.emit({
    body: PulseWebSemconv.LogBody.WEB_VITAL,
    attributes: {
      [PulseWebSemconv.AttributeKey.PULSE_TYPE]: PulseWebSemconv.PulseType.WEB_VITAL,
      "web_vital.name":           "LCP",
      "web_vital.value":          metric.value,          // 1800 (number)
      "web_vital.rating":         metric.rating,         // "good"
      "web_vital.navigation_type": metric.navigationType, // "navigate" | "reload" | "back-forward" | undefined
    },
  });
  // No forceFlush — BatchLogRecordProcessor exports on 5s schedule AND on pagehide
});
```

One log record written to the batch queue. Exported in the next 5s periodic batch (if page is still alive) or in the existing SDK `pagehide` flush.

### Step 3 — Existing log pipeline handles the rest

`BatchLogRecordProcessor` → `PulseBrowserLogExporter` → OTLP HTTP `/v1/logs`. No new pipeline.

`PulseGlobalAttributesProcessor` stamps `session.id`, `screen.name`, `project.id`, `platform` on every log record — the instrumentation does not set these.

### Step 4 — OTLP Collector → ClickHouse `otel_logs`

```
Timestamp  = 2026-05-01T10:23:41.800Z
Body       = "web_vital"
Attributes = {
  "pulse.type":                "web_vital",
  "web_vital.name":            "LCP",
  "web_vital.value":           "1800",       ← stored as string in Map(String, String)
  "web_vital.rating":          "good",
  "web_vital.navigation_type": "navigate",
  "session.id":                "sess-xyz",   ← stamped by global processor
  "screen.name":               "/products"   ← stamped by global processor
}
ProjectId  = "your-project"    ← materialized column
SessionId  = "sess-xyz"        ← materialized column
```

`Attributes` is `Map(LowCardinality(String), String)` — all values stored as strings. Cast at query time: `toFloat64(Attributes['web_vital.value'])`.

**Ingestion note:** The OTLP Collector accepts logs with any string values in `Attributes`. No collector config change needed — the existing logs route (`/v1/logs`) handles web vital records identically to session/click records.

### Step 5 — Query: p75 LCP

One record per page load, no deduplication:

```sql
SELECT
  quantile(0.75)(toFloat64(Attributes['web_vital.value'])) AS p75_lcp_ms,
  quantile(0.95)(toFloat64(Attributes['web_vital.value'])) AS p95_lcp_ms,
  count()                                                  AS page_loads
FROM otel.otel_logs
WHERE
  ProjectId = 'your-project'
  AND Timestamp >= now() - INTERVAL 7 DAY
  AND Attributes['pulse.type'] = 'web_vital'
  AND Attributes['web_vital.name'] = 'LCP'
```

---

## When each vital fires (`reportAllChanges: false`)

### Core Web Vitals — always on

| Vital | When callback fires | Value | `navigationType` |
|-------|--------------------|----|---|
| LCP | First user interaction or `pagehide` | Final largest contentful paint (ms) | Yes |
| INP | `pagehide` | Worst interaction latency seen during page lifetime (ms) | Yes |
| CLS | `pagehide` | Largest cumulative layout shift window score (unitless) | Yes |

**CLS "session window" note:** `web-vitals` reports the largest *browser measurement window* (layout shifts within 1s of each other, max 5s total). Unrelated to the Pulse user session concept.

### Optional vitals — off by default

| Vital | When | Value | Default | Why opt-in |
|-------|------|-------|---------|-----------|
| **FID** | First user interaction | First input delay (ms) — time from first tap/key to first browser response | `fid: false` | Deprecated as Core Web Vital March 2024; replaced by INP. Enable only for legacy dashboards that still report FID. |
| **FCP** | First contentful paint event | Time to first text or image paint (ms) | `fcp: false` | Useful perf signal but not part of the Core Web Vitals trio; Google Search ranking does not use FCP. Enable when product wants paint timing breakdowns. |

Both follow the same emit pattern as core vitals — one callback per page load, same attributes, same pipeline.

**Config to opt in** (extend `instrumentations.webVitals` in `config.ts`):

```ts
webVitals?: {
  enabled?: boolean;   // master switch — default true
  fid?: boolean;       // default false — deprecated, legacy only
  fcp?: boolean;       // default false — non-core, opt-in
}
```

`config.ts` change: extend the existing `webVitals` shape with `fid?: boolean` and `fcp?: boolean`.

---

## bfcache and visibility handling

### pagehide (existing SDK behaviour)

`pagehide` fires with `event.persisted = true` when the browser caches the page for back/forward (bfcache). The existing Pulse SDK `pagehide` listener early-outs on `event.persisted` — does NOT flush on cache entry. This is correct — no records should go out mid-cache.

**Problem:** Any log records that entered the `BatchLogRecordProcessor` queue between the last 5s periodic export and this `pagehide` are now stuck in memory inside the cached page. If the user never navigates back, they are lost. If the user does navigate back (bfcache restore), those records are still in the queue and should be flushed.

### pageshow (bfcache restore) — new handler required

When the browser restores the page from bfcache, `pageshow` fires with `event.persisted = true`. Two things must happen:

1. **Flush pending records** from the previous navigation that were in the queue at cache time.
2. **web-vitals v3+ fires new callbacks** automatically for the restored navigation (new `metric.id`, correct `navigationType = "back-forward"`). These go into the queue normally.

```ts
// In WebVitalsInstrumentation.install()
window.addEventListener("pageshow", (e: PageTransitionEvent) => {
  if (e.persisted) {
    // Flush records queued from the previous navigation (pre-cache)
    void sdk.loggerProvider.forceFlush();
  }
});
```

`screen.name` at callback time will be the restored URL's pathname — correct by construction since `window.location` has already updated before `pageshow` fires.

### visibilitychange hidden — new handler required

On mobile (iOS Safari especially), `pagehide` does not fire reliably when the browser kills a background tab. `visibilitychange` to `"hidden"` is the more reliable signal across all browsers.

web-vitals already uses `visibilitychange` internally to trigger its final callbacks (INP, CLS, LCP if not yet locked). So when the tab hides, callbacks fire and records enter the batch queue — but the queue is not flushed unless we add this handler:

```ts
// In WebVitalsInstrumentation.install()
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "hidden") {
    // Mobile safety net — pagehide may not fire; flush now
    void sdk.loggerProvider.forceFlush();
  }
});
```

This is additive to the existing `pagehide` flush — desktop uses `pagehide`, mobile gets `visibilitychange`. On desktop both may fire; a second `forceFlush()` on an empty queue is a no-op.

**`sdk.loggerProvider` access:** `SdkContext` must expose `loggerProvider` (or a `flush()` method) for these handlers. Confirm at implementation — if not available, wire a flush callback through the constructor.

### Duplicate subscriptions on bfcache restore

`install()` is called once at `start()`. The registry does NOT call `installAll()` again on bfcache restore. `pageshow` and `visibilitychange` listeners registered in `install()` persist in memory across the bfcache cycle — they do not need to be re-registered. Verify in E2E that a back-forward navigation does not trigger a second `install()`.

---

## SPA navigation and screen.name accuracy

Web vitals (LCP, INP, CLS) measure a single **hard navigation** — a full page load. They do **not** reset on SPA route changes. This is consistent with how web-vitals behaves everywhere (PostHog, GA4, Datadog all have the same limitation).

**What this means practically:**

```
User hard-navigates to /home
  → LCP fires on first interaction (URL is /home) → screen.name = "/home" ✓
  → User clicks → React Router navigates to /products (SPA)
  → CLS, INP fire on pagehide (URL is now /products) → screen.name = "/products"
```

INP and CLS records have `screen.name = "/products"` but their measurement covers the entire page lifetime starting from `/home`. This is a known measurement ambiguity, not a Pulse-specific bug.

**`url.path` and `page.url` are always stamped at callback time** (from `window.location`) — they reflect the URL when the vital fires, not when the page loaded. For the UI, filtering by `screen.name` on web vitals means "the screen the user was on when the metric finalised."

For users who want per-route vitals in a SPA, the correct approach is [web-vitals soft navigations](https://github.com/GoogleChrome/web-vitals#measure-metrics-for-soft-navigations) — behind an experimental flag in Chrome. This is out of scope for MVP.

---

## Configuration and feature gates

Installation predicate: `consent OK ∧ gate(web_vitals) ∧ config.webVitals.enabled !== false`

| Layer | Mechanism | Behavior |
|-------|-----------|----------|
| Consent | `PulseDataCollectionConsent` | Not `ALLOWED` → `start()` returns before any provider init — no vitals |
| Remote | `features[]` with `featureName: "web_vitals"` for `pulse_web_js` | `FeatureGate` — explicit `enabled: false` disables; missing entry defaults enabled |
| Static | `instrumentations.webVitals.enabled` | `false` → skip install. Default treat as `true` when undefined |
| Before-send | `beforeSendData` / `beforeSendLog` | Can drop or redact log records at export time |
| Sampling | `ExportSamplingGate` | When session is not sampled, log batches are dropped — web vitals drop with all other signals (inherited, no special handling) |

---

## SDK implementation

```ts
// src/instrumentations/web-vitals.ts
import { logs } from "@opentelemetry/api-logs";
import { onLCP, onINP, onCLS, onFID, onFCP } from "web-vitals";
import type { PulseInstrumentation, SdkContext } from "../instrumentation-registry";
import { PulseWebSemconv } from "../semconv";

export class WebVitalsInstrumentation implements PulseInstrumentation {
  readonly name = "web-vitals";

  install(sdk: SdkContext): void {
    if (typeof window === "undefined") return;

    const logger = logs.getLogger("pulse-web-vitals");
    const wvConfig = sdk.config?.instrumentations?.webVitals;

    const emit = (name: string, value: number, rating: string, navigationType?: string) => {
      const attrs: Record<string, unknown> = {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]: PulseWebSemconv.PulseType.WEB_VITAL,
        "web_vital.name":   name,
        "web_vital.value":  value,
        "web_vital.rating": rating,
      };
      if (navigationType) {
        attrs["web_vital.navigation_type"] = navigationType;
      }
      logger.emit({
        body: PulseWebSemconv.LogBody.WEB_VITAL,
        attributes: attrs,
      });
      // No forceFlush — BatchLogRecordProcessor + existing pagehide flush handles export
    };

    // Core Web Vitals — always on when feature is enabled
    // reportAllChanges: false (default) — one callback per vital, final value only
    onLCP((m) => emit("LCP", m.value, m.rating, m.navigationType));
    onINP((m) => emit("INP", m.value, m.rating, m.navigationType));
    onCLS((m) => emit("CLS", m.value, m.rating, m.navigationType));

    // Optional vitals — off by default, enabled via config
    if (wvConfig?.fid) {
      // FID deprecated as CWV March 2024; still fires on first interaction
      onFID((m) => emit("FID", m.value, m.rating, m.navigationType));
    }
    if (wvConfig?.fcp) {
      onFCP((m) => emit("FCP", m.value, m.rating, m.navigationType));
    }

    // Flush on tab hide — mobile safety net (pagehide unreliable on iOS Safari).
    // web-vitals fires its own callbacks on visibilitychange, which enqueue log records.
    // Without this flush those records are lost when iOS kills the background tab.
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") {
        void sdk.loggerProvider.forceFlush();
      }
    });

    // Flush on bfcache restore — pending records from the previous navigation
    // survive in the BatchLogRecordProcessor queue across the bfcache cycle.
    // web-vitals fires new callbacks for the restored page automatically.
    window.addEventListener("pageshow", (e: PageTransitionEvent) => {
      if (e.persisted) {
        void sdk.loggerProvider.forceFlush();
      }
    });
  }

  uninstall(): void {
    // web-vitals v4 has no cancel() API — listeners cannot be detached.
    // visibilitychange and pageshow listeners also cannot be removed without
    // storing refs; after SDK shutdown, loggerProvider.forceFlush() becomes a no-op.
    // No data leaks after shutdown.
  }
}
```

**Logger access:** Uses `logs.getLogger()` global — same pattern as session and error instrumentations. `logs.setGlobalLoggerProvider` is called in `bindGlobalProviders()` before `installAll()`, so the global is set when `install()` runs. No `SdkContext` change needed.

**`sdk.config` access:** `SdkContext` must expose `config` (or `instrumentations` subset) for the optional vital flags to be readable in `install()`. If `config` is not currently on `SdkContext`, either add it or pass the webVitals sub-config directly when constructing `WebVitalsInstrumentation`. Confirm current `SdkContext` shape at implementation time.

### Semconv additions required

```ts
// semconv.ts additions
PulseType: {
  // ... existing
  WEB_VITAL: "web_vital",
},
LogBody: {
  // ... existing
  WEB_VITAL: "web_vital",
},
```

`web_vital.name`, `web_vital.value`, `web_vital.rating`, `web_vital.navigation_type` as inline strings for MVP. Promote to `AttributeKey` constants once UI queries them by symbol.

---

## Registry wiring

```ts
// instrumentation-registry.ts — inside installAll()
if (this.shouldInstall(InstrumentationKeys.WEB_VITALS)) {
  const wvInstr = new WebVitalsInstrumentation();
  wvInstr.install(this.sdk);
  this.installed.push(wvInstr);
}
```

Same fire-and-forget pattern as session and clicks. No `registerAndInstall` with key — no SDK call-back into this instrumentation. `shouldInstall` checks both remote gate and static config in one call.

---

## Backend changes

```java
// Features.java
web_vitals

// DefaultSdkConfigTemplate.java
features.add(createFeature(Features.web_vitals, 1.0, webJsSdk));
```

Update `DefaultSdkConfigTemplateTest` — expected feature count increases by 1.

---

## Unit tests

| Suite | Cases |
|-------|-------|
| **Gate off** | Remote config with `web_vitals` disabled → `shouldInstall` returns false → `WebVitalsInstrumentation.install()` never called → no `onLCP`/`onINP`/`onCLS` subscriptions |
| **Config off** | `instrumentations: { webVitals: { enabled: false } }` → not installed |
| **Consent denied** | `dataCollectionState: DENIED` → `start()` returns before providers init → no logger, no vitals |
| **Attribute shape** | Mock `logs.getLogger` → spy on `logger.emit` → assert emitted record has `pulse.type = "web_vital"`, `web_vital.name = "LCP"`, `web_vital.value = 1800`, `web_vital.rating = "good"` |
| **navigationType present** | `metric.navigationType = "reload"` → emitted attrs include `web_vital.navigation_type = "reload"` |
| **navigationType absent** | `metric.navigationType = undefined` → `web_vital.navigation_type` key absent from attrs (not emitted as `undefined`) |
| **Single owner** | Call `installAll()` twice (simulate re-init without shutdown) → `onLCP` spy called only once (one subscription, not two) |
| **uninstall no-op** | After `shutdown()` → `logger.emit` spy receives no further calls when `onLCP` fires |
| **before-send drop** | Configure `beforeSendLog` to drop all records where `Attributes['pulse.type'] = 'web_vital'` → OTLP export receives zero web vital records |
| **sampling drop** | `ExportSamplingGate` unsampled → batch processor discards all log records including vitals (regression: confirm vitals do not bypass sampling) |
| **global attrs** | `PulseGlobalAttributesProcessor` stamps `session.id` and `screen.name` on vitals log records (same as session/click records) |
| **FID off by default** | `wvConfig.fid` absent/false → `onFID` never registered → no FID log records emitted |
| **FID on** | `wvConfig.fid = true` → `onFID` fires → log record with `web_vital.name = "FID"`, correct value and rating |
| **FCP off by default** | `wvConfig.fcp` absent/false → `onFCP` never registered → no FCP log records emitted |
| **FCP on** | `wvConfig.fcp = true` → `onFCP` fires → log record with `web_vital.name = "FCP"`, correct value and rating |
| **FID + FCP both on** | Both flags true → exactly 5 records per page load (LCP + INP + CLS + FID + FCP); no cross-contamination of `web_vital.name` |
| **Optional vital gate off** | Remote gate disabled + `fid: true` → instrumentation not installed → `onFID` not registered |
| **visibilitychange flush** | Fire `visibilitychange` with `hidden` → `loggerProvider.forceFlush` called once |
| **visibilitychange visible** | Fire `visibilitychange` with `visible` → `forceFlush` NOT called |
| **pageshow bfcache restore** | Fire `pageshow` with `persisted = true` → `loggerProvider.forceFlush` called |
| **pageshow normal load** | Fire `pageshow` with `persisted = false` → `forceFlush` NOT called |
| **double flush safe** | `forceFlush` called on empty queue (already exported) → no error, no duplicate export |

---

## Integration tests

| Case | How |
|------|-----|
| OTLP payload shape | Export to in-memory log exporter; assert `ResourceLogs[0].ScopeLogs[0].LogRecords[0].Attributes` contains all required keys |
| Core 3 only by default | Simulate LCP + INP + CLS callbacks, no config flags → exactly 3 log records |
| FID + FCP enabled | Set `fid: true, fcp: true`; simulate all 5 callbacks → exactly 5 log records, correct `web_vital.name` per record |
| visibilitychange flushes queue | Enqueue a log record; fire `visibilitychange hidden`; assert record exported without waiting for 5s batch |
| bfcache restore flushes pending | Simulate `pagehide persisted=true` (page cached); fire `pageshow persisted=true`; assert queued records exported |
| bfcache fresh vitals | After restore, simulate new LCP callback → new log record with `navigationType = "back-forward"` and updated `screen.name` |
| No flush on bfcache entry | `pagehide persisted=true` → `forceFlush` NOT called (existing behaviour preserved) |

---

## Edge and permutation matrix

| Dimension | Values | Expected |
|-----------|--------|----------|
| Feature gate | on / off | off → no install |
| `instrumentations.webVitals.enabled` | true / false / undefined | false → no install; undefined → install |
| `fid` flag | true / false / undefined | false/undefined → no FID; true → FID registered |
| `fcp` flag | true / false / undefined | false/undefined → no FCP; true → FCP registered |
| Consent | ALLOWED / DENIED | DENIED → no install |
| `beforeSendLog` | pass-through / drop vitals | drop → zero records exported |
| Sampling | sampled / unsampled session | unsampled → vitals drop with all signals |
| Shutdown timing | shutdown before `onLCP` fires | no panic; emit is no-op after logger shutdown |
| SPA route change mid-session | React Router push while page alive | screen.name reflects URL at callback time, not page load time |
| `navigationType` | `"navigate"` / `"reload"` / `"back-forward"` / `undefined` | correctly set or absent |
| bfcache restore | `pageshow.persisted = true` | pending records flushed; new vitals emitted with `navigationType = "back-forward"` |
| visibilitychange hidden | tab backgrounded | `forceFlush` called; queued records exported |
| visibilitychange visible | tab foregrounded | no flush |
| Invalid remote config | malformed `features` array | SDK does not crash; vitals follow same recovery as other features |
| SSR / no `window` | `typeof window === "undefined"` | `install()` returns immediately, no subscriptions |

---

## E2E (Playwright)

**Minimum: Chromium** (LCP, INP, CLS have full support). Document WebKit/Firefox gaps in [`agent-runtime/test-run-log.md`](../agent-runtime/test-run-log.md).

### Demo app setup

Ensure `examples/ecommerce-demo` starts SDK with `webVitals` not explicitly disabled. Optional: add a button that triggers `click` + `layout shift` to force INP and CLS callbacks in test.

### Spec outline

```ts
// e2e/web-vitals.spec.ts
test("emits LCP log record to /v1/logs", async ({ page }) => {
  const logRequests: unknown[] = [];
  page.on("request", (req) => {
    if (req.url().includes("/v1/logs")) {
      logRequests.push(JSON.parse(req.postData() ?? "{}"));
    }
  });

  await page.goto("/");
  // Trigger LCP lock by clicking — forces onLCP callback
  await page.click("body");
  await page.waitForTimeout(6000); // wait for 5s batch export

  const vitals = logRequests
    .flatMap((body: any) => body.resourceLogs ?? [])
    .flatMap((rl: any) => rl.scopeLogs ?? [])
    .flatMap((sl: any) => sl.logRecords ?? [])
    .filter((r: any) =>
      r.attributes?.some((a: any) => a.key === "pulse.type" && a.value?.stringValue === "web_vital")
    );

  expect(vitals.length).toBeGreaterThan(0);
  const lcp = vitals.find((r: any) =>
    r.attributes?.some((a: any) => a.key === "web_vital.name" && a.value?.stringValue === "LCP")
  );
  expect(lcp).toBeDefined();
  expect(lcp.attributes.find((a: any) => a.key === "web_vital.value")).toBeDefined();
});

test("does not emit vitals when gate disabled", async ({ page }) => {
  // Start SDK with web_vitals feature disabled via mock remote config
  // Assert no log records with pulse.type = web_vital
});
```

Required command: `yarn workspace ecommerce-demo e2e:web-sdk-gates` must pass. Add `e2e:web-vitals` script if separate file.

---

## Touchpoints checklist

| Area | MVP action | Done |
|------|-----------|------|
| `semconv.ts` | Add `PulseType.WEB_VITAL`, `LogBody.WEB_VITAL` | [ ] |
| `src/instrumentations/web-vitals.ts` | New file — `WebVitalsInstrumentation` | [ ] |
| `instrumentation-registry.ts` | Wire in `installAll()` | [ ] |
| `src/types/config.ts` | Extend `webVitals` shape: add `fid?: boolean` and `fcp?: boolean` | [ ] |
| `src/types/instrumentation-registry.ts` | Confirm `SdkContext` exposes `loggerProvider` or a flush callback | [ ] |
| `ADR-web-vitals.md` | Update D1/D2/D6 to reflect logs decision; update D5 to include visibilitychange + pageshow | [ ] |
| `04-contract-parity.md` | Add `web_vital.value`, `web_vital.navigation_type` to web-only attrs table | [ ] |
| `Features.java` | Add `web_vitals` enum value | [ ] |
| `DefaultSdkConfigTemplate.java` | Add default row for `pulse_web_js` | [ ] |
| `DefaultSdkConfigTemplateTest` | Update expected feature count | [ ] |
| Unit tests | All cases in unit tests section above | [ ] |
| E2E | `e2e:web-vitals` Playwright spec | [ ] |
| `agent-runtime/test-run-log.md` | Append pass/fail after E2E run | [ ] |

---

## Additional useful queries

### Distribution by rating

```sql
SELECT
  Attributes['web_vital.rating']                                AS rating,
  count()                                                       AS page_loads,
  round(count() * 100.0 / sum(count()) OVER (), 1)             AS pct
FROM otel.otel_logs
WHERE
  ProjectId = 'your-project'
  AND Timestamp >= now() - INTERVAL 7 DAY
  AND Attributes['pulse.type'] = 'web_vital'
  AND Attributes['web_vital.name'] = 'INP'
GROUP BY rating
ORDER BY pct DESC
```

### Vitals by screen / route

```sql
SELECT
  Attributes['screen.name']                                          AS screen,
  quantile(0.75)(toFloat64(Attributes['web_vital.value']))           AS p75_lcp_ms,
  count()                                                            AS page_loads
FROM otel.otel_logs
WHERE
  ProjectId = 'your-project'
  AND Timestamp >= now() - INTERVAL 7 DAY
  AND Attributes['pulse.type'] = 'web_vital'
  AND Attributes['web_vital.name'] = 'LCP'
GROUP BY screen
ORDER BY p75_lcp_ms DESC
LIMIT 20
```

### All three vitals in one query

```sql
SELECT
  Attributes['web_vital.name']                                       AS vital,
  quantile(0.75)(toFloat64(Attributes['web_vital.value']))           AS p75,
  quantile(0.95)(toFloat64(Attributes['web_vital.value']))           AS p95,
  count()                                                            AS page_loads
FROM otel.otel_logs
WHERE
  ProjectId = 'your-project'
  AND Timestamp >= now() - INTERVAL 7 DAY
  AND Attributes['pulse.type'] = 'web_vital'
  AND Attributes['web_vital.name'] IN ('LCP', 'INP', 'CLS')
GROUP BY vital
ORDER BY vital
```

### FID / FCP (when enabled)

Identical query pattern — just change the `web_vital.name` filter:

```sql
-- FID p75 (only meaningful if fid: true was set)
SELECT quantile(0.75)(toFloat64(Attributes['web_vital.value'])) AS p75_fid_ms
FROM otel.otel_logs
WHERE ProjectId = 'your-project'
  AND Timestamp >= now() - INTERVAL 7 DAY
  AND Attributes['pulse.type'] = 'web_vital'
  AND Attributes['web_vital.name'] = 'FID'

-- FCP p75
SELECT quantile(0.75)(toFloat64(Attributes['web_vital.value'])) AS p75_fcp_ms
FROM otel.otel_logs
WHERE ProjectId = 'your-project'
  AND Timestamp >= now() - INTERVAL 7 DAY
  AND Attributes['pulse.type'] = 'web_vital'
  AND Attributes['web_vital.name'] = 'FCP'
```

### By navigation type

```sql
SELECT
  Attributes['web_vital.navigation_type']                            AS nav_type,
  quantile(0.75)(toFloat64(Attributes['web_vital.value']))           AS p75_lcp_ms,
  count()                                                            AS page_loads
FROM otel.otel_logs
WHERE
  ProjectId = 'your-project'
  AND Timestamp >= now() - INTERVAL 7 DAY
  AND Attributes['pulse.type'] = 'web_vital'
  AND Attributes['web_vital.name'] = 'LCP'
GROUP BY nav_type
ORDER BY p75_lcp_ms DESC
```

---

## No schema change required

`otel_logs` already has:
- `Attributes Map(LowCardinality(String), String)` — stores all web vital attributes
- `SessionId` materialized column
- `ProjectId` materialized column
- `Timestamp` for time-range filters

> Optional improvement: add a `PulseType` materialized column on `otel_logs` (like `otel_metrics_histogram` has `Platform`, `AppVersion`) to make the `pulse.type = 'web_vital'` filter use an index instead of map lookup. Not required for MVP.

---

## Export volume

3 log records per page load (LCP + INP + CLS). At 100k daily sessions: +300k log records/day. `otel_logs` already handles sessions, clicks, errors at comparable volume. Negligible.

---

## Known limitations

| Limitation | Notes |
|------------|-------|
| Crash loss | `pagehide` never fires on crash/force-kill. Final vital value not exported. Same as Plan A and PostHog — accepted for MVP (< 5% of sessions) |
| FID deprecated | Available via `instrumentations.webVitals.fid: true`. Off by default — deprecated as Core Web Vital March 2024 |
| FCP non-core | Available via `instrumentations.webVitals.fcp: true`. Off by default — not part of the Core Web Vitals trio |
| `web-vitals` no cancel API | `uninstall()` is a no-op on subscriptions. After SDK shutdown, `logger.emit()` is a no-op — no data leaks |
| bfcache double-subscription | `install()` called once at `start()`; verify SDK does not re-call `installAll()` on bfcache restore |
| SPA vitals scope | LCP/INP/CLS measure from hard navigation, not SPA route change. `screen.name` reflects URL at callback time. Per-route SPA vitals require web-vitals soft nav API (experimental, out of MVP scope) |
| `uninstall` listeners | `visibilitychange` and `pageshow` listeners cannot be removed without storing refs — after shutdown, `forceFlush()` on a shut-down provider is a no-op, so no leak |
