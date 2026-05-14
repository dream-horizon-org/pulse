# Performance-Metric Distribution — ClickHouse Optimisation Plan

---

## 1. Context

The `/performance-metric/distribution` endpoint aggregates a configurable  
window (default 7 days) of `PulseType = 'interaction'` spans from  
`otel.otel_traces` and returns ~18 metrics in a single response (Apdex,  
duration percentiles, frame counts, crash/ANR counts, network status  
buckets, user-category counts).

1. **Map decoding dominates.** Every row is decoded from
  `Map(LowCardinality(String), String)` 5–7 times for
   `SpanAttributes['pulse.interaction.apdex_score' | 'user_category' |  'app.interaction.frozen_frame_count' | 'slow_frame_count' |  'analysed_frame_count' | 'unanalysed_frame_count']`.
2. **Full `Events.Name` array scans.** `has(Events.Name, 'device.crash')`,
  `has(Events.Name, 'device.anr')`, and four
   `arrayCount(x -> x LIKE 'network.N%', Events.Name)` calls each force a
   fresh decode of the `Array(LowCardinality(String))` column — 6 array
   walks per row.
3. `**WHERE` instead of `PREWHERE`.** The order key
  `(ProjectId, PulseType, SpanName, Timestamp)` perfectly matches the
   query's high-selectivity predicates (`ProjectId`, `PulseType`,
   `Timestamp` range), but those predicates are emitted in `WHERE`. As a
   result ClickHouse decodes `SpanAttributes` and `Events.Name` for
   granules that would otherwise be pruned.

A trailing `LIMIT 100` on a single-row aggregate query also adds noise to
optimizer planning and is not load-bearing.

### Goal

Reduce p95 latency materially **without** changing the API contract or
response shape, by:

- (A) Materialising the hot map / array reads as native columns on
`otel.otel_traces_local`, so the heavy `Map` and `Array` columns are no
longer touched by this query.
- (B) Pushing the order-key predicates into `PREWHERE` so granules are  
pruned before any wide column is read.

---

## 2. Non-Goals

- No API/response shape changes.
- No changes to crash/ANR pipelines, alerts cron, or RCA flows beyond what
is required to keep tests green when the underlying SQL constants change.
- Not touching `SpanAttributes['pulse.interaction.name' | 'is_error' | 'complete_time']` — out of scope.

---

## 3. Current SQL (verbatim)

```sql
SELECT
  avgIf(toFloat64OrNull(SpanAttributes['pulse.interaction.apdex_score']), StatusCode != 'Error') AS apdex,
  countIf(StatusCode != 'Error') AS success_count,
  countIf(StatusCode = 'Error') AS error_count,
  quantileTDigestIf(0.50)(Duration / 1e6, StatusCode != 'Error') AS p50,
  quantileTDigestIf(0.95)(Duration / 1e6, StatusCode != 'Error') AS p95,
  sum(toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count'])) AS frozen_frame,
  sum(toFloat64OrZero(SpanAttributes['app.interaction.unanalysed_frame_count '])) AS unanalysed_frame,
  sum(toFloat64OrZero(SpanAttributes['app.interaction.analysed_frame_count'])) AS analysed_frame,
  countIf(has(Events.Name, 'device.crash')) AS crash,
  countIf(has(Events.Name, 'device.anr')) AS anr,
  sum(arrayCount(x -> x = 'network.0', Events.Name)) AS net_0,
  sum(arrayCount(x -> x LIKE 'network.2%', Events.Name)) AS net_2xx,
  sum(arrayCount(x -> x LIKE 'network.4%', Events.Name)) AS net_4xx,
  sum(arrayCount(x -> x LIKE 'network.5%', Events.Name)) AS net_5xx,
  countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Excellent') AS user_excellent,
  countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Good')      AS user_good,
  countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Average')   AS user_avg,
  countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor')      AS user_poor
FROM otel.otel_traces
WHERE
  ProjectId = 'fancode'
  AND Timestamp >= toDateTime64('2026-05-05 08:44:22', 9, 'UTC')
  AND Timestamp <= toDateTime64('2026-05-12 08:44:22', 9, 'UTC')
  AND PulseType = 'interaction'
LIMIT 100;
```

