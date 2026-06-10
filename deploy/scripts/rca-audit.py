#!/usr/bin/env python3
"""
RCA Segment Audit + Expected-Output Comparison
------------------------------------------------
For every seeded interaction:
  1. Fetches input segments  — GET /v1/interactions/{name}/root-cause
  2. Fetches LLM output      — POST /v1/ai/rca/report
  3. Compares against ground-truth expectations from docs/rca-expected-outputs.md
  4. Prints a per-interaction verdict and a final summary table

Usage:
    python3 rca-audit.py --token <JWT> [--project <id>] [--date <YYYY-MM-DD>] [--host <url>]
    python3 rca-audit.py --token <JWT> --interaction app_launch   # single interaction
    python3 rca-audit.py --token <JWT> --report-only              # summary table only

    # env vars
    RCA_TOKEN=<JWT> RCA_DATE=2026-05-03 python3 rca-audit.py
"""

import argparse
import json
import os
import sys
import urllib.request
import urllib.error
from datetime import date

# ── Config ────────────────────────────────────────────────────────────────────
DEFAULT_HOST = "http://localhost:8080"
DEFAULT_PROJECT = "Testing1223-PmnxqFxx"
DEFAULT_DATE = str(date.today())

def _interaction_input_rate_sum(seg, baseline_metrics):
    sm = seg.get("metrics") or {}
    bm = baseline_metrics or {}
    ssum = float(sm.get("error_rate") or 0) + float(sm.get("poor_user_pct") or 0)
    bsum = float(bm.get("error_rate") or 0) + float(bm.get("poor_user_pct") or 0)
    return ssum, bsum


def _interaction_output_rates_outrank_baseline(structured_seg):
    """LLM structured row: value_number sum vs baseline_number sum."""
    metrics = {m["metric_id"]: m for m in structured_seg.get("metrics", [])}
    er = metrics.get("error_rate", {})
    pu = metrics.get("poor_user_pct", {})
    vs = float(er.get("value_number") or 0) + float(pu.get("value_number") or 0)
    bs = float(er.get("baseline_number") or 0) + float(pu.get("baseline_number") or 0)
    return vs > bs


