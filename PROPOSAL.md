# ClickHouse Rollup Framework — Proposal

**Status:** draft
**Owner:** _TBD_
**Last updated:** 2026-05-19

---

## TL;DR

Pulse dashboards query raw ClickHouse tables (`otel_traces`, `otel_logs`, `stack_trace_events`) for every card. ClickHouse is fast at aggregates, but only when the input row count is small. Today every trend, every percentile, every error-rate card scans tens to hundreds of millions of rows and re-buckets them at read time. Cards are slow, timeouts are common, and the only knobs we have left are caching and bucket-size clamps.

This proposal introduces a **rollup framework**: a curated set of **AggregatingMergeTree materialized views** at fixed time grains (15s / 1m / 5m / 1h / 1d) that pre-compute the aggregates our cards already consume. Cards then read **tiny pre-aggregated tables** instead of raw event tables. We expect dashboard p95 to drop from seconds to milliseconds.

The framework is built around four pieces:

1. **A YAML registry** — one source of truth describing every rollup (source table, dimensions, metrics, grains, filters). Reviewed via PR.
2. **A Java records model** — the registry is parsed into typed records at server boot, used by the migration tool and the read API.
3. **A migration utility** — applies registry changes to ClickHouse (creates/alters MVs and target tables). Run on deploy. Not an ORM. Not a DDL codegen we hand-roll. The YAML *is* the spec; the tool just reconciles ClickHouse to it.
4. **A generic reader** — a single Java service that takes `(rollup name, time range, columns, filters)` and returns rows. Existing per-domain DAOs/DTOs stay; only their SQL changes to point at rollups via the generic reader.

No UI changes. No new card system. No declarative dashboards. Backend-only refactor that changes where cards read from.

---

## Why now

### What dashboards do today

```
┌─────────────────────────┐        every card render
│  pulse-ui (46 screens)  │ ──────────────────────────────────┐
└─────────────────────────┘                                   │
                                                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│  backend/server  ~30 Queries.java DAOs                               │
│                                                                      │
│   WebVitalsQueries   NetworkQueries   InteractionQueries   …         │
│        │                  │                   │                      │
│        ▼                  ▼                   ▼                      │
│   raw SQL with toStartOfInterval(Timestamp, INTERVAL N MINUTE)       │
│   GROUP BY bucket, dims … computed at read time                      │
└──────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│  ClickHouse                                                          │
│    otel_traces       (raw, MergeTree, partition by day)              │
│    otel_logs         (raw, MergeTree, partition by day)              │
│    stack_trace_events(raw, MergeTree, partition by day)              │
│                                                                      │
│    ┌── few existing rollups ──────────────────────────────────┐      │
│    │  interaction_heatmaps_daily   (Summing,    daily)        │      │
│    │  session_summary              (Aggregating, per session) │      │
│    │  project_monthly_usage        (Aggregating, monthly)     │      │
│    └──────────────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────────────────┘
```

A 7-day Web Vitals trend with 30-minute buckets touches every `otel_logs` row in 7 days for that project, just to produce 336 buckets. Same story for network error rate, interaction p95, crash rate. Every card. Every render.

### What's already pre-aggregated, and the gap

| Already aggregated                  | Notes                                                      |
| ----------------------------------- | ---------------------------------------------------------- |
| `interaction_heatmaps_daily`        | Daily only. Locked to one dim set.                         |
| `session_summary`                   | Per-session, not time-bucketed.                            |
| `project_monthly_usage`             | Monthly billing only.                                      |
| `funnel_results`, `journey_results` | Spark batch, daily.                                        |

**The gap:** there is no time-bucketed rollup for the high-traffic dashboards (web vitals trends, network errors, interactions, crashes). Read-time bucketing on raw tables is what's slow.

---

## The aim

Build a framework so that:

- Every recurring dashboard metric has a pre-aggregated home at the right grain.
- Adding a new card means adding a few rows to a YAML file and running a migration — not writing fresh SQL against raw tables.
- Adding a new metric to an existing dim set is an `ALTER ADD COLUMN` plus a backfill. Cheap.
- Read APIs become "give me these columns from this view for this time range" — the generic reader handles it.
- The hard thinking lives in the YAML (what to materialize) and in the MV's `SELECT … State(…)` expressions. The Java/Java SQL surface stays small.

