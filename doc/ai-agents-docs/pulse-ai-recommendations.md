# Pulse AI — Architecture Recommendations

*Concrete migration path from current sequential pipeline to an optimized multi-agent architecture.*

---

## Current Architecture

```
root_agent = SequentialAgent(
    name="pipeline",
    sub_agents=[planner_agent, executor_agent, summary_agent, report_agent],
)
```

| Property | Value |
|---|---|
| Pattern | Sequential Pipeline |
| LLM calls per query | 4 (always) |
| Latency | 4 sequential hops |
| Cost optimization | None (every agent runs always) |
| Quality | Adequate (all personas analyzed in single Executor call) |

### Known Issues

1. **Wasted LLM calls on unclear queries** — "hello" still runs all 4 agents
2. **Context dilution** — Executor analyzes all personas in one prompt, reducing depth
3. **No cost savings for simple queries** — 1-persona query costs same as 5-persona
4. **Serial latency** — each agent waits for the previous to complete

---

## Recommended Migration Path

### Phase 1: Structured Output + Callback Gating (Low effort, immediate savings)

**Goal**: Stop wasting LLM calls on unclear or simple queries. Use structured JSON output from the Planner for reliable, deterministic routing.

**Changes required**:

1. Define a `PlanOutput` Pydantic schema for the Planner's response
2. Add `output_schema=PlanOutput` to the Planner agent
3. Add `after_agent_callback` on Planner to parse JSON and set routing flags
4. Add `before_agent_callback` on Executor and Summary to check flags

**Architecture**:

```
Planner (structured JSON) → [gate] Executor → [gate] Summary → Report
    │                          ↑                   ↑
    │ output_schema=PlanOutput │                   │
    └─→ after_callback ───────►│                   │
         parses JSON,      pure Python check   pure Python check
         sets state flags
```

**Implementation**:

```python
# pulse_ai/agents/schemas.py

from pydantic import BaseModel

class PlanOutput(BaseModel):
    intent_clear: bool
    query_understanding: str
    selected_personas: list[str]   # e.g. ["Product Analytics", "Engineering Manager"]
    analysis_focus: str
    clarification_needed: str | None = None
```

```python
# pulse_ai/agents/callbacks.py

import json
from google.genai import types

CORE_PERSONAS = {"Product Analytics", "Engineering Manager", "Designer"}
DEPENDENT_PERSONAS = {"Customer Success", "Business Leaders"}


async def set_routing_flags(callback_context):
    """Parse the Planner's structured JSON output and set routing flags."""
    raw = callback_context.state.get("plan", "{}")
    try:
        plan = json.loads(raw) if isinstance(raw, str) else raw
    except (json.JSONDecodeError, TypeError):
        plan = {}

    selected = set(plan.get("selected_personas", []))

    callback_context.state["intent_clear"] = plan.get("intent_clear", True)
    callback_context.state["selected_personas"] = list(selected)
    callback_context.state["needs_executor"] = bool(selected & CORE_PERSONAS)
    callback_context.state["needs_summary"] = bool(selected & DEPENDENT_PERSONAS)
    callback_context.state["clarification"] = plan.get("clarification_needed")
    return None


async def gate_on_clear_intent(callback_context):
    """Skip this agent if the Planner flagged intent as unclear."""
    if not callback_context.state.get("intent_clear", True):
        msg = callback_context.state.get(
            "clarification", "Could you clarify your question?"
        )
        return types.Content(
            role="model",
            parts=[types.Part(text=msg)]
        )
    return None


async def gate_summary(callback_context):
    """Skip Summary if no dependent personas were selected."""
    if not callback_context.state.get("needs_summary", True):
        return types.Content(
            role="model",
            parts=[types.Part(text="Summary skipped — no dependent personas selected.")]
        )
    return None
```

