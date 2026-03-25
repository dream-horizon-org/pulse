# Pulse AI

AI layer on top of [Pulse](../README.md) — digital experience and app observability. Pulse AI uses agentic reasoning to analyze observability data across multiple personas and produce unified, actionable reports.

Built with [Google Agent Development Kit (ADK)](https://google.github.io/adk-docs/).

## Architecture

Pulse AI is **not** a static pipeline — agents reason, delegate, and invoke tools dynamically.

### Personas

Three **core** personas drive the analysis:

| Persona | Focus |
|---|---|
| Product Analytics | Usage patterns, funnels, feature adoption |
| Engineering Manager | Performance, errors, reliability |
| Designer | UX flows, interaction patterns, usability |

Two **dependent** personas build on the core three:

| Persona | Depends On |
|---|---|
| Customer Success | Product Analytics + Engineering Manager + Designer |
| Business Leaders | Product Analytics + Engineering Manager + Designer |

### Agent Pipeline

```
User Query
    │
    ▼
┌──────────┐
│  Planner │   Understands intent, selects relevant personas
└────┬─────┘
     │
     ▼
┌──────────┐
│ Executor │   Iterates over selected personas
│  (loop)  │   Each persona invokes its features (tools)
└────┬─────┘
     │
     ▼
┌──────────┐
│ Summary  │   Invokes cross-persona features
│  Agent   │   Produces a unified narrative across all three core personas
└────┬─────┘
     │
     ▼
┌──────────┐
│  Report  │   Generates the final user-facing response
│  Agent   │
└──────────┘
```

**Planner** → Understands the user query and decides which personas are relevant.

**Executor** → Loops over each selected persona, invoking persona-specific tools/features to gather insights.

**Summary Agent** → Runs cross-persona features that span all three core personas and synthesizes a unified narrative.

**Report Agent** → Generates the final, structured response for the user.

## Prerequisites

- Python **3.12+**
- A Google API key from [AI Studio](https://aistudio.google.com/apikey)

## Quick Start (Local)

```bash
cd pulse_ai

# 1. Create and activate a virtual environment
python3 -m venv .venv
source .venv/bin/activate

# 2. Install dependencies
pip install -r requirements.txt

# 3. Create your env file and set your API key
cp .env.example .env
# Edit .env and paste your GOOGLE_API_KEY

# 4. Start the ADK web UI
adk web
```

The agent will be available at **http://localhost:8000**.

## Quick Start (Docker)

If you prefer Docker over a local Python setup:

```bash
cd pulse_ai

# 1. Create your env file and set your API key
cp .env.example .env
# Edit .env and paste your GOOGLE_API_KEY

# 2. Run
./setup.sh
```

| Command | What it does |
|---|---|
| `./setup.sh` | Build and start the agent |
| `./setup.sh stop` | Stop the agent |
| `./setup.sh restart` | Rebuild and restart |
| `./setup.sh logs` | Tail container logs |
| `./setup.sh clean` | Remove containers, images, and volumes |

## Configuration

All configuration lives in the `.env` file:

| Variable | Required | Default | Description |
|---|---|---|---|
| `GOOGLE_API_KEY` | Yes | — | Google AI Studio API key |
| `GOOGLE_GENAI_USE_VERTEXAI` | No | `0` | Set to `1` to use Vertex AI instead of AI Studio |
| `AGENT_MODEL` | No | `gemini-2.5-flash` | Gemini model to use (e.g. `gemini-2.5-pro`) |
| `LOG_LEVEL` | No | `INFO` | Logging level (`DEBUG`, `INFO`, `WARNING`, `ERROR`) |
| `CORS_ALLOWED_ORIGINS` | No | `localhost:3000,3001` | Comma-separated allowed origins |
| `SESSION_DB_URL` | No | in-memory | Session persistence URL (e.g. `sqlite:///sessions.db`) |

## Development

Agent source files (`agent.py`, `server/`, `constants.py`, `agents/`, `__init__.py`) are volume-mounted into the container, so code changes are reflected without rebuilding. For dependency changes (`requirements.txt`), run:

```bash
./setup.sh restart
```