```
┌────────────────────────────────────────────────────────────────────────┐
│  metrics/*.yaml   (registry, source of truth, PR-reviewed)             │
│                                                                        │
│   - rollup: traces_screen_5m                                           │
│     source: otel_traces                                                │
│     filter: PulseType = 'interaction'                                  │
│     grain:  5m                                                         │
│     dims:   [ProjectId, ScreenName, AppVersion, Platform, Country]     │
│     metrics:                                                           │
│       - name: duration_p95                                             │
│         expr: quantileExactState(0.95)(Duration / 1e6)                 │
│         # tdigest alt: quantileTDigestState(0.95)(Duration/1e6)        │
│         #   ~1% error, mergeable, ~10x cheaper memory; switch          │
│         #   if exact becomes a hot path                                │
│       - name: error_count                                              │
│         expr: sumState(toUInt64(StatusCode = 'Error'))                 │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │ parsed at boot
                           ▼
┌────────────────────────────────────────────────────────────────────────┐
│  RollupRegistry (Java records)                                         │
│   record Rollup(name, source, filter, grain, dims, metrics)            │
│   record Metric(name, expr, finalExpr)                                 │
└────────┬─────────────────────────────────────┬─────────────────────────┘
         │                                     │
         ▼                                     ▼
┌─────────────────────────┐         ┌───────────────────────────────────┐
│  Migration tool         │         │  GenericRollupReader              │
│  (run on deploy)        │         │   query(rollup, timeRange,        │
│                         │         │         columns, dims, filters)   │
│  Reconciles ClickHouse  │         │                                   │
│  to registry:           │         │  Builds SQL:                      │
│   - create target table │         │    SELECT dims, finalize(metric)  │
│   - create MV           │         │    FROM <rollup>                  │
│   - ALTER ADD COLUMN    │         │    WHERE bucket BETWEEN …         │
│   - flag breaking diffs │         │      AND ProjectId = …            │
│   - print backfill SQL  │         │    GROUP BY dims, bucket          │
└─────────────────────────┘         └─────────────────┬─────────────────┘
                                                      │
                                                      ▼
                            ┌─────────────────────────────────────────────┐
                            │  Existing per-domain DAOs / DTOs            │
                            │   WebVitalsDao   →   GenericRollupReader    │
                            │   NetworkDao     →   GenericRollupReader    │
                            │   InteractionDao →   GenericRollupReader    │
                            │                                             │
                            │  DTOs unchanged. Mapping unchanged.         │
                            │  Only the SQL source changes.               │
                            └─────────────────────────────────────────────┘
```

---

## How a request flows (after rollout)

```
GET /web-vitals/trend?project=X&from=…&to=…&bucket=30m

  WebVitalsResource
        │
        ▼
  WebVitalsService                ← business rules unchanged
        │
        ▼
  WebVitalsDao
        │
        │   reader.query(Rollup.LOGS_WEB_VITALS_5M,
        │                timeRange, ["lcp_p75","fid_p95","cls_p75"],
        │                groupBy=["ScreenName","AppVersion"],
        │                bucket="30m")
        ▼
  GenericRollupReader
        │   resolves grain (5m ≤ 30m), composes SQL,
        │   runs on the pre-aggregated MV
        ▼
  ClickHouse
        │   reads ~thousands of rows from a 5-minute rollup
        │   instead of millions of raw rows from otel_logs
        ▼
  rows → DTO mapper → JSON
```

The card payload shape stays identical. The hot path no longer touches `otel_logs`.

---

## Design choices and rationale

### 1. Right path on storage layout (not the cheap path)

We materialize at five grains: **15s, 1m, 5m, 1h, 1d**. Why:

- 15s and 1m support live/real-time cards without scanning raw.
- 5m is the workhorse for "last hour to last day" trends.
- 1h covers week views.
- 1d covers month/quarter views and most listing pages.
- Reader picks the **coarsest grain whose bucket size is ≤ requested bucket and whose retention covers the window**.

Storage cost scales with **dim cardinality × number of grains**, not with raw row count. For low-cardinality dim sets (Project × Screen × AppVersion × Platform × Country), even 15s rollups are tiny next to raw `otel_traces`.