```python
# pulse_ai/agents/planner/agent.py

from pulse_ai.agents.callbacks import set_routing_flags
from pulse_ai.agents.schemas import PlanOutput

planner_agent = LlmAgent(
    model=AGENT_MODEL,
    name=PLANNER_AGENT_NAME,
    description="Understands user intent and selects relevant analysis personas.",
    instruction=PLANNER_INSTRUCTION,
    output_key="plan",
    output_schema=PlanOutput,
    after_agent_callback=set_routing_flags,
)
```

```python
# pulse_ai/agents/executor/agent.py

from pulse_ai.agents.callbacks import gate_on_clear_intent

executor_agent = LlmAgent(
    model=AGENT_MODEL,
    name=EXECUTOR_AGENT_NAME,
    description="Iterates over selected personas and produces detailed analysis.",
    instruction=EXECUTOR_INSTRUCTION,
    output_key="execution_results",
    before_agent_callback=gate_on_clear_intent,
)
```

**Example Planner outputs**:

Normal query (`"Show me crash trends for v3.2"`):

```json
{
  "intent_clear": true,
  "query_understanding": "User wants crash rate trends for app version 3.2",
  "selected_personas": ["Engineering Manager", "Product Analytics"],
  "analysis_focus": "Crash rates, error trends, version comparison",
  "clarification_needed": null
}
```

Unclear query (`"hello"`):

```json
{
  "intent_clear": false,
  "query_understanding": "Greeting with no analytical intent",
  "selected_personas": [],
  "analysis_focus": "",
  "clarification_needed": "Hi! I can help you analyze your app's performance, user behavior, and UX. What would you like to know?"
}
```

**Impact**:

| Metric | Before | After |
|---|---|---|
| Unclear intent cost | 4 calls | **2 calls** |
| Normal query cost | 4 calls | 4 calls (no change) |
| Routing reliability | — | **High** (structured JSON, no string matching) |
| Code changes | — | 2 new files (`schemas.py`, `callbacks.py`) + 2 agent edits |
| Risk | — | Very low |

> **Note**: `output_schema` disables tool use on the Planner. This is fine since the Planner only reasons about intent and personas — it doesn't need tools.

---

### Phase 2: Split Executor into Per-Persona Agents (Medium effort, quality boost)

**Goal**: Improve analysis quality by giving each persona a focused context window.

**Changes required**:

1. Create individual `LlmAgent` for each persona (Product Analytics, Engineering, Designer, Customer Success, Business Leaders)
2. Replace single Executor with `ParallelAgent` wrapping persona agents
3. Add `before_agent_callback` on each persona agent for selective execution
4. Update Summary agent to read multiple persona outputs

**Architecture**:

```
Planner → ParallelAgent [          ← simultaneous execution
              product_analytics,       output_key="product_result"
              engineering_manager,     output_key="engineering_result"
              designer,                output_key="designer_result"
          ]
        → Summary                  ← reads {product_result}, {engineering_result}, {designer_result}
        → Report
```

**New directory structure**:

```
pulse_ai/agents/
├── planner/
├── personas/              ← NEW
│   ├── __init__.py
│   ├── product_analytics.py
│   ├── engineering_manager.py
│   ├── designer.py
│   ├── customer_success.py
│   └── business_leaders.py
├── summary/
├── report/
└── callbacks.py           ← from Phase 1
```

**Impact**:

| Metric | Before (Phase 1) | After (Phase 2) |
|---|---|---|
| Normal query (3 personas) | 4 calls, serial | 6 calls, **but parallel** |
| Latency (3 personas) | 4 hops | **4 hops** (personas run simultaneously) |
| Per-persona quality | Diluted | **Focused, deeper analysis** |
| 1-persona query | 4 calls | **4 calls** (2 skipped via callbacks) |
| Code changes | — | ~10 new files |

---

### Phase 3: Custom Agent for Full Control (Higher effort, maximum optimization)

**Goal**: Centralized Python orchestration for complex routing scenarios.

**When to do this**: Only if Phase 1-2 don't provide enough flexibility, e.g.:
- Dynamic agent ordering based on query type
- Conditional dependent-persona execution (only after core personas complete)
- Complex retry logic
- A/B testing different pipeline configurations

**Architecture**:

