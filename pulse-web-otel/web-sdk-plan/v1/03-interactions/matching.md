# 03.2 — Interaction Matching Algorithm

**Goal:** Implement a pure, synchronous state machine that consumes `trackEvent()` calls, matches them against interaction step sequences from config, and transitions interactions from IDLE → ONGOING → COMPLETED/ERROR — the TypeScript port of Android `InteractionEventsTracker` + `InteractionUtil`.

**Files:** `src/interactions/interaction-models.ts` (types) · `src/interactions/interaction-tracker.ts` (per-config tracker) · `src/interactions/interaction-coordinator.ts` (fan-out)  
**Android equivalents:** `InteractionEventsTracker.kt` · `InteractionUtil.kt` · `InteractionManager.kt`

> **Source of truth:** Android `InteractionEventsTracker` + `InteractionUtil`. When this doc disagrees with Android behavior, follow Android.

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
                    │  Inter-step timer running        │
                    └───┬────────────┬────────────────┘
                        │            │
              all required     inter-step timer expired
              steps matched    OR globalBlacklisted event
                        │     OR SEQUENCE_VIOLATION
                        │            │
                        ▼            ▼
               COMPLETED         ERROR (emits error span)
```

### State Definitions

| State | Description |
|---|---|
| `IDLE` | No active tracking; waiting for first step |
| `ONGOING` | First step matched; inter-step timer running |
| `COMPLETED` | All required steps matched in order |
| `ERROR` | Inter-step timer expired, blacklisted event received, or sequence violation |

---

## Key Behavioral Rules (Android Parity)

### 1. Inter-step timer — not a whole-flow timer

The timeout (`thresholdInMs`) is **not** a whole-flow timer. It **resets after every step match**:

- On step match → `clearTimeout` existing timer, set new `setTimeout(thresholdInMs + 10)`
- On expiry → emit **error interaction span** with `errorType: 'TIMEOUT'`
- A single first-step match with no follow-up will time out after `thresholdInMs`, not after some total-flow budget

Android reference: `InteractionEventsTracker.launchResetTimer()` — cancels and replaces the coroutine Job on each call.

### 2. Global blacklist event resets the ongoing match — no error span

If an event whose name is in `config.globalBlacklistedEvents` arrives while `ONGOING`:
1. **Reset** the tracker to `IDLE` (no error span emitted)
2. Clear the inter-step timer

The event is NOT just skipped — the entire ongoing match is silently discarded.

Android reference: `InteractionEventsTracker.checkAndAdd()` checks `globalBlacklistedEvents` before any other logic.

### 3. Sequence violation → error interaction span

If an `ONGOING` tracker receives an event that:
- Is NOT the next expected step
- Is NOT in `globalBlacklistedEvents`
- Does NOT match the first step of this interaction (see rule 4)

Then: emit **error interaction span** with `errorType: 'SEQUENCE_VIOLATION'`, reset to `IDLE`.

Android reference: `InteractionEventsTracker.createErrorInteraction('SEQUENCE_VIOLATION')`.

### 4. `shouldTakeFirstEvent` — overlapping restart

When `ONGOING` and a non-matching event arrives, before declaring a violation, check:

> Does this event match the **first required step** of this interaction?

If yes: the user restarted the flow mid-sequence.
- Emit a **`SEQUENCE_VIOLATION` error span** for the abandoned match
- Start a **fresh match** from this event (it becomes step 1 of the new attempt)

If no: emit `SEQUENCE_VIOLATION` and go `IDLE` (no restart).

Android reference: `InteractionUtil.matchSequence()` returns `MatchResult.shouldTakeFirstEvent` — the caller then re-runs `startInteraction` with the same event.

### 5. Synchronous fan-out — no async

Unlike Android (Kotlin Coroutines + `StateFlow`), web is **synchronous on the main thread**:
- `InteractionCoordinator.onTrackEvent()` loops synchronously over all `InteractionTracker` instances
- `InteractionTracker.checkAndAdd()` is synchronous — span emission callbacks fire synchronously
- Only `setTimeout`/`clearTimeout` are async (for inter-step timeout detection only)
- No event queues, no microtask tricks

This means `PulseWeb.trackEvent()` is a synchronous call that may synchronously emit a span. That is correct and expected.

### 6. Timestamp parameter

`PulseWeb.trackEvent(name, attrs?, timestampMs?)`:
- `timestampMs` — optional Unix epoch ms; mirrors Android `addEvent(name, params, eventTimeInNano)` defaulting to `System.currentTimeMillis() * 1_000_000`
- Default: `Date.now()` if not provided
- Used for span event timestamps and `pulse.interaction.complete_time` calculation (converted to nanos at span build: `durationMs * 1_000_000`)

---

## TypeScript Types

```typescript
// src/interactions/interaction-models.ts

