# PRD: RCA flat + hierarchical segment merge and ranking

**Triage:** complete — issues `01`–`05` in `.scratch/rca-flat-hierarchy-segment-merge/issues/` shipped (2026-05-08).

## Problem Statement

Pulse Root Cause Analysis (RCA) on interactions produces segments that power both the **HTTP root-cause API** (cached in ClickHouse) and the **RCA LLM report** (via enrichment and `serverRank`). Today, each analysis run is either **flat** or **hierarchical** as a whole: flat walks dimensions with one top bucket per dimension; hierarchical drills down a path and materializes **progressive** slices (including **single-dimension** steps) and may add **flat extras**.

That design creates several product and accuracy gaps:

1. **Actionability vs volume:** Broad single-dimension slices (for example platform) often dominate by **raw problematic volume**, while **deeper** slices are easier to act on but may rank lower or never appear alongside systematic flat coverage.

2. **Redundant semantics:** Hierarchical output can include **one-dimensional** segments that overlap the **role** of flat segments (simple cohort cuts), which complicates ranking and LLM guidance without adding a distinct “intersection” story.

3. **Exclusive modes:** Teams cannot get **both** “one top bucket per dimension” **and** “multi-dimensional drill-down” in a **single** capped segment list without changing the algorithm.

4. **Audit and narrative drift:** HTTP audit and product expectations struggle when **primary cohort** meaning mixes “largest bucket” and “worst vs baseline” without an explicit merged policy.

Stakeholders accept **additional ClickHouse/query cost** in exchange for clearer segment roles and more predictable prioritization.

## Solution

Introduce a **unified segment pipeline** for interaction RCA:

1. **Flat pass (always):** Build **only single-dimension** segments: for each dimension in configured order, take the **top bucket by problematic count** (subject to existing positivity / query rules), until a **flat budget** is reached or dimensions are exhausted. Each flat segment has **exactly one** dimension key in its filter map.

2. **Hierarchical pass (unchanged selection logic):** Run the **existing** hierarchical algorithm, including the **similarity / problematic threshold** (for example ~75% of total problematic count per current configuration) for picking and drilling.

3. **Hierarchical output filter:** Emit **only hierarchical segments with two or more dimensions** (intersection / path depth). **Do not** emit hierarchical **single-dimension** materialized rows—flat owns all 1D cohorts.

4. **Merge and cap:** Concatenate **hierarchical (2D+) first**, then **flat (1D)**. Apply **`maxSegments`** as **top N** on this **final ordered list** (stakeholder accepts that this may drop some flat tail if hierarchy consumes the budget).

5. **Ordering within each tier:**
   - **Hierarchical:** Primary sort by **lift** — segment **problematic rate minus baseline problematic rate** (rates from `problematic_count / volume`, with safe handling for zero volume). **Tie-break:** **more dimensions** wins (more specific intersection).
   - **Flat:** Primary sort by **problematic count** (descending). **Tie-break:** **first dimension in configured order** wins (stable, cheap).

6. **Cross-tier priority:** **All hierarchical (2D+) segments rank above all flat (1D)** segments in the final list before applying top N.

7. **Downstream alignment:** **LLM `serverRank`** (and any narrative rules in the RCA agent) must follow this **final** segment order so rank 1 reflects the merged policy.

8. **Wire / cache semantics:** Define how **analysis mode** is reported when the result is **merged** (for example a new **hybrid** mode value, or documented composite behavior) so API consumers and caches do not misinterpret the payload.

## User Stories

1. As a **Pulse user** viewing RCA for an interaction, I want **multi-dimensional** problem hotspots listed **before** simple one-dimensional cuts when both exist, so that I see **actionable intersections** first.

2. As a **Pulse user**, I still want **one clear top bucket per dimension** in the same report, so that I do not miss **per-dimension** coverage when hierarchy focuses on a narrow path.

3. As an **engineering lead**, I want hierarchical drilling to keep using the **same similarity / problematic threshold** as today, so that **segment selection** remains predictable for operations.

