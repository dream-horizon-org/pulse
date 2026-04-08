# Session Evidence - Timestamp Format Fix

## Problem Identified
The session evidence query was failing with:
```
Code: 53. DB::Exception: Cannot convert string '2026-04-01T00:00:00Z' to type DateTime64(9, 'UTC'). (TYPE_MISMATCH)
```

## Root Cause
The Java code was passing timestamps in **ISO format** (`2026-04-01T00:00:00Z`) when calling `Instant.toString()`, but **ClickHouse expects `YYYY-MM-DD HH:MM:SS` format** for DateTime comparisons.

### Original Code (BROKEN):
```java
query.append("    AND Timestamp >= '")
    .append(startTime)  // This produces: 2026-04-01T00:00:00Z
    .append("'\n")
    .append("    AND Timestamp < '")
    .append(endTime)    // This produces: 2026-04-08T00:00:00Z
    .append("'\n");
```

### Fixed Code:
```java
// Format timestamps for ClickHouse: convert from ISO format to "YYYY-MM-DD HH:MM:SS"
DateTimeFormatter chFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
String formattedStartTime = chFormatter.format(startTime);  // Produces: 2026-04-01 00:00:00
String formattedEndTime = chFormatter.format(endTime);      // Produces: 2026-04-08 00:00:00

query.append("    AND Timestamp >= '")
    .append(formattedStartTime)  // Now uses: 2026-04-01 00:00:00
    .append("'\n")
    .append("    AND Timestamp < '")
    .append(formattedEndTime)    // Now uses: 2026-04-08 00:00:00
    .append("'\n");
```

## Files Modified
1. **`SessionEvidenceQueryBuilder.java`**
   - Added imports: `java.time.ZoneOffset`, `java.time.format.DateTimeFormatter`
   - Modified `buildSessionEvidenceQuery()` method (lines 62-90)
   - Modified `buildTotalSessionsCountQuery()` method (lines 134-168)
   - Both methods now format timestamps properly before appending to query

## Query Example
### Before Fix (BROKEN):
```sql
AND Timestamp >= '2026-04-01T00:00:00Z'
AND Timestamp < '2026-04-08T00:00:00Z'
-- ERROR: Cannot convert string '2026-04-01T00:00:00Z' to type DateTime64(9, 'UTC')
```

### After Fix (WORKING):
```sql
AND Timestamp >= '2026-04-01 00:00:00'
AND Timestamp < '2026-04-08 00:00:00'
-- SUCCESS: Returns 5 sessions
```

## Test Result
When tested directly against ClickHouse with corrected timestamp format:
```
d39bace3959ded5a88951399f6b1d8c2	4	4	\N	1
2283880ae7b7ddc5070c66604d31cd69	2	6	0	0.3333333333333333
7afdf0a310f57ee08d84bc2c3d0cb8fc	2	2	\N	1
ac2f27e5e82f56c1c0fe542e68b9ab0a	2	2	\N	1
9504c667f51033809caa1aadc0eb6959	0	2	0	0
```

✅ **5 sessions returned successfully!**

## Impact
This fix should:
1. ✅ Allow the session evidence query to execute successfully in ClickHouse
2. ✅ Return actual session IDs to the backend
3. ✅ Populate `exampleSessionIds` in the request to the LLM
4. ✅ Enable the LLM to populate `affected_sessions` field in the RCA report
5. ✅ Display clickable session buttons in the UI

## Next Steps
1. Build backend with the fix
2. Rebuild Docker image
3. Restart pulse-server
4. Test RCA endpoint to verify sessions are now returned