Per-grain retention is one of the open clarifications.

### 2. `quantileExactState` as the default; t-digest documented as an escape hatch

```yaml
metrics:
  - name: duration_p95
    expr: quantileExactState(0.95)(Duration / 1e6)
    # tdigest alternative — uncomment if exact becomes hot:
    #   expr: quantileTDigestState(0.95)(Duration / 1e6)
    #   error: ~1%, mergeable, ~10x lower memory.
    #   Suitable when we care about trend shape, not absolute p95.
```

Exact is the right default for an observability product where engineers compare numbers across time and against alert thresholds. We accept the cost. T-digest stays one comment-flip away if a specific high-cardinality rollup becomes a memory hot spot.

### 3. Java records, YAML for management

The registry **lives in YAML**. The Java side parses it into immutable records at boot. There is no annotation-driven ORM, no Hibernate, no "discover schema from classes". The migration tool reads YAML → records → diffs ClickHouse → applies DDL.

```
metrics/
  traces_screen.yaml         # all rollups sourced from otel_traces, by screen
  logs_web_vital.yaml
  stack_trace_events.yaml
  …
```

This keeps the source of truth reviewable in Git, lets product/platform managers see what's pre-computed without reading Java, and avoids a runtime "make-me-a-metric" surface (out of scope).

### 4. Migration utility, not DDL codegen we maintain by hand

A small CLI/cron entry point in `backend/`:

```
$ ./mvnw -pl clickhouse-migrate exec:java -Dargs="diff"
[diff]
  + CREATE TABLE rollup_traces_screen_5m  (…)
  + CREATE MATERIALIZED VIEW mv_traces_screen_5m TO rollup_traces_screen_5m AS …
  ~ ALTER TABLE rollup_logs_web_vital_5m ADD COLUMN ttfb_p95 AggregateFunction(quantileExact(0.95), Float64)
  ! BREAKING: rollup_logs_web_vital_5m dim set changed (added Country) → manual cutover required

$ ./mvnw … -Dargs="apply"
```

- Additive changes (new metric column, new rollup) → applied automatically.
- Breaking changes (dim set change, grain change) → tool refuses, prints a runbook stub. Manual cutover (new table, dual-write window, switchover) is fine; it is rare.
- **Backfill is not the tool's job.** It prints the backfill SQL for the operator to run with appropriate batching. Backfill **will be required** any time a new rollup or new metric column is introduced — see "Backfill" section below.

### 5. No DDL codegen in app code; no per-card DTO regen

We do not generate Java DTOs from DDL. We do not generate DDL from Java. The flow is:

```
YAML  ──parsed──►  Java records  ──read by──►  migration tool ──emits──► ClickHouse DDL
                                  ──read by──►  generic reader ──emits──► SELECT SQL
```

DTOs stay hand-written exactly as they are today. The DAO calls `GenericRollupReader.query(...)` instead of running its own raw-table SQL. The mapper from row → DTO is unchanged.

### 6. APIs ask for columns from a view; the reader is generic

Every per-card SQL string is replaced by:

```java
List<Map<String, Object>> rows = reader.query(
    RollupRef.TRACES_SCREEN_5M,
    timeRange,
    columns:  List.of("duration_p95", "error_rate"),
    groupBy:  List.of("ScreenName", "AppVersion"),
    filters:  Map.of("Platform", "ios"),
    bucket:   Duration.ofMinutes(30)
);
```

The reader composes:

```sql
SELECT
  toStartOfInterval(bucket, INTERVAL 30 MINUTE) AS bucket,
  ScreenName, AppVersion,
  quantileExactMerge(0.95)(duration_p95_state) AS duration_p95,
  sumMerge(error_count_state) / sumMerge(total_count_state) AS error_rate
FROM rollup_traces_screen_5m
WHERE bucket BETWEEN ? AND ?
  AND ProjectId = ?
  AND Platform = 'ios'
GROUP BY bucket, ScreenName, AppVersion
ORDER BY bucket
SETTINGS use_query_cache = true, query_cache_ttl = …;
```

The DAO no longer owns SQL. The DAO owns:

- Which rollup to read
- Which columns
- Filter validation against the registry
- Mapping rows → existing DTOs

