# Heatmap — UI Specification

UI-only reference: **API contract**, **data shapes**, **rendering logic**, **compare flow**, **routes**, and **test scenarios**. Backend storage (ClickHouse, Redis) is out of scope here.

---

## 1. API contract (frontend ↔ backend)

### 1.1 Fetch heatmap layers + screenshot metadata

**Option A — Query string (simple filters)**

`GET /v1/heatmap/data`

| Query param | Required | Description |
|-------------|----------|-------------|
| `screenName` | Yes | Screen key (same as route segment). URL-encode when calling. |
| `from` | No | ISO-8601 start (default: last 24h). |
| `to` | No | ISO-8601 end. |
| `app_version` | No | Default: latest or “all” per product. |
| `platform` | No | e.g. `Android`, `iOS`. |
| `aspect_ratio` | No | e.g. `19.5:9`. |
| `cohort_id` | No | Default: `ALL`. |
| `layers` | No | Comma list: `glow,frustration,observability` to trim payload. |

**Auth:** Same as other project APIs (cookies / bearer). **Project** resolved from session or `projectId` in path if you namespace the route (e.g. `/v1/projects/:projectId/heatmap/data`).

**Option B — POST (heavy / compare-prefetch)**

`POST /api/v1/projects/:projectId/heatmap/data`

Use when filters mirror **Screen detail** (large filter JSON) or **compare** prefetch.

**Request body (shape):**

```typescript
interface HeatmapDataRequest {
  screenName: string;
  timeRange: { start: string; end: string }; // ISO UTC
  app_version?: string;
  platform?: string;
  aspect_ratio?: string;
  cohort_id?: string;
  /** Which logical layers to include */
  includeLayers?: Array<"glow" | "frustration" | "observability">;
}
```

**Response:** Same as §2 (`HeatmapDataResponse`).

---

### 1.2 Compare mode (two targets, same filters)

`POST /api/v1/projects/:projectId/heatmap/compare`

```typescript
interface HeatmapCompareRequest extends HeatmapDataRequest {
  /** Second screen or same screen + different variant */
  compare: {
    screenName: string;
    /** Optional A/B or layout variant */
    variantId?: string | null;
  };
  /** Base target uses top-level screenName + optional variantId */
  variantId?: string | null;
}

interface HeatmapCompareResponse {
  shared: Pick<HeatmapDataResponse, "metadata"> extends never
    ? never
    : {
        timeRange: { start: string; end: string };
        filtersApplied: Record<string, unknown>;
      };
  left: HeatmapDataResponse;
  right: HeatmapDataResponse;
  /** Optional: normalize color scale across panels */
  colorScaleMax?: number;
}
```

**Errors:** `400` validation, `404` unknown screen / no assets, `403` project access.

---

## 2. Data scheme (UI consumption)

The UI treats the backend response as **read-only**; all coordinates are **normalized** `0.0–1.0` relative to the screenshot **layout bounds** for that `ui_hash`.

```typescript
interface HeatmapDataResponse {
  metadata: {
    screenName: string;
    ui_hash: string;
    screenshot_url: string;
    total_events: number;
    app_version?: string;
    platform?: string;
    aspect_ratio?: string;
    created_at?: string; // registry first-seen
  };
  layers: {
    /** Volume / attention — drives thermal “glow” */
    glow_map: Array<{ x: number; y: number; weight: number }>;
    frustration_map: {
      rage: Array<{ x: number; y: number; weight: number; avg_sequence_count?: number }>;
      dead: Array<{ x: number; y: number; weight: number }>;
    };
    observability_map: {
      error_clicks: Array<{
        x: number;
        y: number;
        weight: number;
        error_code?: string | number;
      }>;
      latency_hotspots: Array<{
        x: number;
        y: number;
        avg_latency_ms: number;
        weight?: number;
      }>;
    };
  };
}
```

**Field usage:**

| Field | UI use |
|-------|--------|
| `metadata.screenshot_url` | Base image under overlays. |
| `metadata.ui_hash` | Cache key; optional “layout mismatch” guard if user switches version. |
| `metadata.total_events` | Empty-state copy, footers. |
| `layers.glow_map` | Heat / density layer. |
| `layers.frustration_map.*` | Icon or secondary heat when toggles on. |
| `layers.observability_map.*` | Latency pulse, error badges. |

---

## 3. Logic for plotting the heatmap

### 3.1 Layout structure

