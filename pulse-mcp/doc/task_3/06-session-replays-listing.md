# Session replay listing (**`[P]`** — slow queries, JWT email header)

Implementation facts:

- **`POST /v1/sessions/listing`**, **`pageSize`** 1–100, default sort `START_TIME` **DESC**.
- Optional **`user-email`** header only when JWT **`requireUserEmailFromToken()`** succeeds; otherwise omitted (**document behavior** vs UI).
- **Both** `startTime` + **`endTime`** required together—or **omit both** for last **7 days** default ISO range.
- **Reserved no-op:** `device`, **`eventTypes`** described as not mapped—tests document they do nothing (prevents false regression reports).
- **120s** client timeout (`SESSION_LISTING_TIMEOUT_MS`).

---

## TC-SES-001 — Default window (minimal args)

**Tool:** `list_session_replays`

```json
{
  "projectId": "{PROJECT_READ}",
  "pageSize": 20
}
```

---

## TC-SES-002 — Narrow time range (**recommended for CI**)

```json
{
  "projectId": "{PROJECT_READ}",
  "startTime": "{ISO_24H_AGO}",
  "endTime": "{ISO_NOW}",
  "pageSize": 25
}
```

**Expect:** Latency manageable; **`page.nextCursor`** may be absent/present depending on cardinality.

---

## TC-SES-003 — Explicit sort

```json
{
  "projectId": "{PROJECT_READ}",
  "sortBy": "START_TIME",
  "sortDirection": "ASC",
  "pageSize": 10
}
```

---

## TC-SES-004 — Cursor pagination (**`[P]`**)

1. Run TC-SES-002.
2. If `nextCursor` present — call again with same time range + `cursor: "{COPY}"`.

**Expect:** Disjoint sessions vs page 1; fail if duplicates dominate (possible regression).

---

## TC-SES-005 — Interaction name search query bridge

Pass known interaction label fragment:

```json
{
  "projectId": "{PROJECT_READ}",
  "interactionName": "{PARTIAL_MATCH}",
  "pageSize": 15
}
```

---

## TC-SES-006 — Negative: asymmetric time **`[P]`**

```json
{ "projectId": "{PROJECT_READ}", "startTime": "{ISO}", "pageSize": 20 }
```

**omit `endTime`**

**Expect:** MCP throws **before HTTP**: *Provide both startTime and endTime together…*

---

## TC-SES-007 — Boundary `pageSize`

`pageSize: 1` — **PASS**  
`pageSize: 100` — **PASS**  
`pageSize: 101` — expect **argument validation rejection** at MCP layer.

---

## TC-SES-008 — Timeout discipline

Run TC-SES-002 on **heavy** tenant with **`pageSize`: 100** and **30-day** window; if client timeout triggers:

**Expect:** Friendly rewrite mentioning **timeout** / **narrow timeRange**, not ambiguous “Pulse 500”.

---

## TC-SES-009 — JWT without email claim (if reproducible)

If your personal MCP key yields token **without `email`**:

**Expect:** Listing still succeeds if backend allows omit header; **`requireUserEmailFromToken`** not called. Document parity gap vs environments that force header.
