# 03.3 — Interaction Span Output

**Goal:** Convert `AnyInteractionResult` from the matching engine into OTLP spans with Android-parity attribute names, time category classification, and span events per matched step.

**File:** `src/interactions/interaction-span-builder.ts`  
**Android equivalents:** `InteractionInstrumentation.handleSuccessInteraction()` · `InteractionDefaultAttributesExtractor` · `InteractionConstant.kt`

> **Source of truth:** Android `InteractionConstant.kt`. Any attribute key without the `pulse.interaction.` prefix is wrong and will not appear in the Interactions dashboard.

---

## Signal Produced

### `pulse.type = 'interaction'` — one span per completed or errored interaction

| Attribute | Type | Value | Android constant |
|---|---|---|---|
| `pulse.type` | string | `'interaction'` | — |
| `pulse.interaction.id` | string | UUID per match attempt | `InteractionConstant.ID` |
| `pulse.interaction.name` | string | `config.name` | `InteractionConstant.NAME` |
| `pulse.interaction.config.id` | string | `config.id` | `InteractionConstant.CONFIG_ID` |
| `pulse.interaction.config.name` | string | `config.name` | `InteractionConstant.CONFIG_NAME` |
| `pulse.interaction.complete_time` | long | duration in **nanoseconds** | `InteractionConstant.TIME_TO_COMPLETE_IN_NANO` |
| `pulse.interaction.apdex_score` | double | 0.0–1.0 (see §Time Category) | `InteractionConstant.APDEX_SCORE` |
| `pulse.interaction.user_category` | string | `Excellent` / `Good` / `Average` / `Poor` | `InteractionConstant.USER_CATEGORY` |
| `pulse.interaction.is_error` | bool | `true` on timeout or violation | `InteractionConstant.IS_ERROR` |
| `pulse.interaction.error.type` | string | `'TIMEOUT'` or `'SEQUENCE_VIOLATION'` | `InteractionConstant.ERROR_TYPE` |
| `pulse.interaction.error.message` | string | human-readable detail | `InteractionConstant.ERROR_MESSAGE` |

> `pulse.interaction.error.type` and `pulse.interaction.error.message` are only set when `pulse.interaction.is_error = true`.

### Step timeline — span events (not JSON string)

Each matched event is added as an OTel **span event** via `span.addEvent()`. This mirrors Android `addAsSpanEventsTo()`. Do **not** serialize steps into a JSON string attribute.

```typescript
for (const event of result.events) {
  span.addEvent(event.name, {}, event.timestampMs);
}
```

---

## Time Category (Android `TimeCategory` parity)

Android uses **three thresholds** from the interaction config (`uptimeLowerLimitInMs`, `uptimeMidLimitInMs`, `uptimeUpperLimitInMs`) to classify duration into one of four buckets. The simple 3-bucket APDEX vocabulary (`satisfied`/`tolerating`/`frustrated`) is **wrong** — the dashboard filters on the Android strings below.

| Duration | `pulse.interaction.user_category` | `pulse.interaction.apdex_score` |
|---|---|---|
| ≤ `uptimeLowerLimitInMs` | `Excellent` | `1.0` |
| ≤ `uptimeMidLimitInMs` | `Good` | `0.75` |
| ≤ `uptimeUpperLimitInMs` | `Average` | `0.5` |
| > `uptimeUpperLimitInMs` | `Poor` | `0.0` |

For **errored interactions** (`is_error = true`): always `Poor` / `0.0` regardless of duration.

```typescript
export type TimeCategory = 'Excellent' | 'Good' | 'Average' | 'Poor';

function getTimeCategory(
  durationMs: number,
  config: InteractionConfig,
): { category: TimeCategory; score: number } {
  if (durationMs <= config.uptimeLowerLimitInMs) {
    return { category: 'Excellent', score: 1.0 };
  } else if (durationMs <= config.uptimeMidLimitInMs) {
    return { category: 'Good', score: 0.75 };
  } else if (durationMs <= config.uptimeUpperLimitInMs) {
    return { category: 'Average', score: 0.5 };
  } else {
    return { category: 'Poor', score: 0.0 };
  }
}
```

---

## Duration Units — Nanoseconds

`pulse.interaction.complete_time` is in **nanoseconds** — matches Android `InteractionConstant.TIME_TO_COMPLETE_IN_NANO` (`computeInteractionTimeSpanInNanos()`). Milliseconds will corrupt duration-based queries in ClickHouse.

