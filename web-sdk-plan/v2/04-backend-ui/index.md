# Phase 7 — Backend & UI

**Goal:** Make web SDK data visible in all existing Pulse dashboards, add rrweb-based session replay playback to the UI, and add web-specific remote config flags.

**Estimated duration:** Week 10–11
**Prerequisites:** Phase 2 (data flowing into ClickHouse with `Platform = 'web'`). Phase 3 (session replay data in ClickHouse). Can be parallelised with Phase 5/6.

---

## Scope

**In:**
- ClickHouse schema: verify `Platform = 'web'` is accepted
- OTEL Collector routing: add `web_vital` to routing rules if needed
- pulse-ui: platform filter includes `web` across all dashboards
- pulse-ui: rrweb session replay player component
- Remote config: web-specific feature flags
- CORS headers on ingest endpoints (may be a backend change)

**Out:**
- New web-specific dashboards (web vitals trends, SPA navigation funnels) — post-v1
- Web SDK config management UI — post-v1

---

## Deliverable

Engineers and PMs can view web session data in every existing Pulse dashboard (crashes, interactions, sessions). Web session replays are playable in the Pulse UI. Web projects can be configured via remote config.

---

## Implementation Steps

### 1. Verify ClickHouse Schema Compatibility

The existing schema (`otel.otel_traces`, `otel.otel_logs`, `otel.otel_metrics_gauge`) should already accept web data. Verify:

```sql
-- After first web spans land, run:
SELECT Platform, count() as count
FROM otel.otel_traces
GROUP BY Platform;

-- Expected: Android, iOS, web all present

-- Verify new web-specific attributes are stored
SELECT arrayElement(SpanAttributes, indexOf(SpanAttributes.Key, 'browser.name')) as browser
FROM otel.otel_traces
WHERE Platform = 'web'
LIMIT 10;
```

If `Platform = 'web'` is filtered out by any existing `WHERE` clause or materialized view, fix those queries. This is expected to be non-breaking since ClickHouse stores all attributes in the `SpanAttributes` nested array.

---

### 2. OTEL Collector Routing (`backend/ingestion/otel-collector.yaml`)

Check if any routing rules need updating for `web_vital` (the new signal type for web):

```yaml
# Existing routing connector
connector/pulse_router:
  table:
    - statement: route() where attributes["pulse.type"] == "device.crash"
      pipelines: [logs/pulse_backend]
    - statement: route() where attributes["pulse.type"] == "device.anr"
      pipelines: [logs/pulse_backend]
    - statement: route() where attributes["pulse.type"] == "non_fatal"
      pipelines: [logs/pulse_backend]
    # web_vital goes to ClickHouse only (metrics, not incidents)
    # No change needed — default route handles it
```

`web_vital` does not need incident creation so no routing change is needed. Verify the default route sends it to ClickHouse.

---

### 3. CORS Headers (Backend)

The ingest server must allow browser requests. Add CORS middleware to the Vert.x server for OTLP endpoints:

```java
// backend/server — add to OTLP ingestion routes
router.route("/v1/*").handler(CorsHandler.create()
    .addRelativeOrigin(".*")  // or restrict to known domains
    .allowedMethod(HttpMethod.POST)
    .allowedMethod(HttpMethod.OPTIONS)
    .allowedHeader("Content-Type")
    .allowedHeader("X-API-KEY")
);
```

This is the most critical backend change — without it, no browser data flows.

---

### 4. pulse-ui: Platform Filter

The `Platform` filter already exists in the UI. Verify `web` appears as an option when web data is present:

- Check `useGetInteractions` and similar hooks — confirm their query params pass `platform` correctly
- Check `useSessions` / session list queries — confirm `web` sessions appear
- Check crash list / ANR list — confirm they correctly show `Platform: web`

These should be zero-change if the backend queries use `Platform IN (...)` style filters. Audit and patch any hardcoded `Platform = 'android' OR Platform = 'ios'` conditions.

**Files to audit in pulse-ui:**
```
src/hooks/useSessions.ts
src/hooks/useCrashes.ts
src/services/sessionService.ts
src/components/PlatformFilter/
```

---

