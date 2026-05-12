# pulse-ai — Plan index

Component brief: [`/docs/components/pulse-ai.md`](../../components/pulse-ai.md).

Pulse AI is a FastAPI service that wraps Google ADK agents. The HTTP layer turns
incoming requests into ADK `Runner` invocations and streams events back as SSE.
The reasoning layer is a `SequentialAgent` (`root_agent` in `pulse_ai/agent.py`)
that chains the **EM** persona (data analysis with seven tools) into the
**Report** persona (chart + table rendering). Two dedicated runners
(`rca_runner`, `screen_rca_runner`) wrap reasoning-only agents that emit
structured Pydantic outputs.

## Plan tree

### Core

| Page | Covers |
|---|---|
| [core/agent-server.md](core/agent-server.md) | FastAPI factory, middleware, runners, session service, SSE streaming. |
| [core/adk-setup.md](core/adk-setup.md) | ADK agent types (`Agent`, `LlmAgent`, `SequentialAgent`, `ParallelAgent`, `LoopAgent`), tool contract, output schemas. |
| [core/prompt-personas.md](core/prompt-personas.md) | System prompts for EM, RCA, Report, Screen-RCA personas. |

### Tools

| Page | Covers |
|---|---|
| [tools/clickhouse-tools.md](tools/clickhouse-tools.md) | Analytics tools (`query_interaction_health`, `query_interaction_metrics`, `query_interaction_sessions`, `breakdown_interaction`) — backed by pulse-server distribution endpoints over `otel_traces`/`otel_logs`. |
| [tools/mysql-tools.md](tools/mysql-tools.md) | Config tools (`query_interactions`, `query_alerts`) — backed by MySQL-stored Pulse project/alert tables. |
| [tools/orchestration.md](tools/orchestration.md) | `SequentialAgent` composition, why `report_agent` is built via a factory, RCA pipeline shape. |

## Rebuild recipe

```bash
cd pulse_ai
cp .env.example .env                 # set GOOGLE_API_KEY etc.
./setup.sh                           # docker compose up -d → :8000
# Or local:
pip install -r requirements.txt
uvicorn pulse_ai.server.app:app --reload --port 8000
pytest                               # full test suite
```

Minimum env: `GOOGLE_API_KEY`. Optional: `AGENT_MODEL`, `PULSE_BASE_URL`,
`SESSION_DB_URL`, `CORS_ALLOWED_ORIGINS`, `LOG_LEVEL`.
