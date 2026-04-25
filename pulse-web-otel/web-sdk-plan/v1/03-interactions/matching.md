# 03.2 — Interaction Matching Algorithm

**Goal:** Implement a pure state-machine that consumes `trackEvent()` calls, matches them against interaction step sequences from config, and transitions interactions from IDLE → ONGOING → COMPLETED/ERROR — the direct TypeScript port of the Android `InteractionMatcher`.

**File:** `src/interactions/interaction-matcher.ts`
**Android equivalent:** `InteractionMatcher.kt`, `InteractionTracker.kt`

---

## State Machine

```
                    ┌─────────────────────────────────┐
                    │         IDLE                     │
                    │  Waiting for first step match    │
                    └──────────────┬──────────────────┘
                                   │ first required step matches
                                   ▼
                    ┌─────────────────────────────────┐
                    │         ONGOING                  │
                    │  Collecting steps, timer running │
                    └───┬────────────┬────────────────┘
                        │            │
              all required     timeout_ms exceeded
              steps matched    OR unrecoverable error
                        │            │
                        ▼            ▼
               COMPLETED          ERROR
```

### State Definitions

| State | Description |
|---|---|
| `IDLE` | No active tracking; waiting for first step |
| `ONGOING` | First step matched; collecting subsequent steps |
| `COMPLETED` | All required steps matched in order |
| `ERROR` | Timed out or explicitly aborted |

---

## Implementation

```typescript
// src/interactions/interaction-matcher.ts

export type InteractionState = 'IDLE' | 'ONGOING' | 'COMPLETED' | 'ERROR';

interface ActiveInteraction {
  definition: InteractionDefinition;
  state: InteractionState;
  startTime: number;              // performance.now() at first step
  stepsCompleted: string[];       // event names matched so far
  nextRequiredIndex: number;      // index into definition.steps for next required step
  timeoutHandle: ReturnType<typeof setTimeout>;
}

export class InteractionMatcher {
  private active = new Map<string, ActiveInteraction>();  // keyed by interaction.id

  constructor(
    private readonly onComplete: (result: InteractionResult) => void,
    private readonly onError: (result: InteractionResult) => void,
  ) {}

  /** Called when config is loaded or refreshed */
  updateConfig(config: InteractionConfig): void {
    // Cancel interactions that no longer exist in config
    for (const [id, active] of this.active) {
      if (!config.interactions.find(i => i.id === id)) {
        this.abort(id, 'config_updated');
      }
    }
  }

  /** Main entry point — called by PulseSDK.trackEvent() */
  trackEvent(eventName: string, attributes: Record<string, unknown>, config: InteractionConfig): void {
    for (const definition of config.interactions) {
      this.processEvent(definition, eventName, attributes);
    }
  }

  // ─── Private ──────────────────────────────────────────────────────────────

  private processEvent(
    definition: InteractionDefinition,
    eventName: string,
    attributes: Record<string, unknown>,
  ): void {
    const active = this.active.get(definition.id);

    if (!active) {
      // IDLE — check if this event matches the first required step
      const firstRequired = definition.steps.find(s => s.required);
      if (firstRequired && this.stepMatches(firstRequired, eventName, attributes)) {
        this.startInteraction(definition, eventName);
      }
      return;
    }

    if (active.state !== 'ONGOING') return;

    // Find the next expected step
    const currentStep = definition.steps[active.nextRequiredIndex];
    if (!currentStep) return;

    if (this.stepMatches(currentStep, eventName, attributes)) {
      active.stepsCompleted.push(eventName);
      active.nextRequiredIndex = this.nextRequiredStepIndex(definition, active.nextRequiredIndex + 1);

      if (active.nextRequiredIndex >= definition.steps.length) {
        // All required steps matched
        this.completeInteraction(active);
      }
    }
  }

  private startInteraction(definition: InteractionDefinition, firstEvent: string): void {
    const timeoutHandle = setTimeout(() => {
      this.failInteraction(definition.id, 'timeout');
    }, definition.timeout_ms);

    this.active.set(definition.id, {
      definition,
      state: 'ONGOING',
      startTime: performance.now(),
      stepsCompleted: [firstEvent],
      nextRequiredIndex: this.nextRequiredStepIndex(definition, 1),
      timeoutHandle,
    });
  }

  private completeInteraction(active: ActiveInteraction): void {
    clearTimeout(active.timeoutHandle);
    active.state = 'COMPLETED';

    const duration = performance.now() - active.startTime;
    this.onComplete({
      interaction: active.definition,
      state: 'COMPLETED',
      duration,
      stepsCompleted: active.stepsCompleted,
      errorReason: null,
    });

    this.active.delete(active.definition.id);
  }

  private failInteraction(id: string, reason: string): void {
    const active = this.active.get(id);
    if (!active) return;

    clearTimeout(active.timeoutHandle);
    active.state = 'ERROR';

    const duration = performance.now() - active.startTime;
    this.onError({
      interaction: active.definition,
      state: 'ERROR',
      duration,
      stepsCompleted: active.stepsCompleted,
      errorReason: reason,
    });

    this.active.delete(id);
  }

  private abort(id: string, reason: string): void {
    this.failInteraction(id, reason);
  }

  /** Check if an event + attributes match a step definition */
  private stepMatches(
    step: InteractionStep,
    eventName: string,
    attributes: Record<string, unknown>,
  ): boolean {
    if (step.event_name !== eventName) return false;
    if (!step.attributes) return true;

    // All defined attribute filters must match
    return Object.entries(step.attributes).every(
      ([key, expected]) => attributes[key] === expected
    );
  }

  /** Find the index of the next required step at or after `fromIndex` */
  private nextRequiredStepIndex(definition: InteractionDefinition, fromIndex: number): number {
    for (let i = fromIndex; i < definition.steps.length; i++) {
      if (definition.steps[i].required) return i;
    }
    return definition.steps.length; // past the end = all required steps done
  }
}

export interface InteractionResult {
  interaction: InteractionDefinition;
  state: 'COMPLETED' | 'ERROR';
  duration: number;               // ms (performance.now() difference)
  stepsCompleted: string[];
  errorReason: string | null;
}
```

