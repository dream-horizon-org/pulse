# Regression spotchecks (tie to shipped history)

Cross-reference: `pulse-mcp/doc/task 1/`:

- **`mcp-tools-failures.md`** — archived reasons (query builder removal; anomaly tools removed; **`metrics.ts`** aligned to **`POST /v1/interactions/performance-metric/distribution`** rather than stray **`/v3/metric`** paths; sessions listing **`POST /v1/sessions/listing`** instead of **`GET /v1/session-replays`**; **`get_alert_metrics`** requires **`scope`**; **`get_heatmap_data`** may return **403** when SDK heatmap/sampling not enabled).

Run these **explicitly before** declaring “MCP evaluates green”:

| Ref | Probe | Regression signal |
|-----|-------|-------------------|
| R1 | **`get_interaction_apdex_score`** baseline | Must not **404** to legacy **`/v3/metric/*`**; confirms distribution route |
| R2 | **`list_session_replays`** smoke | Must not simulate removed **`GET /v1/session-replays`** semantics |
| R3 | **`get_alert_metrics`** | Scope always provided; unintended bare call surfaces **400** |
| R4 | **`get_heatmap_data`** then **`get_active_sdk_config`** | **403** heatmap aligns with sampling/feature flags narrative |
| R5 | `tools/list` | Removed domains stay removed (**query builder**, **universal SQL**, **`anomaly`**) |

When a regression fires: cite **`mcp-tools-failures.md`** section + git SHA fixed last known good.

