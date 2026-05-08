# RCA HTTP audit: failure buckets (triage)

This document aligns human triage with `deploy/scripts/rca-audit.py` and the **root-cause API** payload. It applies to **interaction** RCA (`GET /v1/interactions/{name}/root-cause`) and any consumer that compares that input to LLM output.

## `mode` and primary cohort (rank 1)

- **`flat`:** Segments come from the **flat (1D)** pass only. **Rank 1** is the first segment in the API’s `segments` array (after server sort): highest **problematic mass** within the flat tier, with dimension-order tie-breaks.

- **`hierarchical`:** Legacy wire value for older **cached** rows. New computations that run the **merged** pipeline use **`hybrid`**, not `hierarchical`, when **2D+** hierarchical candidates exist.

- **`hybrid`:** **Merged** pipeline: **2D+** intersection segments first (ordered by **lift**, then tie-breaks), then **flat 1D** segments (ordered by **problematic count**, then dimension order). **`segments[0]` is the headline cohort** for product and audits—**not** “largest `problematic_count`” if a deeper slice outranks flat tiers.

When triaging audit **WARN**s about “primary signal” or segment titles, compare against **`segments` order as returned**, not against an assumed flat-only or count-only ranking.

## Bucket 1 — FAIL (must fix)

Treat as **blocking** for the audit row:

- **Input integrity:** Empty segment dimensions / blank labels (seed or mapping bug).
- **`everything_good` mismatch:** Expected healthy path but segments or recommendations present, or the opposite.
- **Segment count** below documented min (signals missing from the pipeline).
- **Forbidden keywords** in LLM output (eligibility/direction-filter regression).
- **Strict LLM / error-attribution** expectations from `rca-audit.py` for that interaction (missing required signals).

## Bucket 2 — WARN (primary cohort / noise class)

Treat as **needs review**, not automatically a product bug:

- **Noise in input:** Segment would fail the combined signal floor  
  `S = |Δerror_rate| + |Δpoor_user_pct| <` `MIN_COMBINED_DELTA_SIGNAL`  
  (default **15**, overridable via `RCA_MIN_COMBINED_DELTA_SIGNAL`). Under **hybrid**, if this fires on a **non–rank-1** segment, first confirm **rank 1** still matches expectations—the gate applies **after** merge order; **Bucket 2** often means “weak tail” or **stale cache** before recompute.

- **Borderline expectations:** `borderline: true` rows in `docs/rca-expected-outputs.md` / script expectations—missing keyword is **WARN**, not **FAIL**.

- **Segment count high:** More segments than `segments_max`—possible padding or threshold drift; check ordering **mode** (`hybrid` vs `flat`).

## Related scripts

| Script | Role |
|--------|------|
| `deploy/scripts/rca-audit.py` | HTTP + LLM comparison; **WARN** / **FAIL** per checks above. |
| `deploy/scripts/rca-db-audit.py` | ClickHouse `root_cause_cache` **segments** + optional **mode** assertion; wire values `flat`, `hierarchical`, **`hybrid`**. |

## ClickHouse cache

New computations persist **`mode: hybrid`** when the merged list includes **2D+** hierarchical candidates. **`hierarchical`** may still appear on **older** rows until cache refresh.
