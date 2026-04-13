# 08.2 — UI: Web SDK Data Support

**Goal:** Identify every place in `pulse-ui` that needs to change, adapt, or be built new so that web SDK data (`platform = 'web'`) is correctly displayed — without breaking existing Android/iOS views.

**Depends on:** `08.1-sdk-config-web-support.md`
**Touches:** `pulse-ui/src/screens/`, `pulse-ui/src/services/`, `pulse-ui/src/routes/`

---

## The Core Problem

The UI was built assuming `platform ∈ { android, ios }`. Web adds:

| Mobile assumption | Web reality |
|---|---|
| `device.model` → "Pixel 7", "iPhone 15" | `browser.name` + `browser.version` → "Chrome 120" |
| `network.carrier.name` → "Airtel", "Jio" | No carrier — `network.effective_type` → "4g", "wifi" |
| `os.version` → "Android 14", "iOS 17" | `os.name` → "macOS", `browser.version` → "120" |
| `screen.name` → "ProductDetailActivity" | `screen.name` → "/products/123" or "ProductDetail" |
| Session Replay = screenshot sequence | Session Replay = rrweb DOM events |
| No Web Vitals | LCP, CLS, INP, TTFB, FCP metrics |
| No click heatmap | `app.screen.coordinate.x/y/nx/ny` on every click |

---

## What Works Automatically (Zero Changes)

| Feature | Why |
|---|---|
| Platform filter dropdown | `useGetDashboardFilters()` returns platforms from backend — `web` appears when data flows in |
| Interactions / Funnel screen | Platform-agnostic data model; `PlatformDonutChart` already has web icon logic (line 109 in the component) |
| Home / Dashboard aggregate metrics | No platform-specific attributes |
| User Engagement screen | No platform-specific code |
| SDK Config screen | `pulse_web_js` addition in `08.1` flows through to the UI dropdown |
| Query Builder | Free-form — analyst can query any attribute |
| Alerts | Platform-agnostic rule definitions |

---

## Category A — Conditional Attribute Mapping (Low Risk)

These are existing components that display mobile-only attributes. They need to detect platform and render different fields for web — no new screens, just conditional logic.

### A1. `DeviceInformation` Component

**File:** `pulse-ui/src/screens/AppVitals/components/DeviceInformation.tsx`

Current mobile-only fields: `manufacturer`, `model`, `osVersion`, `screenResolution`, `ramTotal`, `ramAvailable`, `storageTotal`, `storageFree`, `batteryLevel`, `connectionType`, `carrier`

**Change:** Make it polymorphic on `platform`:

```typescript
// DeviceInformation.tsx
function DeviceInformation({ attributes }: { attributes: Record<string, string> }) {
  const platform = attributes['platform'];

  if (platform === 'web') {
    return <WebDeviceInformation attributes={attributes} />;
  }
  return <MobileDeviceInformation attributes={attributes} />;
}

// Web variant — shows browser and connectivity info
function WebDeviceInformation({ attributes }) {
  return (
    <InfoGrid>
      <InfoRow label="Browser"      value={`${attributes['browser.name']} ${attributes['browser.version']}`} />
      <InfoRow label="OS"           value={`${attributes['os.name']} ${attributes['os.version']}`} />
      <InfoRow label="Device Type"  value={attributes['device.type']} />           {/* mobile / tablet / desktop */}
      <InfoRow label="Screen"       value={`${attributes['device.screen.width']} × ${attributes['device.screen.height']}`} />
      <InfoRow label="Connection"   value={attributes['network.effective_type']} />{/* 4g / 3g / wifi */}
      <InfoRow label="Language"     value={attributes['browser.language']} />
    </InfoGrid>
  );
}
```

---

### A2. `ResourceAttributesPanel` Mappings

**File:** `pulse-ui/src/screens/SessionTimeline/components/ResourceAttributesPanel/utils/resourceMappings.ts`

Current: maps `device.model` → "Device", `os.version` → "OS Version", `device.manufacturer` → "Manufacturer"

**Change:** Add web attribute mappings:

