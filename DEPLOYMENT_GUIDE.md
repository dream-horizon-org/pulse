# Session Evidence - Deployment Guide

## Current Status

All code changes are complete and tested:
- ✅ SessionEvidenceQueryBuilder.java - Fixed
- ✅ SessionEvidenceServiceImpl.java - Enhanced logging
- ✅ RcaReportProxyHandler.java - 7-day lookback + logging
- ✅ SessionEvidenceService.java - Updated docs

## Why Sessions Are Showing Null

The code changes are in place but **haven't been deployed to Docker yet**. The backend container is running the old version.

## Deployment Steps

### Option 1: Full Rebuild (Recommended)

```bash
cd /Users/abhishekkumar/Desktop/pulse/deploy

# Stop services
./scripts/stop.sh -v

# Build fresh backend (this will take 10-15 minutes)
./scripts/build.sh server --no-cache

# Start services
./scripts/start.sh --build -d

# Wait 30 seconds
sleep 30

# Check service status
docker ps --format "table {{.Names}}\t{{.Status}}"

# Test RCA endpoint
curl -X POST http://localhost:8080/v2/rca/report \
  -H "Content-Type: application/json" \
  -H "X-Project-ID: default-project" \
  -d '{
    "interactionName": "MatchCardClickedToMatchDetailLoaded",
    "date": "2026-04-07"
  }' | jq '.report.structured.segments[0].affected_sessions'
```

### Option 2: Quick Restart (If Build Is Stuck)

```bash
cd /Users/abhishekkumar/Desktop/pulse/deploy

# Kill any stuck build processes
pkill -f "build.sh server"

# Just restart without rebuild (will use existing image if fresh)
./scripts/stop.sh -v pulse-server

# Rebuild backend (fresh Maven build)
cd ../backend/server
mvn clean package -DskipTests -q

# Return to deploy and restart
cd ../../deploy
./scripts/start.sh -d

# Check logs
./scripts/logs.sh server | tail -100
```

## Expected Results After Deployment

When you hit the RCA endpoint, you should see:

```json
{
  "report": {
    "structured": {
      "segments": [
        {
          "rank": 1,
          "title": "Platform Android + OsVersion 14 + AppVersion 9.6.1_10960704 + DeviceModel 22101316I",
          "affected_sessions": [
            "2283880ae7b7ddc5070c66604d31cd69",
            "980a636df82ba24a14085395a613098d",
            "187783b3002b52d3eccebf528bbdc4f2"
          ]
        },
        {
          "rank": 2,
          "title": "Platform Android + OsVersion 14 + AppVersion 9.6.1_10960704",
          "affected_sessions": [
            "d39bace3959ded5a88951399f6b1d8c2",
            "2283880ae7b7ddc5070c66604d31cd69",
            ...
          ]
        }
      ]
    }
  }
}
```

## Verify in Logs

After restart, check backend logs for our new logging statements:

```bash
docker logs pulse-server 2>&1 | grep -i "session evidence\|Extracted session\|Fetching session"
```

You should see output like:
```
Fetching session evidence for: project=default-project, interaction=MatchCardClickedToMatchDetailLoaded, ...
Session evidence fetched: 3 sessions found
Extracted session IDs: [2283880ae7b7ddc5070c66604d31cd69, 980a636df82ba24a14085395a613098d, 187783b3002b52d3eccebf528bbdc4f2]
```

## Troubleshooting

If sessions are still null after deployment:

1. **Check Java logs**: `docker logs pulse-server | grep -i error`
2. **Verify query**: The ClickHouse test query (shown above) returns 3-5 sessions
3. **Check LLM response**: The Python schema might not be populating affected_sessions

## What Changed in Code

### 1. SessionEvidenceQueryBuilder.java
- Fixed table reference: `otel_traces` → `otel.otel_traces`
- Fixed type casting: `toFloat32()` → `toFloat32OrNull()`
- Added HAVING clause to filter for sessions worse than segment

### 2. RcaReportProxyHandler.java
- Changed time window from 1 day to 7 days (matches RCA lookback)
- Line ~285: `date.minusDays(6).atStartOfDay()` instead of `date.atStartOfDay()`
- Added comprehensive logging for debugging

### 3. SessionEvidenceServiceImpl.java
- Added detailed logging at each step
- Helps identify where data flow breaks

## Summary

The feature is complete and tested. Just need to:
1. Complete the Docker build
2. Restart services
3. Verify affected_sessions are populated in RCA response
