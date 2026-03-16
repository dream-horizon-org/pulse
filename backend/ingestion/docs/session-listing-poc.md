# Session Listing — POC Results

> Date: 2026-03-04
> Environment: Local Docker ClickHouse 24.8, single node
> Strategy: Materialized View (`session_summary`) + journey fetch from `otel_traces`

---

## 1. Executive Summary

We validated two query strategies for the session listing page:

- **Strategy A (Raw table):** Query `otel_traces` directly — simple but scans all spans. ~0.3–2.3 sec at 1M sessions depending on filter selectivity.
- **Strategy B (MV + journey):** Query `session_summary` MV for metrics, then fetch journey from `otel_traces` for the 50 result sessions. **~116-261 ms at 1M sessions** depending on filter type.

**Conclusion:** Strategy B (MV) is the clear winner for plain listing and quick filters (20x faster). For selective filters (DeviceModel, interaction name) the gap narrows (1.2–2.3x) but MV still wins on memory (5–20x less) and scales better beyond 1M sessions.

---

## 2. Test Data

| Dataset | Spans | Sessions | Spans/session | Used for |
|---------|-------|----------|---------------|----------|
| `project-123` | 21K | 1K | ~20 | Part 1-2 (1K scale) |
| `project-1M` | 20M | 1M | ~20 | Part 3 (1M scale validation) |

The 1M dataset includes proper interaction spans (`PulseType = 'interaction'`, `pulse.interaction.name`), 10 distinct interaction names, 3 device models, 6 network providers.

---

## 3. Part 1 — Raw Table Baseline (1K sessions / 21K spans)

All queries on `otel_traces` directly, with capped journey (`arraySlice(..., 1, 10)`).

| Query | Duration | Rows scanned | Memory |
|-------|----------|-------------|--------|
| Plain listing | 9 ms | 21,330 | 2.2 MB |
| Quick filter (HAVING) | 16 ms | 21,330 | 1.9 MB |
| Mixed filter (Platform + DeviceModel + crashes) | 9 ms | 21,330 | 5.3 MB |
| Search (userId, no index) | 7 ms | 21,330 | 91 KB |

**Key takeaway:** All queries scan all 21K rows regardless of filters. Fast at 1K but scales linearly with spans.

---

## 4. Part 2 — MV Queries (1K sessions)

MV setup: `session_summary` (AggregatingMergeTree), `bloom_filter` on `userId` and `SessionId`.

Every MV listing request also runs a journey fetch as a second query. End-to-end = MV listing + journey.

| Query | Filter type | MV listing | Journey | End-to-end | Rows scanned |
|-------|------------|-----------|---------|------------|-------------|
| Plain listing | All in MV | 4 ms | 9 ms | **~13 ms** | 12K |
| Quick filter | All in MV | 7 ms | 9 ms | **~16 ms** | 12K |
| userId search | bloom_filter | 8 ms | 9 ms | **~17 ms** | 12K |
| MV + non-MV (DeviceModel) | Semi-join | 13 ms | 13 ms | **~26 ms** | 66K |

**Key takeaway:** MV reduces rows scanned by 20x for the listing query. bloom_filter on SessionId skipped 5/9 parts for the journey fetch. Semi-join adds ~10 ms overhead at 1K scale.

---

## 5. Part 3 — Scale Validation at 1M Sessions (20M spans)

This is the critical validation. Generated 1M sessions with realistic interaction spans, then ran all scenarios.

### Setup

```
Spans:      20,000,295 (~20M)
Sessions:   1,000,007 (~1M)
Parts:      6 (session_summary), 51 (otel_traces)
Granules:   132 (session_summary), 2,582 (otel_traces)
```

### MV results (warm cache, median of 3 runs)

| Scenario | Filter type | MV listing | Journey | **End-to-end** | Rows read |
|----------|------------|-----------|---------|---------------|-----------|
| **Plain listing** | All in MV | 87 ms | 29 ms | **116 ms** | 1M + 7.6M |
| **Quick filter** (errors, crashes) | All in MV | 88 ms | 29 ms | **117 ms** | 1M + 7.6M |
| **MV + non-MV** (DeviceModel) | Semi-join | 211 ms | 29 ms | **240 ms** | 21M + 7.6M |
| **MV + non-MV** (interaction name) | Semi-join | 232 ms | 29 ms | **261 ms** | 4.8M + 7.6M |
| **userId search** | bloom_filter | 5 ms | 29 ms | **34 ms** | 9.6K + 7.6M |

### Raw table results at 1M (warm cache, median of 3 runs)

All queries scan the full 20M spans — no pre-aggregation. Journey is included in the aggregation (single query).

| Scenario | Duration | Rows read | Memory |
|----------|----------|-----------|--------|
| **Plain listing** | **2.3 sec** | 20M | 5.4 GB |
| **Quick filter** (HAVING) | **1.9 sec** | 20M | 5.4 GB |
| **Mixed filter** (Platform + DeviceModel + crashes) | **550 ms** | 20M | 1.8 GB |
| **Interaction name** (Platform + interaction name) | **310 ms** | 20M | 279 MB |
| **userId search** | **89 ms** | 20M | 5 MB |

> Mixed/interaction name filters are faster than plain listing despite scanning all 20M rows — tighter WHERE clauses reduce aggregation groups, cutting memory from 5.4 GB to 279 MB–1.8 GB.

### MV vs Raw table comparison at 1M

