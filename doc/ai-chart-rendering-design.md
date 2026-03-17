# AI Chat Chart Rendering — Design Document

## Overview

Enable the Pulse AI chat to render **interactive charts** inline in conversations. When a user asks an analytics question (e.g., "Show me error rate trend for the last 7 days"), the AI agent queries ClickHouse, generates an ECharts-compatible chart configuration, and the frontend renders it as a fully interactive chart (hover, zoom, legend toggle).

## Decision

**Approach:** ADK Artifacts + existing ECharts components (Pattern B — Structured Config → Frontend Renders)

**Rejected alternatives:**

| Approach | Why Rejected |
|---|---|
| Vega-Lite + vega-embed | Adds ~300KB dependency, charts look inconsistent with rest of Pulse |
| Streamlit | Requires abandoning the custom React frontend |
| Code execution sandbox | Heavy infra (Python sandbox), slower, security complexity, often produces static Matplotlib images |
| Server-side image rendering | Not interactive — no hover, zoom, or click |
| Inline JSON in LLM text + regex parsing | Fragile — LLM produces malformed JSON ~30% of the time |

**References:**
- [ADK Artifacts + ECharts — Davide Consonni, Jan 2026](https://medium.com/@dconsonni/drawing-charts-in-your-ai-agent-frontend-with-google-adk-9c74a4a98931)
- [Google ADK Artifacts docs](https://irbox.github.io/adk-docs/artifacts/)
- [Google Conversational Analytics API uses Vega-Lite — validates structured config pattern](https://docs.cloud.google.com/gemini/docs/conversational-analytics-api/render-visualization)

---

## Current State (main branch)

### What exists

| Component | Status | Location |
|---|---|---|
| **AI agent (pulse_ai/)** | Does NOT exist on main | `pulse_ai/` is untracked |
| **AiChat screen** | Does NOT exist on main | `pulse-ui/src/screens/AiChat/` is untracked |
| **Chat store (Zustand)** | Does NOT exist on main | `pulse-ui/src/stores/useChatStore.ts` is untracked |
| **Chat types** | Does NOT exist on main | `pulse-ui/src/types/chat.ts` is untracked |
| **Session creation hook** | Exists on main | `pulse-ui/src/hooks/useCreateUserAiSession/` |
| **SSE response hook** | Exists on main | `pulse-ui/src/hooks/useGetPulseAiResponse/` |
| **ECharts components** | Exist on main | `pulse-ui/src/components/Charts/` |
| **Backend AI proxy** | Exists on main | Routes at `/pulse-ai/session` and `/pulse-ai/user-query` |

### API routes on main

```
POST /pulse-ai/session          → Creates an AI session (proxied through pulse-server)
POST /pulse-ai/user-query       → Sends query, returns SSE stream (proxied through pulse-server)
```

### SSE response format on main

The `useGetPulseAiResponse` hook on main uses TanStack Query `useMutation`, reads an SSE stream, and resolves with:

```typescript
{
  data: {
    event: "complete" | "keepalive" | "done" | "error";
    data: {
      text: string;              // LLM response text
      status: string | null;
      function_call: string | null;
      function_response: string | null;
    };
  };
  error: null;
  status: 200;
}
```

### ECharts components on main

All chart wrappers exist and are production-ready:

| Component | Props Interface | Key Props |
|---|---|---|
| `LineChart` | `LineChartProps` | `option`, `height`, `zoom`, `withLegend`, `syncTooltips`, `group` |
| `BarChart` | `BarChartProps` | `option`, `height`, `withLegend` |
| `PieChart` | `PieChartProps` | `option`, `height`, `withLegend` |
| `AreaChart` | `AreaChartProps` | `option`, `height`, `zoom`, `withLegend`, `syncTooltips`, `group` |
| `SparklineChart` | `SparklineChartProps` | `option`, `height` |

All extend `EChartsReactProps` from `echarts-for-react`. The `option` prop accepts a standard ECharts option object.

---

## Architecture

```
User asks question
    → Frontend sends query via SSE
    → Backend proxies to AI agent
    → AI agent:
        1. Calls data query tool → gets data from ClickHouse
        2. Calls create_chart tool → saves ECharts config as ADK Artifact
        3. Returns text response via SSE
    → API loads artifacts after execution
    → API returns { text, charts[] } to frontend
    → Frontend renders text + interactive chart(s)
```

### Data Flow

```
┌─────────────┐     POST /pulse-ai/user-query      ┌──────────────┐
│   pulse-ui   │ ──────────────────────────────────► │ pulse-server │
│  (React)     │                                     │  (proxy)     │
└──────┬───────┘                                     └──────┬───────┘
       │                                                    │
       │  ◄── SSE: { text, charts[] }                       │  POST /run_sse
       │                                                    ▼
       │                                             ┌──────────────┐
       │                                             │   pulse-ai   │
       │                                             │  (ADK Agent) │
       │                                             └──┬───────┬───┘
       │                                                │       │
       │                              query_data tool   │       │  create_chart tool
       │                                                ▼       ▼
       │                                          ┌─────────┐ ┌───────────┐
       │                                          │ClickHouse│ │ Artifact  │
       │                                          │          │ │  Store    │
       │                                          └──────────┘ └───────────┘
       │
       ▼
  ┌──────────────────────────────────────────┐
  │ ChatMessage                               │
  │  ├── ReactMarkdown (text)                 │
  │  ├── SqlResultCard (if SQL present)       │
  │  └── AiChartCard[] (if charts present)    │
  │       └── LineChart / BarChart / PieChart  │
  │           (existing ECharts components)    │
  └──────────────────────────────────────────┘
```

---

## Implementation Plan

### Phase 1: AI Agent — `create_chart` tool

**File:** `pulse_ai/pulse_agent/tools/create_chart.py` (new)

```python
from google.adk.tools import ToolContext
from google.genai import types
import json

async def create_chart(
    chart_type: str,
    title: str,
    data: dict,
    description: str = None,
    tool_context: ToolContext = None
) -> dict:
    """Create a visual chart.

    Args:
        chart_type: One of "line", "bar", "pie", "area", "gauge", "scatter"
        title: Chart title
        data: ECharts-compatible option object. Exact schemas:
            - LINE/BAR/AREA:
              {"xAxis": {"type": "category", "data": ["Mon", "Tue"]},
               "yAxis": {"type": "value"},
               "series": [{"name": "Errors", "data": [10, 20]}]}
            - PIE:
              {"series": [{"type": "pie",
                           "data": [{"name": "A", "value": 10},
                                    {"name": "B", "value": 20}]}]}
        description: Optional text description of the chart
    """
    chart_config = {
        "type": chart_type,
        "title": title,
        "data": data,
        "description": description,
    }

    if tool_context:
        chart_json = json.dumps(chart_config).encode("utf-8")
        safe_title = "".join(
            c if c.isalnum() or c in (" ", "_", "-") else "_"
            for c in title
        )
        chart_part = types.Part(
            inline_data=types.Blob(
                mime_type="application/json",
                data=chart_json,
            )
        )
        await tool_context.save_artifact(
            filename=f"chart_{safe_title[:50]}.json",
            artifact=chart_part,
        )

    return {"success": True, "message": f"Chart '{title}' created"}
```

**Register in agent:**

```python
from tools.create_chart import create_chart

agent = Agent(
    model="gemini-2.5-flash",
    name="pulse_agent",
    tools=[..., create_chart],  # ADK auto-wraps
)
```

### Phase 2: API — Load artifacts and return charts

**File:** API endpoint that runs the agent (new or modified)

After agent execution completes, load chart artifacts and include them in the response:

```python
artifact_keys = await artifact_service.list_artifact_keys(
    app_name="pulse_agent",
    user_id=user_id,
    session_id=session_id,
)

charts = []
for key in artifact_keys:
    if key.startswith("chart_"):
        artifact = await artifact_service.load_artifact(
            app_name="pulse_agent",
            user_id=user_id,
            session_id=session_id,
            filename=key,
        )
        chart_data = json.loads(artifact.inline_data.data)
        charts.append(chart_data)

        # Delete after loading to prevent accumulation
        await artifact_service.delete_artifact(
            app_name="pulse_agent",
            user_id=user_id,
            session_id=session_id,
            filename=key,
        )
```

**Modified SSE response format:**

The final SSE event should include a `charts` field:

```json
{
  "content": {
    "parts": [{ "text": "Error rate spiked on Thursday..." }],
    "role": "model"
  },
  "charts": [
    {
      "type": "line",
      "title": "Error Rate Trend",
      "data": {
        "xAxis": { "type": "category", "data": ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"] },
        "yAxis": { "type": "value" },
        "series": [{ "name": "Error Rate %", "data": [2.1, 3.4, 1.8, 5.2, 4.1, 2.0, 1.5] }]
      },
      "description": "7-day error rate trend showing Thursday spike"
    }
  ]
}
```

### Phase 3: Backend Proxy — Pass charts through

**File:** pulse-server AI proxy endpoint

The backend proxy that forwards requests to `pulse-ai` needs to pass the `charts` array through to the frontend without modification. If the proxy currently only forwards `text`, it needs to also forward `charts`.

### Phase 4: Frontend — Types

**File:** `pulse-ui/src/types/chart.ts` (new) or extend `pulse-ui/src/types/chat.ts`

```typescript
export interface AiChartConfig {
  type: "line" | "bar" | "pie" | "area" | "gauge" | "scatter";
  title: string;
  data: Record<string, unknown>; // ECharts option object
  description?: string;
}
```

Extend `ChatMessage` (or the response type) to include charts:

```typescript
export interface ChatMessage {
  id: string;
  role: "user" | "model";
  text: string;
  sql?: string;
  charts?: AiChartConfig[];   // ← new
  timestamp: number;
  isStreaming?: boolean;
}
```

### Phase 5: Frontend — `AiChartCard` component

**File:** `pulse-ui/src/screens/AiChat/components/AiChartCard/AiChartCard.tsx` (new)

```typescript
import { Box, Text } from "@mantine/core";
import { LineChart, BarChart, PieChart, AreaChart } from "../../../../components/Charts";
import { AiChartConfig } from "../../../../types/chat";
import classes from "./AiChartCard.module.css";

interface AiChartCardProps {
  chart: AiChartConfig;
}

export const AiChartCard = ({ chart }: AiChartCardProps) => {
  const option = {
    title: { text: chart.title, textStyle: { fontSize: 14 } },
    tooltip: { trigger: "axis" },
    ...chart.data,
  };

  const height = 300;

  const renderChart = () => {
    switch (chart.type) {
      case "line":
        return <LineChart option={option} height={height} />;
      case "bar":
        return <BarChart option={option} height={height} />;
      case "pie":
        return <PieChart option={option} height={height} />;
      case "area":
        return <AreaChart option={option} height={height} />;
      default:
        return <LineChart option={option} height={height} />;
    }
  };

  return (
    <Box className={classes.container}>
      {renderChart()}
      {chart.description && (
        <Text size="xs" c="dimmed" mt={4}>{chart.description}</Text>
      )}
    </Box>
  );
};
```

**File:** `pulse-ui/src/screens/AiChat/components/AiChartCard/AiChartCard.module.css` (new)

```css
.container {
  margin-top: var(--mantine-spacing-sm);
  padding: var(--mantine-spacing-xs);
  border: 1px solid var(--mantine-color-gray-3);
  border-radius: var(--mantine-radius-md);
  background: var(--mantine-color-white);
}
```

### Phase 6: Frontend — Update ChatMessage to render charts

**File:** `pulse-ui/src/screens/AiChat/components/ChatMessage/ChatMessage.tsx` (modify)

Add chart rendering after the existing SQL card:

```typescript
import { AiChartCard } from "../AiChartCard";

// Inside the component, after SqlResultCard:
{message.charts?.map((chart, index) => (
  <AiChartCard key={index} chart={chart} />
))}
```

### Phase 7: Frontend — Update SSE hook to parse charts

**File:** `pulse-ui/src/hooks/useGetPulseAiResponse/` (modify)

The SSE hook needs to extract `charts` from the response and pass them to the store. Two options depending on implementation:

**Option A (if using the main branch mutation pattern):**
The `PulseAiResponseData` type already has room — add `charts` to it and parse from the JSON response.

**Option B (if using the uncommitted streaming pattern):**
Parse the `charts` field from the final SSE event when `[DONE]` or `[COMPLETE]` arrives.

---

## File Changes Summary

| File | Action | Description |
|---|---|---|
| `pulse_ai/pulse_agent/tools/create_chart.py` | **New** | ADK tool that saves chart config as artifact |
| `pulse_ai/pulse_agent/agent.py` | **Modify** | Register `create_chart` in agent tools |
| `pulse_ai/` API endpoint | **Modify** | Load chart artifacts after execution, include in response |
| Backend AI proxy | **Modify** | Pass `charts[]` through to frontend |
| `pulse-ui/src/types/chat.ts` | **Modify** | Add `AiChartConfig` type, add `charts?` to `ChatMessage` |
| `pulse-ui/src/screens/AiChat/components/AiChartCard/` | **New** | Component that maps chart config → ECharts component |
| `pulse-ui/src/screens/AiChat/components/ChatMessage/` | **Modify** | Render `AiChartCard` when charts present |
| `pulse-ui/src/hooks/useGetPulseAiResponse/` | **Modify** | Parse `charts` from response |

**New files:** 3 (tool, component, CSS)
**Modified files:** 5
**New dependencies:** 0

---

## Chart Type to ECharts Component Mapping

| Agent `chart_type` | Frontend Component | ECharts Series Type |
|---|---|---|
| `"line"` | `<LineChart>` | `type: "line"` |
| `"bar"` | `<BarChart>` | `type: "bar"` |
| `"pie"` | `<PieChart>` | `type: "pie"` |
| `"area"` | `<AreaChart>` | `type: "line"` with `areaStyle` |
| `"gauge"` | `<LineChart>` (fallback) | `type: "gauge"` |
| `"scatter"` | `<LineChart>` (fallback) | `type: "scatter"` |

---

## Streaming Behavior

Charts do **not** stream incrementally. The flow is:

1. SSE streams **text tokens** progressively (user sees text appear word by word)
2. The `create_chart` tool runs during agent execution — the artifact is saved server-side
3. After agent finishes, the API loads artifacts and sends them in the **final SSE event**
4. Frontend renders charts only when the complete config arrives

This means during streaming, the user sees text building up. Once streaming completes, charts appear below the text. This is the expected UX — you don't want a half-rendered chart.

---

## Artifact Lifecycle

```
create_chart() called     → Artifact saved to ADK Artifact Store
                            (scoped to app_name + user_id + session_id)
API loads artifacts       → Chart JSON extracted
API deletes artifacts     → Prevents accumulation across turns
Frontend receives charts  → Renders with ECharts
```

Without deletion, artifacts accumulate: turn 3 would return charts from turns 1, 2, and 3. Deleting after loading ensures each response only contains charts from that turn.

---

## Testing Plan

### AI Agent
- [ ] Verify `create_chart` tool saves artifact with correct JSON structure
- [ ] Verify agent calls `create_chart` when asked for a visualization
- [ ] Verify artifact is deleted after loading
- [ ] Verify multiple charts in a single response work

### Backend Proxy
- [ ] Verify `charts[]` array passes through proxy unchanged
- [ ] Verify response works when `charts` is empty or absent

### Frontend
- [ ] `AiChartCard` renders LineChart for `type: "line"`
- [ ] `AiChartCard` renders BarChart for `type: "bar"`
- [ ] `AiChartCard` renders PieChart for `type: "pie"`
- [ ] `AiChartCard` renders AreaChart for `type: "area"`
- [ ] `AiChartCard` falls back to LineChart for unknown types
- [ ] Charts appear after streaming completes, not during
- [ ] Charts are interactive (hover tooltip, zoom, legend toggle)
- [ ] Multiple charts render in a single message
- [ ] Messages without charts still render correctly
- [ ] Chart card styling matches the chat UI

### Integration
- [ ] End-to-end: "Show me error rate trend" → see interactive line chart
- [ ] End-to-end: "Compare platform distribution" → see interactive pie chart
- [ ] Verify charts don't accumulate across conversation turns
