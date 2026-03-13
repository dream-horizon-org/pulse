---
name: Root Cause Analysis - Alternate Approach (Count-Similar-to-Total)
overview: "Root Cause Analysis (alternate): from the entire interactions pool, find segments whose problematic count (poor + error) is similar to total; hierarchy by 'closest to total' with flat fallback. Total segments capped at 3–4. This is the plan to implement; threshold-based plan is reference only."
todos:
  - id: alt-phase1
    content: "Phase 1: otel_traces schema; metrics registry; query builder; union problematic count; configurable similarity threshold and lookback; ClickHouse cache table"
    status: pending
  - id: alt-phase2
    content: "Phase 2: Segment selection (count-similar-to-total); hierarchical + flat fallback; baseline/segment queries and deltas; read-through API"
    status: pending
  - id: alt-phase3
    content: "Phase 3: Root Cause view under Critical Interaction Details; render segments and metric tables; loading and edge cases"
    status: pending
isProject: false
---

# Root Cause Analysis – Alternate Approach (Count-Similar-to-Total)

This plan is the **one to implement** for Root Cause Analysis. The threshold-based approach in [root-cause-analysis-algorithm-and-pipeline.plan.md](root-cause-analysis-algorithm-and-pipeline.plan.md) is reference only.

**Core idea:** From the **entire interactions pool** (all spans for that interaction in the lookback window), we check **one dimension** at a time. For each dimension value, we ask: *Is this segment's (poor + error) count **similar to** the total (poor + error) count for that interaction?* If yes, that segment **contains almost all of the problem** — we have found where the issue lives. We then build a **hierarchy** by adding dimensions within the chosen segment, always comparing to **total** problematic count (not parent). **Dimension order** is not fixed: at each step we pick the dimension value whose error+poor count is **closest to** total error+poor count.

---

## Scope

- **Output:** Same as the other plan — **Top Contributing Segments** only, each with **Metric | Value | Baseline | Delta** (APDEX, Error Rate, Poor User %, Duration P50/P95, Crash Rate, ANR Rate, Frozen Frame Rate, Slow Frame Rate, Volume).
- **Criterion:** Segment is chosen when its **problematic count** (poor + error; union or sum — a span is either poor or error, so both are equivalent) is **similar to** the **total** problematic count:
  - **First dimension:** Pick the dimension value whose segment's error+poor count is **closest to** total error+poor count (and ≥ similarity threshold % of total).
  - **Adding dimensions:** Sub-segment count is always compared to **total** problematic count (not parent). Only criterion: segment error+poor count ≥ X% of total; no extra minimum-volume rule.
  - **"Similar"** is **configurable** (e.g. segment count ≥ X% of total; default **75%**).
- **Segment cap:** Total segments in the response are always **≤ 3–4** (hierarchy + flat combined). When adding "top 1 per remaining dimension," add only until total is 3–4 (by dimension order).
- **Tie-breaking:** When two dimension values are equally "closest to total," use **dimension order** (fixed order of dimensions; prefer the value from the earlier dimension).
- **Analysis window:** **Last N days (lookback) ending on `date`.** When `date` is not provided, use **today (UTC)** for the cache key and as the end date of the window.
- **Labels:** Backend sends **label** strings. **Hierarchical:** e.g. `"Android + App 3.4.5 + Jio"`. **Flat:** `"Platform: Android"` (i.e. `DimensionName: Value`). Frontend decides how to display; display can change later.
- **Dimension order:** Single fixed list (Platform, OsVersion, AppVersion, DeviceModel, NetworkProvider, GeoState) used for tie-breaking and for adding flat segments when deepening stops; **no separate "remaining dimensions" list**.
- **Data assumption:** Dimension values are always populated (no null/unknown) in the data.
- **Modes:** (1) **Hierarchical** when some dimension value's segment has count similar to total (and sub-segments stay similar when adding dimensions). (2) **Flat** when no segment reaches the similarity threshold: per dimension (in dimension order), take the value with **highest problematic count**; output **top 1** segment per dimension, capped at 3–4 total.
- **Data:** **Entire interactions pool** — we do not pre-filter to "problematic" spans; we compute counts over all interaction spans and compare segment (poor+error) count to total (poor+error) count. Same data source: `otel_traces` with configurable lookback (default 7 days).
- **Cache and API:** Same as the other plan — **on-demand read-through**: first request computes, writes to ClickHouse cache table, subsequent requests within TTL (e.g. 24h) served from cache. No cron.