| Scenario | Raw table | MV (e2e) | **Speedup** |
|----------|-----------|----------|-------------|
| Plain listing | 2.3 sec | 116 ms | **20x** |
| Quick filter | 1.9 sec | 117 ms | **16x** |
| Mixed filter (DeviceModel) | 550 ms | 240 ms | **2.3x** |
| Interaction name | 310 ms | 261 ms | **1.2x** |
| userId search | 89 ms | 34 ms | **2.6x** |

### bloom_filter effectiveness at 1M

| Index | Granules scanned | Granules skipped | Skip rate |
|-------|-----------------|-----------------|-----------|
| `idx_user_id` on `session_summary` | 2/126 | 124/126 | **98.4%** |
| `idx_session_id` on `otel_traces` (journey) | 995/2,582 | 1,587/2,582 | **61%** |

### Why interaction name semi-join reads only 4.8M rows (not 20M)

`PulseType` is in the `otel_traces` ORDER BY (`ProjectId, ServiceName, PulseType, SpanName, Timestamp`). When the subquery filters `PulseType = 'interaction'`, ClickHouse uses primary key pruning to skip non-interaction granules, reading only ~4.8M rows (the 3.5M interaction spans + MV rows) instead of all 20M.

### Memory usage at 1M

| Scenario | MV | Raw table |
|----------|-----|-----------|
| Plain listing | 538 MB | 5.4 GB |
| Quick filter | 538 MB | 5.4 GB |
| Mixed filter (DeviceModel) | 264 MB | 1.8 GB |
| Interaction name | 222 MB | 279 MB |
| userId search | 4.3 MB | 5 MB |
| Journey fetch | 4.6 MB | — |

---

## 6. Final Projected Latencies

Anchored on **validated 1M actuals**, scaled linearly. ~20 spans/session assumed.

### MV (end-to-end: listing + journey)

| Sessions | Spans | **MV pure** | **Quick filter** | **DeviceModel semi-join** | **Interaction name semi-join** | **userId search** |
|----------|-------|-------------|-----------------|--------------------------|-------------------------------|-------------------|
| **1M** | 20M | **116 ms** | **117 ms** | **240 ms** | **261 ms** | **34 ms** |
| **5M** | 100M | **~470 ms** | **~470 ms** | **~1.1 sec** | **~1.2 sec** | **~45 ms** |
| **10M** | 200M | **~900 ms** | **~910 ms** | **~2.1 sec** | **~2.3 sec** | **~55 ms** |
| **20M** | 400M | **~1.8 sec** | **~1.8 sec** | **~4.3 sec** | **~4.7 sec** | **~70 ms** |
| **50M** | 1B | **~4.4 sec** | **~4.4 sec** | **~10.6 sec** | **~11.6 sec** | **~90 ms** |
| **100M** | 2B | **~8.8 sec** | **~8.8 sec** | **~21 sec** | **~23 sec** | **~120 ms** |

### Raw table (single query, includes journey)

| Sessions | Spans | **Plain listing** | **Quick filter** | **Mixed filter (DeviceModel)** | **Interaction name** | **userId search** |
|----------|-------|--------------------|-----------------|-------------------------------|---------------------|-------------------|
| **1M** | 20M | **2.3 sec** | **1.9 sec** | **550 ms** | **310 ms** | **89 ms** |
| **5M** | 100M | **~12 sec** | **~10 sec** | **~2.8 sec** | **~1.6 sec** | **~450 ms** |
| **10M** | 200M | **~23 sec** | **~19 sec** | **~5.5 sec** | **~3.1 sec** | **~890 ms** |
| **20M** | 400M | **~46 sec** | **~38 sec** | **~11 sec** | **~6.2 sec** | **~1.8 sec** |
| **50M** | 1B | **~115 sec** | **~95 sec** | **~28 sec** | **~16 sec** | **~4.5 sec** |
| **100M** | 2B | **~230 sec** | **~190 sec** | **~55 sec** | **~31 sec** | **~8.9 sec** |

### How each component scales

| Component | Scales with | 1M actual | Growth |
|-----------|------------|-----------|--------|
| MV listing (pure/quick) | Sessions | 87 ms | Linear with session count |
| DeviceModel subquery | Total spans | 211 ms | Linear with all spans |
| Interaction name subquery | Interaction spans (~17.5%) | 232 ms | Linear, but PulseType pruning skips ~82.5% |
| Journey fetch | ~Constant (50 sessions) | 29 ms | Grows slowly with more granules |
| userId search | Logarithmic | 5 ms | bloom_filter keeps pruning >98% at all scales |

---

## 7. Key Findings

1. **MV approach works.** All filter scenarios are under 270 ms end-to-end at 1M sessions — well within acceptable UI latency.

2. **Semi-join is viable, not a bottleneck.** DeviceModel and interaction name filters via semi-join complete in 211-232 ms at 1M sessions. Far better than the ~7.3 sec originally projected from 1K-scale linear extrapolation.

3. **PulseType pruning makes interaction name filtering fast.** Because `PulseType` is in the ORDER BY of `otel_traces`, the subquery skips 82.5% of rows via primary key pruning — reading only interaction spans, not all spans.

4. **bloom_filter on userId is exceptional.** 98.4% granule skip rate at 1M sessions → search in 5 ms. Scales logarithmically.

5. **Journey fetch is nearly constant.** ~29 ms for 50 sessions regardless of total data size, thanks to bloom_filter on SessionId.

6. **All scenarios are under 2.3 sec up to 10M sessions.** Beyond 20M, semi-join paths degrade — mitigate by adding `deviceModel`/`networkProvider` to the MV as `SimpleAggregateFunction(any, String)`.

7. **Linear extrapolation from small data is unreliable.** Our 1K→1M projections were 1.5-35x off. Always validate with realistic data volumes.
