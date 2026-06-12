# RCA Affected Sessions Complete Fix

## Problem Statement
The `affected_sessions` field in `report.structured.segments` was always empty `[]`, even though:
- Backend was correctly enriching `rootCausePayload.segments[i].exampleSessionIds`
- These session IDs were present in the API response
- UI expected them in `report.structured.segments[i].affected_sessions`

## Root Causes Identified

### Issue #1: Response Structure
**Problem**: Backend was using enriched intermediate payload instead of AI response body
- AI response: `{ "report": { "structured": {...} } }`
- Backend was returning: `{ "rootCausePayload": {...} }`
- **Fix**: Use AI response body and merge enriched payload into it

### Issue #2: Session ID Extraction
**Problem**: Formatter didn't have access to original payload with `exampleSessionIds`
- Formatter only received analysis text
- No way to map segments to their session IDs
- **Fix**: Inject original RootCausePayload JSON into formatter prompt

### Issue #3: Session ID Mapping Logic
**Problem**: Instructions told formatter to search for sessions in text, not use payload
- AI analysis text didn't explicitly mention all session IDs
- Formatter couldn't reliably extract and map them
- **Fix**: Direct extraction from payload using segment label matching

## Complete Solution

### 1. Backend (RcaReportProxyHandler.java)
```java
private CompletionStage<AiProxyUpstreamResult> finalizeSuccessfulRcaProxyResult(...) {
    // Use AI response body (contains report.structured)
    String body = result.getBufferedBody();
    
    // Merge enriched rootCausePayload with exampleSessionIds
    root.set(ROOT_CAUSE_PAYLOAD_FIELD, 
        objectMapper.valueToTree(enrichment.rootCause()));
    
    // Merge related heatmaps for UI
    rcaRelatedHeatmapsMerger.mergeInto(...);
    
    // Response now has: report.structured + rootCausePayload + metadata
}
```

### 2. Session Evidence Service (SessionEvidenceQueryBuilder.java)
Already fixed: Correctly queries and returns sessions filtered by segment dimensions

### 3. RCA Formatter Prompt (prompts.py)

#### Step 1: Inject Original Payload
```python
def build_rca_formatter_prompt(ctx=None):
    # Extract rca_analysis_result from session state
    rca_result = raw_state.get("rca_analysis_result")
    
    # Extract original RootCausePayload JSON
    # (contains segments with exampleSessionIds)
    root_cause_payload_json = extract_from_user_message(...)
    
    # Include both in formatter prompt
    return f"""
    ## RCA Analysis
    {analysis}
    
    ## Original RootCausePayload (for session ID mapping)
    {root_cause_payload_json}
    """
```

#### Step 2: Direct Session Extraction
Instructions updated to:
1. Match segment by **label** (e.g., "OsVersion: 11")
2. Find that segment in the payload's segments array
3. Extract its `exampleSessionIds`
4. Copy to output's `affected_sessions`

**Example mapping**:
```
Payload has:
{
  "label": "OsVersion: 11",
  "exampleSessionIds": ["s_1506", "s_1540"]
}

Output should have:
{
  "title": "OsVersion: 11",
  "affected_sessions": ["s_1506", "s_1540"]
}
```

## Data Flow (Fixed)

```
1. Backend enriches segments:
   rootCausePayload.segments[i].exampleSessionIds = [session IDs from DB]

2. Backend sends to AI:
   "RootCausePayload(JSON): {...with exampleSessionIds...}"

3. AI Agent analyzes:
   (mentions segment labels: "OsVersion: 11", etc.)

4. Formatter receives:
   - Analysis text
   - Original payload with exampleSessionIds (injected!)

5. Formatter maps and fills:
   report.structured.segments[i].affected_sessions = 
     rootCausePayload.segments[find by label].exampleSessionIds

6. Response to UI:
   {
     "report": {
       "structured": {
         "segments": [
           {
             "title": "OsVersion: 11",
             "affected_sessions": ["s_1506", "s_1540"]  ✅
           }
         ]
       }
     },
     "rootCausePayload": { ... },
     "cached": true
   }
```

## Files Modified
1. `backend/server/src/main/java/org/dreamhorizon/pulseserver/service/ai/impl/RcaReportProxyHandler.java` - Merge enriched payload into response
2. `pulse_ai/agents/rca/prompts.py` - Inject payload + update mapping instructions

## Testing
After these changes:
1. Clear cache: `docker exec pulse-clickhouse clickhouse-client --query "TRUNCATE TABLE otel.root_cause_cache"`
2. Make new RCA request with `regenerate: true`
3. Verify: `report.structured.segments[i].affected_sessions` are populated

## Expected Result
```json
{
  "report": {
    "structured": {
      "segments": [
        {
          "title": "OsVersion: 11",
          "affected_sessions": ["s_1506", "s_1540"],  // ✅ NOW POPULATED!
          "metrics": [...],
          "insights": "..."
        }
      ]
    }
  },
  "rootCausePayload": {
    "segments": [
      {
        "label": "OsVersion: 11",
        "exampleSessionIds": ["s_1506", "s_1540"],
        ...
      }
    ]
  }
}
```