---

## Algorithm (Alternate: Count-Similar-to-Total)

1. **Total problematic count**  
   Over the interaction and lookback window, compute **total problematic count** = count of spans that are **error OR poor** (union; a span is either poor or error, so union and sum are equivalent). This is the reference "total" for the whole interaction.

2. **Configurable parameters**  
   - **Similarity threshold** (default **75**): segment's problematic count must be ≥ this % of total to be "similar." One config for both first dimension and add-dimension (e.g. `root_cause.similarity_threshold_pct`).
   - **Lookback** (default **7** days): the analysis window is **last N days ending on `date`**. When `date` is omitted, use today (UTC). **Cache TTL** (e.g. 24h). Same as the other plan.

3. **First dimension**  
   For each dimension (in **dimension order**), for each value, compute **segment problematic count** (poor + error). Pick the dimension value whose segment count is **closest to** total (and ≥ similarity threshold % of total). **Tie-break:** by dimension order (prefer earlier dimension).
   - **If some dimension value's segment has count ≥ similarity threshold % of total:** Pick that one. Continue to step 4 (hierarchy).
   - **If no segment is "similar to total":** Use **flat mode** — for each dimension (in dimension order), take the value with **highest problematic count**; output **top 1** segment per dimension, **capped at 3–4 segments total**. Stop.

4. **Adding dimensions (hierarchy)**  
   Within the current segment, consider the next dimensions in **dimension order**. **Reference is always total** (step 1). At each step, pick the dimension value whose sub-segment count is **closest to** total (and ≥ threshold). **Tie-break:** by dimension order.
   - Only add a dimension if some sub-segment's count is ≥ similarity threshold % of **total**. **Total segments (hierarchy) are capped at 3–4**; stop adding when cap is reached.
   - If **no** sub-segment meets the threshold: **stop deepening** and add flat segments: for each dimension not yet in the path (using the **same dimension order**), take the value with **highest problematic count**; add **top 1** segment per such dimension **until total segments ≤ 3–4**. Then stop. Otherwise repeat until cap or no further dimension can be added.

5. **Result**  
   - **Hierarchical:** Output the path (e.g. Android → Android + App 3.4.5 → Android + App 3.4.5 + Jio), **at most 3–4 segments**, each with Metric | Value | Baseline | Delta. Labels e.g. `"Android + App 3.4.5 + Jio"`.
   - **Flat:** Output **top 1** segment per dimension (in dimension order; per-dimension choice = **highest problematic count**), **capped at 3–4 segments**. Labels e.g. `"Platform: Android"`. When deepening stops, same rule for added flat segments until **total ≤ 3–4**.

---

## Relationship to the Threshold-Based Plan

| Aspect | Threshold-based plan | This plan (alternate) |
|--------|------------------------|------------------------|
| **Framing** | "Segment must **cover** X% of the problem." | "Segment's problematic **count** is **similar to** total count." |
| **Criterion** | Share of total problematic ≥ threshold (e.g. 75%). | Segment count ≥ X% of total (e.g. 75%). |
| **Math** | Same: segment_problematic / total_problematic ≥ threshold. | Same once "similar" = "≥ threshold % of total." |
| **Data** | All interaction spans; compute problematic (union) and total. | Explicitly "entire pool"; compare **counts** (no rates). |
| **Add-dimension reference** | Sub-segment share vs **total** (in existing plan). | **Always total** (this plan). |
| **Dimension order** | Fixed or configurable list. | **Dynamic:** at each step, pick the value whose count is **closest to** total. |
| **When deepening stops** | Hierarchy only. | Add **top 1 per remaining dimension** (by dimension order), **total segments ≤ 3–4**. |
| **Segment cap** | Not specified. | **Max 3–4 segments** (hierarchy + flat combined). |
| **Tie-breaking** | Not specified. | **Dimension order** (fixed list). |
| **Default date** | Not specified. | **Today (UTC)** when `date` omitted. |

