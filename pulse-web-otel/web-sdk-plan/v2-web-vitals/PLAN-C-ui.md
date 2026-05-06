# Plan C — Web Vitals UI (pulse-ui)

**Depends on:** [PLAN-B-logs-events.md](./PLAN-B-logs-events.md) — data must be landing in `otel_logs` before any UI work starts.  
**Status:** Proposed. UI is out of scope for SDK MVP but planned here for product handoff.

---

## What the UI needs to show

Web vitals are **per-page-load metrics**. The natural mental model for a user:

1. **Project overview** — "How is my web app performing overall? What are my p75 LCP, INP, CLS?"
2. **By screen/route** — "Which pages are slow? Sort by worst LCP."
3. **Drill into a screen** — "Why is `/checkout` slow? How does it trend? What % of loads are poor?"

This maps to two screens and one extension to an existing screen.

---

## Screen structure

### Screen 1 — Web Vitals Overview (`/projects/:projectId/web-vitals`)

New screen. Entry point from navbar.

**Layout:**

```
┌─────────────────────────────────────────────────────────────┐
│  Web Vitals                              [7d ▼] [nav: all ▼]│
├──────────────┬──────────────┬────────────────────────────────┤
│  LCP p75     │  INP p75     │  CLS p75                       │
│  1.8s  GOOD  │  142ms GOOD  │  0.08 GOOD                     │
│  ████░░░░    │  ████░░░░    │  ████░░░░  (rating bar)        │
├──────────────┴──────────────┴────────────────────────────────┤
│  By Screen                                     [vital: LCP ▼]│
│  ┌────────────────┬──────────┬──────────┬──────┬────────────┐│
│  │ Screen         │ LCP p75  │ INP p75  │ CLS  │ Page loads ││
│  ├────────────────┼──────────┼──────────┼──────┼────────────┤│
│  │ /checkout      │ 3.8s 🔴  │ 340ms 🟡 │ 0.12 │ 12,400     ││
│  │ /products/:id  │ 2.1s 🟡  │ 98ms  🟢 │ 0.04 │ 89,200     ││
│  │ /home          │ 1.2s 🟢  │ 65ms  🟢 │ 0.02 │ 204,000    ││
│  └────────────────┴──────────┴──────────┴──────┴────────────┘│
└─────────────────────────────────────────────────────────────┘
```

**Filters:**
- Time range: 24h / 7d / 30d (default 7d)
- Navigation type: All / navigate / reload / back-forward
- Platform: web only (SDK is web-only; no Android/iOS rows)

**Rating color legend:** Good = green, Needs improvement = amber, Poor = red — same thresholds as Google:
- LCP: good < 2500ms, poor ≥ 4000ms
- INP: good < 200ms, poor ≥ 500ms
- CLS: good < 0.1, poor ≥ 0.25

### Screen 2 — Web Vitals Screen Detail (`/projects/:projectId/web-vitals/:screenName`)

Drill-down from the by-screen table row.

**Layout:**

```
← Web Vitals          /checkout                    [7d ▼]

┌──────────┬──────────┬──────────┐
│ LCP p75  │ INP p75  │ CLS p75  │
│ 3.8s 🔴  │ 340ms 🟡 │ 0.12 🟡  │
└──────────┴──────────┴──────────┘

Rating distribution — LCP
  Good         ████░░░░░░░░  28%
  Needs improv ████████░░░░  47%
  Poor         ████░░░░░░░░  25%

Trend (p75 over time) — line chart [LCP ▼]
  [sparkline across selected time range, daily buckets]

By navigation type
  ┌───────────────┬──────────┬──────────┐
  │ nav type      │ LCP p75  │ loads    │
  ├───────────────┼──────────┼──────────┤
  │ navigate      │ 3.8s     │ 10,200   │
  │ reload        │ 2.1s     │ 1,800    │
  │ back-forward  │ 0.9s     │ 400      │
  └───────────────┴──────────┴──────────┘
```

### Extension — ScreenDetail tab (existing `/projects/:projectId/screens/:screenName`)

Add a "Web Vitals" tab to the existing `ScreenDetail` screen. The tab shows the same content as Screen 2 above but embedded in the existing screen detail shell. This is where a user navigating from screen analytics naturally discovers web vitals for that screen.

**Tab order in ScreenDetail:** existing tabs → `Web Vitals` (new, last tab, web-only, hidden when no web data)

---

## Routing changes

