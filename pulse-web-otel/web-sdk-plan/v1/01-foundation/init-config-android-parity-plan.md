# Web init config — Android parity plan (`beforeSendData`, `logLevel`, `resource`)

**Status:** Planning (no implementation commitment in this doc).  
**Audience:** Web SDK implementers, docs, integrators comparing Android and web.  
**Related:** [`before-send-web-android-parity.md`](./before-send-web-android-parity.md) (semantics and pipeline; this doc focuses on **public config shape** and **logging / resource gaps**).

---

## 1. Why this exists

Pulse positions mobile and web SDKs as **one product surface**. Integrators reading `PulseSDK.initialize` on Android expect the same **named knobs** on `PulseWeb.start` / `PulseWebConfig` where the platform allows parity.

**Export-time hooks:** Web `PulseWebConfig` uses **`beforeSendData`** (same key name as Android). **Logging / resource** still have gaps documented below (`logLevel`, resource injection). Web also carried a legacy **`debugLogRecordLifecycle`** boolean; that flag is **dropped** in favor of **`logLevel`** only.

---

## 2. Current snapshot

### 2.1 Android (`PulseSDK.initialize`)

Source: `pulse-android-otel/pulse-android-sdk/src/main/java/com/pulse/android/sdk/PulseSDK.kt`.

| Concern | Android API |
|--------|----------------|
| Export-time scrub / drop | `beforeSendData: PulseBeforeSendData?` — class with `beforeSend`, `beforeSendSpan`, `beforeSendLog`, `beforeSendMetric` (`pulse-android-api/.../PulseBeforeSendData.kt`). |
| SDK internal logging | `logLevel: PulseLogLevel = PulseLogLevel.NONE` — `pulse-utils/.../PulseLogLevel.kt` (VERBOSE … NONE); `PulseLogger` gates Android `Log.*` by level. |
| Resource | `resource: (ResourceBuilder.() -> Unit)?` — optional block on OTel `ResourceBuilder` before SDK finishes wiring. |
| Extra attrs | `globalAttributes: (() -> Attributes)?` — merged into telemetry as configured by internal wiring. |

Android has **no** second boolean for “lifecycle debug”; integrators use **`logLevel`** only. Web will match that model.

### 2.2 Web (`PulseWebConfig` / `PulseWeb.start`)

Source: `pulse-web-otel/src/types/config.ts`, `src/before-send.ts`, `src/resource.ts`, `src/sdk.ts`.

| Concern | Web today |
|--------|-----------|
| Export-time scrub / drop | Config field **`beforeSendData?: PulseWebBeforeSendConfig`** — same **key** as Android. Object callbacks use Android method names (`beforeSend`, `beforeSendSpan`, …). Semantics → `before-send-web-android-parity.md`. |
| SDK internal logging | No `logLevel`. Legacy **`debugLogRecordLifecycle?: boolean`** toggles log-record lifecycle processors (`log-record-lifecycle-debug-processor.ts`, `wrap-log-exporter-lifecycle-debug.ts`). Several modules use raw **`console.log` / `console.warn`** (e.g. `remote-config.ts`, `export-sampling-gate.ts`) with no shared level policy. **Plan:** remove the boolean; add **`logLevel: PulseLogLevel`** and **`PulseWebLogger`** only. |
| Resource | **`buildResource(config, osVersion)`** only — fixed attribute set from config + UA + browser APIs. No user hook to extend or override the built `Resource`. |
| Extra attrs | `globalAttributes?: Record<...>` + `PulseGlobalAttributesProcessor` (already documented elsewhere). |

### 2.3 Cross-SDK precedent (React Native)

`pulse-react-native-otel` already exposes **`PulseLogLevel`** (numeric enum matching Android’s ordering contract) and **`logLevel`** on init config, with `PulseLogger.setLevel`. Web should **reuse the same enum values and comparison semantics** as RN for a single mental model across JS-hosted SDKs.

---

## 3. Goals and non-goals

**Goals**

1. **Naming parity:** Web public config exposes **`beforeSendData`** only (same init key as Android). No legacy **`beforeSend`** config property.
2. **Logging parity:** Add **`logLevel?: PulseLogLevel`** (default **`NONE`**), implement internal **`PulseWebLogger`** with the **same numeric ordering and gating rules** as React Native (`PulseLogLevel.ts`, `PulseLogger.ts`) and Android (`PulseLogLevel`, `PulseLogger`). **Remove** **`debugLogRecordLifecycle`** and all ad-hoc `console.*` in SDK code paths that are not routed through the logger (see §5).
3. **Resource parity:** Allow integrators to add **custom resource attributes** (`deployment.environment`, `service.namespace`, etc.) via a **plain config object**, with **Pulse-owned keys winning** on conflicts — behavioral alignment with Android’s “user `ResourceBuilder` first, then SDK defaults overwrite” ordering, **without** exposing OTel `Resource` on the public `PulseWebConfig` surface (see §6.2).

