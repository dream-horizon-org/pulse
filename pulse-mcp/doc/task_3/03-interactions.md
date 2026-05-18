# Interactions and RCA ** `[P]` **

Semantic trap: **`get_interaction_root_cause`** uses **`interactionId`** matching `/v1/interactions/{interactionId}` — typically the **Pulse interaction entity id** from **`list_interactions`**, **not** the span name passed to **`get_interaction_apdex_score`**.

Gather from live data:

- `{INTERACTION_DB_ID}` from `list_interactions` response (field name varies—record actual path).
- `{INTERACTION_NAME}` equals **SpanName** / critical interaction label for metric tools (**see `05-metrics-interaction-span.md`**).

---

## TC-INT-001 — List interactions (default paging)

**Tool:** `list_interactions`  
**Args:**

```json
{
  "projectId": "{PROJECT_READ}",
  "page": 0,
  "size": 20
}
```

---

## TC-INT-002 — Filters combined

**Args:** `{ "projectId": "...", "name": "{SUBSTRING}", "status": "ACTIVE", "page": 0, "size": 10 }`

**Expect:** Narrower cardinality than TC-INT-001; **pass** even if zero results with honest empty list vs error.

---

## TC-INT-003 — Pagination boundary

**Args:** `{ "projectId": "...", "page": 0, "size": 1 }` then `page: 1`

**Expect:** Different rows if sufficient data volume.

---

## TC-INT-004 — `list_suggested_interactions`

**Args:** `{ "projectId": "{PROJECT_READ}" }`

---

## TC-INT-005 — Filter options discovery

**Tool:** `get_interaction_filter_options`  
**Expect:** Enough structure to reconcile TC-INT-002 filter values (`status`, emails).

---

## TC-INT-006 — Telemetry filters discovery

**Tool:** `get_interaction_telemetry_filters`  
**Use:** Drive dimension values reused in **`get_interaction_apdex_score`** optional filters (`platform`, `appVersion`, etc.).

---

## TC-INT-007 — RCA happy path **`[P]`**

**Tool:** `get_interaction_root_cause`  
**Args:**

```json
{
  "projectId": "{PROJECT_READ}",
  "interactionId": "{INTERACTION_DB_ID}"
}
```

**Expect:** RCA payload OR explicit “no RCA yet” semantics from backend. **Regression:** Passing `{INTERACTION_NAME}` (span name) here likely **404**/wrong — document if observed (**common operator mistake**).

---

## TC-INT-008 — RCA not found / wrong ID

**Args:** `{ "projectId": "{PROJECT_READ}", "interactionId": "999999999" }` (adjust after observing ID type)

**Expect:** Clean **4xx**, not MCP crash.

---

## TC-INT-009 — Forbidden project context

Reuse `{PROJECT_NO_ACCESS}` for `list_interactions`.

**Expect:** **403**/error string; never silent cross-tenant bleed.

