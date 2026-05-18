# Task 2 — Reducing MCP tool count (strategy)

**Goal:** Decide how to shrink the Pulse MCP surface (~45 tools today) without blocking the workflows that matter.

This doc is strategy only — no implementation checklist until owners pick an approach.

---

## 1. Clarify *why* you are reducing

Different drivers imply different cuts:

| Driver | Implication |
|--------|-------------|
| Model picks wrong tool often | Prefer **fewer, clearer names** or **one facet tool per domain** with an explicit `operation` argument |
| Tool list / prompts too large | Same as above; optionally **split servers** (e.g. `pulse-mcp-minimal` vs full) |
| Maintenance cost | Merge thin wrappers; drop unused domains |
| Product retired APIs | Remove tools entirely (already done for query builder) |

Pick one primary driver; secondary drivers tune the same decisions.

---

## 2. Inventory pass (required before cutting)

1. **Usage:** Logs, analytics, or engineer interviews — which tools run in real sessions vs never?
2. **Overlap:** Same pulse-server route called twice under different tool names?
3. **Workflow bundles:** Typical RCA / funnel / session-debug sequences — which tools always appear together?

Outcome: a **tier list** — P0 (must stay discoverable), P1 (keep but may merge), P2 (candidate to hide or remove).

---

## 3. Consolidation patterns (tradeoffs)

### A. Facet tool per domain (`operation` / `resource` enum)

**Example shape:** `pulse_events({ operation: "list_definitions" | "get" | "categories" | "search", ... })`

- **Pros:** One slot in the tool list per domain; shared auth/header logic.
- **Cons:** Weaker “tool name hints” for the model; larger argument schemas; easier to send invalid combos unless Zod is strict.

Use when: several tools share one backend area and differ only by path/query shape.

### B. Single parameterized metric tool

**Example:** One interaction-metrics tool with `metric: "apdex" | "error_rate" | "latency" | "categorization"` (names TBD).

- **Pros:** Collapses four similar POSTs to the same route family.
- **Cons:** Callers must know enum values; docs must list response shapes per metric.

### C. Server-side aggregation (if pulse-server supports it)

If the backend gains **one batched read** (e.g. metrics bundle), MCP exposes **one tool**. No aggregation on MCP alone without backend support.

### D. Tiered servers or env-gated registration

- **`minimal`:** projects + interactions list + session listing + one metrics path — for demos / narrow agents.
- **`full`:** everything else.

Reduces *effective* tools per deployment without deleting code paths.

---

## 4. What *not* to merge blindly

- **Different IDs / semantics** (e.g. MySQL `interactionId` vs span-name id for metrics) — merging increases wrong-ID bugs unless the tool accepts **one** canonical id and maps internally.
- **Very different latency / failure modes** — listing vs RCA vs heavy aggregates; splitting can help timeouts and retries.
- **Tools that differ only by HTTP method + URL** — safe merge candidates *if* schemas stay strict.

---

## 5. Recommended decision order

1. Define **P0 workflows** (e.g. “list interactions → metric chart → session list” / “RCA path”).
2. Mark tools **outside P0** as candidates for merge, env gate, or removal.
3. Prefer **merges within one `src/tools/*.ts` module** first (low coupling).
4. For each merge: write **one paragraph** in breaking-changes: old names → new shape, migration for prompts/agents.
5. Ship **deprecated aliases** (same handler, old tool name) only if external agents depend on names — short window.

---

## 6. Open questions (resolve before coding)

- Who consumes MCP besides Cursor — need backward-compatible tool names?
- Is the target **absolute count** (e.g. “≤25”) or **% reduction**?
- Will pulse-server add batched APIs soon — affects whether to invest in MCP-only facet tools vs wait?

---

## Related

- Current inventory: [`../task 1/mcp-tools.md`](../task%201/mcp-tools.md)
- Breaking changes log: [`../task 1/mcp-breaking-changes-and-pending.md`](../task%201/mcp-breaking-changes-and-pending.md)
