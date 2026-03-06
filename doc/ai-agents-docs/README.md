# Pulse AI — Agent Patterns & Architecture Guide

Reference documentation for multi-agent design patterns in Google Agent Development Kit (ADK), with specific recommendations for the Pulse AI observability platform.

---

## Documents

| Document | Description |
|---|---|
| [Sequential Pipeline](./sequential-pipeline-pattern.md) | Linear A → B → C execution with `SequentialAgent` |
| [Coordinator / Dispatcher](./coordinator-dispatcher-pattern.md) | LLM-driven routing to specialist sub-agents |
| [Parallel Fan-Out](./parallel-fan-out-pattern.md) | Simultaneous execution with `ParallelAgent` + gather |
| [Callback Conditional Skipping](./callback-conditional-skipping.md) | Deterministic gating via `before_agent_callback` |
| [Custom Agent](./custom-agent-pattern.md) | Full Python control by subclassing `BaseAgent` |
| [Cost & Latency Analysis](./cost-latency-analysis.md) | Per-pattern comparison of LLM calls, latency, and trade-offs |
| [Pulse AI Recommendations](./pulse-ai-recommendations.md) | Concrete migration path for the Pulse AI pipeline |

---

## Quick Reference

### ADK Agent Primitives

| Primitive | Purpose | LLM Calls |
|---|---|---|
| `LlmAgent` | Single agent backed by an LLM | 1 per invocation |
| `SequentialAgent` | Runs sub-agents in order | 0 (orchestration only) |
| `ParallelAgent` | Runs sub-agents simultaneously | 0 (orchestration only) |
| `LoopAgent` | Repeats sub-agents until exit condition | 0 (orchestration only) |
| `BaseAgent` (custom) | User-defined orchestration logic | 0 (orchestration only) |

### Communication Between Agents

Agents in ADK communicate through **session state**, not direct message passing:

1. Agent writes output → `session.state["key"]` (via `output_key`)
2. Next agent reads it → `{key}` placeholder in `instruction` template
3. ADK resolves the placeholder at runtime before the LLM call

### Sources

- [Google ADK Documentation](https://google.github.io/adk-docs/)
- [Developer's Guide to Multi-Agent Patterns in ADK](https://developers.googleblog.com/developers-guide-to-multi-agent-patterns-in-adk/) (Google Developers Blog)
- [ADK Callback Design Patterns](https://google.github.io/adk-docs/callbacks/design-patterns-and-best-practices/)
- [ADK Custom Agents](https://google.github.io/adk-docs/agents/custom-agents/)
