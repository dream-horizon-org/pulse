# SDK configuration

**Important:** **`get_active_sdk_config`** uses **`raw: true`** **`get`** — response envelope may include `{ data }` nesting **outside** standardized unwrap path used by default `get()` calls. Regression tests comparing to other tools MUST allow envelope differences.

---

## TC-CFG-001 — Active SDK config (**raw`)

```json
{ "projectId": "{PROJECT_READ}" }
```
**Tool:** `get_active_sdk_config`

Capture fields relevant to **`09-heatmap.md`**: sampling, heatmap-related feature gates.

---

## TC-CFG-002 — Enumerate configs

```json
{ "projectId": "{PROJECT_READ}" }
```
**Tool:** `list_sdk_configs`

Capture `{CFG_VERSION_NUMBER}` integer from list item.

---

## TC-CFG-003 — Specific version retrieval

```json
{
  "projectId": "{PROJECT_READ}",
  "version": {CFG_VERSION_NUMBER}
}
```
**Tool:** `get_sdk_config`

**Negative:** `version: 999999` → explicit HTTP error surfaced as MCP failure text.

---

## TC-CFG-004 — Rules + features blueprint

```json
{ "projectId": "{PROJECT_READ}" }
```
**Tool:** `get_sdk_rules_features`

---

## TC-CFG-005 — Forbidden project

Run TC-CFG-001 with `{PROJECT_NO_ACCESS}`.
