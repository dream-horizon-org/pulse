# Research — click / rage RUM (ecosystem)

## Industry pattern

- **Per-tap logs** are common for simple RUM; **rage-click detection** groups frustrated repeated taps in a spatio-temporal window before emitting one enriched event.
- Google / product analytics often define rage as **N taps within T ms** near the same screen location.

## Pulse Android reference

- `ClickEventBuffer` (`pulse-android-otel/instrumentation/click-common/…/ClickEventBuffer.kt`) — UI-thread-only buffer, `Handler.postDelayed` for inactivity window, defaults: **2000 ms** window, **3** taps, **50 dp** radius.
- Emits **individual** taps only after eviction from the sliding window or on **flush** (activity pause).
- Rage emission adds `click.is_rage=true` and `click.rage_count` (see `ClickEventEmitter.emitRageClick`).

## Web constraints

- No `Handler`; use **`setTimeout`** / **`clearTimeout`** with the same inactivity semantics.
- Coordinates: **`MouseEvent.clientX/clientY`** are **CSS pixels**; map **dp → px** with `window.devicePixelRatio` as density scale (approximation to Android’s `densityScale`).

## Signal type

- **OTLP logs** — same as Android (`pulse.type` = `app.click`, body `app.widget.click`); rage is **attributes on the same log shape**, not a separate pulse.type.

## Flush

- Buffered taps and open rage clusters must **flush** on **`visibilitychange` → hidden** so tab backgrounding exports (browser has no exact “activity pause” analogue); SDK `pagehide` still drives provider `forceFlush`.

## Gate

- **`PulseFeature.CLICK`** + `InstrumentationKeys.CLICKS`; optional local override `instrumentations.clicks.rage.enabled` to disable rage algorithm while keeping per-click emission (immediate path).