# ── Expectations (sourced from docs/rca-expected-outputs.md) ──────────────────
#
# Each entry:
#   segments_min / segments_max   — expected output segment count range
#   everything_good               — True if agent should return no findings
#   expected_segments             — list of dicts:
#       keywords:    strings that should appear in segment text (see keyword_match /
#                    min_err_delta below). Separate expected_segments rows require
#                    multiple independent cohort checks.
#       keyword_match: "any" (default) or "all" — whether every keyword must appear.
#       match_insights: false (default): match keywords against **segment title only** so insights
#       (+10%, "Android 10") do not hijack checks; true uses title + insights.
#       min_err_delta: optional minimum Δerror_rate% vs baseline for matched segment's metric row.
#       borderline:  True = may not surface, don't fail if absent
#   forbidden_keywords            — title keywords that must NOT appear in any output segment
#   direction_filter_note         — description of good cohort that must stay invisible
#   special_notes                 — list of strings shown in the report
#
# Input segment gate mirrors pulse-server interaction RCA: segment raw error_rate+poor_user_pct sum
# must be strictly greater than the same sum on the interaction baseline payload.
EXPECTATIONS = {
    "app_launch": {
        "segments_min": 2,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            {"keywords": ["10"], "min_err_delta": None, "borderline": False},
            {"keywords": ["Jio"], "min_err_delta": None, "borderline": False},
            {"keywords": ["SM-A135F"], "min_err_delta": None, "borderline": True},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "IN-AP (Andhra Pradesh) sub-cluster should be mentioned in insights/summary",
            "Jio must appear in some segment title/insights — not satisfied by generic android alone",
            "SM-A135F surfaces as DeviceModel segment — borderline volume",
        ],
    },
    "home_feed_load": {
        "segments_min": 1,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            {"keywords": ["Redmi"], "min_err_delta": None, "borderline": False},
            {"keywords": ["4.1.0"], "min_err_delta": None, "borderline": True},
        ],
        "forbidden_keywords": ["wifi", "4.3.0"],
        "direction_filter_note": "wifi + 4.3.0 (~350 sessions, error_rate ~0.4%) must NOT appear — direction filter test",
        "special_notes": [
            "NetworkProvider (BSNL) is 5th in flat-mode dimension order — can't surface in top-4",
            "Primary signals: DeviceModel (Redmi Note 12) + optionally AppVersion (4.1.0)",
        ],
    },
    "product_search": {
        "segments_min": 1,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            {"keywords": ["11"], "min_err_delta": None, "borderline": False},
            {"keywords": ["BSNL", "UP", "BR", "MP"], "min_err_delta": None, "borderline": True},
        ],
        "forbidden_keywords": ["ios", "4.3.0"],
        "direction_filter_note": "iOS + 4.3.0 (~375 sessions, error_rate ~0.3%) must NOT appear — direction filter test",
        "special_notes": [
            "NetworkProvider (BSNL) is 5th in flat-mode dimension order — unlikely to surface in top-4",
            "Primary signal: OsVersion:11 (android+11+UP compound in hierarchical mode)",
        ],
    },
    "product_detail_view": {
        "segments_min": 2,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            {"keywords": ["10"], "min_err_delta": None, "borderline": False},
            {"keywords": ["4.1.0"], "min_err_delta": None, "borderline": False},
            {"keywords": ["Vivo", "POCO", "Vi"], "min_err_delta": None, "borderline": True},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": ["3rd segment (Vivo/POCO + Vi) is borderline — acceptable if missing"],
    },
    "add_to_cart": {
        "segments_min": 1,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            # Track B seeds android+4.2.0+Jio+OS13 Poor sessions → dominates top-4
            # Compound drills: android → 4.2.0 → Jio → 13 in hierarchical mode
            {"keywords": ["android", "4.2.0"], "min_err_delta": None, "borderline": False},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "Track B (1100+ Poor crash + 150 ANR sessions, android+Jio+4.2.0+OS13) dominates hierarchical drill",
            "Primary signal: poor_user_pct delta (error_rate may be negative for compound — Track B uses Error status=Ok)",
            "Error attribution section must have: anr, non_fatal, api signals",
            "relatedAttributions must include crash + ANR rows with risk ratio > 2.0",
        ],
    },
    "checkout_start": {
        "segments_min": 1,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            {"keywords": ["SM-A135F"], "min_err_delta": None, "borderline": False},
            {"keywords": ["android"], "min_err_delta": None, "borderline": True},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "NetworkProvider (Vi) is 5th in flat-mode dimension order — cannot surface in top-4",
            "SM-A135F is primary bad segment; android and OsVersion:12 may also appear",
            "Both segments should show elevated frozen_frame_rate",
        ],
    },
    "payment_processing": {
        "segments_min": 2,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            # android+13+Airtel → surfaces as OsVersion:13 in flat mode
            {"keywords": ["13"], "min_err_delta": None, "borderline": False},
            # ios+4.2.0 → surfaces as AppVersion:4.2.0
            {"keywords": ["4.2.0"], "min_err_delta": None, "borderline": False},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "GeoState (IN-WB) is 6th in flat-mode dimension order — cannot surface in top-4",
            "OsVersion:13 (from android+13+Airtel bad segment) + AppVersion:4.2.0 (from ios+4.2.0) expected",
            "Android 13 expected rank 1 (worst severity by poor_user_pct delta)",
        ],
    },
    "order_confirmation": {
        "segments_min": 1,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            {
                "keywords": ["4.2.0", "android"],
                "keyword_match": "all",
                "min_err_delta": None,
                "borderline": False,
            },
            {"keywords": ["13"], "min_err_delta": None, "borderline": True},
        ],
        "forbidden_keywords": ["Pixel", "14"],
        "direction_filter_note": None,
        "special_notes": [
            "Pixel 8 + Android 14 (~10 sessions) must NOT surface — too small",
            "android+4.2.0 is the primary bad segment; OsVersion:13 may also surface",
        ],
    },
    "order_tracking": {
        "segments_min": 1,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            # LLM title mirrors label (e.g. OsVersion: 16.0) — "ios" may not appear verbatim
            {"keywords": ["16"], "min_err_delta": None, "borderline": False},
            # DeviceModel row may use iPhone14,* style without space before "14"
            {"keywords": ["iPhone"], "min_err_delta": None, "borderline": True},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "NetworkProvider (BSNL) is 5th in flat-mode dimension order — cannot surface in top-4",
            "ios OsVersion:16.0 is the primary bad segment — volume passes new segment-relative check",
        ],
    },
    "category_browse": {
        "segments_min": 2,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            {"keywords": ["4.0.0"], "min_err_delta": None, "borderline": False},
            {"keywords": ["OnePlus"], "min_err_delta": None, "borderline": False},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "OnePlus segment primary signal is crash_rate, not error_rate",
            "NetworkProvider (Jio/AP) is 5th in flat-mode dimension order — cannot surface in top-4",
        ],
    },
    "image_gallery_load": {
        "segments_min": 1,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            {"keywords": ["SM-A135F"], "min_err_delta": None, "borderline": False},
            {"keywords": ["android", "12"], "keyword_match": "any", "min_err_delta": None, "borderline": True},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "SM-A135F is primary signal (high poor_user_pct and frozen_frame_rate)",
            "android-only and OsVersion:12 rows may surface as separate segments (flat + hybrid tier)",
            "NetworkProvider (Jio) is 5th in flat-mode — SM-A135F+Jio compound cannot surface separately",
        ],
    },
    "profile_update": {
        "segments_min": 1,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            # Title is often AppVersion: 4.0.0 without repeating "android"
            {"keywords": ["4.0.0"], "min_err_delta": None, "borderline": False},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "AppVersion:4.0.0 is primary bad segment (644% error delta, 2050% poor delta)",
            "GeoState×Network (KA+Vi) is unlikely to surface in flat-mode top-4",
        ],
    },
    "wishlist_add": {
        "segments_min": 0,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            {"keywords": ["android", "Vivo", "POCO", "BSNL"], "min_err_delta": None, "borderline": True},
            {"keywords": ["SM-A135F", "4.2.0", "UP", "BR"], "min_err_delta": None, "borderline": True},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "Both segments borderline (~26–43 sessions) — may not surface",
            "If segments appear: titles must reference all compound dimensions, not single dimension",
            "If 0 segments: agent must NOT fabricate — everything_good=false with 0 segments acceptable",
        ],
    },
    "coupon_apply": {
        "segments_min": 1,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            # Borderline compound may not reach LLM titles; second row covers flat merge signal
            {"keywords": ["android", "12", "Redmi", "Jio"], "min_err_delta": None, "borderline": True},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "4-way compound (android+OS12+Redmi+Jio) — all four dimensions should appear if it surfaces",
            "Borderline volumes (~34–42 sessions) — 1 segment acceptable",
        ],
    },
    "review_submit": {
        "segments_min": 0,
        "segments_max": 5,
        "everything_good": False,
        "expected_segments": [
            {"keywords": ["OnePlus", "android", "4.3.0"], "min_err_delta": None, "borderline": True},
        ],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "Android 13 + 4.2.0 + WB (~8 sessions) must NOT surface",
            "Agent must NOT pad to 2 segments if only 1 surfaced",
        ],
    },
    "notifications_open": {
        "segments_min": 0, "segments_max": 0,
        "everything_good": True,
        "expected_segments": [],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "Healthy interaction — everything_good MUST be true",
            "NO segments should appear — if any do, Pre-Analysis Gate or direction filter is broken",
            "Recommendations must be empty []",
        ],
    },
    "deeplink_open": {
        "segments_min": 0,
        "segments_max": 5,
        "everything_good": None,  # either outcome acceptable
        "expected_segments": [],
        "forbidden_keywords": [],
        "direction_filter_note": None,
        "special_notes": [
            "iOS 16 + Vi has only ~9 sessions — may not surface (volume risk)",
            "Two valid outcomes: up to maxSegments mirror of server list OR everything_good=true",
            "Harness allows multiple segments when hybrid merge returns a full tiered list",
        ],
    },
}

