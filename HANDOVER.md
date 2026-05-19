# ClickHouse Rollup Framework — Handover / Open Questions

**Audience:** anyone picking this proposal up to drive it forward (engineering lead, platform owner, on-call backend).
**Companion doc:** `PROPOSAL.md` in this folder.
**Last updated:** 2026-05-19.

This document is **the list of unresolved decisions**. Each one materially changes the framework's shape, storage cost, or operational risk. Answer these before implementation begins.

For each question:
- **Why it matters** — what changes based on the answer.
- **Default we'd take** — what we'd ship if no one answers.
- **Decision needed** — what to fill in.

---

## Decisions already made

These are locked, recorded here so reviewers don't relitigate them:

| Topic                     | Decision                                                                                  |
| ------------------------- | ----------------------------------------------------------------------------------------- |
| Storage layout            | "Right path" — multiple grains (15s / 1m / 5m / 1h / 1d), not the cheap two-grain version |
| Quantile algorithm        | `quantileExactState` default; `quantileTDigestState` documented in YAML as comment        |
| Registry format           | YAML, parsed into Java records at boot; managed via PR review                             |
| Migration model           | YAML → migration utility reconciles ClickHouse. No ORM. No DDL→Java codegen. No Java→DDL codegen. |
| Backfill                  | Out of scope for the migration tool itself; tool prints SQL, operator runs it manually   |
| API shape                 | No UI changes. DTOs unchanged. Existing per-domain DAOs swap raw SQL for `GenericRollupReader.query(rollup, time range, columns, filters)` |
| Card binding              | No UI-side card registry; out of scope                                                    |

---

## Open questions

### Q1. Cardinality budget — which dims are allowed in registry rollups?

**Why it matters.** Per-user / per-session / per-URL / per-trace dimensions explode rollup row counts (especially at 15s grain) and defeat the purpose. We need a hard rule.

**Default we'd take.** Registry dims must be low-cardinality:
`ProjectId, ScreenName, AppVersion, Platform, Country, DeviceModel, NetworkType, OsVersion, StatusBucket, Breakpoint`.
Anything per-user, per-session, per-URL, per-trace = raw table only, not rollup-eligible.

**Decision needed.**
- [ ] Confirm the allowlist above, or
- [ ] Provide a different one, or
- [ ] State an explicit cardinality cap (e.g. "no dim with > N distinct values per project per day") and enforce it in the registry parser.

---

### Q2. Grain × retention matrix

**Why it matters.** Storage cost is dominated by the finest grain × longest retention. Wrong numbers either blow up disk or leave gaps cards expect.

**Default we'd take.**

| Grain | Retention |
| ----- | --------- |
| 15s   | 24 h      |
| 1m    | 7 d       |
| 5m    | 30 d      |
| 1h    | 90 d      |
| 1d    | 730 d     |

**Decision needed.**
- [ ] Approve the table above, or
- [ ] Adjust per row — write the new numbers below.

Notes from product / leadership on which retention windows are *contractually* expected on which screens would help. Today raw retention drives card behavior; rollups will replace it for trends.

---

### Q3. "Custom" metrics — compile-time only, or runtime-defined?

**Why it matters.** The framework as proposed assumes all metrics are defined in YAML, reviewed in PR, applied at deploy. A "users/PMs define new metrics from the dashboard at runtime, framework codegens MVs on the fly" world is **a different system**, ~10× the work, and introduces governance, cardinality-attack, and correctness issues.

**Default we'd take.** Compile-time only. The "custom" framing in the original ask is interpreted as "hand-curated by us, not OTel-native" — not "defined dynamically by end users."

**Decision needed.**
- [ ] Confirm compile-time only is fine.
- [ ] If runtime-defined is required, this proposal needs significant rework. Flag it now.

---

### Q4. Late / out-of-order data — watermark for rollup vs raw fallback

**Why it matters.** Mobile SDKs buffer offline, and events can arrive hours or days late. AggregatingMergeTree merges these correctly on insert, but the *reader* needs to know how recent a window can be served from rollup vs needs to fall back to raw.

**Default we'd take.** Reader serves rollup for `bucket < now() - 2h`, falls back to raw + `UNION ALL` for the trailing 2 hours. 2h is a guess.

**Decision needed.**
- [ ] What is the real SDK buffer window in production today? (mobile + web, p95 and p99)
- [ ] Is "trailing window from raw, rest from rollup" acceptable, or do we need rollup-only and accept some skew on recent buckets?

---

### Q5. MV sharing strategy — one MV per dim set, not one per card

**Why it matters.** "One MV per card" multiplies insert amplification on `otel_traces` / `otel_logs` and is the fast path to slowing raw ingestion. "One MV per (source, dim set, grain)" with multiple cards reading the same MV keeps insert amplification bounded.

