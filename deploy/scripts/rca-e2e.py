#!/usr/bin/env python3
"""
RCA End-to-End Test Pipeline
=============================

WHAT THIS PIPELINE TESTS
─────────────────────────
This script validates the full Root Cause Analysis (RCA) feature stack end-to-end
across 17 seeded e-commerce interactions on the Pulse observability platform.

Each interaction simulates a real mobile app flow (e.g. app_launch, checkout_start,
payment_processing) where we have deliberately seeded GOOD and BAD cohorts into
ClickHouse. The RCA engine must:

  1. Identify the correct bad cohorts (by device, OS version, app version, region, network)
  2. Compute segment-level metrics and deltas against baseline
  3. Feed those segments to an LLM agent (Google ADK / Gemini)
  4. The LLM must produce a structured RcaStructuredReportV1 JSON that matches expectations

WHAT WE ARE TESTING
────────────────────
Layer 1 — DATA QUALITY (ClickHouse input)
  • Do the expected bad segments exist in otel.root_cause_cache with sufficient signal?
  • Are any noise segments (tiny Δerror_rate, tiny Δpoor_user_pct) being sent to the LLM?
  Tool: rca-db-audit.py (reads ClickHouse directly, no API needed)

Layer 2 — LLM OUTPUT QUALITY (MySQL report cache)
  • Does the stored LLM report (rca_report_cache.report_body) match expected segment count?
  • Does it surface the right segments (by title/insights keywords)?
  • Does it correctly set everything_good=true for healthy interactions?
  • Does it avoid forbidden keywords (direction filter, eligibility gate working)?
  • Does it avoid padding with noise or fabricated segments?
  Tool: rca-db-audit.py (reads MySQL directly, no API needed)

Layer 3 — API / FULL STACK (HTTP end-to-end)
  • Does the API correctly serve the report for a given project/interaction/date?
  • Does the async job pipeline (POST → 202 → poll → COMPLETED) work?
  • Is the report_body faithfully deserialized and returned by the server?
  Tool: rca-audit.py (uses Bearer JWT, goes through the full HTTP stack)

SEEDED INTERACTIONS AND THEIR EXPECTED FINDINGS
─────────────────────────────────────────────────
  app_launch           bad: Android 10 + Jio, DeviceModel SM-A135F
  home_feed_load       bad: Redmi Note 12; good: wifi+4.3.0 (must NOT surface)
  product_search       bad: OsVersion 11; good: iOS+4.3.0 (must NOT surface)
  product_detail_view  bad: OsVersion 10, AppVersion 4.1.0
  add_to_cart          bad: android+4.2.0+Jio+OS13 (Track B: crashes + ANRs)
  checkout_start       bad: DeviceModel SM-A135F (frozen frames)
  payment_processing   bad: OsVersion 13 (android), AppVersion 4.2.0 (iOS)
  order_confirmation   bad: android+4.2.0 compound
  order_tracking       bad: iOS OsVersion 16.0 (large error spike)
  category_browse      bad: AppVersion 4.0.0, DeviceModel OnePlus 11
  image_gallery_load   bad: DeviceModel SM-A135F
  profile_update       bad: AppVersion 4.0.0 (android)
  wishlist_add         borderline: small bad cohorts (~26-43 sessions)
  coupon_apply         borderline: 4-way compound (android+OS12+Redmi+Jio)
  review_submit        borderline: OsVersion×AppVersion×geo
  notifications_open   HEALTHY — everything_good must be true, zero segments
  deeplink_open        borderline: iOS 16 + Vi (~9 sessions); 0 or 1 segment OK

KEY CORRECTNESS INVARIANTS BEING VERIFIED
───────────────────────────────────────────
  • Direction filter: improving cohorts (lower error rate than baseline) must NEVER appear
  • Eligibility gate: segments where absolute Δerror_rate < 2pp AND Δpoor_user_pct < 5pp
    are statistical noise and must be discarded
  • Volume check: segment current volume must be ≥10% of its own historical baseline volume
  • No padding: if only 1 eligible segment exists, output exactly 1 (do NOT fabricate a 2nd)
  • Healthy detection: if no segments pass eligibility, everything_good=true + empty recommendations

PIPELINE STAGES
────────────────
  [0] Print this test plan
  [1] (optional) Seed e-commerce data into ClickHouse + MySQL  (--seed)
  [2] Regenerate all LLM reports via the async job API         (--token required)
  [3] DB audit   — ClickHouse input + MySQL output quality     (no token needed)
  [4] HTTP audit — full API end-to-end verification            (--token required)

Usage:
    # Full run (seed + generate + audit):
    python3 deploy/scripts/rca-e2e.py --token <JWT> --seed

    # Skip seed (data already in DB), regenerate and audit:
    python3 deploy/scripts/rca-e2e.py --token <JWT>

    # Skip regeneration too (use cached reports), audit only:
    python3 deploy/scripts/rca-e2e.py --token <JWT> --skip-generate

    # Single interaction, full run:
    python3 deploy/scripts/rca-e2e.py --token <JWT> --interaction checkout_start

    # DB audit only (no token, no API):
    python3 deploy/scripts/rca-e2e.py --db-only

    RCA_TOKEN=<JWT> python3 deploy/scripts/rca-e2e.py --seed
"""

