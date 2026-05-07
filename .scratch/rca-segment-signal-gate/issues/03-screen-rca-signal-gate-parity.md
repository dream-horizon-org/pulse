Status: done

## Parent

- `.scratch/rca-segment-signal-gate/PRD.md`
- Canonical PRD: `prd/rca-segment-signal-gate-prd.md`

## What to build

Apply the **same** segment signal gate and **same** configurable threshold to **screen-scoped RCA** so **`screen_root_cause_cache`** (and any screen RCA compute path that mirrors interaction RCA) does not diverge in quality rules. Reuse the **single** helper from issue **01**; read threshold from the **same** root-cause config introduced in issue **02**. Filter in memory after deltas exist, before screen cache persistence / equivalent write path.

## Acceptance criteria

- [x] Screen RCA uses the identical **S** formula and threshold semantics as interaction RCA.
- [x] Ordering of kept segments unchanged; drops only.
- [x] Parity verified by unit/integration coverage appropriate to existing screen RCA tests (add minimal test if none exist).

## Decision (resolves prior PRD-clarification blocker)

Blocker (per `progress.txt`): `SegmentSignalGate` was hardcoded to `(error_rate, poor_user_pct)`,
but `ScreenRcaService.computeScreenDeltas` only emits `click_volume / tap_count / rage_count /
dead_count / bad_frustration` — a literal copy would compute `S = 0` for every screen segment
and drop everything at threshold 15.

Resolution: extend `SegmentSignalGate` to accept a configurable metric-key set
(`String... metricKeys`) while keeping the no-arg overloads — which now delegate to
`DEFAULT_METRIC_KEYS = {ERROR_RATE, POOR_USER_PCT}` — so interaction RCA, db-audit, and the
HTTP audit are unchanged. Screen RCA passes `{BAD_FRUSTRATION}` (its "poor user" driver,
i.e. `dead ∪ rage`). Same helper, same threshold, same null-as-zero / abs / order-preserving
semantics. PRD User Stories 5 (parity), 9 (single helper), 14 (in-memory, no extra CH calls),
16 (real divergence) all satisfied.

Single-metric S for screen is consistent with PRD `User Story 4` ("one side of the sum can be
zero when the other is strong enough") — the screen driver set is degenerate to one term, but
the gate inequality `S >= 15` is identical.

## Blocked by

- `01-segment-signal-gate-helper-and-tests.md`
- `02-rootcause-config-and-interaction-cache-gate.md`

## User stories covered

5, 9, 14, 16

## Comments
