Status: done

## Parent

- `.scratch/rca-segment-signal-gate/PRD.md`
- Canonical PRD: `prd/rca-segment-signal-gate-prd.md`

## What to build

Update **`rca-db-audit.py`** so **[NOISE]** / **[WEAK]** (or equivalent) assertions use the **combined S rule** and the **same default threshold** as pulse-server (**15** unless overridden). Replace the legacy **dual independent** `noise_threshold_err` / `noise_threshold_poor` failure logic for those checks, unless an explicit **legacy mode** flag is retained (PRD prefers removal; optional legacy is product choice — document in script `--help` if kept). v1: **one global** threshold in audit config matching server default; per-interaction overrides only if already required elsewhere.

## Acceptance criteria

- [x] Audit noise classification matches server semantics: **S = |Δerror_rate| + |Δpoor_user_pct|**, missing treated as **0**, **absolute** terms.
- [x] Default threshold aligns with server default (**15**) after issue **02** lands; document how to override for local experiments. (`MIN_COMBINED_DELTA_SIGNAL`, env `RCA_MIN_COMBINED_DELTA_SIGNAL`; banner prints active value.)
- [x] Strict dimension / keyword expectations in db-audit remain unchanged (orthogonal to **S**). Removed only the per-interaction `noise_threshold_err` / `noise_threshold_poor` keys.
- [x] Running `python3 deploy/scripts/rca-db-audit.py` is still the primary Bucket C verification after re-seed + cache recompute.

## Blocked by

- `02-rootcause-config-and-interaction-cache-gate.md` (source of truth for default threshold / env naming to mirror in docs or shared constants if any)

## User stories covered

6, 7, 17

## Comments
