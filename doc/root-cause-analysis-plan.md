# Root Cause Analysis – Planning Document

This document describes the **Root Cause Analysis** feature (segment vs baseline metrics for critical interactions) so it can be re-implemented or planned on the `main` branch.

**Approach: deterministic algorithm + data pipeline + cache.** Segment selection follows a configurable-threshold algorithm (default **75%** for both first dimension and adding dimensions) defined below. A **data pipeline** runs on a schedule for each **active** (not archived) critical interaction over a **configurable lookback window** (default **7 days**): it runs the algorithm against ClickHouse, computes baseline and **Top Contributing Segments** (hierarchy a → b → c), and **writes the result to a dedicated ClickHouse table** (cache). The API and UI **read from this table** for the day.

---

## Feature description (from base prompt)

The following is the intended behaviour of the feature, as expressed in the system and user prompts used on the branch. Use this as the product/feature spec when re-implementing.

**Role and scope**

- The feature acts as an **expert mobile app performance analyst** for the Pulse observability platform.
- For a chosen **critical interaction** and **time period**, it answers: *Which user segments contribute most to poor performance?*

**Goal**

- Find the **specific multi-dimensional user segments** that contribute most to:
  - poor performance (e.g. low APDEX, high duration),
  - high error rates,
  - crashes,
  - ANRs (Application Not Responding),
  - frozen frames.
- Think like a detective: **start broad, then drill into** the dimension(s) and combinations that explain the most variance.

**Segments and dimensions**

- Segments are defined by one or more **dimensions**, e.g.:
  - Platform (e.g. android, ios),
  - OS version,
  - App version,
  - Device model,
  - Network provider (e.g. Jio, Airtel),
  - Region / state (e.g. Andhra Pradesh).
- The feature may explore **combinations** of dimensions (e.g. Android 10 + device model + Jio + region), not only single dimensions. It can go as deep as needed (3, 4 or more dimensions) when the data warrants it.

**Approach (strategy)**

1. Understand the interaction’s **thresholds** (from interaction config).
2. Query **overall baseline** metrics (no dimensions) to see current health of the interaction.
3. **Scan dimensions** to find which show high variance in performance.
4. For high-variance dimensions, **explore multi-dimensional combinations**.
5. **Cross-correlate** with crash/ANR data and error logs for the worst segments.
6. Stop when there are **clear, evidence-backed root causes**.

**Rules**

- Always include **volume** so segments can be assessed for statistical significance.
- **Ignore** segments with very low volume (e.g. &lt; 1% of total) as noise.
- **Focus** on segments that are both **high-volume and poor** on metrics.
- When a bad segment is found, try adding another dimension to see if it gets more specific.
- **Compare every segment to the overall baseline** to quantify impact (Value vs Baseline, and Delta %).

**Output**

The feature produces **Top Contributing Segments only** (no executive summary or recommendations). Each segment is a drill-down level with a **Metric | Value | Baseline | Delta** table.

**Top Contributing Segments** can be in one of two modes:

- **Hierarchical mode (when thresholds are met):** A hierarchy at increasing granularity (same "path" refined step by step), e.g. **(a)** Android (Platform) → **(b)** Android + App version 3.4.5 → **(c)** Android + App version 3.4.5 + Jio or Airtel. Each level has its metric table. Thresholds are **configurable** (e.g. config or env); default is **75%** for both (1) first dimension and (2) adding further dimensions.
- **Flat mode (fallback when no segment reaches the thresholds):** Do **not** go hierarchically. Instead, select **multiple dimensions separately**: output the **top segment value for each dimension independently**, each with its metric table. Example: **Platform – Android** | **Location – Rajasthan** | **Network – Jio** (three separate segments, one per dimension). Use this when problematic share is spread (e.g. 40%, 30%, 30%) so no single path reaches the configured threshold(s).

**Top contributing segments** (detail) – For each segment level:
   - A heading with the dimension combo (e.g. “Android 10 + SM-A135F + Jio (Andhra Pradesh)”).
   - A **Markdown table**: **Metric | Value | Baseline | Delta** (e.g. APDEX, Error Rate, Poor User %, Duration P50/P95, Crash Rate, ANR Rate, Frozen Frame Rate, Slow Frame Rate, Volume).

