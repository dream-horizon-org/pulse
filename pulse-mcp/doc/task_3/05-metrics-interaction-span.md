# Interaction analytics metrics (**`[P]`** — SpanName ≠ interaction DB id)

All four tools call **`POST /v1/interactions/performance-metric/distribution`** with `PulseType=interaction`, `SpanName=interactionId` parameter value.

Fill from environment:

- `{SPAN_NAME}` — **critical interaction span** string identical to Pulse UI drill-down (often same as **`name`** shown in dashboards; validate against **`get_interaction_telemetry_filters`** + known interaction).
- `{WINDOW_START_ISO}`, `{WINDOW_END_ISO}` — last 24h recommended for smoke.

**Danger:** Using numeric DB interaction id here produces **silent wrong** or sparse results.

---

## TC-MET-001 — Apdex baseline

**Tool:** `get_interaction_apdex_score`

```json
{
  "projectId": "{PROJECT_READ}",
  "interactionId": "{SPAN_NAME}",
  "startTime": "{WINDOW_START_ISO}",
  "endTime": "{WINDOW_END_ISO}"
}
```

**Expect:** Rows or JSON error from distribution; MCP must not crash. If empty: widen window or validate span string.

---

## TC-MET-002 — Error rate same window

**Tool:** `get_interaction_error_rate` — identical args shell as TC-MET-001.

---

## TC-MET-003 — Interaction time percentiles aggregate

**Tool:** `get_interaction_time` — same shell.

---

## TC-MET-004 — User categorization aggregate

**Tool:** `get_interaction_categorization` — same shell.

---

## TC-MET-005 — Dimensions stack (mirror UI filters **`[P]`**)

Reuse telemetry filter values (`platform`, `appVersion`, etc.):

```json
{
  "projectId": "{PROJECT_READ}",
  "interactionId": "{SPAN_NAME}",
  "startTime": "{WINDOW_START_ISO}",
  "endTime": "{WINDOW_END_ISO}",
  "platform": "android",
  "appVersion": "{VERSION_FROM_FILTERS}",
  "osVersion": "{OPTIONAL}",
  "networkProvider": "{OPTIONAL}",
  "deviceModel": "{OPTIONAL}",
  "state": "{OPTIONAL}"
}
```

**Expect:** Narrower cardinality; filter typos yield empty series — differentiate from tool bug via **heatmap-style** parity check in UI.

---

## TC-MET-006 — Long window (within ~90d cap logic)

Pick **31 days** ISO range. **`timeBucket.ts`** clamps bucket math for spans > 90 days in UI parity—still expect valid response unless backend forbids.

---

## TC-MET-007 — Negative: reversed / invalid timestamps

### 7a Bad ISO

Use `startTime: "not-a-date"`.

**Implementation note:** MCP `metrics.ts` **`toIsoRange`** throws **`Error`** before HTTP — MCP run should surface as tool error (not Axios).

### 7b start > end (if parseable dates)

Provide end before start chronologically — record backend behavior (**400 vs empty**) for documentation.

---

## TC-MET-008 — Forbidden project **`[P]`**

Same valid body, `{PROJECT_NO_ACCESS}`.

**Expect:** **403**/auth error—not empty success JSON masquerading as “no telemetry”.

---

## TC-MET-009 — Span name typo

`interactionId: "___DOES_NOT_EXIST___"` vs real span.

**Expect:** Honest sparse/empty distinction; evaluator records whether UI shows identical emptiness.

