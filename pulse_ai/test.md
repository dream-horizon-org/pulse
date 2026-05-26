# Testing Screen RCA V2 AI Response with curl

## Step 1: Get Backend Problems

Fetch pre-ranked problems from the backend service.

```bash
curl -X GET "http://localhost:8080/v1/screens/com.fc.home/root-cause/v2?windowEnd=2026-03-17T23:59:59Z" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtb2NrLXVzZXItMSIsImVtYWlsIjoidXNlcjFAZXhhbXBsZS5jb20iLCJuYW1lIjoiVGVzdCBVc2VyIDEiLCJ0eXBlIjoiYWNjZXNzIiwidGVuYW50SWQiOiJkZWZhdWx0IiwiaWF0IjoxNzc5MTkzODY1LCJleHAiOjE3NzkyODAyNjV9.p42dD0dw4n2TmxU-NNH2RZl3H4BR6xEgnAWatRtAeFM" \
  -H "X-Project-ID: default-project" \
  -H "Content-Type: application/json"
```

### Optional Query Parameters

| Parameter | Description | Example |
|---|---|---|
| `windowEnd` | ISO-8601 timestamp | `?windowEnd=2026-05-20T12:00:00Z` |
| `forceRefresh` | Skip cache | `?forceRefresh=true` |

---

## Step 2: Call Pulse AI Directly (Primary LLM Test)

Use this endpoint to directly test the AI-generated RCA response.

```bash
curl -X POST "http://localhost:8000/rca/screen-report/v2" \
  -H "X-Project-ID: default-project" \
  -H "Content-Type: application/json" \
  -d '{
    "screenName": "com.fc.home",
    "start": "2026-03-14T00:00:00Z",
    "end": "2026-03-17T23:59:59Z",
    "problems": [
      {
        "problemType": "network_latency",
        "rank": 1,
        "weightage": 0.25,
        "mostAffectedSegment": "Platform:Linux + NetworkProvider:T-Mobile",
        "metricId": "network_latency_p95",
        "metrics": {
          "affectedVolume": 125,
          "rate": "31.58%",
          "p95Ms": 3800
        },
        "specificIssues": null
      },
      {
        "problemType": "crashes",
        "rank": 2,
        "weightage": 0.25,
        "mostAffectedSegment": "AppVersion:5.1.0",
        "metricId": "crash_rate",
        "metrics": {
          "affectedVolume": 45,
          "rate": "4.20%"
        },
        "specificIssues": [
          {
            "groupId": "grp_abc123",
            "issue": "NullPointerException in ViewParent",
            "count": 30
          }
        ]
      }
    ],
    "evidences": {
      "sessions": ["sess_a1b2c3d4", "sess_e5f6g7h8"],
      "heatmapAvailable": true
    }
  }'
```

---

## Step 3: Alternative Endpoint (Alias)

Same payload using the alias endpoint.

```bash
curl -X POST "http://localhost:8000/screen-rca/v2/report" \
  -H "X-Project-ID: default-project" \
  -H "Content-Type: application/json" \
  -d '{
    "screenName": "com.fc.home",
    "start": "2026-03-14T00:00:00Z",
    "end": "2026-03-17T23:59:59Z",
    "problems": [
      {
        "problemType": "network_latency",
        "rank": 1,
        "weightage": 0.25,
        "mostAffectedSegment": "Platform:Linux + NetworkProvider:T-Mobile",
        "metricId": "network_latency_p95",
        "metrics": {
          "affectedVolume": 125,
          "rate": "31.58%",
          "p95Ms": 3800
        },
        "specificIssues": null
      },
      {
        "problemType": "crashes",
        "rank": 2,
        "weightage": 0.25,
        "mostAffectedSegment": "AppVersion:5.1.0",
        "metricId": "crash_rate",
        "metrics": {
          "affectedVolume": 45,
          "rate": "4.20%"
        },
        "specificIssues": [
          {
            "groupId": "grp_abc123",
            "issue": "NullPointerException in ViewParent",
            "count": 30
          }
        ]
      }
    ],
    "evidences": {
      "sessions": ["sess_a1b2c3d4", "sess_e5f6g7h8"],
      "heatmapAvailable": true
    }
  }'
```

---

## Step 4: Backend Proxy Flow (Production Path)

This route goes through the backend AI proxy.

```bash
curl -X POST "http://localhost:8080/v1/ai/rca/screen-report/v2" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtb2NrLXVzZXItMSIsImVtYWlsIjoidXNlcjFAZXhhbXBsZS5jb20iLCJuYW1lIjoiVGVzdCBVc2VyIDEiLCJ0eXBlIjoiYWNjZXNzIiwidGVuYW50SWQiOiJkZWZhdWx0IiwiaWF0IjoxNzc5MTkzODY1LCJleHAiOjE3NzkyODAyNjV9.p42dD0dw4n2TmxU-NNH2RZl3H4BR6xEgnAWatRtAeFM" \
  -H "X-Project-ID: default-project" \
  -H "Content-Type: application/json" \
  -d '{
    "screenName": "com.fc.home",
    "start": "2026-03-14T00:00:00Z",
    "end": "2026-03-17T23:59:59Z",
    "problems": [
      {
        "problemType": "network_latency",
        "rank": 1,
        "weightage": 0.25,
        "mostAffectedSegment": "Platform:Linux + NetworkProvider:T-Mobile",
        "metricId": "network_latency_p95",
        "metrics": {
          "affectedVolume": 125,
          "rate": "31.58%",
          "p95Ms": 3800
        },
        "specificIssues": null
      },
      {
        "problemType": "crashes",
        "rank": 2,
        "weightage": 0.25,
        "mostAffectedSegment": "AppVersion:5.1.0",
        "metricId": "crash_rate",
        "metrics": {
          "affectedVolume": 45,
          "rate": "4.20%"
        },
        "specificIssues": [
          {
            "groupId": "grp_abc123",
            "issue": "NullPointerException in ViewParent",
            "count": 30
          }
        ]
      }
    ],
    "evidences": {
      "sessions": ["sess_a1b2c3d4", "sess_e5f6g7h8"],
      "heatmapAvailable": true
    }
  }'
```

---

# Expected AI Response

```json
{
  "report": {
    "structured": {
      "version": 2,
      "executive_summary": "com.fc.home is critically impacted: 31% of sessions hit slow network (T-Mobile/linux segment with p95 latency of 3.8 seconds). Crash rate is elevated at 4.2% on AppVersion 5.1.0, with the primary issue being NullPointerException in ViewParent (30 occurrences).",
      "problems": [
        {
          "problem_type": "network_latency",
          "rank": 1,
          "weightage": 0.25,
          "most_affected_segment": "Platform:Linux + NetworkProvider:T-Mobile",
          "metricId": "network_latency_p95"
        },
        {
          "problem_type": "crashes",
          "rank": 2,
          "weightage": 0.25,
          "most_affected_segment": "AppVersion:5.1.0",
          "metricId": "crash_rate"
        }
      ],
      "evidences": {
        "sessions": [
          "sess_a1b2c3d4",
          "sess_e5f6g7h8"
        ],
        "heatmap_available": true
      },
      "recommendations": [
        "Investigate T-Mobile network path for API endpoints — p95 latency is 3.8x above acceptable threshold.",
        "Roll back or hotfix AppVersion 5.1.0 — NullPointerException in ViewParent accounts for 67% of crashes.",
        "Profile memory usage on affected devices — combination of crashes and latency suggests resource contention.",
        "Add retry logic with exponential backoff for network calls on this screen to mitigate user-visible failures."
      ]
    }
  },
  "cached": false
}
```
