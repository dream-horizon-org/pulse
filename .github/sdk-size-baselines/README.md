# Mobile SDK size baselines

Committed byte sizes for reference debug builds. PR CI compares measured artifacts against these files.

## Threshold

- **25 KB** (`thresholdBytes`: **25600**) per artifact — fail if `measured - baseline > 25600`.

## Labels

| Label | When |
|-------|------|
| `sdk-size-delta` | Required when the PR changes dependency manifests under mobile SDK paths. Triggers builds. |
| `sdk-size-baseline-update` | After measurement shows **> 25 KB** growth; CI commits updated JSON to the **PR branch**. |

## Behavior summary

| PR manifest changes? | `sdk-size-delta`? | CI |
|---------------------|-------------------|-----|
| No | No | **Pass** automatically (`auto_pass`) |
| Yes | No | **Fail** — add label |
| Yes | Yes, Δ ≤ 25 KB | Build + **pass**; baselines on `main` unchanged |
| Yes | Yes, Δ > 25 KB | **Fail** until baseline-update path |
| Yes | Yes + `sdk-size-baseline-update` | CI updates JSON on PR → merge promotes to `main` |

## Approval (> 25 KB baseline bump)

When baseline files change, require **≥1** approval from:

- `@kunalchavhan`
- `@anirudhdream11`
- `@chiragSharmaD11`

## Seed baselines on `main`

Before enabling the required check **Mobile SDK size delta**, run once on `main`:

1. Actions → **Mobile SDK size delta** → **Run workflow** → Branch `main` → Job **seed-baselines**.
2. Verify committed `bytes` are non-zero in `android.json`, `ios.json`, `rn.json`.

## Branch protection (maintainers)

On `main`:

1. Require status check **Mobile SDK size delta**.
2. Require review from Code Owners when `/.github/sdk-size-baselines/**` changes.
3. Allow GitHub Actions to push to PR branches (for `update_baselines`).

## Validation checklist (W1–W15)

See plan: code-only auto-pass; dep PR needs label; Δ boundary 25600/25601; baseline-update + one approver; merge promotes baselines (W15).
