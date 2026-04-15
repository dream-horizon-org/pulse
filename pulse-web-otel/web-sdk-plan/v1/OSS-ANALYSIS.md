# V1 — Build vs Buy Analysis

**Scope:** V1 only. Every component analysed for available OSS, what we get free, what we still write.

**Scale:**
- 🟢 Plug & play — drop in, zero custom code
- 🟡 OSS core + thin Pulse layer — library handles hard part, we add signal names / attrs
- 🔴 Build from scratch — no useful OSS exists

---

## Foundation

| Component | OSS | What OSS Gives | Custom Still Needed | Verdict |
|---|---|---|---|---|
| OTLP HTTP exporters | `@opentelemetry/exporter-trace-otlp-http`<br>`exporter-logs-otlp-http`<br>`exporter-metrics-otlp-http` | Full OTLP/HTTP transport, headers, retry | Nothing | 🟢 Plug & play |
| Batch processors | `BatchSpanProcessor`<br>`BatchLogRecordProcessor`<br>(from `sdk-trace-web`, `sdk-logs`) | 5s flush, queue, batch size config | Nothing | 🟢 Plug & play |
| TracerProvider / LoggerProvider / MeterProvider | `@opentelemetry/sdk-trace-web`<br>`@opentelemetry/sdk-logs`<br>`@opentelemetry/sdk-metrics` | Full OTEL provider lifecycle | Nothing | 🟢 Plug & play |
| OTEL Resource class | `@opentelemetry/resources` | `new Resource({...})` container, merge semantics | Populating browser-specific attrs (`browser.name`, `device.type`, `os.version`, etc.) | 🟡 Class = OSS, attribute population = ~60 lines custom |
| UA parsing | Native `navigator.userAgentData` (Client Hints API) + `navigator.userAgent` fallback | Browser/OS detection in modern browsers | Fallback UA string parsing for older browsers (~30 lines, no heavy lib needed) | 🟡 Modern = native free, fallback = small custom |
| gzip compression | Native `CompressionStream` browser API (Chrome 80+, Firefox 113+, Safari 16.4+) | Zero-cost gzip in all modern browsers | Feature-detect guard + fallback no-op for old browsers (~10 lines) | 🟢 Plug & play (native) |
| IndexedDB persistence | `idb` by Jake Archibald (1.6 KB) | Promise-based IndexedDB wrapper — removes all callback hell | `PersistenceExporterDecorator` pattern wrapping OTEL exporter interface | 🟡 `idb` = plug-and-play, decorator = ~80 lines custom |
| Session ID / Installation ID | `crypto.randomUUID()` (native browser API) | UUID generation | 3-tier storage fallback logic, 30-min rotation, SessionProvider event emitter | 🔴 Scratch — UUID is free, everything else custom |
| SDK singleton + consent + registry | None | — | `PulseWebSDK` class, double-init guard, `PulseDataCollectionConsent` gating, `InstrumentationRegistry` | 🔴 Scratch |
| Global attributes processor | OTEL `SpanProcessor` / `LogRecordProcessor` interface | Standard hook points (`onStart`, `onEmit`) | Implementing the processors: read session/screen/network state and inject attrs | 🔴 Scratch (implementing the interface) |
| Sampling processor | None | — | Per-session decision, rule evaluation, critical event bypass | 🔴 Scratch |
| Signal filter processor | None | — | Attribute drop/add, blacklist/whitelist per signal | 🔴 Scratch |

**Foundation verdict:** OTEL packages handle all transport and pipeline plumbing. Identity, session, processors, consent = scratch. Scratch parts are not algorithmically complex — mostly boilerplate-level glue code.

---

## Instrumentations

### Errors (`device.crash`, `non_fatal`)

| | |
|---|---|
| **OSS available** | None purpose-built for OTEL-format browser error capture |
| **What exists** | `window.onerror` + `window.onunhandledrejection` are plain browser events — no lib needed to access them |
| **Custom needed** | Pulse signal names, stack trace normalisation, error deduplication (prevent same error flooding pipeline) |
| **Effort** | ~80 lines |
| **Verdict** | 🔴 Scratch — but trivially small. Two event listeners + log record. |

---

### Network (`http` span)

