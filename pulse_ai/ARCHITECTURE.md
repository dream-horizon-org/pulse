# Pulse AI Agentic Architecture

## Overview

Pulse AI is a **reasoning layer** built on Google's Agent Development Kit (ADK) and Gemini that analyzes mobile app observability data to produce **unified, actionable insights** across multiple business personas. It's integrated into Pulse (an observability platform) and sits behind a FastAPI server that streams agent execution via Server-Sent Events (SSE).

> **Framework:** Google Agent Development Kit (ADK)  
> **LLM:** Gemini (default: `gemini-2.5-flash`)  
> **Server:** FastAPI  
> **Language:** Python 3.12+  
> **Port:** 8000

---

## Core Design Philosophy

Pulse AI is **agent-driven, not template-driven**. Instead of hardcoded rules, LLM agents reason about data, decide what to analyze, invoke tools, and synthesize cross-cutting insights dynamically.

### Design Principle

- Agents **reason** about user intent
- Agents **delegate** tool invocation based on context
- Agents **compose** persona-specific insights into unified narratives
- Schemas **enforce** output structure (Pydantic validation)

---

## Architecture Overview

```
┌─────────────────┐
│  User Query     │
└────────┬────────┘
         │
    ┌────▼─────────────────────┐
    │   EM Agent (root)        │  ◄─── Engineering Manager Persona
    │   → 7 analytics tools    │       (performance, errors, reliability)
    │   → Query interactions,  │
    │      alerts, metrics, etc│
    └────┬──────────┬──────────┘
         │          │
    ┌────▼─┐    ┌───▼────┐
    │ RCA  │    │ Screen  │
    │ Agent│    │  RCA    │
    │      │    │ Agent   │
    └────┬─┘    └───┬────┘
         │          │
    ┌────▼──────────▼─────────────┐
    │    Report Agent             │  ◄─── Transforms analysis into
    │    → create_chart           │       user-facing output
    │    → create_table           │       (charts, tables)
    └────┬──────────────────────┘
         │
    ┌────▼──────────────────────┐
    │  SSE Stream → Frontend    │
    └───────────────────────────┘
```

---

## Personas

### Planned Personas (from README)

**Core (3 personas that drive analysis):**
- **Product Analytics** — Usage patterns, funnels, feature adoption
- **Engineering Manager** — Performance, errors, reliability
- **Designer** — UX flows, interaction patterns, usability

**Dependent (2 personas that compose from core):**
- **Customer Success** — Depends on Product Analytics + Engineering Manager + Designer
- **Business Leaders** — Depends on Product Analytics + Engineering Manager + Designer

