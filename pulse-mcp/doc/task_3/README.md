# Task 3 — MCP evaluation test corpus

This folder defines **manual and semi-automated test cases** for `pulse-mcp`: natural-language prompts / tool-call shapes you can replay in Cursor, Claude Desktop, or a JSON-RPC harness. It is aligned with **`pulse-mcp/src/tools/*.ts`** as of authoring; when tools change, update the matrix and IDs.

## Why this matters

Poor coverage here wastes engineering time or worse: teams **trust bad answers** (wrong project, wrong `interactionId` semantics, silent empty results interpreted as “no bugs”). The cases below separate **transport healthy** vs **semantic correct** vs **permission expected**.

## Scope

| In scope | Out of scope |
|----------|----------------|
| All registered MCP tools (~46) | Automated runner code (optional follow-up) |
| Auth startup, 401 retry path, envelope handling | pulse-server internals beyond observable HTTP |
| Read-only assurance (no state mutation via MCP) | Load / soak testing |

**Approximate registered tool count:** **45** (confirm with **`tools/list`** after `yarn build`).

## How to run a case

1. **Prereqs:** Built server (`yarn build`), valid `PULSE_BASE_URL`, personal `PULSE_API_KEY` (`pulse_mcp_*`), tenant with known **project IDs** you may read.
2. **Substitute placeholders** in each file (`{PROJECT_ID}`, `{GROUP_ID}`, etc.).
3. **Record:** tool name, arguments JSON, MCP text response (truncated OK), HTTP status if you sniff proxy logs, **verdict** (pass / fail / expected limitation).
4. **Triangulate ambiguous “empty”:**
   - App Vitals: look for `"ok": true, "empty": true` + `hint`.
   - Heatmap: tool may return **HTTP error JSON in text** without throwing — compare `09-heatmap.md`.

## Naming

- **TC-AREA-xxx** — stable identifiers; reference them in bugs and regressions.
- **`[P]`** — **principal risk** if misunderstood (can mislead downstream users).

## Document map

| File | Contents |
|------|-----------|
| [00-matrix-tool-inventory.md](./00-matrix-tool-inventory.md) | Full tool list, route summary, risk tags |
| [01-cross-cutting-auth-and-env.md](./01-cross-cutting-auth-and-env.md) | Startup, tokens, base URL, 401 |
| [02-projects.md](./02-projects.md) | Projects + members |
| [03-interactions.md](./03-interactions.md) | Interactions, RCA **`[P]`** |
| [04-events-catalog.md](./04-events-catalog.md) | Event definitions & search |
| [05-metrics-interaction-span.md](./05-metrics-interaction-span.md) | Apdex / error rate / durations **`[P]`** |
| [06-session-replays-listing.md](./06-session-replays-listing.md) | Pagination, timeouts **`[P]`** |
| [07-funnels-journeys.md](./07-funnels-journeys.md) | Funnels & journeys |
| [08-alerts.md](./08-alerts.md) | Alerts, scopes, metrics **`[P]`** |
| [09-heatmap.md](./09-heatmap.md) | Heatmap + SDK interplay **`[P]`** |
| [10-sdk-config.md](./10-sdk-config.md) | Active / versions / rules |
| [11-app-vitals.md](./11-app-vitals.md) | App Vitals **`[P]`** |
| [12-negative-boundaries-multi-tenant.md](./12-negative-boundaries-multi-tenant.md) | Wrong IDs, limits, fuzz |
| [13-regression-spotchecks.md](./13-regression-spotchecks.md) | Historical breakage replay |
| [14-multi-step-agent-scenarios.md](./14-multi-step-agent-scenarios.md) | End-to-end misuse traps + ground-truth tool sets |
| [15-appendix-jsonrpc-skeletons.md](./15-appendix-jsonrpc-skeletons.md) | Raw stdio call templates |
| [16-eval-nl-prompts.md](./16-eval-nl-prompts.md) | **LLM tool-selection eval** — 55 NL prompts with expected tools, must-not-pick, and confidence tags |
| [17-scoring-rubric.md](./17-scoring-rubric.md) | Scoring rubric — precision/recall, headline metrics, failure attribution |

## Safety and data handling

- **Read-only MCP** — tools should not create/update production config; still assume responses can contain **session IDs, user IDs, stack traces** → handle like production PII in logs.
- Use **narrow time ranges** in shared environments to limit exposure and query cost.

## Completion criteria for an “evaluation pass”

- Each tool listed in **00-matrix** receives at least one **smoke** (happy path or genuinely empty dataset) plus a **boundary or negative** exercise where materially different code paths exist.
- All **`[P]`** packs executed on a project with real telemetry **and** one edge project (minimal data / restrictive permissions).

