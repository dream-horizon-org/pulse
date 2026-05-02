# Contract parity — Network (web vs Android)

## Aligned

| Topic | Android | Web |
|-------|---------|-----|
| Signal | Trace span; `pulse.type` = `network.<statusCode>` (e.g. `network.200`, `network.0`) | Same (`networkPulseType` in `network-http.ts`) |
| Stable HTTP attrs | `http.request.method`, `url.full`, `http.response.status_code`, `server.address`, optional sizes | Same |
| Peer mapping | `peerServiceMap` host → `peer.service` | `instrumentations.network.peerServiceMap` |
| Header allowlists | Captured request/response headers | Same keys (`http.request.header.*`, `http.response.header.*`) |
| Errors | 4xx/5xx / transport errors → span error + `error.type` classification | Same intent (`error.type`: `4xx`/`5xx`/`network_error`/`cors_error`) |

## Web-only

| Topic | Notes |
|-------|-------|
| **URL sanitisation** | Strip query from `url.full` by default (`captureQueryParams`) — Android doc notes URLs without browser query surface. |
| **GraphQL attrs** | Optional `graphql.operation.*` when body parseable (limited on web; see PLAN-B deferrals). |
| **`error.type` values** | Web uses class strings: `"4xx"`, `"5xx"`, `"network_error"`, `"cors_error"`. Android uses span status only, not this attribute. OTel spec expects specific code strings (`"404"`) — Pulse deviates intentionally; ClickHouse queries use the class grouping. |
| **`http.duration`** | Convenience span attribute (ms, from `PerformanceResourceTiming`). Not the OTel `http.client.request.duration` histogram metric — those are separate instruments. See PLAN-C §P3. |

## OTel spec gaps (vs https://opentelemetry.io/docs/specs/semconv/http/http-spans/)

| Attribute | OTel requirement | Status |
|-----------|-----------------|--------|
| `server.port` | Required | Pulse skips default ports (80/443). `URL.port` is `""` for defaults so unavoidable without hardcoding; verify base OTel lib covers it. See PLAN-C §P2.4. |
| `url.full` credentials | Must not contain `user:pass@` | `sanitizeHttpUrl` strips query only — username/password not cleared. Fix: `u.username = ""; u.password = ""`. See PLAN-C §P1.2. |
| `network.protocol.version` | Recommended | Not set. Available via `PerformanceResourceTiming.nextHopProtocol` (`"h2"` → `"2"`). See PLAN-C §P2.3. |
| `network.peer.address` | Recommended | Not available from browser Fetch/XHR API. Accepted gap. |
| `http.client.request.duration` | Stable metric, Required | Not emitted. Deferred — opt-in flag `emitRequestDurationMetric` in config when implemented. See PLAN-C §P3.5. |
