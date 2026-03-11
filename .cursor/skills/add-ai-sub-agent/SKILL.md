---
name: add-ai-sub-agent
description: Step-by-step workflow for adding a new sub-agent or tool to the Pulse AI system. Use when creating a new ADK agent, tool, or extending the AI pipeline in pulse_ai/.
---

# Add AI Sub-Agent or Tool

## Current State

The AI agent has a flat structure — a single root `LlmAgent` in `agent.py` with constants in `constants.py`. Sub-agents, tools, and registries do not exist yet. Follow this workflow when adding new capabilities.

## Workflow

```
- [ ] Step 1: Define constants
- [ ] Step 2: Create tool function (if needed)
- [ ] Step 3: Create the sub-agent
- [ ] Step 4: Wire into root agent
- [ ] Step 5: Test the agent
```

## Step 1: Constants

Add new constants to `pulse_ai/constants.py`:
```python
MY_NEW_CONSTANT = "my_value"
```

For environment-based config, follow the existing pattern:
```python
import os
MY_VALUE = os.getenv("MY_ENV_KEY", "default")
```

## Step 2: Create Tool

Create a new file at the `pulse_ai/` root (e.g., `pulse_ai/tools.py` or `pulse_ai/my_tool.py`):

```python
from google.adk.tools import FunctionTool
from google.adk.tools.tool_context import ToolContext

def my_tool(tool_context: ToolContext) -> dict:
    result = do_something()
    return {"status": "success", "data": result}

my_function_tool = FunctionTool(my_tool)
```

## Step 3: Create Sub-Agent

Create a new file at the `pulse_ai/` root (e.g., `pulse_ai/planner.py`):

```python
from google.adk.agents.llm_agent import Agent
from constants import agent_model

planner_agent = Agent(
    model=agent_model,
    name="planner_agent",
    description="Plans the analysis approach.",
    instruction="Your instructions here.",
    tools=[my_function_tool],
)
```

## Step 4: Wire Into Root Agent

Update `pulse_ai/agent.py` to import and include the new sub-agent:

```python
from planner import planner_agent

root_agent = Agent(
    model=agent_model,
    name='root_agent',
    description='...',
    instruction='...',
    sub_agents=[planner_agent],
)
```

Or use `SequentialAgent` / `ParallelAgent` for pipeline orchestration:
```python
from google.adk.agents import SequentialAgent

pipeline = SequentialAgent(
    name="pipeline",
    sub_agents=[planner_agent, executor_agent, summary_agent],
)
```

## Step 5: Test

```bash
cd pulse_ai && ./setup.sh restart   # rebuild Docker container
./setup.sh logs                      # check for startup errors
curl http://localhost:8000            # verify health
```

Test via the ADK web UI at `http://localhost:8000`.

## Agent Types Reference

| Type | Use |
|------|-----|
| `Agent` / `LlmAgent` | LLM-powered agents with instructions + tools |
| `SequentialAgent` | Pipeline of sub-agents run in order |
| `ParallelAgent` | Sub-agents run concurrently |
| `LoopAgent` | Iterative refinement until exit condition |
| `BaseAgent` | Custom routing logic |