**Formatting**

- Use **Markdown tables** (Metric | Value | Baseline | Delta) for each segment level. **Rank by impact** (volume × severity).

**Segment selection algorithm (canonical solution)**

The following **deterministic algorithm** is the canonical way to choose and refine segments. The data pipeline implements this in code to produce the cached result for the day.

1. **Problematic area**  
   Define total problematic count = number of interactions that are **error OR poor** (user experience). Use a **union** (distinct count): an interaction that is both error and poor is counted once. So: `count(DISTINCT span_id)` where `StatusCode = 'Error' OR SpanAttributes['pulse.interaction.user_category'] = 'Poor'` (or equivalent in ClickHouse), not the sum of two counts.

2. **Configurable parameters**  
   The following are **configurable** (e.g. application config or env):  
   - **First dimension threshold** (default **75**), in percent (0–100): minimum share of total problematic area that a single dimension value must reach to enter hierarchical mode.  
   - **Add-dimension threshold** (default **75**), in percent (0–100): when adding further dimensions, minimum share of total problematic area that a sub-segment must maintain.  
   - **Lookback window** (default **7**), in days: time range over which the pipeline queries data (e.g. “last N days”).  
   If not set, thresholds default to **75%** and lookback to **7** days.

3. **First dimension**  
   For each dimension (Platform, OsVersion, AppVersion, DeviceModel, NetworkProvider, GeoState/Location), find the segment value that covers the **largest share** of the total problematic area.  
   - **If some dimension value reaches the first-dimension threshold** (default 75%): Pick that one (the most focused such). Continue to step 4 (hierarchical mode).  
   - **If no dimension value reaches the threshold** (e.g. top values are 40%, 30%, 30%): **Do not go hierarchically.** Use **flat mode** (step 5b): output the **top segment value per dimension separately** (e.g. Platform – Android, Location – Rajasthan, Network – Jio), each with its metric table. Stop.

4. **Adding further dimensions (hierarchical mode only)**  
   From the current selected dimension(s), consider adding **other dimensions** in order (e.g. Platform → AppVersion → Network provider). When adding a dimension:
   - Only accept the new sub-segment if it still contains **at least the add-dimension threshold** (default 75%) of the total problematic area.
   - If **no** sub-segment can reach the add-dimension threshold: **Do not go deeper.** Use **flat mode** (step 5b): keep the current hierarchy up to this point, and **additionally** output the **top segment value for each remaining dimension separately** (e.g. Platform – Android, Location – Rajasthan, Network – Jio), each with its metric table. Stop.
   - Otherwise, repeat until no further dimension can be added without dropping below the threshold.

5. **Result**  
   - **Hierarchical result:** The pipeline outputs the **Top Contributing Segments** hierarchy (a) → (b) → (c), each with its metric table (Value, Baseline, Delta).  
   - **Flat result (fallback):** The pipeline outputs **multiple dimensions separately**: one segment per dimension (e.g. Platform – Android, Location – Rajasthan, Network – Jio), each with its metric table. No combined hierarchy when no single path reaches the configured thresholds.

---

## 1. Feature Overview

### What it does

- For a **critical interaction** (e.g. "Contest Join", "Checkout"), the feature identifies **user segments** (by device, OS, network, region, app version, or combinations) that contribute most to poor performance.
- For each segment (and for the overall baseline), it shows a **metric table** with:
  - **Metric** – e.g. APDEX, Error Rate, Poor User %, Duration P50/P95, Crash Rate, ANR Rate, Frozen Frame Rate, Slow Frame Rate, Volume.
  - **Value** – metric for the segment (or current period).
  - **Baseline** – metric for the overall interaction (same time range, no segment).
  - **Delta** – percentage change vs baseline: `((Value - Baseline) / Baseline) * 100`. For Volume, Delta is sometimes shown as segment volume as % of total: `(Value / Baseline) * 100`.

### Example output (table format)