(Note the stray space in `'app.interaction.unanalysed_frame_count '` — a
real bug in `ClickhouseConstants.CH_UNANALYSED_FRAME_SELECT_CLAUSE` that
this change also fixes.)

---

## 4. Change Set

### 4.1 Schema — materialise hot attributes

**File:** `backend/db/prod/clickhouse/otel.otel_traces.sql`

Add to `otel.otel_traces_local` (after the existing materialised-column
block ending at `ScreenName`, before the index declarations):

```sql
ApdexScore           Float32                MATERIALIZED toFloat32OrZero(SpanAttributes['pulse.interaction.apdex_score'])         CODEC(T64, ZSTD(1)),
UserCategory         LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['pulse.interaction.user_category'], '')            CODEC(ZSTD(1)),
FrozenFrameCount     UInt32                 MATERIALIZED toUInt32OrZero(SpanAttributes['app.interaction.frozen_frame_count'])     CODEC(T64, ZSTD(1)),
SlowFrameCount       UInt32                 MATERIALIZED toUInt32OrZero(SpanAttributes['app.interaction.slow_frame_count'])       CODEC(T64, ZSTD(1)),
AnalysedFrameCount   UInt32                 MATERIALIZED toUInt32OrZero(SpanAttributes['app.interaction.analysed_frame_count'])   CODEC(T64, ZSTD(1)),
UnanalysedFrameCount UInt32                 MATERIALIZED toUInt32OrZero(SpanAttributes['app.interaction.unanalysed_frame_count']) CODEC(T64, ZSTD(1)),
HasCrashEvent        UInt8                  MATERIALIZED toUInt8(has(Events.Name, 'device.crash'))                                CODEC(T64, ZSTD(1)),
HasAnrEvent          UInt8                  MATERIALIZED toUInt8(has(Events.Name, 'device.anr'))                                  CODEC(T64, ZSTD(1)),
Net0Count            UInt16                 MATERIALIZED toUInt16(arrayCount(x -> x = 'network.0',  Events.Name))                 CODEC(T64, ZSTD(1)),
Net2xxCount          UInt16                 MATERIALIZED toUInt16(arrayCount(x -> x LIKE 'network.2%', Events.Name))              CODEC(T64, ZSTD(1)),
Net4xxCount          UInt16                 MATERIALIZED toUInt16(arrayCount(x -> x LIKE 'network.4%', Events.Name))              CODEC(T64, ZSTD(1)),
Net5xxCount          UInt16                 MATERIALIZED toUInt16(arrayCount(x -> x LIKE 'network.5%', Events.Name))              CODEC(T64, ZSTD(1)),
```

Plus a skip index:

```sql
INDEX idx_user_category UserCategory TYPE set(8) GRANULARITY 4,
```

**Why these specific columns:**

- All are read by `/performance-metric/distribution`.
- All are in the inner expressions of `*_RATE` constants
(`CRASH_RATE`, `ANR_RATE`, `FROZEN_FRAME_RATE`, `SLOW_FRAME_RATE`,
`*_USER_RATE`) — so alerts cron and RCA SQL benefit too without
per-call-site work, since they reference the same Java constants.
- `Net3xxCount` intentionally omitted — not used by the distribution query.
Symmetry isn't worth the storage/ingest cost.

**Live cluster ALTER (operations PR, not app deploy):**

