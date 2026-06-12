# Spark Funnel Drop-off Implementation — Checkpoint

Tracking the Spark-side mirror of the ClickHouse drop-off correlation work
(Phases 14 + 15). Resume from the next unchecked item if context runs out.

## Context (don't re-discover)

- **CH compute path is already complete** with windowFunnel alignment and
  attribution precompute. See `FUNNEL_DROPOFF_PROGRESS.md`.
- **Spark reads from S3 parquet**, NOT from ClickHouse. Bucket:
  `pulse-otel-ingestion`. Folder layout: `{projectId}/{year}/{month}/{day}/*.parquet`.
- Kafka consumer writes logs/metrics/traces to S3 in this layout.
- Spark writes results back to ClickHouse via the existing `ClickHouseClient`.

## Goals (match CH behaviour exactly)

1. ✅ DONE on CH side — for reference: `windowFunnel` semantics, single-anchor
   chain, per-session bridge for both modes, cross-session user_state for
   UNIQUE_USERS, precomputed attribution table, two-tier DAO read with live
   fallback, cascading delete.
2. ⏳ Spark `FunnelComputeJob` currently emits its OWN bridge + user rollup
   (chain-walk semantics). Replace with windowFunnel-aligned single-anchor.
3. ⏳ Spark needs to also emit `funnel_dropoff_attribution` rows joining S3
   parquet of `stack_trace_events` / `otel_traces` / `session_summary`.
4. ⏳ All inserts share one `RunTime` stamp (already true in CH; preserve in Spark).
5. ⏳ Tests + docs.

## Implementation plan (work top to bottom; tick as you go)

### Phase A — explore existing Spark code ✅ DONE

### Phase B — replace per-session chain with windowFunnel-equivalent ✅ DONE
- Bridge `attempts` CTE now uses `groupBy(identity).agg(min(ts))` for single
  anchor matching `windowFunnel`. Confirmed in `computePerSessionBridge`.

### Phase C — cross-session UNIQUE_USERS user_state ✅ DONE
- New `computeCrossSessionUserState` method runs an independent chain on
  `user_id` (not derived from session_state).
- Uses `min_by(sid, ts)` Spark SQL aggregate (Spark 3.0+) to track the
  session that contributed each matched step's first match.
- Canonical session = sid of deepest matched step (via `row_number` over
  `step_idx desc, ts_at_step desc` window).
- `SessionAttempts` = `countDistinct(session_id)` across funnel events
  per user.
- Dimensions hydrated from raw at `(canonical_sid, ts_at_step, user_id)`.
- Per-session bridge still written for both modes (x-ray drill-in).

### Phase D — attribution precompute ✅ DONE
- `FunnelAttributionRow` model added.
- `ClickHouseClient.insertFunnelDropoffAttribution` added with 5k-row
  chunking via `bulkInsert`. Formats `ExampleSessions` as CH `Array(String)`.
- `emitAttribution` method joins cohorts against S3-loaded
  `otel_logs` (crash/anr/non_fatal) and `otel_traces` (http_5xx/4xx).
- `frozen_frame` is DEFERRED — CH derives it via `session_summary` MV which
  isn't S3-archived. DAO falls back to live join for runs where Spark's
  precomputed rows don't cover frozen_frame.
- PValue stubbed to 0.0 (matches CH).
- Skips when `stepCount < 2` or unordered.

### Phase E — shared RunTime + ordering ✅ DONE
- `runTime` is a method parameter threaded through all four inserts
  (`insertFunnelResults`, `insertFunnelSessionState`,
  `insertFunnelUserState`, `insertFunnelDropoffAttribution`).
- Attribution wired AFTER bridge inserts inside `emitBridgeAndRollup`
  with try/catch — failure here doesn't fail the bridge work (best-effort).

### Phase F — S3 read helpers ✅ DONE
- `loadOtelSignalParquet(spark, projectId, tableName, runTime, funnel)`
  iterates daily partitions over the run window using the s3-archiver
  layout: `s3a://pulse-otel-ingestion/{projectId}/{tableName}/year=YYYY/month=MM/day=DD/`.
- Reads `mergeSchema=true` so older partitions with schema drift load.
- Bucket overridable via `pulse.s3.otelBucket` system property; defaults to
  the known bucket name. Per-day failures don't fail the whole load.

### Phase G — cascading delete ✅ DONE (no Spark change needed)
- Verified: Spark doesn't issue DELETEs. The CH-side
  `ClickHouseComputeService.deleteFunnelResults` cascade I shipped earlier
  already cleans `funnel_results` + 3 bridge/attribution tables. Spark
  produces data, CH retains and cleans it.

### Phase H — tests ⚠️ DEFERRED
- Spark module has no JUnit / test infrastructure in its `pom.xml`. Adding
  it is its own task (needs spark-testing-base or equivalent). Documented
  as a follow-up in `FUNNEL_DROPOFF_PROGRESS.md`.

### Phase I — docs ✅ DONE
- `FUNNEL_DROPOFF_PROGRESS.md` — status bumped to Phase 16; new Spark
  inventory subsection; follow-ups list updated (Spark caveat replaced
  with frozen_frame-on-Spark + test-infra items).
- `docs/funnel-dropoff-correlation.md` — §3 solution overview now
  describes dual-engine populate. §6 Step 8 footnote describes shared
  RunTime threading on Spark and the cause-coverage matrix (CH vs Spark).
- HTML regenerated (54.2 KB).

## Files I expect to touch

| File | Action |
|---|---|
| `backend/spark/.../FunnelComputeJob.java` | rewrite bridge/rollup + add attribution |
| `backend/spark/.../ClickHouseClient.java` | add `insertFunnelDropoffAttribution` |
| `backend/spark/.../model/FunnelSessionState.java` | likely no change |
| `backend/spark/.../model/FunnelUserState.java` | maybe new fields if any drift |
| `backend/spark/.../model/FunnelAttributionRow.java` | NEW |
| `backend/spark/src/test/.../FunnelComputeJobTest.java` | new tests |
| `FUNNEL_DROPOFF_PROGRESS.md` | status + file inventory |
| `docs/funnel-dropoff-correlation.md` | drop Spark caveat |
| `docs/funnel-dropoff-correlation.html` | regenerate |

## Important constraints

- **Spark doesn't have ClickHouse's `windowFunnel` function.** We emulate
  via DataFrame ops: per uid, sort events by ts, walk through step sequence
  picking first match, anchored at first step-0.
- **`argMinIf` equivalent in Spark**: use `min_by(sid, ts)` (Spark SQL has it
  since 3.0) or a `Window` partition + `first(...)` ordered ascending. Pick
  whichever the existing FunnelComputeJob already uses for consistency.
- **`uniqExact` in CH** = `countDistinct` in Spark.
- **`groupArraySample(50)` in CH** = `slice(collect_set(...), 1, 50)` or
  custom UDF. Spark doesn't have native reservoir sampling on arrays at
  aggregate time; just take any 50.
- **Cross-shard semantics**: not relevant for Spark.

## Resume notes

If you resume mid-implementation, do these in order:
1. Read this file fully.
2. `grep -rn "buildAttributionInsertSql" backend/server` to refresh the
   reference SQL shape.
3. `Read` the relevant Spark file from the next unchecked item.
4. Cross-check against the corresponding CH method in
   `ClickHouseFunnelComputeDao.java`.
5. Update this file's checkboxes as you finish each item.
