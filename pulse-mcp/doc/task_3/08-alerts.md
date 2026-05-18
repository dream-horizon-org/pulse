# Alerts (**`scope` trap on metrics** **`[P]`**)

Pulse-server requires **`scope`** query string for **`GET /v1/alert/metrics`**. Calling without scope historically produced **HTTP 400** (README + task-1 failures doc).

Harvest `{ALERT_ID}` from **`list_alerts`**.

---

## TC-ALT-001 — Alerts list baseline

```json
{ "projectId": "{PROJECT_READ}" }
```

---

## TC-ALT-002 — Alerts list filtered

```json
{
  "projectId": "{PROJECT_READ}",
  "search": "latency",
  "severity": "{FROM_get_alert_severities}",
  "scope": "{OPTIONAL_SCOPE_STRING}"
}
```

---

## TC-ALT-003 — Filters discovery

```json
{ "projectId": "{PROJECT_READ}" }
```
**Tool:** `get_alert_filters`

---

## TC-ALT-004 — Scopes discovery **`[P]`**

**Tool:** `get_alert_scopes`

**Purpose:** Canonical values for **`get_alert_metrics`**.

---

## TC-ALT-005 — Metrics for scope **`[P]`**

Pick `scope = "interaction"` **if** scopes list supports it:

```json
{
  "projectId": "{PROJECT_READ}",
  "scope": "{SCOPE_FROM_TC_ALT_004}"
}
```

---

## TC-ALT-005b — Regression: omit scope (simulate wrong client)

Craft raw HTTP omitting **`scope`** (optional deep test outside MCP)—expected **400** validates server contract MCP depends on.

---

## TC-ALT-006 — Severities enum

```json
{ "projectId": "{PROJECT_READ}" }
```
**Tool:** `get_alert_severities`

---

## TC-ALT-007 — Evaluation history detail

```json
{
  "projectId": "{PROJECT_READ}",
  "alertId": "{ALERT_ID}"
}
```

---

## TC-ALT-008 — Negative alert id sentinel

`synthetic-absent-alert` or random UUID string — capture **404** vs **403**.

---

## TC-ALT-009 — Notification channels

```json
{ "projectId": "{PROJECT_READ}" }
```

