# Session Evidence - Complete Root Cause & Fix

## Executive Summary
The session evidence query was **completely failing** due to a timestamp format mismatch between Java and ClickHouse. This prevented any sessions from being retrieved, causing `affected_sessions` to be null in all RCA reports.

**Status**: ✅ **FIXED** - Query now returns sessions

---

## The Problem

### Error in ClickHouse
```
Code: 53. DB::Exception: Cannot convert string '2026-04-01T00:00:00Z' to type DateTime64(9, 'UTC'). (TYPE_MISMATCH)
```

### Why It Happened
1. Java's `Instant` object's `toString()` method produces **ISO 8601 format**: `2026-04-01T00:00:00Z`
2. ClickHouse's `DateTime64` type expects: `YYYY-MM-DD HH:MM:SS` format (e.g., `2026-04-01 00:00:00`)
3. The Java code was directly appending `Instant` to the SQL query without formatting
4. ClickHouse type validation failed, query error occurred
5. Error was caught silently and returned 0 sessions
6. No sessions = no `exampleSessionIds` = LLM can't populate `affected_sessions`

---

## The Solution

### Files Modified
**`backend/server/src/main/java/org/dreamhorizon/pulseserver/dao/rootcause/SessionEvidenceQueryBuilder.java`**

#### Change 1: Added necessary imports
```java
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
```

#### Change 2: Format timestamps in `buildSessionEvidenceQuery()` method
```java
// BEFORE (BROKEN):
query.append("    AND Timestamp >= '")
    .append(startTime)  // ❌ Produces: 2026-04-01T00:00:00Z
    .append("'\n");

// AFTER (FIXED):
DateTimeFormatter chFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
String formattedStartTime = chFormatter.format(startTime);  // ✅ Produces: 2026-04-01 00:00:00
query.append("    AND Timestamp >= '")
    .append(formattedStartTime)  // ✅ Now uses correct format
    .append("'\n");
```

#### Change 3: Same formatting applied to `buildTotalSessionsCountQuery()` method
Both timestamp fields (startTime and endTime) in both methods now use the formatter.

---

## Verification

### Before Fix (Query Failed)
```sql
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
    AND Timestamp >= '2026-04-01T00:00:00Z'  ❌ INVALID FORMAT
    AND Timestamp < '2026-04-08T00:00:00Z'   ❌ INVALID FORMAT
    AND SessionId != ''
    AND Platform = 'Android'
)
GROUP BY SessionId
HAVING
  (error_rate > 0.014005602240896)
  OR (avg_apdex < 0.31255414288626165)
ORDER BY error_count DESC, avg_apdex ASC
LIMIT 5

-- Result: ERROR - Cannot convert string to type DateTime64(9, 'UTC')
```

### After Fix (Query Succeeds)
```sql
-- Same query but with corrected timestamps:
    AND Timestamp >= '2026-04-01 00:00:00'  ✅ VALID FORMAT
    AND Timestamp < '2026-04-08 00:00:00'   ✅ VALID FORMAT

-- Result:
d39bace3959ded5a88951399f6b1d8c2	4	4	(null)	1
2283880ae7b7ddc5070c66604d31cd69	2	6	0	0.333...
7afdf0a310f57ee08d84bc2c3d0cb8fc	2	2	(null)	1
ac2f27e5e82f56c1c0fe542e68b9ab0a	2	2	(null)	1
9504c667f51033809caa1aadc0eb6959	0	2	0	0

-- 5 sessions returned successfully! ✅
```

---

## Expected Impact After Deployment

### Current Flow (Broken)
```
RCA Request
  → SessionEvidenceService.getSessionEvidence()
    → ClickHouseQueryService.executeQueryOrCreateJob()
      → ClickHouse Query with WRONG timestamp format
        → ERROR: Type mismatch
          → onErrorResumeNext() returns 0 sessions
            → exampleSessionIds = []
              → LLM has no sessions to embed
                → affected_sessions = null ❌
```

### After Fix (Working)
```
RCA Request
  → SessionEvidenceService.getSessionEvidence()
    → ClickHouseQueryService.executeQueryOrCreateJob()
      → ClickHouse Query with CORRECT timestamp format
        → SUCCESS: Returns 5 sessions
          → exampleSessionIds = ["sess-abc", "sess-def", "sess-ghi", "sess-jkl", "sess-mno"]
            → LLM receives sessions in prompt
              → LLM populates affected_sessions field
                → UI displays clickable session buttons ✅
```

---

## Next Steps
1. ✅ Query fix is complete
2. ⏳ Build backend JAR
3. ⏳ Build Docker image for pulse-server
4. ⏳ Restart pulse-server container
5. ⏳ Test RCA endpoint with regenerate=true
6. ⏳ Verify affected_sessions are populated with actual session IDs
7. ⏳ Test UI rendering of session buttons

---

## Commit Hash
```
81f549b5e fix(backend): format timestamps to ClickHouse datetime format (YYYY-MM-DD HH:MM:SS)
```

**This is a CRITICAL fix that was blocking the entire session evidence feature.**