4. As a **data analyst**, I want **flat** segments to remain **purely 1D**, and **hierarchical** contributions to be **2D+ only** after this change, so that **roles** of segments are unambiguous.

5. As a **data analyst**, I want **no duplicate segment keys** between flat and hierarchical tiers by construction, so that **top N** is not wasted on identical filters.

6. As a **PM**, I want the **headline cohort** (rank 1) to prefer **worse-than-baseline concentration** for **deep** segments, so that **lift** drives hierarchy ordering—not only raw volume.

7. As a **PM**, I want **flat** ordering to emphasize **largest problematic mass** per dimension bucket, so that **volume of harm** remains visible in the 1D tier.

8. As a **Pulse user** reading the AI RCA report, I want **`serverRank`** to match the server’s merged ordering, so that the **narrative** matches the **API** segment priority.

9. As a **maintainer**, I want **deterministic** tie-breaks (dimension count; dimension order), so that **regressions** are easy to spot in tests and audits.

10. As a **maintainer**, I want the **combined signal gate** (pre-LLM filter on delta strength) to remain applicable **after** merge where it applies today, so that **weak** segments are still dropped consistently.

11. As a **developer** integrating the **root-cause API**, I want **documented** behavior for **mode** when flat and hierarchical segments appear together, so that **UI badges** and **analytics** stay correct.

12. As a **QA engineer**, I want **seeded** e-commerce / RCA audit scenarios updated or extended to assert **merged** ordering rules, so that **CI** catches ordering regressions.

13. As a **SRE**, I accept **higher query cost** for this pipeline, but I want **bounded** segment count and **unchanged** time windows by default, so that **latency** stays manageable.

14. As a **Pulse user** on a **shallow** hierarchy (few 2D+ segments), I want the report to **still** populate with **flat** segments after hierarchical slots, so that the experience does not look **empty**.

15. As a **tenant admin**, I want **`maxSegments`** to cap the **final** merged list, so that **LLM context** and **UI** remain bounded.

16. As a **maintainer**, I want unit tests for **merge ordering** independent of ClickHouse, so that **ordering rules** are cheap to verify.

17. As a **maintainer**, I want integration-style tests (or existing RCA service tests) to cover **threshold fallback** (no hierarchical pick) **plus** flat merge, so that **edge cases** do not lose segments.

18. As a **documentation reader**, I want **RCA audit / failure bucket** docs updated to describe **hybrid** payloads and **rank 1** meaning under the new policy, so that **WARN/FAIL** triage stays accurate.

19. As a **Pulse user**, I want **example session IDs** per segment to remain populated where supported, so that **drill-down** still works after merge.

20. As a **security-conscious reviewer**, I want **no relaxation** of **project isolation** or **tenant** filters in new queries, so that **multi-tenant** guarantees hold.

21. As a **future maintainer**, I want a **single place** (deep module) encapsulating **merge + sort + cap**, so that **product tweaks** do not scatter across the whole RCA service.

22. As a **data scientist**, I want **baseline** rate used for lift to be the **same interaction baseline** as today, so that **rate − baseline** is comparable across segments.

23. As a **UI engineer**, I want segment **labels** to remain human-readable for both **1D** and **multi-dim** paths, so that **no UX regression** occurs.

24. As a **cron / cache consumer**, I want **cache invalidation** rules unchanged unless **mode** or **payload shape** requires a **version bump**, so that **stale** reports are not silently mixed with new semantics.

25. As a **product owner**, I want an explicit **out-of-scope** list for **v1** of this PRD, so that **scope creep** (new metrics, UI redesign) is controlled.

26. As a **mobile developer** reading RCA output, I want **dimension names** to stay aligned with **OpenTelemetry / Pulse** conventions, so that I can map findings to **SDK** attributes.

27. As a **release manager**, I want **changelog** and **migration notes** if **cache mode** values change, so that **deploy** order is clear.

28. As a **maintainer**, I want **logging** at **debug** for tier counts (hierarchical vs flat) after merge, so that **field** issues are diagnosable without **PII**.