# ── HTTP helpers ──────────────────────────────────────────────────────────────

def _headers(token, project_id):
    return {
        "Authorization": f"Bearer {token}",
        "X-Project-ID": project_id,
        "user-email": "user@example.com",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }


def _get(url, token, project_id):
    req = urllib.request.Request(url, headers=_headers(token, project_id))
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read()), None
    except urllib.error.HTTPError as e:
        return None, f"HTTP {e.code}: {e.read().decode()[:300]}"
    except Exception as e:
        return None, str(e)


def _post(url, body, token, project_id):
    data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers=_headers(token, project_id), method="POST")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read()), None
    except urllib.error.HTTPError as e:
        return None, f"HTTP {e.code}: {e.read().decode()[:300]}"
    except Exception as e:
        return None, str(e)


# ── Verdict helpers ───────────────────────────────────────────────────────────

PASS = "PASS"
FAIL = "FAIL"
WARN = "WARN"
SKIP = "SKIP"

def _verdict_color(v):
    return {"PASS": "\033[32m", "FAIL": "\033[31m", "WARN": "\033[33m", "SKIP": "\033[90m"}.get(v, "")

RESET = "\033[0m"

def _c(v, text):
    return f"{_verdict_color(v)}{text}{RESET}"


def _delta(val, base):
    if val is None or base is None or base == 0:
        return None
    return (val - base) / base * 100


