# Heatmap API contract — as implemented in Pulse UI

This document mirrors **`pulse-ui`** today (`heatmap.types.ts`, `heatmapApi.ts`, `useHeatmapData`). Use it as the write-up for product/backend alignment.

**Code source of truth:** `pulse-ui/src/screens/ScreenDetail/Heatmap/heatmap.types.ts` and `heatmapApi.ts`.

**Transport:** All calls go through `makeRequest`, which returns an envelope:

```ts
{ data: HeatmapDataResponse | null; error?: { message: string; ... } }
```

The JSON shapes below describe **`data`** (the heatmap payload), not the envelope.

**Naming:** Types and the UI use **camelCase** field names (`screenName`, `glow_map` is snake_case for the layer key only—see response). If the backend emits **snake_case** (`screen_name`), the client must map or the server should match this contract.

---

## 1. Fetch aggregated layers for one screen

### 1.1 Primary path (used by `HeatmapPanel`)

**`GET /v1/heatmap/data`**

Query parameters (built by `buildHeatmapDataQueryString`):

| Query param     | Required | Description |
|----------------|----------|-------------|
| `screenName`   | **Yes**  | Screen key (same as Screen detail route). Always sent. |
| `from`         | **Yes** in practice* | Start of range, ISO-8601 UTC (from `useHeatmapData` / `dayjs.utc` → `toISOString()`). |
| `to`           | **Yes** in practice* | End of range, ISO-8601 UTC. |
| `app_version`  | No       | Sent when the user selects an app version in header **Filters** (`filterValues.APP_VERSION`). |
| `platform`     | No       | Sent when the user selects a platform in header **Filters** (`filterValues.PLATFORM`). |
| `aspect_ratio` | No       | Supported in client; **not** exposed in heatmap UI today—omit unless you wire a control. |
| `cohort_id`    | No       | Supported in client; **not** exposed in heatmap UI today. |
| `layers`       | No       | Comma-separated layer filter (e.g. trim payload); **not** sent by current UI. |

\*The hook enables the query only when `screenName`, formatted `from`, and formatted `to` are non-empty.

**Optional alternate path (implemented, not used by `HeatmapPanel`):**

- **`POST /api/v1/projects/:projectId/heatmap/data`**
- Body: `HeatmapDataRequestBody` — `screenName`, `timeRange: { start, end }`, optional `app_version`, `platform`, `aspect_ratio`, `cohort_id`, `includeLayers?: ("glow" \| "frustration" \| "observability")[]`
- Enable via `useHeatmapData({ ..., usePost: true })` (requires `projectId` from `useProjectContext`).

---

### 1.2 Compare mode (as implemented in UI)

There is **no** dedicated compare HTTP endpoint in the client anymore.

**Compare** = **two parallel `GET /v1/heatmap/data`** calls with the same `from`, `to`, and filter params:

1. `screenName` = current screen  
2. `screenName` = “other screen” from compare UI  

Shared color scale for the two charts is computed **in the browser** from both responses (max `weight` over the selected layer).

---

## 2. Response body: `HeatmapDataResponse`

### 2.1 `metadata`

| Field            | Type     | Notes |
|------------------|----------|--------|
| `screenName`     | `string` | Echo of logical screen. |
| `ui_hash`        | `string` | Layout / coordinate-frame fingerprint for this screenshot and `x,y` normalization. |
| `screenshot_url` | `string` | Underlay image URL (can be empty string if none). |
| `total_events`   | `number` | Used for subtitles and client-side “heatmap quality” heuristic. |
| `app_version`    | optional | Echo / resolved version. |
| `platform`       | optional | Echo / resolved platform. |
| `aspect_ratio`   | optional | Echo if filtered. |
| `created_at`     | optional | ISO string. |

### 2.2 `layers`

| Layer key            | Shape |
|----------------------|--------|
| `glow_map`           | `Array<{ x: number; y: number; weight: number }>` |
| `frustration_map`    | `{ rage: FrustrationPoint[]; dead: FrustrationPoint[] }` |
| `observability_map`  | `{ error_clicks: ErrorClickPoint[]; latency_hotspots: LatencyHotspot[] }` |

**`FrustrationPoint`:** `{ x, y, weight, avg_sequence_count? }`

**`ErrorClickPoint`:** `{ x, y, weight, error_code? }`

**`LatencyHotspot`:** `{ x, y, avg_latency_ms, weight? }`

**Coordinates:** `x` and `y` are **normalized 0–1** in the layout frame for `ui_hash` (see UI copy / viz).

---

## 3. Example JSON (matches mock fixture shape)

```json
{
  "metadata": {
    "screenName": "HomeScreen",
    "ui_hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "screenshot_url": "/heatmap-pulse-rn-telemetry-underlay.png",
    "total_events": 15420,
    "app_version": "2.1.0",
    "platform": "Android",
    "aspect_ratio": "19.5:9",
    "created_at": "2026-03-01T12:00:00.000Z"
  },
  "layers": {
    "glow_map": [
      { "x": 0.45, "y": 0.82, "weight": 10000 },
      { "x": 0.21, "y": 0.62, "weight": 500 },
      { "x": 0.72, "y": 0.35, "weight": 120 }
    ],
    "frustration_map": {
      "rage": [
        { "x": 0.45, "y": 0.82, "weight": 450, "avg_sequence_count": 5 }
      ],
      "dead": [
        { "x": 0.1, "y": 0.1, "weight": 120 }
      ]
    },
    "observability_map": {
      "error_clicks": [
        { "x": 0.88, "y": 0.05, "weight": 15, "error_code": "500" }
      ],
      "latency_hotspots": [
        { "x": 0.5, "y": 0.5, "avg_latency_ms": 2450, "weight": 300 }
      ]
    }
  }
}
```

---

## 4. Legacy types still in repo (optional)

`HeatmapCompareResponse` and `heatmapMockCompare` remain for **mock / historical** POST-compare payloads; **the Heatmap tab does not call** a compare POST anymore.

---

## 5. UI mapping (signal chips → layers)

| UI signal | Layer consumed for blobs |
|-----------|---------------------------|
| Tap / Scroll / Gesture | `glow_map` |
| Rage | `frustration_map.rage` (fallback `glow_map` if empty) |
| Dead | `frustration_map.dead` (fallback `glow_map` if empty) |

`observability_map` is **not** wired to the main viz in the current UI.