import argparse
import os
import subprocess
import sys
import time
from datetime import date

# ── Config ─────────────────────────────────────────────────────────────────────
SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DATE = str(date.today())

# ── Colours ────────────────────────────────────────────────────────────────────
GREEN  = "\033[32m"
RED    = "\033[31m"
YELLOW = "\033[33m"
CYAN   = "\033[36m"
BOLD   = "\033[1m"
DIM    = "\033[2m"
RESET  = "\033[0m"

def _c(color, text):
    return f"{color}{text}{RESET}"

def _bold(text):
    return f"{BOLD}{text}{RESET}"

def _header(title):
    sep = "═" * 72
    print(f"\n{sep}")
    print(f"  {_bold(title)}")
    print(sep)


# ── Step runner ────────────────────────────────────────────────────────────────

def run_step(label, cmd, fatal=True):
    """
    Run a subprocess step, stream its output, and return (success, elapsed_s).
    If fatal=True and the step fails, exit the pipeline immediately.
    """
    _header(label)
    print(_c(DIM, f"  $ {' '.join(cmd)}\n"))
    sys.stdout.flush()
    start = time.time()
    result = subprocess.run(cmd)
    elapsed = time.time() - start

    if result.returncode == 0:
        status = _c(GREEN, "PASSED")
    else:
        status = _c(RED, "FAILED")

    print(f"\n  {status}  ({elapsed:.1f}s)")

    if result.returncode != 0 and fatal:
        print(_c(RED, "\n  Pipeline aborted — fix the failure above before continuing.\n"))
        sys.exit(result.returncode)

    return result.returncode == 0, elapsed


# ── Test plan banner ───────────────────────────────────────────────────────────