29. As a **Pulse user**, I want **everything_good** behavior unchanged when **baseline** has **zero** problematic sessions, so that **healthy** interactions do not regress.

30. As a **data analyst**, I want **deltas** on each segment to remain **baseline-relative** as today, so that **lift** narratives stay consistent with **metrics**.

31. As a **QA engineer**, I want **rca-db-audit** expectations reviewed after implementation, so that **ClickHouse** segment dimensions still match **audit** dimensions.

32. As a **maintainer**, I want **Checkstyle / coverage** gates satisfied for new code, so that **CI** stays green.

33. As a **consumer** of **OpenAPI** or **DTO** docs, I want **mode** enum values documented, so that **clients** do not deserialize unknown values.

34. As a **Pulse user**, I want **no increase** in **false** “everything good” when strong segments exist, so that **Bucket 1**-class failures do not return.

35. As a **performance engineer**, I want **query count** documented for **worst-case** dimension order length, so that **capacity** reviews are possible.

36. As a **security reviewer**, I want **no new** admin ClickHouse credentials in application code, so that **tenant** isolation holds.

37. As a **maintainer**, I want **feature** behavior behind **clear** configuration defaults matching **today’s** threshold unless explicitly changed, so that **rollout** is controlled.

38. As a **support engineer**, I want **internal runbooks** updated with **rank 1** semantics, so that **customer** questions get **one** official answer.

39. As a **Pulse user**, I want **consistent** segment **ordering** between **refreshed** cache and **new** computation, so that **stale** vs **fresh** does not flip **rank** arbitrarily beyond **data** changes.

40. As a **code reviewer**, I want **small** PRs or **clear** commits separating **algorithm** from **API** enum changes, so that **revert** is safe.

## Implementation Decisions

- **Orchestration:** Extend the interaction RCA segment builder so it runs **flat collection**, **hierarchical collection** (existing threshold behavior), **filters hierarchical to 2D+**, **sorts within tiers**, **concatenates** (hierarchical then flat), then **truncates** to **`maxSegments`**. **Screen RCA** uses the same flow; **`RcaHybridMergeOutcome`** (`mergeForInteraction` / `mergeForScreen`) is the single adapter for **merge + cap + `FLAT`/`HYBRID` mode** so interaction vs screen metric keys cannot drift.

- **Deep module (recommended):** Introduce a small, testable component responsible for **normalizing segment tier**, **comparing segments for sort** (lift, problematic count, dimension count, dimension order index), and **merging + capping**. The RCA orchestration service delegates **merge/sort/cap** to this module; **ClickHouse query orchestration** stays in the existing service layer.

- **Hierarchical materialization:** Stop emitting **intermediate 1D** rows from the hierarchical path into the **merged** list; only **final** path steps with **≥2** dimensions (per dimension map size) are candidates. **Flat extras** inside the old hierarchical routine should be **reconciled**: either **removed** in favor of the global flat pass, or **deduped** by policy—default decision: **global flat pass is canonical** for 1D.

- **Flat budget:** Decide whether flat pass uses **the full `maxSegments`** before hierarchy, or **unbounded collection** then **global top N** after merge. **Recommended:** collect flat segments **up to `maxSegments`** in dimension order (current spirit), then merge with hierarchy and **re-apply** top N so hierarchy can **displace** flat tail—matches stakeholder “hierarchical first, then flat, then top N.”

- **Configuration:** Reuse **`similarityThresholdPct`** (e.g. 75%) for hierarchical picking. Add explicit config only if needed for **minimum hierarchical dimension count** (default 2).

- **Root cause result model:** Add or reuse fields so each segment can carry **tier** (`flat` vs `hierarchical`) for debugging, tests, and optional API exposure—or keep internal-only if API surface is frozen.

- **Analysis mode enum:** Add **`HYBRID`** (or equivalent) **or** document that **`HIERARCHICAL`** now **may include** flat segments—pick one approach and update **cache serialization**, **API DTOs**, and **consumers** (UI, audits, docs).