```typescript
export const RESOURCE_ATTRIBUTE_MAPPINGS: Record<string, AttributeMapping> = {
  // Existing mobile mappings
  'device.model':          { label: 'Device',        platform: ['android', 'ios'] },
  'device.manufacturer':   { label: 'Manufacturer',   platform: ['android', 'ios'] },
  'os.version':            { label: 'OS Version',     platform: ['android', 'ios'] },
  'network.carrier.name':  { label: 'Carrier',        platform: ['android', 'ios'] },

  // Web mappings
  'browser.name':          { label: 'Browser',        platform: ['web'] },
  'browser.version':       { label: 'Browser Version',platform: ['web'] },
  'os.name':               { label: 'OS',             platform: ['web'] },
  'device.type':           { label: 'Device Type',    platform: ['web'] },
  'network.effective_type':{ label: 'Connection',     platform: ['web'] },
  'browser.language':      { label: 'Language',       platform: ['web'] },

  // Common to all platforms
  'device.screen.width':   { label: 'Screen Width',   platform: ['android', 'ios', 'web'] },
  'device.screen.height':  { label: 'Screen Height',  platform: ['android', 'ios', 'web'] },
  'installation.id':       { label: 'Installation ID',platform: ['android', 'ios', 'web'] },
  'session.id':            { label: 'Session ID',     platform: ['android', 'ios', 'web'] },
};

// Filter mappings by current session's platform
export function getMappingsForPlatform(platform: string): AttributeMapping[] {
  return Object.entries(RESOURCE_ATTRIBUTE_MAPPINGS)
    .filter(([, mapping]) => mapping.platform.includes(platform))
    .map(([key, mapping]) => ({ key, ...mapping }));
}
```

---

### A3. SessionTimeline Query Builder — Conditional Carrier Filter

**File:** `pulse-ui/src/screens/SessionTimeline/utils/buildQuery.ts`

Current: always includes `network.carrier.name` in filter dimensions

**Change:** Only include carrier filter when platform is Android/iOS:

```typescript
function buildSessionTimelineQuery(filters: SessionFilters) {
  const platformIsWeb = filters.platform === 'web';

  const dimensions = [
    'session.id',
    'screen.name',
    'url.path',
    // Carrier only available on mobile
    ...(!platformIsWeb ? ['network.carrier.name'] : []),
  ];

  const deviceDimension = platformIsWeb
    ? 'browser.name'       // web: browser name
    : 'device.model';      // mobile: device model

  return buildQuery({ dimensions, deviceDimension, ...filters });
}
```

---

### A4. SessionTimeline API Response Transform

**File:** `pulse-ui/src/screens/SessionTimeline/utils/transformApiResponse.ts`

**Change:** Conditionally map device display name based on platform:

```typescript
function transformSession(raw: RawSession): Session {
  const platform = raw['platform'];
  const isWeb = platform === 'web';

  return {
    sessionId:    raw['session.id'],
    platform,
    // Web: "Chrome 120 · macOS"  |  Mobile: "Pixel 7 · Android 14"
    deviceLabel:  isWeb
      ? `${raw['browser.name']} ${raw['browser.version']} · ${raw['os.name']}`
      : `${raw['device.model']} · ${raw['os.version']}`,
    screenName:   raw['screen.name'],
    installationId: raw['installation.id'],
    carrier:      isWeb ? null : raw['network.carrier.name'],
    connection:   raw['network.effective_type'] ?? raw['network.connection.type'],
  };
}
```

---

### A5. App Vitals Filters — OS Version Label

**File:** `pulse-ui/src/screens/AppVitals/components/AppVitalsFilters/AppVitalsFilters.tsx`

Current filter label: "OS Version" — still correct for web (macOS 14, Windows 11)

**Change:** When platform = web, also add "Browser" as a filter dimension (maps to `browser.name`):

```typescript
// Add browser filter only for web
const filterOptions = [
  { key: 'APP_VERSION',      label: 'App Version' },
  { key: 'PLATFORM',         label: 'Platform' },
  { key: 'OS_VERSION',       label: 'OS Version' },
  // Conditionally show based on selected platform:
  ...(selectedPlatform !== 'web'
    ? [{ key: 'NETWORK_PROVIDER', label: 'Network Provider' }]
    : [{ key: 'BROWSER',          label: 'Browser' }]),
  { key: 'STATE',            label: 'State' },
];
```

---

## Category B — New Screen: Web Vitals

**Route:** `PROJECT_WEB_VITALS` → `/projects/:projectId/web-vitals`
**File:** `pulse-ui/src/screens/WebVitals/WebVitals.tsx`

