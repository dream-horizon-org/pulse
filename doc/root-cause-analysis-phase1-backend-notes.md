# Root Cause Analysis – Phase 1 backend notes (schema & data)

Phase 1 is schema and data only; no Java or API in scope.

## otel_traces – RCA usage

- **Dimensions (materialized):** Platform, OsVersion, AppVersion, DeviceModel, NetworkProvider, GeoState. All present in `backend/ingestion/clickhouse-otel-schema.sql`.
- **Metrics:** StatusCode, Duration, Events.Name; SpanAttributes: `pulse.interaction.apdex_score`, `pulse.interaction.user_category`, `app.interaction.frozen_frame_count`, `app.interaction.slow_frame_count`, `app.interaction.analysed_frame_count` (and unanalysed for rates). Use existing `ClickhouseConstants` patterns where applicable.
- **Filter:** `PulseType = 'interaction'`, `SpanName = <interaction_name>`, `ProjectId = <project_id>`.

## Problematic count (error OR poor)

Used for “similar to total” segment selection. A span is either error or poor, so **union = sum**; distinct by SpanId is not required.

**Recommended expression:**

```sql
countIf(StatusCode = 'Error' OR ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor')
```

If you ever need distinct spans (e.g. if semantics change):  
`uniqExactIf(SpanId, StatusCode = 'Error' OR ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor')`.

## root_cause_cache table

- **DB:** `otel`, table `root_cause_cache`.
- **Columns:** project_id (LowCardinality(String)), interaction_name (String), date (Date), mode (LowCardinality: 'hierarchical'|'flat'), baseline (String, JSON), segments (String, JSON), cached_at (DateTime64(3, 'UTC')).
- **ORDER BY:** (project_id, interaction_name, date) — one row per interaction per date.
- **Engine:** ReplacingMergeTree(cached_at); latest cached_at wins on merge.
- **TTL:** No table-level TTL; API should enforce expiry (e.g. serve from cache only if cached_at within last 24h).

## Schema difference from plan

- Plan referenced **tenant_id** and project_id for cache. **Main branch uses ProjectId only** for isolation; there is no tenant_id in this schema. Use **project_id** as the cache key (with interaction_name and date). Backend should resolve project_id from current context (e.g. request/project) and use it for cache read/write.
