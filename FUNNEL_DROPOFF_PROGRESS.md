# Funnel Drop-off Correlation — Implementation Progress

Status: **Phases 1–13 complete + windowFunnel alignment (Phase 14) +
attribution precompute (Phase 15) shipped on the ClickHouse compute path.**
Bridge tables now match the `windowFunnel`-based `funnel_results` cohort
sizes exactly for both `SESSIONS` and `UNIQUE_USERS` modes, and the
side-panel reads ranked causes from the precomputed
`funnel_dropoff_attribution` table — no live OTel joins at query time
unless precomputed rows are absent for a given run. Spark compute path
retains the original chain-walk semantics and live-join read path;
aligning Spark is the next phase.

## Goal

Bridge the Product/Engineering gap by correlating each funnel step's drop-off
with the underlying OTel signals (crash / ANR / non-fatal / HTTP 4xx/5xx /
frozen frames) and surfacing a ranked cause list with per-session evidence.

## Design recap

- **`funnel_results` algorithm (CH):** `windowFunnel` — single-anchor chain
  walk grouped by `SessionId` (SESSIONS mode) or `AppInstallationId`
  (UNIQUE_USERS mode), anchored on the first step-0 event per group. Replaces
  the legacy multi-attempt chain walker for performance.
- **Per-session bridge:** `otel.funnel_session_state` — one row per
  `(funnel × session)` for every ordered funnel regardless of mode.
  Single-anchor chain matching `windowFunnel` semantics. Carries
  `LastReachedStep`, `DropoffStep`, `LastReachedAt`, `TraceIdAtDropoff`, and
  dimension carryover. For SESSIONS funnels it's the cohort source. For
  UNIQUE_USERS funnels it powers the x-ray drill-in (per-session view of one
  user's funnel attempts) and single-session debug view — the cohort comes
  from `funnel_user_state` instead.
- **Per-user state (UNIQUE_USERS only):** `otel.funnel_user_state` —
  computed independently from `otel.otel_logs` via a cross-session
  `windowFunnel`-equivalent chain grouped by `AppInstallationId`. Each step's
  matched session is tracked via `argMinIf(sid, ts, condition)`;
  `CanonicalSessionId` = `sid` of the deepest matched step. Cohort numbers
  match `funnel_results.UserCount` exactly, including cross-session
  conversions that a per-session view can't capture.
- **Attribution (precomputed):** `otel.funnel_dropoff_attribution` —
  populated by the CH funnel compute via `buildAttributionInsertSql`. One
  row per `(FunnelId, RunTime, StepIndex, CauseKind, CauseKey)` with
  cohort sizes, affected counts, lift, and a capped 50-element
  `ExampleSessions` array. Reads from the mode-appropriate bridge
  (`funnel_session_state` for SESSIONS, `funnel_user_state` for
  UNIQUE_USERS), joins `stack_trace_events` / `otel_traces` /
  `session_summary` within a 30s-before / 60s-after window around
  `LastReachedAt`, and emits ranked rows. Drop-off DAO reads from this
  table first; falls back to the live join (`buildCausesSql`) if no
  precomputed rows exist for the requested run.
- **UI semantics:** cohort denominators follow the funnel's configured mode —
  users (UNIQUE_USERS) or sessions (SESSIONS). The drop-off DAO mode-switches
  the `FROM` table; cause join SQL is identical for both modes.
- **Shared RunTime contract:** `funnel_results` insert, both bridge
  inserts, AND the attribution insert all share one `RunTime` literal,
  threaded through `ClickHouseFunnelComputeDao.newRunTimeLiteral()` →
  `buildInsertSqlForDefinition(def, runTime)` →
  `buildInsertSqlWindowFunnel(def, runTime)` /
  `buildInsertSqlUnordered(def, runTime)`, plus
  `buildSessionStateInsertSql`, `buildUserStateInsertSql`, and
  `buildAttributionInsertSql`, so the drop-off DAO's `MAX(RunTime)`
  lookup returns rows from one consistent run across all four tables.
- **Cascading delete:** `ClickHouseComputeService.deleteFunnelResults` fans
  out to `funnel_results` + `funnel_session_state` + `funnel_user_state` +
  `funnel_dropoff_attribution`. Bridge cleanup is best-effort — primary
  delete succeeds even if a bridge cleanup fails.

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

### ClickHouse compute path (windowFunnel-aligned)
- `backend/server/.../service/analytics/ClickHouseFunnelComputeDao.java`
  — `buildSessionStateInsertSql(def, runTime)` writes per-session bridge
  rows for ALL ordered funnels (both modes). Uses single-anchor chain
  (`min(t0)` per session in `attempts` CTE) matching `windowFunnel`'s
  anchoring rule. Dimensions hydrated via LEFT JOIN on `otel.otel_logs` at
  `(SessionId, Timestamp)`. — `buildUserStateInsertSql(def, runTime)`
  rewritten to scan `otel.otel_logs` directly (no longer derived from
  session_state). Cross-session chain on `AppInstallationId`, tracking
  `SessionId` per step via `argMinIf(sid, ts, cond)`, canonical session =
  `sid` of the deepest matched step. `SessionAttempts` =
  `uniqExact(SessionId)`. Skipped for SESSIONS mode and unordered funnels.
  — `buildInsertSqlWindowFunnel(def, runTime)` overload added so the active
  ordered-funnel builder accepts a caller-supplied `RunTime` literal.
  `buildInsertSqlForDefinition(def, runTime)` now threads `runTime` into
  the windowFunnel branch (was previously dropping it on the floor — broke
  the shared-RunTime contract). All four inserts (`funnel_results`,
  `funnel_session_state`, `funnel_user_state`, `funnel_dropoff_attribution`)
  now actually share one stamp.
  — `buildAttributionInsertSql(def, runTime)` writes the precomputed
  `(StepIndex, CauseKind, CauseKey)` rows. Mode-switches the bridge table
  it reads (`funnel_session_state` for SESSIONS, `funnel_user_state` for
  UNIQUE_USERS). Per-step dropper cohorts via `dropper_cohorts` CTE,
  scalar converter cohort. Three cause branches: stack_trace_events
  (crash / anr / non_fatal), otel_traces (http_5xx / http_4xx),
  session_summary (frozen_frame). Lift computed inline with 999.0
  sentinel for zero-baseline cases. `groupArraySample(50)` caps the
  example sessions array. PValue stubbed to 0.0 — chi-square deferred.
  Skipped for unordered funnels and funnels with fewer than 2 steps.
- `backend/server/.../service/analytics/ClickHouseComputeService.java` —
  `computeOne(def)` chains `funnel_results → funnel_session_state →
  funnel_user_state → funnel_dropoff_attribution` per funnel with a
  shared RunTime via `emitDropoffBridge`. Any cascade failure is logged
  and swallowed (`onErrorReturn`) so the primary funnel compute never
  fails. Each insert may no-op via the executeInsert blank-SQL
  short-circuit (e.g. `funnel_user_state` for SESSIONS funnels;
  attribution for single-step funnels).
  — `deleteFunnelResults(projectId, funnelId)` cascades to all three
  bridge tables in addition to `funnel_results`. Bridge cleanup is
  best-effort.

### Backend — DAO + query builder
- `dao/productAnalysis/funneldropoff/FunnelDropoffQueries.java` —
  `buildCausesSqlFromAttribution(projectId, funnelId, stepIndex, runTime)`
  reads ranked causes directly from `funnel_dropoff_attribution` —
  indexed lookup, no OTel joins. `buildCausesSql` (the live OTel join
  retained as fallback) mode-switches between `funnel_session_state` and
  `funnel_user_state`. `buildEvidenceSql` unchanged.
- `dao/productAnalysis/funneldropoff/FunnelDropoffDao.java` — `queryCauses`
  now does a two-tier read: tries `queryCausesFromAttribution` (precomputed
  table) first; falls back to `queryCausesLive` (live OTel join) if the
  attribution side returns an empty list. `queryEvidence` unchanged. Both
  return `Single<List<…>>`.
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
- `dao/productAnalysis/funneldropoff/FunnelDropoffQueriesTest.java` —
  existing `BuildCausesSql` + `BuildEvidenceSql` groups unchanged. New
  `BuildCausesSqlFromAttribution` group asserts the precomputed read
  hits only `funnel_dropoff_attribution` (no OTel joins), maps
  `stepIndex` to the attribution table's `StepIndex = stepIndex + 1`
  convention, orders by lift DESC + caps at 50, converts the
  `Array(String)` example sessions back to CSV for the row mapper,
  and falls back to `MAX(RunTime)` when `runTime` is null.
- `dao/productAnalysis/funneldropoff/FunnelDropoffDaoTest.java` —
  `QueryCauses` group covers the new two-tier read:
  `shouldReturnMappedRowsFromPrecomputedAttribution` (precomputed hits),
  `shouldFallBackToLiveJoinWhenAttributionTableIsEmpty` (empty
  precomputed → live join takes over), and the empty/null edge cases.
- `service/productAnalysis/funnel/FunnelDropoffMapperTest.java`
- `service/productAnalysis/funnel/impl/FunnelDropoffServiceImplTest.java`
- `service/analytics/ClickHouseFunnelComputeDaoTest.java` —
  `BuildSessionStateInsertSql` `@Nested` group covers
  unordered/zero-step short-circuits, single-anchor `min(t0)` semantics
  matching `windowFunnel`, both-mode emission (per-session bridge written
  for SESSIONS and UNIQUE_USERS to power x-ray drill-in), project/pulseType
  filter, dimension hydration LEFT JOIN, and shared RunTime plumbing.
  `BuildUserStateInsertSql` `@Nested` group covers SESSIONS/unordered/zero-step
  short-circuits, direct read from `otel_logs` (not session_state),
  AppInstallationId grouping, single-anchor cross-session chain, sid
  tracking via `argMinIf` per step, distinct-session count for
  `SessionAttempts`, canonical session = sid of deepest matched step,
  cross-session converter detection (`t_{stepCount-1} IS NOT NULL` →
  `DropoffStep = -1`), dimension hydration via
  `(CanonicalSessionId, CanonicalLastReachedAt)` join. New
  `BuildAttributionInsertSql` `@Nested` group covers
  unordered/single-step/zero-step short-circuits, mode-switched bridge
  table reads (session_state vs user_state with canonical column
  rewriting for UNIQUE_USERS), dropper/converter DropoffStep filters,
  shared RunTime stamping, INNER JOINs against each cause source
  (stack_trace_events / otel_traces / session_summary), the 30s-before /
  60s-after attribution window, `groupArraySample(50)` cap on example
  sessions, lift sentinel for zero-baseline cases, PValue stub, and
  the three UNION ALL cause branches. New `BuildInsertSqlForDefinition`
  tests verify the `runTime` parameter is threaded into both ordered
  (windowFunnel) and unordered branches.
- `service/analytics/ClickHouseComputeServiceTest.java` —
  `deleteFunnelResults_cascadesAcrossResultsAndDropoffBridgeTables`
  asserts all four DELETE statements fire (results + 3 bridge tables).
  `deleteFunnelResults_swallowsBridgeFailureWhenPrimaryDeleteSucceeds`
  asserts best-effort semantics. New
  `computeFunnel_alsoEmitsAttributionPrecomputeForOrderedFunnels`
  verifies the `INSERT INTO otel.funnel_dropoff_attribution` statement
  fires as part of the cascade. New
  `computeFunnel_swallowsAttributionFailureWhenResultsSucceeded`
  asserts that an attribution insert error doesn't fail
  `computeFunnel` — the side-panel can fall back to live join.

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

- Align Spark compute path with the CH `windowFunnel` semantics AND the
  attribution precompute. Spark still uses the multi-attempt chain walker
  for `funnel_results`, the derived-from-session_state user_state, and has
  no `funnel_dropoff_attribution` writer. Cohort numbers from
  Spark-computed funnels won't match CH-computed funnels for the same
  definition, and Spark-routed runs always fall back to the live cause
  join. Will be done in the next phase.
- Extend bridge + attribution emission to unordered funnels (currently
  skipped — "furthest step" is undefined for unordered).
- Wire the x-ray drill-in UI: when a UNIQUE_USERS user is selected from
  the drop-off panel, query `funnel_session_state` filtered by
  `(FunnelId, RunTime, UserId)` to enumerate that user's per-session
  funnel attempts. The `idx_user_id` bloom filter on
  `funnel_session_state` keeps this cheap.
- Compute a real `PValue` (chi-square or Fisher's exact) in
  `buildAttributionInsertSql`. Currently emits `0.0` as a placeholder —
  lift filtering does most of the work but proper significance would let
  the UI tag "weak" vs "strong" signals more rigorously.
- Add more cause kinds (slow-render, network-timeout breakdown by op,
  rage-tap / dead-click from session replay events).
- Deterministic tiebreak on `argMinIf(sid, ts, ...)` in user_state when
  two sessions emit the same step at the exact same millisecond. Today
  picks one nondeterministically; could switch to
  `argMin(sid, (ts, sid))` for stable canonical-session selection across
  reruns. Extremely rare in practice.