```ts
// Constants.ts additions
PROJECT_WEB_VITALS: {
  key: "PROJECT_WEB_VITALS",
  basePath: "/projects/:projectId/web-vitals",
  path: "/projects/:projectId/web-vitals",
},
PROJECT_WEB_VITALS_SCREEN_DETAIL: {
  key: "PROJECT_WEB_VITALS_SCREEN_DETAIL",
  basePath: "/projects/:projectId/web-vitals",
  path: "/projects/:projectId/web-vitals/:screenName",
},
```

```ts
// NAVBAR_ROUTES addition (after APP_VITALS)
WEB_VITALS: "/web-vitals",
```

```ts
// NAVBAR_ITEMS addition — after App Vitals entry
{
  tabName: "Web Vitals",
  icon: IconSpeedboat,   // or IconGauge from @tabler/icons-react
  routeTo: NAVBAR_ROUTES.WEB_VITALS,
  path: NAVBAR_ROUTES.WEB_VITALS,
  iconSize: 25,
}
```

---

## Backend API endpoints (pulse-server)

New controller: `WebVitalsResource` under `resources/web-vitals/`.

All endpoints require `ProjectId` from path + project membership auth (same as other project-scoped endpoints).

### `GET /v1/projects/:projectId/web-vitals/summary`

Query params: `from`, `to` (ISO timestamps), `navigationType?` (optional filter)

Response:
```json
{
  "lcp":  { "p75": 1800, "p95": 3200, "goodPct": 62, "needsImprovementPct": 28, "poorPct": 10, "pageLoads": 304000 },
  "inp":  { "p75": 142,  "p95": 310,  "goodPct": 71, "needsImprovementPct": 20, "poorPct": 9,  "pageLoads": 304000 },
  "cls":  { "p75": 0.08, "p95": 0.21, "goodPct": 78, "needsImprovementPct": 15, "poorPct": 7,  "pageLoads": 304000 }
}
```

ClickHouse query (per vital, run in parallel):
```sql
SELECT
  quantile(0.75)(toFloat64(Attributes['web_vital.value'])) AS p75,
  quantile(0.95)(toFloat64(Attributes['web_vital.value'])) AS p95,
  countIf(Attributes['web_vital.rating'] = 'good')               AS good_count,
  countIf(Attributes['web_vital.rating'] = 'needs-improvement')  AS needs_improvement_count,
  countIf(Attributes['web_vital.rating'] = 'poor')               AS poor_count,
  count()                                                         AS page_loads
FROM otel.otel_logs
WHERE
  ProjectId = :projectId
  AND Timestamp >= :from
  AND Timestamp <  :to
  AND Attributes['pulse.type'] = 'web_vital'
  AND Attributes['web_vital.name'] = :vital   -- 'LCP' | 'INP' | 'CLS'
  -- optional: AND Attributes['web_vital.navigation_type'] = :navigationType
```

### `GET /v1/projects/:projectId/web-vitals/by-screen`

Query params: `from`, `to`, `navigationType?`, `page` (default 0), `pageSize` (default 20), `sortBy` (lcp|inp|cls, default lcp), `sortOrder` (asc|desc, default desc)

Response:
```json
{
  "total": 42,
  "rows": [
    {
      "screenName": "/checkout",
      "lcp":  { "p75": 3800, "rating": "poor" },
      "inp":  { "p75": 340,  "rating": "needs-improvement" },
      "cls":  { "p75": 0.12, "rating": "needs-improvement" },
      "pageLoads": 12400
    }
  ]
}
```

ClickHouse query:
```sql
SELECT
  Attributes['screen.name']                                              AS screen_name,
  quantile(0.75)(toFloat64(
    IF(Attributes['web_vital.name'] = 'LCP', Attributes['web_vital.value'], NULL)
  ))                                                                     AS lcp_p75,
  quantile(0.75)(toFloat64(
    IF(Attributes['web_vital.name'] = 'INP', Attributes['web_vital.value'], NULL)
  ))                                                                     AS inp_p75,
  quantile(0.75)(toFloat64(
    IF(Attributes['web_vital.name'] = 'CLS', Attributes['web_vital.value'], NULL)
  ))                                                                     AS cls_p75,
  countIf(Attributes['web_vital.name'] = 'LCP')                        AS page_loads
FROM otel.otel_logs
WHERE
  ProjectId = :projectId
  AND Timestamp >= :from
  AND Timestamp <  :to
  AND Attributes['pulse.type'] = 'web_vital'
  AND Attributes['web_vital.name'] IN ('LCP', 'INP', 'CLS')
GROUP BY screen_name
ORDER BY lcp_p75 DESC   -- controlled by sortBy/sortOrder
LIMIT :pageSize OFFSET :offset
```

