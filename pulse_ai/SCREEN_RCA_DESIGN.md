# Screen RCA v2 — Problem-Focused Design

## Problem Definition

Screen RCA currently analyzes **only frustration** (rage taps, dead clicks from `otel_logs WHERE PulseType = 'app.click'`). A screen can crash users, load slowly, or fail with ANRs but show zero issues unless there are rage taps.

**Fix:** Expand root cause analysis to identify **which segment causes each problem type**, not just frustration.

---

## Problems Analyzed (List Format)

### 1) Crashes

**Top Segment:** `device_model: SM-A135F`  
**Specific Issues (from ClickHouse):** Top 3 `ExceptionMessage` values from crashes on this segment

- `java.lang.NullPointerException: Attempt to invoke virtual method 'android.view.View.getParent()' on a null object reference`
- `java.lang.OutOfMemoryError: Failed to allocate 4194304 byte(s) with 1234567 bytes free`
- `android.view.WindowManager$BadTokenException: Unable to add window — token null is not valid`

**Source Table:** `otel_logs WHERE PulseType = 'device.crash' AND ScreenName = 'checkout' AND DeviceModel = 'SM-A135F'`  
**ClickHouse Column:** `LogAttributes['exception.message']` (STRING)  
**UI Reference:** [CriticalInteractionDetails → SessionTimeline](https://github.com/dreamhorizon/pulse/tree/main/pulse-ui/src/screens/CriticalInteractionDetails) — view crash events in session replay  

---

### 2) ANR (Android Not Responding)

**Top Segment:** `os_version: Android 12`  
**Specific Issues (from ClickHouse):** Top 3 blocked thread names + duration

- `main: ANR detected - blocked for 7,234ms on EventLoop`
- `main: ANR detected - blocked for 8,120ms waiting on PaymentProcessor lock`
- `RenderThread: ANR detected - blocked for 6,890ms on frame render`

**Source Table:** `otel_logs WHERE PulseType = 'device.anr' AND ScreenName = 'checkout' AND OsVersion = 'Android 12'`  
**ClickHouse Column:** `LogAttributes['thread.name']`, `LogAttributes['anr.duration_ms']` (STRING, INT64)  
**UI Reference:** [CriticalInteractionDetails → Analysis Tab](https://github.com/dreamhorizon/pulse/tree/main/pulse-ui/src/screens/CriticalInteractionDetails/components/InteractionDetailsMainContent) — ANR rate breakdown by OS  

---

### 3) Frozen Frames

**Top Segment:** `app_version: 5.1.0`  
**Metric Overview:** Animation jank (>16ms frame time) during checkout  
**Source Table:** `otel_logs WHERE PulseType = 'app.jank.frozen' AND ScreenName = 'checkout' AND AppVersion = '5.1.0'`  
**ClickHouse Column:** Aggregate count of frozen frames; no specific issue details extracted  
**UI Reference:** [ScreenDetail → Performance Metrics](https://github.com/dreamhorizon/pulse/tree/main/pulse-ui/src/screens/ScreenDetail) — frame rate trends per screen  

---

### 4) Bad Clicks (Frustration)

**Top Segment:** `device_model: SM-A135F`  
**Specific Issues:** (Uses heatmap RCA approach — same as current screen_rca implementation)

- Rage taps: x=450, y=120 (payment_button) — 23 rapid taps in 3.5s
- Dead clicks: x=450, y=120 (payment_button) — 18 occurrences
- Rage taps: x=320, y=250 (submit_form) — 15 rapid taps in 2.1s

**Source Table:** `otel_logs WHERE PulseType = 'app.click' AND ScreenName = 'checkout' AND DeviceModel = 'SM-A135F' AND (ClickType='dead' OR Rage=true)`  
**ClickHouse Columns:** `LogAttributes['click.x']`, `LogAttributes['click.y']`, `LogAttributes['click.target_id']`, `LogAttributes['rage_count']` (INT64, INT64, STRING, INT64)  
**UI Reference:** [ScreenDetail Heatmap](https://github.com/dreamhorizon/pulse/tree/main/pulse-ui/src/screens/ScreenDetail) — frustration heatmap + click breakdown by device  

---

### 5) Slow Rendering

**Top Segment:** `device_model: Pixel 3`  
**Metric Overview:** Slow frame rate >10% due to layout inflation  
**Source Table:** `otel_logs WHERE PulseType = 'app.jank.slow' AND ScreenName = 'checkout' AND DeviceModel = 'Pixel 3'`  
**ClickHouse Column:** Aggregate count of slow frames; no specific issue details extracted  
**UI Reference:** [CriticalInteractionDetails → ThreeDimensionalSection](https://github.com/dreamhorizon/pulse/tree/main/pulse-ui/src/screens/CriticalInteractionDetails/components/InteractionDetailsMainContent/components/Analysis/components/ThreeDimensionalSection.interface.ts) — slow frames broken down by device  

---

### 6) Network Failures

**Top Segment:** `network_type: 3G`  
**Metric Overview:** 4xx/5xx errors, timeouts on slow networks  
**Source Table:** `otel_logs WHERE PulseType LIKE 'network.%' AND ScreenName = 'checkout' AND NetworkProvider = '3G' AND HttpStatusCode >= 400`  
**ClickHouse Column:** Aggregate count of network errors; no specific issue details extracted  
**UI Reference:** [NetworkDetail Screen](https://github.com/dreamhorizon/pulse/tree/main/pulse-ui/src/screens/NetworkDetail) — HTTP errors by provider + status code breakdown  

---

### 7) Screen Load Time

**Top Segment:** `platform: iOS`  
**Metric Overview:** P95 screen load 3.2s (baseline 1.0s)  
**Source Table:** `otel_traces WHERE PulseType = 'screen_load' AND ScreenName = 'checkout' AND Platform = 'iOS' AND Duration > :slow_threshold`  
**ClickHouse Column:** `Duration` (quantile for P95); no specific issue details extracted  
**UI Reference:** [ScreenDetail → Engagement Data](https://github.com/dreamhorizon/pulse/tree/main/pulse-ui/src/screens/ScreenDetail/hooks/useGetScreenEngagementData) — load time trends + device/network breakdown  

---

### 8) Screen Interactive (Time to Interactive)
**Top Segment:** `network_type: 4G`  
**Metric Overview:** P95 time to interactive 2.8s (baseline 0.9s), delays in rendering first interactive element  
**Source Table:** `otel_traces WHERE PulseType = 'screen_interactive' AND ScreenName = 'checkout' AND NetworkType = '4G'`  
**ClickHouse Column:** `Duration` (quantile for P95); no specific issue details extracted  
**UI Reference:** [ScreenDetail → Engagement Data](https://github.com/dreamhorizon/pulse/tree/main/pulse-ui/src/screens/ScreenDetail/hooks/useGetScreenEngagementData) — interactive time trends + network breakdown  

---

### 9) Network Latency
**Top Segment:** `network_type: 2G`  
**Metric Overview:** P95 API request latency 4.5s (baseline 0.8s) due to high-latency networks  
**Source Table:** `otel_logs WHERE PulseType = 'network.request' AND ScreenName = 'checkout' AND NetworkType = '2G'`  
**ClickHouse Column:** `Duration` (quantile for P95); no specific issue details extracted  
**UI Reference:** [NetworkDetail Screen](https://github.com/dreamhorizon/pulse/tree/main/pulse-ui/src/screens/NetworkDetail) — request latency by network type + endpoint breakdown  

---

---

## Data Model: Problem Metadata


| Field          | Type   | Example                                                                                                                 |
| -------------- | ------ | ----------------------------------------------------------------------------------------------------------------------- |
| `problem_type` | string | `"crashes"`, `"anr"`, `"frozen_frames"`, `"slow_rendering"`, `"network_failures"`, `"screen_load_time"`, `"screen_interactive"`, `"network_latency"`, `"bad_clicks"` |
| `top_segment`  | string | `"device_model: SM-A135F"`                                                                                              |
| `rank`         | int    | 1 (most critical) to 9 (least) — based on severity + volume                                                             |
| `weightage`    | float  | 0.0–1.0 (currently 1.0/9 ≈ 0.111 for all; can be tuned by business rules)                                                             |
| `severity`     | string | `"critical"`, `"high"`, `"medium"`, `"normal"`                                                         |
| `source_table` | string | `"otel_logs"` or `"otel_traces"` + condition                                                          |
| `ui_reference` | string | Link to pulse-ui page showing this metric                                                             |


---

## Schema: `ScreenRcaStructuredV2`

```python
class ScreenRcaSpecificIssue(BaseModel):
    """Individual issue from ClickHouse data."""
    description: str  # e.g., "java.lang.NullPointerException: Attempt to invoke virtual method..."
    count: int  # How many times this occurred
    source_field: str  # Which ClickHouse column (e.g., "ExceptionMessage", "thread.name")

class ScreenRcaProblem(BaseModel):
    """Single-row problem analysis."""
    problem_type: Literal[
        "crashes", "anr", "frozen_frames", "slow_rendering",
        "network_failures", "screen_load_time", "screen_interactive", "network_latency", "bad_clicks"
    ]
    rank: int  # 1 (most critical) to 9 (least) — based on severity + volume
    weightage: float  # 0.0–1.0; currently 1.0/9 ≈ 0.111 for all
    top_segment: str  # e.g. "device_model: SM-A135F"
    specific_issues: Optional[List[ScreenRcaSpecificIssue]] = None  # Crashes & ANR only
    severity: Literal["critical", "high", "medium", "normal"]
    metric_id: str  # e.g., "crash_rate", "screen_load_p95"
    value_display: str  # e.g., "5%", "3.2s"
    baseline_display: str  # e.g., "2%", "1.0s"
    delta_display: str  # e.g., "+250%", "+200%"
    affected_sessions: Optional[List[str]] = None  # Top 2 session IDs

class ScreenRcaStructuredV2(BaseModel):
    version: int = 2
    executive_summary: str  # Screen health assessment (not problem list). LLM prompt should emphasize rank 1, minimize lower ranks.
    problems: List[ScreenRcaProblem]  # Ranked by criticality; one row per problem found
    recommendations: List[str]  # 3-7 bullets (same as V1)
```

### Executive Summary Guidelines

**Summary should focus on SCREEN HEALTH**, not problem details. The **LLM system prompt** (not the summary) contextualizes ranks:

**LLM Prompt provides:**
- Problems are ranked by severity (critical > high > medium > normal), volume, and type priority
- Use `rank` field to contextualize: lower rank = higher priority to emphasize
- Rank 1-3: Emphasize heavily (these are most critical issues)
- Rank 4-6: Mention clearly (secondary issues)
- Rank 7+: Minimize or omit unless critical

**Summary itself should:**
- Lead with the dominant problem's impact (highest ranked critical issue)
- Mention business/segment context (affected %, device, revenue risk)
- Highlight interconnections if present ("memory pressure compounds issues")
- Skip detailed problem listing (that's in the ranked array)
- Stay concise (2-3 sentences focusing on health)

**Example (Screen Health Focus):**
```
"Checkout screen is experiencing critical stability issues on Samsung Galaxy A13 (SM-A135F), with crash rate at 5.2% (+260% vs baseline). Secondary performance bottlenecks include ANR events (+250%) and delayed screen interactivity (+211%). These issues are interconnected, suggesting memory constraints during load exacerbate both app crashes and ANRs. Overall screen health is degraded; affects 15% of checkout sessions, creating significant revenue risk on low-end devices."
```

---

## ClickHouse Query Strategy: Extracting Specific Issues

For **crashes & ANR only**, extract **top 3 most-occurring specific issues**:

### Query Pattern (Generic)

```sql
SELECT 
  LogAttributes['<FIELD_NAME>'] as issue,
  COUNT(*) as count
FROM otel_logs
WHERE 
  ProjectId = :project_id
  AND ScreenName = :screen_name
  AND PulseType = :pulse_type
  AND <DIMENSION_FILTER>  -- e.g., DeviceModel = 'SM-A135F'
  AND Timestamp >= :start AND Timestamp < :end
GROUP BY issue
ORDER BY count DESC
LIMIT 3
```

### Problem-Specific Queries


| Problem     | Field(s)                          | Query Condition              |
| ----------- | --------------------------------- | ---------------------------- |
| **Crashes** | `ExceptionMessage`                | `PulseType = 'device.crash'` |
| **ANR**     | `thread.name` + `anr.duration_ms` | `PulseType = 'device.anr'`   |


*Other problems (Frozen Frames, Slow Rendering, Network, Screen Load, Bad Clicks) use aggregate counts, not specific issues.*

---

**File:** `backend/server/src/main/java/.../ScreenRcaQueryBuilder.java`

For each problem, compute:

1. **Crash rate on screen**: `countDistinct(SessionId WHERE PulseType='device.crash') / countDistinct(SessionId WHERE PulseType='screen_session')`
2. **ANR rate on screen**: Same, but `PulseType='device.anr'`
3. **Frozen frame rate**: Same, but `PulseType='frozen_frame'`
4. **Slow frame rate**: Same, but `PulseType='slow_frame'`
5. **Network error rate**: `countIf(StatusCode >= 400) / count() WHERE PulseType LIKE 'network.%'` + SessionId match
6. **Screen load (bad count)**: `countDistinct(SessionId WHERE PulseType='screen_load' AND Duration > 500ms) / countDistinct(SessionId)` + P95 duration
7. **Screen interactive (bad count)**: `countDistinct(SessionId WHERE PulseType='screen_interactive' AND Duration > 7300ms) / countDistinct(SessionId)` + P95 duration
8. **Network latency (bad count)**: `countDistinct(SessionId WHERE PulseType='network.request' AND Duration > 1000ms) / countDistinct(SessionId)` + P95 by network type
9. **Bad click rate**: `countIf(ClickType='dead' OR Rage) / count() WHERE PulseType='app.click'`

For each problem, **find the single top segment** (dimension value) by volume-weighted severity:

- Segment with highest count of sessions/events with the problem
- Apply anomaly threshold (e.g., crash_rate > 2% for Critical)
- Return that segment's metrics

---

## Agent Instructions

**File:** `agents/screen_rca/prompts.py`

System prompt updates:

### System Prompt: Summary Context for LLM

The system prompt should guide the LLM on what to focus on when generating the executive summary:

```
You are analyzing screen health across 8 problem types.
Rank 1 (Crashes) is the MOST CRITICAL issue — emphasize heavily in summary.
Rank 2-3 (ANR, Frozen Frames) are HIGH PRIORITY — mention clearly.
Rank 4-6 (Network, Screen Load, Screen Interactive) are MEDIUM — brief mentions.
Rank 7-8 (Slow Rendering, Bad Clicks) are LOW PRIORITY — minimize or omit unless critical.

Summary should:
- Lead with the rank 1 problem's impact and root cause
- Highlight interconnections between top 2-3 problems
- Quantify business impact (affected users, revenue risk)
- Stay focused on SCREEN HEALTH (not detailed problem lists)
- Use rank 1's segment + metrics as the primary lens
```

### Anomaly Thresholds


| Problem               | Warning | Critical |
| --------------------- | ------- | -------- |
| Crash Rate            | >1%     | >5%      |
| ANR Rate              | >0.5%   | >2%      |
| Frozen Frame %        | >5%     | >15%     |
| Slow Frame %          | >10%    | >20%     |
| Network Error %       | >5%     | >10%     |
| Screen Load P95 delta | >+30%   | >+100%   |
| Screen Load (Universal Per-Screen Threshold) | <100-300ms (Good) | >500ms (Critical) |
| Screen Interactive (Bugsee) | <3.8s (Good) | 3.8-7.3s (Needs Improvement) | >7.3s (Poor/Critical) |
| Network Latency (User Perception) | <300ms (Acceptable) | 300-1000ms (Sluggish) | >1000ms (Unacceptable/Critical) |
| Bad Click Rate delta  | >+20%   | >+50%    |


### Task

1. Compute all 9 problem metrics (crash rate, anr rate, frozen %, slow %, network error %, load P95, interactive P95, latency P95, bad click %)
2. Identify top segment per problem using volume-weighted severity
3. Rank problems by: severity → volume → type priority (crashes > anr > ff > slt > sint > nf > sr > bc)
4. Extract specific_issues for crashes & ANR only
5. Generate executive_summary using LLM prompt context above — emphasizing rank 1, minimizing lower ranks
6. Populate `ScreenRcaStructuredV2` schema

### Ranking Details (3-Tier Tiebreaker)

- **Tier 1:** Severity (critical > high > medium > normal)
- **Tier 2:** Affected user volume (higher affected_sessions wins)
- **Tier 3:** Problem type priority order (crashes → anr → ff → slt → sint → nf → nl → sr → bc)

### Output

Populate `ScreenRcaStructuredV2` schema with one problem per row. Segment recommendation based on volume + severity.

---

## Native SDKs: PulseType Mapping


| SDK              | Screen Load Signal                                                                                                              |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| **iOS**          | `PulseType = 'screen_load'` — Span name "Created", opened at viewDidLoad, closed at viewDidAppear. Duration = screen load time. |
| **Android**      | `PulseType = 'screen_load'` — Auto-captured via `ScreenLoadInstrumentation`. Span duration = load duration.                     |
| **React Native** | `PulseType = 'screen_load'` (or feature flag `rn_screen_load`). Same signal as native.                                          |


All SDKs emit `screen_load` spans to `otel_traces` with:

- `SpanAttributes['pulse.type'] = 'screen_load'`
- `SpanAttributes['screen.name']` = screen identifier
- `Duration` = milliseconds to interactive

---

## PulseType Constants (for ClickHouse queries)

All data is stored in `otel_logs` with the following `PulseType` constants:


| Problem              | PulseType                    | Note                                                    |
| -------------------- | ---------------------------- | ------------------------------------------------------- |
| **Crashes**          | `'device.crash'`             | Emitted by all SDKs when an exception occurs            |
| **ANR**              | `'device.anr'`               | Android-specific; blocked main thread detected          |
| **Frozen Frames**    | `'app.jank.frozen'`          | Frame duration > 16ms (Android/iOS)                     |
| **Slow Rendering**   | `'app.jank.slow'`            | Frame jank during rendering; stored in `otel_logs`      |
| **Network Failures** | `'network.error'` or similar | HTTP status >= 400; some variants in `otel_logs`        |
| **Screen Load Time** | `'screen_load'`              | Spans in `otel_traces` (not `otel_logs`)                |
| **Screen Interactive** | `'screen_interactive'`   | Spans in `otel_traces`; time to first interactive element |
| **Network Latency** | `'network.request'` | Request duration from span; P95 latency per network type |
| **Bad Clicks**       | `'app.click'`                | `LogAttributes['click.type'] = 'dead'` or `Rage = true` |


---

## Implementation Checklist

### Backend

- Extend `ScreenRcaQueryBuilder` — add 7 problem-specific metric expressions
- Extend `ScreenRcaService` — compute top segment per problem
- Ensure `affected_sessions` extraction (top 2 SessionIds per segment)

### pulse_ai

- Create `ScreenRcaStructuredV2` schema
- Update `agents/screen_rca/prompts.py` — problem thresholds + problem-ranking logic
- Update `agents/screen_rca/agent.py` — use V2 schema, output_key, validation
- Update `server/screen_rca_runner.py` — validate V2 schema

### Optional: EM Agent Tools (Phase 2)

- `query_screens()` — list screens with health per problem
- `query_screen_problems()` — drill into a screen's problem data

---

## Example Output

**Input:** Checkout screen RCA request

**Output:**

```json
{
  "version": 2,
  "executive_summary": "Checkout screen is experiencing critical stability issues on Samsung Galaxy A13 (SM-A135F), with crash rate at 5.2% (+260% vs baseline). Secondary performance bottlenecks include ANR events (+250%) and delayed screen interactivity (+211%). These issues are interconnected, suggesting memory constraints during load exacerbate both app crashes and ANRs. Overall screen health is degraded; affects 15% of checkout sessions, creating significant revenue risk on low-end devices.",
  "problems": [
    {
      "problem_type": "crashes",
      "rank": 1,
      "weightage": 0.111,
      "top_segment": "device_model: SM-A135F",
      "specific_issues": [
        {
          "description": "java.lang.NullPointerException: Attempt to invoke virtual method 'android.view.View.getParent()' on a null object reference",
          "count": 234,
          "source_field": "ExceptionMessage"
        },
        {
          "description": "java.lang.OutOfMemoryError: Failed to allocate 4194304 byte(s) with 1234567 bytes free",
          "count": 156,
          "source_field": "ExceptionMessage"
        },
        {
          "description": "android.view.WindowManager$BadTokenException: Unable to add window — token null is not valid",
          "count": 89,
          "source_field": "ExceptionMessage"
        }
      ],
      "severity": "critical",
      "metric_id": "crash_rate",
      "value_display": "5.2%",
      "baseline_display": "2.0%",
      "delta_display": "+260%",
      "affected_sessions": ["sess-checkout-001", "sess-checkout-002"]
    },
    {
      "problem_type": "anr",
      "rank": 2,
      "weightage": 0.111,
      "top_segment": "os_version: Android 12",
      "specific_issues": [
        {
          "description": "main thread: ANR detected - blocked for 7234ms on EventLoop",
          "count": 123,
          "source_field": "LogAttributes['thread.name'] + ['anr.duration_ms']"
        },
        {
          "description": "main thread: ANR detected - blocked for 8120ms waiting on PaymentProcessor lock",
          "count": 98,
          "source_field": "LogAttributes['thread.name'] + ['anr.duration_ms']"
        },
        {
          "description": "RenderThread: ANR detected - blocked for 6890ms on frame render",
          "count": 67,
          "source_field": "LogAttributes['thread.name'] + ['anr.duration_ms']"
        }
      ],
      "severity": "high",
      "metric_id": "anr_rate",
      "value_display": "2.8%",
      "baseline_display": "0.8%",
      "delta_display": "+250%",
      "affected_sessions": ["sess-checkout-005", "sess-checkout-006"]
    },
    {
      "problem_type": "frozen_frames",
      "rank": 3,
      "weightage": 0.111,
      "top_segment": "app_version: 5.1.0",
      "specific_issues": null,
      "severity": "high",
      "metric_id": "frozen_frame_rate",
      "value_display": "12.5%",
      "baseline_display": "5.0%",
      "delta_display": "+150%",
      "affected_sessions": ["sess-checkout-007", "sess-checkout-008"]
    },
    {
      "problem_type": "network_failures",
      "rank": 4,
      "weightage": 0.111,
      "top_segment": "network_type: 3G",
      "specific_issues": null,
      "severity": "high",
      "metric_id": "network_error_rate",
      "value_display": "8.5%",
      "baseline_display": "2.0%",
      "delta_display": "+325%",
      "affected_sessions": []
    },
    {
      "problem_type": "slow_rendering",
      "rank": 5,
      "weightage": 0.111,
      "top_segment": "device_model: Pixel 3",
      "specific_issues": null,
      "severity": "medium",
      "metric_id": "slow_frame_rate",
      "value_display": "14.2%",
      "baseline_display": "6.5%",
      "delta_display": "+118%",
      "affected_sessions": []
    },
    {
      "problem_type": "screen_load_time",
      "rank": 6,
      "weightage": 0.111,
      "top_segment": "platform: iOS",
      "specific_issues": null,
      "severity": "critical",
      "metric_id": "screen_load_p95",
      "value_display": "3.2s",
      "baseline_display": "1.0s",
      "delta_display": "+220%",
      "affected_sessions": ["sess-checkout-003", "sess-checkout-004"]
    },
    {
      "problem_type": "screen_interactive",
      "rank": 7,
      "weightage": 0.111,
      "top_segment": "network_type: 4G",
      "specific_issues": null,
      "severity": "critical",
      "metric_id": "screen_interactive_p95",
      "value_display": "2.8s",
      "baseline_display": "0.9s",
      "delta_display": "+211%",
      "affected_sessions": ["sess-checkout-009"]
    },
    {
      "problem_type": "network_latency",
      "rank": 8,
      "weightage": 0.111,
      "top_segment": "network_type: 2G",
      "specific_issues": null,
      "severity": "high",
      "metric_id": "network_latency_p95",
      "value_display": "4.5s",
      "baseline_display": "0.8s",
      "delta_display": "+462%",
      "affected_sessions": []
    },
    {
      "problem_type": "bad_clicks",
      "rank": 9,
      "weightage": 0.111,
      "top_segment": "device_model: SM-A135F",
      "specific_issues": null,
      "severity": "medium",
      "metric_id": "bad_click_rate",
      "value_display": "8.5%",
      "baseline_display": "3.2%",
      "delta_display": "+166%",
      "affected_sessions": []
    }
  ],
  "recommendations": [
    "Investigate Samsung Galaxy A13 (Snapdragon 888) checkout crash: likely memory pressure or OS API incompatibility in v5.0.0. Profile on Pixel 3 + A13 emulation.",
    "Reduce image size or implement progressive loading for checkout on low-end devices. Current 3.2s load + 2.8s interactive time is unacceptable.",
    "Review v5.1.0 animation code — frozen frames jumped 150%. Test on low-end device under memory pressure.",
    "Optimize screen interactive time: defer non-critical UI elements, use progressive rendering, implement lazy loading for images.",
    "Consider feature flag to disable animations on devices < 2GB RAM or load time > 2s.",
    "Implement timeout retry logic for 3G network: exponential backoff + progressive timeout based on network speed."
  ]
}
```

### Schema Breakdown

`**rank`**: Integer 1–9 (or fewer if not all problems detected)

**Ranking Logic (three-tier tiebreaker):**
1. **Primary:** Severity (critical > high > medium > normal)
2. **Secondary:** Affected user volume (higher count = higher rank)
3. **Tertiary (Default Order):** Problem type priority
   - Crashes (cr)
   - ANR (anr)
   - Frozen Frames (ff)
   - Screen Load Time (slt)
   - Screen Interactive (sint)
   - Network Failures (nf)
   - Slow Rendering (sr)
   - Bad Clicks (bc)

Example: If two problems both have `severity=critical` and `affected_sessions=500`, the one appearing earlier in the priority list ranks higher.

`**weightage**`: Float 0.0–1.0

- Currently uniform: `1.0 / num_problems` (e.g., 0.111 for 9 problems)
- Can be tuned per business logic (e.g., crashes weighted higher than frustration)
- Used for calculating composite "screen health score" (future): `health_score = sum(weightage[i] * severity_score[i])`

---

## Open Questions

1. **Threshold tuning** — Are the anomaly thresholds (crash_rate > 5% = Critical) correct for your userbase?
2. **Problem ordering** — Should problems be sorted by severity or by affected user volume?
3. **Missing problems** — Should we add app crashes (non_fatal) as a separate problem? Or lump under "Network Failures"?

---

## Weightage Tuning (Future Business Logic)

Currently all problems are equally weighted (1.0/7 ≈ 0.143). This can be tuned based on business priorities:

### Example Scenario 1: Convert-Critical (E-commerce)

Prioritize network/performance issues over UX annoyances:

```json
{
  "crashes": 0.25,         // Highest — revenue blocker
  "screen_load_time": 0.25,// High — affects conversion
  "network_failures": 0.20,// High — transaction failures
  "anr": 0.10,             // Medium
  "frozen_frames": 0.10,   // Medium
  "slow_rendering": 0.05,  // Low
  "bad_clicks": 0.05       // Low — minor UX issue
}
```

### Example Scenario 2: All Equal (Current Default)

```json
{
  "crashes": 0.143,
  "screen_load_time": 0.143,
  "network_failures": 0.143,
  "anr": 0.143,
  "frozen_frames": 0.143,
  "slow_rendering": 0.143,
  "bad_clicks": 0.143
}
```

### How Weightage is Used

1. **Ranking problems**: `effective_rank = severity * volume * weightage[problem_type]`
2. **Composite health score**: `health_score = sum(weightage[i] * normalized_metric[i])` — score between 0–1
3. **Alert prioritization**: Problems with higher effective_rank trigger alerts first

Configuration location: Backend `ScreenRcaQueryBuilder` or via admin console (future).