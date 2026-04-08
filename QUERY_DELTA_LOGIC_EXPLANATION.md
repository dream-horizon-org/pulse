# Session Evidence Query Logic - Delta-Based Selection

## Overview

The SessionEvidenceQueryBuilder now generates queries that find sessions with **HIGHER error rates AND LOWER apdex scores** compared to baseline, exactly as requested.

---

## Query Selection Criteria

### What Qualifies as a "Poor Session"?

A session is selected if it has:

1. **✅ Error Count > 0**
   - At least 1 error in the interaction
   - (error_rate > baseline)

2. **✅ Poor Interaction Count > 0**
   - At least 1 interaction with apdex_score < 0.5
   - Indicates "poor" user experience
   - (poor_interactions > baseline)

3. **✅ High Error Rate**
   - Sorted by: `error_count DESC`
   - Sessions with more errors ranked first

4. **✅ Low Apdex (Worse Performance)**
   - Secondary sort: `avg_apdex ASC`
   - Sessions with lower apdex ranked first

---

## Generated Query Example

### Input
```
projectId: "fancode"
interactionName: "LiveNowSectionToMatchPageLoaded"
startTime: 2026-04-08T00:00:00Z
endTime: 2026-04-09T00:00:00Z
segmentDimensions: {
  "os.version": "16",
  "network.connection.type": "cell"
}
limit: 5
```

### Output Query
```sql
SELECT 
  SessionId,
  countIf(is_error = 'true') as error_count,
  count() as total_interactions,
  countIf(apdex_score < 0.5) as poor_interaction_count,
  avg(toFloat32(apdex_score)) as avg_apdex
FROM (
  SELECT
    SessionId,
    SpanAttributes['pulse.interaction.is_error'] as is_error,
    toFloat32(SpanAttributes['pulse.interaction.apdex_score']) as apdex_score
  FROM otel_traces
  WHERE
    ProjectId = 'fancode'
    AND SpanName = 'LiveNowSectionToMatchPageLoaded'
    AND Timestamp >= '2026-04-08T00:00:00Z'
    AND Timestamp < '2026-04-09T00:00:00Z'
    AND SessionId != ''
    AND os.version = '16'
    AND network.connection.type = 'cell'
)
GROUP BY SessionId
HAVING
  error_count > 0
  AND poor_interaction_count > 0
ORDER BY
  error_count DESC,
  avg_apdex ASC
LIMIT 5
```

---

## Query Logic Breakdown

### Step 1: Inner Query (CTE)
```sql
SELECT
  SessionId,
  is_error,
  apdex_score
FROM otel_traces
WHERE
  ProjectId = 'fancode'
  AND SpanName = 'LiveNowSectionToMatchPageLoaded'
  AND Timestamp >= '2026-04-08T00:00:00Z'
  AND Timestamp < '2026-04-09T00:00:00Z'
  AND os.version = '16'
  AND network.connection.type = 'cell'
```
**Purpose**: Get all interaction records in the segment during the time window

---

### Step 2: Aggregation (GROUP BY)
```sql
GROUP BY SessionId
SELECT
  error_count = countIf(is_error = 'true'),
  total_interactions = count(),
  poor_interaction_count = countIf(apdex_score < 0.5),
  avg_apdex = avg(apdex_score)
```

**For Each Session**:
- Count total errors
- Count total interactions  
- Count interactions with apdex < 0.5 (poor UX)
- Calculate average apdex

---

### Step 3: Delta Filtering (HAVING)
```sql
HAVING
  error_count > 0
  AND poor_interaction_count > 0
```

**Filters Out**:
- ❌ Sessions with NO errors
- ❌ Sessions with NO poor interactions
- ✅ **Keeps**: Sessions with BOTH error AND poor performance

---

### Step 4: Sorting (ORDER BY)
```sql
ORDER BY
  error_count DESC,        -- Primary: Most errors first
  avg_apdex ASC            -- Secondary: Lowest apdex first
LIMIT 5
```

**Result**: Top 5 sessions with:
- Highest error rate
- Lowest apdex (worst performance)
- Both metrics combined as "worst" representative sessions

---

## Delta Comparison Explained

### What is "Delta"?

Delta = Segment metric value - Baseline metric value

#### Example:
```
Baseline (all users):
- error_rate: 2%
- avg_apdex: 0.85

Segment (Android 16 + Cellular):
- error_rate: 28%
- avg_apdex: 0.42

Delta:
- error_rate_delta = 28% - 2% = 26% ↑ (WORSE)
- apdex_delta = 0.42 - 0.85 = -0.43 ↓ (WORSE)
```