export type PropertyOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'CONTAINS'
  | 'NOT_CONTAINS'
  | 'STARTS_WITH'
  | 'ENDS_WITH';

export interface PropertyFilter {
  key: string;
  value: string;
  operator: PropertyOperator;
}

export interface InteractionEvent {
  name: string;
  required: boolean;
  isBlacklisted?: boolean;
  props?: PropertyFilter[];
}

export interface InteractionConfig {
  id: string;
  name: string;
  events: InteractionEvent[];
  thresholdInMs: number;             // inter-step timeout (not whole-flow)
  uptimeLowerLimitInMs: number;      // ≤ this → Excellent
  uptimeMidLimitInMs: number;        // ≤ this → Good
  uptimeUpperLimitInMs: number;      // ≤ this → Average; above → Poor
  globalBlacklistedEvents: string[];
}

export type MatchErrorType = 'TIMEOUT' | 'SEQUENCE_VIOLATION';

export interface InteractionResult {
  configId: string;
  configName: string;
  interactionId: string;
  events: Array<{ name: string; timestampMs: number }>;
  startTimeMs: number;
  endTimeMs: number;
  isError: false;
}

export interface InteractionErrorResult {
  configId: string;
  configName: string;
  interactionId: string;
  events: Array<{ name: string; timestampMs: number }>;
  startTimeMs: number;
  endTimeMs: number;
  isError: true;
  errorType: MatchErrorType;
  errorMessage: string;
}

export type AnyInteractionResult = InteractionResult | InteractionErrorResult;
```

---

## Implementation

### `InteractionTracker` (per-config)

```typescript
// src/interactions/interaction-tracker.ts