> See: [`pulse_ai/README.md`](./README.md#personas)

### Currently Implemented Personas

| Persona | Agent | Status | Reference |
|---------|-------|--------|-----------|
| Engineering Manager | `EM Agent` | ✅ Fully implemented with 7 tools | [`agents/em/agent.py`](./agents/em/agent.py) |
| RCA (Root Cause Analysis) | `RCA Agent` | ✅ Schema-driven, no tools | [`agents/rca/agent.py`](./agents/rca/agent.py) |
| Screen RCA | `Screen RCA Agent` | ✅ Executive summary + recommendations | [`agents/screen_rca/agent.py`](./agents/screen_rca/agent.py) |

> **Note:** The Planner → Executor → Summary orchestration described in README is **not yet implemented** in code; current architecture is pipeline-based.

---

## Agent Pipeline

### Root Agent (`SequentialAgent`)

**File:** [`pulse_ai/agent.py`](./agent.py)

```python
root_agent = SequentialAgent(
    name='root_agent',
    sub_agents=[em_agent, report_agent],
    description=(
        'Sequential pipeline: EM Agent (data analysis) → '
        'Report Agent (visualization with charts and tables)'
    ),
)
```

**Execution flow:**
1. **EM Agent** analyzes data via 7 tools
2. **Report Agent** transforms analysis into charts/tables
3. Final output streamed via SSE

---

### 1. EM Agent (Engineering Manager)

**File:** [`pulse_ai/agents/em/agent.py`](./agents/em/agent.py)

LLM-powered agent representing the Engineering Manager persona, focused on **performance, errors, and reliability**.

#### Definition

```python
em_agent = Agent(
    model=AGENT_MODEL,
    name=EM_AGENT_NAME,
    description='Engineering Manager agent for Pulse mobile app observability',
    instruction=build_system_prompt,  # Dynamic UTC timestamp injection
    output_key='engineering_manager_result',
    tools=[...7 tools],
)
```

#### Tools (7 total)

| Tool | File | Purpose |
|------|------|---------|
| `query_interactions` | [`agents/em/tools/config/query_interactions.py`](./agents/em/tools/config/query_interactions.py) | List app interactions with status/health filters |
| `query_alerts` | [`agents/em/tools/config/query_alerts.py`](./agents/em/tools/config/query_alerts.py) | Fetch alerts for interactions |
| `query_interaction_health` | [`agents/em/tools/analytics/query_interaction_health.py`](./agents/em/tools/analytics/query_interaction_health.py) | Top interactions by error rate (top_n) |
| `query_interaction_metrics` | [`agents/em/tools/analytics/query_interaction_metrics.py`](./agents/em/tools/analytics/query_interaction_metrics.py) | Time-series metrics (latency, errors, throughput) |
| `query_interaction_sessions` | [`agents/em/tools/analytics/query_interaction_sessions.py`](./agents/em/tools/analytics/query_interaction_sessions.py) | Session-level data for an interaction |
| `breakdown_interaction` | [`agents/em/tools/analytics/breakdown_interaction.py`](./agents/em/tools/analytics/breakdown_interaction.py) | Drill-down metrics by dimension (device, OS, version) |
| `calculate` | [`agents/em/tools/utils/calculate.py`](./agents/em/tools/utils/calculate.py) | Safe math operations (no eval injection) |

#### Instruction Builder

**File:** [`agents/em/prompts.py`](./agents/em/prompts.py)

System prompt builder that **dynamically injects current UTC timestamp** to prevent hallucination of time-dependent insights.

```python
def build_system_prompt() -> str:
    """Build EM agent instruction with current UTC time."""
    # Timestamp injection happens here
```

---

### 2. Report Agent

**File:** [`pulse_ai/agents/report/agent.py`](./agents/report/agent.py)

Consumes EM agent output → generates interactive visualizations (charts, tables).

#### Definition

```python
report_agent = LlmAgent(
    model=AGENT_MODEL,
    name=REPORT_AGENT_NAME,
    description="Generates final user-facing response with interactive charts and data tables.",
    instruction=build_report_prompt,
    tools=[create_chart, create_table],
)
```

#### Tools (2)

| Tool | File | Purpose |
|------|------|---------|
| `create_chart` | [`agents/report/tools/create_chart.py`](./agents/report/tools/create_chart.py) | Generate ECharts JSON for interactive visualizations |
| `create_table` | [`agents/report/tools/create_table.py`](./agents/report/tools/create_table.py) | Generate HTML tables |

#### Output Format

**File:** [`agents/report/prompts.py`](./agents/report/prompts.py)

Instruction template that guides LLM to structure output with:
- Executive summary
- Key findings
- Visualizations (charts + tables)
- Recommendations

---

### 3. RCA Agent (Root Cause Analysis)

**File:** [`pulse_ai/agents/rca/agent.py`](./agents/rca/agent.py)

**Single-step, schema-driven** agent (no tools). Analyzes root-cause payload and produces structured JSON.

#### Definition

```python
rca_agent = LlmAgent(
    model=AGENT_MODEL,
    name=RCA_AGENT_NAME,
    description="Root cause analysis agent that analyzes segment data and produces structured RCA report.",
    instruction=build_rca_prompt,
    tools=[],  # No tools; schema-driven
    output_schema=RcaStructuredReportV1,
    output_key="rca_structured_report",
)
```

#### Output Schema

**File:** [`pulse_ai/schemas/rca_structured_v1.py`](./schemas/rca_structured_v1.py)

Pydantic model enforcing structured output:
```python
class RcaStructuredReportV1(BaseModel):
    root_cause_analysis: str
    key_insights: List[str]
    contributing_factors: List[str]
```

#### Use Case

Endpoint: `POST /rca/report` — Non-conversational, one-shot RCA for a specific interaction.

---

### 4. Screen RCA Agent

**File:** [`pulse_ai/agents/screen_rca/agent.py`](./agents/screen_rca/agent.py)

**Single-step, schema-driven** agent for screen-level frustration RCA.

#### Definition

```python
screen_rca_narrative_agent = LlmAgent(
    model=AGENT_MODEL,
    name=SCREEN_RCA_NARRATIVE_AGENT_NAME,
    description=(
        "Produces executive_summary and recommendations for screen-level frustration RCA "
        "from tabular RootCausePayload JSON."
    ),
    instruction=build_screen_rca_system_instruction,
    tools=[],
    output_schema=ScreenRcaNarrativeV1,
    output_key="screen_rca_narrative",
)
```

#### Output Schema

**File:** [`pulse_ai/schemas/screen_rca_narrative_v1.py`](./schemas/screen_rca_narrative_v1.py)

```python
class ScreenRcaNarrativeV1(BaseModel):
    executive_summary: str
    recommendations: List[str]
```

#### Use Case

Endpoint: `POST /rca/screen-report` — Screen-level frustration RCA with executive summary + recommendations.

---

## Dedicated RCA Agent Section

### RCA Agent (Root Cause Analysis for Interactions)

**File:** [`pulse_ai/agents/rca/agent.py`](./agents/rca/agent.py)

#### Purpose

The RCA Agent performs **deep-dive root cause analysis** on specific interactions (user flows) in a mobile app. It's invoked when a user wants to understand **why** an interaction is experiencing issues (high error rates, slow performance, crashes, etc.).

**Use case example:** "Why is the checkout flow crashing on Android 12 devices?"

#### Design Pattern: Schema-Driven (No Tools)

Unlike the EM Agent (which has 7 tools), the RCA Agent is **schema-driven**:

```python
rca_agent = LlmAgent(
    model=AGENT_MODEL,
    name=RCA_AGENT_NAME,
    description="Root cause analysis agent that analyzes segment data and produces structured RCA report.",
    instruction=build_rca_prompt,
    tools=[],  # ← NO TOOLS
    output_schema=RcaStructuredReportV1,  # ← SCHEMA ENFORCEMENT
    output_key="rca_structured_report",
    include_contents="default",
)
```

- **No tools** — Agent doesn't call Pulse backend; it reasons over pre-computed data only
- **Schema enforcement** — Pydantic validates output matches `RcaStructuredReportV1`
- **One-shot** — Non-conversational; fresh session per request (ephemeral)

#### Input Data: RootCausePayload

**File:** [`pulse_ai/schemas/root_cause.py`](./schemas/root_cause.py)

The agent receives pre-computed **segment data** from Pulse backend:

```python
class RootCausePayloadSchema(BaseModel):
    """Root-cause tabular data for an interaction."""
    noDataAvailable: bool          # No data in time window
    everythingGood: bool           # All metrics normal
    segments: List[SegmentData]    # List of dimension combinations
    exampleSessionIds: List[str]   # Session IDs for replay
    errorAttributionPayload: Optional[Dict]  # Error drill-down data
```

**Segment structure** (flat, varying dimensions):
```python
class SegmentData(BaseModel):
    label: str                     # e.g., "device_model: SM-A135F"
    dimension: str                 # e.g., "device_model"
    dimensionValue: str            # e.g., "SM-A135F"
    exampleSessionIds: List[str]   # 2 most relevant sessions for this segment
    metrics: Dict[str, MetricTriple]  # metric_name → {value, baseline, delta}
```

**Example segments** (from actual Pulse data):

```json
[
  {
    "label": "device_model: SM-A135F",
    "dimension": "device_model",
    "dimensionValue": "SM-A135F",
    "exampleSessionIds": ["sess-001", "sess-002"],
    "metrics": {
      "apdex": {"value": 0.43, "baseline": 0.75, "delta": -0.32},
      "error_rate": {"value": 0.27, "baseline": 0.05, "delta": 0.22},
      "anr_rate": {"value": 0.12, "baseline": 0.02, "delta": 0.10},
      "volume": {"value": 8500, "baseline": 10000, "delta": -1500}
    }
  },
  {
    "label": "app_version: 4.2.1",
    "dimension": "app_version",
    "dimensionValue": "4.2.1",
    "exampleSessionIds": ["sess-003", "sess-004"],
    "metrics": {
      "crash_rate": {"value": 0.08, "baseline": 0.01, "delta": 0.07},
      "apdex": {"value": 0.55, "baseline": 0.75, "delta": -0.20}
    }
  }
]
```

**Key insight:** Segments are **FLAT and heterogeneous**. One segment might have [device_model, os_version], another might have [app_version, region, network]. The agent must compare across this variation to find patterns.

#### Agent Instructions: Multi-Stage Analysis

**File:** [`pulse_ai/agents/rca/prompts.py`](./agents/rca/prompts.py)

The system prompt guides the agent through:

1. **Anomaly Detection** — Apply severity thresholds to identify abnormal metrics
   - APDEX < 0.5 = Critical (affects user satisfaction)
   - Error Rate Δ > +25% = Critical
   - ANR Rate Δ > +100% = Critical (doubled)
   - Crash Rate Δ > +100% = Critical
   - Duration P95 Δ > +50% = Warning

2. **Root Cause Identification** — Isolate problematic dimension values
   - Compare segments: If all segments with `device_model: SM-A135F` show poor metrics but other device models are fine → SM-A135F is the root cause
   - Volume-weighted analysis: Prioritize issues affecting more users
   - Correlation analysis: If multiple metrics are degraded in the same segment → likely systemic issue

3. **Severity Classification** — Tag findings with impact
   - **Critical**: Multiple thresholds breached, high volume
   - **High**: Single critical threshold, moderate volume
   - **Medium**: Single warning, low volume or isolated

4. **Evidence Binding** — Link findings to actual sessions
   - Each segment has `exampleSessionIds` (2 most relevant sessions)
   - Agent copies these into `affected_sessions` field in output
   - Frontend uses these for session replay

#### Output Schema: RcaStructuredReportV1

**File:** [`pulse_ai/schemas/rca_structured_v1.py`](./schemas/rca_structured_v1.py)

```python
class RcaStructuredReportV1(BaseModel):
    version: int  # Always 1
    
    # Executive summary (up to 4 sentences)
    executive_summary: str
    
    # Error attribution insights (if error attribution payload was provided)
    error_attribution_insights: Optional[List[Dict]] = None
    error_attribution: Optional[Dict] = None
    
    # Root cause segments (ranked by impact)
    segments: List[RcaSegmentFinding]
    
    # Actionable recommendations (≥3)
    recommendations: List[str]

class RcaSegmentFinding(BaseModel):
    rank: int                      # 1 = most impactful
    title: str                     # Segment label (e.g., "device_model: SM-A135F")
    insights: str                  # 2-4 sentences explaining severity & impact
    affected_sessions: List[str]   # Session IDs from payload's exampleSessionIds
    metrics: List[MetricDisplay]   # All metrics for this segment

class MetricDisplay(BaseModel):
    metric_id: str                 # "apdex", "error_rate", "anr_rate", etc.
    metric_label: str              # "APDEX", "Error Rate", "ANR Rate"
    value_display: str             # "0.43", "27.5%", "12ms"
    baseline_display: str          # "0.75", "5%"
    delta_display: str             # "+27.5%", "-0.32", "+200%"
    value_number: float
    baseline_number: float
```

#### Example Output

```json
{
  "version": 1,
  "executive_summary": "The checkout interaction experiences critical issues primarily on Samsung devices running Android 12, with a 32% drop in APDEX and 22% spike in error rate. A secondary issue affecting app version 4.2.1 shows a crash rate increase from 1% to 8%. Immediate action recommended for both device-specific investigation and app version rollback evaluation.",
  "error_attribution_insights": [
    {
      "signal": "anr",
      "summary": "ANR events on Samsung Galaxy A13 correlate with checkout flow timeout. 10% increase vs baseline. Likely tied to device memory constraints under checkout processing load."
    },
    {
      "signal": "non_fatal",
      "summary": "Non-fatal exceptions in version 4.2.1 checkout show 45% increase (from 20 to 29 occurrences). Stack trace points to new payment gateway integration in this version."
    },
    {
      "signal": "api",
      "summary": "Payment API timeout rate elevated 60% (from 2% to 3.2%) during peak checkout hours, correlating with error rate spike."
    }
  ],
  "segments": [
    {
      "rank": 1,
      "title": "device_model: SM-A135F",
      "insights": "Samsung Galaxy A13 shows critical degradation: APDEX dropped 32 points (0.75 → 0.43), error rate spiked 22%, and ANR rate increased 10%. Volume affected: 8,500 sessions (85% of total). This device-specific pattern suggests memory or processing constraints specific to the A13 hardware.",
      "affected_sessions": ["sess-checkout-001", "sess-checkout-002"],
      "metrics": [
        {
          "metric_id": "apdex",
          "metric_label": "APDEX",
          "value_display": "0.43",
          "baseline_display": "0.75",
          "delta_display": "-0.32",
          "value_number": 0.43,
          "baseline_number": 0.75
        },
        {
          "metric_id": "error_rate",
          "metric_label": "Error Rate",
          "value_display": "27.5%",
          "baseline_display": "5.2%",
          "delta_display": "+22.3%",
          "value_number": 0.275,
          "baseline_number": 0.052
        },
        {
          "metric_id": "volume",
          "metric_label": "Volume",
          "value_display": "8,500",
          "baseline_display": "10,000",
          "delta_display": "-1,500",
          "value_number": 8500,
          "baseline_number": 10000
        }
      ]
    },
    {
      "rank": 2,
      "title": "app_version: 4.2.1",
      "insights": "App version 4.2.1 shows elevated crash rate (1% → 8%, +7% delta) and APDEX decline (0.75 → 0.55). Affects 3,200 sessions (32% of total). Timing correlates with new payment gateway integration deployed in this version.",
      "affected_sessions": ["sess-checkout-003", "sess-checkout-004"],
      "metrics": [
        {
          "metric_id": "crash_rate",
          "metric_label": "Crash Rate",
          "value_display": "8.0%",
          "baseline_display": "1.0%",
          "delta_display": "+7.0%",
          "value_number": 0.08,
          "baseline_number": 0.01
        },
        {
          "metric_id": "apdex",
          "metric_label": "APDEX",
          "value_display": "0.55",
          "baseline_display": "0.75",
          "delta_display": "-0.20",
          "value_number": 0.55,
          "baseline_number": 0.75
        }
      ]
    }
  ],
  "recommendations": [
    "Investigate Samsung Galaxy A13 memory/CPU utilization during checkout; consider adding device-specific performance optimizations or fallback code paths.",
    "Evaluate rollback of app version 4.2.1 or hotfix for the payment gateway integration; validate against crash stack traces.",
    "Monitor API timeout rate in production; consider increasing timeout budgets or adding retry logic with exponential backoff.",
    "Perform device-specific testing on A13 under high-load checkout scenarios to validate fixes before production release."
  ]
}
```

#### Execution Flow: RCA Runner

**File:** [`pulse_ai/server/rca_runner.py`](./server/rca_runner.py)

```python
async def generate_rca_report(
    runner: Any,
    payload: RootCausePayloadSchema,
    interaction_name: str,
    example_session_ids: list[str] | None = None,
    error_attribution_payload: dict[str, Any] | None = None,
) -> RcaReportResponse:
    """
    Runs the RCA pipeline with retries and schema validation.
    
    Flow:
    1. Build user message from payload + error attribution
    2. Run RCA agent (attempt 1)
    3. If schema validation fails → retry (up to MAX_RETRIES=2)
    4. Return structured report or raise RcaRunnerError
    """
```

**Retry logic** (important for LLM reliability):

- **Attempt 1:** Run agent → validate schema
- If validation fails → **Attempt 2:** Run agent again (fresh session, non-deterministic LLM)
- If both fail → **RcaRunnerError(500)**: "RCA report generation failed after retries"
- If attempt times out (> 300s) → **RcaRunnerError(504)**: "Gateway timeout"

**Session lifecycle:**
- Fresh ephemeral session per attempt (ID: `USER_ID_RCA = "rca_report_service"`)
- Successful attempt → clean up session, return report
- Failed attempt → clean up session, retry or error

#### API Endpoint

**File:** [`pulse_ai/server/routes.py`](./server/routes.py#L248)

```python
@app.post("/rca/report")
async def generate_root_cause_report(
    request: RcaReportRequest,
    authorization: str | None = Header(default=None, alias="Authorization"),
    project_id: str | None = Header(default=None, alias="X-Project-ID"),
) -> RcaReportResponse:
    """
    Generate non-conversational RCA report for an interaction.
    
    Accepts root-cause data two ways:
    1. Embedded — rootCausePayload in request body (preferred)
    2. Callback — omit rootCausePayload; pulse_ai calls pulse-server to fetch it
    """
```

**Request schema:**
```python
class RcaReportRequest(BaseModel):
    entityKey: str                                    # Interaction name
    date: str                                         # Date (yyyy-MM-dd)
    rootCausePayload: Optional[RootCausePayloadSchema]  # Pre-fetched data
    errorAttributionPayload: Optional[Dict]           # Error drill-down
```

**Response schema:**
```python
class RcaReportResponse(BaseModel):
    report: ReportPayloadSchema                       # Contains RcaStructuredReportV1
    cached: bool                                      # Cache hit? (always False for RCA)
```

---

## Dedicated Screen RCA Agent Section

### Screen RCA Agent (Frustration Analysis for Screens)

**File:** [`pulse_ai/agents/screen_rca/agent.py`](./agents/screen_rca/agent.py)

#### Purpose

The Screen RCA Agent performs **screen-level frustration analysis**. It analyzes user interaction metrics on a specific UI screen (e.g., "Checkout Screen", "User Settings") to identify **why users are frustrated** (rage taps, dead clicks, etc.).

**Use case example:** "Why are users rage-tapping the checkout button on iOS?"

#### Design Pattern: Schema-Driven (No Tools)

```python
screen_rca_narrative_agent = LlmAgent(
    model=AGENT_MODEL,
    name=SCREEN_RCA_NARRATIVE_AGENT_NAME,
    description=(
        "Produces executive_summary and recommendations for screen-level frustration RCA "
        "from tabular RootCausePayload JSON."
    ),
    instruction=build_screen_rca_system_instruction,
    tools=[],  # ← NO TOOLS
    output_schema=ScreenRcaNarrativeV1,  # ← SCHEMA ENFORCEMENT
    output_key="screen_rca_narrative",
)
```

Similar to RCA Agent:
- **No tools** — Analyzes pre-computed data only
- **Schema enforcement** — Validates output
- **One-shot** — Ephemeral session per request

#### Input Data: Frustration Metrics

**File:** [`pulse_ai/schemas/root_cause.py`](./schemas/root_cause.py)

Reuses `RootCausePayloadSchema` but with **frustration-specific metrics**:

```python
class SegmentData(BaseModel):
    label: str                     # e.g., "platform: ios"
    dimension: str                 # e.g., "platform"
    dimensionValue: str            # e.g., "ios"
    metrics: Dict[str, MetricTriple]
```

**Frustration metrics** (instead of performance metrics):
- **click_volume** — Total qualifying clicks on screen
- **tap_count** — Normal taps (baseline interaction)
- **rage_count** — Rapid repeated taps (frustration signal)
- **dead_count** — Taps with no response/navigation (frustration signal)
- **bad_frustration** — Composite frustration score (0.0–1.0, higher = worse)

**Example segments for Checkout screen:**

```json
[
  {
    "label": "platform: ios",
    "dimension": "platform",
    "dimensionValue": "ios",
    "metrics": {
      "click_volume": {"value": 45000, "baseline": 40000, "delta": 5000},
      "rage_count": {"value": 2800, "baseline": 1200, "delta": 1600},
      "dead_count": {"value": 800, "baseline": 300, "delta": 500},
      "bad_frustration": {"value": 0.65, "baseline": 0.30, "delta": 0.35}
    }
  },
  {
    "label": "app_version: 5.0.0",
    "dimension": "app_version",
    "dimensionValue": "5.0.0",
    "metrics": {
      "rage_count": {"value": 3500, "baseline": 1200, "delta": 2300},
      "bad_frustration": {"value": 0.72, "baseline": 0.30, "delta": 0.42}
    }
  }
]
```

**Flags:**
- `noDataAvailable: true` — No checkout taps in window
- `everythingGood: true` — All frustration metrics normal

#### Agent Instructions: Frustration Analysis

**File:** [`pulse_ai/agents/screen_rca/prompts.py`](./agents/screen_rca/prompts.py)

```python
def build_screen_rca_system_instruction(ctx=None) -> str:
    """
    You receive RootCausePayload for a single screen describing:
    - Taps, rage taps, dead clicks
    - Bad frustration rate vs baseline
    
    Compare segments to identify:
    - Which dimensions (platform, app version, region) have high frustration
    - Whether frustration is systemic or isolated
    - Correlation with business metrics (e.g., checkout abandonment)
    
    Output: Executive summary + 3-7 recommendations
    """
```

**Key differences from interaction RCA:**
- No session-level evidence ("Do not claim session-level evidence; session IDs are not provided")
- Focus on **user experience and UX patterns**, not system metrics
- Recommendations target **mobile engineers and PMs**

#### Output Schema: ScreenRcaNarrativeV1

**File:** [`pulse_ai/schemas/screen_rca_narrative_v1.py`](./schemas/screen_rca_narrative_v1.py)

```python
class ScreenRcaNarrativeV1(BaseModel):
    version: int                   # Always 1
    
    # Executive summary (up to 4 sentences)
    executive_summary: str
    
    # Actionable recommendations (3-7 bullets)
    recommendations: List[str]
```

**Simpler than interaction RCA:**
- No segment rankings (Screen RCA is summary-focused)
- No detailed metric tables
- Focus on narrative + recommendations

#### Example Output

```json
{
  "version": 1,
  "executive_summary": "Checkout screen frustration has escalated significantly on iOS, with rage taps increasing 133% (1,200 → 2,800) and dead clicks up 167% (300 → 800) vs baseline. App version 5.0.0 shows the highest frustration spike (bad_frustration: 0.72, +42% delta). The issue appears tied to UI responsiveness in the new payment flow, affecting 45,000 total tap events in the analysis window.",
  "recommendations": [
    "Profile iOS app version 5.0.0 payment flow for UI thread blocking; consider simplifying the submit button animation or moving validation off the main thread.",
    "Increase tap target size and visual feedback for payment buttons to reduce missed clicks and rage taps.",
    "A/B test simplified checkout flow (fewer steps, clearer CTA) against current version 5.0.0 to validate UX improvements.",
    "Monitor dead-click rate in real-time dashboards; trigger alerts if dead clicks exceed 5% of total taps.",
    "Review payment gateway integration timeout; if user sees 'loading' state for >2s, reduce perceived latency with progress indicators or optimistic UI updates.",
    "Consider rollback of version 5.0.0 payment flow to previous version pending hotfix validation."
  ]
}
```

#### Execution Flow: Screen RCA Runner

**File:** [`pulse_ai/server/screen_rca_runner.py`](./server/screen_rca_runner.py)

```python
async def generate_screen_rca_report(
    runner: Any,
    payload: RootCausePayloadSchema,
    screen_name: str,
    start_iso: str | None = None,
    end_iso: str | None = None,
    date_str: str | None = None,
    as_of_iso: str | None = None,
) -> ScreenRcaReportResponse:
    """
    Runs the screen RCA narrative pipeline.
    
    Simpler than interaction RCA:
    - Single attempt (no retries)
    - 300s timeout
    - No error attribution drill-down
    """
```

**Differences from RCA runner:**
- **No retries** — Screen RCA is simpler, typically succeeds on first attempt
- **Single attempt** — Fresh session, run agent, validate, return/error
- **Same timeout** — 300s (RCA_PIPELINE_TIMEOUT_SECONDS)
- **No error attribution** — Frustration analysis doesn't drill into crash/error signals

#### API Endpoint

**File:** [`pulse_ai/server/routes.py`](./server/routes.py#L292)

```python
@app.post("/rca/screen-report")
async def generate_screen_root_cause_narrative(
    request: ScreenRcaReportRequest,
) -> ScreenRcaReportResponse:
    """
    Generate executive summary and recommendations for screen-level frustration RCA.
    
    Requires rootCausePayload (tabular JSON from GET /v1/screens/{screen}/root-cause).
    """
```

**Request schema:**
```python
class ScreenRcaReportRequest(BaseModel):
    screenName: str                                   # Screen identifier
    rootCausePayload: Dict                            # Pre-fetched frustration data
    start: Optional[str]                              # ISO start (inclusive)
    end: Optional[str]                                # ISO end (exclusive)
    date: Optional[str]                               # Legacy date (yyyy-MM-dd)
    asOf: Optional[str]                               # Legacy asOf (ISO)
```

**Response schema:**
```python
class ScreenRcaReportResponse(BaseModel):
    report: ScreenRcaReportPayloadSchema              # Contains ScreenRcaNarrativeV1
    cached: bool                                      # Always False
```

---

## RCA vs. Screen RCA: Comparison Table

| Aspect | RCA Agent | Screen RCA Agent |
|--------|-----------|------------------|
| **Purpose** | Interaction-level root cause analysis | Screen-level frustration analysis |
| **Metrics** | APDEX, Error Rate, Crashes, ANR, P95 latency | Rage taps, dead clicks, bad frustration score |
| **Dimensions** | Device, OS, App Version, Network, Region | Platform, App Version, Region, etc. |
| **Output Schema** | `RcaStructuredReportV1` (segments + metrics) | `ScreenRcaNarrativeV1` (summary + recommendations) |
| **Segment Ranking** | Yes (rank 1, 2, 3...) | No (narrative-only) |
| **Session Evidence** | Yes (`affected_sessions` array) | No (screen RCA has no session IDs) |
| **Error Attribution** | Yes (optional drill-down) | No |
| **Retries** | Yes (max 2 attempts on schema validation) | No (single attempt) |
| **Timeout** | 300s | 300s |
| **Use Case** | "Why is checkout crashing on Android 12?" | "Why are users rage-tapping the payment button?" |
| **Frontend Integration** | Links to session replay | Suggests UX improvements |

---

## Error Attribution (Optional Feature for RCA Agent)

**File:** [`pulse_ai/schemas/error_attribution_rca.py`](./schemas/error_attribution_rca.py)

The RCA Agent can optionally receive **error attribution drill-down data** to correlate root causes with specific error signals.

**Signals analyzed:**
- `anr` — Application Not Responding events
- `non_fatal` — Non-fatal exceptions
- `api` — API errors

**Structure:**
```python
class ErrorAttributionPayload(BaseModel):
    disclaimer: str                # Legal/correlation disclaimer
    minRiskRatioForIssueAttribution: float  # Threshold (e.g., 2.0)
    relatedAttributions: List[Dict]  # Drill-down rows
```

**RCA Agent obligation:** When error attribution is provided, output **exactly 3 rows** in order:
1. `{"signal": "anr", "summary": "...", "caveat": "..."}`
2. `{"signal": "non_fatal", "summary": "..."}`
3. `{"signal": "api", "summary": "..."}`

Each row must include:
- Drill-down findings (2-4 sentences)
- Optional caveat (e.g., "Correlative drill-down only; not causal proof")

If a signal has no meaningful data, still output the row with a **neutral placeholder**:
- "No notable ANR drill-down patterns in the supplied window."

---

## Server & API

**File:** [`pulse_ai/server/app.py`](./server/app.py)

FastAPI application factory with ADK runners, middleware, and shared services.

### Endpoints

**File:** [`pulse_ai/server/routes.py`](./server/routes.py)

| Endpoint | Method | Purpose | Agent |
|----------|--------|---------|-------|
| `/run_sse` | POST | Conversational streaming (multi-turn chat) | `root_agent` (EM → Report) |
| `/sessions` | POST | Create new chat session | ADK session service |
| `/sessions/{user_id}` | GET | List user's sessions (scoped to project) | ADK session service |
| `/sessions/{user_id}/{session_id}` | GET | Get session with message history | ADK session service |
| `/sessions/{user_id}/{session_id}` | DELETE | Delete session (idempotent 204) | ADK session service |
| `/rca/report` | POST | Generate non-conversational RCA | `rca_agent` |
| `/rca/screen-report` | POST | Screen-level RCA narrative | `screen_rca_narrative_agent` |
| `/health` | GET | Health check | — |

### Middleware

**File:** [`pulse_ai/server/middleware.py`](./server/middleware.py)

- **AuthMiddleware** — Validates bearer tokens via `X-Project-ID` header
- **CORSMiddleware** — Allows frontend origins (configurable via `CORS_ALLOWED_ORIGINS`)

### Session Management

**File:** [`pulse_ai/server/session_scope_store.py`](./server/session_scope_store.py)

- ADK's `DatabaseSessionService` (async SQLAlchemy) or in-memory
- Sidecar `session_scope_store` — Tracks `project_id` per session for multi-tenancy isolation
- Enables project-scoped session listing and deletion

---

## Data Flow: Tools → Backend → Analytics

```
┌─────────────────────┐
│  Frontend (React)   │
│  JWT in Authorization header
└──────────┬──────────┘
           │
    ┌──────▼──────────────────┐
    │  pulse-ui sends query   │
    │  + Authorization header │
    └──────┬──────────────────┘
           │
    ┌──────▼──────────────────────┐
    │  Pulse AI Agent Tool        │
    │  (e.g., query_interactions) │
    │  → Extracts bearer token    │
    │  → Extracts project_id      │
    └──────┬──────────────────────┘
           │
    ┌──────▼──────────────────────────────┐
    │  Pulse Backend API (:8080)          │
    │  POST /v1/interactions/...          │
    │  Authorization: Bearer <jwt>        │
    │  X-Project-ID: <project_id>         │
    └──────┬──────────────────────────────┘
           │
    ┌──────▼──────────────────────────────┐
    │  OpenFGA Authorization Check        │
    │  (ensures project access)           │
    └──────┬──────────────────────────────┘
           │
    ┌──────▼──────────────────────────────┐
    │  ClickHouse Queries                 │
    │  • otel_traces                      │
    │  • otel_logs                        │
    │  • otel_metrics                     │
    │  • stack_trace_events               │
    │  • interaction_heatmaps_daily       │
    └──────┬──────────────────────────────┘
           │
    ┌──────▼──────────────────────────────┐
    │  Tool returns structured result     │
    └──────┬──────────────────────────────┘
           │
    ┌──────▼──────────────────────────────┐
    │  Agent reasons + invokes more tools │
    │  or passes to Report Agent          │
    └──────┬──────────────────────────────┘
           │
    ┌──────▼──────────────────────────────┐
    │  SSE Stream → Frontend              │
    │  (real-time reasoning feedback)     │
    └──────────────────────────────────────┘
```

### Tool Implementation Pattern

**Example:** [`agents/em/tools/config/query_interactions.py`](./agents/em/tools/config/query_interactions.py)

```python
async def query_interactions(
    tool_context: ToolContext,
    health: str = "ALL",
    page: int = 0,
    size: int = 10,
) -> dict:
    """Query interactions with optional health/pagination filters."""
    # 1. Extract session context (bearer token, project_id)
    session_context = tool_context.user_state.get("session_context")
    
    # 2. Build Pulse backend request
    url = f"{PULSE_SERVER_BASE_URL}/v1/interactions?health={health}&page={page}&size={size}"
    headers = {
        "Authorization": session_context.bearer_token,
        "X-Project-ID": session_context.project_id,
    }
    
    # 3. Call Pulse backend
    async with httpx.AsyncClient() as client:
        response = await client.get(url, headers=headers, timeout=75)
    
    # 4. Return structured result
    return {
        "status": "success",
        "data": response.json(),
    }
```

---

## Schemas & Type Safety

**File:** [`pulse_ai/schemas/`](./schemas/)

Output schemas enforce LLM compliance via Pydantic validation:

| Schema | Agent | File | Fields |
|--------|-------|------|--------|
| `RcaStructuredReportV1` | RCA Agent | [`rca_structured_v1.py`](./schemas/rca_structured_v1.py) | `root_cause_analysis`, `key_insights`, `contributing_factors` |
| `ScreenRcaNarrativeV1` | Screen RCA Agent | [`screen_rca_narrative_v1.py`](./schemas/screen_rca_narrative_v1.py) | `executive_summary`, `recommendations` |
| `RootCausePayloadSchema` | Shared | — | Incoming root-cause data structure |

**Schema enforcement:** When `output_schema` is set on an agent, ADK ensures LLM output matches the schema. Invalid JSON is rejected.

---

## Configuration

**File:** `.env`

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GOOGLE_API_KEY` | ✅ Yes | — | Google AI Studio API key from [aistudio.google.com](https://aistudio.google.com/apikey) |
| `AGENT_MODEL` | ❌ No | `gemini-2.5-flash` | Gemini model family (e.g., `gemini-2.5-pro`) |
| `GOOGLE_GENAI_USE_VERTEXAI` | ❌ No | `0` | Set to `1` to use Vertex AI instead of AI Studio |
| `LOG_LEVEL` | ❌ No | `INFO` | Logging level: `DEBUG`, `INFO`, `WARNING`, `ERROR` |
| `CORS_ALLOWED_ORIGINS` | ❌ No | `localhost:3000,3001` | Comma-separated allowed frontend origins |
| `SESSION_DB_URL` | ❌ No | in-memory | Session persistence (e.g., `sqlite:///sessions.db`, `postgresql://...`) |
| `PULSE_SERVER_BASE_URL` | ❌ No | `http://localhost:8080` | Pulse backend URL for tool calls |
| `PULSE_BASE_URL` | ❌ No | `http://localhost:8080` | Alias for `PULSE_SERVER_BASE_URL` |

**File:** [`pulse_ai/constants.py`](./constants.py)

```python
APP_NAME = "pulse_ai"
DEFAULT_MODEL = "gemini-2.5-flash"
AGENT_MODEL = os.getenv("AGENT_MODEL", DEFAULT_MODEL)
PULSE_SERVER_BASE_URL = os.getenv("PULSE_SERVER_BASE_URL", "http://localhost:8080")
```

---

## Running Pulse AI

### Standalone (Docker)

```bash
cd pulse_ai
cp .env.example .env
# Edit .env and paste your GOOGLE_API_KEY

./setup.sh              # Build and start
./setup.sh stop         # Stop
./setup.sh restart      # Rebuild and restart
./setup.sh logs         # Tail logs
./setup.sh clean        # Remove containers, images, volumes
```

**Files:**
- `Dockerfile` — Python 3.12-slim + google-adk
- `docker-compose.yml` — Single-service compose
- `setup.sh` — CLI wrapper

### Full Stack (from `deploy/`)

```bash
cd deploy
cp .env.example .env
# Edit .env and set GOOGLE_API_KEY, other variables

./scripts/start.sh -d   # Start detached
./scripts/logs.sh pulse-ai-agent  # Tail logs
./scripts/stop.sh       # Stop
```

**Port:** 8000  
**Health check:** `curl -sf http://localhost:8000/health`

### Local Python (without Docker)

```bash
cd pulse_ai
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# Edit .env and paste GOOGLE_API_KEY

adk web  # Start ADK web UI on :8000
```

---

## Development

### Live Reloading

Source files (`agent.py`, `server/`, `constants.py`, `agents/`, `__init__.py`) are **volume-mounted** into the container, so code changes reflect **without rebuilding**.

```bash
# Edit pulse_ai/agents/em/agent.py locally
# Changes appear in running container immediately
```

### Dependency Updates

For changes to `requirements.txt`, rebuild:

```bash
./setup.sh restart
```

### Testing

**File:** [`pulse_ai/tests/`](./tests/)

Run tests:

```bash
pytest pulse_ai/tests/
pytest pulse_ai/tests/test_agent.py -v
pytest pulse_ai/tests/test_rca_agent.py::test_rca_returns_correct_schema -v
```

---

## Project Structure

```
pulse_ai/
├── __init__.py                  # Package init
├── agent.py                     # Root SequentialAgent (EM → Report)
├── constants.py                 # Model, URL, timeout, schema configs
├── .env                         # Credentials (not committed)
├── .env.example                 # Template
├── .dockerignore                # Docker build ignore
├── Dockerfile                   # Python 3.12-slim + google-adk
├── docker-compose.yml           # Single-service compose
├── setup.sh                     # CLI: start / stop / restart / logs / clean
├── requirements.txt             # Dependencies (google-adk, python-dotenv, httpx, etc.)
├── README.md                    # Quick start + architecture overview
├── ARCHITECTURE.md              # This file
├──
├── agents/
│   ├── __init__.py              # Exports em_agent, rca_agent, screen_rca_narrative_agent
│   ├── em/                      # Engineering Manager persona
│   │   ├── agent.py             # EM Agent definition
│   │   ├── prompts.py           # System prompt builder (UTC injection)
│   │   ├── tools/               # 7 tools for data analysis
│   │   │   ├── config/          # Configuration queries
│   │   │   │   ├── query_interactions.py
│   │   │   │   └── query_alerts.py
│   │   │   ├── analytics/       # Analytics queries
│   │   │   │   ├── query_interaction_health.py
│   │   │   │   ├── query_interaction_metrics.py
│   │   │   │   ├── query_interaction_sessions.py
│   │   │   │   └── breakdown_interaction.py
│   │   │   └── utils/           # Utilities
│   │   │       └── calculate.py # Safe math (no eval)
│   │   ├── templates/           # Response templates
│   │   │   ├── base.py
│   │   │   └── interaction_templates.py
│   │   └── transformers/        # Response transformers
│   │       └── response_transformer.py
│   ├── rca/                     # Root Cause Analysis
│   │   ├── agent.py             # RCA Agent (schema-driven, no tools)
│   │   └── prompts.py           # RCA system prompt builder
│   ├── report/                  # Report generation
│   │   ├── agent.py             # Report Agent (charts + tables)
│   │   ├── prompts.py           # Report formatting instructions
│   │   └── tools/
│   │       ├── create_chart.py  # ECharts JSON generation
│   │       └── create_table.py  # HTML table generation
│   └── screen_rca/              # Screen-level RCA
│       ├── agent.py             # Screen RCA Agent (schema-driven)
│       └── prompts.py           # Screen RCA system prompt
├──
├── schemas/                     # Pydantic models for structured output
│   ├── __init__.py
│   ├── rca_structured_v1.py     # RCA output schema
│   ├── screen_rca_narrative_v1.py  # Screen RCA output schema
│   ├── error_attribution_rca.py # Error attribution schema
│   └── root_cause.py            # Root cause data contract
├──
├── server/                      # FastAPI app + routes
│   ├── __init__.py
│   ├── app.py                   # FastAPI factory, runners, session service
│   ├── routes.py                # Endpoint handlers
│   ├── middleware.py            # Auth + CORS middleware
│   ├── run_sse_utils.py         # SSE streaming utilities
│   ├── serializers.py           # Event-to-message serialization
│   ├── project_headers.py       # X-Project-ID validation
│   ├── session_scope_store.py   # Multi-tenant session tracking
│   ├── root_cause_fetch.py      # Fetch RCA payload from pulse-server
│   ├── rca_runner.py            # RCA agent execution
│   ├── screen_rca_runner.py     # Screen RCA agent execution
│   └── schemas.py               # Request/response models
├──
├── client/
│   ├── __init__.py
│   └── pulse_client.py          # Python client for pulse_ai endpoints
├──
├── tests/                       # Unit and integration tests
│   ├── __init__.py
│   ├── conftest.py              # Pytest fixtures
│   ├── test_agent.py
│   ├── test_rca_agent.py
│   ├── test_rca_runner_prompt_order.py
│   ├── test_analytics_tools.py
│   ├── test_calculate_tool.py
│   └── ... (more tests)
├──
└── tool_session_auth.py         # Bearer token extraction from session
```

---

## RCA Case Examples: Segment Dependency Patterns

The RCA Agent analyzes segments that can have **varying dimension combinations**. Understanding how segments relate helps interpret root causes. Here are three patterns:

### Case 1: Completely Dependent Segments (Single Dimension = Root Cause)

**Scenario:** Checkout flow failing on Android 12 devices regardless of other factors.

**Input segments:**

```json
[
  {
    "label": "os_version: Android 12",
    "dimension": "os_version",
    "dimensionValue": "Android 12",
    "exampleSessionIds": ["sess-001", "sess-002"],
    "metrics": {
      "apdex": {"value": 0.35, "baseline": 0.75, "delta": -0.40},
      "error_rate": {"value": 0.32, "baseline": 0.05, "delta": 0.27},
      "crash_rate": {"value": 0.06, "baseline": 0.01, "delta": 0.05},
      "volume": {"value": 12000, "baseline": 10000, "delta": 2000}
    }
  },
  {
    "label": "os_version: Android 11",
    "dimension": "os_version",
    "dimensionValue": "Android 11",
    "exampleSessionIds": ["sess-003", "sess-004"],
    "metrics": {
      "apdex": {"value": 0.74, "baseline": 0.75, "delta": -0.01},
      "error_rate": {"value": 0.06, "baseline": 0.05, "delta": 0.01},
      "crash_rate": {"value": 0.01, "baseline": 0.01, "delta": 0.00},
      "volume": {"value": 9500, "baseline": 10000, "delta": -500}
    }
  },
  {
    "label": "os_version: iOS 16",
    "dimension": "os_version",
    "dimensionValue": "iOS 16",
    "exampleSessionIds": ["sess-005", "sess-006"],
    "metrics": {
      "apdex": {"value": 0.76, "baseline": 0.75, "delta": 0.01},
      "error_rate": {"value": 0.05, "baseline": 0.05, "delta": 0.00},
      "crash_rate": {"value": 0.01, "baseline": 0.01, "delta": 0.00},
      "volume": {"value": 8500, "baseline": 9000, "delta": -500}
    }
  }
]
```

**RCA Analysis:**

All segments are **completely dependent** on a single dimension (OS version). The pattern is clear:
- **Android 12**: All metrics critical (APDEX -40%, error rate +27%, crash rate +5%)
- **Android 11**: All metrics normal (APDEX -1%, error rate +1%, no crash increase)
- **iOS 16**: All metrics normal

**Root cause:** OS-specific issue. Likely Android 12 system API changes or resource constraints.

**RCA Output:**

```json
{
  "version": 1,
  "executive_summary": "Checkout failure is isolated to Android 12 devices, affecting 12,000 users (55% of total volume). Critical metrics include APDEX drop to 0.35 (down from 0.75), error rate spike to 32% (up from 5%), and crash rate increase to 6%. Android 11 and iOS 16 devices show normal behavior, confirming the root cause is OS-specific.",
  "segments": [
    {
      "rank": 1,
      "title": "os_version: Android 12",
      "insights": "Android 12 shows critical system-level degradation: APDEX fell 40 points, error rate increased 27%, and crash rate quintupled. Volume impact is substantial (12,000 sessions). The across-the-board metric failure suggests an incompatibility with Android 12's permissions model, memory management, or network stack changes introduced in this OS version.",
      "affected_sessions": ["sess-001", "sess-002"],
      "metrics": [
        {
          "metric_id": "apdex",
          "metric_label": "APDEX",
          "value_display": "0.35",
          "baseline_display": "0.75",
          "delta_display": "-0.40",
          "value_number": 0.35,
          "baseline_number": 0.75
        },
        {
          "metric_id": "error_rate",
          "metric_label": "Error Rate",
          "value_display": "32%",
          "baseline_display": "5%",
          "delta_display": "+27%",
          "value_number": 0.32,
          "baseline_number": 0.05
        }
      ]
    }
  ],
  "recommendations": [
    "Test checkout flow on Android 12 emulator; check for permissions (INTERNET, NETWORK_STATE) that Android 12 enforces strictly.",
    "Review crash logs for Android 12 to identify specific exception (e.g., SecurityException, IllegalStateException).",
    "Consider API-level branching: Add conditional code paths for Android 12+ to handle network/permissions differently.",
    "Coordinate with QA to test on physical Android 12 devices (Samsung Galaxy S21+, Pixel 6+) to confirm fix before rollout."
  ]
}
```

---

### Case 2: Semi-Dependent Segments (Multiple Dimensions, Overlapping Issues)

**Scenario:** Payment flow experiencing issues across multiple device/app version combinations, but not uniformly.

**Input segments:**

```json
[
  {
    "label": "device_model: Samsung Galaxy S21, app_version: 4.2.1",
    "dimension": "device_model,app_version",
    "exampleSessionIds": ["sess-007", "sess-008"],
    "metrics": {
      "apdex": {"value": 0.45, "baseline": 0.75, "delta": -0.30},
      "error_rate": {"value": 0.25, "baseline": 0.05, "delta": 0.20},
      "anr_rate": {"value": 0.08, "baseline": 0.02, "delta": 0.06},
      "volume": {"value": 3200, "baseline": 3500, "delta": -300}
    }
  },
  {
    "label": "device_model: Samsung Galaxy S21, app_version: 4.1.0",
    "dimension": "device_model,app_version",
    "exampleSessionIds": ["sess-009", "sess-010"],
    "metrics": {
      "apdex": {"value": 0.72, "baseline": 0.75, "delta": -0.03},
      "error_rate": {"value": 0.06, "baseline": 0.05, "delta": 0.01},
      "anr_rate": {"value": 0.02, "baseline": 0.02, "delta": 0.00},
      "volume": {"value": 2800, "baseline": 3000, "delta": -200}
    }
  },
  {
    "label": "device_model: iPhone 13, app_version: 4.2.1",
    "dimension": "device_model,app_version",
    "exampleSessionIds": ["sess-011", "sess-012"],
    "metrics": {
      "apdex": {"value": 0.71, "baseline": 0.75, "delta": -0.04},
      "error_rate": {"value": 0.07, "baseline": 0.05, "delta": 0.02},
      "anr_rate": {"value": 0.01, "baseline": 0.02, "delta": -0.01},
      "volume": {"value": 2900, "baseline": 3200, "delta": -300}
    }
  },
  {
    "label": "device_model: Google Pixel 6, app_version: 4.2.1",
    "dimension": "device_model,app_version",
    "exampleSessionIds": ["sess-013", "sess-014"],
    "metrics": {
      "apdex": {"value": 0.52, "baseline": 0.75, "delta": -0.23},
      "error_rate": {"value": 0.18, "baseline": 0.05, "delta": 0.13},
      "anr_rate": {"value": 0.05, "baseline": 0.02, "delta": 0.03},
      "volume": {"value": 2950, "baseline": 3100, "delta": -150}
    }
  }
]
```

**RCA Analysis:**

Segments are **semi-dependent**—both device and app version matter:
- **Samsung S21 + v4.2.1**: Critical (APDEX -30%, error +20%, ANR +6%)
- **Samsung S21 + v4.1.0**: Normal (APDEX -3%, error +1%, ANR flat)
- **iPhone 13 + v4.2.1**: Normal (APDEX -4%, error +2%)
- **Pixel 6 + v4.2.1**: High severity (APDEX -23%, error +13%, ANR +3%)

**Pattern:** App version 4.2.1 is problematic, but the severity varies by device. Suggests:
- App v4.2.1 has a **device-specific bug** (not all devices equally affected)
- Samsung Galaxy S21 appears most susceptible
- Pixel 6 also affected but less severely than S21

**RCA Output:**

```json
{
  "version": 1,
  "executive_summary": "Payment flow issues in app version 4.2.1 are device-dependent, with the highest impact on Samsung Galaxy S21 (APDEX 0.45, error rate 25%). Pixel 6 also shows elevated errors (18%), while iPhone 13 with the same app version is largely unaffected. App version 4.1.0 shows normal behavior on S21, indicating a regression in 4.2.1. The pattern suggests a device-specific memory or performance issue, possibly related to Samsung's custom Android implementation.",
  "segments": [
    {
      "rank": 1,
      "title": "device_model: Samsung Galaxy S21, app_version: 4.2.1",
      "insights": "Most critical segment: S21 + v4.2.1 shows APDEX collapse (0.45), error rate spike (25%), and ANR increase (8%). Affects 3,200 sessions. The fact that the same device (S21) with v4.1.0 is normal confirms the regression is in v4.2.1, not the device itself. Likely a resource management issue specific to S21's hardware (Snapdragon 888, 8GB RAM base).",
      "affected_sessions": ["sess-007", "sess-008"],
      "metrics": [
        {
          "metric_id": "apdex",
          "metric_label": "APDEX",
          "value_display": "0.45",
          "baseline_display": "0.75",
          "delta_display": "-0.30",
          "value_number": 0.45,
          "baseline_number": 0.75
        }
      ]
    },
    {
      "rank": 2,
      "title": "device_model: Google Pixel 6, app_version: 4.2.1",
      "insights": "Secondary issue: Pixel 6 + v4.2.1 shows elevated errors (18% vs 5% baseline) and high ANR rate (5%). Volume impact smaller (2,950 sessions) but severity remains high. The fact that multiple high-end Android flagships are affected suggests a systematic issue in v4.2.1's Android-specific code paths, not device-specific quirks.",
      "affected_sessions": ["sess-013", "sess-014"],
      "metrics": [
        {
          "metric_id": "error_rate",
          "metric_label": "Error Rate",
          "value_display": "18%",
          "baseline_display": "5%",
          "delta_display": "+13%",
          "value_number": 0.18,
          "baseline_number": 0.05
        }
      ]
    }
  ],
  "recommendations": [
    "Hotfix for app v4.2.1: Reduce thread pool size or memory allocation in payment processing—likely hitting memory limits on Snapdragon 888 devices.",
    "Test payment flow under low-memory conditions (simulate <1GB available RAM) on S21 and Pixel 6 to reproduce issue.",
    "Evaluate v4.2.1 changes to network timeouts, SSL/TLS handshake, or payment gateway integration for inefficient blocking calls.",
    "Rollback v4.2.1 to v4.1.0 for high-end Android devices while preparing hotfix; use feature flags to A/B test fix.",
    "Monitor Pixel 6 and S21 separately post-fix; consider device-specific testing in CI/CD pipeline."
  ]
}
```

---

### Case 3: Independent Segments (Isolated Issues, Different Root Causes)

**Scenario:** Checkout flow has multiple unrelated problems: one on iOS due to network, another on Android due to UI, etc.

**Input segments:**

```json
[
  {
    "label": "platform: ios, network: cellular",
    "dimension": "platform,network",
    "exampleSessionIds": ["sess-015", "sess-016"],
    "metrics": {
      "apdex": {"value": 0.50, "baseline": 0.75, "delta": -0.25},
      "error_rate": {"value": 0.22, "baseline": 0.05, "delta": 0.17},
      "duration_p95": {"value": 8500, "baseline": 3000, "delta": 5500},
      "volume": {"value": 5200, "baseline": 5000, "delta": 200}
    }
  },
  {
    "label": "platform: ios, network: wifi",
    "dimension": "platform,network",
    "exampleSessionIds": ["sess-017", "sess-018"],
    "metrics": {
      "apdex": {"value": 0.73, "baseline": 0.75, "delta": -0.02},
      "error_rate": {"value": 0.06, "baseline": 0.05, "delta": 0.01},
      "duration_p95": {"value": 3100, "baseline": 3000, "delta": 100},
      "volume": {"value": 4800, "baseline": 5100, "delta": -300}
    }
  },
  {
    "label": "platform: android, app_version: 4.2.0",
    "dimension": "platform,app_version",
    "exampleSessionIds": ["sess-019", "sess-020"],
    "metrics": {
      "apdex": {"value": 0.60, "baseline": 0.75, "delta": -0.15},
      "error_rate": {"value": 0.12, "baseline": 0.05, "delta": 0.07},
      "frozen_frame_rate": {"value": 0.15, "baseline": 0.05, "delta": 0.10},
      "volume": {"value": 6100, "baseline": 5900, "delta": 200}
    }
  },
  {
    "label": "platform: android, app_version: 4.1.0",
    "dimension": "platform,app_version",
    "exampleSessionIds": ["sess-021", "sess-022"],
    "metrics": {
      "apdex": {"value": 0.74, "baseline": 0.75, "delta": -0.01},
      "error_rate": {"value": 0.05, "baseline": 0.05, "delta": 0.00},
      "frozen_frame_rate": {"value": 0.06, "baseline": 0.05, "delta": 0.01},
      "volume": {"value": 3900, "baseline": 4000, "delta": -100}
    }
  }
]
```

**RCA Analysis:**

Segments are **completely independent**—different dimensions drive different issues:
- **iOS + cellular**: Latency problem (P95 +5.5s), network-related
- **iOS + wifi**: Normal
- **Android + v4.2.0**: UI jank problem (frozen frames +10%), app version issue
- **Android + v4.1.0**: Normal

**Pattern:** Two separate root causes:
1. **iOS cellular timeout** — Network infrastructure issue (likely API timeout on slow networks)
2. **Android v4.2.0 UI jank** — App regression in animation/rendering

**RCA Output:**

```json
{
  "version": 1,
  "executive_summary": "Checkout flow has two independent failure modes: (1) iOS users on cellular networks experience severe latency (P95 8.5s vs 3s baseline), affecting 5,200 sessions with 22% error rate; (2) Android app version 4.2.0 shows elevated UI jank (15% frozen frames), impacting 6,100 sessions with 12% error rate. Both require separate fixes targeting network resilience and rendering optimization.",
  "segments": [
    {
      "rank": 1,
      "title": "platform: ios, network: cellular",
      "insights": "Cellular network users on iOS experience critical latency: P95 increased from 3s to 8.5s (+5.5s delta), error rate jumped to 22%. APDEX fell to 0.50. Affects 5,200 sessions. The latency spike is exclusively cellular (WiFi users normal), indicating a timeout in API calls or payment gateway communication that's only triggered under slower networks. This is a infrastructure/backend performance issue, not an app code issue.",
      "affected_sessions": ["sess-015", "sess-016"],
      "metrics": [
        {
          "metric_id": "duration_p95",
          "metric_label": "P95 Latency",
          "value_display": "8,500ms",
          "baseline_display": "3,000ms",
          "delta_display": "+5,500ms",
          "value_number": 8500,
          "baseline_number": 3000
        },
        {
          "metric_id": "error_rate",
          "metric_label": "Error Rate",
          "value_display": "22%",
          "baseline_display": "5%",
          "delta_display": "+17%",
          "value_number": 0.22,
          "baseline_number": 0.05
        }
      ]
    },
    {
      "rank": 2,
      "title": "platform: android, app_version: 4.2.0",
      "insights": "Android app version 4.2.0 shows elevated UI jank (15% frozen frames, up from 5%), and higher error rate (12% vs 5%). Affects 6,100 sessions. Android 4.1.0 on same hardware is normal, confirming the regression is in 4.2.0's rendering/animation code. Likely an animation loop or layout invalidation issue introduced in this version.",
      "affected_sessions": ["sess-019", "sess-020"],
      "metrics": [
        {
          "metric_id": "frozen_frame_rate",
          "metric_label": "Frozen Frame Rate",
          "value_display": "15%",
          "baseline_display": "5%",
          "delta_display": "+10%",
          "value_number": 0.15,
          "baseline_number": 0.05
        }
      ]
    }
  ],
  "recommendations": [
    "iOS cellular: Increase payment API timeout from 10s to 20s and add exponential backoff retry (2x, 4x, 8s delays) for slow networks.",
    "iOS cellular: Consider progressive timeout based on network speed detection (use NWPathMonitor); use longer timeout for 2G/3G.",
    "Android v4.2.0: Profile animations on Pixel 4a (lower-end device) during checkout; check for layout invalidation loops in submit button animation.",
    "Android v4.2.0: Use Layout Inspector and Systrace to identify janky frames; likely a synchronous work on main thread during payment processing.",
    "Rollback Android v4.2.0 to v4.1.0 for non-critical UX; prepare hotfix with animation optimization and test with Monkey Runner under load.",
    "Post-fix: Monitor cellular vs WiFi and Android version splits independently in production dashboards."
  ]
}
```

---

### Segment Dependency Summary Table

| Pattern | Segment Characteristics | Root Cause Type | Example | RCA Approach |
|---------|----------------------|-----------------|---------|--------------|
| **Completely Dependent** | Single dimension varies, all have same degradation pattern | **Systemic** (one root cause affects all) | Android 12 OS incompatibility | Focus on that dimension; explain why others unaffected |
| **Semi-Dependent** | Multiple dimensions, overlapping issues with varying severity | **Version/Device bug** (specific combo matters) | App v4.2.1 + Samsung S21 | Identify common dimension (app version), explain device variation |
| **Independent** | Different dimensions drive different issues | **Multiple root causes** | iOS network timeout + Android UI jank | Rank by severity; explain each separately; different fixes |

---

## Architecture Strengths

✅ **Agentic reasoning** — LLM decides tool invocation order, not hardcoded pipeline  
✅ **Persona-driven** — Extensible to Product Analytics, Designer, Dependent personas  
✅ **Streaming first** — SSE for real-time agent execution feedback to frontend  
✅ **Multi-tenant** — `project_id` scoping via `session_scope_store`  
✅ **Schema enforcement** — Pydantic validates RCA agent outputs (prevents hallucination)  
✅ **Tool isolation** — Safe math (calculate tool), no shell/eval injection  
✅ **Session persistence** — Optional DB storage for multi-turn conversations  
✅ **Type safety** — Full type hints on all functions (Python 3.12+)  
✅ **Async-first** — FastAPI + httpx for non-blocking I/O  
✅ **Live development** — Volume-mounted source files (Docker)

---

## Roadmap (from README)

The planned architecture (per documentation) includes:

1. **Planner agent** — Intent recognition + persona selection
2. **Executor (loop)** — Iterate over selected personas, gather insights
3. **Summary agent** — Cross-persona synthesis
4. **Product Analytics persona** — Usage patterns, funnels, feature adoption
5. **Designer persona** — UX flows, interaction patterns, usability
6. **Dependent personas** — Compose insights from core three (Customer Success, Business Leaders)

This structure enables **composable, reusable personas** without touching core agent plumbing.

---

## Key Files Reference

| File | Purpose |
|------|---------|
| [`pulse_ai/agent.py`](./agent.py) | Root SequentialAgent pipeline definition |
| [`pulse_ai/constants.py`](./constants.py) | Configuration constants (model, timeouts, URLs) |
| [`pulse_ai/agents/em/agent.py`](./agents/em/agent.py) | Engineering Manager Agent |
| [`pulse_ai/agents/rca/agent.py`](./agents/rca/agent.py) | RCA Agent (schema-driven) |
| [`pulse_ai/agents/screen_rca/agent.py`](./agents/screen_rca/agent.py) | Screen RCA Agent |
| [`pulse_ai/server/app.py`](./server/app.py) | FastAPI app factory + runners |
| [`pulse_ai/server/routes.py`](./server/routes.py) | Endpoint handlers |
| [`pulse_ai/server/session_scope_store.py`](./server/session_scope_store.py) | Multi-tenant session tracking |
| [`pulse_ai/schemas/rca_structured_v1.py`](./schemas/rca_structured_v1.py) | RCA output schema |
| [`pulse_ai/schemas/screen_rca_narrative_v1.py`](./schemas/screen_rca_narrative_v1.py) | Screen RCA output schema |
| [`pulse_ai/README.md`](./README.md) | Quick start guide + architecture overview |

---

## Quick Start

### Prerequisites
- Python 3.12+
- Google API key from [AI Studio](https://aistudio.google.com/apikey)
- (Optional) Docker

### Start (Docker)
```bash
cd pulse_ai
cp .env.example .env
# Edit .env and set GOOGLE_API_KEY
./setup.sh
```

### Start (Local Python)
```bash
cd pulse_ai
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# Edit .env and set GOOGLE_API_KEY
adk web
```

Agent available at **http://localhost:8000**

---

## See Also

- [`README.md`](./README.md) — Quick start + overview
- [Google ADK Docs](https://google.github.io/adk-docs/)
- [Gemini API Docs](https://ai.google.dev/)
- [Pulse Backend Architecture](../backend/server/README.md)
- [Frontend UI (pulse-ui)](../pulse-ui/README.md)
