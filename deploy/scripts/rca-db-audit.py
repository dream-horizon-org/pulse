#!/usr/bin/env python3
"""
RCA ClickHouse Audit
--------------------
Reads pre-computed input segments directly from ClickHouse (otel.root_cause_cache)
and checks whether the expected bad cohorts are present with sufficient signal
before the LLM ever sees them.

This is a DATA QUALITY check — it catches seed data problems and backend
segmentation issues without needing a running server or a JWT token.

What it checks per interaction:
  • Expected bad segments are present in ClickHouse
  • Their signal is above the noise floor (Δerr and Δpoor are meaningful)
  • No pure-noise segments are being sent to the LLM unnecessarily

Usage:
    python3 deploy/scripts/rca-db-audit.py [--project <id>] [--date <YYYY-MM-DD>]
    python3 deploy/scripts/rca-db-audit.py --interaction app_launch
    python3 deploy/scripts/rca-db-audit.py --verbose
    PROJECT_ID=x python3 deploy/scripts/rca-db-audit.py
"""

import argparse
import json
import os
import sys
import urllib.request
import urllib.error
from datetime import date

# ── ClickHouse connection ──────────────────────────────────────────────────────
CH_HOST     = os.environ.get("CH_HOST",     "127.0.0.1")
CH_PORT     = os.environ.get("CH_PORT",     "8123")
CH_USER     = os.environ.get("CH_USER",     "pulse_user")
CH_PASSWORD = os.environ.get("CH_PASSWORD", "pulse_password")
CH_DB       = os.environ.get("CH_DB",       "otel")

DEFAULT_DATE = str(date.today())

INTERACTIONS = [
    "app_launch", "home_feed_load", "product_search", "product_detail_view",
    "add_to_cart", "checkout_start", "payment_processing", "order_confirmation",
    "order_tracking", "category_browse", "image_gallery_load", "profile_update",
    "wishlist_add", "coupon_apply", "review_submit", "notifications_open", "deeplink_open",
]