The UI has a `CoreWebVitals` interface already defined in `/services/sessionReplay/types.ts` (with `lcp`, `fid`, `cls` fields) — meaning this was anticipated. It needs a full screen.

### Data

Queries the `web_vital` signal type from ClickHouse:
```sql
SELECT metric.name, metric.value, metric.rating, url.path, browser.name, timestamp
FROM otel.otel_logs
WHERE platform = 'web'
  AND pulse.type = 'web_vital'
  AND project_id = ?
  AND timestamp BETWEEN ? AND ?
```

### Layout

```
┌─────────────────────────────────────────────────────────┐
│  Web Vitals                         [Time range] [Filter]│
├──────────┬──────────┬──────────┬──────────┬─────────────┤
│   LCP    │   CLS    │   INP    │   TTFB   │    FCP      │
│  2.1s    │  0.05    │  180ms   │  320ms   │  1.2s       │
│ 🟢 Good  │ 🟢 Good  │ 🟢 Good  │ 🟢 Good  │ 🟢 Good     │
│ p75 score│          │          │          │             │
├──────────┴──────────┴──────────┴──────────┴─────────────┤
│  LCP Trend (line chart, 7 days)                         │
├─────────────────────────────────────────────────────────┤
│  Top Pages by LCP    │  Browser Breakdown               │
│  /checkout   3.2s 🔴 │  Chrome   2.1s                   │
│  /products   2.8s 🟡 │  Safari   2.4s                   │
│  /home       1.9s 🟢 │  Firefox  1.8s                   │
├─────────────────────────────────────────────────────────┤
│  LCP Attribution Detail (element, load_delay, render)   │
└─────────────────────────────────────────────────────────┘
```

### Components to Build

```
pulse-ui/src/screens/WebVitals/
├── WebVitals.tsx                        Main screen
├── components/
│   ├── VitalScoreCard/
│   │   └── VitalScoreCard.tsx           Good/NI/Poor badge + p75 value
│   ├── VitalTrendChart/
│   │   └── VitalTrendChart.tsx          Line chart over time
│   ├── TopPagesByVital/
│   │   └── TopPagesByVital.tsx          Table: url.path → p75 value + rating
│   ├── BrowserBreakdown/
│   │   └── BrowserBreakdown.tsx         Bar chart: browser.name → p75 value
│   └── LcpAttributionPanel/
│       └── LcpAttributionPanel.tsx      lcp.element, lcp.load_delay, lcp.render_delay
└── hooks/
    ├── useWebVitals.ts                  Fetches aggregate p75 per metric
    └── useVitalTrend.ts                 Time-series data for a specific metric
```

### Metric Thresholds (for rating badges)

```typescript
export const VITAL_THRESHOLDS = {
  LCP:  { good: 2500, poor: 4000 },   // ms
  CLS:  { good: 0.1,  poor: 0.25 },   // score
  INP:  { good: 200,  poor: 500 },    // ms
  TTFB: { good: 800,  poor: 1800 },   // ms
  FCP:  { good: 1800, poor: 3000 },   // ms
};

export function getVitalRating(metric: string, value: number): 'good' | 'needs-improvement' | 'poor' {
  const t = VITAL_THRESHOLDS[metric];
  if (!t) return 'good';
  if (value <= t.good) return 'good';
  if (value <= t.poor) return 'needs-improvement';
  return 'poor';
}
```

---

## Category C — Session Replay Player: rrweb Support

**Current:** Screenshot-based player (`ReplayImageView.tsx`) — renders `imageUrl` frames
**Needed:** DOM-based rrweb player for web sessions

### Conditional Player Rendering

```typescript
// SessionReplayDetail/components/player/ReplayPlayer.tsx

function ReplayPlayer({ session }: { session: SessionReplaySession }) {
  if (session.platform === 'web') {
    return <RrwebReplayPlayer sessionId={session.id} />;
  }
  return <ScreenshotReplayPlayer snapshots={session.snapshots} />;
}
```

### `RrwebReplayPlayer` Component