def _fmt_delta(v):
    if v is None:
        return "—"
    sign = "+" if v >= 0 else ""
    return f"{sign}{v:.1f}%"


def _segment_matches_keywords(segment, keywords, keyword_match, title_only):
    """Match expected cohort keywords against output segment text."""
    title = (segment.get("title") or "").lower()
    insights = (segment.get("insights") or "").lower()
    text = title if title_only else f"{title} {insights}"
    kws = [k.lower() for k in keywords]
    if keyword_match == "all":
        return all(kw in text for kw in kws)
    return any(kw in text for kw in kws)


# ── Check functions ───────────────────────────────────────────────────────────

def check_input_segments(input_data, name):
    """Check for problems in segments sent to the LLM."""
    issues = []
    segments = input_data.get("segments", [])
    baseline_metrics = input_data.get("baseline") or {}

    for s in segments:
        label = s.get("label", "")
        dims = s.get("dimensions", {})
        m = s.get("metrics", {})
        d = s.get("deltas", {})

        # Empty dimension value
        if not label or not any(v for v in dims.values()):
            issues.append((FAIL, f"Empty-dimension segment in input: dims={dims} — seed attribute key bug"))
            continue

        ssum, bsum = _interaction_input_rate_sum(s, baseline_metrics)
        if ssum <= bsum:
            issues.append((WARN,
                f"Noise segment in input: [{label}]  err+poor={ssum:.2f}% ≤ baseline_sum={bsum:.2f}% "
                "— server gate should have dropped this"))

    return issues


