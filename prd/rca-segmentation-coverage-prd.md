# PRD: RCA segmentation coverage (docs, seeds, audits, tests)

**Status:** Draft for local planning (no GitHub issue created).  
**Canonical path:** `prd/rca-segmentation-coverage-prd.md`  
**Source:** RCA segmentation follow-through plan + grill-me locked decisions.  
**Related docs:** RCA segmentation algorithm, RCA segmentation scenarios catalog, RCA E2E test cases.

---

## Problem Statement

Teams cannot reliably tell whether **root-cause segmentation** (how Pulse slices problematic sessions by platform, OS, app version, device, network, geo) behaves as designed after changes to seeds, prompts, or backend logic. Today, expectations are partly encoded in scripts and prose, but **coverage against the full segmentation checklist is incomplete**, **interaction names in human docs diverge from seed and audit names**, and **pre-LLM cache segments** are not asserted with the same rigor the team wants for **`dimensions`** maps. Without a single traceability story, regressions slip through as “LLM weirdness” when the root cause is **ClickHouse input or segmentation**.

---

## Solution

Deliver a **closed loop** from segmentation specification → **traceability matrix** → **tuned ecommerce seed cohorts** (existing interactions only) → **stricter ClickHouse cache audit** (exact `dimensions` maps after stable sort, plus `mode` for a small stable interaction set) → **targeted unit tests** for edge cases best expressed with mocked analytics queries → **documentation and agent skill links** so developers and automation use the same vocabulary. **LLM-facing audit** remains tolerant (counts, keywords, forbidden phrases) so prompt iteration does not fight strict cache parity.

---

## User Stories

1. As an **RCA engineer**, I want a **single appendix** that maps each segmentation checklist item to unit test, db-audit, and seed ownership, so that I know where to add or fix coverage.
2. As an **RCA engineer**, I want **seed tuning** on existing seeded interactions only (no new interaction rows this pass), so that integration runs reproduce hybrid, hierarchical, flat-extra, healthy, and single-cohort stories without MySQL churn.
3. As an **RCA engineer**, I want **db-audit** to compare **expected vs actual `dimensions` maps** with **exact equality** after a **stable Python sort**, so that cache output is verified precisely without depending on raw segment list order from the backend.
4. As an **RCA engineer**, I want **`mode` (`HIERARCHICAL` vs `FLAT`)** asserted only for a **small stable** set of interactions after seeds settle, so that CI is meaningful without flaking on every cohort tweak.
5. As an **RCA engineer**, I want **`rca-audit` (LLM path)** to **not** require strict `dimensions` parity with cache, so that prompt and narrative changes do not break segmentation verification.
6. As a **QA or dev running E2E**, I want **e2e human docs** to use a **canonical name mapping** (e.g. doc “cart checkout” → seed `checkout_start`), so that I never run audits against the wrong interaction name.
7. As a **developer**, I want **`rca-e2e` skill** to link the **scenarios catalog**, so that when db-audit fails I can read label and `dimensions` rules in one place.
8. As an **RCA engineer**, I want **unit tests** for segmentation edge cases (empty dimension order, tie-breaking, empty segment metrics row, flat-extra semantics), so that checklist items do not all depend on heavy seeds.
9. As an **on-call engineer**, I want **verification steps** documented (re-seed, unit test command, db-audit, optional full db-only e2e), so that I can confirm a change before merge without guessing commands.
10. As a **future maintainer**, I want **out-of-scope** items explicit (full rename of interactions, new synthetic interactions for pure-flat-only), so that scope creep is intentional.
11. As a **product reader**, I want the **scenarios doc** to stay the **human-readable** catalog with examples, and the **matrix appendix** to stay **tabular**, so that I do not wade through one giant narrative.
12. As an **AI agent user**, I want **cross-links** from E2E test case doc to scenarios doc, so that expected segment language in tests aligns with segmentation label rules.
13. As an **RCA engineer**, I want **noise threshold** behavior in db-audit changed only when the matrix calls for it, so that unrelated noise rules do not move in the same change set.
14. As a **contributor**, I want **Java graphify** noted as a post-step only when Java changes land, so that repo graph rules stay satisfied without running graphify for doc-only edits.
15. As a **release owner**, I want **no GitHub issue** requirement for this PRD, so that local planning still has a single artifact to track work (this document or local tracker).

---

## Implementation Decisions

