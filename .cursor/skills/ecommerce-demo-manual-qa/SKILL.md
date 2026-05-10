---
name: ecommerce-demo-manual-qa
description: Designs step-by-step manual test scenarios for the Pulse ecommerce demo, maps UI actions to SDK install paths, PulseFeature gates, and pulse.type signals, and keeps context current via a refresh protocol. Use when manually QAing pulse-web-otel in examples/ecommerce-demo, explaining which instrumentation fires for a route or button, or writing human repro steps for a new Web SDK instrumentation.
---

# Ecommerce demo — manual QA and scenario design

## Scope

- **Primary app:** `pulse-web-otel/examples/ecommerce-demo/`
- **SDK sources of truth:** `pulse-web-otel/src/instrumentation-registry.ts`, `pulse-web-otel/src/sdk.ts` (`installInstrumentations`), `pulse-web-otel/src/types/remote-config.ts` (`PulseFeature`), `pulse-web-otel/src/semconv.ts` (`PulseType`, log bodies)
- **Demo routing and knobs:** `examples/ecommerce-demo/src/App.tsx`, `main.tsx`, `public/pulse-sdk-config.mock*.json`

This skill does **not** replace E2E or `pulse-web-sdk-sanity`; it gives **human-run** checklists and **accurate** “what fires when” explanations.

## Before answering (always)

1. Read **`pulse-web-otel/examples/ecommerce-demo/DEMO-QA-MAP.md`** — maintained map of routes, demo controls, and signal paths.
2. Re-read the **current** `installInstrumentations` + `InstrumentationRegistry` code paths (registry + any direct `registerAndInstall` in `sdk.ts`). **Do not** assume all `InstrumentationKeys` are installed in `installAll()` — some ship later (see inline `M3` / TODO comments in registry).
3. If the question is “what `pulse.type` for X?” open **`src/semconv.ts`** and the specific instrumentation under `src/instrumentations/`.

## How Pulse “triggers” an instrumentation (chain)

1. **Consent** — `PulseDataCollectionConsent.ALLOWED` in `PulseProvider` config (`App.tsx` / query `?pulse_consent=`) or SDK never initializes; no instrumentations.
2. **Remote config** — `SdkConfigFetcher` loads features for `pulse_web_js`. `FeatureGate.isEnabled(PulseFeature.*)` must be true for that key (and `config.instrumentations[InstrumentationKeys.*]` if present).
3. **Registry** — `InstrumentationRegistry.shouldInstall` combines gate + per-key config. `registerAndInstall` calls `instrumentation.install(sdk)`.
4. **Extra wiring** — `sdk.ts` constructs `InteractionInstrumentation`, runs `registry.installAll()`, then **`registerAndInstall(..., InstrumentationKeys.INTERACTIONS)`** so the interaction module installs **after** session/web-vitals entries inside `installAll()`. `useRouterTracking` only calls **`Pulse.setScreenName`** (stamps `screen.name` on later signals; it is **not** a separate navigation log unless Navigation instrumentation is installed and spec says otherwise).
5. **`Pulse.trackEvent`** — can emit **two** paths when enabled: (a) `PulseFeature.CUSTOM_EVENTS` → log with `pulse.type` **custom_event**; (b) if data collection allowed → `interactionInstrumentation.trackEvent` (interaction pipeline / feature gate **interaction**). Same click may satisfy both.

When describing a scenario, name **consent → feature(s) → class → pulse.type** where applicable (`trackEvent` may need **custom_events** and **interaction** called out separately).

## Manual test scenario template

Use this for any instrumentation or demo flow. Output Markdown the human can follow in a browser.

```markdown
## Scenario: <short name>

**Goal:** <one line>

**Preconditions**
- [ ] Demo: from `pulse-web-otel/`, `yarn demo` (runs `ecommerce-demo` Vite dev server; or `yarn workspace ecommerce-demo dev` from repo root)
- [ ] Consent: allowed (default) / or `?pulse_consent=denied` for negative
- [ ] Config: <mock JSON path or `VITE_PULSE_MOCK_*`> — cite `DEMO-QA-MAP.md` or `MANUAL-*.md`
- [ ] Feature gate: <PulseFeature name> on in active SDK config

**Steps**
1. <URL + action>
2. <…>

**Expected telemetry**
- `pulse.type`: <exact value from semconv>
- Other attrs: <session.id, screen.name, …>

**How to verify**
- Shift+P → **PulseDebugPanel** (dev): OTLP calls list
- Network: `/v1/logs` (json/protobuf per env)

**Cleanup / reload** — if testing init-once or shutdown: note reload or `await window.Pulse.shutdown()` (demo may expose the singleton on `window` for debugging)
```

For Web Vitals specifics, point to **`MANUAL-WEB-VITALS-DEMO.md`**. For lifecycle / disk / shutdown, point to **`MANUAL-PULSE-LIFECYCLE.md`**.

## Self-heal — keep context from going stale

Automation cannot rewrite this skill file by itself. **Procedural self-heal:**

1. After **meaningful changes** to the demo (`App.tsx` routes, new pages, new env/query knobs, mock JSON) or to **registry / sdk install order / new instrumentation class**:
   - Update **`pulse-web-otel/examples/ecommerce-demo/DEMO-QA-MAP.md`** (routes, table rows, “not yet shipped” notes).
2. From **`pulse-web-otel/`**, refresh graph context (per project rules):  
   `graphify update . --no-viz`  
   For broader merge readiness, optionally sync **`pulse-web-otel/graphify-out/GRAPH_REPORT.md`** and CI logs per [pulse-web-sdk-sanity](../pulse-web-sdk-sanity/SKILL.md).
3. Append **one line** to **`pulse-web-otel/examples/ecommerce-demo/QA-CONTEXT-REFRESH-LOG.md`**: ISO date, what changed (demo vs SDK), optional PR/link.

Optional: run **`scripts/refresh-demo-context.sh`** in this skill folder to print a quick inventory (routes + grep anchors); use output to edit `DEMO-QA-MAP.md`.

### New demo app (not ecommerce)

If the workspace adds **`examples/<other-app>/`** wired to `@dreamhorizonorg/pulse-web`:

- Add a **`DEMO-QA-MAP.md`** (or `APP-QA-MAP.md`) in that app with the same sections: routes, SDK entry, env/query table, “action → feature → signal” table.
- Add a one-line pointer in **`DEMO-QA-MAP.md`** (ecommerce) or in this skill’s [reference.md](reference.md) under “Other apps” so the next session discovers it.

## Related project skills

- [pulse-web-sdk-sanity](../pulse-web-sdk-sanity/SKILL.md) — test ladder, merge gate
- [web-sdk-instrumentation-lifecycle](../web-sdk-instrumentation-lifecycle/SKILL.md) — new instrumentation docs + Phase 5 gate
- [web-sdk-instrumentation-e2e-from-design](../web-sdk-instrumentation-e2e-from-design/SKILL.md) — E2E matrix from design docs

## Additional detail

- Deeper tables and file anchors: [reference.md](reference.md)
