# Custom Span API — SPEC

**Status:** Stable (web-only, spans via OTel in-process)

## Overview

The Custom Span API (`Pulse.startSpan` / `Pulse.trackSpan`) allows host applications to emit rich span signals with method chaining, event recording, and exception handling. Spans are marked `pulse.type = custom_span` and exported via the standard OTLP pipeline.

**Web specialization:** Web wraps the in-process OTel span via closure; no native bridge ID indirection. `spanId` is deliberately omitted from the public `PulseSpan` interface.

## API

### `Pulse.startSpan(name, options?): PulseSpan`

Create and return a mutable span handle. The caller is responsible for calling `end()`.

```ts
const span = Pulse.startSpan("fetch-data", {
  attributes: { user_id: "123", endpoint: "/api/v1/data" },
});
try {
  const data = await fetch("/api/v1/data");
  span.end("OK");
} catch (error) {
  span.addEvent("error", { message: error.message });
  span.end("ERROR");
}
```

**Signature:**
```ts
startSpan(name: string, options?: SpanOptions): PulseSpan
```

**Parameters:**
- `name` *(string)* — Span name (required).
- `options.attributes` *(PulseAttributes, optional)* — Caller attributes. Any `pulse.type` key is silently stripped; SDK always sets `pulse.type = custom_span`.

**Returns:** `PulseSpan` object with methods `end`, `addEvent`, `setAttributes`, `recordException`.

**Behavior:**
- Returns `noopSpan` (no-op closure) if SDK is not initialized.
- Span `kind` is always `INTERNAL`.
- Span creation timestamp is `Date.now()` at call time.
- Global attributes processor injects `session.id`, `screen.name`, `platform=web` on export.

### `Pulse.trackSpan<T>(name, fn, options?): T | Promise<T>`

Execute a function or async function within a span, auto-ending on success or error.

```ts
// Sync
const result = Pulse.trackSpan("validate-input", () => {
  return validator.check(data);
});

// Async
const data = await Pulse.trackSpan(
  "load-config",
  () => fetch("/config").then(r => r.json()),
  { attributes: { config_env: "prod" } }
);
```

**Signature:**
```ts
trackSpan<T>(name: string, fn: () => T | Promise<T>, options?: SpanOptions): T | Promise<T>
```

**Parameters:**
- `name` *(string)* — Span name.
- `fn` *(() => T | Promise<T>)* — Sync or async function to wrap.
- `options` *(SpanOptions, optional)* — Caller attributes.

**Returns:** The return value of `fn`, preserving its type (sync or async).

**Behavior:**
- On successful completion: `span.end(OK)` automatically.
- On sync throw or async rejection: `span.end(ERROR)`, then error is rethrown/rejected.
- If SDK not initialized, `fn()` is called directly without span wrapping.

### `PulseSpan` interface

```ts
type PulseSpan = {
  end: (statusCode?: SpanStatusCode) => void;
  addEvent: (name: string, attributes?: PulseAttributes) => void;
  setAttributes: (attributes: PulseAttributes) => void;
  recordException: (error: Error, attributes?: PulseAttributes) => void;
};
```

**Methods:**

| Method | Purpose | Notes |
|--------|---------|-------|
| `end(statusCode?)` | Finalize the span. | Default status is `UNSET` (no status set). Calling twice is a no-op. |
| `addEvent(name, attrs?)` | Record a breadcrumb event. | Events have ordered `timeUnixNano` in OTLP. |
| `setAttributes(attrs)` | Add or update span attributes. | Overwrites prior keys; does not strip `pulse.type`. |
| `recordException(error, attrs?)` | Record an exception. | Coerces non-`Error` to `Error`. |

### `SpanStatusCode` enum

```ts
enum SpanStatusCode {
  OK = "OK",
  ERROR = "ERROR",
  UNSET = "UNSET",
}
```

Maps to OTel `SpanStatusCode`:
- `OK` → `0`
- `ERROR` → `2`
- `UNSET` → skips `setStatus` call (no status set)

## Data contract

**Signal type:** OTLP Span

**Required attributes (SDK-set):**
- `pulse.type` = `"custom_span"`
- `platform` = `"web"` (via global attributes processor)
- `session.id` (via global attributes processor)

**Optional attributes (caller-provided):**
- User attributes passed via `SpanOptions.attributes`.

**Example exported span:**
```json
{
  "name": "fetch-data",
  "kind": 0,
  "startTimeUnixNano": "1716590400000000000",
  "endTimeUnixNano": "1716590401234000000",
  "attributes": {
    "pulse.type": "custom_span",
    "platform": "web",
    "session.id": "sess-abc123",
    "screen.name": "/products",
    "user_id": "123",
    "endpoint": "/api/v1/data"
  },
  "events": [
    {
      "name": "error",
      "timeUnixNano": "1716590400500000000",
      "attributes": { "message": "Network timeout" }
    }
  ],
  "status": { "code": 2, "message": "" }
}
```

## Cross-platform parity

### Web vs React Native