```typescript
// SessionReplayDetail/components/player/RrwebReplayPlayer.tsx
import { Replayer } from 'rrweb';
import 'rrweb/dist/style.css';

function RrwebReplayPlayer({ sessionId }: { sessionId: string }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const replayerRef = useRef<Replayer | null>(null);
  const { events, isLoading } = useRrwebEvents(sessionId);

  useEffect(() => {
    if (!events || !containerRef.current) return;

    replayerRef.current = new Replayer(events, {
      root: containerRef.current,
      speed: 1,
      showWarning: false,
      showDebug: false,
      blockClass: 'pulse-block',
      maskTextClass: 'pulse-mask',
    });

    return () => {
      replayerRef.current?.destroy();
    };
  }, [events]);

  return (
    <div className="rrweb-replay-container">
      {isLoading && <LoadingSpinner />}
      <div ref={containerRef} />
      <ReplayControls replayer={replayerRef.current} />
    </div>
  );
}
```

### Event Fetching

The transport layer (04.3) sends rrweb events as base64(gzip(JSON)) chunks in OTLP logs. The UI needs to:
1. Fetch all `session_replay` log records for the session (by `replay.session_id`)
2. Sort by `replay.chunk_index`
3. Decode: base64 → gzip decompress → JSON parse → `eventWithTime[]`
4. Feed to `rrweb.Replayer`

```typescript
// hooks/useRrwebEvents.ts

async function useRrwebEvents(sessionId: string) {
  // 1. Fetch chunks from backend
  const chunks = await fetchReplayChunks(sessionId);
  // chunks sorted by replay.chunk_index

  // 2. Decode each chunk
  const events = chunks.flatMap(chunk => {
    const binary = atob(chunk['replay.payload']);
    const bytes = Uint8Array.from(binary, c => c.charCodeAt(0));
    const decompressed = pako.ungzip(bytes, { to: 'string' });
    return JSON.parse(decompressed) as eventWithTime[];
  });

  return events;
}
```

### New Backend Query Needed

The backend needs to support fetching session replay chunks by `replay.session_id`:

```
GET /v1/session-replay/web/{sessionId}/chunks
→ Returns array of log records where pulse.type = 'session_replay'
   ordered by replay.chunk_index
```

---

## Category D — Click Heatmap for Web

The web SDK sends `app.screen.coordinate.nx` and `app.screen.coordinate.ny` (0.0–1.0 normalised) on every click. These can power a click heatmap.

**File:** `pulse-ui/src/screens/Heatmap/components/ClickHeatmap.tsx` (new)

The existing "heatmap" in the codebase is a **geographic heatmap** (country-level). The click heatmap is a new, different feature.

### How It Works

```
Web SDK click event:
  app.screen.coordinate.nx = 0.45   ← 45% from left
  app.screen.coordinate.ny = 0.32   ← 32% from top
  url.path = '/checkout'
  click.is_rage = false

UI:
  1. Query all clicks for url.path = '/checkout'
  2. Plot nx/ny as percentage positions on a screenshot or blank canvas
  3. Use density rendering (heatmap.js or canvas gradient) to show hot spots
```

### Component Sketch

```typescript
// ClickHeatmap.tsx

function ClickHeatmap({ urlPath, timeRange }: ClickHeatmapProps) {
  const { clicks } = useClickData({ urlPath, timeRange });

  // Group into grid cells (e.g. 20×20 grid)
  const heatData = useMemo(() =>
    clicks.map(c => ({
      x: Math.round(c['app.screen.coordinate.nx'] * 100), // 0–100
      y: Math.round(c['app.screen.coordinate.ny'] * 100),
      value: 1,
      isRage: c['click.is_rage'],
    })), [clicks]);

  return (
    <div className="heatmap-wrapper" style={{ position: 'relative' }}>
      {/* Optional: screenshot of the page as background */}
      <HeatmapCanvas
        data={heatData}
        width={800}
        height={600}
        maxOpacity={0.8}
        radius={20}
      />
      {/* Rage clicks shown as red dots */}
      {heatData.filter(d => d.isRage).map((d, i) => (
        <RageDot key={i} x={`${d.x}%`} y={`${d.y}%`} />
      ))}
    </div>
  );
}
```

---

## Category E — `screen.name` Display Across Screens

On mobile, `screen.name` = `ProductDetailActivity` — a clean class name.
On web, `screen.name` = `/products/123` (raw) or `ProductDetail` (normalised via route patterns).

**Impact:** Everywhere the UI renders `screen.name`, it should:
- Display the normalised name if available
- Display the raw pathname if not (still readable)
- Not truncate or mangle URL-style names in table/dropdown