So the **implementation** differs in: closest-to-total selection; tie-break by dimension order; cap at 3–4 segments; default date today (UTC); labels from backend, display by frontend.

---

## Phase 1 – Data and metrics foundation

Same as the main plan:

- **Schema:** `otel_traces` with required columns and attributes (including frozen/slow frame).
- **Metrics registry:** volume, apdex, error_rate, poor_user_pct, duration_p50/p95, crash_rate, anr_rate, frozen_frame_rate, slow_frame_rate.
- **Query builder:** Build ClickHouse SELECTs for baseline (no GROUP BY) and segment (GROUP BY dimension(s)); support dimension filters.
- **Problematic count:** Per-segment and total count of spans that are error OR poor (union; span is either poor or error so union = sum). Used for "similar to total." No minimum-volume rule; only criterion is segment count ≥ X% of total.
- **Config:** Similarity threshold (default 75), lookback days (default 7; window = **last N days ending on date**), cache TTL (e.g. 24h), **dimension order** (single fixed list: Platform, OsVersion, AppVersion, DeviceModel, NetworkProvider, GeoState — used for tie-breaking and for flat segments; no separate "remaining" list). Reference is always total. **Max segments:** 3–4.
- **Cache table:** Same as the other plan — e.g. `otel.root_cause_cache` with tenant_id, project_id, interaction_name, date, mode, baseline (JSON), segments (JSON), cached_at.

---

## Phase 2 – Segment selection and read-through API

- **Algorithm in code:** Implement the count-similar-to-total logic above (first dimension → hierarchy, always vs total; at each step pick value whose count is closest to total; when deepening stops, add top 1 per remaining dimension). Reuse same baseline/segment query builder and delta calculation.
- **API:** Same read-through: `GET /v1/interactions/:name/root-cause`; check cache; on miss/expiry compute (using this algorithm), write cache, return. Single-flight per cache key is **deferred** for now. **Feature flag:** Root Cause is behind an **environment variable** (e.g. `ROOT_CAUSE_ENABLED`); when disabled, return 404 or do not register the route.
- **API contract:** `GET /v1/interactions/:name/root-cause` with optional query param **date**; when omitted, use **today (UTC)** (window = last N days ending on that date). Response: baseline, segments (with **label** — hierarchical e.g. `"Android + App 3.4.5 + Jio"`, flat e.g. `"Platform: Android"`; frontend decides display), dimensions, metrics, deltas, mode (hierarchical | flat), cachedAt. **Max 3–4 segments.** Include **everythingGood** and **message** for edge cases. Caller always has a valid interaction; no 404 for unknown interaction.

---

## Phase 3 – UI

Root Cause view under Critical Interaction Details; call API; render Metric | Value | Baseline | Delta per segment; handle loading (first request may be slow), error, and edge cases (no data, everything good). **Display:** Show only **"Data as of &lt;cachedAt&gt;"**. Segment **label** from backend: hierarchical e.g. `"Android + App 3.4.5 + Jio"`, flat e.g. `"Platform: Android"`; frontend decides how to render. **Feature flag:** Show the Root Cause tab only when the same **environment variable** (e.g. `ROOT_CAUSE_ENABLED`) is enabled; hide tab when disabled.

---

## Cache table schema and algorithm output

Same as [root-cause-analysis-algorithm-and-pipeline.plan.md](root-cause-analysis-algorithm-and-pipeline.plan.md): same cache table columns and primary key; same algorithm output structure (mode, baseline, segments with label, dimensions, metrics, deltas). Only the **rule** for which segments are chosen differs (count-similar-to-total, always vs total; closest-to-total selection; top-1-per-remaining when deepening stops).

