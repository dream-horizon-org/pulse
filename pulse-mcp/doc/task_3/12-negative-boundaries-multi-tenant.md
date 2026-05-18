# Negative cases, boundaries, tenant isolation

Purpose: Failures must be **truthful**, **non-leaky**, and **actionable**.

---

## Multi-tenant

| Case ID | Tools (examples) | Input | Expected class |
|---------|-----------------|-------|----------------|
| TC-NEG-MT-01 | Many | `{PROJECT_READ}` typo → random string | No data masquerading as success |
| TC-NEG-MT-02 | `get_project`, distributions | `{PROJECT_FORBIDDEN}` | **403** family |
| TC-NEG-MT-03 | Sequential | MCP fixes `projectId` per invocation | Each tool respects listed `projectId`; no bleed |

---

## Argument boundary matrix

| Tool | Field | Value | Expected |
|------|-------|-------|----------|
| `list_session_replays` | pageSize | 0 / 101 | MCP reject |
| `get_event_definition` | id | non-integer schema | MCP reject |
| `get_sdk_config` | version | `-1` | MCP reject |
| `get_app_vitals_issue_stack_traces` | limit | 0 / 51 | MCP reject |
| App vitals list | limit | 0 / 101 | MCP reject |
| First/last | groupIds length | 0 / 51 | MCP reject |

---

## Temporal chaos suite

Reuse across metrics + vitals (**record divergences**):

1. Epoch zero `1970-01-01T00:00:00Z` tiny window vs now.
2. Future-dated **`endTime`** (+365d).
3. **Microsecond ISO** fractions (`2026-01-01T00:00:00.123Z`).
4. Timezone **`+05:30` offset** — different tools parse differently (metrics **`Date`** + **`toISOString`**; vitals **`tryFormatTimeToIso`** path).

Outcome: categorize **silent clamp**, **400**, vs **tool throw**.

---

## Pathological strings (serialization)

Injection-like tokens in `search`, `interactionName`, `screenName`:

- `%00`, Unicode combining marks, `' OR 1=1 --`

**Expect:** No MCP crash / no shell escape — server sanitizes or returns **400** safely.

---

## Tool-specific soft-failure fingerprints

Document each as PASS/FAIL informational:

| Area | Observation target |
|------|-------------------|
| Heatmap | Returns JSON **`{ httpStatus, pulseError }`** (no thrown error) vs other tools |
| App vitals empty | **`ok: true`, `empty: true`, `hint`** present |
| SDK active | **`get_active_sdk_config`** envelope may differ from default unwrap |

---

## SSRF note (evaluation harness)

**`PULSE_BASE_URL`** points only at Pulse API origins you intend to hit. Poisoned env is operator risk, not MCP Zod-layer.