| | |
|---|---|
| **OSS available** | `@opentelemetry/instrumentation-fetch` ✅ + `@opentelemetry/instrumentation-xml-http-request` ✅ |
| **What OSS gives** | Patches `fetch` + `XHR` globally, creates spans, standard `http.url` / `http.method` / `http.status_code` attributes, handles streaming, AbortController, cancellation edge cases |
| **What OSS misses** | `pulse.type: http` attribute, GraphQL operation name extraction from POST body, URL blocklist (exclude Pulse ingest endpoints), `http.request_size` / `http.response_size` |
| **Custom needed** | Thin `SpanProcessor` on top: add `pulse.type`, extract GraphQL op name, apply URL blocklist (~60 lines) |
| **Effort** | ~60 lines on top of OSS |
| **Verdict** | 🟡 Biggest OSS win in V1. Fetch/XHR patching is notoriously hard — streaming, service workers, AbortController, cancellation all have edge cases. Let OSS handle it. |

> ⚠️ **Do not write custom fetch/XHR patching.** The OSS instrumentation handles years of edge cases. Custom implementation = months of bug fixing.

---

### Web Vitals (`web_vital` Metric)

| | |
|---|---|
| **OSS available** | `web-vitals` by Google ✅✅ |
| **What OSS gives** | LCP, CLS, INP, FCP, TTFB with attribution. Handles BFCache restore, soft navigations, SPA re-measurement, all browser quirks across Chrome/Firefox/Safari |
| **What OSS misses** | Nothing on collection side. Output is a callback — we wrap it in `meter.createObservableGauge()` |
| **Custom needed** | Callback → OTLP Gauge wrapper: add `pulse.type`, `metric.name`, `metric.rating`, attribution attrs (~40 lines) |
| **Effort** | ~40 lines |
| **Verdict** | 🟢 Near plug-and-play. `web-vitals` does 95% of the work. Web Vitals edge cases (BFCache, attribution, soft navigations) took Google engineers years. We get it free. |

---

### Navigation (`screen_load`, `screen_interactive`, `screen_session`)

| | |
|---|---|
| **OSS available** | `@opentelemetry/instrumentation-document-load` ✅ (partial) |
| **What OSS gives** | Initial page load only — Navigation Timing API entries as spans (`documentFetch`, `documentLoad`, `resourceFetch`) |
| **What OSS misses** | SPA route changes via History API (`pushState` / `replaceState` / `popstate`), Pulse signal names (`screen_load`, `screen_interactive`, `screen_session`), `screen.name` resolution chain, TTI calculation |
| **Two options** | A) Use OSS for initial load + write custom SPA tracking on top. B) Write full custom navigation instrumentation with our signal names. |
| **Recommended** | Option B — OSS signal names don't map cleanly to Pulse names, transform layer would be as much work as writing from scratch |
| **Effort** | ~150 lines (initial load timing + SPA History API patching) |
| **Verdict** | 🟡 OSS exists but signal name mismatch makes full custom cleaner. SPA tracking = scratch regardless. |

---

### Clicks (`app.click`)

| | |
|---|---|
| **OSS available** | None for OTEL-based click tracking |
| **Analytics libs** | Mixpanel / Amplitude / PostHog have click tracking but not OTLP, not open, heavy dependencies |
| **Custom needed** | `document.addEventListener('click')` at capture phase, element fingerprinting (tag/id/classes/text), rage click algorithm (3+ clicks / 700ms sliding window on same target), dead click detection, normalised x/y coordinates for heatmap |
| **Effort** | ~120 lines (click capture + rage click sliding window + element fingerprinting) |
| **Verdict** | 🔴 Scratch — no useful OSS. Rage click algorithm is well-documented pattern, ~30 lines of the total. |

---

## Interactions

| | |
|---|---|
| **OSS available** | **Nothing** — no OSS equivalent exists anywhere |
| **Closest things** | XState (state machines) — but 20 KB+ dependency, far more complex than our simple 3-state machine |
| **Custom needed** | Config fetcher (CDN fetch + cache), state machine (IDLE → ONGOING → COMPLETED / ERROR), step matching with 6 operators, timeout handling, blacklists, APDEX scoring, OTel span output |
| **Effort** | ~400 lines — largest single custom component in V1 |
| **Verdict** | 🔴 100% scratch — unique Pulse IP, no OSS anywhere close |

---

## SDK Config (Remote Config)

| | |
|---|---|
| **OSS available** | LaunchDarkly, Split.io, Statsig — remote feature flag systems exist |
| **Why not use them** | Pulse-specific schema (signal filters, attribute manipulation, session-level sampling), adds heavy external dependency, Pulse already owns the backend config endpoint |
| **Custom needed** | `SdkConfigFetcher` (localStorage cache + background fetch), `FeatureGate` (session-level feature enable/disable), `PulseSamplingProcessor` (per-session sampling decision), `SignalFilterProcessor` (attribute drop/add/transform) |
| **Effort** | ~300 lines across 4 classes |
| **Verdict** | 🔴 Scratch — schema is Pulse-specific, external libs are wrong abstraction |