```sql
ALTER TABLE otel.otel_traces_local ON CLUSTER 'pulse-ch'
  ADD COLUMN ApdexScore           Float32                MATERIALIZED ... CODEC(T64, ZSTD(1)),
  ADD COLUMN UserCategory         LowCardinality(String) MATERIALIZED ... CODEC(ZSTD(1)),
  ADD COLUMN FrozenFrameCount     UInt32                 MATERIALIZED ... CODEC(T64, ZSTD(1)),
  ADD COLUMN SlowFrameCount       UInt32                 MATERIALIZED ... CODEC(T64, ZSTD(1)),
  ADD COLUMN AnalysedFrameCount   UInt32                 MATERIALIZED ... CODEC(T64, ZSTD(1)),
  ADD COLUMN UnanalysedFrameCount UInt32                 MATERIALIZED ... CODEC(T64, ZSTD(1)),
  ADD COLUMN HasCrashEvent        UInt8                  MATERIALIZED ... CODEC(T64, ZSTD(1)),
  ADD COLUMN HasAnrEvent          UInt8                  MATERIALIZED ... CODEC(T64, ZSTD(1)),
  ADD COLUMN Net0Count            UInt16                 MATERIALIZED ... CODEC(T64, ZSTD(1)),
  ADD COLUMN Net2xxCount          UInt16                 MATERIALIZED ... CODEC(T64, ZSTD(1)),
  ADD COLUMN Net4xxCount          UInt16                 MATERIALIZED ... CODEC(T64, ZSTD(1)),
  ADD COLUMN Net5xxCount          UInt16                 MATERIALIZED ... CODEC(T64, ZSTD(1)),
  ADD INDEX  idx_user_category    UserCategory TYPE set(8) GRANULARITY 4;
```

Backfill (off-peak, partition-by-partition; **never** whole-table):

```sql
ALTER TABLE otel.otel_traces_local ON CLUSTER 'pulse-ch'
  MATERIALIZE COLUMN ApdexScore IN PARTITION 'YYYYMMDD';
-- Repeat per partition for the most recent ~7 partitions only;
-- older partitions age out via the existing 90-day TTL.
```

> **Important:** Until a partition is materialised, queries against the new
> columns return `0`/`''` for that partition. Code paths must therefore
> swap to the new columns **after** the 7-day backfill completes (see
> Rollout, §7).

### 4.2 SQL — swap constants to materialised columns

**File:** `backend/server/.../constant/ClickhouseConstants.java`


| Constant (line)                                                   | Old                                                                                                     | New                                                                             |
| ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `CH_APDEX_SELECT_CLAUSE` (62)                                     | `avgIf(toFloat64OrNull(SpanAttributes['pulse.interaction.apdex_score']), StatusCode != 'Error')`        | `avgIf(ApdexScore, StatusCode != 'Error' AND ApdexScore > 0)`                   |
| `CH_ANR_SELECT_CLAUSE` (64)                                       | `countIf(has(Events.Name, 'device.anr'))`                                                               | `sum(HasAnrEvent)`                                                              |
| `CH_CRASH_SELECT_CLAUSE` (65)                                     | `countIf(has(Events.Name, 'device.crash'))`                                                             | `sum(HasCrashEvent)`                                                            |
| `CH_FROZEN_FRAME_SELECT_CLAUSE` (66)                              | `sum(toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count']))`                            | `sum(FrozenFrameCount)`                                                         |
| `CH_SLOW_FRAME_SELECT_CLAUSE` (67)                                | `sum(toFloat64OrZero(SpanAttributes['app.interaction.slow_frame_count']))`                              | `sum(SlowFrameCount)`                                                           |
| `CH_ANALYSED_FRAME_SELECT_CLAUSE` (68)                            | `sum(toFloat64OrZero(SpanAttributes['app.interaction.analysed_frame_count']))`                          | `sum(AnalysedFrameCount)`                                                       |
| `CH_UNANALYSED_FRAME_SELECT_CLAUSE` (69)                          | `sum(toFloat64OrZero(SpanAttributes['app.interaction.unanalysed_frame_count ']))` *(stray space — bug)* | `sum(UnanalysedFrameCount)`                                                     |
| `CH_SPAN_USER_CATEGORY_RAW` (21–22)                               | `ifNull(SpanAttributes['pulse.interaction.user_category'], '')`                                         | `UserCategory`                                                                  |
| `CH_SPAN_USER_CATEGORY_IS_POOR` (25)                              | `… = 'Poor'`                                                                                            | `UserCategory = 'Poor'`                                                         |
| `EXCELLENT_CAT` / `GOOD_CAT` / `AVERAGE_CAT` / `POOR_CAT` (79–82) | `countIf(ifNull(SpanAttributes['…user_category'], '') = 'X')`                                           | `countIf(UserCategory = 'X')`                                                   |
| `NET_0` (88)                                                      | `sum(arrayCount(x -> x = 'network.0', Events.Name))`                                                    | `sum(Net0Count)`                                                                |
| `NET_2XX` … `NET_5XX` (89–92)                                     | `sum(arrayCount(x -> x LIKE 'network.N%', Events.Name))`                                                | `sum(Net2xxCount)` … `sum(Net5xxCount)`                                         |
| `CRASH_RATE` (103)                                                | `… (countIf(has(Events.Name, 'device.crash'))/count()) …`                                               | `… (sum(HasCrashEvent)/count()) …`                                              |
| `ANR_RATE` (104)                                                  | analogous                                                                                               | `… (sum(HasAnrEvent)/count()) …`                                                |
| `FROZEN_FRAME_RATE` (105–106)                                     | three map lookups                                                                                       | `sum(FrozenFrameCount) / (sum(AnalysedFrameCount) + sum(UnanalysedFrameCount))` |
| `SLOW_FRAME_RATE` (107–108)                                       | analogous                                                                                               | `sum(SlowFrameCount) / (sum(AnalysedFrameCount) + sum(UnanalysedFrameCount))`   |
| `POOR_USER_RATE` … `EXCELLENT_USER_RATE` (110–117)                | `ifNull(SpanAttributes['…user_category'], '') …`                                                        | `UserCategory …`                                                                |


