#!/usr/bin/env python3
"""
Test the new query logic - just return worst sessions in segment, no HAVING filters.
"""

import requests
import json

clickhouse_url = "http://localhost:8123"
clickhouse_user = "pulse_user"
clickhouse_password = "pulse_password"

def test_segment(name, dimensions):
    print("\n" + "=" * 80)
    print(f"TEST: {name}")
    print("=" * 80)
    
    print(f"\nDimensions: {json.dumps(dimensions, indent=2)}")
    
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
            if lines and lines[0]:
                print(f"Sessions found: {len(lines)}")
                print(f"\nResults:")
                for line in lines:
                    parts = line.split('\t')
                    if len(parts) >= 5:
                        session_id, error_count, total, apdex, error_rate = parts[0:5]
                        print(f"  SessionId: {session_id}")
                        print(f"    Errors: {error_count}/{total}")
                        print(f"    Error Rate: {error_rate}")
                        print(f"    Avg Apdex: {apdex}")
            else:
                print("  (No sessions found)")
        else:
            print(f"Error: {response.text[:500]}")
            
    except Exception as e:
        print(f"Exception: {e}")

# Test Segment 1 - more specific dimensions
test_segment(
    "Segment 1: Android + 14 + 9.6.1_10960704 + 22101316I",
    {
        "Platform": "Android",
        "OsVersion": "14",
        "AppVersion": "9.6.1_10960704",
        "DeviceModel": "22101316I"
    }
)

# Test Segment 2 - broader dimensions
test_segment(
    "Segment 2: Android + 14",
    {
        "Platform": "Android",
        "OsVersion": "14"
    }
)

# Test root level - all sessions
test_segment(
    "Segment 3: All (Platform Android only)",
    {
        "Platform": "Android"
    }
)
