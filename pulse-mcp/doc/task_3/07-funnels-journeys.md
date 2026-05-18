# Funnels and journeys

Paging defaults: **`page`** and **`pageSize`** start at **`1`** and **`10`** for funnels (`list_funnels`). **Interactions** **`list_interactions`** uses **`page: 0`**. **`[P]`** — easy evaluator confusion mixing zero- vs one-based pages.

Harvest:

- `{FUNNEL_ID}`, `{JOURNEY_ID}` from list responses.

---

## TC-FNJ-001 — List funnels default

```json
{
  "projectId": "{PROJECT_READ}",
  "page": 1,
  "pageSize": 10
}
```

---

## TC-FNJ-002 — Search + status filter

```json
{
  "projectId": "{PROJECT_READ}",
  "search": "onboard",
  "status": "ACTIVE",
  "tags": [],
  "page": 1,
  "pageSize": 25
}
```

---

## TC-FNJ-003 — Tags filter serialization

Supply two tags `[ "tagA", "tagB" ]` — MCP joins with **`","`** in query (`tags`). Expect backend accepts.

---

## TC-FNJ-004 — Fetch concrete funnel detail

```json
{ "projectId": "{PROJECT_READ}", "funnelId": {FUNNEL_ID} }
```

Expect structural JSON (steps, conversion definition—record keys).

---

## TC-FNJ-005 — Auxiliary funnel lookups

Sequential tools (same `{PROJECT_READ}`):

- `get_funnel_tags`
- `get_funnel_events`
- `get_funnel_filters`

---

## TC-FNJ-006 — Negative funnel id sentinel

Invalid id `99999999` → **404** expected.

---

## TC-FNJ-007 — Journey list + detail

```
list_journeys { projectId, page:1, pageSize:10 }
get_journey { projectId, journeyId: {JOURNEY_ID} }
```

---

## TC-FNJ-008 — Journey tag filter parity

Reuse tag filter pattern.