These are pure string substitutions. Downstream call sites
(`Functions.java`, `RootCauseMetricsRegistry.java`, `ClickhouseMetricService.java`,
`ErrorAttributionDrillDownQueryBuilder.java`) read these constants by
reference and need **no source changes**.

`NET_3XX` is left as-is (uses `Events.Name` array scan). It is not on the
hot path for `/performance-metric/distribution`. We can revisit.

**Other production references using raw inline SQL** — swap in the same
PR for consistency, but only on interaction-pulse-type code paths:

- `dao/heatmap/HeatmapQueries.java` (L56–57) — apdex
- `dao/rootcause/SessionEvidenceQueryBuilder.java` (L72) — apdex
- `dao/session/SessionListingConstants.java` (L71) — `UserCategory = 'Poor'`; (L72) — `FrozenFrameCount > 0`

(Out of scope for this round: `pulse.interaction.name`, `is_error`,
`complete_time` — these are not on the hot path for distribution.)

### 4.3 PREWHERE — push order-key predicates

**File:** `backend/server/.../service/interaction/ClickhouseMetricService.java`
(WHERE-clause builder used by `getMetricDistribution`)

Emit `ProjectId`, `PulseType`, and the `Timestamp` range as a `PREWHERE`
block; remaining filters stay in `WHERE`:

```sql
… FROM otel.otel_traces
PREWHERE ProjectId = :projectId
   AND PulseType = :pulseType
   AND Timestamp >= :start AND Timestamp <= :end
WHERE … other filters …
```

The order key is `(ProjectId, PulseType, SpanName, Timestamp)`, so this
prunes granules **before** any wide column (`SpanAttributes`,
`Events.Name`, or our new materialised columns) is read.

Also drop the trailing `LIMIT 100` — it's a no-op on this single-row
aggregate and inhibits some optimiser paths.

---

## 5. Files Touched

### Production


| Path                                                                      | Change                                              |
| ------------------------------------------------------------------------- | --------------------------------------------------- |
| `backend/db/prod/clickhouse/otel.otel_traces.sql`                         | Add 12 materialised columns + 1 skip index          |
| `backend/server/.../constant/ClickhouseConstants.java`                    | Constant swaps (table in §4.2)                      |
| `backend/server/.../service/interaction/ClickhouseMetricService.java`     | PREWHERE for order-key predicates; drop `LIMIT 100` |
| `backend/server/.../dao/heatmap/HeatmapQueries.java` (L56–57)             | Apdex → `nullIf(ApdexScore, 0)`                     |
| `backend/server/.../dao/rootcause/SessionEvidenceQueryBuilder.java` (L72) | Apdex → materialised column                         |
| `backend/server/.../dao/session/SessionListingConstants.java` (L71–72)    | `UserCategory` + `FrozenFrameCount`                 |


