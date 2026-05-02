# ADR — Web click instrumentation + rage buffer

## Decision

Adopt **Android-aligned `ClickEventBuffer`** in TypeScript behind `ClicksInstrumentation`, with defaults **timeWindowMs=2000**, **threshold=3**, **radiusDp=50**, **max 5 active rage clusters**. OTLP remains **logs** with `pulse.type=app.click`, body `app.widget.click`; rage adds `click.is_rage` + `click.rage_count`.

## Rejected alternative

See [PLAN-A-immediate-per-click-emit.md](./PLAN-A-immediate-per-click-emit.md).

## Flush / lifecycle

- Flush buffer on **`visibilitychange` (hidden)**; then `sdk.loggerProvider?.forceFlush()` (no extra `pagehide` on clicks — SDK + session already own `pagehide`).
- `uninstall()` → `buffer.dispose()` (flush + cancel timeouts).

## Grill (Phase 3) — summary

- **Double install:** registry `installAllCompleted` prevents duplicate `installAll`; clicks listener removed on uninstall.
- **Consent / gate off:** instrumentation not installed → zero listeners; E2E seeds config with `click` feature `sessionSampleRate: 0`.
- **SSR:** `typeof window === "undefined" || typeof document === "undefined"` → no-op install.
- **Optional attrs:** omit empty widget/context keys on emit.

## Remote config

- Rage tuning is **local** `PulseWebConfig.instrumentations.clicks.rage` for v1; remote `rage.*` keys deferred to a follow-up if backend adds them to merged SDK JSON.
