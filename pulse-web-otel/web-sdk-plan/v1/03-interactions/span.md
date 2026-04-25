# 03.3 — Interaction Span Output

**Goal:** Convert `InteractionResult` objects from the matching engine (03.2) into OTLP spans with APDEX scoring, user category classification, and the full attribute contract — matching the Android `InteractionSpanBuilder`.

**File:** `src/interactions/interaction-span.ts`
**Android equivalent:** `InteractionSpanBuilder.kt`, `ApdexCalculator.kt`

---

## Signals Produced

### `pulse.type: interaction` — one span per completed or errored interaction

| Attribute | Type | Source | Android Equivalent |
|---|---|---|---|
| `pulse.type` | string | `"interaction"` | `pulse.type: interaction` |
| `interaction.id` | string | `definition.id` | `interaction.id` |
| `interaction.name` | string | `definition.name` | `interaction.name` |
| `interaction.status` | string | `"completed"` \| `"error"` | `interaction.status` |
| `interaction.duration` | long | `result.duration` (ms, rounded) | `interaction.duration` |
| `interaction.error_reason` | string | `result.errorReason ?? ""` | `interaction.error_reason` |
| `interaction.steps_completed` | string | JSON array of step names | `interaction.steps_completed` |
| `interaction.apdex_score` | double | Calculated (0.0–1.0) | `interaction.apdex_score` |
| `interaction.user_category` | string | `"satisfied"` \| `"tolerating"` \| `"frustrated"` | `interaction.user_category` |
| `interaction.apdex_threshold` | long | `definition.apdex_threshold_ms` | `interaction.apdex_threshold` |

---

## APDEX Scoring

APDEX (Application Performance Index) is a standardised score (0.0–1.0) measuring user satisfaction based on response time.

### Formula

```
satisfied_count  = interactions where duration ≤ T
tolerating_count = interactions where T < duration ≤ 4T
frustrated_count = interactions where duration > 4T

APDEX = (satisfied_count + tolerating_count / 2) / total_count
```

For a single interaction (count = 1), this simplifies to:

| Duration | Category | APDEX |
|---|---|---|
| ≤ T | `satisfied` | `1.0` |
| T < d ≤ 4T | `tolerating` | `0.5` |
| > 4T | `frustrated` | `0.0` |

Where **T = `definition.apdex_threshold_ms`**.

### Implementation

```typescript
function calculateApdex(
  durationMs: number,
  thresholdMs: number,
): { score: number; category: 'satisfied' | 'tolerating' | 'frustrated' } {
  if (durationMs <= thresholdMs) {
    return { score: 1.0, category: 'satisfied' };
  } else if (durationMs <= thresholdMs * 4) {
    return { score: 0.5, category: 'tolerating' };
  } else {
    return { score: 0.0, category: 'frustrated' };
  }
}
```

---

## Full Span Builder Implementation

```typescript
// src/interactions/interaction-span.ts

export class InteractionSpanBuilder {
  constructor(private readonly tracer: Tracer) {}

  buildFromResult(result: InteractionResult): void {
    const { interaction, duration, state, stepsCompleted, errorReason } = result;

    const durationMs = Math.round(duration);
    const startTime = Date.now() - durationMs;

    const apdex = state === 'COMPLETED'
      ? calculateApdex(durationMs, interaction.apdex_threshold_ms)
      : { score: 0.0, category: 'frustrated' as const };

    const span = this.tracer.startSpan(interaction.name, {
      startTime,
      kind: SpanKind.CLIENT,
    });

    span.setAttributes({
      'pulse.type':                   'interaction',
      'interaction.id':               interaction.id,
      'interaction.name':             interaction.name,
      'interaction.status':           state.toLowerCase(),
      'interaction.duration':         durationMs,
      'interaction.error_reason':     errorReason ?? '',
      'interaction.steps_completed':  JSON.stringify(stepsCompleted),
      'interaction.apdex_score':      apdex.score,
      'interaction.user_category':    apdex.category,
      'interaction.apdex_threshold':  interaction.apdex_threshold_ms,
    });

    if (state === 'ERROR') {
      span.setStatus({
        code: SpanStatusCode.ERROR,
        message: errorReason ?? 'unknown',
      });
    } else {
      span.setStatus({ code: SpanStatusCode.OK });
    }

    span.end(Date.now());
  }
}
```

---

## Wiring It Together — `InteractionManager`

The `InteractionManager` ties together the config fetcher (03.1), matcher (03.2), and span builder (03.3):

```typescript
// src/interactions/interaction-manager.ts

export class InteractionManager {
  private matcher: InteractionMatcher;
  private spanBuilder: InteractionSpanBuilder;
  private configFetcher: InteractionConfigFetcher;
  private currentConfig: InteractionConfig | null = null;

  constructor(tracer: Tracer, projectId: string, cdnBaseUrl: string) {
    this.matcher = new InteractionMatcher(
      result => this.spanBuilder.buildFromResult(result),   // onComplete
      result => this.spanBuilder.buildFromResult(result),   // onError
    );
    this.spanBuilder = new InteractionSpanBuilder(tracer);
    this.configFetcher = new InteractionConfigFetcher(
      `${cdnBaseUrl}/interactions/${projectId}.json`,
      projectId,
    );

    this.configFetcher.onChange(config => {
      this.currentConfig = config;
      this.matcher.updateConfig(config);
    });
  }

  async init(): Promise<void> {
    await this.configFetcher.init();
    this.currentConfig = this.configFetcher.getConfig();
    if (this.currentConfig) {
      this.matcher.updateConfig(this.currentConfig);
    }
  }

  /** Called by public SDK API: PulseSDK.trackEvent(name, attributes) */
  trackEvent(eventName: string, attributes: Record<string, unknown> = {}): void {
    if (!this.currentConfig) return;
    this.matcher.trackEvent(eventName, attributes, this.currentConfig);
  }

  destroy(): void {
    this.configFetcher.destroy();
  }
}
```