### `GET /v1/projects/:projectId/web-vitals/screen/:screenName/summary`

Same as `/summary` but filtered to one screen. Used by Screen 2 and ScreenDetail tab.

### `GET /v1/projects/:projectId/web-vitals/screen/:screenName/trend`

Query params: `from`, `to`, `vital` (LCP|INP|CLS), `bucket` (hour|day, default day)

Response: time-series array for the line chart.
```json
{
  "buckets": [
    { "timestamp": "2026-04-25T00:00:00Z", "p75": 1700, "pageLoads": 4200 },
    { "timestamp": "2026-04-26T00:00:00Z", "p75": 1900, "pageLoads": 3800 }
  ]
}
```

ClickHouse query:
```sql
SELECT
  toStartOfDay(Timestamp)                                              AS bucket,
  quantile(0.75)(toFloat64(Attributes['web_vital.value']))            AS p75,
  count()                                                              AS page_loads
FROM otel.otel_logs
WHERE
  ProjectId = :projectId
  AND Timestamp >= :from
  AND Timestamp <  :to
  AND Attributes['pulse.type'] = 'web_vital'
  AND Attributes['web_vital.name'] = :vital
  AND Attributes['screen.name'] = :screenName
GROUP BY bucket
ORDER BY bucket ASC
```

### `GET /v1/projects/:projectId/web-vitals/screen/:screenName/by-nav-type`

Returns breakdown by `web_vital.navigation_type`. Used in Screen 2 detail.

---

## API routes in `Constants.ts`

```ts
GET_WEB_VITALS_SUMMARY: {
  key: "GET_WEB_VITALS_SUMMARY",
  apiPath: `/v1/projects/:projectId/web-vitals/summary`,
  method: API_METHODS.GET,
},
GET_WEB_VITALS_BY_SCREEN: {
  key: "GET_WEB_VITALS_BY_SCREEN",
  apiPath: `/v1/projects/:projectId/web-vitals/by-screen`,
  method: API_METHODS.GET,
},
GET_WEB_VITALS_SCREEN_SUMMARY: {
  key: "GET_WEB_VITALS_SCREEN_SUMMARY",
  apiPath: `/v1/projects/:projectId/web-vitals/screen/:screenName/summary`,
  method: API_METHODS.GET,
},
GET_WEB_VITALS_SCREEN_TREND: {
  key: "GET_WEB_VITALS_SCREEN_TREND",
  apiPath: `/v1/projects/:projectId/web-vitals/screen/:screenName/trend`,
  method: API_METHODS.GET,
},
GET_WEB_VITALS_BY_NAV_TYPE: {
  key: "GET_WEB_VITALS_BY_NAV_TYPE",
  apiPath: `/v1/projects/:projectId/web-vitals/screen/:screenName/by-nav-type`,
  method: API_METHODS.GET,
},
```

---

## File structure (pulse-ui)

```
pulse-ui/src/screens/WebVitals/
├── index.ts
├── WebVitals.tsx                    ← overview screen (Screen 1)
├── WebVitals.module.css
├── WebVitals.constants.ts
├── WebVitals.interface.ts
└── components/
    ├── VitalSummaryCard/            ← single metric card (LCP p75 + rating bar)
    │   ├── VitalSummaryCard.tsx
    │   └── VitalSummaryCard.module.css
    ├── VitalRatingBar/              ← % good / needs-improvement / poor bar
    │   └── VitalRatingBar.tsx
    └── ByScreenTable/               ← sortable table
        └── ByScreenTable.tsx

pulse-ui/src/screens/WebVitalsScreenDetail/
├── index.ts
├── WebVitalsScreenDetail.tsx        ← Screen 2 (drill-down)
├── WebVitalsScreenDetail.module.css
├── WebVitalsScreenDetail.interface.ts
└── components/
    ├── VitalTrendChart/             ← line chart (Mantine recharts or existing chart pattern)
    │   └── VitalTrendChart.tsx
    └── ByNavTypeTable/
        └── ByNavTypeTable.tsx

pulse-ui/src/hooks/useWebVitalsSummary/
├── index.ts
└── useWebVitalsSummary.ts          ← TanStack Query for /summary

pulse-ui/src/hooks/useWebVitalsByScreen/
├── index.ts
└── useWebVitalsByScreen.ts

pulse-ui/src/hooks/useWebVitalsScreenSummary/
├── index.ts
└── useWebVitalsScreenSummary.ts

pulse-ui/src/hooks/useWebVitalsScreenTrend/
├── index.ts
└── useWebVitalsScreenTrend.ts
```