### 5. rrweb Session Replay Player (pulse-ui)

This is the most significant UI change in this phase.

**New component:** `src/components/SessionReplayWebPlayer/`

```typescript
// SessionReplayWebPlayer.tsx
import { Replayer } from 'rrweb';
import { useEffect, useRef } from 'react';

interface Props {
  sessionId: string;
  platform: 'web' | 'ios' | 'android';
}

export function SessionReplayWebPlayer({ sessionId, platform }: Props) {
  if (platform !== 'web') {
    // Fall back to existing screenshot-based player
    return <SessionReplayMobilePlayer sessionId={sessionId} />;
  }

  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // 1. Fetch session replay chunks from backend
    // GET /v1/sessions/{sessionId}/replay-chunks
    // Returns: sorted array of { chunk: string, timestamp: number }

    // 2. Decompress each chunk
    // 3. Feed to rrweb Replayer
    const events = chunks
      .flatMap(c => JSON.parse(ungzip(atob(c.chunk))));

    const replayer = new Replayer(events, {
      root: containerRef.current!,
      speed: 1,
      showController: true,
      blockClass: 'pulse-block',   // honour same privacy classes
    });

    replayer.play();
    return () => replayer.pause();
  }, [sessionId]);

  return <div ref={containerRef} className="replay-container" />;
}
```

**Backend endpoint needed:**

```
GET /v1/sessions/{sessionId}/replay-chunks?platform=web

Response: [
  { chunk_index: 0, compressed_data: "base64...", timestamp: 1712345678000 },
  { chunk_index: 1, compressed_data: "base64...", timestamp: 1712345683000 },
  ...
]
```

This queries ClickHouse for `pulse.type = 'session_replay'` logs matching the session ID, sorted by timestamp.

---

### 6. Remote Config: Web Feature Flags

Add web-specific flags to the SDK config schema so features can be toggled server-side without SDK update:

```json
{
  "web_errors": true,
  "web_network": true,
  "web_interactions_ui": true,
  "web_vitals": true,
  "web_navigation": true,
  "web_long_tasks": true,
  "web_session_replay": false,
  "web_session_replay_sample_rate": 0.1,
  "interaction": true
}
```

These mirror the existing mobile flags (`js_crash`, `network_instrumentation`, etc.) but namespaced for web. The web SDK reads these from the `/v1/configs/active` response.

---

## Testing Cycle

### Backend
- Send a test web span via curl: confirm it appears in ClickHouse with `Platform = 'web'`
- Send a web vital metric: confirm it appears in `otel_metrics_gauge`
- Confirm CORS preflight OPTIONS request returns correct headers
- Confirm session replay chunks queryable by session ID

### pulse-ui
- Load Pulse dashboard with a web project
- Verify `Platform: web` appears in all filter dropdowns
- Verify web sessions appear in session list
- Verify web crashes appear in crash list
- Open a web session → verify rrweb player loads and plays back the recording
- Toggle a web feature flag in remote config → verify web SDK respects it on next page load

---

## Done Criteria

- [ ] Web spans visible in ClickHouse (`Platform = 'web'` confirmed in query)
- [ ] CORS headers correct on all OTLP ingest endpoints
- [ ] `Platform: web` option appears in all Pulse UI filter dropdowns
- [ ] Web crashes/errors appear in the crashes list
- [ ] Web sessions appear in the sessions list
- [ ] Web interactions appear in the Interactions tab with APDEX
- [ ] rrweb session replay plays back correctly in Pulse UI
- [ ] Web feature flags respected by SDK via remote config

---

## Known Risks

- **rrweb Replayer in pulse-ui**: adds `rrweb` as a dependency to the dashboard. Lazy-load it (dynamic import) so it doesn't bloat the initial dashboard bundle.
- **Replay chunk ordering**: ClickHouse query for replay chunks must ORDER BY timestamp strictly. Out-of-order chunks cause rrweb Replayer errors.
- **Backend session replay endpoint**: this requires a new server endpoint. Coordinate with backend team early so it doesn't block the UI work.
- **CORS**: if the ingest endpoint sits behind an API gateway or load balancer, CORS headers may need to be set at the gateway level rather than the application level.
