# Coordinator / Dispatcher Pattern

*aka "The Concierge"*

---

## Overview

A central intelligent agent acts as a dispatcher. It analyzes the user's intent and routes the request to a specialist sub-agent best suited for the task. Unlike `SequentialAgent`, the routing decision is made by the LLM at runtime — making it dynamic and context-aware.

```
                    ┌─────────────┐
                    │ Coordinator │  (LLM decides routing)
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ Billing  │ │  Tech    │ │ General  │
        │Specialist│ │ Support  │ │  FAQ     │
        └──────────┘ └──────────┘ └──────────┘
```

---

## How It Works in ADK

### The `description` Field Drives Routing

When an `LlmAgent` has sub-agents, ADK's `AutoFlow` mechanism:

1. Injects each sub-agent's `description` into the parent's context
2. Provides a `transfer_to_agent(agent_name)` function call
3. The parent LLM reads the descriptions and decides which sub-agent to delegate to

This is why the `description` field matters for this pattern — it's the "API documentation" the coordinator reads to make routing decisions.

### Transfer Mechanism

From `LlmAgent` source (`llm_agent.py`):

```python
@property
def _llm_flow(self) -> BaseLlmFlow:
    if (self.disallow_transfer_to_parent
        and self.disallow_transfer_to_peers
        and not self.sub_agents):
        return SingleFlow()
    else:
        return AutoFlow()  # enables transfer_to_agent
```

`AutoFlow` automatically adds `transfer_to_agent` as an available function call when the agent has sub-agents.

---

## Implementation

```python
from google.adk.agents.llm_agent import LlmAgent

billing_specialist = LlmAgent(
    name="billing_specialist",
    description="Handles billing inquiries, invoices, and payment issues.",
    instruction="You are a billing specialist. Help the user with their billing question.",
    tools=[BillingSystemDB],
    disallow_transfer_to_peers=True,
)

tech_support = LlmAgent(
    name="tech_support",
    description="Troubleshoots technical issues, errors, and performance problems.",
    instruction="You are tech support. Diagnose and resolve the user's technical issue.",
    tools=[DiagnosticTool],
    disallow_transfer_to_peers=True,
)

coordinator = LlmAgent(
    name="coordinator",
    model="gemini-2.5-flash",
    instruction="""\
You are a routing agent for customer support.
Analyze the user's message and route to the appropriate specialist.

If the intent is unclear, ask the user for clarification before routing.
""",
    sub_agents=[billing_specialist, tech_support],
)
```

### Execution Flow

```
User: "My app keeps crashing on the payment screen"

coordinator (LLM call #1):
  → Reads sub-agent descriptions
  → Decides: this is a tech issue
  → Calls transfer_to_agent("tech_support")

tech_support (LLM call #2):
  → Receives the user's query
  → Uses DiagnosticTool to investigate
  → Returns diagnosis to user
```

---

## Pulse AI: How This Would Look

```python
coordinator = LlmAgent(
    name="coordinator",
    instruction="""\
You are the Pulse AI routing agent.

Analyze the user's query and decide the appropriate action:
- For analytical queries about app performance, user behavior, or UX:
  route to the analysis_pipeline
- For unclear or conversational queries:
  respond directly with a clarification question
- For simple factual questions:
  respond directly without invoking the pipeline
""",
    sub_agents=[analysis_pipeline],  # SequentialAgent as sub-agent
)
```

---

## When to Use

| Use When | Avoid When |
|---|---|
| Multiple specialist agents with distinct capabilities | The flow is always the same regardless of input |
| Routing logic is complex or nuanced | Simple conditional skip would suffice |
| You want the LLM to handle unclear intent naturally | Every LLM call counts (adds 1 call overhead) |
| Customer service / help desk scenarios | You need deterministic, testable routing |

---

## Cost Impact

| Scenario | LLM Calls |
|---|---|
| Route to one specialist | 2 (coordinator + specialist) |
| Unclear intent — respond directly | 1 (coordinator only) |
| Route to pipeline of 4 agents | 5 (coordinator + 4 pipeline agents) |

**Key trade-off**: Adds 1 LLM call to every request for routing flexibility. For high-volume systems, this overhead matters.

---

## Limitations

1. **Extra LLM call** — the coordinator always costs 1 LLM call, even when routing is obvious
2. **Non-deterministic** — the LLM decides routing; same input might route differently across runs
3. **Description quality matters** — poor sub-agent descriptions lead to poor routing
4. **Debugging complexity** — routing decisions are opaque (hidden in LLM reasoning)

---

## Related Patterns

- [Sequential Pipeline](./sequential-pipeline-pattern.md) — when routing isn't needed
- [Callback Conditional Skipping](./callback-conditional-skipping.md) — deterministic routing without extra LLM calls
- [Custom Agent](./custom-agent-pattern.md) — deterministic routing with full Python control
