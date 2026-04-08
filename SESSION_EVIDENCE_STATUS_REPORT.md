# Session Evidence - Current Status Report

## ✅ MAJOR BREAKTHROUGH - Query is NOW Working!

### Test Results
```
RCA Request made at 2026-04-08 16:48:36
Backend logs show:
  ✅ "Parsed session evidence result: 5 sessions"
  ✅ "Session evidence callback - 5 sessions found"
  ✅ "Successfully added exampleSessionIds to working object"
  ✅ "Enriched body contains exampleSessionIds: true"

RCA Response received:
  ✅ affected_sessions field is now [] (empty array, not null!)
```

---

## Root Cause Analysis

### Problem 1: ✅ FIXED - Timestamp Format
**Issue**: Query was failing in ClickHouse
```
Code: 53. DB::Exception: Cannot convert string '2026-04-01T00:00:00Z' to type DateTime64(9, 'UTC')
```

**Solution**: Format timestamps to ClickHouse format `yyyy-MM-dd HH:mm:ss`
- **File**: `backend/server/src/main/java/org/dreamhorizon/pulseserver/dao/rootcause/SessionEvidenceQueryBuilder.java`
- **Changes**:
  - Added `DateTimeFormatter` to format `Instant` objects
  - Applied formatting in both `buildSessionEvidenceQuery()` and `buildTotalSessionsCountQuery()`
- **Status**: ✅ DEPLOYED & WORKING

### Problem 2: ⚠️ PARTIALLY SOLVED - LLM Not Including Sessions in Output

**Current Situation**:
- Backend successfully finds 5 sessions
- Backend successfully passes `exampleSessionIds` in request
- **BUT** Python AI service shows: `Extracted exampleSessionIds from payload: None`
- **RESULT**: LLM receives no session IDs, returns empty array for `affected_sessions`

**Root Cause**: Session IDs are being added to `working` object in backend, but may not be reaching the Python service correctly or may be extracted at wrong point in code.

**What's Needed**:
1. Verify the exact JSON structure being sent by backend to AI service
2. Check if `exampleSessionIds` field name matches between Java and Python
3. Possibly adjust the extraction logic in `routes.py`

---

## Code Changes Made This Session

### 1. **SessionEvidenceQueryBuilder.java** ✅
```java
// Before: Timestamp >= '2026-04-01T00:00:00Z' (ISO format)
// After:  Timestamp >= '2026-04-01 00:00:00' (ClickHouse format)

DateTimeFormatter chFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
String formattedStartTime = chFormatter.format(startTime);
```

### 2. **rca_runner.py** ✅
```python
# Fixed SyntaxError in f-string with nested quotes
# Changed from problematic nested quote escaping to pre-computed string
example_sessions_truncated = ', '.join([f'"{sid[:10]}..."' for sid in example_session_ids[:2]])
```

### 3. **routes.py** ⏳ (Deployed, awaiting detailed logs)
```python
# Added verbose debugging to identify extraction issue
logger.info(f"[DEBUG] rootCausePayload keys: {request.rootCausePayload.keys()}")
logger.info(f"[DEBUG] Extracted exampleSessionIds: {example_sessions}")
```

---

## Flow Status

### ✅ Working (Backend → ClickHouse)
```
RcaReportProxyHandler
  → SessionEvidenceService.getSessionEvidence()
    → ClickHouse Query (NOW WORKING!)
      → 5 sessions found
        → session IDs extracted
          → exampleSessionIds added to request JSON
```

### ⚠️ Broken (AI Service → LLM)
```
Python routes.py
  → Attempts to extract exampleSessionIds
    → Gets None instead of list
      → LLM receives empty session list
        → LLM returns affected_sessions: []
```

---

## Next Steps

### Immediate (1-2 minutes)
1. Check AI agent logs with new detailed logging to see what `rootCausePayload` keys actually exist
2. Verify if backend is sending `exampleSessionIds` or if it's nested in `rootCausePayload.segments[].sessions` or similar
3. Adjust extraction logic if needed

### Then
1. Rebuild and redeploy AI agent
2. Test curl command again
3. Verify `affected_sessions` contains actual session IDs
4. Test UI rendering of session buttons

---

## Commits Made
```
81f549b5e - fix(backend): format timestamps to ClickHouse datetime format (TIMESTAMP FIX)
57e2efa74 - fix(ai): add detailed logging to debug exampleSessionIds extraction (DEBUGGING)
```

---

## Key Verification Points

### ✅ Verified Working
- ClickHouse query returns 5 sessions when timestamp format is correct
- Backend successfully finds and parses sessions  
- Backend successfully adds `exampleSessionIds` to request
- RCA endpoint returns structured response with `affected_sessions` field

### ⚠️ Needs Investigation
- Exact JSON structure being sent by backend
- Whether `exampleSessionIds` field name is matching
- Whether extraction is happening at correct time in request lifecycle
- Whether Pydantic model validation is stripping the field

---

## Summary
We've successfully fixed the critical timestamp format bug that was blocking the entire session evidence feature! The backend is now finding sessions and attempting to pass them to the AI service. The next issue is at the Python boundary where the session IDs aren't being extracted properly. Once we fix that extraction issue, the LLM will receive the session IDs and can include them in the RCA report, allowing users to see clickable session replay buttons in the UI.