---

## Backend file structure (pulse-server)

```
resources/web-vitals/
├── WebVitalsResource.java           ← REST controller
├── WebVitalsMapper.java             ← MapStruct (request → params, row → DTO)
└── dto/
    ├── WebVitalsSummaryResponse.java
    ├── WebVitalsVitalSummary.java
    ├── WebVitalsByScreenResponse.java
    ├── WebVitalsScreenRow.java
    └── WebVitalsVitalMetric.java

service/web-vitals/
├── WebVitalsService.java
└── impl/
    └── WebVitalsServiceImpl.java

dao/web-vitals/
├── WebVitalsDao.java
└── WebVitalsQueries.java           ← all SQL constants
```

---

## Data model — response types (TypeScript)

```ts
// WebVitals.interface.ts
export interface VitalMetric {
  p75: number;
  p95: number;
  goodPct: number;
  needsImprovementPct: number;
  poorPct: number;
  pageLoads: number;
}

export type VitalRating = "good" | "needs-improvement" | "poor";

export interface WebVitalsSummaryResponse {
  lcp: VitalMetric;
  inp: VitalMetric;
  cls: VitalMetric;
}

export interface WebVitalsScreenRow {
  screenName: string;
  lcp: { p75: number; rating: VitalRating };
  inp: { p75: number; rating: VitalRating };
  cls: { p75: number; rating: VitalRating };
  pageLoads: number;
}

export interface WebVitalsByScreenResponse {
  total: number;
  rows: WebVitalsScreenRow[];
}

export interface WebVitalsTrendBucket {
  timestamp: string;
  p75: number;
  pageLoads: number;
}

export interface WebVitalsTrendResponse {
  buckets: WebVitalsTrendBucket[];
}
```

---

## Rating thresholds (shared constant)

```ts
// WebVitals.constants.ts
export const WEB_VITAL_THRESHOLDS = {
  LCP:  { good: 2500, poor: 4000 },   // ms
  INP:  { good: 200,  poor: 500  },   // ms
  CLS:  { good: 0.1,  poor: 0.25 },   // unitless
} as const;

export type VitalName = "LCP" | "INP" | "CLS";

export function getRating(name: VitalName, value: number): VitalRating {
  const t = WEB_VITAL_THRESHOLDS[name];
  if (value < t.good) return "good";
  if (value < t.poor) return "needs-improvement";
  return "poor";
}

export function formatVitalValue(name: VitalName, value: number): string {
  if (name === "CLS") return value.toFixed(3);
  if (value >= 1000) return `${(value / 1000).toFixed(1)}s`;
  return `${Math.round(value)}ms`;
}
```

---

## TanStack Query hooks

```ts
// useWebVitalsSummary.ts
export function useWebVitalsSummary(
  projectId: string,
  params: { from: string; to: string; navigationType?: string }
) {
  return useQuery({
    queryKey: ["web-vitals-summary", projectId, params],
    queryFn: () => makeRequest<WebVitalsSummaryResponse>(
      API_ROUTES.GET_WEB_VITALS_SUMMARY.apiPath
        .replace(":projectId", projectId),
      { method: "GET", params }
    ),
    enabled: !!projectId,
    staleTime: 60_000,
  });
}
```

Same pattern for `useWebVitalsByScreen`, `useWebVitalsScreenSummary`, `useWebVitalsScreenTrend`.

---

## Edge cases and display logic

| Scenario | Behaviour |
|----------|-----------|
| No web vital data yet (SDK not installed) | Show empty state: "No web vitals data. Install the Web SDK to start collecting LCP, INP, and CLS." with link to docs |
| Screen has LCP but not INP (no user interaction) | Show `—` for INP, not 0 |
| CLS value display | Show as decimal (0.082), not ms — use `formatVitalValue` |
| All page loads for a screen are "good" rating | Rating bar shows 100% green |
| p75 CLS is 0.00 | Show "0.000" — do not show as blank |
| Navigation type filter applied with no data for that type | Empty state per section, not whole page error |
| Very long screen names (e.g. `/users/:id/settings/notifications/email`) | Truncate with tooltip in table |
| `screenName` URL param in Screen 2 needs decoding | `decodeURIComponent` — screen names may contain `/` encoded as `%2F` |
| Web vitals not available in non-web projects (Android/iOS only) | Gate: only show "Web Vitals" navbar item when project has `platform = web` data |
| bfcache navigations (`back-forward`) typically have much lower LCP | Note in tooltip: "back-forward LCP is usually lower because the page is restored from cache, not re-rendered" |

