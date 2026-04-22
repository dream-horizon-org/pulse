# Funnel Drop-off Correlation — Implementation Progress

Status: **All 13 phases complete**. Both `UNIQUE_USERS` and `SESSIONS` modes
supported across both analytics engines (Spark + ClickHouse).

## Goal

Bridge the Product/Engineering gap by correlating each funnel step's drop-off
with the underlying OTel signals (crash / ANR / non-fatal / HTTP 4xx/5xx /
frozen frames) and surfacing a ranked cause list with per-session evidence.

## Design recap

- **Bridge (always per-session):** `otel.funnel_session_state` — one row per
  `(funnel × session)` with `LastReachedStep`, `DropoffStep`, `LastReachedAt`,
  `TraceIdAtDropoff`, and dimension carryover (`AppVersion`, `Platform`, `OsName`,
  `DeviceModel`, etc.).
- **User rollup (UNIQUE_USERS only):** `otel.funnel_user_state` — derived from
  bridge; picks a **canonical session** per user (furthest step, latest wins
  ties) so OTel attribution stays anchored on one concrete moment.
- **Attribution:** queried live — the DAO joins the bridge (or user rollup)
  against `stack_trace_events` / `otel_traces` / `session_summary` within a
  30s-before / 60s-after window around `LastReachedAt`, computing lift vs the
  converter cohort.
- **UI semantics:** cohort denominators follow the funnel's configured mode —
  users (UNIQUE_USERS) or sessions (SESSIONS). The bridge is always per-session
  under the hood; users see whatever unit their funnel was designed around.
- **Dual-engine parity:** funnels are computed by either Spark
  (`FunnelComputeJob`) or ClickHouse (`ClickHouseFunnelComputeDao`) depending
  on `AnalyticsEngineConfig` and routed via `RoutingAnalyticsBatchService`.
  Both engines emit the bridge + user rollup with a shared `RunTime` stamp so
  the drop-off DAO can join `funnel_results` with the bridge by `MAX(RunTime)`
  regardless of which engine produced the run.

## File inventory

### ClickHouse
- `deploy/clickhouse/init/funnel_dropoff_tables.sql` — 3 new tables
  (session_state, user_state, dropoff_attribution) with Replicated + Distributed
  engines, 7d→cold, 90d→DELETE TTL.

### Spark compute path
- `backend/spark/src/main/java/org/dreamhorizon/pulsespark/FunnelComputeJob.java`
  — now emits bridge + user rollup after `insertFunnelResults`.
- `backend/spark/src/main/java/org/dreamhorizon/pulsespark/ClickHouseClient.java`
  — new `insertFunnelSessionState` / `insertFunnelUserState` methods (5k-row
  chunked inserts).
- `backend/spark/src/main/java/org/dreamhorizon/pulsespark/model/FunnelSessionState.java`
  / `FunnelUserState.java` — records matching the CH row shapes.

### ClickHouse compute path (parity with Spark)
- `backend/server/.../service/analytics/ClickHouseFunnelComputeDao.java`
  — added `buildSessionStateInsertSql(def, runTime)` (per-session bridge,
  mirrors the chain walk but identity is always `SessionId`, hydrates
  dimensions via LEFT JOIN on `otel.otel_logs` at the exact event timestamp)
  and `buildUserStateInsertSql(def, runTime)` (per-user rollup that reads
  from the just-written `funnel_session_state` rows, skipped for SESSIONS
  mode). Overloaded `buildInsertSqlForDefinition` / `buildInsertSqlChain` /
  `buildInsertSqlUnordered` to accept a caller-supplied `RunTime` literal so
  all three inserts (`funnel_results`, `funnel_session_state`,
  `funnel_user_state`) share one stamp. Unordered funnels skip bridge
  emission (matches Spark).
- `backend/server/.../service/analytics/ClickHouseComputeService.java` —
  `computeFunnel` and `computeFunnelBatch` now chain
  `funnel_results → funnel_session_state → funnel_user_state` per funnel
  with a shared RunTime. Bridge insert failures are logged and swallowed so
  they never fail the primary funnel compute.

### Backend — DAO + query builder
- `dao/productAnalysis/funneldropoff/FunnelDropoffQueries.java` — SQL builder
  for `buildCausesSql` (CTE with droppers/converters/stack/http/frame) and
  `buildEvidenceSql`. Mode-switches between `funnel_session_state` and
  `funnel_user_state`.
- `dao/productAnalysis/funneldropoff/FunnelDropoffDao.java` — `queryCauses`
  and `queryEvidence`, both returning `Single<List<…>>`.
- `dao/productAnalysis/funneldropoff/models/FunnelDropoffCauseRow.java` /
  `FunnelDropoffEvidenceRow.java` — DAO row POJOs.