### Tests


| Path                                                              | Change                                                                                       |
| ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `service/interaction/ClickhouseMetricServiceTest.java` (L458–482) | Refresh literal SELECT-clause expectations to new column names                               |
| `service/clickhouse/ClickhouseMetricTest.java` (L95–1132)         | Refresh any SQL-substring asserts                                                            |
| `service/rootcause/RootCauseQueryBuilderTest.java` (L30–33)       | Refresh expected SQL for `CRASH_RATE` / `ANR_RATE` / `FROZEN_FRAME_RATE` / `SLOW_FRAME_RATE` |
| `service/alert/core/util/MetricToFunctionMapperTest.java`         | Re-run; should be unaffected (enum-name asserts only)                                        |
| `service/alert/core/AlertEvaluationServiceTest.java`              | Mocks `ClickhouseMetricService` — re-run; spot-check L838                                    |
| `dao/heatmap/*Test.java`, `dao/session/*Test.java` (if present)   | Sanity-check apdex / filter assertions                                                       |


**New test:** extend `ClickhouseMetricServiceTest` to assert that the
emitted SQL (a) contains `PREWHERE ProjectId =` and (b) does **not**
contain `LIMIT 100` on the distribution path.

---

## 6. Impact & Risk Analysis

### 6.1 Existing queries — safe

`MATERIALIZED` columns are excluded from `SELECT `* and from `INSERT`
column lists by default. None of the 30+ files identified that read or
write `otel.otel_traces` use `SELECT *`; all read by explicit column
names. ⇒ Zero implicit-coupling risk.

### 6.2 Ingest CPU — small overhead

Each INSERT now evaluates 12 extra expressions per row:

- 4 × `toUInt32OrZero(Map[...])` (cheap)
- 1 × `Float32` map lookup, 1 × `LowCardinality(String)` map lookup
- 2 × `has(Events.Name, ...)` (early-terminating)
- 4 × `arrayCount(... LIKE / =, Events.Name)` (heaviest)

On non-interaction spans `Events.Name` is typically empty, so array scans
short-circuit. **Expected ingest CPU overhead: <5 %.** Will be measured on
staging before prod DDL.

### 6.3 Disk — modest

Most new columns are integer or `LowCardinality`, dominated by zeros / a
small dictionary. `T64+ZSTD(1)` compresses these very well. Rough
estimate: a few GB/day/shard hot-tier delta. The existing
`7-day → cold, 90-day → delete` TTL bounds total cost. `UserCategory`
(`LowCardinality`) is essentially free.

### 6.4 Merges — marginal

~17 → ~29 materialised columns. Per-part metadata grows slightly. No
expected merge regression at current part counts; will monitor
`system.merges` and part counts during rollout.

### 6.5 Backfill — the real operational risk

`MATERIALIZE COLUMN … IN PARTITION` rewrites every part in the named
partition. If run on a hot partition during peak ingest, the mutation
queues behind merges and spikes disk IO.

**Mitigations:**

- Backfill **oldest hot partition first**, off-peak.
- **One partition at a time**, polling `system.mutations` for `is_done = 1`
before starting the next.
- New (post-DDL) partitions populate natively at ingest — no backfill
needed there.

### 6.6 Schema drift

Confirm `backend/db/prod/clickhouse/otel.otel_traces.sql` is the only
canonical DDL and that no separate dev/staging bootstrap diverges
(`grep -r "otel_traces_local" backend/db backend/ingestion deploy`).

### 6.7 Behavioural change to call out

`avgIf(ApdexScore, StatusCode != 'Error' AND ApdexScore > 0)` adds an
explicit `> 0` filter that the previous `toFloat64OrNull` produced
implicitly (it returned NULL for missing/blank attributes, and `avgIf`
ignored NULLs). For spans where `apdex_score` is genuinely `0`, the new
behaviour will exclude them from the average. Worth a quick check with
data — if real `0` apdex spans exist, switch to
`avgIf(nullIf(ApdexScore, 0), …)`.

---

## 7. Rollout Sequence

