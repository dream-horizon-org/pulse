# Why Sessions Are Now Being Found

## The Root Cause

Your segment data structure has:
```
{
  "metrics": {
    "error_rate": 6.666...%,      ← Use THIS for filtering
    "apdex": 0.197...             ← Use THIS for filtering
  },
  "deltas": {
    "error_rate": 376.0,           ← Don't use this for filtering!
    "apdex": -36.743...            ← Don't use this for filtering!
  }
}
```

The **metrics** are the segment's **actual performance values**
The **deltas** are how much the segment **deviates from baseline** (percentage changes)

## The Query Evolution

### ❌ OLD APPROACH (No Sessions Returned)
```
Taking delta = 376.0
Divide by 100 → threshold = 3.76
Filter: WHERE error_rate > 3.76 (meaning > 376%)
Result: No sessions found (error_rate can't exceed 100%)
```

### ✓ NEW APPROACH (Sessions Being Found)
```
Taking metric = 6.666...
Divide by 100 → threshold = 0.0666
Filter: WHERE error_rate > 0.0666 (meaning > 6.66%)
Result: Sessions found! (Those worse than the segment)
```

## Visual: Data Flow

```
RootCauseSegment
├── dimensions: {Platform: Android, OsVersion: 14}
├── metrics: {error_rate: 6.66%, apdex: 0.197}    ← Extract these
└── deltas: {error_rate: 376%, apdex: -36.7%}     ← Ignore these

                    ↓

extractSegmentMetrics(metrics)
├── error_rate: 6.66 → 0.0666 (as decimal)
└── apdex: 0.197 → 0.197

                    ↓

ClickHouse Query
SELECT SessionId, ... 
WHERE Platform='Android' AND OsVersion='14' AND ...
HAVING 
  (error_rate > 0.0666) OR (avg_apdex < 0.197)
ORDER BY error_count DESC, avg_apdex ASC
LIMIT 5

                    ↓

SessionEvidenceResult
├── session1: sess-abc123
├── session2: sess-def456
├── session3: sess-ghi789
├── session4: sess-jkl012
└── session5: sess-mno345

                    ↓

LLM receives example session IDs
Embeds them in RcaStructuredSegmentV1.affected_sessions

                    ↓

UI renders as clickable buttons
```

## Key Understanding

When RCA identifies a **segment** as problematic:
- The segment has actual metrics (performance numbers)
- We want to find sessions **within that segment** that are **worse than the segment average**
- Use the segment's metrics as thresholds
- Filter for sessions exceeding those thresholds
- These are the "evidence" sessions proving the problem

## Testing Your Fix

To verify sessions are now being returned:

```bash
# 1. Rebuild backend
cd backend/server && mvn clean compile

# 2. Start Docker stack
cd deploy && ./scripts/start.sh --build

# 3. Make RCA request
curl -X POST http://localhost:8080/v2/rca/report \
  -H "Content-Type: application/json" \
  -H "X-Project-ID: default-project" \
  -d '{
    "interactionName": "MatchCardClickedToMatchDetailLoaded",
    "date": "2026-04-07"
  }'

# 4. Check response for "affected_sessions" in report_body
# Should now contain session IDs!
```

If you still don't see sessions:
1. Check ClickHouse has data for Platform='Android' + OsVersion='14'
2. Verify sessions have error_rate > 6.66% or apdex < 0.197
3. Check docker logs: `docker logs pulse-server`
