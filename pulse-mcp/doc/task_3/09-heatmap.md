# Heatmap (**`[P]`** — 403 semantics differ from REST throw path)

Unlike most tools that reject with thrown Axios errors, **`get_heatmap_data`** **catch**es Axios errors and returns **text JSON** containing **`httpStatus`** + **`pulseError`**.

Correlation test: **`get_active_sdk_config`** must show **`heatmap`** feature sampling (`sessionSampleRate > 0` per README) or expect **403** not misread as MCP bug.

Acquire `{SCREEN_FROM_UI_OR_SEED}` that has heatmaps in QA.

---

## TC-HM-001 — Baseline with minimal filters

```json
{
  "projectId": "{PROJECT_READ}",
  "screenName": "{SCREEN}",
  "from": "{WINDOW_START_ISO}",
  "to": "{WINDOW_END_ISO}"
}
```

**Expect:** Structured heatmap geometry payload **or** text JSON with **`httpStatus`**. Evaluator verifies **consistent** UX—tool still returns MCP `content:text`.

---

## TC-HM-002 — Dimensions

```json
{
  "projectId": "{PROJECT_READ}",
  "screenName": "{SCREEN}",
  "from": "{WINDOW_START_ISO}",
  "to": "{WINDOW_END_ISO}",
  "platform": "android",
  "appVersion": "{SEMVER}",
  "breakpoint": "{OPTION}",
  "geographical_region": "(verify schema—param maps to geographicalRegion arg)"
}
```

Note: MCP maps **`geographicalRegion`** argument → query **`geographical_region`**.

---

## TC-HM-003 — Cross-check disabled feature path

Before call: **`get_active_sdk_config`** confirming heatmaps off OR sample rate 0 → run TC-HM-001:

**Expect:** **`httpStatus`** **403** in returned JSON explaining server restriction (wording varies).

---

## TC-HM-004 — Narrow window empty vs legit error

Ultra-narrow timestamps with feature ON — distinguishing **403** vs **empty heatmap blob** visually / via parsing.

---

## TC-HM-005 — Forbidden project **`[P]`**

Use `{PROJECT_NO_ACCESS}` → expect **403** JSON text or equivalent.

---

## TC-HM-006 — Garbage screen label

Unused screen name `"__no_such_screen__"` with feature enabled — differentiate **404/400 vs empty**.