1. **Staging — schema only.** Land DDL on staging. Let new partitions
  populate natively for ≥1 day. Measure ingest CPU + part size delta vs
   the prior week.
2. **Staging — backfill.** `MATERIALIZE COLUMN … IN PARTITION` for the
  last 7 partitions, oldest first, polling `system.mutations`.
3. **Staging — parity.** Run §8.1 parity check on a top-3-tenant +
  7-day window.
4. **Prod — schema only.** Land DDL on prod. Allow native population for
  ≥1 day; verify ingest stable.
5. **Prod — backfill.** Last 7 partitions, off-peak, one at a time.
6. **App PR.** Merge constant swap + PREWHERE + LIMIT removal + test
  updates **after** backfill is complete on prod.
7. **Observe.** Watch `system.query_log` for the endpoint's `query_id`
  pattern; expect ≥5× drop in `read_bytes` and large drop in
   `query_duration_ms`.

---

## 8. Verification

### 8.1 Correctness parity (staging)

```bash
clickhouse-client --query "
  SELECT … (old expressions) …
  FROM otel.otel_traces
  WHERE ProjectId = '…' AND PulseType = 'interaction'
    AND Timestamp BETWEEN '…' AND '…'"
clickhouse-client --query "
  SELECT … (new expressions) …
  FROM otel.otel_traces
  PREWHERE ProjectId = '…' AND PulseType = 'interaction'
    AND Timestamp BETWEEN '…' AND '…'"
```

Assert apdex within 1e-6, all counts and percentiles equal.

### 8.2 Latency / IO

Compare `query_duration_ms`, `read_rows`, `read_bytes`, `memory_usage`
from `system.query_log` for both queries on the same tenant. **Target:
`read_bytes` ↓ ≥5×, `query_duration_ms` ↓ materially.**

### 8.3 PREWHERE actually applied

`EXPLAIN PIPELINE` / `EXPLAIN indexes = 1` on the new query — verify
`Prewhere` step lists `ProjectId`, `PulseType`, `Timestamp`, and that
`Granules:` count drops vs old.

### 8.4 Backend tests

```bash
cd backend/server && mvn -Dtest=ClickhouseMetricServiceTest test
cd backend/server && mvn -Dtest=ClickhouseMetricTest test
cd backend/server && mvn -Dtest=RootCauseQueryBuilderTest test
cd backend/server && mvn -Dtest=AlertEvaluationServiceTest test
cd backend/server && mvn verify
```

### 8.5 Endpoint smoke

```bash
cd deploy && ./scripts/start.sh -d
curl -s -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H 'Content-Type: application/json' -d @sample-req.json | jq -S . > new.json
diff baseline.json new.json   # must be empty
```

### 8.6 Backfill completeness

```sql
SELECT
  count() AS rows,
  countIf(ApdexScore = 0 AND SpanAttributes['pulse.interaction.apdex_score'] != '') AS unmaterialized
FROM otel.otel_traces_local
WHERE _partition_id = 'YYYYMMDD' AND PulseType = 'interaction';
-- `unmaterialized` must be 0 before swapping app code to read new columns.
```

---

## 9. Rollback

- **App code:** revert the constant-swap commit; constants point back at
the `SpanAttributes[...]` / `Events.Name` expressions. Rollback is a
single PR revert.
- **Schema:** materialised columns can be left in place safely (no
consumer reads them after the app revert). If we want them gone:
  ```sql
  ALTER TABLE otel.otel_traces_local ON CLUSTER 'pulse-ch'
    DROP COLUMN ApdexScore, DROP COLUMN UserCategory, … ;
  ```
  Off-peak; this rewrites parts.
- **PREWHERE/LIMIT changes:** revertable independently of the schema.

---

## 10. Open Questions

1. **Behavioural change in §6.7** — if any tenant reports genuine
  apdex `0` rows, do we want `avgIf(nullIf(ApdexScore, 0), …)` instead of
   the `ApdexScore > 0` predicate? Equivalent today, but slightly
   different semantics if an upstream change ever emits a real `0`.
2. `**Net3xxCount` exclusion** — fine to skip, or add for symmetry?