**Non-goals (this plan)**

- Changing **OTLP payload** semantics or `beforeSend` **order/drop rules** (already specified in the dedicated before-send doc).
- Moving export work off the main thread (separate performance / worker initiative).
- Parity for Android-only `instrumentations: (InstrumentationConfiguration.() -> Unit)?` (web uses a different `InstrumentationConfig` shape by design).
- **Public `Resource` / `Resource.merge` callbacks on `PulseWebConfig` for v1** — couples the app to the SDK’s **`@opentelemetry/resources` major**; structural mismatches if the host app uses another OTel version (§6.2). Defer to v2 only if a concrete customer need appears.

---

## 4. Track A — `beforeSendData` on `PulseWebConfig` (locked)

### 4.1 Decision

- **Single init key:** `PulseWebConfig.beforeSendData?: PulseWebBeforeSendConfig` — matches Android `initialize(..., beforeSendData = null)`.
- **No** top-level **`beforeSend`** property on `PulseWebConfig` (removes confusion with the **generic hook** name `beforeSend` inside `PulseWebBeforeSendCallbacks`, which stays aligned with Android’s `PulseBeforeSendData.beforeSend(...)`).

### 4.2 Wiring (reference)

`validateBeforeSendConfig(config.beforeSendData)` in **`validateConfig`** → `resolveBeforeSend(config.beforeSendData)` in **`sdk.ts`** → `ExporterConfig.beforeSendData` in **`createProviders`** (`exporters.ts`) → `BeforeSend*Exporter` in **`before-send-exporters.ts`**. **`ResolvedBeforeSend`** still exposes `beforeSend` / `beforeSendSpan` / … as **resolved hook fields** (not config keys).

### 4.3 Breaking migration

Integrations that previously passed **`beforeSend`** on `PulseWebConfig` must rename to **`beforeSendData`** (value shape unchanged).

### 4.4 Tests

- Invalid `beforeSendData` rejected at `PulseWeb.start()` with **`[PulseWeb] beforeSendData.*`** messages.
- Valid function and callback object accepted.

---

## 5. Track B — `PulseLogLevel` and Android-style logging (single knob)

### 5.1 Problem

- Android uses **`PulseLogLevel`** + **`PulseLogger`** for all SDK-internal diagnostics; no parallel “debug this one subsystem” flag on init.
- Web mixes a **legacy boolean** (`debugLogRecordLifecycle`) with **raw `console.*`**, so verbosity is inconsistent and the public surface does not match Android or React Native.

### 5.2 Decision: one field, no legacy boolean

- **`PulseWebConfig` exposes only `logLevel?: PulseLogLevel`** (default **`NONE`**). **`debugLogRecordLifecycle` is removed** (not deprecated alongside `logLevel` — eliminated to avoid two sources of truth).
- **Migration for anyone who used the boolean:** use **`logLevel: PulseLogLevel.DEBUG`** (or **`VERBOSE`** if lifecycle lines are classified at VERBOSE — pick one level in implementation and document it in `WEB-SDK-AGENT-CONTEXT.md`).

### 5.3 Implementation shape (mirror Android / RN)

1. **`src/pulse-log-level.ts`** — Export **`PulseLogLevel`** enum with **identical numeric values** to `pulse-react-native-otel/src/PulseLogLevel.ts` (`VERBOSE = 0` … `NONE = 5`). Same ordering contract as Android Kotlin enum ordinals for cross-platform docs.
2. **`src/pulse-web-logger.ts`** (internal module; optionally re-export `PulseLogLevel` from package `index` for integrators):
   - **`setLevel(level: PulseLogLevel)`** at **`PulseWeb.start`** from `config.logLevel ?? NONE` (and on shutdown if you need to reset tests).
   - Methods **`verbose` / `debug` / `info` / `warn` / `error`** (signatures aligned with RN `PulseLogger`: lazy string bodies where useful to avoid work when level filters out).
   - **Gating:** same comparison pattern as RN: emit only if `currentLevel <= threshold` for that severity (lower number = more verbose).
3. **Init wiring:** call **`PulseWebLogger.setLevel(...)`** once at startup from config (same lifecycle point as Android’s `PulseLogger.logLevel = logLevel` in `PulseSDKInternal.initialize`).
4. **Replace raw `console.*`** in SDK sources with **`PulseWebLogger`** calls at appropriate levels, including:
   - `remote-config.ts`
   - `export-sampling-gate.ts`
   - `sdk.ts` (any diagnostic paths)
   - any other `console.*` under `src/` used for SDK diagnostics (search `console.` in `pulse-web-otel/src`).
