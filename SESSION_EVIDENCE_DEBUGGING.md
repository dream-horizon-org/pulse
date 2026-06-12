# Session Evidence Query Verification - Debug Report

## Issue
Session `s_1516` is appearing in Rank 1 (device_model: SM-A135F) but has multiple device models including iPhone16,1.

## Data Analysis

### Actual Session Dimensions (from ClickHouse)
```
Session s_1516 has interactions with:
- iPhone16,1 (OS 17.2)
- SM-A135F (OS 13)

Session s_1163 has interactions with:
- iPhone15,4 (OS 17.2)

Session s_1227 has interactions with:
- SM-A135F (OS 13)
- SM-S911B (OS 12)

Session s_1138 has interactions with:
- Realme 10 Pro (OS 13)
- SM-A135F (OS 13)
- SM-S911B (OS 13 and 12)
- SM-A546B (OS 13 and 12)
```

## Root Cause

The issue is that **sessions span multiple device models and OS versions** across different interactions. When the query filters by `DeviceModel = 'SM-A135F'`, it's getting:
- ALL interactions from that session with that device model
- But the session ID persists across all interactions, so it appears in multiple segments

### Query Verification

When testing with proper filters:
```sql
WHERE DeviceModel = 'SM-A135F' AND SpanName = 'app_launch'
```
Returns: `s_804`, `s_1291` ✓ (correct sessions for this segment)

But API is returning: `s_1516`, `s_1163` ❌ (wrong sessions)

## Possible Issues

### Issue #1: Cache Not Cleared
- The RCA result might be cached from before the fixes
- Need to clear both ClickHouse and MySQL cache
- Check: Is `exampleSessionIds` field actually being stored in cache?

### Issue #2: Segment Dimensions Not Passed Correctly
- The `segment.getDimensions()` might not include the device_model correctly
- Need to verify what dimensions map `"device_model: SM-A135F"` to the correct column filter

### Issue #3: SessionEvidenceService Getting Wrong Metrics
- The `segmentMetrics` might not match the actual segment thresholds
- Query builder uses these to create HAVING clause thresholds

## Verification Steps

1. **Check RootCauseSegment** in the payload from backend API response:
   - Does it have `exampleSessionIds` field?
   - What are the `dimensions` values?
   
2. **Verify Cache Status**:
   - Query ClickHouse root_cause_cache table
   - Check if `exampleSessionIds` is in the JSON?
   
3. **Log Session Evidence Query**:
   - Add logging to SessionEvidenceServiceImpl to print the actual SQL being executed
   - Verify dimensions and thresholds are correct

4. **Test Query Directly**:
   - Run the exact query the backend generates
   - Compare with manual query above

## Next Steps

1. Verify `exampleSessionIds` is in the RootCauseSegment response JSON
2. If missing, check if code changes were properly deployed
3. If present, verify the dimensions being passed match the segment title
4. Enable debug logging in SessionEvidenceServiceImpl to see the actual query

## Code Changes Made

1. **SessionEvidenceQueryBuilder** - Fixed ClickHouse syntax ✓
2. **RcaReportProxyHandler** - Store sessions per-segment ✓
3. **RootCauseSegment** - Added exampleSessionIds field ✓
4. **RCA Formatter Prompt** - Use segment sessions ✓

All changes appear correct, but verification needed that they're actually being used.