1. **Container:** A wrapper with `position: relative` and fixed **aspect ratio** (match image) or `max-height` with `object-fit: contain`.
2. **Base layer:** `<img src={screenshot_url} />` or `background-image` — **ref** to read `clientWidth` / `clientHeight` after load.
3. **Overlay:** `position: absolute; inset: 0` — **Canvas** or **SVG** matching the **displayed** image box (not natural image size if CSS scales).

### 3.2 Coordinate transform

For each point `(x, y)` in `[0,1]`:

```
pixelX = x * overlayWidth
pixelY = y * overlayHeight
```

On **window resize** or **image load**, re-read dimensions and **redraw** (or use `ResizeObserver` on the container).

### 3.3 Glow layer (`glow_map`)

- Each entry has **`weight`** (aggregated count for that bin).
- **Normalize weights** for display: `wNorm = weight / maxWeight` across visible points (or use global `colorScaleMax` from compare API).
- **Render options:**
  - **heatmap.js** (or similar): `{ x, y, value: wNorm }` in **pixel** coords.
  - **Canvas:** radial gradients with alpha ∝ `wNorm`, or blur pass.
- **Color ramp:** cold (low) → hot (high); consistent with Pulse design tokens.

### 3.4 Frustration layer

- When **Rage** or **Dead** toggle is on, draw **small markers** at `(pixelX, pixelY)`.
- **Optional:** scale marker size by `Math.log1p(weight)` to avoid clutter.

### 3.5 Observability layer

- **Latency:** If `avg_latency_ms > threshold` (configurable), draw **warning ring** or **pulsing** animation at that coordinate.
- **Errors:** Badge or icon at coordinate; tooltip shows `error_code`.

### 3.6 Layer order (bottom → top)

1. Screenshot  
2. Glow  
3. Frustration (optional)  
4. Observability (optional)  
5. **Interaction** (hover, focus region for compare — see §4)

### 3.7 Empty / loading / error

| State | UI behavior |
|-------|-------------|
| **Loading** | Skeleton over image area; no partial layers. |
| **Empty** (`total_events === 0` or empty arrays) | Illustration + copy; keep filters visible. |
| **Error** | Inline error; retry; keep tab context. |
| **No screenshot** (`screenshot_url` missing) | Placeholder; still show data dots if backend returns bins. |

---

## 4. Heatmap compare flow

### 4.1 Entry

- **From Screen detail:** User enables **Compare** on Heatmap tab → **second screen** picker (search or list) + optional **variant** / **app version** for each side.
- **Deep link (optional):** `?tab=heatmap&compare=1&rightScreen=...` — parse on mount.

### 4.2 Request

- Single `POST .../heatmap/compare` with **same** `timeRange` and **same** header filters for both sides.
- **Left** = current screen (from route); **Right** = selected compare target.

### 4.3 UI layout

- **Two columns** (desktop): two **stacked** “screenshot + overlay” panels with **shared** legend and **colorScaleMax** when provided.
- **Narrow:** stacked vertically; same data flow.

### 4.4 Behavior

- **Sync pan/zoom** (if implemented): optional; v1 can be static.
- **Legend:** same min/max for fairness when `colorScaleMax` present.
- **Exit compare:** clears compare state, returns to single heatmap fetch.

---

## 5. Navigation routes

### 5.1 Existing (pulse-ui)

| Route | Constant |
|-------|----------|
| Screen list | `/projects/:projectId/screens` — `ROUTES.PROJECT_SCREENS` |
| Screen detail | `/projects/:projectId/screens/:screenName` — `ROUTES.PROJECT_SCREEN_DETAILS` |

`screenName` is **URL-encoded** in the path (`encodeURIComponent` when navigating).

### 5.2 Heatmap tab (to implement)

- **Screen detail** gains a **fourth tab** (e.g. “Heatmap”) alongside Engagement / Performance / Network.
- **Optional query params:**

| Param | Example | Purpose |
|-------|---------|---------|
| `tab` | `heatmap` | Open Screen detail on Heatmap tab. |
| `compare` | `1` | Open compare mode. |
| `rightScreen` | encoded name | Pre-fill compare target. |

Example:

`/projects/proj-1/screens/checkout%20home?tab=heatmap`

### 5.3 Drill-down navigation (preserve context)

| From | To | Params to preserve |
|------|-----|---------------------|
| Heatmap → “View sessions” | Session list / replay | `timeRange`, `screenName`, `projectId`, filters |
| Heatmap → Interaction | Critical interaction detail | `interactionId`, time + filters |
| Heatmap → Issue | App vitals issue detail | existing issue routes + screen filter |