### Public SDK API

```typescript
// In the main SDK entry point:
pulse.trackEvent('order_placed', {
  order_id: '12345',
  channel:  'organic',
});
```

---

## APDEX Examples

| Interaction | Duration | Threshold | Score | Category |
|---|---|---|---|---|
| Checkout Flow | 3s | 5s | 1.0 | `satisfied` |
| Checkout Flow | 8s | 5s | 0.5 | `tolerating` |
| Checkout Flow | 25s | 5s | 0.0 | `frustrated` |
| Checkout Flow (timeout) | 120s (timeout) | 5s | 0.0 | `frustrated` |

---

## Edge Cases

| Case | Handling |
|---|---|
| APDEX threshold not set in config | Default to `3000ms` (3 seconds) as a sensible fallback |
| `result.duration` is `0` (instant completion) | Valid — score is `satisfied` (≤ threshold) |
| ERROR result (timeout) | APDEX `frustrated: 0.0` regardless of duration; span status = ERROR |
| `stepsCompleted` is empty | Shouldn't happen (first step must match to start); stored as `"[]"` |
| Span end time before start time | Guard: `endTime = Math.max(startTime + 1, Date.now())` |

---

## Testing

### Unit Tests (Vitest)

```typescript
describe('calculateApdex', () => {
  it('returns satisfied for duration ≤ threshold', () => {
    expect(calculateApdex(3000, 5000)).toEqual({ score: 1.0, category: 'satisfied' });
  });

  it('returns tolerating for threshold < duration ≤ 4T', () => {
    expect(calculateApdex(8000, 5000)).toEqual({ score: 0.5, category: 'tolerating' });
  });

  it('returns frustrated for duration > 4T', () => {
    expect(calculateApdex(25000, 5000)).toEqual({ score: 0.0, category: 'frustrated' });
  });

  it('handles duration exactly at threshold', () => {
    expect(calculateApdex(5000, 5000)).toEqual({ score: 1.0, category: 'satisfied' });
  });

  it('handles duration exactly at 4T', () => {
    expect(calculateApdex(20000, 5000)).toEqual({ score: 0.5, category: 'tolerating' });
  });
});

describe('InteractionSpanBuilder', () => {
  it('emits interaction span with correct attributes on COMPLETED', () => {
    const spans = captureSpans();
    const builder = new InteractionSpanBuilder(mockTracer);

    builder.buildFromResult({
      interaction: mockDefinition,   // apdex_threshold_ms: 5000
      state: 'COMPLETED',
      duration: 3200,
      stepsCompleted: ['cart_viewed', 'checkout_started', 'order_placed'],
      errorReason: null,
    });

    expect(spans[0]['pulse.type']).toBe('interaction');
    expect(spans[0]['interaction.status']).toBe('completed');
    expect(spans[0]['interaction.apdex_score']).toBe(1.0);
    expect(spans[0]['interaction.user_category']).toBe('satisfied');
  });

  it('sets span status ERROR on failed interaction', () => {
    const spans = captureSpans();
    const builder = new InteractionSpanBuilder(mockTracer);

    builder.buildFromResult({
      interaction: mockDefinition,
      state: 'ERROR',
      duration: 120000,
      stepsCompleted: ['cart_viewed'],
      errorReason: 'timeout',
    });

    expect(spans[0]['interaction.status']).toBe('error');
    expect(spans[0]['interaction.error_reason']).toBe('timeout');
    expect(spans[0]['interaction.apdex_score']).toBe(0.0);
  });

  it('serialises stepsCompleted as JSON array string', () => {
    const spans = captureSpans();
    const builder = new InteractionSpanBuilder(mockTracer);

    builder.buildFromResult({ ...completedResult, stepsCompleted: ['a', 'b', 'c'] });
    expect(spans[0]['interaction.steps_completed']).toBe('["a","b","c"]');
  });
});
```

### E2E (Playwright)

```typescript
test('completes interaction and emits span', async ({ page }) => {
  await page.goto('/test-page');

  // The test page calls pulse.trackEvent() on user actions
  await page.click('[data-testid="view-cart"]');
  await page.click('[data-testid="checkout"]');
  await page.click('[data-testid="place-order"]');

  const span = await waitForSpan(receiver, 'interaction');
  expect(span['interaction.id']).toBe('checkout_flow');
  expect(span['interaction.status']).toBe('completed');
  expect(span['interaction.apdex_score']).toBeGreaterThanOrEqual(0);
});
```

---

## Done Criteria

- [ ] `COMPLETED` interaction emits `interaction` span with all 10 attributes
- [ ] `ERROR` interaction emits span with `interaction.status: 'error'` and `interaction.error_reason`
- [ ] APDEX score `1.0 / 0.5 / 0.0` calculated correctly from duration vs threshold
- [ ] `interaction.user_category` matches APDEX bands
- [ ] `interaction.steps_completed` serialised as JSON array string
- [ ] Span `startTime` reconstructed from `Date.now() - duration`
- [ ] Span status set to `OK` for completed, `ERROR` for errored interactions
- [ ] `InteractionManager.trackEvent()` wires config → matcher → span builder end-to-end
- [ ] All unit tests passing
