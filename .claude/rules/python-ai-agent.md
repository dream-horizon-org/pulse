---
paths:
  - "pulse_ai/**/*.py"
---

# Pulse AI Agent Conventions

## Framework

Google ADK (`google.adk.agents`) with Gemini models, served via `adk web`.

## Project Structure

```
pulse_ai/
├── agent.py          # Root agent definition (LlmAgent)
├── constants.py      # Model config constants
├── Dockerfile        # Python 3.12-slim + google-adk
├── setup.sh          # CLI: start / stop / restart / logs / clean
└── requirements.txt
```

## Agent Pattern

```python
from google.adk.agents.llm_agent import Agent
root_agent = Agent(
    model=agent_model,
    name='root_agent',
    description='...',
    instruction='...',
)
```

## Tool Pattern

```python
from google.adk.tools import FunctionTool
from google.adk.tools.tool_context import ToolContext

def my_tool(tool_context: ToolContext) -> dict:
    result = do_something()
    return {"status": "success", "data": result}

my_function_tool = FunctionTool(my_tool)
```

## Agent Types

| Type | Use |
|------|-----|
| `Agent` / `LlmAgent` | LLM-powered with instructions + tools |
| `SequentialAgent` | Pipeline of sub-agents in order |
| `ParallelAgent` | Sub-agents run concurrently |
| `LoopAgent` | Iterative refinement until exit condition |

## Configuration

| Variable | Required | Default |
|----------|----------|---------|
| `GOOGLE_API_KEY` | Yes | — |
| `AGENT_MODEL` | No | `gemini-2.5-flash` |
| `GOOGLE_GENAI_USE_VERTEXAI` | No | `0` |

## Best Practices

- Type hints on all function signatures
- `snake_case` modules, functions; `PascalCase` classes
- Tools should be pure functions with deterministic behavior
- Return structured dicts with `status` field from all tools
- Never hardcode credentials — use `.env`