export class InteractionTracker {
  private ongoing = false;
  private stepIndex = 0;
  private interactionId = '';
  private matchedEvents: Array<{ name: string; timestampMs: number }> = [];
  private startTimeMs = 0;
  private interStepTimerId: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly config: InteractionConfig,
    private readonly onResult: (result: AnyInteractionResult) => void,
  ) {}

  checkAndAdd(eventName: string, props: Record<string, unknown>, timestampMs: number): void {
    // Rule 2: global blacklist always takes priority
    if (this.config.globalBlacklistedEvents.includes(eventName)) {
      if (this.ongoing) this.resetToIdle();
      return;
    }

    if (!this.ongoing) {
      const first = this.findFirstRequired();
      if (first && this.eventMatches(first, eventName, props)) {
        this.startMatch(eventName, timestampMs);
      }
      return;
    }

    // ONGOING: find the next required step
    const nextExpected = this.config.events[this.stepIndex];
    if (!nextExpected) return;

    if (this.eventMatches(nextExpected, eventName, props)) {
      // Step matches — advance
      this.matchedEvents.push({ name: eventName, timestampMs });
      this.stepIndex = this.advanceToNextRequired(this.stepIndex + 1);
      this.resetInterStepTimer();

      if (this.stepIndex >= this.config.events.length) {
        this.completeMatch(timestampMs);
      }
    } else {
      // Rule 4: does this event restart the flow? (shouldTakeFirstEvent)
      const first = this.findFirstRequired();
      if (first && this.eventMatches(first, eventName, props)) {
        // Emit violation for the abandoned match, then restart from this event
        this.emitError(
          'SEQUENCE_VIOLATION',
          `Flow restarted: expected ${nextExpected.name}, got ${eventName}`,
          timestampMs,
        );
        this.startMatch(eventName, timestampMs);
        return;
      }
      // Rule 3: true sequence violation
      this.emitError(
        'SEQUENCE_VIOLATION',
        `Expected ${nextExpected.name}, got ${eventName}`,
        timestampMs,
      );
    }
  }

  shutdown(): void {
    this.clearInterStepTimer();
    this.resetToIdle();
  }

  // ─── Private ──────────────────────────────────────────────────────────────

  private startMatch(firstEvent: string, timestampMs: number): void {
    this.ongoing = true;
    this.interactionId = crypto.randomUUID();
    this.startTimeMs = timestampMs;
    this.matchedEvents = [{ name: firstEvent, timestampMs }];
    this.stepIndex = this.advanceToNextRequired(1);
    this.resetInterStepTimer();
  }

  private completeMatch(endTimeMs: number): void {
    this.clearInterStepTimer();
    const result: AnyInteractionResult = {
      configId: this.config.id,
      configName: this.config.name,
      interactionId: this.interactionId,
      events: [...this.matchedEvents],
      startTimeMs: this.startTimeMs,
      endTimeMs,
      isError: false,
    };
    this.resetToIdle();
    this.onResult(result);
  }

  private emitError(type: MatchErrorType, message: string, endTimeMs: number): void {
    this.clearInterStepTimer();
    const result: AnyInteractionResult = {
      configId: this.config.id,
      configName: this.config.name,
      interactionId: this.interactionId,
      events: [...this.matchedEvents],
      startTimeMs: this.startTimeMs,
      endTimeMs,
      isError: true,
      errorType: type,
      errorMessage: message,
    };
    this.resetToIdle();
    this.onResult(result);
  }

  private resetToIdle(): void {
    this.ongoing = false;
    this.stepIndex = 0;
    this.matchedEvents = [];
    this.interactionId = '';
    this.clearInterStepTimer();
  }

  // Rule 1: inter-step timer resets on every step advance
  private resetInterStepTimer(): void {
    this.clearInterStepTimer();
    this.interStepTimerId = setTimeout(() => {
      this.emitError(
        'TIMEOUT',
        `No next step within ${this.config.thresholdInMs}ms`,
        Date.now(),
      );
    }, this.config.thresholdInMs + 10);
  }

  private clearInterStepTimer(): void {
    if (this.interStepTimerId !== null) {
      clearTimeout(this.interStepTimerId);
      this.interStepTimerId = null;
    }
  }

  private findFirstRequired(): InteractionEvent | undefined {
    return this.config.events.find(e => e.required && !e.isBlacklisted);
  }

  private advanceToNextRequired(fromIndex: number): number {
    for (let i = fromIndex; i < this.config.events.length; i++) {
      if (this.config.events[i].required) return i;
    }
    return this.config.events.length;
  }

  private eventMatches(expected: InteractionEvent, name: string, props: Record<string, unknown>): boolean {
    if (expected.name !== name) return false;
    if (!expected.props || expected.props.length === 0) return true;
    return expected.props.every(f => this.propMatches(f, props));
  }

  private propMatches(filter: PropertyFilter, props: Record<string, unknown>): boolean {
    const actual = String(props[filter.key] ?? '');
    const expected = filter.value;
    switch (filter.operator) {
      case 'EQUALS':       return actual === expected;
      case 'NOT_EQUALS':   return actual !== expected;
      case 'CONTAINS':     return actual.includes(expected);
      case 'NOT_CONTAINS': return !actual.includes(expected);
      case 'STARTS_WITH':  return actual.startsWith(expected);
      case 'ENDS_WITH':    return actual.endsWith(expected);
    }
  }
}
```

### `InteractionCoordinator` (fan-out)

```typescript
// src/interactions/interaction-coordinator.ts