### Backend — service + mapper + DTOs
- `service/productAnalysis/funnel/FunnelDropoffService.java` (interface).
- `service/productAnalysis/funnel/impl/FunnelDropoffServiceImpl.java` —
  validates funnel + step range, resolves mode from `FunnelDefinitionRow`,
  delegates to DAO, assembles response.
- `service/productAnalysis/funnel/FunnelDropoffMapper.java` — DAO → DTO with
  CSV splitting and rounding.
- `resources/productAnalysis/funnel/models/FunnelDropoffCauseDto.java`,
  `FunnelDropoffResponse.java`, `FunnelDropoffEvidenceDto.java`,
  `FunnelDropoffEvidenceResponse.java`.

### Backend — REST + wiring
- `resources/productAnalysis/funnel/FunnelsController.java` — two new GET
  endpoints: `/v1/funnels/{id}/dropoffs/{stepIndex}` and the `/evidence`
  sub-resource.
- `module/InteractionModule.java` — Guice bindings for
  `FunnelDropoffDao` + `FunnelDropoffService`.
- `error/ServiceError.java` — added `FUNNEL_STEP_OUT_OF_RANGE (BE1014)` and
  `FUNNEL_DROPOFF_UNAVAILABLE (BE1015)`.

### Backend tests
- `dao/productAnalysis/funneldropoff/FunnelDropoffQueriesTest.java`
- `dao/productAnalysis/funneldropoff/FunnelDropoffDaoTest.java`
- `service/productAnalysis/funnel/FunnelDropoffMapperTest.java`
- `service/productAnalysis/funnel/impl/FunnelDropoffServiceImplTest.java`
- `service/analytics/ClickHouseFunnelComputeDaoTest.java` — added
  `BuildSessionStateInsertSql` and `BuildUserStateInsertSql` `@Nested`
  groups covering unordered/zero-step short-circuits, SessionId-always
  grouping, project/pulseType filter, dimension hydration join, shared
  RunTime plumbing, DropoffStep converter logic, and canonical-session
  argMax semantics.

### UI — hooks + service
- `pulse-ui/src/constants/Constants.ts` — `FUNNEL_DROPOFF`,
  `FUNNEL_DROPOFF_EVIDENCE` API route constants.
- `pulse-ui/src/services/funnels.service.ts` — `fetchFunnelDropoff`,
  `fetchFunnelDropoffEvidence`, response types.
- `pulse-ui/src/hooks/useFunnelDropoff/` and
  `pulse-ui/src/hooks/useFunnelDropoffEvidence/` — TanStack Query hooks,
  barrel-exported from `hooks/index.ts`.

### UI — panel component
- `pulse-ui/src/components/DropoffPanel/DropoffPanel.tsx` — Mantine Drawer
  with KPI row, ranked cause list, empty state.
- `pulse-ui/src/components/DropoffPanel/components/CauseRow.tsx` — collapsible
  row with lift/drop-off-rate chips, toggles evidence on expand.
- `pulse-ui/src/components/DropoffPanel/components/EvidenceLinks.tsx` —
  per-session replay + trace deep links.
- `pulse-ui/src/components/DropoffPanel/DropoffPanel.{module.css,interface.ts,constants.ts}`.

### UI — funnel wiring
- `pulse-ui/src/screens/FunnelJourneyCreate/components/FunnelVisualization.tsx`
  — new `onStepDropoffClick(stepIndex)` prop; drop-off segment is now a
  clickable button with keyboard handler.
- `pulse-ui/src/screens/FunnelJourneyDetail/FunnelDetail.tsx` — opens the
  DropoffPanel on click, manages the selected step in local state.

### UI tests
- `pulse-ui/src/hooks/useFunnelDropoff/__tests__/useFunnelDropoff.test.ts` —
  enable-gating + fetch delegation.
- `pulse-ui/src/components/DropoffPanel/__tests__/DropoffPanel.test.tsx` —
  closed-state smoke test, cause-list render, empty-state copy.

## Known follow-ups (not in scope for this PR)

- Populate the precomputed `funnel_dropoff_attribution` table from the compute
  jobs (today the DAO queries the raw OTel tables on demand). Schema exists.
- Emit real `TraceIdAtDropoff` / `GeoCountry` from the Spark vector-log parquet
  — currently stored as empty strings. The CH compute path already hydrates
  both from `otel.otel_logs` materialized columns.
- Extend bridge emission to unordered funnels (currently skipped in both Spark
  and the CH compute path — "furthest step" is undefined for unordered).
- Add more cause kinds (slow-render, network-timeout breakdown by op).