def check_output_segments(structured, name):
    """Compare LLM output segments against expectations."""
    issues = []
    exp = EXPECTATIONS.get(name)
    if not exp:
        issues.append((SKIP, "No expectations defined for this interaction"))
        return issues

    out_segs = structured.get("segments", [])
    eg = structured.get("everything_good", False)
    nd = structured.get("no_data_available", False)
    recs = structured.get("recommendations", [])

    # everything_good check
    expected_eg = exp.get("everything_good")
    if expected_eg is True:
        if not eg:
            issues.append((FAIL, f"Expected everything_good=true but got false — segments={len(out_segs)}"))
        else:
            issues.append((PASS, "everything_good=true as expected"))
        if len(out_segs) > 0:
            issues.append((FAIL, f"everything_good=true but {len(out_segs)} segments returned — must be empty"))
        if len(recs) > 0:
            issues.append((FAIL, f"everything_good=true but {len(recs)} recommendations returned — must be empty"))
        return issues  # no further segment checks needed

    # Segment count
    seg_count = len(out_segs)
    smin = exp["segments_min"]
    smax = exp["segments_max"]
    if seg_count < smin:
        issues.append((FAIL, f"Segment count {seg_count} < expected min {smin}"))
    elif seg_count > smax:
        issues.append((WARN, f"Segment count {seg_count} > expected max {smax} — possible padding"))
    else:
        issues.append((PASS, f"Segment count {seg_count} in expected range [{smin},{smax}]"))

    # Output segments — raw err+poor should beat baseline (same idea as pulse-server gate).
    for s in out_segs:
        title = s.get("title", "")
        if not _interaction_output_rates_outrank_baseline(s):
            metrics = {m["metric_id"]: m for m in s.get("metrics", [])}
            er_m = metrics.get("error_rate", {})
            pu_m = metrics.get("poor_user_pct", {})
            vs = float(er_m.get("value_number") or 0) + float(pu_m.get("value_number") or 0)
            bs = float(er_m.get("baseline_number") or 0) + float(pu_m.get("baseline_number") or 0)
            issues.append((WARN,
                f"Noise segment in output: [{title}]  err+poor={vs:.2f} ≤ baseline_sum={bs:.2f} "
                "from structured metrics — narrative should not uplift below-baseline slices"))

    # Forbidden keywords
    all_text = " ".join([
        s.get("title", "") + " " + s.get("insights", "")
        for s in out_segs
    ]).lower()
    for kw in exp.get("forbidden_keywords", []):
        if kw.lower() in all_text:
            issues.append((FAIL, f"Forbidden keyword '{kw}' found in output segments — direction filter or eligibility gate broken"))

    # Expected segment keyword checks
    for exp_seg in exp.get("expected_segments", []):
        keywords = exp_seg["keywords"]
        is_borderline = exp_seg.get("borderline", False)
        min_err = exp_seg.get("min_err_delta")
        keyword_match = exp_seg.get("keyword_match", "any")
        title_only = not exp_seg.get("match_insights", False)
        matched_seg = None
        for s in out_segs:
            if _segment_matches_keywords(s, keywords, keyword_match, title_only):
                matched_seg = s
                break
        if matched_seg is None:
            level = WARN if is_borderline else FAIL
            label = f"[{'/'.join(keywords)}]"
            issues.append((level, f"Expected segment {label} not found in output{'  (borderline volume — acceptable)' if is_borderline else ''}"))
        else:
            title = matched_seg.get("title", "")
            if min_err is not None:
                metrics = {m["metric_id"]: m for m in matched_seg.get("metrics", [])}
                err_m = metrics.get("error_rate", {})
                derr = _delta(err_m.get("value_number"), err_m.get("baseline_number"))
                if derr is not None and derr < min_err:
                    issues.append((WARN, f"Segment [{title}]: Δerror_rate={_fmt_delta(derr)} below expected min {_fmt_delta(min_err)}"))
                else:
                    issues.append((PASS, f"Segment [{title}] found with sufficient error delta ({_fmt_delta(derr)})"))
            else:
                issues.append((PASS, f"Segment [{title}] found"))

    return issues


def check_error_attribution(structured, name):
    """Check error attribution for add_to_cart."""
    if name != "add_to_cart":
        return []
    issues = []
    ea = structured.get("error_attribution") or {}
    insights = structured.get("error_attribution_insights") or []
    attributions = ea.get("relatedAttributions", [])
    signals = {i.get("signal") for i in insights}

    for expected_signal in ["anr", "non_fatal", "api"]:
        if expected_signal not in signals:
            issues.append((FAIL, f"Error attribution: missing signal '{expected_signal}'"))
        else:
            issues.append((PASS, f"Error attribution: signal '{expected_signal}' present"))

    if not attributions:
        issues.append((WARN, "Error attribution: relatedAttributions is empty — Track B data may not be seeded"))
    else:
        issues.append((PASS, f"Error attribution: {len(attributions)} attribution rows present"))

    return issues


# ── Per-interaction audit ─────────────────────────────────────────────────────

