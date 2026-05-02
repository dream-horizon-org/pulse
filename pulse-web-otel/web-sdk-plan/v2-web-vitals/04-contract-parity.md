# Contract parity: Web Vitals vs other Pulse SDKs

**Status:** Planning artifact (Phase E).  
**Related:** [ADR-web-vitals.md](./ADR-web-vitals.md).

---

## 1. Purpose

Mobile and RN SDKs do **not** emit browser **Core Web Vitals** (LCP, INP, CLS). Parity means:

1. **Same envelope** for cross-platform concepts: `pulse.type`, `platform`, `session.id`, `project.id`, `rum.sdk.name`, consent, remote feature gates, before-send, sampling.
2. **Explicit web-only extensions** documented here so backend and analytics do not expect these attributes from Android/iOS.

---

## 2. Envelope (must match)

| Concept | Web SDK | Android (`PulseAttributes`) | Notes |
|---------|---------|----------------------------|--------|
| `pulse.type` | `web_vital` (new) | N/A for vitals | New value for Web-only perf signals; not added to mobile emitters. |
| `platform` | `"web"` (`PulseWebSemconv.FixedValue.PLATFORM_WEB`) | `os.name` materialization | Already consistent per platform. |
| Session | `session.id` | Same key | Web [`SessionProvider`](../../src/session/) |
| Project | `project.id` from API key | Same | Resource / global attrs |
| SDK name | `rum.sdk.name` / `pulse_web_js` | `pulse_android_java`, etc. | Distinct per surface |
| Consent | `dataCollectionState` | Android `dataCollectionState` | No signals if denied |
| Remote kill switch | `features[].featureName === "web_vitals"` | Feature rows per SDK | Backend must add [`Features.web_vitals`](../../../backend/server/src/main/java/org/dreamhorizon/pulseserver/service/configs/models/Features.java) — see [03-touchpoints-matrix.md](./03-touchpoints-matrix.md) |

---

## 3. Closest mobile analogues (informational, not same metric)

| Web vital | Android / iOS analogue | Relationship |
|-----------|------------------------|--------------|
| LCP / paint timing | `screen_load`, cold/warm spans | Different definitions — **do not** merge in one KPI without product spec |
| INP / responsiveness | Touch / interaction spans, `app.jank.*` | Different measurement model |
| CLS | Nothing identical | **Web-only** |

---

## 4. Allowed drift — web-only attributes

The following are **valid on Web** and **optional or absent** on mobile:

| Attribute | Description |
|-----------|-------------|
| `web_vital.name` | `LCP`, `INP`, `CLS`, `FID`, … |
| `web_vital.rating` | `good` / `needs-improvement` / `poor` when sourced from `web-vitals` |
| Navigation / URL | `url.path`, `page.url` — already in [`PulseWebSemconv`](../../src/semconv.ts) |
| `navigationType` | From Performance API / `web-vitals` where exposed — exact key chosen at implementation |

**Android `PulseTypeValues`** ([reference](../../../pulse-android-otel/pulse-semconv/src/main/java/com/pulse/semconv/PulseAttributes.kt)) does not need a `WEB_VITAL` constant for runtime emission; optionally add a **documented** constant for shared query tooling /.proto parity — product decision.

---

## 5. AI agent and analytics

Templates that inject `PulseType` filters (e.g. interaction queries in `pulse_ai`) should **not** assume `web_vital` unless the tool explicitly targets web performance. New tools or filters can opt in using `PulseType = web_vital` once metrics land in ClickHouse.

---

## 6. Change control

Any change to metric names, `pulse.type` value, or required attributes must:

1. Update this document.
2. Update [ADR-web-vitals.md](./ADR-web-vitals.md) if architectural.
3. Run web SDK contract tests per [`pulse-web-sdk-sanity`](../../../.cursor/skills/pulse-web-sdk-sanity/SKILL.md).
