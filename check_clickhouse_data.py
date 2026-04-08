#!/usr/bin/env python3
"""
Check if there are sessions in ClickHouse matching the query criteria.
"""

import os
import json
from datetime import datetime, timezone
import requests

# Check if ClickHouse is running and has data
clickhouse_url = "http://localhost:8123"
clickhouse_user = "pulse_user"
clickhouse_password = "pulse_password"

# Query to check if data exists
test_query = """
SELECT COUNT() as total_spans, COUNT(DISTINCT SessionId) as unique_sessions
FROM otel.otel_traces
WHERE 
  ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
  AND Platform = 'Android'
  AND OsVersion = '14'
"""

print("=" * 80)
print("CHECK 1: Do we have data in ClickHouse?")
print("=" * 80)

try:
    response = requests.post(
        clickhouse_url,
        data=test_query,
        auth=(clickhouse_user, clickhouse_password),
        timeout=5
    )
    print(f"Status: {response.status_code}")
    print(f"Response: {response.text}")
except Exception as e:
    print(f"Error connecting to ClickHouse: {e}")
    print("ClickHouse may not be running at http://localhost:8123")

print("\n" + "=" * 80)
print("CHECK 2: Session evidence query")
print("=" * 80)

evidence_query = """
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
    AND Platform = 'Android'
    AND OsVersion = '14'
)
GROUP BY SessionId
HAVING
  (error_rate > 0.0667)
  OR (avg_apdex < 0.1977)
ORDER BY
  error_count DESC,
  avg_apdex ASC
LIMIT 5
"""

try:
    response = requests.post(
        clickhouse_url,
        data=evidence_query,
        auth=(clickhouse_user, clickhouse_password),
        timeout=5
    )
    print(f"Status: {response.status_code}")
    print(f"Response:\n{response.text}")
except Exception as e:
    print(f"Error: {e}")

print("\n" + "=" * 80)
print("CHECK 3: Check if attributes exist")
print("=" * 80)

attrs_query = """
SELECT 
  SpanAttributes['pulse.interaction.apdex_score'] as apdex,
  SpanAttributes['pulse.interaction.is_error'] as is_error,
  SessionId
FROM otel.otel_traces
WHERE 
  ProjectId = 'default-project'
  AND SpanName = 'MatchCardClickedToMatchDetailLoaded'
  AND Timestamp >= '2026-04-07 00:00:00'
  AND Timestamp < '2026-04-08 00:00:00'
LIMIT 5
"""

try:
    response = requests.post(
        clickhouse_url,
        data=attrs_query,
        auth=(clickhouse_user, clickhouse_password),
        timeout=5
    )
    print(f"Status: {response.status_code}")
    print(f"Response:\n{response.text}")
except Exception as e:
    print(f"Error: {e}")
