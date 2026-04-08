# Session Evidence - Current Debug Status

## Current State
- **Status**: `affected_sessions` fields are null in final RCA output
- **Response time**: 2-2.5 minutes per RCA request
- **Field present**: Yes, `affected_sessions: null` appears in all segments

## Code Changes Made (This Session)
1. **Backend (`RcaReportProxyHandler.java`)**: Added aggressive DEBUG logging to trace:
   - Session evidence fetch start
   - Number of sessions found
   - Session IDs extracted
   - Whether exampleSessionIds were added to request body

2. **Python (`routes.py`)**: Added DEBUG logging to trace:
   - Whether exampleSessionIds are extracted from payload
   - Payload type (dict vs Pydantic model)

3. **Python (`rca_runner.py`)**: Added DEBUG logging to trace:
   - example_session_ids parameter value
   - First 500 chars of prompt

4. **Python (`prompts.py`)**: Updated `RCA_REPORT_INSTRUCTION` to be EXTREMELY explicit:
   - Added full section "### CRITICAL: affected_sessions handling"
   - Explicitly states "EVERY segment MUST include an `affected_sessions` field"
   - Shows JSON example format
   - States "DO NOT leave the field null or omit it"

5. **Python (`submit_rca_structured_report.py`)**: Updated tool docstring to:
   - Mention `affected_sessions` is optional but expected
   - Show example segment with affected_sessions field

## Hypothesis
The LLM (Gemini) is receiving the instructions but **is choosing not to include the `affected_sessions` field** in its JSON output. Possible reasons:
1. The instruction is in the system prompt but not "loud enough" for the LLM
2. The LLM sees the field as "optional" in the Pydantic schema and decides to omit it
3. The session IDs are not actually reaching the LLM prompt
4. The sessions list is empty on the backend side

## Next Steps
1. Check backend DEBUG logs to verify sessions are actually being fetched
2. Verify the `exampleSessionIds` field is in the JSON sent to the LLM
3. If sessions ARE being sent, try a different approach:
   - Make the field REQUIRED (not optional) in the Pydantic model
   - Or inject sessions directly into the LLM instruction/payload with explicit examples
4. If sessions are NOT being sent from backend, debug the ClickHouse query and SessionEvidenceService

## Command to Check Next
```bash
curl -s 'http://localhost:8080/v1/ai/rca/report' \
  -H 'Content-Type: application/json' \
  -H 'X-Project-ID: default-project' \
  -H 'Authorization: Bearer ...' \
  --data-raw '{"interactionName":"MatchCardClickedToMatchDetailLoaded","regenerate":true,"date":"2026-04-07"}' \
  2>&1 | jq '.report.structured.segments[0].affected_sessions'
```

Currently returns: `null`
Expected: Array of session IDs like `["sess-123", "sess-456"]` or at least `[]`
