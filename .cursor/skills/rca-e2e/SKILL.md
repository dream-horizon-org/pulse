---
name: rca-e2e
description: Run the RCA end-to-end test pipeline. Validates the full Root Cause Analysis stack — ClickHouse input segments, LLM report output, and HTTP API — across 17 seeded e-commerce interactions. Use when testing prompt changes, seed data changes, or backend RCA logic changes.
---

# /rca-e2e

You are running the RCA end-to-end test pipeline for the Pulse observability platform.

## What this pipeline validates

The RCA feature takes pre-computed segments from ClickHouse, feeds them to a Gemini LLM agent,
and produces a `RcaStructuredReportV1` JSON report. The pipeline verifies all three layers:

| Layer | Tool | What it checks |
|---|---|---|
| ClickHouse input | `rca-db-audit.py` | Expected bad segments present with sufficient delta signal before the LLM sees them |
| LLM output + HTTP API | `rca-audit.py` | Segment count, keywords, `everything_good`, forbidden keywords, async job pipeline |

**When `rca-db-audit` fails** (segment count, label, `dimensions` map, or sort/mode mismatch), consult:

- [`docs/rca-segmentation-scenarios.md`](../../../docs/rca-segmentation-scenarios.md) — canonical scenarios catalog: label rules, `dimensions` shapes (single / hybrid / hierarchy + flat extras), expected sort + mode per cohort.
- [`prd/rca-segmentation-coverage-prd.md`](../../../prd/rca-segmentation-coverage-prd.md) — segmentation coverage PRD: §9 traceability matrix (interaction ↔ scenario ↔ test), seed-owned vs unit-only rows, strict-audit policy.

## Step 1 — Understand what the user wants

Ask one question (not multiple):

> "Which mode? (1) db-only — check DB state, no token  (2) full — regenerate + audit  (3) seed+full — wipe, re-seed, regenerate, audit  (4) single interaction — which one?"

If the user's message already implies a mode (e.g. "after my prompt fix" → full, "just check" → db-only), skip asking.

## Step 2 — Token handling

For any mode except `db-only`, a JWT is needed. Check in order:
1. `RCA_TOKEN` env var — run `echo $RCA_TOKEN` to check
2. If not set, ask the user: "Please provide your JWT or run `export RCA_TOKEN=<jwt>` first"

Never echo or log the token value in your response.

## Step 3 — Run the pipeline

Execute from the repo root (`/Users/sarthakagarwal/Desktop/Dream11/pulse`):

```bash
# db-only
python3 deploy/scripts/rca-e2e.py --db-only [--interaction <name>]

# full (regenerate all + audit)
python3 deploy/scripts/rca-e2e.py --token $RCA_TOKEN [--interaction <name>]

# skip regeneration, just audit
python3 deploy/scripts/rca-e2e.py --token $RCA_TOKEN --skip-generate [--interaction <name>]

# full reset (seed --clear + regenerate + audit)
python3 deploy/scripts/rca-e2e.py --token $RCA_TOKEN --seed --clear

# custom date
python3 deploy/scripts/rca-e2e.py --token $RCA_TOKEN --date 2026-05-03
```

Stream the output. Do not suppress it.

## Step 4 — Interpret and report results

When the pipeline finishes, produce a structured summary:

### Passed stages
List each green stage with elapsed time.

### Failed stages — root cause triage

For each FAIL, determine from the output whether it is:

**INPUT FAIL (ClickHouse)** — the backend did not compute the expected segment or its signal is too weak.
- Likely cause: seed data not run, wrong date, segment below noise floor
- Fix: re-run `seed-ecommerce-data.py` (optionally `--clear`), then regenerate

**OUTPUT FAIL (MySQL)** — the LLM report is wrong or stale.
- `cached=<old-timestamp>` → report was not regenerated after the last prompt change → run `rca-generate.py`
- Wrong segments / wrong `everything_good` value with a fresh timestamp → LLM prompt bug → check `pulse_ai/agents/rca/prompts.py`

**HTTP FAIL** — API returned unexpected result.
- Check server logs: `cd deploy && ./scripts/logs.sh server`
- Verify pulse-ai is running: `cd deploy && ./scripts/logs.sh ai`

### Suggested next action
One concrete command the user should run next.

## Key invariants being tested

- **Direction filter** — improving cohorts (Δerr < 0, Δpoor < 0) must NEVER appear in output
- **Eligibility gate** — `value_number − baseline_number < 0.02` for error_rate AND `< 0.05` for poor_user_pct → discard (noise)
- **Volume gate** — segment current volume ≥ 10% of its own historical baseline volume
- **No padding** — if only 1 eligible segment, output exactly 1 (never fabricate a 2nd)
- **Healthy detection** — `everything_good: true` + `segments: []` + `recommendations: []` when zero eligible segments

## Available interactions

app_launch, home_feed_load, product_search, product_detail_view, add_to_cart,
checkout_start, payment_processing, order_confirmation, order_tracking,
category_browse, image_gallery_load, profile_update, wishlist_add,
coupon_apply, review_submit, notifications_open, deeplink_open