Use **React Router** `navigate` with **state** or **search params** so `DateTimeRangePicker` / filter store can stay aligned.

---

## 6. Testing scenarios

### 6.1 API / contract

| ID | Scenario | Expected |
|----|------------|----------|
| T1 | `GET` with `screenName` only | `200`, `metadata` + `layers` with at least `glow_map` array. |
| T2 | Invalid `screenName` | `404` or empty `glow_map` + messaging per product. |
| T3 | `from` / `to` in future | Empty data or `400`. |
| T4 | `POST` compare with two valid screens | `200`, `left`/`right` same shape as single response. |
| T5 | `POST` compare with unauthorized `projectId` | `403`. |

### 6.2 Rendering

| ID | Scenario | Expected |
|----|------------|----------|
| T6 | `glow_map` single point, high weight | Visible hot spot at correct pixel. |
| T7 | `x=0,y=0` and `x=1,y=1` | Corners of overlay; no overflow. |
| T8 | Resize window | Overlays rescale; coords still align with image. |
| T9 | Toggle frustration off | Rage/dead markers not in DOM or hidden. |
| T10 | `latency_hotspots` above threshold | Warning style visible; tooltip shows `avg_latency_ms`. |

### 6.3 Compare

| ID | Scenario | Expected |
|----|------------|----------|
| T11 | Two panels, same `colorScaleMax` | Same color means same intensity across panels. |
| T12 | Exit compare | Single-panel fetch; no stale `right` data. |

### 6.4 Navigation

| ID | Scenario | Expected |
|----|------------|----------|
| T13 | Load `?tab=heatmap` | Heatmap tab active on mount. |
| T14 | Navigate away and back | Filters preserved if using global store. |
| T15 | Deep link with encoded `screenName` | `decodeURIComponent` matches API `screenName`. |

### 6.5 Accessibility / resilience

| ID | Scenario | Expected |
|----|------------|----------|
| T16 | `screenshot_url` fails to load | Fallback UI; overlay can still draw if data present. |
| T17 | Keyboard-only | Layer toggles reachable; focus visible. |

---

## 7. Mock E2E (local QA)

### 7.1 Enable mock server

1. Set `REACT_APP_USE_MOCK_SERVER=true` in the Pulse UI env (see project `.env.example`).
2. Set `REACT_APP_PULSE_SERVER_URL` to your API base (mock intercepts via `makeRequestToServer`).
3. Start the app: `yarn start`.

### 7.2 Flow

1. Open a project → **Screens** → open any screen (Screen detail).
2. Select the **Heatmap** tab (or append `?tab=heatmap` to the URL).
3. With mocks, **GET `/v1/heatmap/data`** returns layered JSON from [heatmapMockFixtures.ts](../../pulse-ui/src/mocks/responses/heatmapMockFixtures.ts).
4. **Compare:** enable “Compare with another screen”, enter a second screen name; **POST `/api/v1/projects/:projectId/heatmap/compare`** returns left/right payloads.

### 7.3 Special `screenName` values (mock only)

| `screenName` | Behavior |
|----------------|----------|
| `__empty__` | Empty `glow_map`, `total_events: 0` |
| `__error__` | HTTP 500 from mock (error UI) |

### 7.4 Implementation map

| Piece | Location |
|-------|----------|
| API routes | `pulse-ui/src/constants/Constants.ts` — `GET_HEATMAP_DATA`, `POST_HEATMAP_DATA`, `POST_HEATMAP_COMPARE` |
| Types + `heatmapApi` | `pulse-ui/src/screens/ScreenDetail/Heatmap/` |
| Hooks | `pulse-ui/src/hooks/useHeatmapData/`, `useHeatmapCompare/` |
| Mock routing | `pulse-ui/src/mocks/MockResponseGenerator.ts` — `handleHeatmapEndpoints` |
| UI | `pulse-ui/src/screens/ScreenDetail/Heatmap/HeatmapPanel.tsx` |

**Note:** Heatmap fetches use **TanStack `useQuery`** (read-only). **`useMutation`** is not used until a state-changing heatmap API exists.

---

## Related files (in-repo)

| Topic | Location |
|-------|----------|
| Screen detail shell | `pulse-ui/src/screens/ScreenDetail/ScreenDetail.tsx` |
| Routes | `pulse-ui/src/constants/Constants.ts` (`PROJECT_SCREEN_DETAILS`, etc.) |
| Full-stack + data pipeline | `wireframes/heatmap/HEATMAP_INTEGRATED_SPEC.md` |
| Wireframes | `wireframes/heatmap/frames.pen` |
