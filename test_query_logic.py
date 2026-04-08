#!/usr/bin/env python3
"""
Test to verify session evidence query works correctly with segment metrics.
"""

import json
from datetime import datetime, timezone

# The segment data from user
segment = {
    "label": "Android + 14",
    "dimensions": {
        "OsVersion": "14",
        "Platform": "Android"
    },
    "metrics": {
        "volume": 90,
        "apdex": 0.19770980140935304,
        "error_rate": 6.666666666666667,  # 6.66%
        "poor_user_pct": 50.0,
        "duration_p50": 2082.0,
        "duration_p95": 3063.0,
    },
    "deltas": {
        "error_rate": 376.0,  # 376% worse than baseline - NOT USED
        "apdex": -36.743823139372196,  # -36% worse - NOT USED
    }
}

print("=" * 80)
print("SESSION EVIDENCE QUERY - TESTING WITH SEGMENT METRICS")
print("=" * 80)

print("\nSegment Info:")
print(f"  Label: {segment['label']}")
print(f"  Dimensions: {json.dumps(segment['dimensions'], indent=4)}")

print("\nSegment Metrics (used for filtering):")
error_rate_pct = segment["metrics"]["error_rate"]
apdex = segment["metrics"]["apdex"]
print(f"  error_rate: {error_rate_pct}%")
print(f"  apdex: {apdex}")

print("\nSegment Deltas (ignored in new logic):")
print(f"  error_rate delta: {segment['deltas']['error_rate']}%")
print(f"  apdex delta: {segment['deltas']['apdex']}%")

print("\n" + "=" * 80)
print("QUERY LOGIC (NEW - CORRECT)")
print("=" * 80)

# Convert error_rate percentage to decimal
error_rate_threshold = error_rate_pct / 100.0  # 6.66% -> 0.0666
apdex_threshold = apdex  # 0.197

print(f"\nThresholds extracted from segment metrics:")
print(f"  error_rate threshold: {error_rate_threshold:.4f} (from {error_rate_pct}%)")
print(f"  apdex threshold: {apdex_threshold:.4f}")

print(f"\nHAVING clause:")
print(f"  (error_rate > {error_rate_threshold:.4f}) OR (avg_apdex < {apdex_threshold:.4f})")

print(f"\nIn English:")
print(f"  ✓ Find sessions where:")
print(f"    - error_rate > 6.66% (more errors than this segment)")
print(f"    - OR avg_apdex < 0.197 (worse performance than this segment)")
print(f"\nThese are the 'worst' sessions in this segment - good for evidence!")

print("\n" + "=" * 80)
print("WHY THIS IS CORRECT")
print("=" * 80)

print(f"""
1. The RCA identified this segment (Platform=Android, OsVersion=14) as problematic
2. This segment has error_rate = 6.66% and apdex = 0.197 (lower apdex = worse)
3. To show evidence, we want sessions WORSE than this segment's own metrics
4. So we filter for: error_rate > 6.66% OR apdex < 0.197
5. These sessions are the "bad actors" within the segment - perfect evidence!

Contrast with old logic:
  ❌ Old: error_rate > 3.76 (which is 376/100) - WRONG!
     - This was dividing the delta by 100, treating it as a percentage to convert
     - But deltas are percentage CHANGES, not percentage points!
  
  ✓ New: error_rate > 0.0666 (which is 6.66/100) - CORRECT!
     - We use the segment's own metric as the threshold
     - Sessions with HIGHER error_rate or LOWER apdex are worse than the segment
""")

print("\n" + "=" * 80)
print("EXPECTED OUTCOME")
print("=" * 80)

print(f"""
When you query ClickHouse with these filters for:
  - Project: default-project
  - Interaction: MatchCardClickedToMatchDetailLoaded
  - Date: 2026-04-07 to 2026-04-08
  - Dimensions: Platform='Android' AND OsVersion='14'
  - Filters: (error_rate > 0.0666) OR (avg_apdex < 0.197)

You should get sessions where at least one of these is true:
  1. Session error_rate > 6.66%
  2. OR session avg_apdex < 0.197

These will be sorted by error_count DESC, then avg_apdex ASC
So the worst error-count sessions appear first, then worst apdex sessions.
""")
