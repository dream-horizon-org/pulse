# Session sampling rules — Web SDK vs Android (parity tracker)

Audience: engineers and **Pulse dashboard** authors who edit `sampling.rules[]` in the active SDK config.  
Code: `pulse-web-otel/src/utils/session-sampling-rate.ts` (`sessionRuleMatchesWeb`, `resolveSessionSamplingRate`).

---

## What session sampling rules do (shared)

- **`sampling.default.sessionSampleRate`**: fallback fraction \([0,1]\) for “keep this session’s export bucket.”
- **`sampling.rules[]`**: ordered list. **First rule** where the client is in **`sdks`** **and** the rule **matches** wins; otherwise use **default**.
- That rate feeds **one random draw per SDK init** on Web (`ExportSamplingGate`), same *idea* as Android export-time session sampling (`PulseSessionConfigParser` + `PulseSamplingSignalProcessors`).

Backend stores rule **`name`** as enum values: `os_version`, `app_version`, `country`, `platform`, `state`, `device`, `network` (`backend/server/.../SamplingRule.java`, `rules.java`). JSON uses lowercase names (e.g. `"platform"`).

---

## Android behavior (source of truth)

- **`PulseSessionSamplingRule.matches(Context)`** — `name` selects a **device attribute**; **`value`** is matched in the Android stack against live **Context**-backed values (not “regex on full UA string” as the generic story).
- Parser: **`PulseSessionConfigParser`** — first rule with `currentSdkName in sdks && matches(context)`.

Reference: `pulse-android-otel/pulse-sampling/` (`PulseSessionConfigParser.kt`, `PulseSessionSamplingRule`).

---

## Web SDK — what is implemented today

| `rule.name` (from server) | Behavior on Web |
|---------------------------|-----------------|
| `""` or `UNKNOWN` | Matches **every** session (for rows whose `sdks` include the current SDK). |
| **`platform`** (case-insensitive) | Matches Pulse RUM **`platform`** = **`web`** (constant `PulseWebSemconv.FixedValue.PLATFORM_WEB`). **`rule.value`** is a **RegExp** against that literal; invalid regex → **literal string equality** fallback. |
| **`app_version`** | Matches **`service.version`** passed at SDK init (same default as resource: `PulseWebConfig.serviceVersion` or **`0.0.0`**). **`rule.value`**: RegExp on that string; invalid regex → literal equality. |
| **`os_version`** | Matches **`os.name` + space + `os.version`** from `parseUserAgent()` (Client Hints path may leave `osVersion` empty — then mostly OS name / UA-derived fields); if both empty, falls back to **`navigator.userAgent`**. |
| **`network`** | Matches **`${network.connection.type}/${network.connection.effectiveType}`** with **`unknown`** defaults (same snapshot idea as global attrs on signals). |
| **`device`** | Matches parsed **`device.type`**: `desktop` \| `mobile` \| `tablet` (`parseUserAgent()`). |
| **Other names** (e.g. `country`, `state`, custom) | **Legacy:** `rule.value` as **RegExp on `navigator.userAgent`** (invalid regex → no match; missing `navigator` → no match). |

---

## Gap list — not in parity with Android (dashboard / product implications)

| Server `name` | Android | Web today | Risk if dashboard assumes Android semantics |
|---------------|---------|-----------|-----------------------------------------------|
| `platform` | Native OS / product platform from Context | **RUM `platform` = `web` only** (implemented). Rules meant for “Android vs iOS” must use **different `sdks`** rows or **different `value`** patterns for `pulse_web_js`. | Low if documented; `value: "web"` targets browsers. |
| `os_version` | OS version from device | **Structured** `osName` + `osVersion` from UA / hints (not identical to Android OS APIs; Client Hints may omit version until enriched). | Low–medium — author patterns against the combined string or UA fallback. |
| `app_version` | App build from Context | **`service.version`** at init (aligned with OTEL resource). | Low for release semver; not the native “versionCode” integer story. |
| `network` | Network class / type from Android APIs | **`navigator.connection`** `type` + `effectiveType` (browser support varies). | Low where API exists; **unknown/unknown** when absent. |
| `device` | Device model / class | **Coarse** `desktop` / `mobile` / `tablet` only (no hardware model string). | Medium if rules assume **model** regex. |
| `country` / `state` | Locale / geo from Context | **UA regex legacy** only; no IP-geo or structured locale mapping in the matcher. | High for geo-targeted sampling. |

**Remaining gaps (short):** **`country` / `state`** (and any custom server names) still use **UA-only** legacy matching until a defined browser/geo mapping exists.

---

## Pulse Web dashboard guidance

1. **For `pulse_web_js` rows**, treat **`platform`** as **Pulse RUM platform** = **`web`**; use `value` patterns such as `^web$` or `web`.
2. **`app_version` / `os_version` / `network` / `device`** use the mappings in the table above (not raw Android Context keys). **`country` / `state`** still require **UA-shaped** `value` patterns unless/until implemented.
3. Prefer **`UNKNOWN` / empty `name`** + explicit `sdks` + `sessionSampleRate` when you only need “this % for all web sessions” without device slicing.

---

## Related docs

- `WEB-SDK-IMPLEMENTATION-M1.md` — flow, `ExportSamplingGate`, session-rule note.
- `web-sdk-plan/WEB-SDK-AGENT-CONTEXT.md` — Android parity ground rule.
- `web-sdk-plan/v1/01-foundation/sdk-config.md` — older sketch of device-attribute matching (not all implemented in `session-sampling-rate.ts`).

---

## Changelog

| Date | Change |
|------|--------|
| 2026-04 | Document parity gaps; implement **`platform` → `web`** matching in `sessionRuleMatchesWeb`. |
| 2026-04 | Add **`app_version`** (`service.version`), **`os_version`** (parsed OS), **`network`** (`navigator.connection`), **`device`** (parsed `device.type`); narrow gap list to **country/state/geo**. |
