# Session Evidence Query Fix - Segment Metrics Approach

## The Problem

You were not getting any session evidence because the query was using the **wrong threshold logic**.

### What Was Wrong

The old code used `segmentDeltas` to calculate the filter threshold:

```java
// OLD - WRONG
Double errorRateDelta = segmentDeltas.getOrDefault("error_rate", 0.0);  // e.g., 376.0
double errorRateThreshold = errorRateDelta / 100.0;  // 376.0 / 100 = 3.76
// Query: error_rate > 3.76  ❌ LOOKING FOR SESSIONS WITH 376% ERROR RATE!
```

For your segment with `error_rate delta = 376.0`:
- Converted to: `error_rate > 3.76` (i.e., > 376%)
- **Problem**: Error rate is 0-100%, so no sessions can ever have 376% error rate!
- **Result**: Query returns 0 sessions ❌

### Why Deltas Are Wrong

The deltas you provided are **percentage changes from baseline**, not thresholds:
- `error_rate delta = 376.0` means the segment is **376% worse** than baseline
- It's NOT a percentage value to compare against
- It's a relative change metric, not suitable for direct filtering

## The Solution

Changed from using `segmentDeltas` to using `segmentMetrics`:

```java
// NEW - CORRECT
Double errorRateThreshold = segmentMetrics.getOrDefault("error_rate", 0.0);  // e.g., 6.666...
double errorRateThresholdDecimal = errorRateThreshold / 100.0;  // 6.666 / 100 = 0.0666
// Query: error_rate > 0.0666  ✓ LOOKING FOR SESSIONS WITH >6.66% ERROR RATE
```

### Why This Works

For your segment with `error_rate metric = 6.666...`:
- Converted to: `error_rate > 0.0666` (i.e., > 6.66%)
- **Logic**: Find sessions worse than the segment itself
- **Result**: Returns actual sessions ✓

## The Corrected Query

Now the `HAVING` clause uses the segment's own metrics as thresholds:

```sql
HAVING
  (error_rate > 0.0667)  -- Find sessions with more errors than the segment
  OR (avg_apdex < 0.1977)  -- OR find sessions with lower apdex (worse performance)
ORDER BY
  error_count DESC,
  avg_apdex ASC
LIMIT 5
```

## Files Changed

1. **SessionEvidenceQueryBuilder.java**
   - Changed parameter from `Map<String, Double> segmentDeltas` to `Map<String, Double> segmentMetrics`
   - Updated HAVING clause to use metrics as thresholds instead of dividing deltas
   - Changed OR logic for more flexible filtering

2. **SessionEvidenceService.java** (interface)
   - Updated method signature to use `segmentMetrics` instead of `segmentDeltas`

3. **SessionEvidenceServiceImpl.java**
   - Updated to pass `segmentMetrics` to query builder

4. **RcaReportProxyHandler.java**
   - Added `extractSegmentMetrics()` helper to convert `Map<String, Object> metrics` to `Map<String, Double>`
   - Extracts only `error_rate` and `apdex` from segment metrics
   - Passes segment metrics instead of deltas to SessionEvidenceService

## Expected Behavior

For your segment:
- Platform = Android
- OsVersion = 14
- error_rate = 6.66%
- apdex = 0.197

The query will now find up to 5 sessions where:
1. Session error_rate > 6.66%, OR
2. Session avg_apdex < 0.197

These are sorted by error count (descending) then apdex (ascending), giving you the "worst" sessions as evidence.

## Next Steps

1. Rebuild backend: `cd backend/server && mvn clean compile`
2. Run the full stack with your ClickHouse data
3. You should now see session IDs in the RCA report response
4. Sessions will appear as clickable buttons in the UI