5. **Log record lifecycle:** remove the **config-driven branch** that installs `LogRecordLifecycleDebugProcessor` / `wrapLogExporterLifecycleDebug` based on `debugLogRecordLifecycle`. **Either:**
   - **5a (preferred):** keep those helpers **internal-only**, registered only when `logLevel <= DEBUG` (or `VERBOSE`) inside the logger-aware bootstrap so OTLP lifecycle noise is one class of **DEBUG** diagnostic; **or**
   - **5b:** inline equivalent logging inside existing processors/exporters using **`PulseWebLogger.debug`** and delete the dedicated wrapper files if redundant.
   Goal: lifecycle traces appear **only** when level allows — **no separate flag**.
6. **Tests:** update fixtures that passed `debugLogRecordLifecycle: true` to pass **`logLevel: PulseLogLevel.DEBUG`** (or VERBOSE). Remove assertions on the old property.
7. **Breaking change:** document in changelog: removed `debugLogRecordLifecycle`; use `logLevel`.

### 5.4 Level guidelines (assign in code review)

| Area | Suggested level |
|------|-----------------|
| Sdk config fetch phases, cache hits | **DEBUG** |
| Export sampling decisions, batch sizes | **DEBUG** |
| Log record lifecycle (processor → exporter) | **DEBUG** or **VERBOSE** (choose one; VERBOSE if very chatty) |
| Recoverable oddities | **WARN** |
| Export / init hard failures | **ERROR** |
| Future init deprecations (if any) | **WARN** via `PulseWebLogger`, once per process when applicable |

The **severity** column above is which **`PulseWebLogger.*` method** to call in code; **every** call still runs through the same **`logLevel` gate** as Android’s `PulseLogger` / RN’s `PulseLogger` (no special bypass for “important” messages).

### 5.4.1 `PulseLogLevel.NONE` — fully silent (resolved; Android parity)

**Decision:** At **`logLevel: NONE`**, the web SDK emits **no** SDK-internal console output — **including** lines that describe **export failures**, **fetch failures**, **invalid remote schema**, or other situations you would tag as **`PulseWebLogger.error`** in code.

**Rationale:** Android **`PulseLogger`** gates all severities by `logLevel`; at **`PulseLogLevel.NONE`**, even **ERROR**-tier Android log calls do not run. Web matches that contract so cross-platform docs stay true.

**Integrator guidance:** To see export or config-fetch failures in the console, set at least **`logLevel: PulseLogLevel.ERROR`**. For warnings (e.g. deprecation) use **`WARN`**; for full diagnostics use **`DEBUG`** / **`VERBOSE`**.

### 5.5 Acceptance criteria

- **`PulseWebConfig`** has **`logLevel`** only; **no** `debugLogRecordLifecycle` in types, validation, or docs.
- With **`logLevel: NONE`**, **zero** SDK-internal console output (no diagnostics **and** no ERROR-level lines): remote config, sampling, lifecycle, **config-fetcher** fetch/schema failures, export errors — all silent unless/until level is raised.
- With **`logLevel: DEBUG`**, integrators get the same *class* of internal signal as Android at DEBUG where features align.
- No remaining **ad-hoc `console.log` / `console.warn` / `console.debug`** for SDK diagnostics under `src/` except possibly a **bootstrap chicken-and-egg** case before logger init (avoid if possible; otherwise one guarded fallback).

### 5.6 Files to add / change / delete (logging)

| Action | Path / area |
|--------|-------------|
| Add | `src/pulse-log-level.ts`, `src/pulse-web-logger.ts` |
| Change | `src/types/config.ts`, `src/config.ts`, `src/sdk.ts` |
| Change | `remote-config.ts`, `config-fetcher.ts` (fetch failures, invalid schema — today `console.warn`), `export-sampling-gate.ts`, processors/exporters touched by lifecycle |
| Remove field + branches | `debugLogRecordLifecycle` from config types, `validateConfig`, `sdk.ts` |
| Delete or fold | `log-record-lifecycle-debug-processor.ts`, `wrap-log-exporter-lifecycle-debug.ts` if superseded by logger-gated path |
| Tests / demo | `src/__tests__/integration-simplified-init.test.ts`, `examples/ecommerce-demo` |
| Export | Package `index.ts` — export **`PulseLogLevel`** for parity with RN public API |

---

## 6. Track C — Resource builder / injection

### 6.1 Problem

- Web builds a single `Resource` in `buildResource()` from **fixed** keys + `PulseWebConfig` subset.
- Android allows **`resource: (ResourceBuilder.() -> Unit)?`** so enterprises can set `service.namespace`, `deployment.environment`, custom `k8s.*`, etc., without forking the SDK.

### 6.2 Recommendation — **C1 only** (`resourceAttributes`)

**Public API (v1):** `PulseWebConfig.resourceAttributes?: Record<string, string | number | boolean>` only. **Do not** add `resource?: Resource` or `(base: Resource) => Resource` on `PulseWebConfig`.