```typescript
// ✓ Correct
const durationNanos = (result.endTimeMs - result.startTimeMs) * 1_000_000;

// ✗ Wrong
const durationMs = result.endTimeMs - result.startTimeMs;
```

---

## Root Span — `ROOT_CONTEXT` Required

Interaction spans must have **no parent** — mirrors Android `setNoParent()`. Without `ROOT_CONTEXT`, calling `tracer.startSpan()` inside an active span context (e.g., inside a click handler) auto-parents the interaction span, which is incorrect.

```typescript
// ✓ Correct — no auto-parent
import { context, ROOT_CONTEXT } from '@opentelemetry/api';

const span = context.with(ROOT_CONTEXT, () =>
  tracer.startSpan(config.name, { startTime: result.startTimeMs, kind: SpanKind.CLIENT })
);

// ✗ Wrong — may pick up whatever span is currently active
const span = tracer.startSpan(config.name, { startTime: result.startTimeMs });
```

---

## Full Implementation

```typescript
// src/interactions/interaction-span-builder.ts
import {
  Tracer,
  SpanKind,
  SpanStatusCode,
  context,
  ROOT_CONTEXT,
} from '@opentelemetry/api';
import { AnyInteractionResult, InteractionConfig, TimeCategory } from './interaction-models';

export class InteractionSpanBuilder {
  constructor(private readonly tracer: Tracer) {}

  build(result: AnyInteractionResult, config: InteractionConfig): void {
    const durationMs = result.endTimeMs - result.startTimeMs;
    const durationNanos = durationMs * 1_000_000;

    const { category, score } = result.isError
      ? { category: 'Poor' as TimeCategory, score: 0.0 }
      : getTimeCategory(durationMs, config);

    // ROOT_CONTEXT: interaction spans must have no parent (Android: setNoParent())
    const span = context.with(ROOT_CONTEXT, () =>
      this.tracer.startSpan(config.name, {
        startTime: result.startTimeMs,
        kind: SpanKind.CLIENT,
      })
    );

    span.setAttributes({
      'pulse.type':                         'interaction',
      'pulse.interaction.id':               result.interactionId,
      'pulse.interaction.name':             config.name,
      'pulse.interaction.config.id':        config.id,
      'pulse.interaction.config.name':      config.name,
      'pulse.interaction.complete_time':    durationNanos,
      'pulse.interaction.apdex_score':      score,
      'pulse.interaction.user_category':    category,
      'pulse.interaction.is_error':         result.isError,
    });

    if (result.isError) {
      span.setAttribute('pulse.interaction.error.type', result.errorType);
      span.setAttribute('pulse.interaction.error.message', result.errorMessage);
      span.setStatus({ code: SpanStatusCode.ERROR, message: result.errorType });
    } else {
      span.setStatus({ code: SpanStatusCode.OK });
    }

    // Add each matched event as a span event (step timeline — Android addAsSpanEventsTo())
    for (const event of result.events) {
      span.addEvent(event.name, {}, event.timestampMs);
    }

    span.end(result.endTimeMs);
  }
}
```

---

## APDEX Examples

Config: `uptimeLowerLimitInMs: 2000`, `uptimeMidLimitInMs: 5000`, `uptimeUpperLimitInMs: 10000`

| Interaction | Duration | Score | Category |
|---|---|---|---|
| Checkout | 1.5s | 1.0 | `Excellent` |
| Checkout | 3s | 0.75 | `Good` |
| Checkout | 7s | 0.5 | `Average` |
| Checkout | 15s | 0.0 | `Poor` |
| Checkout (TIMEOUT) | any | 0.0 | `Poor` |
| Checkout (SEQUENCE_VIOLATION) | any | 0.0 | `Poor` |

---

## Edge Cases

| Case | Handling |
|---|---|
| Threshold fields not in config | Default: lower=2000, mid=5000, upper=10000 |
| Duration = 0 | Valid; classified as `Excellent` |
| Errored interaction | Always `Poor` / `0.0`; `error.type` + `error.message` set |
| No matched events in result | Shouldn't happen (first step must match to start); `span.end()` still called |
| `context.with(ROOT_CONTEXT)` | Always available from `@opentelemetry/api`; no fallback needed |

---

## Testing

