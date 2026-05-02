# Research — clicks in OTel JS + Pulse Web SDK

## Where it plugs in

- **`ClicksInstrumentation`** — `document` capture-phase `click` listener (same as prior M3).
- **`logs.getLogger("pulse-web-clicks").emit`** — OTLP log pipeline; **optional keys omitted** per semconv contract.
- **`SdkContext.loggerProvider?.forceFlush()`** — after buffer flush emits records, match Web Vitals pattern so batches leave before tab discard.

## Why not traces/metrics

- Android parity and dashboard filters use **log records** with `pulse.type` + body; traces would fragment UX queries.

## Timer ownership

- **`ClickEventBuffer`** owns `setTimeout` handles for rage inactivity; **`ClicksInstrumentation.uninstall`** calls `buffer.dispose()` to cancel timers and flush remaining state (parity with Android `flush()` on teardown).
