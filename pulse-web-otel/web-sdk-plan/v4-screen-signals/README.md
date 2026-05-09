# Screen Navigation Signals (v4-screen-signals)

Phase 0–3 complete: research, design, ADR, grill. **Locked. Ready for PRD + issues + implementation.**

## Status

- ✅ Phase 0: Research & stop-condition answers (signal type, flush, gate)
- ✅ Phase 1: Touchpoints matrix (files touched, cross-package scope)
- ✅ Phase 2: Rejected alternative (PLAN-A: metrics approach)
- ✅ Phase 3: ADR + PLAN-B + design summary
- ✅ Phase 3b: **Grill completed** (design locked, no blockers)
- ⏳ Phase 4–5: Implementation + testing (ralph/loop.sh)

## Active docs

| Doc | Purpose | Audience |
|---|---|---|
| **[FINAL-PLAN.md](./FINAL-PLAN.md)** | **HOLY GRAIL** — all design decisions + strategy | **Start here for implementation** |
| **[TDD-MANDATE.md](./TDD-MANDATE.md)** | **MANDATORY** — test-first development workflow + issue template | **Ralph + all implementers** |
| [DESIGN.md](./DESIGN.md) | What + why + quick checklist | Decision makers / onboarding |
| [PLAN-B-screen-navigation-spans.md](./PLAN-B-screen-navigation-spans.md) | Lifecycle + attributes + 20+ hardened test cases | Implementers |
| [ADR-screen-navigation.md](./ADR-screen-navigation.md) | Decision record + grill summary | Architects / reviewers |
| [GRILL-SESSION-NOTES.md](./GRILL-SESSION-NOTES.md) | Q&A log + findings | If questions arise / context |
| [03-touchpoints-matrix.md](./03-touchpoints-matrix.md) | Files touched (cross-package) | Project leads |
| [01-research-ecosystem.md](./01-research-screen-signals-ecosystem-and-industry.md) | Industry patterns + OTel alignment | Context / alternatives |
| [02-research-otel-pulse.md](./02-research-otel-js-browser-and-pulse-sdk.md) | SDK integration points | Implementers |

## Next gates

1. ✅ **Grill** — Completed. All questions resolved, no blockers.
2. ⏳ **PRD** — `/to-prd-ralph` (spec + cross-package tasks)
3. ⏳ **Issues** — `/to-issues-ralph` (vertical slices + dependencies + evals)
4. ⏳ **Ralph loop** — `./ralph/loop.sh` (implementation + tests)
5. ⏳ **Review** — `/review` on branch

## Grill checklist (completed)

Grill session resolved all critical questions:

- ✅ **Why split spans?** — Different attributes + analytics + parity (GRILL-SESSION-NOTES.md §Q1)
- ✅ **TTI on SPA nav?** — No. No industry standard, sync rendering (GRILL-SESSION-NOTES.md §Q2)
- ✅ **Web vitals separate?** — Yes, GA4 model (GRILL-SESSION-NOTES.md §Q3)
- ✅ **Lifecycle** — Initial load + SPA nav + pagehide all covered (FINAL-PLAN.md §Lifecycle)
- ✅ **Flush timing** — `loggerProvider.forceFlush()` on visibility + pagehide (FINAL-PLAN.md §Lifecycle)
- ✅ **Screen.name resolution** — 4-step fallback locked (FINAL-PLAN.md §Screen name resolution)
- ✅ **Android parity** — All items verified (FINAL-PLAN.md §Android parity checklist)
- ✅ **Double install** — `installAllCompleted` guard (FINAL-PLAN.md §Feature gate & consent)
- ✅ **Consent** — Gates install + export (FINAL-PLAN.md §Feature gate & consent)
- ✅ **SSR safe** — `typeof window === "undefined"` no-op (FINAL-PLAN.md §Feature gate & consent)
- ✅ **Deferred** — BFCache, hash routes, web vitals, remote sampling listed (FINAL-PLAN.md §Deferred)
- ✅ **Cross-package** — Backend `Features.java`, UI ready, ClickHouse ready (FINAL-PLAN.md §Cross-package)

**All ✅ locked. No blockers.**

## Deferred to follow-ups

- **Web vitals per screen** (next phase after this lands)
- **Hash routes** (`/#/...` support requires config)
- **BFCache** special handling
- **Remote sampling** (per-screen sample rates)

## Contacts

- **Decision owner**: Jatin (this sprint)
- **Implementation**: Ralph agent (loop)
- **Review**: Team code review