export class InteractionCoordinator {
  private trackers: InteractionTracker[] = [];

  setConfigs(configs: InteractionConfig[], onResult: (r: AnyInteractionResult) => void): void {
    this.shutdown();
    this.trackers = configs.map(cfg => new InteractionTracker(cfg, onResult));
  }

  /** Synchronous fan-out — called from PulseWeb.trackEvent() */
  onTrackEvent(name: string, props: Record<string, unknown> = {}, timestampMs = Date.now()): void {
    for (const tracker of this.trackers) {
      tracker.checkAndAdd(name, props, timestampMs);
    }
  }

  shutdown(): void {
    for (const t of this.trackers) t.shutdown();
    this.trackers = [];
  }
}
```

---

## Property Operator Reference

All comparisons are **case-sensitive string comparisons** (mirrors Android). Non-string prop values are coerced to `String` before matching.

| Operator | Meaning | Example: `key='type'`, `value='shirt'` |
|---|---|---|
| `EQUALS` | Exact match | `'shirt' === 'shirt'` → pass |
| `NOT_EQUALS` | Any other value | `'shoe' !== 'shirt'` → pass |
| `CONTAINS` | Substring present | `'premium_shirt'.includes('shirt')` → pass |
| `NOT_CONTAINS` | Substring absent | `'jacket'.includes('shirt')` → fail |
| `STARTS_WITH` | Prefix match | `'shirt_slim'.startsWith('shirt')` → pass |
| `ENDS_WITH` | Suffix match | `'slim_shirt'.endsWith('shirt')` → pass |

---

## Edge Cases

| Case | Handling |
|---|---|
| Event in `globalBlacklistedEvents` while ONGOING | Reset to IDLE silently — no error span |
| Expected next step doesn't arrive within `thresholdInMs` | Error span with `TIMEOUT`; reset to IDLE |
| Wrong event during ONGOING (not first-step, not blacklisted) | Error span with `SEQUENCE_VIOLATION`; reset to IDLE |
| Wrong event = first step of this interaction | Emit `SEQUENCE_VIOLATION` for old match; restart fresh from this event |
| `trackEvent()` before config loads | Coordinator has no trackers → no-op |
| Config refresh | `setConfigs()` calls `shutdown()` on existing trackers (clears their timers) before creating new ones |
| `PulseWeb.shutdown()` | `InteractionCoordinator.shutdown()` → each `tracker.shutdown()` → all `clearTimeout` fired |
| Page hidden mid-interaction | No special handling at matching layer; timer keeps running (browser allows `setTimeout` in hidden pages) |
| User provides `timestampMs` | Used for event timestamp and span duration; defaults to `Date.now()` |

---

## Testing

```typescript
describe('InteractionTracker — Android parity', () => {
  it('inter-step timer resets on each step, not just at start', () => {
    vi.useFakeTimers();
    const onResult = vi.fn();
    const tracker = new InteractionTracker({ ...mockConfig, thresholdInMs: 1000 }, onResult);

    tracker.checkAndAdd('step_1', {}, 0);
    vi.advanceTimersByTime(900);                    // still within threshold
    tracker.checkAndAdd('step_2', {}, 900);         // advance; timer RESETS
    vi.advanceTimersByTime(900);                    // 900ms after step_2 — still within threshold
    expect(onResult).not.toHaveBeenCalled();
    vi.advanceTimersByTime(200);                    // 1100ms after step_2 → timeout
    expect(onResult).toHaveBeenCalledWith(
      expect.objectContaining({ isError: true, errorType: 'TIMEOUT' })
    );
    vi.useRealTimers();
  });

  it('global blacklist event resets match silently', () => {
    const onResult = vi.fn();
    const config = { ...mockConfig, globalBlacklistedEvents: ['cancel'] };
    const tracker = new InteractionTracker(config, onResult);

    tracker.checkAndAdd('step_1', {}, 0);
    tracker.checkAndAdd('cancel', {}, 100);         // blacklisted → silent reset
    expect(onResult).not.toHaveBeenCalled();

    // Fresh start possible after reset
    tracker.checkAndAdd('step_1', {}, 200);
    tracker.checkAndAdd('step_2', {}, 300);
    expect(onResult).toHaveBeenCalledWith(expect.objectContaining({ isError: false }));
  });

  it('sequence violation emits error span', () => {
    const onResult = vi.fn();
    const tracker = new InteractionTracker(mockConfig, onResult);

    tracker.checkAndAdd('step_1', {}, 0);
    tracker.checkAndAdd('wrong_event', {}, 100);
    expect(onResult).toHaveBeenCalledWith(
      expect.objectContaining({ isError: true, errorType: 'SEQUENCE_VIOLATION' })
    );
  });

  it('first-step during ongoing emits violation then restarts', () => {
    const onResult = vi.fn();
    const tracker = new InteractionTracker(mockConfig, onResult);

    tracker.checkAndAdd('step_1', {}, 0);           // starts match
    tracker.checkAndAdd('step_1', {}, 100);         // first step again → emit violation, restart
    expect(onResult).toHaveBeenCalledWith(
      expect.objectContaining({ isError: true, errorType: 'SEQUENCE_VIOLATION' })
    );
    onResult.mockClear();

    tracker.checkAndAdd('step_2', {}, 200);         // continues the restarted match
    expect(onResult).toHaveBeenCalledWith(expect.objectContaining({ isError: false }));
  });

  it('CONTAINS operator matches substring', () => {
    const config = {
      ...mockConfig,
      events: [{
        name: 'purchase', required: true,
        props: [{ key: 'category', value: 'electronics', operator: 'CONTAINS' as const }],
      }],
    };
    const onResult = vi.fn();
    const tracker = new InteractionTracker(config, onResult);

    tracker.checkAndAdd('purchase', { category: 'premium_electronics' }, 0);
    expect(onResult).toHaveBeenCalledWith(expect.objectContaining({ isError: false }));
  });

  it('shutdown clears inter-step timer', () => {
    vi.useFakeTimers();
    const onResult = vi.fn();
    const tracker = new InteractionTracker(mockConfig, onResult);
    tracker.checkAndAdd('step_1', {}, 0);
    tracker.shutdown();
    vi.advanceTimersByTime(10_000);
    expect(onResult).not.toHaveBeenCalled();
    vi.useRealTimers();
  });

  it('coordinator fan-out: single event advances all matching trackers', () => {
    const onA = vi.fn();
    const onB = vi.fn();
    const coordinator = new InteractionCoordinator();
    coordinator.setConfigs([configA, configB], result => {
      if (result.configId === 'flow_a') onA(result);
      else onB(result);
    });

    coordinator.onTrackEvent('shared_first_step', {});
    coordinator.onTrackEvent('step_a_final', {});      // completes A only
    expect(onA).toHaveBeenCalledWith(expect.objectContaining({ isError: false }));
    expect(onB).not.toHaveBeenCalled();               // B still waiting
  });
});
```

---

## Done Criteria

- [ ] Inter-step timer resets on each step advance (not whole-flow timer)
- [ ] Global blacklist event → silent reset to IDLE, no error span emitted
- [ ] Sequence violation → error span with `errorType: 'SEQUENCE_VIOLATION'`
- [ ] First-step event during ongoing → emit violation for old match, restart fresh
- [ ] All 6 property operators implemented (`EQUALS`/`NOT_EQUALS`/`CONTAINS`/`NOT_CONTAINS`/`STARTS_WITH`/`ENDS_WITH`)
- [ ] `shutdown()` clears all `setTimeout` handles
- [ ] Synchronous fan-out from `InteractionCoordinator.onTrackEvent()`
- [ ] `timestampMs` param on `onTrackEvent` (defaults to `Date.now()`)
- [ ] Config update calls `shutdown()` on existing trackers before creating new ones
- [ ] All unit tests passing