| Topic | React Native | Web |
|-------|--------------|-----|
| `startSpan` signature | `(name, options?)` | Same |
| Return type | Rich `Span` object | Rich `PulseSpan` object |
| Methods | `end`, `addEvent`, `setAttributes`, `recordException` | Same |
| `SpanStatusCode` | `OK` / `ERROR` / `UNSET` | Same |
| `SpanOptions.inheritContext` | Supported | Deferred — always uses `ROOT_CONTEXT` |
| `trackSpan` signature | `(name, options, fn)` | **`(name, fn, options?)`** — web ergonomics |
| `trackSpan` completion | `finally() => end(UNSET)` always | Explicit `end(OK)` / `end(ERROR)` |
| `spanId?: string` on span | Yes — native bridge handle | **Omitted** — web closure holds OTel span |

### Web vs Android

Web custom span API is **RN-shaped**, not Android-shaped:

| Topic | Android | Web |
|-------|---------|-----|
| `startSpan` return | `() -> Unit` close callback only | Rich `PulseSpan` object |
| Span methods | No `addEvent` / `setAttributes` / `recordException` | Full rich API (RN parity) |
| `SpanStatusCode` | Not exposed | `OK` / `ERROR` / `UNSET` on `end()` |
| Attributes | `Map<String, Any?>` positional arg | `SpanOptions.attributes?: PulseAttributes` |

### Mobile semconv alignment (future work)

Neither RN (`PULSE_TYPES`) nor Android (`PulseAttributes.PulseTypeValues`) currently define `custom_span`. Any mobile custom spans carry no `pulse.type`. This web-only `custom_span` type will require SDK owner alignment before a unified backend query can assume mobile support.

## Feature gates

Custom spans are **not gated** — unlike `trackEvent` (requires `PulseFeature.CUSTOM_EVENTS`), custom tracing is core SDK. Init guard + consent-at-init is sufficient.

## Context propagation

**Deferred.** `startSpan` always uses `ROOT_CONTEXT`; no parent context inheritance. Callers cannot manually parent auto-instrumented child spans (e.g., network spans created within a custom span). This is a deliberate omission for MVP; follow-on: `SpanOptions.inheritContext` or context manager.

## Design decisions

### Why no `pulse.type` override?

SDK owns span classification. Caller-provided `pulse.type` is silently stripped, then always set to `custom_span`. This ensures:
- Consistent backend queries by type.
- No accidental type collisions (e.g., caller mistakenly passing `network.200`).

### Why omit `spanId` on `PulseSpan`?

Web holds the OTel span in-closure; there is no native bridge ID indirection (unlike RN's native spanId handle). The underlying `otelSpan.spanContext().spanId` is available for debugging correlation, but exposing it on the public interface creates API surface debt without practical benefit in web.

### Why explicit OK/ERROR instead of RN's UNSET?

RN's `finally(end(UNSET))` pattern loses signal — callers cannot distinguish "span ended without explicit status" from "caller forgot to set status". Web's explicit `end(OK)` / `end(ERROR)` in trackSpan is an intentional improvement; unclear cases raise no event.

## Error handling

- **Non-`Error` recordException:** Coerced to `Error(String(value))`.
- **Throw/reject in trackSpan:** Automatically sets `end(ERROR)`, then rethrows/rejects.
- **Double-end:** Second `end()` call is a silent no-op (no-op flag in closure).

## Limitations

- No context propagation into `context.active()` (deferred).
- No `discardSpan` (web has no span discard path; spans export only on `end()`).
- No `getActiveSpan()` (deferred).

## Testing

**Unit test suite:** `src/__tests__/custom-span.test.ts`
- Positive (CS-P1–P14): happy path, methods, status mapping, attributes, concurrent spans.
- Negative (CS-N1–N6): pre-init, throws, post-shutdown (future), 500 errors.
- Edge (CS-E1–E6): noopSpan idempotence, double-end, non-Error coercion, SSR, empty name.
- BeforeSend (CS-F1/F2): filter-ability, attribute export.

**E2E test suites:**
- `examples/ecommerce-demo/e2e/custom-span.spec.ts` (@custom-span) — 7 journeys + 3 negatives.
- `examples/nextjs-demo/e2e/custom-span.spec.ts` (@custom-span-next) — 7 journeys (app/pages router, RSC timing, multi-hop session).

## Examples

### Wrap a fetch with attributes

```ts
const response = await Pulse.trackSpan(
  "get-user-profile",
  () => fetch(`/api/users/${userId}`).then(r => r.json()),
  { attributes: { user_id: userId, role: "admin" } }
);
```

### Manual span with events

```ts
const span = Pulse.startSpan("compute-heavy-task");
try {
  for (let i = 0; i < 10; i++) {
    const result = doWork(i);
    span.addEvent("iteration", { iteration: i, result });
  }
  span.end("OK");
} catch (error) {
  span.recordException(error);
  span.end("ERROR");
}
```

### Coexist with trackEvent

```ts
Pulse.trackEvent("user_purchased", { amount: 99.99 });
const receipt = Pulse.trackSpan("process-payment", () => api.pay(99.99));
```
