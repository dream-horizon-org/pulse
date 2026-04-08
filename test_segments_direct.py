#!/usr/bin/env python3
"""
Direct ClickHouse test for the exact segment from user's RCA response.
"""

import requests
import json

clickhouse_url = "http://localhost:8123"
clickhouse_user = "pulse_user"
clickhouse_password = "pulse_password"

# Test with the exact segment from user's response
segment_1_dimensions = {
    "Platform": "Android",
    "OsVersion": "14",
    "AppVersion": "9.6.1_10960704",
    "DeviceModel": "22101316I"
}

segment_1_metrics = {
    "error_rate": 5.0,  # 5%
    "apdex": 0.03501888128392731  # ~0.035
}

segment_2_dimensions = {
    "Platform": "Android",
    "OsVersion": "14"
}

segment_2_metrics = {
    "error_rate": 6.666666666666667,  # 6.67%
    "apdex": 0.19770980140935304  # ~0.197
}

def test_segment(name, dimensions, metrics):
    print("\n" + "=" * 80)
    print(f"TEST: {name}")
    print("=" * 80)
    
    print(f"\nDimensions: {json.dumps(dimensions, indent=2)}")
    print(f"Metrics: {json.dumps(metrics, indent=2)}")
    
    # Build query
    error_rate_pct = metrics["error_rate"]
    apdex = metrics["apdex"]
    error_rate_decimal = error_rate_pct / 100.0
    
    # Build dimension filters
    dimension_filters = ""
    for key, value in dimensions.items():
        dimension_filters += f"    AND {key} = '{value}'\n"
    
    query = f"""
SELECT 
  SessionId,
  countIf(is_error = 'true') as error_count,
  count() as total_interactions,
  avg(toFloat32OrNull(apdex_score)) as avg_apdex,
  (error_count / total_interactions) as error_rate
FROM (
  SELECT
    SessionId,
    SpanAttributes['pulse.interaction.is_error'] as is_error,
    SpanAttributes['pulse.interaction.apdex_score'] as apdex_score
  FROM otel.otel_traces
  WHERE
    ProjectId = 'default-project'
    AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
    AND Timestamp >= '2026-04-07 00:00:00'
    AND Timestamp < '2026-04-08 00:00:00'
    AND SessionId != ''
{dimension_filters})
GROUP BY SessionId
HAVING
  (error_rate > {error_rate_decimal})
  OR (avg_apdex < {apdex})
ORDER BY
  error_count DESC,
  avg_apdex ASC
LIMIT 5
"""
    
    print(f"\nQuery:\n{query}")
    
    try:
        response = requests.post(
            clickhouse_url,
            data=query,
            auth=(clickhouse_user, clickhouse_password),
            timeout=10
        )
        
        print(f"\nStatus: {response.status_code}")
        
        if response.status_code == 200:
            lines = response.text.strip().split('\n')
            print(f"Sessions found: {len(lines)}")
            if lines and lines[0]:
                print(f"\nResults:")
                for line in lines:
                    print(f"  {line}")
            else:
                print("  (No sessions found)")
        else:
            print(f"Error: {response.text[:500]}")
            
    except Exception as e:
        print(f"Exception: {e}")

# Test both segments
test_segment(
    "Segment 1: Android + 14 + 9.6.1_10960704 + 22101316I",
    segment_1_dimensions,
    segment_1_metrics
)

test_segment(
    "Segment 2: Android + 14",
    segment_2_dimensions,
    segment_2_metrics
)

# Also test without dimension filters to see if data exists
print("\n" + "=" * 80)
print("DEBUG: Check if any data exists without dimension filters")
print("=" * 80)

debug_query = """
SELECT COUNT(DISTINCT SessionId) as unique_sessions
FROM otel.otel_traces
WHERE
  ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
"""

try:
    response = requests.post(
        clickhouse_url,
        data=debug_query,
        auth=(clickhouse_user, clickhouse_password),
        timeout=10
    )
    print(f"Status: {response.status_code}")
    print(f"Response: {response.text}")
except Exception as e:
    print(f"Exception: {e}")