---

## `screenName` URL encoding

Screen names contain `/` (e.g. `/products/:id`). When used as a URL param in `/web-vitals/:screenName`, the `/` must be encoded.

```ts
// Navigation to detail
navigate(`/projects/${projectId}/web-vitals/${encodeURIComponent(screenName)}`);

// Reading from param
const { screenName } = useParams();
const decodedScreen = decodeURIComponent(screenName ?? "");
```

Backend: URL-decode the `screenName` path param before passing to ClickHouse query.

---

## Integration with existing Screens section

`ScreenDetail` (`/projects/:projectId/screens/:screenName`) already shows per-screen data (crashes, session replays, network). Add a "Web Vitals" tab:

```
Tabs: Overview | Crashes | Network | Session Replays | Web Vitals (new)
```

The "Web Vitals" tab renders `WebVitalsScreenSummary` component (reused from Screen 2) inline. Only visible when `platform = web` or when web vital data exists for this screen. Use the same `useWebVitalsScreenSummary` hook.

---

## Tests

### Unit tests (React Testing Library + Jest)

| Test | What |
|------|------|
| `VitalSummaryCard` renders p75, rating badge, rating bar | Mock `VitalMetric`, assert correct text and color class |
| `getRating` thresholds | LCP 2499 → good, 2500 → needs-improvement, 4000 → poor |
| `formatVitalValue` | LCP 1800 → "1.8s", LCP 900 → "900ms", CLS 0.08 → "0.080" |
| `ByScreenTable` sorts on column click | Click LCP header → rows reorder |
| Empty state renders when data is empty | `rows: []` → "No web vitals data" text visible |
| `WebVitals` hook triggers refetch on filter change | Change time range → `queryKey` changes → new request |
| ScreenDetail "Web Vitals" tab hidden when no web data | `pageLoads: 0` → tab absent |

### Integration tests

| Test | What |
|------|------|
| `GET /v1/projects/:projectId/web-vitals/summary` returns 200 with correct shape | Mock ClickHouse, assert DTO |
| Returns 403 when user not member of project | Auth check |
| `by-screen` pagination: `page=1&pageSize=5` returns correct slice | |
| `screenName` with encoded `/` decoded correctly in backend | `/products%2F%3Aid` → `/products/:id` in query |

---

## Touchpoints checklist

| Area | Action | Done |
|------|--------|------|
| `pulse-ui/src/constants/Constants.ts` | Add 2 routes, 1 navbar route, 1 navbar item, 5 API routes | [ ] |
| `pulse-ui/src/screens/WebVitals/` | New screen — overview | [ ] |
| `pulse-ui/src/screens/WebVitalsScreenDetail/` | New screen — drill-down | [ ] |
| `pulse-ui/src/screens/ScreenDetail/` | Add "Web Vitals" tab | [ ] |
| `pulse-ui/src/hooks/useWebVitals*/` | 4 TanStack Query hooks | [ ] |
| `WebVitals.constants.ts` | `WEB_VITAL_THRESHOLDS`, `getRating`, `formatVitalValue` | [ ] |
| `pulse-server/resources/web-vitals/` | `WebVitalsResource.java` + mapper + DTOs | [ ] |
| `pulse-server/service/web-vitals/` | Service interface + impl | [ ] |
| `pulse-server/dao/web-vitals/` | DAO + `WebVitalsQueries.java` (all SQL constants) | [ ] |
| Unit tests (React) | `VitalSummaryCard`, `getRating`, `formatVitalValue`, `ByScreenTable` | [ ] |
| Unit tests (Java) | `WebVitalsServiceImplTest`, `WebVitalsResourceTest` | [ ] |

---

## What is NOT in this plan (Later)

- Alert definitions on web vitals (e.g. "alert when p75 LCP exceeds 3s") — needs alert system integration
- Time-series trend on the overview screen (only on drill-down)
- Comparing vitals across app versions or platforms
- FID / FCP charts (optional vitals, no UI until core 3 shipped and adopted)
- AI agent integration for "why is my LCP slow" queries
