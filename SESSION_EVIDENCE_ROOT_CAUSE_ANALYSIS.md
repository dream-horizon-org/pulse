# Session Evidence - Root Cause Found

## Problem Analysis
The `affected_sessions` fields remain `null` in RCA output despite extensive fixes and modifications.

## Root Cause Identified
After testing directly against ClickHouse, the actual issue is not in the query logic itself, but rather in how the **ClickhouseQueryService** executes the query through the backend.

### Key Findings:
1. **Query works directly in ClickHouse**:
   - When executing the session evidence query directly against ClickHouse CLI, we GET results:
     ```
     8bf62a493702d55a8a225c1400201144	24	0.5602111344536146
     2c8561eec6dc44fd97b862d0eb1054f0	22	0.532001634890383
     ... (5 more rows)
     ```

2. **Query fails when executed through ClickhouseQueryService**:
   - Backend logs show: `Failed to get session evidence for interaction=MatchCardClickedToMatchDetailLoaded: Failed to execute tenant query`
   - The `onErrorResumeNext` handler catches the error and returns 0 sessions
   - This causes `affected_sessions` to be null in the final output

3. **Dimension Reference Clarification**:
   - Dimensions like `Platform`, `OsVersion`, `AppVersion`, `DeviceModel` are **MATERIALIZED COLUMNS** in ClickHouse
   - They are NOT in `SpanAttributes` dictionary
   - They come from `ResourceAttributes` (Platform←os.name, OsVersion←os.version, etc.)
   - The original query format (just column name, not `SpanAttributes['pulse.interaction.Platform']`) is CORRECT

## What Still Needs Fixing
The error "Failed to execute tenant query" is likely due to:
1. Query configuration issue in ClickhouseQueryService
2. Authorization/permission issue with the tenant context
3. ClickHouse client connection issue
4. Query parsing or validation issue in the service layer

## Solution Path
Need to:
1. Check the actual exception/stack trace in more detail
2. Add better error logging to capture the full exception from ClickHouse
3. Verify QueryConfiguration is being created correctly for this type of query
4. Check if tenant context is properly initialized for this query
5. Test if the issue is specific to the Session Evidence query or affects other queries too

## Relevant Code
- `SessionEvidenceServiceImpl.java` - WHERE THE ERROR OCCURS (line 64-75)
- `ClickhouseQueryService.executeQueryOrCreateJob()` - WHERE QUERY EXECUTION HAPPENS
- `QueryConfiguration` - HOW QUERY IS CONFIGURED

## Current Status  
- Backend is rebuilding (as of 16:54 UTC, started 09:24 UTC)
- Has added more detailed error logging:
  - `log.info("Full Query:\n{}", query)` - Will show the actual SQL
  - `log.error(..., error.getClass().getSimpleName(), ...)` - Will show exception type
- Awaiting test of new backend build to see the detailed error message
