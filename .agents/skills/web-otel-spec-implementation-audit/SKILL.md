---
name: web-otel-spec-implementation-audit
description: Audits pulse-web-otel docs/instrumentations/*/SPEC.md against src for format compliance (including mandatory Mermaid HLD, LD, and edge-case flow diagrams) and spec–code parity with a positive/negative/edge test matrix. Uses audit-index.json for dimension checklist and path mapping. Use when editing pulse-web-otel SPECs or instrumentations, after queue hooks fire, or when the user asks for SPEC vs implementation alignment.
disable-model-invocation: true
---

# Web SDK — SPEC implementation audit

## Preconditions

- Repo root is the Pulse monorepo; primary paths are under `pulse-web-otel/`.
- Read [audit-index.json](audit-index.json) for **dimensions** (audit checklist) and **instrumentations** (one row per primary `SPEC.md` path). **sdk-core** splits topics under `docs/sdk-core/<topic>/SPEC.md` (parallel to instrumentations). **session** is `docs/instrumentations/session/SPEC.md`.

## What to audit (dimensions)

For the target instrumentation row, verify every dimension id in `default_dimension_ids` (see `audit-index.json`), or the subset the user asked for:

| Dimension id | What “pass” means |
|--------------|-------------------|
| `spec_structure` | SPEC has clear goal, assumptions, requirements, non-functional notes, and an implementation index listing authoritative `src/` paths. |
| `mermaid_hld` | At least one **Mermaid** diagram showing high-level components, registry/exporter boundaries, and who calls whom (not sequence-level). |
| `mermaid_ld` | At least one **Mermaid** diagram for internal modules, key types/functions, and data flow between units. |
| `mermaid_flows_edge_cases` | At least one **Mermaid** `flowchart` or `stateDiagram` covering the happy path **and** explicit branches for each documented edge (SSR / no `window`, feature off, consent, uninstall, dedupe windows, cross-origin, empty payloads, etc.). |
| `requirements_testable` | Numbered requirements are concrete and falsifiable. |
| `attribute_contract_table` | Tables for spans/logs/metrics align with `src/semconv.ts` and actual attribute keys emitted in code. |
| `implementation_paths` | Every path listed under implementation / touchpoints exists and is still the right owner file. |
| `test_matrix_positive_negative_edge` | A dedicated section or table with **positive**, **negative**, and **edge** scenarios (Given / When / Then); each row cites a test file path or is marked **missing**. |
| `spec_code_parity` | SPEC claims match behavior in `pulse-web-otel/src/`. **Default:** treat code as truth and propose SPEC updates unless an ADR explicitly makes the SPEC normative. |
| `traceability_req_to_tests` | Each requirement maps to at least one matrix row or has an explicit **gap** callout. |

## Procedure

1. **Pick instrumentation** — From user input or from `.cursor/pulse-web-otel-spec-audit-queue.jsonl` tail, resolve `instrumentation_id`. Load `spec_path` and `related_src_globs` from `audit-index.json` → `instrumentations[]`.
2. **Read SPEC** — Check Mermaid fences are well-formed (opening and closing triple-backtick fences with `mermaid` language tag); flag missing HLD/LD/flow.
3. **Read implementation** — Open registry (`instrumentation-registry.ts`), primary instrumentation files, integrations referenced in the SPEC, and Vitest/E2E tests that cover the feature.
4. **Matrix pass** — For each scenario row, mark **covered** / **drift** / **missing test**.
5. **Report** — Use the output template below. Severities: **Critical** (wrong contract or misleading operators), **Major** (drift or missing safety/edge in diagrams or matrix), **Minor** (wording, ordering).

## Queue file (optional context)

If `.cursor/pulse-web-otel-spec-audit-queue.jsonl` exists, read recent lines with matching `instrumentation_id` to prioritize files the editor just touched. Clearing the queue is manual after the user confirms audits are done.

## Output template

```markdown
## SPEC audit: <instrumentation_id>

### Summary
- …

### Dimension results
| Dimension | Status | Notes |
|-----------|--------|-------|

### Findings (by severity)
#### Critical
- …

#### Major
- …

#### Minor
- …

### Suggested changes
- SPEC: …
- Code: … (only if ADR-normative SPEC)
```

## Additional reference

For report examples or long tables, add [reference.md](reference.md) later; keep this SKILL lean.
