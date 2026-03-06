# Callback Conditional Skipping

*Deterministic agent gating via `before_agent_callback`*

---

## Overview

This pattern adds **deterministic, zero-cost gating** to any agent in a pipeline. A pure Python callback runs before each agent and decides whether to proceed or skip — no LLM call needed for the decision.

This is an officially documented ADK pattern: **"Conditional Skipping of Steps"** from the [ADK Callback Design Patterns](https://google.github.io/adk-docs/callbacks/design-patterns-and-best-practices/).

```
Agent A runs (sets flags in state)
    │
    ▼
before_agent_callback(B)  ← pure Python, 0 LLM calls
    │
    ├─ returns None     → Agent B runs normally (1 LLM call)
    └─ returns Content  → Agent B is SKIPPED (0 LLM calls)
```

---

## How It Works in ADK

### The Mechanism

From `BaseAgent._handle_before_agent_callback` (source: `base_agent.py`):

```python
if before_agent_callback_content:
    ret_event = Event(
        invocation_id=ctx.invocation_id,
        author=self.name,
        content=before_agent_callback_content,
        actions=callback_context._event_actions,
    )
    ctx.end_invocation = True   # skip this agent
    return ret_event
```

When the callback returns `Content`:
- The agent's LLM call is **completely skipped**
- The returned content becomes the agent's "output"
- The `output_key` is populated with the returned content
- The pipeline continues to the next agent

When the callback returns `None`:
- The agent runs normally

### Callback Signature

```python
from google.adk.agents.callback_context import CallbackContext
from google.genai import types

async def my_callback(callback_context: CallbackContext) -> types.Content | None:
    # Access session state
    value = callback_context.state.get("some_key")
    
    if should_skip:
        return types.Content(
            role="model",
            parts=[types.Part(text="Skipped.")]
        )
    return None  # proceed normally
```

---

## Implementation Patterns

### Pattern 1: Structured JSON Output (Recommended)

The most reliable approach. Use ADK's `output_schema` to force the Planner to return structured JSON, then parse it deterministically in callbacks.

**Why this is recommended over string matching**:

| Aspect | String matching (`"Engineering" in plan`) | Structured JSON (`plan["selected_personas"]`) |
|---|---|---|
| Reliability | Fragile (LLM might phrase differently) | **Guaranteed schema** (ADK enforces it) |
| Parsing | Regex/substring hacks | **`json.loads()` + typed dict access** |
| Extensibility | Add more `if` checks | **Add fields to the Pydantic model** |
| Downstream use | Agents parse free text | **Agents get structured data** |
| Testability | Hard to mock | **Easy to mock with JSON fixtures** |

> **Caveat**: When `output_schema` is set, the agent **cannot use tools** (function tools, RAGs, agent transfer, etc.). This is fine for the Planner since it only reasons and produces a plan.

#### Step 1: Define the Pydantic schema

```python
from pydantic import BaseModel

class PlanOutput(BaseModel):
    intent_clear: bool
    query_understanding: str
    selected_personas: list[str]   # e.g. ["Product Analytics", "Engineering Manager"]
    analysis_focus: str
    clarification_needed: str | None = None  # populated when intent_clear=False
```

#### Step 2: Assign schema to the Planner

```python
planner_agent = LlmAgent(
    name="planner",
    instruction=PLANNER_INSTRUCTION,
    output_key="plan",
    output_schema=PlanOutput,    # forces structured JSON output
)
```

ADK forces the LLM to return valid JSON matching the schema. The raw JSON string is stored in `session.state["plan"]`.

#### Step 3: Parse JSON in the `after_agent_callback`

```python
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
```

#### Step 4: Gate downstream agents using parsed flags

```python
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


async def gate_executor(callback_context):
    """Skip Executor if no core personas were selected."""
    if not callback_context.state.get("needs_executor", True):
        return types.Content(
            role="model",
            parts=[types.Part(text="Executor skipped — no core personas selected.")]
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

#### Step 5: Wire it up

```python
planner_agent = LlmAgent(
    name="planner",
    instruction=PLANNER_INSTRUCTION,
    output_key="plan",
    output_schema=PlanOutput,
    after_agent_callback=set_routing_flags,
)

executor_agent = LlmAgent(
    name="executor",
    instruction=EXECUTOR_INSTRUCTION,
    output_key="execution_results",
    before_agent_callback=gate_on_clear_intent,
)

summary_agent = LlmAgent(
    name="summary",
    instruction=SUMMARY_INSTRUCTION,
    output_key="summary",
    before_agent_callback=gate_summary,
)
```

#### Example: What the Planner outputs

**Normal query** (`"Show me crash trends for v3.2"`):

```json
{
  "intent_clear": true,
  "query_understanding": "User wants crash rate trends for app version 3.2",
  "selected_personas": ["Engineering Manager", "Product Analytics"],
  "analysis_focus": "Crash rates, error trends, version comparison",
  "clarification_needed": null
}
```

Routing flags: `intent_clear=True`, `needs_executor=True`, `needs_summary=False`

**Unclear query** (`"hello"`):

```json
{
  "intent_clear": false,
  "query_understanding": "Greeting with no analytical intent",
  "selected_personas": [],
  "analysis_focus": "",
  "clarification_needed": "Hi! I can help you analyze your app's performance, user behavior, and UX. What would you like to know?"
}
```

Routing flags: `intent_clear=False` → Executor and Summary both skipped.

---

### Pattern 2: Simple Flag-Based Gating (Lightweight alternative)

When you don't want to use `output_schema` (e.g., the Planner needs tools), you can fall back to string matching on free-text output. This is less reliable but requires no schema changes.

```python
async def set_routing_flags(callback_context):
    plan = callback_context.state.get("plan", "")

    unclear_signals = ["UNCLEAR", "cannot determine", "need more information"]
    callback_context.state["intent_clear"] = not any(
        s.lower() in plan.lower() for s in unclear_signals
    )
    callback_context.state["needs_executor"] = callback_context.state["intent_clear"]
    callback_context.state["needs_summary"] = (
        callback_context.state["intent_clear"]
        and ("Customer Success" in plan or "Business Leaders" in plan)
    )
    return None

async def gate_executor(callback_context):
    if not callback_context.state.get("needs_executor", True):
        return types.Content(
            role="model",
            parts=[types.Part(text="Executor skipped — no personas selected.")]
        )
    return None

planner_agent = LlmAgent(
    name="planner",
    instruction=PLANNER_INSTRUCTION,
    output_key="plan",
    after_agent_callback=set_routing_flags,
)

executor_agent = LlmAgent(
    name="executor",
    instruction=EXECUTOR_INSTRUCTION,
    output_key="execution_results",
    before_agent_callback=gate_executor,
)
```

> **When to use**: Only when the Planner needs tools (since `output_schema` disables tools). Prefer Pattern 1 in all other cases.

---

## ADK Best Practices for Callbacks

From the [official ADK documentation](https://google.github.io/adk-docs/callbacks/design-patterns-and-best-practices/):

1. **Keep Focused** — each callback should have a single, well-defined purpose
2. **Mind Performance** — callbacks execute synchronously; avoid blocking operations
3. **Handle Errors Gracefully** — use `try/except` blocks; don't let callback errors crash the pipeline
4. **Manage State Carefully** — use specific state keys to avoid unintended side effects
5. **Consider Idempotency** — design callbacks to be safe to run multiple times
6. **Test Thoroughly** — unit test callbacks with mock context objects

---

## Cost Impact

| Scenario | Without Callbacks | With Callbacks |
|---|---|---|
| Normal query (all agents needed) | 4 LLM calls | 4 LLM calls (callbacks return None) |
| Unclear intent | 4 LLM calls | **1-2 LLM calls** (downstream skipped) |
| Simple query (skip optional agents) | 4 LLM calls | **2-3 LLM calls** |

**The callback itself costs zero** — it's pure Python, no LLM involved. It only saves cost by preventing unnecessary LLM calls downstream.

---

## When to Use

| Use When | Avoid When |
|---|---|
| Skip logic is deterministic (string matching, flags, JSON parsing) | Routing requires LLM-level reasoning |
| You want zero overhead for the gating decision | The skip condition is too complex for Python logic |
| Working within a SequentialAgent or ParallelAgent | You need dynamic, multi-hop routing |
| Cost optimization is important | |

---

## Related Patterns

- [Sequential Pipeline](./sequential-pipeline-pattern.md) — the base pattern this enhances
- [Parallel Fan-Out](./parallel-fan-out-pattern.md) — combine with gating for selective parallel execution
- [Coordinator / Dispatcher](./coordinator-dispatcher-pattern.md) — when routing needs LLM reasoning