# ── Expectations (keep in sync with rca-audit.py) ─────────────────────────────
# Per-interaction schema:
#   expected_segments    — list of {keywords, borderline}: readability fallback for
#                          presence checks. Always populated.
#   expected_dimensions  — optional list of dimensions dicts (e.g. {"OsVersion": "10"}).
#                          When present, audit performs STRICT post-sort equality:
#                            • sort key: tuple(sorted(dimensions.items())) — stable,
#                              order-independent across runs and seed reshuffles.
#                            • actual segment count MUST equal len(expected_dimensions)
#                              (count mismatch = hard fail; no silent extras).
#                            • each index after sort must match exactly.
#                          Golden lists are recorded from a clean root_cause_cache
#                          run after issue 004 (deferred to issue 007 closeout).
#   mode                 — optional "HIERARCHICAL" / "FLAT" assertion. Allowlist
#                          (per docs/rca-segmentation-scenarios.md row G2):
#                            • checkout_start    → HIERARCHICAL
#                            • notifications_open → FLAT (everything_good path)
#                          Other interactions: mode is informational only.
#   noise_threshold_*    — segments below both thresholds are flagged as noise
#                          being sent to LLM.
#
# rca-audit.py (LLM path) intentionally does NOT carry strict dimensions parity —
# scenarios doc §G1 locks ownership of strict map equality to this script only.
EXPECTATIONS = {
    "app_launch": {
        "expected_segments": [
            {"keywords": ["10"],             "borderline": False},
            {"keywords": ["Jio", "android"], "borderline": False},
            {"keywords": ["SM-A135F"],       "borderline": True},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "home_feed_load": {
        "expected_segments": [
            {"keywords": ["Redmi"],  "borderline": False},
            {"keywords": ["4.1.0"], "borderline": True},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "product_search": {
        "expected_segments": [
            {"keywords": ["11"], "borderline": False},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "product_detail_view": {
        "expected_segments": [
            {"keywords": ["10"],                "borderline": False},
            {"keywords": ["4.1.0"],             "borderline": False},
            {"keywords": ["Vivo", "POCO", "Vi"],"borderline": True},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "add_to_cart": {
        "expected_segments": [
            {"keywords": ["android", "4.2.0"], "borderline": False},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "checkout_start": {
        "expected_segments": [
            {"keywords": ["SM-A135F"], "borderline": False},
            {"keywords": ["android"],  "borderline": True},
        ],
        "mode": "HIERARCHICAL",  # G2 allowlist
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "payment_processing": {
        "expected_segments": [
            {"keywords": ["13"],    "borderline": False},
            {"keywords": ["4.2.0"], "borderline": False},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "order_confirmation": {
        "expected_segments": [
            {"keywords": ["4.2.0", "android"], "borderline": False},
            {"keywords": ["13"],               "borderline": True},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "order_tracking": {
        "expected_segments": [
            {"keywords": ["16", "ios"],    "borderline": False},
            {"keywords": ["iPhone", "14"], "borderline": True},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "category_browse": {
        "expected_segments": [
            {"keywords": ["4.0.0"],   "borderline": False},
            {"keywords": ["OnePlus"], "borderline": False},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "image_gallery_load": {
        "expected_segments": [
            {"keywords": ["SM-A135F"],                "borderline": False},
            {"keywords": ["android", "OsVersion","12"],"borderline": True},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "profile_update": {
        "expected_segments": [
            {"keywords": ["4.0.0", "android"], "borderline": False},
            {"keywords": ["KA", "Vi"],         "borderline": True},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "wishlist_add": {
        "expected_segments": [
            {"keywords": ["android", "Vivo", "POCO", "BSNL"],  "borderline": True},
            {"keywords": ["SM-A135F", "4.2.0", "UP", "BR"],    "borderline": True},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "coupon_apply": {
        "expected_segments": [
            {"keywords": ["android", "12", "Redmi", "Jio"], "borderline": True},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "review_submit": {
        "expected_segments": [
            {"keywords": ["OnePlus", "android", "4.3.0"], "borderline": True},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "notifications_open": {
        # Healthy — all segments in ClickHouse should be noise (nothing should trigger RCA)
        "expected_segments": [],
        "expected_dimensions": [],  # strict: zero segments expected on the everything-good path
        "mode": "FLAT",             # G2 allowlist (everything_good emits FLAT)
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
    "deeplink_open": {
        "expected_segments": [
            {"keywords": ["ios", "16", "Vi"], "borderline": True},
        ],
        "noise_threshold_err": 10, "noise_threshold_poor": 15,
    },
}

# ── ClickHouse helpers ─────────────────────────────────────────────────────────

def ch_query(sql):
    url = f"http://{CH_HOST}:{CH_PORT}/?database={CH_DB}&user={CH_USER}&password={CH_PASSWORD}"
    try:
        req = urllib.request.Request(url, data=sql.encode("utf-8"))
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        print(f"  ClickHouse error ({e.code}): {e.read().decode()[:400]}")
        return None


def _ch_esc(s):
    return (s or "").replace("\\", "\\\\").replace("'", "\\'")


def detect_project():
    raw = ch_query(
        "SELECT ProjectId FROM otel.root_cause_cache"
        " ORDER BY cached_at DESC LIMIT 1 FORMAT TSV"
    )
    if raw and raw.strip():
        return raw.strip().splitlines()[0].strip()
    print("ERROR: no project found in ClickHouse root_cause_cache. "
          "Run the seed + generate pipeline first, or pass --project explicitly.")
    sys.exit(1)


def fetch_segments(project_id, name, audit_date):
    """
    Returns (segments, mode, computed_at) from the most recent ClickHouse cache row,
    or (None, None, None) on miss.
    """
    sql = (
        "SELECT segments, mode, toString(cached_at) AS ts"
        " FROM otel.root_cause_cache"
        f" WHERE ProjectId = '{_ch_esc(project_id)}'"
        f" AND interaction_name = '{_ch_esc(name)}'"
        f" AND date = '{_ch_esc(audit_date)}'"
        " ORDER BY cached_at DESC LIMIT 1"
        " FORMAT JSONEachRow"
    )
    raw = ch_query(sql)
    if not raw or not raw.strip():
        return None, None, None
    try:
        row  = json.loads(raw.strip().splitlines()[0])
        segs = json.loads(row["segments"]) if row.get("segments") else []
        return segs, row.get("mode"), row.get("ts")
    except Exception as e:
        print(f"  [parse error] {name}: {e}")
        return None, None, None


# ── Verdict helpers ────────────────────────────────────────────────────────────

PASS = "PASS"
FAIL = "FAIL"
SKIP = "SKIP"
INFO = "INFO"

_COLORS = {PASS: "\033[32m", FAIL: "\033[31m", SKIP: "\033[90m", INFO: "\033[36m"}
RESET = "\033[0m"


def _c(v, text):
    return f"{_COLORS.get(v, '')}{text}{RESET}"


def _fmt_pct(v):
    if v is None:
        return "—"
    return f"{'+'if v >= 0 else ''}{v:.1f}%"


# ── Sort key ───────────────────────────────────────────────────────────────────
# Documented stable sort key for both expected and actual segment lists.
# Choice: tuple of sorted (key, value) pairs from the segment's `dimensions` map.
# Why this key:
#   • Order-independent: two segments built in different orders sort to the same
#     position so long as their dimensions match.
#   • Deterministic: pure-Python tuple compare on strings.
#   • Faithful to the comparison target: index alignment after sort assumes the
#     thing being compared (the dimensions map) IS the sort basis, so two
#     equivalent segments cannot trade places between runs.
# Segments with no dimensions sort to the empty tuple (); the count-mismatch
# guard catches the case where a healthy interaction unexpectedly emits one.

def segment_sort_key(seg):
    dims = seg.get("dimensions") or {}
    return tuple(sorted((str(k), str(v)) for k, v in dims.items()))


# ── Check ──────────────────────────────────────────────────────────────────────

def check_segments(segments, mode, name):
    """
    Checks ClickHouse root_cause_cache.segments for one interaction.

    Segment schema (from RootCauseSegment.java):
      label            — human-readable, e.g. "OsVersion: 10"
      dimensions       — {dim_name: value}
      metrics          — {metric_name: raw_float}
      deltas           — {metric_name: relative_pct_change}  (positive = worse for degrading metrics)
      exampleSessionIds — [str, ...]
    """
    issues = []
    exp = EXPECTATIONS.get(name)
    if not exp:
        return [(SKIP, "No expectations defined")]

    segments = segments or []

    # Mode allowlist assertion — only when EXPECTATIONS pins a mode.
    expected_mode = exp.get("mode")
    if expected_mode is not None:
        if mode == expected_mode:
            issues.append((PASS, f"[MODE]    mode={mode}"))
        else:
            issues.append((FAIL, f"[MODE]    expected mode={expected_mode}, got {mode}"))

    # Strict dimensions equality — only when expected_dimensions is provided.
    exp_dims = exp.get("expected_dimensions")
    if exp_dims is not None:
        actual_sorted = sorted(segments, key=segment_sort_key)
        expected_sorted = sorted(
            ({"dimensions": d} for d in exp_dims), key=segment_sort_key
        )
        if len(actual_sorted) != len(expected_sorted):
            issues.append((FAIL,
                f"[COUNT]   expected {len(expected_sorted)} segment(s), got {len(actual_sorted)} "
                "(strict dimensions check requires exact count — no silent extras)"))
        else:
            for idx, (a, e) in enumerate(zip(actual_sorted, expected_sorted)):
                a_dims = a.get("dimensions") or {}
                e_dims = e.get("dimensions") or {}
                if a_dims == e_dims:
                    issues.append((PASS, f"[DIMS#{idx}] {a_dims}"))
                else:
                    issues.append((FAIL,
                        f"[DIMS#{idx}] expected {e_dims}, got {a_dims} (post-sort index)"))
        if not segments:
            # Empty expected + empty actual is success; skip presence/noise loops.
            return issues

    if not segments:
        issues.append((FAIL, "No segments in ClickHouse for this interaction/date"))
        return issues

    te = exp.get("noise_threshold_err",  10)
    tp = exp.get("noise_threshold_poor", 15)

    # Noise check — segments below the noise floor waste LLM context
    for s in segments:
        label  = s.get("label", "?")
        deltas = s.get("deltas", {})
        derr   = deltas.get("error_rate")
        dpup   = deltas.get("poor_user_pct")
        if derr is not None and dpup is not None and abs(derr) < te and abs(dpup) < tp:
            issues.append((FAIL, f"[NOISE]   [{label}]  Δerr={_fmt_pct(derr)}  Δpoor={_fmt_pct(dpup)}"))

    # Presence check — every expected bad segment (including borderline) must exist
    for exp_seg in exp.get("expected_segments", []):
        keywords = exp_seg["keywords"]
        matched  = None
        for s in segments:
            label    = (s.get("label") or "").lower()
            dims_str = " ".join(str(v) for v in (s.get("dimensions") or {}).values()).lower()
            if any(kw.lower() in label + " " + dims_str for kw in keywords):
                matched = s
                break

        kw_str = "/".join(keywords)
        if not matched:
            issues.append((FAIL, f"[MISSING] [{kw_str}] not found in ClickHouse segments"))
            continue

        derr = (matched.get("deltas") or {}).get("error_rate")
        dpup = (matched.get("deltas") or {}).get("poor_user_pct")
        if derr is not None and dpup is not None and abs(derr) < te and abs(dpup) < tp:
            issues.append((FAIL,
                f"[WEAK]    [{matched['label']}]  Δerr={_fmt_pct(derr)}  Δpoor={_fmt_pct(dpup)} — below noise floor"))
        else:
            issues.append((PASS,
                f"[OK]      [{matched['label']}]  Δerr={_fmt_pct(derr)}  Δpoor={_fmt_pct(dpup)}"))

    return issues


# ── Per-interaction audit ──────────────────────────────────────────────────────

def audit_interaction(name, project_id, audit_date, verbose):
    print(f"\n{'─'*72}")
    print(f"  {name}")
    print(f"{'─'*72}")

    segments, mode, computed_at = fetch_segments(project_id, name, audit_date)

    if segments is None:
        print(f"  {_c(FAIL, 'FAIL')}  No ClickHouse entry for {name} on {audit_date}")
        return FAIL, [(FAIL, "No ClickHouse cache entry")]

    print(f"  {_c(INFO, 'INFO')}  {len(segments)} segment(s)  mode={mode}  computed={computed_at}")

    if verbose:
        for s in segments:
            d    = s.get("deltas", {})
            derr = d.get("error_rate")
            dpup = d.get("poor_user_pct")
            vol  = (s.get("metrics") or {}).get("volume")
            print(f"    [{s.get('label','?')}]  "
                  f"Δerr={_fmt_pct(derr)}  Δpoor={_fmt_pct(dpup)}  vol={vol}")

    issues = check_segments(segments, mode, name)
    for verdict, msg in issues:
        print(f"    {_c(verdict, verdict)}  {msg}")

    if any(v == FAIL for v, _ in issues):
        return FAIL, issues
    return PASS, issues


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="RCA ClickHouse Audit — checks input segment quality in otel.root_cause_cache"
    )
    parser.add_argument("--project",     default=os.environ.get("PROJECT_ID", None))
    parser.add_argument("--date",        default=os.environ.get("RCA_DATE", DEFAULT_DATE))
    parser.add_argument("--interaction", default=None, help="Audit a single interaction")
    parser.add_argument("--verbose",     action="store_true",
                        help="Print every segment with its deltas and volume")
    args = parser.parse_args()

    if not args.project:
        args.project = detect_project()
        print(f"Auto-detected project: {args.project}")

    targets = [args.interaction] if args.interaction else INTERACTIONS
    print(f"RCA ClickHouse Audit  |  project={args.project}  date={args.date}")
    print(f"ClickHouse: {CH_HOST}:{CH_PORT}/{CH_DB}\n")

    results = {}
    for name in targets:
        overall, issues = audit_interaction(name, args.project, args.date, args.verbose)
        results[name] = (overall, issues)

    # ── Summary ────────────────────────────────────────────────────────────────
    sep = "═" * 72
    print(f"\n{sep}")
    print("  SUMMARY")
    print(sep)

    counts = {PASS: 0, FAIL: 0}
    for name, (overall, issues) in results.items():
        counts[overall] = counts.get(overall, 0) + 1
        fails = sum(1 for v, _ in issues if v == FAIL)
        print(f"  {_c(overall, overall):<20}  {name:<28}  {fails} failure(s)")

    print(sep)
    print(f"  {_c(PASS, str(counts[PASS]) + ' pass')}  "
          f"{_c(FAIL, str(counts[FAIL]) + ' fail')}")
    print(sep)

    if counts[FAIL]:
        sys.exit(1)


if __name__ == "__main__":
    main()
