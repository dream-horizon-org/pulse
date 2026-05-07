# Contract parity — Network (web vs Android)

## Aligned

| Topic | Android | Web |
|-------|---------|-----|
| Signal | Trace span; `pulse.type` = `network.<statusCode>` (e.g. `network.200`, `network.0`) | Same (`networkPulseType` in `network-http.ts`) |
| Stable HTTP attrs | `http.request.method`, `url.full`, `http.response.status_code`, `server.address`, optional sizes | Same |
| Peer mapping | `peerServiceMap` host → `peer.service` | `instrumentations.network.peerServiceMap` |
| Header allowlists | Captured request/response headers | Same keys (`http.request.header.*`, `http.response.header.*`) |
| Errors | 4xx/5xx / transport errors → span error + `error.type` classification | Same intent (`error.type`: `4xx`/`5xx`/`network_error`/`cors_error`) |

## React Native (semantic mapping)

| Topic | Notes |
|-------|-------|
| HTTP attribute keys | RN uses internal `ATTRIBUTE_KEYS` in `pulse-react-native-otel/src/network-interceptor/span-helpers.ts` — not necessarily the literal OTel string constants in source. |
| Parity with web | Align on semantics: `pulse.type` = `network.<statusCode>`, method, URL, status / error classification — not on grepping identical attribute key spellings in TS files. |

## Web-only

| Topic | Notes |
|-------|-------|
| **URL sanitisation** | Strip query from `url.full` by default (`captureQueryParams`) — Android doc notes URLs without browser query surface. |
| **GraphQL attrs** | Optional `graphql.operation.*` when body parseable (limited on web; see PLAN-B deferrals). |
| **`error.type` values** | Web uses class strings: `"4xx"`, `"5xx"`, `"network_error"`, `"cors_error"`. Android uses span status only, not this attribute. OTel spec expects specific HTTP code strings — Pulse deviates intentionally (PLAN-C §P1.1); ClickHouse uses class grouping. |
| **`http.duration`** | Convenience span attribute (ms, from `PerformanceResourceTiming`). Not the OTel `http.client.request.duration` histogram metric — metric deferred (PLAN-C §P3). |

## OTel alignment (PLAN-C)

| Attribute | OTel | Web implementation |
|-----------|------|---------------------|
| `url.full` (no credentials) | Required | `sanitizeHttpUrl` clears `username` / `password` on URL before stringifying. |
| `server.port` | Required | Set to explicit port or **443** / **80** when `URL.port` is empty for https/http. |
| `network.protocol.version` | Recommended | From `PerformanceResourceTiming.nextHopProtocol` when available (`h2` → `2`, etc.). |
| `network.peer.address` | Recommended | Not exposed by browser APIs — accepted gap. |
| `http.client.request.duration` (metric) | Stable histogram | **Deferred** — `emitRequestDurationMetric` reserved on config; no histogram wired yet. |
