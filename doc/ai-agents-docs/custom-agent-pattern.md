# Custom Agent Pattern

*Full deterministic control via `BaseAgent` subclass*

---

## Overview

When the built-in primitives (`SequentialAgent`, `ParallelAgent`, `LoopAgent`) don't fit your orchestration needs, you can create a **Custom Agent** by subclassing `BaseAgent` and implementing `_run_async_impl`. This gives you 100% Python control over which agents run, in what order, and under what conditions — with zero LLM overhead for orchestration.

```
                ┌──────────────────┐
                │   Custom Agent   │  (pure Python logic)
                │                  │
                │  if condition_a: │
                │    run Agent A   │
                │    run Agent B   │
                │  else:           │
                │    run Agent C   │
                └──────────────────┘
```

This is documented in the [ADK Custom Agents guide](https://google.github.io/adk-docs/agents/custom-agents/).

---

## How It Works

### Subclassing `BaseAgent`

Override `_run_async_impl` to define your orchestration logic:

```python
from google.adk.agents.base_agent import BaseAgent
from google.adk.events.event import Event
from typing import AsyncGenerator

class CustomAgent(BaseAgent):
    async def _run_async_impl(
        self, ctx: InvocationContext
    ) -> AsyncGenerator[Event, None]:
        # Your orchestration logic here
        # Run sub-agents conditionally, in any order
        pass
```

### Running Sub-Agents

Use `sub_agent.run_async(ctx)` to execute any sub-agent and yield its events:

```python
async for event in sub_agent.run_async(ctx):
    yield event
```

### Accessing Session State

Read from state to make routing decisions:

```python
plan = ctx.session.state.get("plan", "")
```

---

## Implementation

### Example: Conditional Pipeline

```python
from google.adk.agents.base_agent import BaseAgent
from google.adk.agents.invocation_context import InvocationContext
from google.adk.events.event import Event
from typing import AsyncGenerator

class ConditionalPipeline(BaseAgent):
    """Runs planner, then conditionally routes to different agent paths."""
    
    async def _run_async_impl(
        self, ctx: InvocationContext
    ) -> AsyncGenerator[Event, None]:
        # Always run the planner first
        planner = self.find_sub_agent("planner")
        async for event in planner.run_async(ctx):
            yield event
        
        # Read the planner's output from state
        plan = ctx.session.state.get("plan", "")
        
        # Deterministic routing based on plan content
        if "UNCLEAR" in plan:
            # Skip everything, run report to return clarification
            report = self.find_sub_agent("report")
            async for event in report.run_async(ctx):
                yield event
            return
        
        # Check which personas are needed
        personas = []
        if "Product Analytics" in plan:
            personas.append("product_analytics")
        if "Engineering" in plan:
            personas.append("engineering_manager")
        if "Designer" in plan:
            personas.append("designer")
        
        # Run selected persona agents
        for persona_name in personas:
            agent = self.find_sub_agent(persona_name)
            if agent:
                async for event in agent.run_async(ctx):
                    yield event
        
        # Run summary and report
        for agent_name in ["summary", "report"]:
            agent = self.find_sub_agent(agent_name)
            async for event in agent.run_async(ctx):
                yield event
```

### Wiring It Up

```python
root_agent = ConditionalPipeline(
    name="pulse_ai_pipeline",
    sub_agents=[
        planner_agent,
        product_analytics_agent,
        engineering_manager_agent,
        designer_agent,
        summary_agent,
        report_agent,
    ],
)
```

### Example: Parallel with Custom Gather Logic

```python
import asyncio
from google.adk.utils.context_utils import Aclosing

class ParallelWithCustomGather(BaseAgent):
    """Runs agents in parallel with custom aggregation logic."""
    
    async def _run_async_impl(
        self, ctx: InvocationContext
    ) -> AsyncGenerator[Event, None]:
        # Run planner
        planner = self.find_sub_agent("planner")
        async for event in planner.run_async(ctx):
            yield event
        
        # Determine which persona agents to run
        plan = ctx.session.state.get("plan", "")
        persona_agents = []
        for name in ["product_analytics", "engineering_manager", "designer"]:
            if name.replace("_", " ").title() in plan or "all" in plan.lower():
                agent = self.find_sub_agent(name)
                if agent:
                    persona_agents.append(agent)
        
        # Run persona agents (sequentially here; for true parallel, 
        # use asyncio.gather with event collection)
        for agent in persona_agents:
            async for event in agent.run_async(ctx):
                yield event
        
        # Run synthesizer and report
        for name in ["summary", "report"]:
            agent = self.find_sub_agent(name)
            async for event in agent.run_async(ctx):
                yield event
```

---

## Key Methods Available

| Method | Purpose |
|---|---|
| `self.find_sub_agent(name)` | Find a sub-agent by name |
| `self.sub_agents` | List of all registered sub-agents |
| `sub_agent.run_async(ctx)` | Execute a sub-agent |
| `ctx.session.state` | Read/write session state |
| `self._create_agent_state_event(ctx)` | Create state tracking events |

---

## When to Use

| Use When | Avoid When |
|---|---|
| Orchestration logic is complex (multi-branch, loops with custom exit) | A simple `SequentialAgent` + callbacks suffices |
| You need deterministic, testable routing | You want the LLM to decide routing |
| Performance is critical (zero LLM overhead for routing) | The overhead of writing a custom class isn't justified |
| Built-in primitives don't fit your workflow | The pattern is standard enough for built-in primitives |

---

## Cost Impact

| Scenario | LLM Calls | Routing Cost |
|---|---|---|
| Full pipeline (all personas) | 6 (planner + 3 personas + summary + report) | **0** (Python) |
| Partial pipeline (1 persona) | 4 (planner + 1 persona + summary + report) | **0** (Python) |
| Unclear intent | 2 (planner + report with clarification) | **0** (Python) |

**Most cost-efficient pattern** — you only pay for the agents you actually run, and routing decisions are free.

---

## Advantages Over Callbacks

| Aspect | Callbacks | Custom Agent |
|---|---|---|
| Logic location | Scattered across individual agents | **Centralized** in one class |
| Testability | Test each callback separately | **Test orchestration as a unit** |
| Complex routing | Awkward (flags passed through state) | **Natural** (Python control flow) |
| Readability | Implicit flow (must trace callbacks) | **Explicit** (read the code) |

---

## Limitations

1. **More code to maintain** — you're writing orchestration logic that built-in primitives handle for free
2. **No automatic state tracking** — `SequentialAgent` handles resume/pause automatically; custom agents must manage this manually if needed
3. **Testing burden** — must unit test the orchestration logic yourself
4. **ADK upgrade risk** — custom code may need updates when ADK internals change

---

## Related Patterns

- [Sequential Pipeline](./sequential-pipeline-pattern.md) — simpler alternative for linear flows
- [Callback Conditional Skipping](./callback-conditional-skipping.md) — lighter-weight gating
- [Coordinator / Dispatcher](./coordinator-dispatcher-pattern.md) — LLM-based routing alternative