| Metric          | Value  | Baseline | Delta  |
|-----------------|--------|----------|--------|
| APDEX           | 0.04   | 0.56     | -93%   |
| Error Rate      | 15.6%  | 6.9%     | +126%  |
| Poor User Pct   | 84.8%  | 14.7%    | +477%  |
| Duration P50    | 2781 ms| 1041 ms  | +167%  |
| Duration P95    | 3774 ms| 3238 ms  | +16%   |
| Crash Rate      | 4.8%   | 1.1%     | +336%  |
| ANR Rate        | 4.8%   | 1.2%     | +300%  |
| Frozen Frame Rate | 2.1% | 0.5%     | +320%  |
| Slow Frame Rate | 8.2%   | 3.0%     | +173%  |
| Volume          | 500    | 3855     | 13%    |

---

## 2. Data Model (needed for implementation)

### Interaction definitions (MySQL)

- **Table:** `interaction` (see `deploy/db/mysql-init.sql`).
- **Columns (relevant):** `interaction_id`, `tenant_id`, `name`, `status`, `details` (JSON), `is_archived`, timestamps.
- Critical interaction **names** (e.g. "Contest Join") are stored here; the same name is used to filter telemetry in ClickHouse.

### Interaction telemetry (ClickHouse)

- **Primary table:** `otel.otel_traces` (schema in `backend/ingestion/clickhouse-otel-schema.sql`).
- **Filter for “interaction” spans:** `PulseType = 'interaction'` (materialized from `SpanAttributes['pulse.type']`).
- **Filter for a specific interaction:** `SpanName = '<interaction_name>'` (e.g. `SpanName = 'Contest Join'`).
- **Key columns for metrics:**
  - `SpanName` – interaction name.
  - `SpanAttributes` – map including `pulse.interaction.apdex_score`, `pulse.interaction.user_category`, `app.interaction.frozen_frame_count`, etc.
  - `Events.Name` – array of event names (e.g. `device.crash`, `device.anr`).
  - `Duration`, `StatusCode`, `Timestamp`, `UserId`, `SessionId`, `ProjectId`.
  - Dimensions: `Platform`, `OsVersion`, `AppVersion`, `DeviceModel`, `NetworkProvider`, `GeoState` (many materialized from attributes).

So: **Value** and **Baseline** are computed by running **aggregation queries** on `otel_traces` with the appropriate filters and GROUP BY (or no GROUP BY for baseline).

---

## 3. Metrics to Support

These are the metrics that should appear in the segment vs baseline table. Each is an aggregation over `otel_traces` with `PulseType = 'interaction'` and `SpanName = '<interaction_name>'` (and time range, tenant/project).

| Metric key     | Label / display     | ClickHouse expression (conceptually) |
|----------------|---------------------|--------------------------------------|
| volume         | Volume              | `count()`                             |
| apdex          | APDEX               | `avgIf(toFloat64OrNull(SpanAttributes['pulse.interaction.apdex_score']), StatusCode != 'Error')` |
| error_rate     | Error Rate %        | `if(count() = 0, NULL, (countIf(StatusCode = 'Error')/count()) * 100)` |
| poor_user_pct  | Poor User %         | Poor user category count / total with category, * 100 |
| duration_p50   | Duration P50 (ms)   | `quantileTDigestIf(0.50)(Duration/1e6, StatusCode != 'Error')` |
| duration_p95   | Duration P95 (ms)   | `quantileTDigestIf(0.95)(Duration/1e6, StatusCode != 'Error')` |
| crash_rate     | Crash Rate %        | `if(count() = 0, NULL, (countIf(has(Events.Name, 'device.crash'))/count()) * 100)` |
| anr_rate       | ANR Rate %          | `if(count() = 0, NULL, (countIf(has(Events.Name, 'device.anr'))/count()) * 100)` |
| frozen_frame_rate | Frozen Frame Rate % | Same as `ClickhouseConstants.FROZEN_FRAME_RATE`: frozen_frame_count / (analysed_frame_count + unanalysed_frame_count) * 100 |
| slow_frame_rate   | Slow Frame Rate %   | Same pattern as frozen: `slow_frame_count` / (analysed_frame_count + unanalysed_frame_count) * 100 (`SpanAttributes['app.interaction.slow_frame_count']`, etc.) |

- **Baseline:** same SELECT expressions, same WHERE (interaction + time range), **no GROUP BY** → one row.
- **Segment:** same SELECT, same WHERE, **GROUP BY** chosen dimensions (e.g. `Platform`, `OsVersion`) → one row per segment. Optionally filter by dimension values.