**Edge-case outputs (locked):**
- **No data:** Total **volume** = 0 (no interaction spans in lookback window) → "no data available" / "NA" (e.g. `noDataAvailable: true`, `message`).
- **Everything good:** Volume > 0 and **total problematic count = 0** → `everythingGood: true`, `segments: []`, optional `message`. API response includes `everythingGood` and `message` (or equivalent) so UI can show the right state.

---

## Implementation order

1. **Phase 1:** Schema; metrics registry; query builder; union problematic count; config (similarity threshold, lookback, TTL); cache table.
2. **Phase 2:** Segment selection (count-similar-to-total, hierarchical + flat); read-through API.
3. **Phase 3:** UI view and API integration.

---

## Acceptance / test scenarios

Use these to validate the implementation:

1. **No data** — No interaction spans in the lookback window (total volume = 0). **Expect:** `noDataAvailable: true` (or equivalent), message "no data available" / "NA," no segments.

2. **Everything good** — Total volume > 0, total problematic count = 0. **Expect:** `everythingGood: true`, `segments: []`, optional message "everything is good."

3. **Single dominant segment (hierarchy)** — One dimension value has e.g. ≥75% of total problematic count. **Expect:** Hierarchical mode; at least one segment (that value); total segments ≤ 3–4; baseline and deltas correct.

4. **No segment meets threshold (flat)** — No dimension value has ≥75% of total (e.g. 40%, 30%, 30%). **Expect:** Flat mode; per dimension (in order) take value with **highest problematic count**; top 1 segment per dimension, **capped at 3–4 segments**; labels e.g. `"Platform: Android"`; each with metric table and deltas.

5. **Hierarchy then stop + flat** — First dimension meets threshold; at least one sub-segment meets threshold; then no further sub-segment meets it. **Expect:** Hierarchical path plus "top 1 per remaining dimension" by dimension order, **total segments ≤ 3–4**.

6. **Segment cap** — Any case that would produce >4 segments. **Expect:** At most **3–4 segments** in the response (by dimension order when capping).

7. **Labels** — Backend returns segments with `label`: hierarchical e.g. `"Android + App 3.4.5 + Jio"`, flat e.g. `"Platform: Android"`. **Expect:** Frontend receives labels and can render as-is or reformat.

8. **Cache and date** — Request without `date` for an interaction that has data "today" (UTC). **Expect:** Window = last N days ending today (UTC); cache key = today (UTC); first request computes and caches; second request within TTL served from cache.

9. **Feature flag** — When env (e.g. `ROOT_CAUSE_ENABLED`) is disabled. **Expect:** API returns 404 or route not registered; UI hides Root Cause tab.

---

## Open decisions (deferred or optional)

- **Cache table TTL:** No table-level TTL for now; expiry is enforced in the API (e.g. serve from cache only if `cached_at` within last 24h). Table-level TTL left as an open point for later.
- **Single-flight per cache key:** Deferred for now.
- **Timeout/error handling:** Deferred; no explicit timeout or error-response behaviour in this plan.

**Feature visibility (locked):** Root Cause is behind an **environment variable** (e.g. `ROOT_CAUSE_ENABLED`). When disabled: backend returns 404 or does not register the route; UI hides the Root Cause tab.

---

## Key files and references

- **Other plan:** [root-cause-analysis-algorithm-and-pipeline.plan.md](root-cause-analysis-algorithm-and-pipeline.plan.md) — threshold-based; same phases and cache/output shape.
- **Spec:** [doc/root-cause-analysis-plan.md](doc/root-cause-analysis-plan.md) — metrics, delta formula, edge cases.
- **Backend:** ClickhouseQueryService, ClickhouseConstants, ClickhouseMetricService, InteractionDao; ClickHouse schema in backend/ingestion.
- **UI:** CriticalInteractionDetails.
