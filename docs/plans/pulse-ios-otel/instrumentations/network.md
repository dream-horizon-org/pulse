# iOS · Network

Captures outbound HTTP via `URLSession` swizzling → OTLP spans with `pulse.type = http`.

Brief: [../../../components/pulse-ios-otel.md](../../../components/pulse-ios-otel.md) · Peers: [../core/semconv](../core/semconv.md), [session](./session.md).

## Source location

- `pulse-ios-otel/Sources/Instrumentation/URLSession/`
- `pulse-ios-otel/Sources/PulseKit/NetworkAttributesSpanProcessor.swift` — enriches spans with redaction + platform attrs
- `pulse-ios-otel/Sources/PulseKit/PulseRedaction.swift` — PII + header redaction

## Public surface

Installed automatically via `PulseKit.start`. No per-call API; users opt out via `PulseKitConfiguration.disableURLSessionInstrumentation = true`.

## Internal design

Swizzles `URLSession.dataTask`, `uploadTask`, `downloadTask` families. For each task:
1. Start a client span at creation (`http.method`, `url.full`, `server.address`).
2. On completion: set `http.status_code`, `http.response.body.size`, `error.type` (for non-2xx), record span status.
3. Redaction pass in `NetworkAttributesSpanProcessor` strips PII keys and masks Authorization headers before export.
4. Large bodies never captured — only sizes.

## Data contracts

Signal: span (trace).
- `pulse.type = http`
- `platform = ios`
- `http.request.method`, `url.full`, `server.address`, `server.port`
- `http.response.status_code`, `http.response.body.size`
- `error.type` when status ≥ 400 or transport error

## Tests

`Tests/.../URLSessionInstrumentationTests.swift` — stubs `URLProtocol`, runs sample tasks, asserts span attrs + redaction.

## History / decisions

Swizzling over proxy-delegate so third-party SDKs using `URLSession` are captured without user intervention.

## Rebuild recipe

1. Add category/extension that swizzles the three task families on first `PulseKit.start`.
2. Track span ID → task mapping in a weak map.
3. On `didComplete`: end span; run through `NetworkAttributesSpanProcessor`.
4. Ship with redaction defaults for `Authorization`, `Cookie`, `Set-Cookie`.