---

## Step Matching Rules

1. **Event name must match exactly** — case-sensitive string comparison
2. **Attribute filters are AND conditions** — all specified attributes must match
3. **Required vs optional steps** — optional steps are skipped if not received; required steps must appear in order
4. **Ordering is strict for required steps** — step 3 cannot be matched before step 1 and 2
5. **Optional steps between required ones** — may or may not appear; the matcher advances past them

### Example: Optional Step Handling

```
Definition steps: [cart_viewed (R), promo_applied (O), checkout_started (R), order_placed (R)]

Events received:  cart_viewed → checkout_started → order_placed
Result: COMPLETED (promo_applied was optional, correctly skipped)

Events received:  cart_viewed → promo_applied → order_placed
Result: ERROR (checkout_started was required, timed out waiting)
```

---

## Concurrent Interactions

Multiple interactions can be ONGOING simultaneously (e.g. a user navigating through two parallel flows). The `active` map holds one entry per `definition.id`. Receiving an event that matches multiple interactions' current step advances both independently.

---

## Edge Cases

| Case | Handling |
|---|---|
| Same event matches first step of two interactions | Both start simultaneously |
| `trackEvent()` called before config loads | Config is null; matcher is a no-op until `updateConfig()` is called |
| Interaction times out | `failInteraction('timeout')` clears timer and calls `onError` |
| User closes tab mid-interaction | Timeout fires if tab is reopened within `timeout_ms`; otherwise interaction is dropped |
| Config refresh invalidates in-progress interaction | `abort('config_updated')` emits ERROR result |
| Step with attribute filter receives event without that attribute | `attributes[key] === expected` — if attribute is missing, `undefined !== expected` → no match |

---

## Testing

### Unit Tests (Vitest)

```typescript
it('transitions IDLE → ONGOING on first required step', () => {
  const matcher = new InteractionMatcher(vi.fn(), vi.fn());
  matcher.updateConfig(mockConfig);
  matcher.trackEvent('cart_viewed', {}, mockConfig);
  // No assertion on external state — verify via onComplete/onError callbacks
});

it('completes interaction when all required steps matched', () => {
  const onComplete = vi.fn();
  const matcher = new InteractionMatcher(onComplete, vi.fn());
  matcher.updateConfig(mockConfig);  // checkout_flow with 3 required steps

  matcher.trackEvent('cart_viewed', {}, mockConfig);
  matcher.trackEvent('checkout_started', {}, mockConfig);
  matcher.trackEvent('order_placed', {}, mockConfig);

  expect(onComplete).toHaveBeenCalledOnce();
  expect(onComplete.mock.calls[0][0].state).toBe('COMPLETED');
  expect(onComplete.mock.calls[0][0].stepsCompleted).toEqual([
    'cart_viewed', 'checkout_started', 'order_placed',
  ]);
});

it('errors on timeout', async () => {
  vi.useFakeTimers();
  const onError = vi.fn();
  const matcher = new InteractionMatcher(vi.fn(), onError);
  matcher.updateConfig(mockConfig);  // timeout_ms: 5000

  matcher.trackEvent('cart_viewed', {}, mockConfig);  // starts interaction
  vi.advanceTimersByTime(6000);

  expect(onError).toHaveBeenCalledOnce();
  expect(onError.mock.calls[0][0].errorReason).toBe('timeout');
  vi.useRealTimers();
});

it('skips optional steps correctly', () => {
  const onComplete = vi.fn();
  const matcher = new InteractionMatcher(onComplete, vi.fn());
  matcher.updateConfig(mockConfigWithOptionalStep);

  matcher.trackEvent('cart_viewed', {}, mockConfigWithOptionalStep);    // required
  // promo_applied (optional) — skipped
  matcher.trackEvent('checkout_started', {}, mockConfigWithOptionalStep); // required
  matcher.trackEvent('order_placed', {}, mockConfigWithOptionalStep);     // required

  expect(onComplete).toHaveBeenCalledOnce();
});

it('matches step attributes exactly', () => {
  const onComplete = vi.fn();
  const matcher = new InteractionMatcher(onComplete, vi.fn());
  const config = mockConfigWithAttrFilter; // step requires { channel: 'organic' }

  matcher.trackEvent('checkout_started', { channel: 'paid' }, config);   // wrong attr
  matcher.trackEvent('checkout_started', { channel: 'organic' }, config); // correct

  // Only the second event starts the interaction
  // ... then complete it
  matcher.trackEvent('order_placed', {}, config);
  expect(onComplete).toHaveBeenCalledOnce();
});
```

---

## Done Criteria

- [ ] `trackEvent()` starts an interaction when the first required step matches
- [ ] All required steps matched in order → `COMPLETED` result
- [ ] Timeout exceeded → `ERROR` result with `errorReason: 'timeout'`
- [ ] Optional steps correctly skipped
- [ ] Attribute filters applied (AND logic)
- [ ] Multiple concurrent interactions tracked independently
- [ ] Config refresh aborts active interactions no longer in config
- [ ] Duration measured accurately with `performance.now()`
- [ ] All unit tests passing
