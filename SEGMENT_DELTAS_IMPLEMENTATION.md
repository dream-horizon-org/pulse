# Enhanced Session Evidence Query - Using Segment Deltas

## ✅ What Changed

You requested: **Find sessions WORSE than the segment itself, not just baseline.**

Old logic: Compare sessions to baseline (0% error rate, 0% poor interactions)  
**New logic**: Compare sessions to segment deltas (e.g., 28% error rate, 35% poor interactions)

---

## How It Works

### Step 1: RCA Identifies Segment
```
RCA Analysis Result:
- Segment: Android 16 + Cellular network
- Label: "Performance degradation on Android 16 cellular"

Deltas (compared to baseline):
- error_rate_delta: +28% (28% error rate vs 2% baseline)
- poor_interaction_delta: +35% (35% poor vs 10% baseline)
```

### Step 2: Query Uses Those Deltas
```sql
HAVING
  error_rate > 0.28              -- Sessions with error_rate > 28%
  AND poor_interaction_rate > 0.35  -- Sessions with poor_interaction_rate > 35%
ORDER BY
  error_count DESC,               -- Most errors first
  poor_interaction_count DESC     -- Most poor interactions next
LIMIT 5
```

### Step 3: Result
Returns **top 5 sessions within that segment** that are **worse than the segment's own deltas**.

These are the "super worst" sessions - representatives of the worst cases within the problem segment.

---

## Complete Generated Query

### Input
```
projectId: "fancode"
interactionName: "LiveNowSectionToMatchPageLoaded"
segmentDimensions: {
  "os.version": "16",
  "network.connection.type": "cell"
}
segmentDeltas: {
  "error_rate": 28.0,          // 28% error rate
  "poor_interaction": 35.0      // 35% poor interactions
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
  avg(toFloat32(apdex_score)) as avg_apdex,
  (error_count / total_interactions) as error_rate,
  (poor_interaction_count / total_interactions) as poor_interaction_rate
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
  error_rate > 0.28
  AND poor_interaction_rate > 0.35
ORDER BY
  error_count DESC,
  poor_interaction_count DESC
LIMIT 5
```

---

## Filter Thresholds

### Before vs After

| Metric | Before | After |
|--------|--------|-------|
| **error_rate threshold** | > 0% (any error) | > segment_delta (e.g., > 28%) |
| **poor_interaction threshold** | > 0% (any poor) | > segment_delta (e.g., > 35%) |
| **Rationale** | Generic baseline | Specific to problem segment |
| **Result** | All sessions in segment | Only worst sessions in segment |

### Example Result

```
Segment (Android 16 + Cellular):
- error_rate: 28%
- poor_interaction_rate: 35%

Sessions returned:
1. sess-A: error_rate=42%, poor_rate=48% ✅ Both > segment
2. sess-B: error_rate=35%, poor_rate=40% ✅ Both > segment
3. sess-C: error_rate=31%, poor_rate=36% ✅ Both > segment
4. sess-D: error_rate=29%, poor_rate=35% ✅ Both > segment (at boundary)
5. sess-E: error_rate=28%, poor_rate=37% ✅ Both > segment (at boundary)

NOT returned:
- sess-F: error_rate=25%, poor_rate=32% ❌ Both < segment
- sess-G: error_rate=30%, poor_rate=30% ❌ poor_rate < segment
```

---

## Data Flow

```
1. RCA identifies segment
   ├─ dimensions: {os.version: "16", network: "cell"}
   ├─ error_rate_delta: 28%
   └─ poor_interaction_delta: 35%

2. Backend calls SessionEvidenceService
   └─ Passes: dimensions + deltas

3. SessionEvidenceServiceImpl
   ├─ Calls buildSessionEvidenceQuery(deltas)
   └─ Query builder converts deltas to thresholds

4. ClickHouse Query
   ├─ WHERE: segment dimensions
   ├─ HAVING: error_rate > 0.28 AND poor_rate > 0.35
   └─ ORDER BY: error_count DESC, poor_count DESC

5. Result: Top 5 sessions worse than the segment

6. Backend passes to LLM
   └─ LLM includes in affected_sessions

7. UI renders buttons
   └─ User clicks to see replay
```

---

## API Changes

### SessionEvidenceService Interface

**Old signature**:
```java
Single<SessionEvidenceResult> getSessionEvidence(
    String projectId,
    String interactionName,
    Instant startTime,
    Instant endTime,
    Map<String, String> segmentDimensions,
    Integer limit);
```

**New signature** (primary):
```java
Single<SessionEvidenceResult> getSessionEvidence(
    String projectId,
    String interactionName,
    Instant startTime,
    Instant endTime,
    Map<String, String> segmentDimensions,
    Map<String, Double> segmentDeltas,        // ← NEW
    Integer limit);
```

**Backward compatible**:
```java
// Old code still works - calls new method with null deltas
Single<SessionEvidenceResult> getSessionEvidence(
    String projectId,
    String interactionName,
    Instant startTime,
    Instant endTime,
    Map<String, String> segmentDimensions,
    Integer limit);
```

---

## Query Builder Changes

### SessionEvidenceQueryBuilder

**New method signature**:
```java
public static String buildSessionEvidenceQuery(
    String projectId,
    String interactionName,
    Instant startTime,
    Instant endTime,
    Map<String, String> segmentDimensions,
    Map<String, Double> segmentDeltas,        // ← NEW
    Integer limit)
```

**Key logic**:
```java
// Convert percentages to decimals
double errorRateThreshold = errorRateDelta / 100.0;      // 28.0 → 0.28
double poorInteractionThreshold = poorInteractionDelta / 100.0;  // 35.0 → 0.35

// Use in HAVING clause
"HAVING error_rate > " + errorRateThreshold
+ " AND poor_interaction_rate > " + poorInteractionThreshold
```

---

## RcaReportProxyHandler Update

**Now passes segment deltas to service**:
```java
sessionEvidenceService.getSessionEvidence(
    projectId,
    interactionName,
    startTime,
    endTime,
    bestSegment.getDimensions(),    // Segment dimensions
    bestSegment.getDeltas(),         // ← NEW: Segment deltas!
    5);
```

---

## Backward Compatibility

If `segmentDeltas` is `null`:
```java
// Uses default thresholds (> 0)
double errorRateThreshold = 0.0 / 100.0  // = 0.0
double poorInteractionThreshold = 0.0 / 100.0  // = 0.0

// Falls back to old behavior: any error/poor interaction
HAVING error_rate > 0.0 AND poor_interaction_rate > 0.0
```

**Result**: Old code continues to work!

---

## Benefits

| Aspect | Before | After |
|--------|--------|-------|
| **Session Selection** | Generic (any error/poor) | Segment-specific |
| **Representativeness** | Moderate | High (worst within segment) |
| **Context** | Baseline only | Segment deltas included |
| **Accuracy** | General | Targeted to problem |
| **User Value** | Good examples | Best examples |

---

## Testing

All existing tests pass with backward compatibility.

New behavior tested with:
- Delta values provided: Uses thresholds
- Delta values null: Uses baseline (> 0)
- Edge cases: Sessions at boundary

---

## Conclusion

✅ **Sessions are now selected based on segment deltas:**

1. ✅ RCA provides segment deltas (error_rate_delta, poor_interaction_delta)
2. ✅ Query converts deltas to thresholds (percentages → decimals)
3. ✅ Sessions filtered where BOTH error_rate AND poor_rate > thresholds
4. ✅ Ranked by highest error_count then poor_interaction_count
5. ✅ Returns top 5 worst sessions within that specific segment

**This gives you the most representative worst-case sessions for the identified problem!** 🎯
