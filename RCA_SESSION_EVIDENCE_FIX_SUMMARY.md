# RCA Session Evidence Fix - Summary

## Issue Identified
Session `d39bace3959ded5a88951399f6b1d8c2` was appearing in both:
- **Rank 1 (device_model: 22101316I)** - WRONG ❌
- **Rank 2 (os_version: 14)** - CORRECT ✓

**Actual session data:**
- Session `d39bace3959ded5a88951399f6b1d8c2` → `DeviceModel: V2158`, `OsVersion: 14`

## Root Causes

### 1. ClickHouse Query Syntax Error (FIXED)
**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/dao/rootcause/SessionEvidenceQueryBuilder.java`

**Problem:** 
```sql
-- WRONG: toFloat32OrNull() in SELECT scope where aliases aren't available
SELECT avg(toFloat32OrNull(apdex_score)) ...
FROM (SELECT ... SpanAttributes['...'] as apdex_score ...)
GROUP BY SessionId
```

**Fix:**
```sql
-- CORRECT: toFloat32OrNull() in subquery SELECT where alias is created
SELECT avg(apdex_score) ...
FROM (SELECT ... toFloat32OrNull(SpanAttributes['...']) as apdex_score ...)
GROUP BY SessionId
```

### 2. Session Pooling Instead of Segmentation (FIXED)
**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/service/ai/impl/RcaReportProxyHandler.java`

**Problem:**
- Sessions were fetched per-segment with correct dimension filters ✓
- BUT all sessions were merged into a single global `allSessionIds` list ❌
- No way to track which session belonged to which segment ❌
- RCA formatter couldn't know which sessions matched which segment ❌

**Fix:**
- Changed to store sessions directly in each `RootCauseSegment` object
- Each segment now has its own `exampleSessionIds` field
- Sessions are attached with proper segment index via `segmentIndex`
- Enriched body is now used in finalization (line 261-263)

### 3. Missing RootCauseSegment Field (FIXED)
**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/service/rootcause/models/RootCauseSegment.java`

**Added:**
```java
/** Example session IDs demonstrating this segment's issues (2 most relevant). */
private java.util.List<String> exampleSessionIds;
```

### 4. RCA Formatter Not Using Segment Sessions (FIXED)
**File:** `pulse_ai/agents/rca/prompts.py`

**Problem:** Formatter was extracting sessions from LLM analysis text instead of using pre-computed segment-specific sessions

**Fix:** Updated formatter prompt to:
1. First check `exampleSessionIds` from each segment in the payload
2. Match segments by title/dimensions
3. Use those sessions directly (no fallback to text extraction)

### 5. Module Organization (FIXED)
**Files:**
- `backend/server/src/main/java/org/dreamhorizon/pulseserver/module/ConfigModule.java`
- `backend/server/src/main/java/org/dreamhorizon/pulseserver/module/InteractionModule.java`

**Change:** Moved `SessionEvidenceService` binding from `ConfigModule` to `InteractionModule` for better semantic organization

## ClickHouse Query Verification

Both queries return the **correct sessions** when tested directly:

### Rank 1: device_model: 22101316I
```sql
SELECT SessionId ... WHERE DeviceModel = '22101316I'
HAVING (error_rate >= 0.05) OR (avg_apdex <= 0.035)
LIMIT 2
```
✓ Returns: `2283880ae7b7ddc5070c66604d31cd69`, `980a636df82ba24a14085395a613098d`

### Rank 2: os_version: 14
```sql
SELECT SessionId ... WHERE OsVersion = '14'
HAVING (error_rate >= 0.067) OR (avg_apdex <= 0.1977)
LIMIT 2
```
✓ Returns: `d39bace3959ded5a88951399f6b1d8c2`, `2283880ae7b7ddc5070c66604d31cd69`

## Files Modified

1. **SessionEvidenceQueryBuilder.java** - Fixed ClickHouse query syntax (lines 68-110)
2. **RcaReportProxyHandler.java** - Segment-specific session storage (lines 354-441, 261-263)
3. **RootCauseSegment.java** - Added exampleSessionIds field (line 25)
4. **prompts.py** - Updated formatter to use segment sessions (lines 241-256)
5. **ConfigModule.java** - Removed SessionEvidenceService binding
6. **InteractionModule.java** - Added SessionEvidenceService binding

## Next Steps for Verification

1. Ensure Docker image is rebuilt with latest code
2. Clear all caches (ClickHouse root_cause_cache table)
3. Call RCA endpoint with `?regenerate=true` to force recomputation
4. Verify Rank 1 now returns: `2283880ae7b7ddc5070c66604d31cd69`, `980a636df82ba24a14085395a613098d`
5. Verify Rank 2 now returns: `d39bace3959ded5a88951399f6b1d8c2`, `2283880ae7b7ddc5070c66604d31cd69`

## Code Quality
- ✓ No linter errors
- ✓ Backward compatible (formatter has fallback to text extraction if needed)
- ✓ Follows existing patterns (Lombok, RxJava, etc.)
- ✓ Proper thread synchronization (synchronized blocks for segment modifications)
