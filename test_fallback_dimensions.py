#!/usr/bin/env python3
"""
Test what sessions we get if we relax the dimension constraints.
"""

import requests

clickhouse_url = "http://localhost:8123"
clickhouse_user = "pulse_user"
clickhouse_password = "pulse_password"

def test_query(name, where_clause):
    print("\n" + "=" * 80)
    print(f"TEST: {name}")
    print("=" * 80)
    
    query = f"""
SELECT 
  SessionId,
  Platform,
  OsVersion,
  AppVersion,
  DeviceModel,
  countIf(is_error = 'true') as error_count,
  count() as total_interactions
FROM otel.otel_traces
WHERE ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
  {where_clause}
GROUP BY SessionId, Platform, OsVersion, AppVersion, DeviceModel
ORDER BY error_count DESC
LIMIT 5
"""
    
    try:
        response = requests.post(
            clickhouse_url,
            data=query,
            auth=(clickhouse_user, clickhouse_password),
            timeout=10
        )
        
        if response.status_code == 200:
            lines = response.text.strip().split('\n')
            if lines and lines[0]:
                print(f"Sessions found: {len(lines)}")
                print(f"\nResults:")
                print(response.text)
            else:
                print("  (No sessions found)")
        else:
            print(f"Error: {response.text[:200]}")
            
    except Exception as e:
        print(f"Exception: {e}")

# Test 1: All 4 dimensions (the one from RCA)
test_query(
    "All 4 dimensions (RCA Segment 1)",
    "AND Platform = 'Android' AND OsVersion = '14' AND AppVersion = '9.6.1_10960704' AND DeviceModel = '22101316I'"
)

# Test 2: Drop DeviceModel - use only Platform, OsVersion, AppVersion
test_query(
    "Drop DeviceModel - use only Platform, OsVersion, AppVersion",
    "AND Platform = 'Android' AND OsVersion = '14' AND AppVersion = '9.6.1_10960704'"
)

# Test 3: Drop AppVersion too - use only Platform, OsVersion
test_query(
    "Drop AppVersion too - use only Platform, OsVersion",
    "AND Platform = 'Android' AND OsVersion = '14'"
)

# Test 4: Show what combinations exist for Platform + OsVersion + AppVersion
print("\n" + "=" * 80)
print("Available DeviceModel values for Platform=Android + OsVersion=14 + AppVersion=9.6.1_10960704")
print("=" * 80)

query = """
SELECT DISTINCT DeviceModel, COUNT(DISTINCT SessionId) as session_count
FROM otel.otel_traces
WHERE ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
  AND Platform = 'Android'
  AND OsVersion = '14'
  AND AppVersion = '9.6.1_10960704'
GROUP BY DeviceModel
"""

response = requests.post(
    clickhouse_url,
    data=query,
    auth=(clickhouse_user, clickhouse_password),
    timeout=10
)
print(response.text)
