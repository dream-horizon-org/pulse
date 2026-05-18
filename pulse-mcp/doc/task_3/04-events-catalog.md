# Event catalog and search

`search_events` forwards `searchString` → query param **`search_string`**.

Capture from environment:

- `{EVENT_DEF_NUMERIC_ID}` integer from `list_event_definitions`.

---

## TC-EVT-001 — Definitions default window

**Tool:** `list_event_definitions`  
**Args:**

```json
{
  "projectId": "{PROJECT_READ}",
  "limit": 50,
  "offset": 0
}
```

---

## TC-EVT-002 — Definitions search + category

**Args:**

```json
{
  "projectId": "{PROJECT_READ}",
  "search": "purchase",
  "category": "{CATEGORY_FROM_LIST}",
  "limit": 25,
  "offset": 0
}
```

**Expect:** Monotonic narrowing or valid empty vs error distinguishing “no hits” vs “bad category”.

---

## TC-EVT-003 — Offset pagination sanity

Same as TC-EVT-001 but `offset: 50` — expect no duplicate overlap with first page IDs if catalog large enough.

---

## TC-EVT-004 — Get single definition

**Tool:** `get_event_definition`  
**Args:** `{ "projectId": "{PROJECT_READ}", "id": {EVENT_DEF_NUMERIC_ID} }`

---

## TC-EVT-005 — Negative: unknown definition id

**Args:** `{ "projectId": "{PROJECT_READ}", "id": -1 }` or very large sentinel.

**Expect:** **404**/**400** surfaced.

---

## TC-EVT-006 — Categories list

**Tool:** `list_event_categories`

---

## TC-EVT-007 — Search events default

**Tool:** `search_events`  
**Args:** `{ "projectId": "{PROJECT_READ}", "searchString": "", "limit": 10 }`

**Expect:** Mirrors “empty autocomplete” behavior from UI.

---

## TC-EVT-008 — Search events prefix / token

**Args:** `{ "projectId": "...", "searchString": "sess", "limit": 25 }`

