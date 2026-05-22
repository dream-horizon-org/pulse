---
name: web-otel-spec-audit-orchestrator
description: Index skill for the SPEC audit orchestrator — load the agent definition for full persona, Task invocation, and iteration loop. Use when the user @mentions the orchestrator skill or asks for multi-pass SPEC vs implementation sweeps for pulse-web-otel.
disable-model-invocation: true
---

# Web SDK — SPEC audit orchestrator (skill index)

**Canonical orchestrator persona (read this first):** [`.agents/agents/web-otel-spec-audit-orchestrator.md`](../../.agents/agents/web-otel-spec-audit-orchestrator.md)

Act as that agent: mission, **audit priority (correctness before structure; `smoke` escape)**, preconditions, **Invocation** (`@web-otel-spec-audit-orchestrator` or `Task(subagent_type="web-otel-spec-audit-orchestrator", …)`), confirmation gate, outer iteration loop, queue drain, nested `Task(subagent_type="pulse-web-sdk", readonly=true)` for per-unit audits, fallback in-thread audit, and **§ F** close-out.

**Shared index:** [web-otel-spec-implementation-audit/audit-index.json](../web-otel-spec-implementation-audit/audit-index.json)

**Per-unit checklist:** [web-otel-spec-implementation-audit/SKILL.md](../web-otel-spec-implementation-audit/SKILL.md)

**Hooks:** `.cursor/hooks/queue-web-otel-spec-audit.sh` (queue + stderr nudge) + `resolve-spec-audit-instrumentation.mjs` → `.cursor/pulse-web-otel-spec-audit-queue.jsonl`

**Verify setup:** `.cursor/hooks/verify-spec-audit-setup.sh`