---

## React Integration

| | |
|---|---|
| **OSS available** | `@opentelemetry/instrumentation-react-load` exists but very limited |
| **What OSS gives** | Component load timing only — not useful for our use case |
| **Custom needed** | `<PulseProvider>` (thin wrapper, SSR guard), `<PulseErrorBoundary>` (standard React class component pattern), `useRouterTracking()` hook (`useLocation` diff → `screen_session` span) |
| **Effort** | ~100 lines — React patterns here are standard boilerplate |
| **Verdict** | 🔴 Scratch — but React patterns are trivial. No algorithmic complexity. |

---

## Build & Distribution

| Component | OSS | Verdict |
|---|---|---|
| Bundler | `tsup` (esbuild-based, multi-entry-point support) | 🟢 Plug & play |
| Bundle size enforcement | `size-limit` | 🟢 Plug & play |
| TypeScript | `tsc` | 🟢 Plug & play |
| GitHub Actions CI/CD | Standard `actions/checkout`, `actions/setup-node` | 🟢 Plug & play |
| S3/CloudFront CDN upload | `aws-cli` in CI | 🟢 Plug & play |

---

## Summary

### By verdict

```
🟢 Pure plug-and-play (zero custom code):
   OTLP exporters
   Batch processors (spans, logs)
   TracerProvider / LoggerProvider / MeterProvider
   CompressionStream (native)
   tsup · size-limit · TypeScript · GitHub Actions

🟡 OSS core + thin Pulse layer (~30–80 lines each):
   Network → instrumentation-fetch + custom SpanProcessor
   Web Vitals → web-vitals lib + OTLP gauge wrapper
   Navigation → partial OSS + custom SPA tracking
   OTEL Resource → class from OSS + custom browser attr population
   IndexedDB → idb lib + custom persistence decorator

🔴 Build from scratch (no useful OSS):
   Session / Installation ID (storage + rotation)
   SDK singleton + consent + registry
   Global attrs / Sampling / Signal filter processors
   Errors instrumentation (small, ~80 lines)
   Clicks + rage click detection (~120 lines)
   Interactions — entire system (~400 lines)
   SDK Config — entire system (~300 lines)
   React integration wrappers (~100 lines)
```

### Effort distribution

| Category | Approx lines | OSS saves |
|---|---|---|
| OTLP pipeline (exporters + providers + processors) | ~0 custom | ~2000 lines saved |
| Network instrumentation | ~60 custom | ~800 lines saved (fetch/XHR patching) |
| Web Vitals | ~40 custom | ~500 lines saved (browser quirks, BFCache) |
| Navigation | ~150 custom | ~100 lines saved (initial load timing) |
| Errors | ~80 custom | 0 |
| Clicks | ~120 custom | 0 |
| Interactions | ~400 custom | 0 |
| SDK Config | ~300 custom | 0 |
| Foundation (session, identity, registry, processors) | ~400 custom | ~200 lines saved (UUID, idb) |
| React integration | ~100 custom | 0 |
| **Total custom** | **~1,650 lines** | |

### Key decisions

| Decision | Reason |
|---|---|
| Use `@opentelemetry/instrumentation-fetch` + `instrumentation-xml-http-request` | Fetch/XHR patching has years of edge cases (streaming, AbortController, service workers). Custom = months of bugs. |
| Use `web-vitals` lib | BFCache, soft navigation, attribution edge cases took Google years. Free. |
| Skip `instrumentation-document-load` | Signal name mismatch with Pulse schema makes transform layer as expensive as writing from scratch. |
| Skip XState for interactions | 20 KB dependency for a 3-state machine. Overkill. |
| Skip LaunchDarkly / Split.io for SDK Config | Pulse-specific schema, external dependency, wrong abstraction level. |

### Highest reinvention risk

These three have no OSS fallback and are the most complex custom pieces:

1. **Interactions** (~400 lines) — state machine + APDEX + config fetch. No OSS anywhere. Unique Pulse IP.
2. **SDK Config** (~300 lines) — sampling + feature gates + signal filters. Well-defined spec but all custom.
3. **Session Provider** (~150 lines) — rotation logic, BFCache edge cases, 3-tier storage. Simple but tricky edge cases.