def print_test_plan(args):
    sep  = "═" * 72
    thin = "─" * 72

    stages = []
    n = 1
    if args.seed:
        stages.append(f"  [{n}] Seed data   {'(--clear) ' if args.clear else ''}→ seed-ecommerce-data.py")
        n += 1
    if not args.skip_generate and not args.db_only:
        stages.append(f"  [{n}] Regenerate LLM reports  → rca-generate.py")
        n += 1
    elif args.skip_generate:
        stages.append(f"  [–] Regenerate skipped (--skip-generate)")
    stages.append(f"  [{n}] DB audit    (ClickHouse + MySQL, no token)  → rca-db-audit.py")
    n += 1
    if not args.db_only:
        stages.append(f"  [{n}] HTTP audit  (full API stack, token required)  → rca-audit.py")

    scope = f"interaction={args.interaction}" if args.interaction else "all 17 interactions"

    print(f"\n{sep}")
    print(f"  {_bold('RCA END-TO-END TEST PIPELINE')}")
    print(thin)
    print(f"  project  : {args.project or '(auto-detect)'}")
    print(f"  date     : {args.date}")
    print(f"  scope    : {scope}")
    print(thin)
    print("  STAGES:")
    for s in stages:
        print(s)
    print(thin)
    print("  WHAT WE VERIFY:")
    print("    Layer 1 — ClickHouse input  : correct bad segments with sufficient signal")
    print("    Layer 2 — MySQL output      : LLM report matches expectations (segment count,")
    print("                                  keywords, everything_good, forbidden keywords)")
    if not args.db_only:
        print("    Layer 3 — HTTP API          : async job pipeline, deserialization, serving")
    print(thin)
    print("  KEY INVARIANTS:")
    print("    • Direction filter   — improving cohorts must never surface")
    print("    • Eligibility gate   — Δerr < 2pp AND Δpoor < 5pp → noise, discard")
    print("    • Volume gate        — segment volume ≥ 10% of its own baseline")
    print("    • No padding         — do not fabricate a 2nd segment when only 1 is eligible")
    print("    • Healthy detection  — everything_good=true + [] when zero eligible segments")
    print(sep)
    print()


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="RCA end-to-end test pipeline: seed → generate → db-audit → http-audit",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--token",         default=os.environ.get("RCA_TOKEN", ""),
                        help="Bearer JWT for API calls (required unless --db-only)")
    parser.add_argument("--project",       default=os.environ.get("PROJECT_ID", None),
                        help="Project ID (auto-detected from DB if omitted)")
    parser.add_argument("--date",          default=os.environ.get("RCA_DATE", DEFAULT_DATE))
    parser.add_argument("--host",          default="http://localhost:8080")
    parser.add_argument("--interaction",   default=None,
                        help="Run for a single interaction only")
    parser.add_argument("--seed",          action="store_true",
                        help="Run seed-ecommerce-data.py before generating")
    parser.add_argument("--clear",         action="store_true",
                        help="Pass --clear to the seed script (wipes existing data first)")
    parser.add_argument("--skip-generate", action="store_true",
                        help="Skip rca-generate.py — use whatever is cached in MySQL")
    parser.add_argument("--db-only",       action="store_true",
                        help="Run DB audit only (no API token required, skips generate + http audit)")
    args = parser.parse_args()

    # Token required for any stage that talks to the API
    if not args.db_only and not args.token:
        print(_c(RED, "ERROR: --token (or RCA_TOKEN env) is required unless --db-only is set."))
        sys.exit(1)

    print_test_plan(args)
    sys.stdout.flush()

    pipeline_start = time.time()
    step_results = []
    stage = 1  # sequential stage counter

    # ── Stage: Seed (optional) ──────────────────────────────────────────────
    if args.seed:
        cmd = [sys.executable, os.path.join(SCRIPTS_DIR, "seed-ecommerce-data.py")]
        if args.clear:
            cmd.append("--clear")
        ok, elapsed = run_step(f"STAGE {stage} — Seed e-commerce data", cmd, fatal=True)
        step_results.append(("Seed", ok, elapsed))
        stage += 1

    # ── Stage: Generate ─────────────────────────────────────────────────────
    if not args.skip_generate and not args.db_only:
        cmd = [
            sys.executable, os.path.join(SCRIPTS_DIR, "rca-generate.py"),
            "--token", args.token,
            "--host",  args.host,
            "--date",  args.date,
        ]
        if args.project:
            cmd += ["--project", args.project]
        if args.interaction:
            cmd += ["--interaction", args.interaction]
        ok, elapsed = run_step(f"STAGE {stage} — Regenerate LLM reports", cmd, fatal=False)
        step_results.append(("Generate", ok, elapsed))
        stage += 1

    # ── Stage: DB audit ─────────────────────────────────────────────────────
    cmd = [
        sys.executable, os.path.join(SCRIPTS_DIR, "rca-db-audit.py"),
        "--date", args.date,
    ]
    if args.project:
        cmd += ["--project", args.project]
    if args.interaction:
        cmd += ["--interaction", args.interaction]
    ok, elapsed = run_step(f"STAGE {stage} — DB audit (ClickHouse input + MySQL output)", cmd, fatal=False)
    step_results.append(("DB Audit", ok, elapsed))
    stage += 1

    # ── Stage: HTTP audit ───────────────────────────────────────────────────
    if not args.db_only:
        cmd = [
            sys.executable, os.path.join(SCRIPTS_DIR, "rca-audit.py"),
            "--token", args.token,
            "--host",  args.host,
            "--date",  args.date,
        ]
        if args.project:
            cmd += ["--project", args.project]
        if args.interaction:
            cmd += ["--interaction", args.interaction]
        ok, elapsed = run_step(f"STAGE {stage} — HTTP audit (full API stack)", cmd, fatal=False)
        step_results.append(("HTTP Audit", ok, elapsed))

    # ── Final summary ───────────────────────────────────────────────────────
    total = time.time() - pipeline_start
    _header("PIPELINE SUMMARY")

    all_passed = True
    for name, ok, elapsed in step_results:
        icon    = _c(GREEN, "✓ PASSED") if ok else _c(RED, "✗ FAILED")
        all_passed = all_passed and ok
        print(f"  {icon}  {name:<20} ({elapsed:.1f}s)")

    print(f"\n  Total time: {total:.1f}s")

    if all_passed:
        print(_c(GREEN, f"\n  All stages passed.\n"))
    else:
        failed = [n for n, ok, _ in step_results if not ok]
        print(_c(RED, f"\n  Failed stages: {', '.join(failed)}"))
        print(_c(YELLOW, "  Fix the failures above, then re-run the relevant stage(s).\n"))
        sys.exit(1)


if __name__ == "__main__":
    main()
