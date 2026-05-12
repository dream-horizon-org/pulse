# pipeline/processors

## 1. Purpose

Two synchronous processors sit between instrumentation emission and the batch exporter: the **global-attrs** processor enriches every span/log with session, identity, network, and screen attributes; the **signal-filter** processor applies remote-config attribute add/drop rules.

## 2. Source location

- `pulse-web-otel/src/processors/global-attrs-processor.ts` — `PulseGlobalAttributesProcessor` (span + log)
- `pulse-web-otel/src/processors/signal-filter-processor.ts` — `SignalFilterProcessor`
- `pulse-web-otel/src/processors/log-record-lifecycle-debug-processor.ts` — dev-only diagnostic

## 3. Public surface

```ts
class PulseGlobalAttributesProcessor implements SpanProcessor, LogRecordProcessor {
  constructor(sessionProvider: SessionProvider, config: PulseWebConfig);
  onStart(span: Span, _ctx: Context): void;
  onEmit(record: SdkLogRecord, _ctx: Context): void;
  forceFlush(): Promise<void>;
  shutdown(): Promise<void>;
}

class SignalFilterProcessor implements SpanProcessor, LogRecordProcessor {
  constructor(getConfig: () => PulseSdkConfig);
  // applies attributesToAdd / attributesToDrop on onStart / onEmit
}

export function resolveScreenNameFromUrl(url: string, hints?: ...): string;
```

## 4. Internal design

`PulseGlobalAttributesProcessor` stamps, on every span and log:

- `session.id` (from `SessionProvider.getSessionId()`)
- `window.id` (from `SessionProvider.getWindowId()`)
- `installation.id`, `user.id`, `pulse.user.previous_id`, user properties
- `screen.name` (current, mutable, set by navigation instrumentation)
- Network connection attrs (`network.connection.type`, `network.effective_type`, `network.rtt`, `network.downlink`) read from `navigator.connection`
- `device.screen.width`, `device.screen.height` (live `window.innerWidth/innerHeight`)
- `app.build_name` mirror

`SignalFilterProcessor`:

- Reads the current `PulseSdkConfig.signals.attributesToAdd` rules; for each rule, if `pulseSignalConditionMatches(signal)`, inject the configured attribute.
- Reads `attributesToDrop`; drops attributes whose key matches any pattern (`attributeKeyMatchesAnyDropPattern`).
- Honours scope filtering (`LOGS` / `TRACES` / `METRICS`).

`LogRecordLifecycleDebugProcessor` is wired only when `logLevel = DEBUG`; it logs each record at ingress and pre-batch with a sequence id.

## 5. Dependencies

- `@opentelemetry/api`, `@opentelemetry/sdk-trace-web`, `@opentelemetry/sdk-logs`
- `session.ts`, `remote-config.ts`, `utils/sampling-signal-match.ts`

## 6. Data contracts

Indirectly drives every signal — these processors are the reason a click log carries `session.id` and `screen.name`.

## 7. Tests

- `src/__tests__/signal-filter-processor.test.ts`
- `src/__tests__/screen-name-resolution.test.ts`
- `src/__tests__/sampling-signal-match.test.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md` § processors. Processors must be cheap and synchronous; expensive lookups (battery, storage) live in the error instrumentation's prefetch path.

## 9. Rebuild recipe

1. Implement `PulseGlobalAttributesProcessor` as a single class implementing both `SpanProcessor` and `LogRecordProcessor`.
2. Implement `SignalFilterProcessor` reading a live config getter so remote reloads take effect without rebuilding the pipeline.
3. Register processors **before** the batch processor.
