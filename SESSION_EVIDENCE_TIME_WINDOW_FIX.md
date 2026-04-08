# Session Evidence - TIME WINDOW FIX ✓

## The Problem

Segment 1 was returning 0 sessions even though sessions **do exist** with those exact dimensions.

### Root Cause

**Time window mismatch:**
- RCA computes using **7-day lookback** (configurable)
- Session evidence was querying only **1 day** (the anchor date)
- Segment 1 sessions were on **2026-04-06**
- But query searched **2026-04-07 to 2026-04-08** ❌

## The Solution

Updated `RcaReportProxyHandler.java` to use **7-day lookback** for session evidence:

```java
// Before: Only 1 day
date.atStartOfDay() to date.plusDays(1).atStartOfDay()

// After: 7 days (same as RCA)
date.minusDays(6).atStartOfDay() to date.plusDays(1).atStartOfDay()
```

## Verification

With the 7-day window, we now get **4 sessions** for Segment 1:
- `2283880ae7b7ddc5070c66604d31cd69` - 2 errors in 6 interactions (33% error rate)
- `980a636df82ba24a14085395a613098d` - 0 errors in 6 interactions (0% error rate)
- `187783b3002b52d3eccebf528bbdc4f2` - Low apdex (0.032)
- `4033a2741601e1af2c89318a7b0c31ba` - Low apdex (0.058)

## Why This Matters

The RCA analysis examines data trends over time (typically 7 days). When we identify a problematic segment, the evidence sessions should come from the same lookback window, not just the anchor day.

## Files Modified

- `RcaReportProxyHandler.java` - Line 285: Changed time window from 1 day to 7 days lookback

## Expected Behavior Now

✓ Segment 1 (narrow): `affected_sessions` = [4 session IDs] ✓ (has evidence now!)
✓ Segment 2 (broad): `affected_sessions` = [5 session IDs] ✓ (still has evidence)

All segments should now have evidence where applicable!

## Backend Status

✓ Compiled successfully
✓ Ready for Docker rebuild and testing