**Specific places to verify:**
- `ScreenList` screen — lists all screens by `screen.name` — URL paths will just appear as rows ✅ (no code change, just data looks different)
- `ScreenDetail` screen — shows detail for a specific `screen.name` — works if query uses `screen.name` value directly ✅
- Session Timeline — shows `screen.name` per event — works ✅
- Interaction step labels — uses event names, not screen names — unaffected ✅

No code changes needed — URL-style screen names are valid strings and will display correctly. The UX difference is that web screens look like `/checkout` instead of `CheckoutActivity`.

---

## New Routes Needed

**File:** `pulse-ui/src/routes/routes.tsx`

```typescript
// Add to PROJECT routes
PROJECT_WEB_VITALS: '/projects/:projectId/web-vitals',
```

**Sidebar navigation:** Add "Web Vitals" to project nav, visible only when project has web SDK data (or always visible as a dedicated section).

---

## Summary Table

| Area | File(s) | Change Type | Priority |
|---|---|---|---|
| DeviceInformation | `AppVitals/components/DeviceInformation.tsx` | Conditional render (web vs mobile fields) | High |
| Resource Attribute Mappings | `SessionTimeline/.../resourceMappings.ts` | Add browser.* web mappings | High |
| Session Timeline Query | `SessionTimeline/utils/buildQuery.ts` | Conditional carrier filter | High |
| Session Timeline Transform | `SessionTimeline/utils/transformApiResponse.ts` | Conditional device label | High |
| App Vitals Filters | `AppVitals/components/AppVitalsFilters.tsx` | Browser filter for web | Medium |
| **Web Vitals Screen** | `screens/WebVitals/` *(new)* | New screen + charts | High |
| **rrweb Replay Player** | `SessionReplayDetail/player/RrwebReplayPlayer.tsx` *(new)* | New component | High |
| Replay Player Router | `SessionReplayDetail/player/ReplayPlayer.tsx` | Platform-conditional rendering | High |
| rrweb Events Hook | `hooks/useRrwebEvents.ts` *(new)* | Fetch + decode chunks | High |
| **Click Heatmap** | `screens/Heatmap/ClickHeatmap.tsx` *(new)* | New component | Medium |
| Routes | `routes/routes.tsx` | Add `PROJECT_WEB_VITALS` | Medium |
| PlatformDonutChart | Already has web icon | Verify + test | Low |
| Platform filter | Automatic from backend | Verify + test | Low |
| ScreenList / ScreenDetail | No change needed | Test with URL-style names | Low |

---

## Done Criteria

### Category A — Attribute Mapping
- [ ] `DeviceInformation` shows browser/OS/connection for web sessions, device/carrier for mobile
- [ ] `ResourceAttributesPanel` shows `browser.name`, `browser.version`, `device.type` for web sessions
- [ ] SessionTimeline does not query `network.carrier.name` for web sessions
- [ ] SessionTimeline shows "Chrome 120 · macOS" style label for web sessions
- [ ] App Vitals shows browser filter when platform = web

### Category B — Web Vitals Screen
- [ ] `/projects/:projectId/web-vitals` route accessible
- [ ] Score cards for all 6 vitals (LCP, CLS, INP, TTFB, FCP, FID)
- [ ] Rating badges (good/needs-improvement/poor) correct per threshold
- [ ] Trend chart shows p75 over time
- [ ] Top pages table shows LCP breakdown by `url.path`
- [ ] Browser breakdown chart rendered

### Category C — Session Replay
- [ ] Web sessions open `RrwebReplayPlayer`, mobile sessions open `ScreenshotReplayPlayer`
- [ ] rrweb chunks fetched, sorted by `replay.chunk_index`, decoded correctly
- [ ] DOM replay plays back in `rrweb.Replayer`
- [ ] Replay controls (play/pause/seek/speed) work

### Category D — Click Heatmap
- [ ] Click coordinates rendered on heatmap canvas using nx/ny
- [ ] Rage clicks shown as distinct markers
- [ ] Filterable by `url.path` (screen)

### General
- [ ] `PlatformDonutChart` renders web icon correctly in Interactions screen
- [ ] Platform filter dropdown includes `web` when web data exists
- [ ] No existing Android/iOS views broken by the changes
