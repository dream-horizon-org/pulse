# Projects (`registerProjectTools`)

Placeholders: `{PROJECT_READ}`, `{PROJECT_NO_ACCESS}`, `{ALIEN_UUID}` fake UUID/id.

---

## TC-PROJ-001 — List projects smoke

**Tool:** `list_projects`  
**Args:** `{}`

**Expect:** JSON array or object shaped per API; contains at least one project user can access. **Pass** if HTTP 200 and parseable structured data (not HTML error).

---

## TC-PROJ-002 — Get project for accessible ID

**Tool:** `get_project`  
**Args:** `{ "projectId": "{PROJECT_READ}" }`

**Expect:** Project metadata. Field presence depends on backend (record sample keys for regression snapshots).

---

## TC-PROJ-003 — Members list (+ PII warning)

**Tool:** `list_project_members`  
**Args:** `{ "projectId": "{PROJECT_READ}" }`

**Expect:** Member rows; possibly emails. Treat output as confidential in evaluation logs.

---

## TC-PROJ-004 — Negative: project ID typo / unknown

**Tool:** `get_project`  
**Args:** `{ "projectId": "definitely-nonexistent-project-id" }`

**Expect:** HTTP error surfaced as MCP error text (**404** or **403** per deployment). Agent must **not** invent project existence.

---

## TC-PROJ-005 — Multi-tenant: ID exists but forbidden

**Tool:** `get_project` OR `list_project_members`  
**Args:** `{ "projectId": "{PROJECT_NO_ACCESS}" }` (valid format, forbidden)

**Expect:** **403** (or equivalent). Confirms MCP forwards `X-Project-ID`.

---

## TC-PROJ-006 — Consistency chain

**Intent:** Listed project IDs resolve with `get_project`.

**Steps:**

1. `list_projects`
2. For first `projectId`: `get_project` + `list_project_members`

**Expect:** Same `projectId` string round-trips.