**Why not C3 (exposed `Resource`) on web:** `buildResource()` may use `@opentelemetry/resources` internally (normal bundle dependency). Putting **`Resource` on public config** forces integrators onto the **same `@opentelemetry/resources` major** as the SDK; another OTel version in the app risks **structural / type breakage**. Android does not have this pattern on a JS-style public boundary.

**C2** (mutating callback on attrs) can be a later ergonomic; not required for v1 if C1 covers enterprise attrs.

**Gap vs Android `ResourceBuilder`:** No `Resource.detect()` or arbitrary `Resource.merge()` chain from the host before init — **rare for RUM**; **v2 / on request** if needed.

### 6.3 Merge order — user layer first, **Pulse wins on conflicts**

Same **idea** as Android: user-supplied attributes first, then **Pulse / platform overwrites** on collision.

In **OpenTelemetry JS**, `a.merge(b)` means **`b` wins** on duplicate attribute keys. Implementation sketch:

```ts
const userLayer = new Resource(config.resourceAttributes ?? {});
const pulseLayer = buildResource(config, osVersion);
const finalResource = userLayer.merge(pulseLayer); // pulseLayer wins overlaps
```

**Reserved keys (Pulse wins silently):** at minimum **`project.id`**, **`rum.sdk.name`**, **`platform`**. Extend the list in product docs if other attrs must be non-negotiable (e.g. `rum.sdk.version`). User values for those keys in `resourceAttributes` **do not override** Pulse.

**One-line contract:** **User `resourceAttributes` are applied first; Pulse reserved keys always win on conflict.**

### 6.4 Validation and tests

1. **SSR:** Plain object only for user attrs — no OTel class required from the integrator for the public path.
2. **Tests:** Custom keys appear on exported signals; `project.id` / `rum.sdk.name` / `platform` from user input are **replaced** by Pulse after merge.
3. **Optional:** warn in dev if user passes reserved keys; never let them replace Pulse for those keys.

---

## 7. Rollout and documentation

1. **Changelog (if any external guide still says `beforeSend`):** **`PulseWebConfig.beforeSend` removed — use `beforeSendData`** (same value shape).
2. **`logLevel` / `debugLogRecordLifecycle` removal:** Treat as **breaking** in the release that ships them (changelog + migration: `logLevel: PulseLogLevel.DEBUG`). If the package is still **0.x**, a single minor bump with a prominent breaking note is acceptable per team semver policy.
3. Update **`WEB-SDK-AGENT-CONTEXT.md`** parity table; link this file from **`v1/MILESTONES.md`** foundation row if PM wants M1 follow-up tracked.

---

## 8. File touch list (implementation checklist)

| Area | Likely files |
|------|----------------|
| Config types | `src/types/config.ts`, `src/config.ts` (`validateConfig`) |
| `beforeSendData` wiring | `src/before-send.ts`, `src/sdk.ts`, `src/exporters.ts`, `src/types/exporters.ts` |
| Logging | §5.6: `pulse-log-level.ts`, `pulse-web-logger.ts`; `config-fetcher.ts`; strip `debugLogRecordLifecycle`; route all SDK diagnostics through logger; delete or fold lifecycle-only modules |
| Resource | `resourceAttributes` on config; `src/resource.ts` + `src/sdk.ts`: `new Resource(attrs).merge(buildResource(...))` per §6.3; tests |
| Docs / demo | `web-sdk-plan/WEB-SDK-AGENT-CONTEXT.md`, `examples/ecommerce-demo/src/App.tsx`, `before-send-web-android-parity.md` (canonical key) |

---

## 9. Open questions

1. **SSR:** `resourceAttributes` is object-only; **`buildResource`** must stay safe when `window` is missing — confirm call sites in existing docs.

---

## Document history

| Date | Change |
|------|--------|
| 2026-04-23 | Initial plan from Android/web/RN comparison. |
| 2026-04-23 | Track B: single `logLevel` only; remove `debugLogRecordLifecycle` and raw `console.*`; align `PulseWebLogger` with Android/RN; add §5.4–5.6 and rollout note for breaking removal. |
| 2026-04-23 | §5.4.1: **`NONE` = fully silent** (including ERROR-tier messages); Android parity. §5.6: add `config-fetcher.ts`. §9: drop resolved NONE/ERROR question. |
| 2026-04-23 | **Track A:** `PulseWebConfig` / `ExporterConfig` use **`beforeSendData` only** (no `beforeSend` config key); plan §4/§7/§9 updated accordingly. |
| 2026-04-23 | **Track C:** **C1 only** (`resourceAttributes`); drop public **`Resource`** on config (OTel version coupling). Merge: `userLayer.merge(pulseLayer)` so **Pulse wins** on conflicts; document reserved keys (`project.id`, `rum.sdk.name`, `platform`). |
