# Module 1 — Foundation

**Goal:** A working SDK skeleton that initialises, manages sessions, and exports a real span to the Pulse backend over OTLP HTTP. Every subsequent module builds on this. The foundation must be production-grade from day one — batching, persistence, shutdown, and compression are Module 1 scope, not retrofits.

**Prerequisites:** None — this is the starting point.

---

## What Gets Built

| Area | Detail | Doc |
|---|---|---|
| Repo scaffold & config types | Project structure, package.json, OTEL dependencies, `PulseWebConfig` | [scaffold.md](./scaffold.md) |
| Identity management | Installation ID (3-tier storage), Session Provider (ID + rotation) | [identity.md](./identity.md) |
| OTEL Resource & global attributes | Static browser attributes + dynamic per-signal attributes | [resource.md](./resource.md) |
| Export pipeline | OTLP exporters, batching, IndexedDB persistence, gzip compression | [pipeline.md](./pipeline.md) |
| SDK lifecycle | Singleton, shutdown API, instrumentation registry | [sdk-lifecycle.md](./sdk-lifecycle.md) |
| Session instrumentation | `session.start` / `session.end` signal emission | [session.md](./session.md) |
| Remote SDK config | Sampling, feature gates, signal filters, attribute manipulation | [sdk-config.md](./sdk-config.md) |

---

## Scope

**In:**
- Repo scaffold under `pulse-web-otel/`
- `PulseWeb.start(config)` / `PulseWeb.shutdown()`
- Session ID + Installation ID management
- OTEL Resource builder (browser attributes)
- OTLP HTTP exporters (traces, logs, metrics)
- Consent management (`dataCollectionState`)
- `sendBeacon` flush on page hide
- Batching — configurable batch processor for all signal types
- Persistence — IndexedDB-backed signal buffer; drain on next session
- Payload format + Compression — JSON (default) or Protobuf; gzip via `CompressionStream`
- Instrumentation registry — every instrumentation has `install()` / `uninstall()`; all toggleable at init
- Session as an instrumentation — `session.start` / `session.end` signals
- Shutdown API — force flush, uninstall all instrumentations, clear state

**Out:**
- Any auto-instrumentation beyond session (Module 2)
- Interactions (Module 3)
- Session replay (V2)
- Framework-specific wrappers (Module 5)
- Remote SDK config (Module 4 — added after instrumentations are stable)

---

## Deliverable

`PulseWeb.start({ endpointBaseUrl, apiKey, serviceName })` sends a heartbeat span visible in the Pulse ClickHouse dashboard with correct `ProjectId`, `SessionId`, `Platform = 'web'`, and `SDKVersion`.

---

## Done Criteria

Full criteria are in each sub-doc. High-level gates:

- [ ] `PulseWeb.start()` runs without errors in Chrome, Firefox, Safari
- [ ] A heartbeat span appears in ClickHouse: `platform = 'web'`, `project.id`, `session.id`, `rum.sdk.version` all correct
- [ ] CORS headers verified on all ingest endpoints
- [ ] `installation.id` persists across page reloads (3-tier fallback working)
- [ ] `session.id` rotates after 30 min inactivity; `session.previous_id` set correctly
- [ ] Signals batched with 5s flush, 2048 queue, 512 batch size
- [ ] `pagehide` triggers `forceFlush()` before tab closes
- [ ] `await PulseWeb.shutdown()` force-flushes all providers and uninstalls all instrumentations
- [ ] `instrumentations.errors.enabled: false` prevents that instrumentation from installing
- [ ] Unit tests passing for all sub-modules

See [sdk-lifecycle.md](./sdk-lifecycle.md) for the full done checklist and known risks.