Most of the *business logic* is in the MV's `SELECT … State(…)` clause — it's defined once when the rollup is registered, in the YAML.

---

## What changes, what doesn't

### Stays the same

- All UI screens and components.
- All REST endpoints and their request/response shapes.
- All DTOs and MapStruct mappings.
- Raw tables (`otel_traces`, `otel_logs`, `stack_trace_events`) — untouched.
- Existing rollups (`session_summary`, `interaction_heatmaps_daily`, `project_monthly_usage`) — left in place; can be migrated into the framework later.

### Changes

- Each migrated DAO swaps its hand-written SQL for a `GenericRollupReader.query(...)` call.
- A new `metrics/` directory at server resources root holds YAML.
- A new `clickhouse-migrate` module hosts the migration tool.
- A new `RollupRegistry` (Java records) and `GenericRollupReader` (Vert.x + RxJava service) under `backend/server/`.
- ClickHouse gains rollup tables and MVs (read-side only; writes still go to raw).

### Out of scope

- Dynamic / user-defined metrics at runtime.
- Card config registry on the UI side.
- Replacing existing pre-aggregations (heatmap, session_summary).
- Migration strategy itself — handled separately. Backfill is required.

---

## Backfill

Each new rollup or new metric column requires a backfill from raw. The migration tool emits the backfill SQL; the operator runs it manually with batching, monitoring, and pacing — not the tool. A typical backfill is:

```sql
INSERT INTO rollup_traces_screen_5m
SELECT
  toStartOfInterval(Timestamp, INTERVAL 5 MINUTE) AS bucket,
  ProjectId, ScreenName, AppVersion, Platform, Country,
  quantileExactState(0.95)(Duration / 1e6) AS duration_p95_state,
  sumState(toUInt64(StatusCode = 'Error'))   AS error_count_state,
  sumState(toUInt64(1))                      AS total_count_state
FROM otel_traces
WHERE PulseType = 'interaction'
  AND Timestamp BETWEEN ? AND ?
GROUP BY bucket, ProjectId, ScreenName, AppVersion, Platform, Country;
```

Done in day-sized chunks per project. Detailed backfill runbook lives outside this proposal.

---

## Expected impact

| Card class                | Today (raw)               | After (rollup)            |
| ------------------------- | ------------------------- | ------------------------- |
| Web Vitals 24h trend      | ~10–50M rows scanned      | ~300 rows scanned         |
| Network errors 7d trend   | ~50–200M rows scanned     | ~2k rows scanned          |
| Interaction p95 24h       | ~20M rows scanned         | ~300 rows scanned         |
| Crash trend 30d           | ~5M rows scanned          | ~1k rows scanned          |

p95 latency goes from "seconds, sometimes timeouts" to "tens of milliseconds". The exact numbers will be measured during the proof-of-concept (web vitals trend) before we expand scope.

---

## Risks

| Risk                                                             | Mitigation                                                                                  |
| ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| MV insert amplification slows raw ingestion                      | Cap MVs per source. Measure ingest throughput in staging before each new rollup lands.      |
| Late-arriving mobile data (offline buffers) skews recent buckets | Reader treats the trailing window (size TBD) as raw; rollup elsewhere.                       |
| Storage growth from many grains                                  | Per-grain TTL set conservatively. Drop 15s/1m as soon as it's no longer needed for live views. |
| Dim-set changes are expensive (rebuild + cutover)                | Design dim sets generously up-front. Lock review of new rollups to a small group.           |
| Registry drift vs ClickHouse                                     | Migration tool's `diff` runs in CI on PRs touching `metrics/*.yaml`.                         |

---

## Rollout plan (sketch)

1. **Proof of concept** — one rollup (`logs_web_vital_5m`), one DAO swap (`WebVitalsQueries#GET_WEB_VITALS_TREND`), measure latency end-to-end.
2. **Framework hardening** — registry parser, migration tool, generic reader, integration tests.
3. **Phase 1 cards** — Web Vitals, Network, Interactions, Crashes (the worst offenders).
4. **Phase 2 cards** — long tail of Queries.java DAOs, screen-by-screen.
5. **Retire / migrate** existing hand-written rollups (`session_summary` etc.) into the framework if it pays off.

Phase boundaries are decision points, not commitments. Each phase is gated on the previous phase's measured impact.
