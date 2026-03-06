# Parallel Fan-Out / Gather Pattern

*aka "The Octopus"*

---

## Overview

Multiple agents execute tasks **simultaneously**, and their outputs are aggregated by a final synthesizer agent. This reduces latency for independent tasks and improves quality by giving each agent a focused context window.

```
                ┌──────────┐
                │  Planner │
                └────┬─────┘
                     │
        ┌────────────┼────────────┐         Fan-Out
        ▼            ▼            ▼
   ┌─────────┐ ┌─────────┐ ┌─────────┐
   │ Agent A │ │ Agent B │ │ Agent C │     (parallel)
   └────┬────┘ └────┬────┘ └────┬────┘
        │            │            │
        └────────────┼────────────┘         Gather
                     ▼
              ┌─────────────┐
              │ Synthesizer │
              └─────────────┘
```

---

## How It Works in ADK

ADK provides the `ParallelAgent` primitive that runs all sub-agents concurrently. Key rules:

1. All sub-agents share `session.state` — use **unique `output_key`** per agent to avoid race conditions
2. `ParallelAgent` itself makes zero LLM calls — it's pure orchestration
3. Combine with `SequentialAgent` to create fan-out → gather workflows

---

## Implementation

```python
from google.adk.agents.llm_agent import LlmAgent
from google.adk.agents.parallel_agent import ParallelAgent
from google.adk.agents.sequential_agent import SequentialAgent

# --- Fan-out: Three parallel workers ---

product_agent = LlmAgent(
    name="product_analytics",
    instruction="""\
Analyze from the Product Analytics perspective using the plan below.
Focus on: usage patterns, funnels, feature adoption, conversion rates.

{plan}
""",
    output_key="product_analytics_result",
)

engineering_agent = LlmAgent(
    name="engineering_manager",
    instruction="""\
Analyze from the Engineering Manager perspective using the plan below.
Focus on: performance, errors, crash rates, latency, ANRs, API health.

{plan}
""",
    output_key="engineering_result",
)

designer_agent = LlmAgent(
    name="designer",
    instruction="""\
Analyze from the Designer perspective using the plan below.
Focus on: UX flows, interaction patterns, screen load times, user journey friction.

{plan}
""",
    output_key="designer_result",
)

# --- Parallel execution ---

parallel_analysis = ParallelAgent(
    name="persona_analysis",
    sub_agents=[product_agent, engineering_agent, designer_agent],
)

# --- Gather: Synthesize results ---

synthesizer = LlmAgent(
    name="synthesizer",
    instruction="""\
Combine the following persona analyses into a unified narrative:

**Product Analytics**:
{product_analytics_result}

**Engineering Manager**:
{engineering_result}

**Designer**:
{designer_result}
""",
    output_key="unified_analysis",
)

# --- Full workflow ---

workflow = SequentialAgent(
    name="pipeline",
    sub_agents=[planner, parallel_analysis, synthesizer, report_agent],
)
```

### Data Flow

```
planner runs          → state["plan"] = "..."
                        (1 LLM call)

parallel_analysis:
  product_agent       → state["product_analytics_result"] = "..."  ┐
  engineering_agent   → state["engineering_result"] = "..."        ├─ simultaneous
  designer_agent      → state["designer_result"] = "..."           ┘
                        (3 LLM calls, running in parallel)

synthesizer runs      → reads all three results, writes state["unified_analysis"]
                        (1 LLM call)

report_agent runs     → reads {unified_analysis}, generates final response
                        (1 LLM call)
```

---

## Combining with Callback Gating

Not all personas are relevant for every query. Use `before_agent_callback` to skip irrelevant persona agents:

```python
async def gate_product_analytics(callback_context):
    plan = callback_context.state.get("plan", "")
    if "Product Analytics" not in plan:
        from google.genai import types
        return types.Content(
            role="model",
            parts=[types.Part(text="Product Analytics: Not relevant for this query.")]
        )
    return None

product_agent = LlmAgent(
    name="product_analytics",
    instruction="...",
    output_key="product_analytics_result",
    before_agent_callback=gate_product_analytics,
)
```

This way, only selected personas make LLM calls — skipped personas cost zero.

---

## Pulse AI: Applying This Pattern

### Current (Sequential)

```
Planner → Executor (all personas in 1 call) → Summary → Report
4 LLM calls, serial, diluted context
```

### With Parallel Fan-Out

```
Planner → ParallelAgent[3 persona agents] → Synthesizer → Report
3-6 LLM calls, parallel personas, focused context per persona
```

### Quality Improvement

| Aspect | Sequential (1 Executor) | Parallel (per-persona agents) |
|---|---|---|
| Context per persona | Diluted (all personas in one prompt) | **Focused** (dedicated prompt) |
| Output depth | Generic | **Detailed, persona-specific** |
| Latency | Sum of all steps | Max of parallel steps (faster) |
| Controllability | All-or-nothing | **Per-persona gating** |

---

## When to Use

| Use When | Avoid When |
|---|---|
| Tasks are independent (no cross-dependencies) | Agent B needs Agent A's output |
| Latency matters (parallel = faster) | Minimizing LLM calls is the top priority |
| Each task benefits from focused context | Tasks are trivially simple |
| You need per-task control (gating, retries) | A single agent can handle everything adequately |

---

## Cost Impact

| Scenario | Planner | Parallel Agents | Synthesizer | Report | Total |
|---|---|---|---|---|---|
| All 5 personas | 1 | 5 | 1 | 1 | **8** |
| 3 core personas | 1 | 3 | 1 | 1 | **6** |
| 1 persona only | 1 | 1 | 1 | 1 | **4** |
| Unclear intent (all skipped) | 1 | 0 | 0 | 1 | **2** |

**Key trade-off**: More LLM calls than sequential, but better quality and lower latency due to parallelism.

---

## Limitations

1. **Higher cost ceiling** — max LLM calls = number of parallel agents + sequential agents
2. **Shared state races** — each parallel agent must write to a unique `output_key`
3. **No cross-agent communication** — parallel agents can't read each other's output during execution
4. **Synthesizer complexity** — combining diverse outputs requires a well-crafted prompt

---

## Related Patterns

- [Sequential Pipeline](./sequential-pipeline-pattern.md) — when tasks depend on each other
- [Callback Conditional Skipping](./callback-conditional-skipping.md) — gate parallel agents to reduce cost
- [Pulse AI Recommendations](./pulse-ai-recommendations.md) — applying this pattern to Pulse AI
