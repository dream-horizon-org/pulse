# instrumentations/interaction

## 1. Purpose

User-defined interaction tracing: a sequence of named events (e.g. `add_to_cart`, `checkout_started`, `checkout_completed`) is matched against remote-config rules and emitted as a single OTel span with `pulse.type = interaction`. Mirrors the Android Interaction feature.

## 2. Source location

- `pulse-web-otel/src/instrumentations/interaction.ts` — thin `InteractionInstrumentation` wrapper
- `pulse-web-otel/src/interactions/interaction-feature.ts` — feature orchestrator (lifecycle, config fetch)
- `pulse-web-otel/src/interactions/interaction-tracker.ts` — receives `trackEvent` calls
- `pulse-web-otel/src/interactions/interaction-sequence-matcher.ts` — matches event series against config rules
- `pulse-web-otel/src/interactions/interaction-coordinator.ts` — coordinates open interactions
- `pulse-web-otel/src/interactions/interaction-span-builder.ts` — assembles the final span
- `pulse-web-otel/src/interactions/config-fetcher.ts` — interaction-config endpoint
- `pulse-web-otel/src/interactions/interaction-models.ts` — types

## 3. Public surface

```ts
class InteractionInstrumentation implements PulseInstrumentation {
  readonly name = PulseInstrumentationName.INTERACTIONS;
  install(sdk: SdkContext): void;
  uninstall(): void;
  trackEvent(name: string, attrs?: PulseAttributes, timestampMs?: number): void;
}
```

Reachable through `Pulse.trackEvent(name, attrs)` on the SDK facade. Gated by `PulseFeature.INTERACTION`.

## 4. Internal design

- `install()` constructs an `InteractionFeature` with `(endpointBaseUrl, config, gate, enabled, tracer)` and calls `init()` (fetches interaction config asynchronously).
- `trackEvent` is buffered in `InteractionTracker`; the `InteractionSequenceMatcher` walks the remote `interactions[]` rules, matching ordered prefixes against the buffered queue.
- When a rule completes, `InteractionSpanBuilder` produces an OTel span (kind `INTERNAL`) with rule name, ordered event timeline, and any aggregate attributes.
- `InteractionCoordinator` enforces a one-open-interaction-per-name policy and times out idle sequences.
- `shutdown()` flushes any in-flight interaction.

## 5. Dependencies

- `@opentelemetry/api` (Tracer)
- `remote-config.ts` and the gate
- Network: own config fetcher endpoint

## 6. Data contracts

`pulse.type = interaction`. Attribute keys: `event.name` (per included event), interaction name on the span, plus user-supplied `PulseAttributes`. Inherits `session.id`, `screen.name`.

## 7. Tests

- `src/__tests__/interaction-feature.test.ts`
- `src/__tests__/interactions-tracker.test.ts`
- `src/__tests__/interactions-sequence-matcher.test.ts`
- `src/__tests__/interactions-span-builder.test.ts`
- `src/__tests__/interaction-instrumentation.test.ts`
- E2E: `examples/ecommerce-demo/e2e/m2-interactions.spec.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/interactions/SPEC.md`. Decomposition into five collaborator classes (feature / tracker / matcher / coordinator / span-builder) keeps each file <200 LOC and individually unit-testable.

## 9. Rebuild recipe

1. Define `InteractionFeature` as the only thing the instrumentation knows about; it owns the lifecycle.
2. Implement the sequence matcher as a stateless function over event queues + rule definitions.
3. Coordinator owns timeouts and per-name uniqueness.
4. Span builder finalises into a single OTel span with timeline attributes.
