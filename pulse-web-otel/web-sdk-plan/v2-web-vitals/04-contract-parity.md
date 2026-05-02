# Contract parity: Web Vitals vs other Pulse SDKs

**Status:** Planning artifact (Phase E).  
**Related:** [ADR-web-vitals.md](./ADR-web-vitals.md), [PLAN-B-logs-events.md](./PLAN-B-logs-events.md).

---

## 1. Purpose

Mobile and RN SDKs do **not** emit browser **Core Web Vitals**. Parity means:

1. **Same envelope** for cross-platform concepts: `pulse.type`, `platform`, `session.id`, `project.id`, `rum.sdk.name`, consent, remote feature gates, before-send, sampling.
2. **Explicit web-only extensions** documented here.

---

## 2. Envelope (must match)

| Concept | Web SDK | Android (`PulseAttributes`) | Notes |
|---------|---------|----------------------------|--------|
| `pulse.type` | `web_vital` | N/A for vitals | On **log** records for Plan B. |
| `platform` | `"web"` | `os.name` materialization | Consistent. |
| Session | `session.id` | Same key | Global log processor stamps logs. |
| Project | `project.id` | Same | Resource / global attrs |
| SDK name | `rum.sdk.name` / `pulse_web_js` | Distinct per surface | |
| Consent | `dataCollectionState` | Android parity | |
| Remote kill switch | `features[].featureName === "web_vitals"` | | Backend `Features.web_vitals` |

---

## 3. Closest mobile analogues (informational)

| Web vital | Mobile analogue | Relationship |
|-----------|-----------------|---------------|
| LCP / paint | `screen_load`, etc. | Different definitions |
| INP | Touch / jank signals | Different model |
| CLS | — | **Web-only** |

---

## 4. Allowed drift — web-only log attributes (Plan B)

| Attribute | Description |
|-----------|-------------|
| `web_vital.name` | `LCP`, `INP`, `CLS`, `FID`, `FCP`, … |
| `web_vital.value` | Numeric in OTLP; in ClickHouse `otel_logs` map values are **strings** — use **`toFloat64(Attributes['web_vital.value'])`** for quantiles. |
| `web_vital.rating` | `good` / `needs-improvement` / `poor` |
| `web_vital.navigation_type` | Present only when defined — **omit key** if undefined (no null / empty / `"undefined"`). |
| Log body | `web_vital` (stable string) | Distinct from session/click bodies. |
| URL / screen | `url.path`, `page.url`, `screen.name` | Stamped at **emit time** by global processor — SPA caveat: [PLAN-B SPA section](./PLAN-B-logs-events.md). |

---

## 5. AI agent and analytics

Opt-in filters on `pulse.type = web_vital` and `LogBody` / attributes; data lives in **`otel_logs`**, not `otel_metrics`, for Plan B.

---

## 6. Change control

1. Update this document when attrs change.  
2. Update ADR if architectural.  
3. Run web SDK contract tests per [`pulse-web-sdk-sanity`](../../../.cursor/skills/pulse-web-sdk-sanity/SKILL.md).