def audit_one(name, token, project_id, host, audit_date):
    result = {
        "name": name,
        "input_ok": None,
        "output_ok": None,
        "issues": [],
        "input_segments": [],
        "output_segments": [],
        "mode": "?",
        "baseline": {},
        "everything_good": False,
        "no_data_available": False,
        "cached": False,
    }

    # 1. Input segments
    url_rc = f"{host}/v1/interactions/{name}/root-cause?date={audit_date}"
    rc, err = _get(url_rc, token, project_id)
    if err:
        result["issues"].append((FAIL, f"Input API error: {err}"))
        result["input_ok"] = False
    else:
        data = rc.get("data", rc)
        result["mode"] = data.get("mode", "?")
        result["baseline"] = data.get("baseline", {})
        result["input_segments"] = data.get("segments", [])
        input_issues = check_input_segments(data, name)
        result["issues"].extend(input_issues)
        result["input_ok"] = not any(v == FAIL for v, _ in input_issues)

    # 2. LLM output
    url_rca = f"{host}/v1/ai/rca/report"
    rca, err = _post(url_rca, {"rcaType": "INTERACTION", "entityKey": name}, token, project_id)
    if err:
        result["issues"].append((FAIL, f"LLM output API error: {err}"))
        result["output_ok"] = False
    else:
        report = rca.get("report", {})
        structured = report.get("structured", {})
        result["cached"] = rca.get("cached", False)
        result["everything_good"] = structured.get("everything_good", False)
        result["no_data_available"] = structured.get("no_data_available", False)
        result["output_segments"] = structured.get("segments", [])

        output_issues = check_output_segments(structured, name)
        ea_issues = check_error_attribution(structured, name)
        result["issues"].extend(output_issues)
        result["issues"].extend(ea_issues)
        result["output_ok"] = not any(v == FAIL for v, _ in output_issues + ea_issues)

    return result


# ── Pretty printer ────────────────────────────────────────────────────────────

def _seg_line(s, is_input=True):
    if is_input:
        label = s.get("label") or "(empty)"
        dims = s.get("dimensions", {})
        m = s.get("metrics", {})
        d = s.get("deltas", {})
        vol = m.get("volume", "?")
        prob = m.get("problematic_count")
        prob_s = "—" if prob is None else str(prob)
        err = m.get("error_rate")
        pup = m.get("poor_user_pct")
        derr = d.get("error_rate")
        dpup = d.get("poor_user_pct")
        err_s = f"{err:.1f}%" if err is not None else "—"
        pup_s = f"{pup:.1f}%" if pup is not None else "—"
        return (f"    INPUT  [{label}]  vol={vol}  prob={prob_s}  "
                f"err={err_s}  poor={pup_s}  "
                f"Δerr={_fmt_delta(derr)}  Δpoor={_fmt_delta(dpup)}")
    else:
        title = s.get("title", "?")
        rank = s.get("rank", "?")
        metrics = {m["metric_id"]: m for m in s.get("metrics", [])}
        vol = metrics.get("volume", {}).get("value_number", "?")
        err_m = metrics.get("error_rate", {})
        pup_m = metrics.get("poor_user_pct", {})
        derr = _delta(err_m.get("value_number"), err_m.get("baseline_number"))
        dpup = _delta(pup_m.get("value_number"), pup_m.get("baseline_number"))
        prob_row = metrics.get("problematic_count", {})
        prob_v = prob_row.get("value_number")
        if prob_v is None:
            prob_s = "—"
        elif isinstance(prob_v, float) and prob_v == int(prob_v):
            prob_s = str(int(prob_v))
        else:
            prob_s = str(prob_v)
        return (f"    OUTPUT [#{rank}] [{title}]  vol={vol}  prob={prob_s}  "
                f"Δerr={_fmt_delta(derr)}  Δpoor={_fmt_delta(dpup)}")


