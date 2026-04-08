#!/usr/bin/env python3
"""
Deep inspection of dimension values in ClickHouse.
"""

import requests
import json

clickhouse_url = "http://localhost:8123"
clickhouse_user = "pulse_user"
clickhouse_password = "pulse_password"

print("=" * 80)
print("STEP 1: Get unique dimension values")
print("=" * 80)

# Check what Platform values exist
query1 = """
SELECT DISTINCT Platform
FROM otel.otel_traces
WHERE ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
LIMIT 100
"""

print("\nPlatform values:")
response = requests.post(
    clickhouse_url,
    data=query1,
    auth=(clickhouse_user, clickhouse_password),
    timeout=10
)
print(response.text)

# Check OsVersion values
query2 = """
SELECT DISTINCT OsVersion
FROM otel.otel_traces
WHERE ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
LIMIT 100
"""

print("\nOsVersion values:")
response = requests.post(
    clickhouse_url,
    data=query2,
    auth=(clickhouse_user, clickhouse_password),
    timeout=10
)
print(response.text)

# Check AppVersion values
query3 = """
SELECT DISTINCT AppVersion
FROM otel.otel_traces
WHERE ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
LIMIT 100
"""

print("\nAppVersion values:")
response = requests.post(
    clickhouse_url,
    data=query3,
    auth=(clickhouse_user, clickhouse_password),
    timeout=10
)
print(response.text)

# Check DeviceModel values
query4 = """
SELECT DISTINCT DeviceModel
FROM otel.otel_traces
WHERE ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
LIMIT 100
"""

print("\nDeviceModel values:")
response = requests.post(
    clickhouse_url,
    data=query4,
    auth=(clickhouse_user, clickhouse_password),
    timeout=10
)
print(response.text)

print("\n" + "=" * 80)
print("STEP 2: Check combination - Platform=Android AND OsVersion=14")
print("=" * 80)

query5 = """
SELECT 
  AppVersion,
  DeviceModel,
  COUNT(DISTINCT SessionId) as session_count,
  COUNT() as total_spans
FROM otel.otel_traces
WHERE ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
  AND Platform = 'Android'
  AND OsVersion = '14'
GROUP BY AppVersion, DeviceModel
ORDER BY session_count DESC
"""

print("\nAppVersion + DeviceModel combinations for Platform=Android + OsVersion=14:")
response = requests.post(
    clickhouse_url,
    data=query5,
    auth=(clickhouse_user, clickhouse_password),
    timeout=10
)
print(response.text)

print("\n" + "=" * 80)
print("STEP 3: Check if specific combo exists")
print("=" * 80)

query6 = """
SELECT 
  COUNT(DISTINCT SessionId) as session_count,
  COUNT() as total_spans
FROM otel.otel_traces
WHERE ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
  AND Platform = 'Android'
  AND OsVersion = '14'
  AND AppVersion = '9.6.1_10960704'
  AND DeviceModel = '22101316I'
"""

print("\nData for: Platform=Android + OsVersion=14 + AppVersion=9.6.1_10960704 + DeviceModel=22101316I")
response = requests.post(
    clickhouse_url,
    data=query6,
    auth=(clickhouse_user, clickhouse_password),
    timeout=10
)
print(response.text)

print("\n" + "=" * 80)
print("STEP 4: Show raw data sample with all dimensions")
print("=" * 80)

query7 = """
SELECT 
  Platform,
  OsVersion,
  AppVersion,
  DeviceModel,
  SessionId,
  COUNT() as span_count
FROM otel.otel_traces
WHERE ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
GROUP BY Platform, OsVersion, AppVersion, DeviceModel, SessionId
ORDER BY span_count DESC
LIMIT 20
"""

print("\nAll dimension combinations with session IDs:")
response = requests.post(
    clickhouse_url,
    data=query7,
    auth=(clickhouse_user, clickhouse_password),
    timeout=10
)
print(response.text)