Full exact expressions can be copied from the branch’s `DataSourceRegistry` (data source `interaction_traces`) or from `ClickhouseConstants` / `ClickhouseMetricService` where the same metrics are used for dashboards/alerts.

---

## 4. Delta Calculation

- For **all metrics except Volume** (when showing “segment as % of total”):
  - `deltaPct = (Baseline == 0 || Baseline == null) ? null : ((Value - Baseline) / Baseline) * 100`
- For **Volume** only, if the product wants “segment volume as % of total”:
  - `deltaPct = (Baseline == 0 || Baseline == null) ? null : (Value / Baseline) * 100`
- All calculations are deterministic from Value and Baseline.

---

## 5. Data pipeline and cache

The feature is driven by a **data pipeline** that fills root-cause data and **writes it to a dedicated ClickHouse table** (cache). The API and UI **read from this table**; they do not run the algorithm on demand.

**Pipeline (fills the data)**

- Runs on a **schedule** (e.g. once per day) for each **active** critical interaction (i.e. **not archived**: `is_archived = 0` in MySQL).
- **Time window:** **configurable lookback** in days (default **7**), rolling.
- For each interaction and the last-7-days window:
  1. Run the **segment selection algorithm**: total problematic count → first dimension (90% rule) → add dimensions (75% rule) → selected segment(s).
  2. Run **baseline** query (no GROUP BY); run **segment** query(ies) for the selected dimension filters.
  3. Compute **deltas** from baseline (Section 4).
  4. **Write** the result (baseline, **Top Contributing Segments** hierarchy a → b → c, metrics, deltas) into a **dedicated ClickHouse table** (cache), keyed by e.g. `tenantId + interactionName + date` (one row or partition per interaction per day).

**Cache (ClickHouse table)**

- Create a **new table in ClickHouse** to store the pipeline output (e.g. baseline, segments at each level, metrics, deltas, `cachedAt`). Key by tenant, interaction name, and date so the API can read by interaction (and optional date).
- API: when the UI requests root-cause data for an interaction, **read from this table**. If no row exists (pipeline not yet run or no data), return the appropriate response (see **Edge cases** below).

**No on-demand computation.** The pipeline is the only place that runs the algorithm and writes data; the API is read-only from the cache table.

**Active interactions**

- **Active** means not archived: use `is_archived = 0` (or equivalent) when listing interactions for the pipeline.

**Edge cases (messaging still under discussion)**

- **Total problematic count = 0:** Indicate that **everything is good** (e.g. return a flag or message to that effect; no segments to show).
- **No data for tenant** (e.g. no traces in the window): Return **no data available** or **NA** (exact wording and response shape to be finalised).
- **No segment reaches 90%** (e.g. top dimension values are 40%, 30%, 30%): Use **flat mode** — output the **top segment value per dimension separately** (e.g. Platform – Android, Location – Rajasthan, Network – Jio), each with its metric table. Do not build a hierarchy.
- **No sub-segment reaches 75%** (adding any dimension drops below 75%): Use **flat mode** — output multiple dimensions separately as above (or keep current hierarchy level and add top-per-dimension for remaining dimensions). Do not go deeper.
- *Note: The exact user-facing messages and API response format for these edge cases are still under discussion.*

## 6. Algorithm-based implementation 
The segment vs baseline **numbers** and **table** are produced by **implementing the segment selection algorithm** (Section “Segment selection algorithm”) in code, plus ClickHouse queries and delta calculation (used by the pipeline in Section 5).

### 6.1 Reuse

- **ClickHouse:** `otel_traces`, same filters (`PulseType = 'interaction'`, `SpanName = '<name>'`, time range, tenant/project).
- **Metric definitions:** Either copy the metric expressions from the branch’s `DataSourceRegistry` / `ClickhouseConstants` into a small config or registry on main, or reintroduce a `DataSourceRegistry`-like class that only holds interaction_traces metrics and dimensions.
- **Query execution:** Use the same path as the branch’s query execution (e.g. a builder that builds a SELECT with the chosen metrics, WHERE with time + SpanName + optional dimension filters, and optional GROUP BY). If main already has a way to run ad-hoc ClickHouse queries for performance (e.g. a “data query” or “metrics” API), that can be reused; otherwise add a thin layer that builds and runs one query per “request” (baseline or segment).