```typescript
describe('InteractionSpanBuilder', () => {
  it('uses pulse.interaction.* attribute prefix', () => {
    const spans = captureSpans();
    new InteractionSpanBuilder(mockTracer).build(completedResult, mockConfig);

    expect(spans[0].attributes['pulse.type']).toBe('interaction');
    expect(spans[0].attributes['pulse.interaction.id']).toBe(completedResult.interactionId);
    expect(spans[0].attributes['pulse.interaction.config.id']).toBe(mockConfig.id);
    expect(spans[0].attributes['pulse.interaction.is_error']).toBe(false);
    // Bare 'interaction.*' keys must NOT be present
    expect(spans[0].attributes['interaction.id']).toBeUndefined();
  });

  it('emits pulse.interaction.complete_time in nanoseconds', () => {
    const spans = captureSpans();
    const result = { ...completedResult, startTimeMs: 1000, endTimeMs: 2500 }; // 1500ms
    new InteractionSpanBuilder(mockTracer).build(result, mockConfig);
    expect(spans[0].attributes['pulse.interaction.complete_time']).toBe(1_500_000_000);
  });

  it('uses Excellent for duration within lower limit', () => {
    const spans = captureSpans();
    const config = { ...mockConfig, uptimeLowerLimitInMs: 2000, uptimeMidLimitInMs: 5000, uptimeUpperLimitInMs: 10000 };
    const result = { ...completedResult, startTimeMs: 0, endTimeMs: 1500 };
    new InteractionSpanBuilder(mockTracer).build(result, config);
    expect(spans[0].attributes['pulse.interaction.user_category']).toBe('Excellent');
    expect(spans[0].attributes['pulse.interaction.apdex_score']).toBe(1.0);
  });

  it('uses Good for duration within mid limit', () => {
    const spans = captureSpans();
    const config = { ...mockConfig, uptimeLowerLimitInMs: 2000, uptimeMidLimitInMs: 5000, uptimeUpperLimitInMs: 10000 };
    const result = { ...completedResult, startTimeMs: 0, endTimeMs: 3000 };
    new InteractionSpanBuilder(mockTracer).build(result, config);
    expect(spans[0].attributes['pulse.interaction.user_category']).toBe('Good');
    expect(spans[0].attributes['pulse.interaction.apdex_score']).toBe(0.75);
  });

  it('sets error fields and Poor/0.0 on errored result', () => {
    const spans = captureSpans();
    new InteractionSpanBuilder(mockTracer).build(timeoutResult, mockConfig);
    expect(spans[0].attributes['pulse.interaction.is_error']).toBe(true);
    expect(spans[0].attributes['pulse.interaction.error.type']).toBe('TIMEOUT');
    expect(spans[0].attributes['pulse.interaction.user_category']).toBe('Poor');
    expect(spans[0].attributes['pulse.interaction.apdex_score']).toBe(0.0);
    expect(spans[0].status.code).toBe(SpanStatusCode.ERROR);
  });

  it('adds span events for each matched step', () => {
    const spans = captureSpans();
    new InteractionSpanBuilder(mockTracer).build({
      ...completedResult,
      events: [
        { name: 'step_1', timestampMs: 1000 },
        { name: 'step_2', timestampMs: 1800 },
      ],
    }, mockConfig);
    expect(spans[0].events).toHaveLength(2);
    expect(spans[0].events[0].name).toBe('step_1');
    expect(spans[0].events[1].name).toBe('step_2');
  });

  it('span has no parent (ROOT_CONTEXT)', () => {
    const spans = captureSpans();
    new InteractionSpanBuilder(mockTracer).build(completedResult, mockConfig);
    expect(spans[0].parentSpanId).toBeUndefined();
  });
});
```

---

## Done Criteria

- [ ] All attributes use `pulse.interaction.*` prefix — no bare `interaction.*` keys anywhere
- [ ] `pulse.interaction.user_category` ∈ `{Excellent, Good, Average, Poor}` — `satisfied`/`tolerating`/`frustrated` must not appear
- [ ] `pulse.interaction.complete_time` in nanoseconds (`durationMs * 1_000_000`)
- [ ] `pulse.interaction.is_error` (bool) set on every span
- [ ] `pulse.interaction.error.type` + `pulse.interaction.error.message` set only when `is_error = true`
- [ ] Errored interactions always produce `Poor` / `0.0` regardless of actual duration
- [ ] Span events added for each matched step via `span.addEvent()` — no JSON string attribute
- [ ] Span started with `ROOT_CONTEXT` (no auto-parent) and verified in test
- [ ] All unit tests passing