**Default we'd take.** One MV per `(source, dim set, grain)`. Cards share. We expect ~20–30 MVs total across all sources × grains, not hundreds.

**Decision needed.**
- [ ] Confirm shared-MV model.
- [ ] Confirm "no per-card MVs" as a hard rule the registry parser enforces.

---

### Q6. Dim-set evolution — accept generous upfront dim sets?

**Why it matters.** Adding a *metric* to an existing rollup is cheap (`ALTER ADD COLUMN` + backfill). Adding a *dim* changes the MV's `GROUP BY`, which means a new target table, dual-write window, and cutover. Expensive and rare.

**Default we'd take.** Design dim sets generously up-front (include any dim a card *might* reasonably want for that source). Accept some over-keying. Reserve dim-set changes for breaking quarterly cleanup.

**Decision needed.**
- [ ] Confirm "design generously, change rarely."
- [ ] Or define a process for dim-set changes (who approves, how cutover is staged).

---

### Q7. Distinct counts — `uniqState` / `uniqCombined64State` in scope?

**Why it matters.** DAU / unique sessions / unique users are common card metrics. They need their own state columns, separate from sum/count. Adding them roughly doubles per-metric storage cost on those rollups (vs. sum/count alone).

**Default we'd take.** In scope. Use `uniqCombined64State` (cheap, ~1.6% error) by default, document `uniqExactState` as a comment alternative for low-cardinality cases where exact matters.

**Decision needed.**
- [ ] Confirm uniq is in scope.
- [ ] Confirm `uniqCombined64State` as default.

---

### Q8. Filter pushdown — what happens when a card filters on a non-registered dim?

**Why it matters.** Today cards can filter on any column. After rollups, only registered dims are available on the rollup. A card filtering on `custom_attr` must either fail loudly or fall back to raw. Silent fallback is the worst option (slow and surprising).

**Default we'd take.** Generic reader rejects filters on non-registered dims with a clear error. Caller decides whether to (a) drop the filter, (b) hit a separate `/raw` query endpoint, or (c) request the dim be added to the registry.

**Decision needed.**
- [ ] Confirm reject-with-error.
- [ ] Or specify automatic fallback to raw (with caveats).

---

### Q9. DTO mapping shape — keep per-domain DTOs, or unify?

**Why it matters.** The proposal keeps per-domain DTOs unchanged (`WebVitalsTrendDto`, `NetworkTrendDto`, etc.) and just swaps the SQL layer. An alternative is a generic `MetricSeriesResponse { dims, buckets, values }` DTO with thin per-domain adapters. Cleaner long-term, more refactor now.

**Default we'd take.** Keep per-domain DTOs. Lowest churn. Matches "no UI changes" constraint.

**Decision needed.**
- [ ] Confirm per-domain DTOs stay as-is.
- [ ] Or schedule a separate phase for DTO unification.

---

### Q10. Multi-source MVs — supported from day 1?

**Why it matters.** Some existing rollups (`session_summary`) merge 3 sources (`otel_traces`, `stack_trace_events`, replay events) into one target via 3 MVs. Supporting that pattern in the framework adds complexity (multiple MV definitions per rollup target, validation rules).

**Default we'd take.** Day 1 supports **one source per rollup**. Multi-source rollups (`session_summary` and similar) stay hand-written as today and are a candidate for migration in Phase 2+.

**Decision needed.**
- [ ] Confirm single-source-per-rollup for v1.
- [ ] Or commit to multi-source support upfront (more design work needed).

---

## Lesser unknowns (can be answered during implementation)

These can be deferred to the implementation phase but should be acknowledged:

| Topic                          | Default plan                                                                |
| ------------------------------ | --------------------------------------------------------------------------- |
| Row policies on rollup tables  | Mirror raw-table per-project policies, since `ProjectId` is in order key    |
| Naming convention              | `rollup_<source>_<dimset>_<grain>` for tables; `mv_<same>` for MVs          |
| YAML location                  | `backend/server/src/main/resources/metrics/*.yaml`                          |
| Migration tool location        | New module `backend/clickhouse-migrate/` invoked from `deploy/scripts/`     |
| Validation                     | Migration tool runs `diff` in CI on PRs touching `metrics/*.yaml`           |
| Read-side query cache          | Reuse existing `use_query_cache = true` setting on rollup queries           |
| Reader time-out / row caps     | Inherited from existing ClickHouse client constants                         |

---

## What to do with this document

1. Owner of the framework picks it up and walks through Q1–Q10 with the relevant stakeholders (platform, product, on-call).
2. Each `[ ]` becomes either a confirmed default or a written-in alternative.
3. Once Q1–Q10 are answered, update `PROPOSAL.md` to fold the answers in, then start the proof-of-concept (web vitals trend rollup end-to-end).
4. Until then, **no implementation work** beyond exploratory spikes.
