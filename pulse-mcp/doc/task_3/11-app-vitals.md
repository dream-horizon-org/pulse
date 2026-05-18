# App Vitals (distribution on **`EXCEPTIONS`** / **`LOGS`** **`[P]`**)

Universal facts:

- **List tools** capped **`limit`** 1–**100**, default **10** (`MAX_LIST_LIMIT` / `DEFAULT_LIST_LIMIT`).
- Responses often JSON with **`ok`**, **`empty`**, **`hint`** on success-but-no-rows (**do not confuse with tool failure**).
- Failures serialize **`{ ok: false, error: "..." }`** (403 permission text includes **can_view** hint where applicable).

**Golden chain (record once per tenant):**

1. `list_app_vitals_crash_issues` → copy **`group_id`** (field index from `fields`/`rows`; record mapping).
2. `get_app_vitals_issue_summary` same window.
3. `get_app_vitals_issue_trend` (**all** **`trendView`** values).
4. `get_app_vitals_issue_stack_traces`.
5. `get_app_vitals_issue_screen_breakdown`.

Time parsing: **`resolveTimeRange`** accepts ISO **with** **`T`/Z**, or **`YYYY-MM-DD HH:mm:ss`** (**interpreted UTC** via helper). Omitting times → **rolling last 7 days**.

---

## Section A — List tools parity

### TC-AV-L01 — Crash list default window

```json
{
  "projectId": "{PROJECT_READ}",
  "limit": 10
}
```
**Tool:** `list_app_vitals_crash_issues`

### TC-AV-L02 — ANR default

Same shell — **`list_app_vitals_anr_issues`**

### TC-AV-L03 — Non-fatal default

**`list_app_vitals_nonfatal_issues`**

### TC-AV-L04 — Max limit boundary

All three lists with **`"limit": 100`** — expect acceptance.

### TC-AV-L05 — Narrowing filters (**`all` sentinel**)

```json
{
  "projectId": "{PROJECT_READ}",
  "platform": "ios",
  "appVersion": "{REAL_OR_all}",
  "limit": 25
}
```

Repeat with dimensional filters `osVersion`, `device`, `networkProvider`, `state`, plus optional **`screenName`**.

---

## Section B — Session denominator

### TC-AV-S01 — **`get_app_vitals_user_session_totals`** baseline

Defaults (omit start/end):

```json
{ "projectId": "{PROJECT_READ}" }
```

**Expect:** **`unique_users`**, **`unique_sessions`** fields in distribution output or empty hint about missing **`session.start`**.

---

## Section C — Issue detail (**needs real `groupId`**)

Assume `{GROUP_CRASH}`, `{GROUP_ANR}`, `{GROUP_NF}` from lists.

### TC-AV-D01 — Summary crash

```json
{
  "projectId": "{PROJECT_READ}",
  "groupId": "{GROUP_CRASH}"
}
```
**Tool:** `get_app_vitals_issue_summary`

### TC-AV-D02 — Trend aggregated

```json
{
  "projectId": "{PROJECT_READ}",
  "groupId": "{GROUP_CRASH}",
  "trendView": "aggregated"
}
```
**Expect:** Wrapped JSON includes **`bucketSize`** for debugging.

### TC-AV-D03 — Trend **`appVersion`**

```json
{ "projectId": "...", "groupId": "...", "trendView": "appVersion", "startTime": "...", "endTime": "..." }
```

### TC-AV-D04 — Trend **`os`**

```json
{ "...", "trendView": "os" }
```

### TC-AV-D05 — Stack traces (**limit edges**)

`limit: 1`, `limit: 50`

### TC-AV-D06 — Screen breakdown

Default window.

---

## Section D — First / last **`[P]`** — **`eventName` semantics**

Implementation: **omitting** **`eventName`** forces **`PulseType = non_fatal`**. Crash/ANR batch calls **must** pass **`device.crash`** / **`device.anr`** explicitly.

### TC-AV-F01 — Crash IDs with **`eventName: device.crash`**

```json
{
  "projectId": "{PROJECT_READ}",
  "groupIds": ["{GROUP_CRASH}"],
  "eventName": "device.crash"
}
```

### TC-AV-F02 — ANR **`device.anr`**

### TC-AV-F03 — Wrong pairing (**high severity false-negative risk**)

Call with **`GROUP_CRASH`** but **omit **`eventName`****.

**Expect:** Empty / filtered-out rows — evaluator records **severity**: agent could falsely conclude timelines missing).

### TC-AV-F04 — Non-fatal path

`groupIds`: non-fatal id list, **omit** `eventName` — expect matches.

### TC-AV-F05 — Boundary array sizes

Min **1**, max **50** IDs — Zod rejects **0** ids and **`>50`**.

Synthetic: `"groupIds": [ ... 51 entries ... ]"` must fail MCP validation without HTTP spam.

---

## Section E — Malformed temporal inputs

Provide **half-pair**: only **`startTime`** without **`endTime`** (`resolveTimeRange` falls through to defaults—**still legal** unlike sessions tool). Evaluate whether this is desirable vs confusing; snapshot behavior.

Garbage strings for times → default range fallback (**document**):

```json
{ "projectId": "{PROJECT_READ}", "startTime": "not-a-real-time", "endTime": "" }
```
(omit or empty second—capture actual MCP resolution)

---

## Section F — Permission regression

Reuse `{PROJECT_NO_ACCESS}` against **`list_app_vitals_crash_issues`**.

Expect **`ok:false`** with **403** messaging or equivalent — never cross-tenant data rows.
