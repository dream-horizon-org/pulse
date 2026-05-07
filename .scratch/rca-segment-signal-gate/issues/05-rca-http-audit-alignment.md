Status: done

## Parent

- `.scratch/rca-segment-signal-gate/PRD.md`
- Canonical PRD: `prd/rca-segment-signal-gate-prd.md`

## What to build

Align **`rca-audit.py`** (HTTP RCA audit) warnings or checks that relate to “noise” or weak segment signal with the **same combined S rule** and threshold as **`rca-db-audit.py`** and the server, so HTTP-level validation is not stricter or looser than cache policy without an explicit documented reason.

## Acceptance criteria

- [x] Any noise / weak-segment logic in `rca-audit.py` uses **S** and the same default as server/db-audit.
- [x] Docstrings or audit output text updated so operators understand the single rule.
- [x] No change to API contracts beyond reflecting fewer segments when running against a server that implements the gate.

## Implementation notes (2026-05-07)

- `deploy/scripts/rca-audit.py`: added module-level `MIN_COMBINED_DELTA_SIGNAL` (default 15.0,
  env `RCA_MIN_COMBINED_DELTA_SIGNAL`) and `_combined_signal(derr, dpup)` mirroring server
  `SegmentSignalGate` + `rca-db-audit.py`.
- `check_input_segments` and `check_output_segments` both replaced their dual
  `noise_threshold_err` / `noise_threshold_poor` floors with a single `S < threshold` WARN
  that prints `S=…  < {threshold:g}`.
- Per-interaction `noise_threshold_err` / `noise_threshold_poor` keys removed from every
  `EXPECTATIONS` entry — single global knob per PRD v1, parity with `rca-db-audit.py`.
- Banner now prints active threshold and env var name.
- `python3 -c "ast.parse(...)"` clean; ralph TS commands (`pnpm`/`tsc`) N/A for this
  Python-only slice.

## Blocked by

- `04-rca-db-audit-combined-s-rule.md`

## User stories covered

17

## Comments
