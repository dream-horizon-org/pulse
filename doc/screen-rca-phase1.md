# Screen-scoped RCA — Phase 1 (brief design)

**Status:** Design notes (not implemented)  
**Scope:** A **Root cause** panel when the user has **opened a specific screen**: show **which segments** concentrate **tap frustration** on that screen — using **only** **taps**, **rage**, and **dead** signals from **`otel.otel_logs`** (`pulse.type` = **`app.click`**). No Apdex, errors, Poor user category, session summary, or heatmap-daily in Phase 1.

Mobile SDKs emit clicks as **OTLP log records**; they land in **`otel_logs`**, not as `otel_traces` spans.

**Independent** of Session Replay RCA and of **global** “all screens” insights (out of scope).

---

## 1. What Phase 1 delivers

- **Cohort:** `otel.otel_logs` rows in the **project + time window** where **`LogAttributes['pulse.type'] = 'app.click'`** and **`screen.name` matches the selected screen** (`nullIf(trimBoth(LogAttributes['screen.name']), '') = :screenName`). **No other user filters** — **screen name** is the only slice input besides `projectId`, `start`, `end`.
- **Signals (only these three):**
  - **`tap_count`** — exposure / “normal” taps (define explicitly vs rage/dead; see §4).
  - **`rage_count`** — rage taps (`LogAttributes['click.is_rage']`, typically string `'true'` in storage).
  - **`dead_count`** — dead taps (`LogAttributes['click.type'] = 'dead'`).
- **Segmentation driver:** **`bad_frustration`** — same **concentration / closest-to-total / hierarchy** pattern as `problematic_count` in `RootCauseService` (e.g. 75% threshold). Use an expression aligned with how you count rage + dead (see §4 overlap).
- **Baseline row:** one aggregate over the cohort: `tap_count`, `rage_count`, `dead_count` (and derived `bad_frustration` if useful for display).
- **Output:** ranked segments with **per-segment** `tap_count`, `rage_count`, `dead_count` (and deltas vs baseline for those three only).

**Volume guard:** **None for Phase 1** — no minimum tap or frustration threshold; revisit later if noisy empty segments are a problem.

**Out of scope (Phase 1):** Apdex, `problematic_count`, error rate, duration, `session_summary`, `interaction_heatmaps_daily`, screens-list insights, tab filters on the cohort.

---

## 2. How this differs from Interaction RCA (today)

| | **Interaction RCA** (`RootCauseService`) | **Screen RCA (Phase 1)** |
|---|------------------------------------------|----------------------------|
| **Table** | `otel.otel_traces` | `otel.otel_logs` |
| **Scope** | One **interaction** (`SpanName` = …) | One **screen** (`LogAttributes['screen.name']` / materialized `ScreenName` when present) + project + time |
| **Row grain** | Interaction **span** | One **log row** per tap (`app.click`) |
| **Counted signal** | `problematic_count` (error OR Poor) | **`bad_frustration`** (from rage + dead — see §4); **`tap_count`** for context |
| **Baseline** | One row for that interaction | One row for **screen cohort** — taps / rage / dead only |
| **Segments** | `GROUP BY` dimensions with **problematic** per bucket | Same **dimension order** and rules, but per-bucket frustration + **tap_count** for display |

**Screen name** is **`WHERE` only**, not `GROUP BY`. Segment dimensions: Platform, OsVersion, AppVersion, … (same `dimensionOrder` style as interaction RCA config).

---

## 3. Why `otel.otel_logs` (not `otel_traces`)

Tap / rage / dead are carried on **log** attributes (`click.type`, `click.is_rage`, `click.rage_count`, `screen.name`, etc.), consistent with [clickhouse-otel-schema.sql](../backend/ingestion/clickhouse-otel-schema.sql) materialized columns when the cluster schema includes them. Query **`LogAttributes['…']`** for compatibility with older local ClickHouse that may not expose all materialized columns.

**Caveats**

- A screen can have many interactions — this cohort is **all taps on that screen**, not filtered by `pulse.interaction.name` unless added later.
- Rows can be **dead + rage** on the same event — document **mutual exclusivity vs union** for `bad_frustration` in `ScreenRcaQueryBuilder` (see §4).

---

## 4. Metric contract

| Metric | Role |
|--------|------|
| `tap_count` | Exposure — e.g. `countIf(LogAttributes['click.type'] != 'dead')` or `countIf(good and not rage)` depending on product definition; **lock one rule in code**. |
| `rage_count` | Rage taps — e.g. `countIf(LogAttributes['click.is_rage'] = 'true')` |
| `dead_count` | Dead taps — e.g. `countIf(LogAttributes['click.type'] = 'dead')` |
| `bad_frustration` | **Only** input to **75% / closest-to-total** segmentation — prefer **`countIf(dead OR rage)`** per row if summing `rage_count + dead_count` would double-count dead+rage rows |

**Do not** use Apdex, span status, user category, crash, or heatmap aggregates in Phase 1.

**Materialized segment row:** `dimensions` + `tap_count`, `rage_count`, `dead_count`; optional **deltas** vs baseline **for those three only**.

---

## 5. Example baseline query (shape only)

> Adjust expressions to match the chosen `tap_count` / overlap rules in §4.

```sql
SELECT
    count() AS click_volume,
    <tap_expr>   AS tap_count,
    <rage_expr>  AS rage_count,
    <dead_expr>  AS dead_count
FROM otel.otel_logs
WHERE ProjectId = :projectId
  AND LogAttributes['pulse.type'] = 'app.click'
  AND Timestamp >= toDateTime64(:start, 9, 'UTC')
  AND Timestamp <  toDateTime64(:end,   9, 'UTC')
  AND nullIf(trimBoth(LogAttributes['screen.name']), '') = :screenName
;
```

(`PulseType` materialized column may equal `app.click` when present; filtering on `LogAttributes['pulse.type']` stays explicit.)

**Segmentation:** same `WHERE`, `GROUP BY <dimension>`, select the **same three expressions** per bucket; run dominance logic on **`bad_frustration`** (see §4).

---

## 6. Implementation sketch (backend)

1. **`ScreenRcaQueryBuilder`** — `tap_expr`, `rage_expr`, `dead_expr`; baseline + per-dimension query returning all three counts per bucket; **`FROM otel.otel_logs`** with `app.click` + screen filter.
2. **`ScreenRcaService`** — reuse `RootCauseService`-style flow with **`bad_frustration`** replacing `problematic_count`; **no volume guard** in Phase 1.
3. **API** — `screenName` + project + time only.
4. **Tests** — golden counts for tap/rage/dead on fixture **log** rows (e.g. [seed-screen-rca-app-clicks.py](../deploy/scripts/seed-screen-rca-app-clicks.py)).

---

## 7. Related code in this repo

- **Algorithm pattern only:** `RootCauseService`, `RootCauseQueryBuilder.buildProblematicCountByDimensionQuery` (swap in `bad_frustration` expression per bucket; **table** = `otel_logs`).

No dependency on `RootCauseMetricsRegistry` / Apdex / `PROBLEMATIC_COUNT` for this Phase 1 feature.