- **Traceability matrix:** Append to the end of the existing **RCA segmentation scenarios** document as a compact table; split to a separate matrix document only if the table grows unwieldy.
- **Naming strategy (locked):** **Documentation mapping only** — human-facing E2E doc gains a canonical names table and aligned headings; **no** full rename of interaction identifiers across MySQL, seed, and services in this effort.
- **Seed scope (locked):** **Tune existing ecommerce interactions** only; defer **new** interaction identifiers (e.g. dedicated “pure flat only”) to a follow-up if still needed after unit tests and matrix gaps are clear.
- **Db-audit expectations:** Evolve from keyword-oriented checks toward a structured **list of expected segments**, each defined primarily by **`dimensions` map**; optional keywords retained only if they improve failure messages.
- **Stable ordering:** Implement **list sorting in the audit script** (not necessarily in the Java service) so that “exact map per index” means **post-sort slot agreement** between expected and actual segment lists; choose one canonical sort key (e.g. lexicographic serialization of dimension keys and values) and document it in testing decisions.
- **`mode` assertions:** Limited to a **small allowlist** of interactions agreed after seed tuning (e.g. healthy baseline, single strong cohort, one hierarchy-heavy case); not required for all seeded interactions until cohorts are frozen.
- **LLM audit script:** Explicitly **out of scope** for strict `dimensions` equality with cache; continues to validate job lifecycle, segment count bands, forbidden terms, and similar **behavioral** checks.
- **Unit tests:** Extend existing **root cause service** test suite with Mockito-driven ClickHouse row fixtures; prefer **new nested test groups** per checklist theme rather than one oversized test method.
- **Deep module (encapsulation):** Keep **comparison and sort** logic inside the **db-audit script** (or a tiny extracted Python helper module colocated with it) so that “how we diff segments” is one place to change; Java segmentation algorithm unchanged unless a later decision requires deterministic server-side ordering.

---

## Testing Decisions

- **Good tests** assert **observable outcomes**: cached segment list’s **`dimensions`** (after documented sort), optional **`mode`**, and absence of disallowed noise segments; unit tests assert **segmentation branch behavior** given controlled query result rows, not private method internals.
- **Modules under test:** Segmentation service (unit), ClickHouse cache read path (db-audit integration against real or docker CH in dev), optionally full **db-only e2e** pipeline when cache is populated.
- **Prior art:** Existing root cause service tests with mocked analytics client; existing db-audit script with per-interaction expectation tables; existing E2E runner invoking db-audit and optional token-based audit.
- **Sort contract:** Document the exact **string or tuple key** used to sort segment lists in the audit script; golden expected lists in the script must use the same ordering convention so failures point to data or segmentation, not comparator drift.
- **Regression policy:** When seed tuning intentionally changes cohorts, **update expected dimension lists** in the same change (or immediately after) so main stays green.

---

## Out of Scope

- **GitHub issue creation** and **`needs-triage` label** automation (local issues only per team setup).
- **Full rename** of interaction names across MySQL, seed data, audit scripts, and UI to match marketing names (e.g. `cart_checkout` everywhere).
- **Adding multiple new interaction** rows solely for segmentation matrix completeness in this pass.
- **Strict LLM output `dimensions` matching** cache segments in `rca-audit`.
- **Changing Java `materializeSegments` label rules** for UX (unless raised as a separate product PRD).
- **Screen RCA** parity work beyond what the matrix explicitly assigns (can be a follow-up row in the appendix).

---

## Further Notes

- **Local issue tracking:** Paste a link to this PRD or its path into your local tracker; no GitHub publish was performed.
- **Grill-me lock-in summary:** Doc mapping; matrix appendix in scenarios doc; seed tune-only; db-audit exact `dimensions` + sort + selective `mode`; rca-audit not strict on dimensions.
- **Risk:** Exact map equality is strict; **sort key** and **segment count** must stay aligned with seeds; when in doubt, extend the matrix row with “borderline” notes before loosening assertions.

---

## Revision history

| Date | Change |
|------|--------|
| 2026-05-06 | Initial PRD from segmentation coverage plan + grill-me decisions. |
| 2026-05-07 | US9 verification ladder published as a copy-paste block in `docs/rca-e2e-test-cases.md` (re-seed → `RootCauseServiceTest` → `rca-generate` → `rca-db-audit` → optional `rca-e2e.py --db-only`). Issue 007 AC#4 closed; AC#1–3 still pending a hands-on stack run. |
