#!/usr/bin/env python3
"""
Debug script to check why sessions aren't being returned.
Tests the query logic with actual ClickHouse data.
"""

import json
import sys
from datetime import datetime, timezone

# Segment data from user
segment_data = {
    "label": "Android + 14",
    "dimensions": {
        "OsVersion": "14",
        "Platform": "Android"
    },
    "metrics": {
        "OsVersion": "14",
        "Platform": "Android",
        "volume": 90,
        "apdex": 0.19770980140935304,
        "error_rate": 6.666666666666667,
        "poor_user_pct": 50.0,
        "duration_p50": 2082.0,
        "duration_p95": 3063.0,
        "crash_rate": 0.0,
        "anr_rate": 0.0,
        "frozen_frame_rate": 0.0,
        "slow_frame_rate": 0.05003752814610958,
        "problematic_count": 48
    },
    "deltas": {
        "volume": -87.39495798319328,
        "apdex": -36.743823139372196,
        "error_rate": 376.0,  # THIS IS THE ISSUE
        "poor_user_pct": 141.0958904109589,
        "duration_p50": 42.57085286753025,
        "duration_p95": 0.0,
        "anr_rate": -100.0,
        "slow_frame_rate": 11.631342554534953
    }
}

print("=" * 80)
print("DEBUGGING SESSION EVIDENCE QUERY")
print("=" * 80)

print("\nSegment Data:")
print(json.dumps(segment_data, indent=2))

# Analyze deltas
print("\n" + "=" * 80)
print("DELTA ANALYSIS")
print("=" * 80)

error_rate_delta = segment_data["deltas"]["error_rate"]
apdex_delta = segment_data["deltas"]["apdex"]
segment_error_rate = segment_data["metrics"]["error_rate"]
segment_apdex = segment_data["metrics"]["apdex"]

print(f"\nSegment Metrics:")
print(f"  - error_rate: {segment_error_rate}%")
print(f"  - apdex: {segment_apdex}")

print(f"\nSegment Deltas:")
print(f"  - error_rate delta: {error_rate_delta}")
print(f"  - apdex delta: {apdex_delta}")

print(f"\nCurrent Query Logic (WRONG):")
print(f"  - Convert error_rate delta {error_rate_delta} to threshold: {error_rate_delta / 100.0}")
print(f"  - Filter: error_rate > {error_rate_delta / 100.0}")
print(f"  - Filter: avg_apdex < 0.5")
print(f"  ❌ ISSUE: Looking for error_rate > 3.76 (376%) which is impossible!")

print(f"\n" + "=" * 80)
print("PROPOSED FIX")
print("=" * 80)

print(f"\nInterpretation: Deltas are RELATIVE differences (%):")
print(f"  - Segment error_rate: {segment_error_rate}%")
print(f"  - Delta: +{error_rate_delta}% means this segment is +{error_rate_delta} percentage points worse")
print(f"  - So we should find sessions with error_rate > {segment_error_rate + error_rate_delta}%")

# But wait, that also seems wrong. Let me think...

print(f"\nAlternative: Deltas are percentage CHANGE from baseline:")
print(f"  - If delta = 376.0 means 376% worse (i.e., 3.76x higher)")
print(f"  - Baseline error_rate ≈ {segment_error_rate}%")
print(f"  - Segment is 3.76x worse, so baseline ≈ {segment_error_rate / 3.76}%")

print(f"\nAlternative: Deltas are just statistical variance, not directly comparable:")
print(f"  - The delta tells us the segment deviates by 376 percentage points")
print(f"  - But that doesn't make sense for error_rate which is 0-100%")

print(f"\n" + "=" * 80)
print("REAL ISSUE: The apdex_delta is -36.74")
print("=" * 80)
print(f"\nSegment apdex: {segment_apdex}")
print(f"Apdex delta: {apdex_delta}")
print(f"\nThis suggests the segment's apdex is 36.74 points LOWER than baseline.")
print(f"If baseline apdex ≈ (0.197 + 36.74) = ~37.0, that's way > 1.0 (impossible)")
print(f"\nSo deltas are likely percentage CHANGE:")
print(f"  - apdex_delta = -36.74 means segment apdex is 36.74% LOWER than baseline")
print(f"  - error_rate_delta = 376.0 means segment error_rate is 376.0% HIGHER than baseline")

print(f"\n" + "=" * 80)
print("CORRECT INTERPRETATION")
print("=" * 80)
print(f"\nIf we assume baseline metrics from the root level:")
print(f"  Root error_rate: 1.400560224089636%")
print(f"  Segment error_rate: 6.666666666666667%")
print(f"\nDelta calculation:")
baseline_error_rate = 1.400560224089636
segment_error_rate_actual = 6.666666666666667
calculated_delta = ((segment_error_rate_actual - baseline_error_rate) / baseline_error_rate) * 100
print(f"  ({segment_error_rate_actual} - {baseline_error_rate}) / {baseline_error_rate} * 100 = {calculated_delta}%")
print(f"\nBut deltas show: {error_rate_delta}%")
print(f"This is DIFFERENT! So deltas might be calculated differently...")

print(f"\n" + "=" * 80)
print("QUERY SHOULD LOOK FOR SESSIONS WHERE:")
print("=" * 80)
print(f"\n✓ Dimensions match: Platform='Android' AND OsVersion='14'")
print(f"✓ Interaction: MatchCardClickedToMatchDetailLoaded")
print(f"✓ Timeframe: 2026-04-07 to 2026-04-08")
print(f"\nThen for filtering:")
print(f"  Option A: error_rate > {segment_error_rate}% (6.66%)")
print(f"  Option B: error_rate > {segment_error_rate + (segment_error_rate * error_rate_delta / 100)}% (if delta is % change)")
print(f"  Option C: avg_apdex < {segment_apdex} ({segment_apdex})")
print(f"\nCurrent query uses:")
print(f"  - error_rate > {error_rate_delta / 100.0} (3.76) ❌ WRONG")
print(f"  - avg_apdex < 0.5 ❌ May be too strict")