- **Enrichment / LLM:** Update **segment ordering** used for **`serverRank`** to match **post-merge** order. Prompt text should state that **rank 1** may be **2D+** when hierarchy exists, and **1D** segments may follow. *(Implemented: `RcaReportEnrichmentService.sanitizeForAiReport` assigns ranks in `RootCauseResult.getSegments()` list order — same as cached `GET /root-cause` after merge/gate.)* **Agent:** `pulse_ai/schemas/root_cause.py` accepts **`mode: hybrid`**; `pulse_ai/agents/rca/prompts.py` documents hybrid + **`serverRank`**; see `issues/04-pulse-ai-rca-contract-and-prompts.md`.

- **Combined signal gate:** Apply **after** merged list is formed (or preserve current **per-segment** gate on the **final** list) so behavior stays **consistent** with today’s intent—exact placement is an implementation detail but must be **documented** in tests.

- **Performance:** Accept **additional queries** for always-on flat pass; **no requirement** in this PRD to parallelize beyond existing patterns, but **avoid** accidental **query explosion** (reuse dimension order, existing query builders).

- **Backward compatibility:** **GET root-cause** JSON shape remains **segments array + baseline**; **semantic** change is **ordering**, **cardinality**, and **mode**. **Version** cache column or **migration** only if existing rows **must** be distinguished.

## Testing Decisions

- **Good tests** assert **observable outcomes**: **segment count**, **dimension map sizes** (1 vs 2+), **order** of labels or stable keys, **`serverRank`** order in enrichment output, and **mode** field when exposed—not internal private methods or query string literals.

- **Unit-test** the **merge/sort/cap** deep module with **synthetic** segments (baseline + metrics maps) covering: empty hierarchy, empty flat, ties on lift, ties on problematic count, tie-break dimension count, tie-break dimension order, **top N** truncation cutting only flat tail, **top N** cutting hierarchy when hierarchy is huge.

- **Service-level tests** (existing RCA service test style): scenarios where **hierarchical** fails first pick (pure flat today) **plus** scenarios with **successful** hierarchy, asserting **merged** behavior and **no 1D** hierarchical rows in output.

- **Enrichment tests:** Verify **`serverRank`** matches merged order and preserves ranks under **session evidence** fetch.

- **Prior art:** Existing **RootCauseService** tests, **RcaReportEnrichmentService** tests, and **RCA audit / e2e** scripts as **regression** harnesses after seed or expectation updates.

## Out of Scope

- Changing **definitions** of **problematic_count**, **error_rate**, **poor_user_pct**, or ClickHouse metric expressions.

- Replacing the **combined delta signal gate** thresholds or formula unless a **regression** forces a follow-up.

- **UI** redesign of the RCA screen (beyond consuming **reordered** segments if already generic).

- **Screen-level RCA** (non-interaction) parity—this PRD targets **interaction** RCA unless implementation naturally shares code.

- **Automatic** dedupe of **overlapping populations** between **different** 2D hierarchical paths (same key only is guaranteed by dimension map equality).

- **Per-tenant** feature flags unless already standard—default is **global** behavior change.

- **LLM prompt** rewrite beyond **ranking / tier** semantics and **minimum** clarity updates.

## Further Notes

- **Open decision:** Whether **flat collection** runs with an **independent sub-cap** (e.g. reserve K slots for flat) vs **pure global top N** after merge; current stakeholder preference is **global top N** with **hierarchical first**.

- **Resolved (v1):** **Cache / API** uses explicit wire value **`hybrid`** for merged 2D+ + 1D output — see `RootCauseAnalysisMode.HYBRID`, `docs/rca-http-audit-failure-buckets.md`.

- **Module confirmation:** Implementers should **review** the proposed **merge/sort/cap** extraction with maintainers during triage; tests should follow **module** boundaries agreed there.

- **Documentation:** RCA HTTP audit failure buckets: **`docs/rca-http-audit-failure-buckets.md`** (**rank 1** / **hybrid** / WARN vs FAIL).