def print_result(r, verbose=True):
    sep = "─" * 72
    name = r["name"]
    fails = sum(1 for v, _ in r["issues"] if v == FAIL)
    warns = sum(1 for v, _ in r["issues"] if v == WARN)
    overall = FAIL if fails > 0 else (WARN if warns > 0 else PASS)

    print(f"\n{sep}")
    print(f"  {_c(overall, overall)}  {name}  "
          f"(mode={r['mode']}  cached={r['cached']}  "
          f"eg={r['everything_good']}  nd={r['no_data_available']}  "
          f"in={len(r['input_segments'])}→out={len(r['output_segments'])})")
    print(sep)

    if verbose:
        baseline = r["baseline"]
        if baseline and baseline.get('volume') is not None:
            print(f"  Baseline: vol={baseline.get('volume')}  "
                  f"err={baseline.get('error_rate', 0) or 0:.2f}%  "
                  f"problematic={baseline.get('problematic_count')}")

        for s in r["input_segments"]:
            print(_seg_line(s, is_input=True))
        for s in r["output_segments"]:
            print(_seg_line(s, is_input=False))

        exp = EXPECTATIONS.get(name, {})
        notes = exp.get("special_notes", [])
        df = exp.get("direction_filter_note")
        if df:
            print(f"\n  ⚠ Direction filter: {df}")
        if notes:
            for n in notes:
                print(f"  ℹ {n}")

    print()
    for verdict, msg in r["issues"]:
        print(f"  {_c(verdict, f'[{verdict}]')} {msg}")


# ── Summary table ─────────────────────────────────────────────────────────────

def print_summary(results):
    sep = "=" * 72
    print(f"\n\n{sep}")
    print("  SUMMARY")
    print(sep)
    header = f"{'Interaction':<26} {'Result':<6} {'Fails':<6} {'Warns':<6} {'In→Out':<8} {'Mode'}"
    print(header)
    print("─" * 72)

    total_pass = total_fail = total_warn = 0
    for r in results:
        fails = sum(1 for v, _ in r["issues"] if v == FAIL)
        warns = sum(1 for v, _ in r["issues"] if v == WARN)
        overall = FAIL if fails > 0 else (WARN if warns > 0 else PASS)
        total_pass += overall == PASS
        total_fail += overall == FAIL
        total_warn += overall == WARN
        seg_io = f"{len(r['input_segments'])}→{len(r['output_segments'])}"
        print(f"  {r['name']:<24} {_c(overall, overall):<16} {str(fails):<6} {str(warns):<6} {seg_io:<8} {r['mode']}")

    print("─" * 72)
    print(f"  Total: {_c(PASS, str(total_pass))} pass  "
          f"{_c(FAIL, str(total_fail))} fail  "
          f"{_c(WARN, str(total_warn))} warn")
    print(sep)


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="RCA segment audit with expected-output comparison")
    parser.add_argument("--token", default=os.environ.get("RCA_TOKEN"), help="JWT bearer token")
    parser.add_argument("--project", default=os.environ.get("RCA_PROJECT", DEFAULT_PROJECT))
    parser.add_argument("--host", default=os.environ.get("RCA_HOST", DEFAULT_HOST))
    parser.add_argument("--date", default=os.environ.get("RCA_DATE", DEFAULT_DATE))
    parser.add_argument("--interaction", default=None, help="Audit a single interaction")
    parser.add_argument("--quiet", action="store_true", help="Only show issues, not segment lines")
    args = parser.parse_args()

    if not args.token:
        print("ERROR: --token or RCA_TOKEN env var required", file=sys.stderr)
        sys.exit(1)

    interactions = [args.interaction] if args.interaction else list(EXPECTATIONS.keys())

    print(f"RCA Audit  |  host={args.host}  project={args.project}  date={args.date}")
    print("Input gate mirrors server: (error_rate + poor_user_pct) segment > baseline.\n")
    print(f"Checking {len(interactions)} interaction(s)...\n")

    results = []
    for name in interactions:
        sys.stdout.write(f"  → {name}...")
        sys.stdout.flush()
        r = audit_one(name, args.token, args.project, args.host, args.date)
        results.append(r)
        fails = sum(1 for v, _ in r["issues"] if v == FAIL)
        warns = sum(1 for v, _ in r["issues"] if v == WARN)
        overall = FAIL if fails > 0 else (WARN if warns > 0 else PASS)
        print(f" {_c(overall, overall)}")

    for r in results:
        print_result(r, verbose=not args.quiet)

    print_summary(results)


if __name__ == "__main__":
    main()