### 6.2 New or refactored pieces

- **Baseline query:** One query with no GROUP BY, returning one row: all of the metrics above for the interaction and time range.
- **Segment query(ies):** One or more queries with GROUP BY dimension(s) (e.g. `Platform`, or `Platform, OsVersion`). Optional: filter by dimension values. Order by volume (or worst metric) and limit N.
- **Delta calculation:** In code, for each (segment, metric): compute Delta from Value and Baseline using the rules in **Section 4**.
- **Response shape:** e.g.  
  `{ baseline: { volume, apdex, error_rate, ... }, segments: [ { dimensions: { Platform: "android", OsVersion: "10" }, metrics: { ... }, deltas: { ... } }, ... ] }`  
  so the UI can render “Metric | Value | Baseline | Delta” per segment.

### 6.3 Segment strategy: use the algorithm

Use the **segment selection algorithm**:

1. Compute total problematic count (errors + poor interactions). Query each dimension (e.g. Platform, OsVersion, AppVersion, DeviceModel, NetworkProvider, GeoState) for segment-level **problematic count** (and volume). If **some value reaches ~90%**, pick it and go to step 2 (hierarchy). If **none reaches 90%**, use **flat mode**: output the **top segment value per dimension separately** (e.g. Platform – Android, Location – Rajasthan, Network – Jio), each with its metric table; stop.
2. (Hierarchical mode) With the first dimension fixed, try adding one more dimension at a time. For each candidate sub-segment, accept it only if it still contains **≥ 75% of the total problematic area**. If **no** sub-segment reaches 75%, use **flat mode**: output top per dimension separately; stop. Otherwise stop when no further dimension can be added.
3. Return either **hierarchical** segments (a)→(b)→(c) or **flat** segments (one per dimension) with their metrics, baseline, and deltas. The UI renders the “Metric | Value | Baseline | Delta” table from this structured response.

All steps are deterministic: queries + arithmetic + 90% / 75% rules + **flat fallback** when no segment reaches 90% or 75%. The pipeline runs this and writes the result to the cache for the day.

---

## 7. Implementation Plan (for main branch)

Use this as a checklist when implementing on main. **Implement the data pipeline that fills the cache; the API serves from cache for the day.**

### Phase 1 – Data and metrics

- [ ] Confirm `otel_traces` (and optionally `otel_logs`) schema on main matches or can support the metrics above (e.g. `SpanAttributes['pulse.interaction.apdex_score']`, `Events.Name`, etc.).
- [ ] Define a small **metrics config** or **registry** on main: list of metrics (key, label, ClickHouse expression) for “interaction traces,” plus list of dimensions (key, column name) for segment breakdown.
- [ ] Implement a **query builder** that, given (interaction name, time range, tenant/project, list of metric keys, optional dimensions and filters), produces a single ClickHouse SELECT and runs it (reuse existing ClickHouse client on main).
- [ ] Add a way to get **problematic count** per segment (and total) using **union**: count of spans where `StatusCode = 'Error' OR SpanAttributes['pulse.interaction.user_category'] = 'Poor'` (no double-count). Same for segment-level counts so the 90% / 75% algorithm can be applied.
- [ ] Design **new ClickHouse table** for root-cause cache (columns e.g. tenant_id, interaction_name, date, baseline JSON, segments JSON or normalized rows, cached_at). Pipeline writes; API reads.

### Phase 2 – Segment selection algorithm + baseline and segments

