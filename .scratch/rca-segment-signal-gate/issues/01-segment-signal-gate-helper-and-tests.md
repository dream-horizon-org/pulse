Status: done (closed retroactively 2026-05-07; helper + tests verified in tree, exercised by issues 02–05)

## Parent

- `.scratch/rca-segment-signal-gate/PRD.md`
- Canonical PRD: `prd/rca-segment-signal-gate-prd.md`

## What to build

Implement a single **segment signal eligibility** deep module (pure helper or small class) that encapsulates the locked contract: **S = |Δerror_rate| + |Δpoor_user_pct|** with **missing deltas treated as 0**, **absolute** values on present deltas, **drop** when **S < threshold**, **keep** when **S ≥ threshold**. The threshold is passed in (default **15** applied by callers once config exists) so this slice stays free of Spring/config wiring.

Deliver **unit tests** only in this slice: boundaries (**14.9** drop vs **15.0** keep), one null + one strong, both null, one zero + one ≥ threshold, negative deltas (absolute behavior). No ClickHouse, no service wiring.

## Acceptance criteria

- [x] Helper exposes a simple API (e.g. compute **S** for a segment, and/or filter a list) usable from interaction and screen RCA without duplicating formula logic.
  - `SegmentSignalGate` (utility class) exposes `computeSignal`, `isEligible`, `filter` with `String... metricKeys` overloads. Default keys = `{ERROR_RATE, POOR_USER_PCT}` (interaction RCA); screen RCA passes `{BAD_FRUSTRATION}`.
- [x] Behavior matches PRD: null/missing delta → **0** for that term; **absolute** on non-null terms; strict **< threshold** drop, **≥ threshold** keep.
  - `absOrZero(Double)` returns 0 for null, `Math.abs` otherwise. `isEligible` is `S >= threshold`, so `S < threshold` drops (issue 03 added explicit 14.9-vs-15.0 boundary tests).
- [x] Unit tests cover the cases listed in the PRD **Testing Decisions** section for the helper.
  - `SegmentSignalGateTest` (29 tests, incl. `CustomMetricKeys` nested class — boundaries, null+strong, both null, zero+over, negative deltas, cross-key isolation, empty-keys, filter-order preservation).
- [x] No changes yet to persistence, `rca-db-audit.py`, or HTTP audit (those are later issues).
  - Helper is pure; persistence/audit wiring landed in issues 02 / 04 / 05.

## Blocked by

None — can start immediately.

## User stories covered

3, 4, 9, 11, 14, 18, 19

## Comments