Since the Planner outputs structured JSON (from Phase 1), the Custom Agent can parse it directly:

```python
import json

PERSONA_AGENT_MAP = {
    "Product Analytics": "product_analytics",
    "Engineering Manager": "engineering_manager",
    "Designer": "designer",
    "Customer Success": "customer_success",
    "Business Leaders": "business_leaders",
}

CORE_PERSONAS = {"Product Analytics", "Engineering Manager", "Designer"}

class PulseAIPipeline(BaseAgent):
    async def _run_async_impl(self, ctx):
        # 1. Run planner (outputs structured JSON via output_schema)
        async for event in self.find_sub_agent("planner").run_async(ctx):
            yield event

        raw = ctx.session.state.get("plan", "{}")
        try:
            plan = json.loads(raw) if isinstance(raw, str) else raw
        except (json.JSONDecodeError, TypeError):
            plan = {}

        # 2. Check intent — deterministic, no LLM call
        if not plan.get("intent_clear", True):
            ctx.session.state["clarification"] = plan.get("clarification_needed", "")
            async for event in self.find_sub_agent("report").run_async(ctx):
                yield event
            return

        # 3. Run only selected persona agents
        selected = set(plan.get("selected_personas", []))
        for persona_name, agent_name in PERSONA_AGENT_MAP.items():
            if persona_name in selected:
                agent = self.find_sub_agent(agent_name)
                if agent:
                    async for event in agent.run_async(ctx):
                        yield event

        # 4. Summary (only if dependent personas were selected)
        if selected & {"Customer Success", "Business Leaders"}:
            async for event in self.find_sub_agent("summary").run_async(ctx):
                yield event

        # 5. Report
        async for event in self.find_sub_agent("report").run_async(ctx):
            yield event
```

**Impact**:

| Metric | Before (Phase 2) | After (Phase 3) |
|---|---|---|
| Routing flexibility | Callback-based (per-agent) | **Centralized Python logic** |
| Testability | Test each callback separately | **Unit test orchestration as a whole** |
| Code complexity | Low-Medium | **High** |
| Maintenance | Framework handles orchestration | **You own the orchestration** |

---

## Summary: Migration Phases

| Phase | Effort | Risk | Cost Savings | Quality Improvement | Recommended |
|---|---|---|---|---|---|
| **Phase 1**: Structured JSON + Callbacks | 1-2 hours | Very low | 10-50% on edge cases | Reliable routing | **Yes, do this first** |
| **Phase 2**: Parallel personas | 1-2 days | Low-Medium | Neutral (more calls but parallel) | **Significant** | Yes, when quality matters |
| **Phase 3**: Custom Agent | 2-3 days | Medium | Optimal | **Significant** | Only if Phase 2 isn't enough |

> **Key principle**: All three phases build on the same foundation — the Planner's structured JSON output. Phase 1 introduces the schema + callbacks. Phase 2 uses the `selected_personas` array to gate parallel agents. Phase 3 reads the same JSON in a centralized Custom Agent.

---

## Decision Checklist

Before moving to the next phase, ask:

- [ ] Are we wasting LLM calls on trivial queries? → **Phase 1**
- [ ] Is per-persona analysis depth insufficient? → **Phase 2**
- [ ] Do we need routing logic that callbacks can't handle? → **Phase 3**
- [ ] Is latency a problem? → **Phase 2** (parallel execution)
- [ ] Is cost the primary concern? → **Phase 1** (minimal overhead)

---

## Sources

- [Google ADK Documentation](https://google.github.io/adk-docs/)
- [Developer's Guide to Multi-Agent Patterns](https://developers.googleblog.com/developers-guide-to-multi-agent-patterns-in-adk/)
- [ADK Callback Design Patterns](https://google.github.io/adk-docs/callbacks/design-patterns-and-best-practices/)
- [The ADK Guide: Tips, Tricks and Patterns](https://office.qz.com/the-adk-survival-guide-tips-tricks-and-patterns-for-production-agents-abdc73d99588) (Ben Mizrahi, Google Cloud Community)
