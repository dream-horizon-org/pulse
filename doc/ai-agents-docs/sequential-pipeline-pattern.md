# Sequential Pipeline Pattern

*aka "The Assembly Line"*

---

## Overview

The simplest multi-agent pattern. Agents execute one after another in a fixed order, each building on the output of the previous agent. Think of it as an assembly line — Agent A finishes and hands the baton to Agent B.

```
User Query → Agent A → Agent B → Agent C → Response
```

ADK provides the `SequentialAgent` primitive for this pattern. It takes zero LLM calls itself — it simply iterates through its `sub_agents` list in order.

---

## How It Works in ADK

### Agent Communication

Agents communicate through **shared session state**, not direct message passing:

1. Each `LlmAgent` has an `output_key` that stores its text response in `session.state`
2. The next agent's `instruction` template contains `{placeholder}` references
3. ADK resolves placeholders from `session.state` before sending the prompt to the LLM

### Source Code (simplified)

From `google.adk.agents.sequential_agent`:

```python
async def _run_async_impl(self, ctx):
    for i in range(start_index, len(self.sub_agents)):
        sub_agent = self.sub_agents[i]
        async for event in sub_agent.run_async(ctx):
            yield event
```

It's a plain `for` loop — no conditional logic, no skipping, no branching. Every sub-agent runs, always.

---

## Implementation

```python
from google.adk.agents.llm_agent import LlmAgent
from google.adk.agents.sequential_agent import SequentialAgent

parser = LlmAgent(
    name="parser",
    instruction="Parse the raw input and extract structured data.",
    output_key="parsed_data",
)

analyzer = LlmAgent(
    name="analyzer",
    instruction="Analyze the following data:\n{parsed_data}",
    output_key="analysis",
)

reporter = LlmAgent(
    name="reporter",
    instruction="Generate a user-facing report from:\n{analysis}",
)

pipeline = SequentialAgent(
    name="pipeline",
    sub_agents=[parser, analyzer, reporter],
)
```

### Data Flow

```
session.state = {}

parser runs     → state["parsed_data"] = "..."
analyzer runs   → reads {parsed_data}, writes state["analysis"] = "..."
reporter runs   → reads {analysis}, generates final response
```

---

## Pulse AI: Current Architecture

Pulse AI uses this pattern today:

```
User Query
    │
    ▼
┌──────────┐
│  Planner │   output_key="plan"
└────┬─────┘
     │
     ▼
┌──────────┐
│ Executor │   reads {plan}, output_key="execution_results"
└────┬─────┘
     │
     ▼
┌──────────┐
│ Summary  │   reads {execution_results}, output_key="summary"
└────┬─────┘
     │
     ▼
┌──────────┐
│  Report  │   reads {summary}, generates final response
└──────────┘
```

**LLM calls**: 4 (one per agent), every time, regardless of query complexity.

---

## When to Use

| Use When | Avoid When |
|---|---|
| Steps have clear dependencies (B needs A's output) | Steps are independent and could run in parallel |
| The pipeline is deterministic and well-defined | Routing depends on runtime conditions |
| Debugging simplicity is important | Cost optimization is critical (all agents always run) |
| Starting a new project (add complexity later) | Some steps are optional depending on input |

---

## Limitations

1. **No conditional execution** — every agent runs regardless of whether it's needed
2. **Serial latency** — total time = sum of all agent execution times
3. **No early exit** — if the first agent determines the query is unclear, the rest still run
4. **Rigid ordering** — can't dynamically reorder agents based on context

---

## Related Patterns

- [Callback Conditional Skipping](./callback-conditional-skipping.md) — adds gating to overcome limitation #1
- [Parallel Fan-Out](./parallel-fan-out-pattern.md) — overcomes limitation #2 for independent steps
- [Custom Agent](./custom-agent-pattern.md) — overcomes all limitations with full Python control