- [ ] **Total problematic count:** One query (no GROUP BY) for the interaction and time range: total = **union** count (spans that are error OR poor; no double-count).
- [ ] **First dimension (90% rule):** For each dimension, query segment-level problematic share. If **some value reaches ~90%**, pick it and continue to add dimensions (75% rule). If **no value reaches 90%**, use **flat mode**: output the **top segment value per dimension separately** (e.g. Platform – Android, Location – Rajasthan, Network – Jio), each with its metric table; store in cache and stop.
- [ ] **Add dimensions (75% rule)** (hierarchical mode only): From that segment, iteratively try adding one more dimension. Accept a sub-segment only if it still contains **≥ 75% of the total problematic area**. If **no** sub-segment reaches 75%, use **flat mode**: output top per dimension separately (or current hierarchy + top-per-dimension); store in cache and stop. Otherwise continue until no dimension can be added.
- [ ] **Baseline:** Call the query builder with no dimensions; parse the single row into a map `metricKey → value`.
- [ ] **Segment metrics:** For the selected segment(s), run the query builder with the chosen dimension filters; parse rows into metrics and compute **Delta** from baseline (Section 4).
- [ ] **Cache table:** Create a **new table in ClickHouse** to store pipeline output (baseline, Top Contributing Segments — either **hierarchical** (a→b→c) or **flat** (one segment per dimension), metrics, deltas, cachedAt), keyed by tenant, interaction name, date.
- [ ] **Pipeline job:** Implement a scheduled job that, per day, for each **active** (not archived) interaction, over the **configured lookback window** (default 7 days): runs the algorithm, computes baseline + segments + deltas, and **writes to the ClickHouse cache table**.
- [ ] **API:** `GET .../interactions/{name}/root-cause` (optional date); **reads from the ClickHouse cache table**. On cache miss or no data: see **Edge cases** (point 7 — messaging still under discussion: e.g. total problematic = 0 → "everything is good"; no data for tenant → "no data available" or "NA").

### Phase 3 – UI

- [ ] Add a view (e.g. under Critical Interaction Details) that calls the API and renders the “Metric | Value | Baseline | Delta” table per segment (and optionally overall baseline). Optionally show the dimension combo (e.g. “Android 10 + Jio (Andhra Pradesh)”) and a short impact line.
---

## 8. References

- ClickHouse schema: `backend/ingestion/clickhouse-otel-schema.sql` (otel_traces).
- **New ClickHouse table:** to be created for root-cause cache (pipeline output per tenant + interaction + date; see Phase 1).
- Interaction table: `deploy/db/mysql-init.sql` (interaction), and backend DAOs that read interaction by name/tenant. **Active** = `is_archived = 0`.
- Metric expressions: use the same definitions as for dashboards/alerts (e.g. `ClickhouseConstants`, or a small registry with table `otel_traces`, base filter `PulseType = 'interaction'`, dimensions Platform/OsVersion/AppVersion/DeviceModel/NetworkProvider/GeoState, and metrics volume, apdex, error_rate, poor_user_pct, duration_p50/p95, crash_rate, anr_rate, frozen_frame_rate, slow_frame_rate).
- **Time window:** configurable lookback (default 7 days), rolling. **Fallback:** when no segment reaches the first-dimension threshold or no sub-segment reaches the add-dimension threshold, use **flat mode** (multiple dimensions separately, e.g. Platform – Android, Location – Rajasthan, Network – Jio). **Edge cases (point 7):** total problematic = 0 → "everything is good"; no data for tenant → "no data available" or "NA" — *exact messaging still under discussion.*

---

## 9. Summary

- **Feature:** Segment vs baseline metric table (Value, Baseline, Delta) for critical interactions.
- **Solution:** **Data pipeline + ClickHouse cache table.** Pipeline runs for **active** (not archived) interactions over the **configured lookback window** (default 7 days); runs the segment selection algorithm (configurable thresholds, default 75%, **union** for problematic count). **Output:** either **hierarchical** Top Contributing Segments (a→b→c) when thresholds are met, or **flat** (multiple dimensions separately, e.g. Platform – Android, Location – Rajasthan, Network – Jio) when no segment reaches the thresholds. Writes to a new ClickHouse table; API reads from it. **Edge cases (point 7):** total problematic = 0 → "everything is good"; no data for tenant → "no data available" or "NA"; thresholds not met → flat mode — *messaging still under discussion.*
- **Data:** Interaction definitions in MySQL; interaction telemetry in ClickHouse `otel_traces`; **new ClickHouse table** for root-cause cache.
- **On main:** Metrics registry, query builder, **segment selection algorithm** (union count), **new ClickHouse cache table**, **pipeline job** (configurable lookback, default 7 days, active only), **API** (read from cache table), UI. Refine as needed.