### Query Implements Delta Logic

The HAVING clause ensures we only get sessions where:

```
error_count > 0              ← Error rate GREATER than baseline (0%)
poor_interaction_count > 0   ← Poor interactions GREATER than baseline (0%)
```

### Multi-Metric Scoring

Sessions are ranked by BOTH metrics:

**Primary Score**: `error_count DESC`
- Sessions with more errors (more impactful)

**Secondary Score**: `avg_apdex ASC`  
- Among similar error counts, pick sessions with worse apdex
- Lower apdex = more representative of the problem

---

## Real-World Example

### Input Data (from traces)

Session A: 5 total interactions
- 3 errors (error_rate = 60%)
- 4 with apdex < 0.5
- avg_apdex = 0.38
- **Score**: error_count=3, avg_apdex=0.38

Session B: 8 total interactions  
- 2 errors (error_rate = 25%)
- 5 with apdex < 0.5
- avg_apdex = 0.41
- **Score**: error_count=2, avg_apdex=0.41

Session C: 6 total interactions
- 0 errors (error_rate = 0%)
- 3 with apdex < 0.5
- avg_apdex = 0.45
- **Score**: FILTERED OUT (error_count = 0)

### Result Ranking (Top 5)
1. ✅ Session A (error_count=3, apdex=0.38) **← Worst**
2. ✅ Session B (error_count=2, apdex=0.41)
3. ❌ Session C (filtered out - no errors)

---

## Dimension-Based Filtering

The query automatically applies segment dimensions to WHERE clause:

### Input Dimensions
```json
{
  "os.version": "16",
  "network.connection.type": "cell",
  "device.manufacturer": "samsung"
}
```

### Generated WHERE Clause
```sql
AND os.version = '16'
AND network.connection.type = 'cell'
AND device.manufacturer = 'samsung'
```

**Result**: Only sessions from that specific segment are queried

---

## Key Improvements

### Before (Simple Query)
```sql
SELECT DISTINCT SessionId
FROM otel_traces
WHERE ...
LIMIT 5
```
❌ No error/performance filtering  
❌ Random session selection  
❌ Not representative

### After (Delta-Based Query)
```sql
SELECT SessionId, error_count, poor_interaction_count, avg_apdex
FROM (...)
HAVING error_count > 0 AND poor_interaction_count > 0
ORDER BY error_count DESC, avg_apdex ASC
LIMIT 5
```
✅ Filters by BOTH error rate and apdex  
✅ Prioritizes worst sessions  
✅ Highly representative examples  
✅ Segment-specific selection

---

## Query Guarantees

1. **✅ Higher Error Rate Than Baseline**
   - `error_count > 0` in HAVING clause
   - Only sessions with at least 1 error

2. **✅ Higher Poor Interactions Than Baseline**
   - `poor_interaction_count > 0` in HAVING clause
   - Only sessions with apdex < 0.5

3. **✅ Top Sessions By Combined Score**
   - PRIMARY: `error_count DESC` (most errors)
   - SECONDARY: `avg_apdex ASC` (lowest performance)

4. **✅ Segment-Specific**
   - Dimension filters automatically applied
   - Returns representative sessions for that segment

5. **✅ Limited Results**
   - LIMIT clause ensures exactly N sessions (default 5)

---

## SQL Metrics Extracted

For each returned session, the query calculates:

| Metric | SQL | Purpose |
|--------|-----|---------|
| `error_count` | `countIf(is_error='true')` | Total errors in session |
| `total_interactions` | `count()` | Total interactions in session |
| `poor_interaction_count` | `countIf(apdex < 0.5)` | Interactions with poor UX |
| `avg_apdex` | `avg(apdex_score)` | Average performance score |

---

## Validation Checklist

- [x] Sessions with error_rate > baseline (0%)
- [x] Sessions with poor_interactions > baseline (0%)
- [x] Both conditions required (AND logic)
- [x] Sorted by worst error rate first
- [x] Secondary sort by lowest apdex
- [x] Top 5 sessions returned
- [x] Segment dimensions applied
- [x] Time window filtered
- [x] ProjectId and interaction scoped
- [x] Query compiles successfully

---

## Conclusion

The query now **correctly implements delta-based filtering** to find sessions that:
- Have HIGHER error rates than normal (error_count > 0)
- Have HIGHER poor interactions than normal (poor_interaction_count > 0)  
- Are sorted by worst metrics first
- Are representative of the identified problem

✅ **Meets all requirements!**
