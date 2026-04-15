# 04.1 — Session Replay Recorder

**Goal:** Record DOM mutations, user interactions, and style changes using rrweb, buffer them in memory with a configurable rolling window, and expose batches to the transport layer (04.3) for upload.

**File:** `src/replay/recorder.ts`
**Android equivalent:** None — web-exclusive capability

---

## How rrweb Works

`rrweb` works by:
1. Taking a **full DOM snapshot** at recording start (serialises the entire document to a JSON tree)
2. Observing subsequent changes via `MutationObserver`, event listeners, and `requestAnimationFrame` hooks
3. Emitting **incremental snapshot events** for each change (attribute mutation, text change, scroll, mouse move, etc.)

A replay player reconstructs the DOM by replaying the initial snapshot + incremental events in sequence.

---

## Buffer Strategy

```
rrweb events
     │
     ▼
┌──────────────────────────────────┐
│  Rolling Buffer (circular array) │
│  max: 5MB or 60s of events       │
│  Oldest events dropped when full │
└──────────┬───────────────────────┘
           │ flush trigger
           ▼
     Transport (04.3)
```

### Flush Triggers

| Trigger | When |
|---|---|
| `pagehide` / tab close | Immediate flush via `sendBeacon` |
| Buffer reaches 5MB | Flush and clear |
| Every 30s (periodic) | Background flush |
| Error detected (01 errors) | Immediate flush (preserve context around error) |
| Manual: `flush()` API | When app calls it |

---

## Implementation

```typescript
// src/replay/recorder.ts
import { record, EventType } from 'rrweb';
import type { eventWithTime } from '@rrweb/types';

const MAX_BUFFER_BYTES = 5 * 1024 * 1024;  // 5MB
const MAX_BUFFER_AGE_MS = 60 * 1000;       // 60 seconds
const PERIODIC_FLUSH_INTERVAL_MS = 30 * 1000;

export class ReplayRecorder {
  private events: eventWithTime[] = [];
  private bufferBytes = 0;
  private stopRecording?: () => void;
  private flushTimer?: ReturnType<typeof setTimeout>;
  private onFlush: (events: eventWithTime[]) => void;

  constructor(
    onFlush: (events: eventWithTime[]) => void,
    private readonly config: ReplayConfig,
  ) {
    this.onFlush = onFlush;
  }

  start(): void {
    this.stopRecording = record({
      emit: (event) => this.onEvent(event),

      // Privacy config — see 04.2
      maskAllInputs: this.config.maskAllInputs ?? true,
      maskInputOptions: this.config.maskInputOptions ?? {},
      maskTextClass: this.config.maskTextClass ?? 'pulse-mask',
      blockClass: this.config.blockClass ?? 'pulse-block',

      // Performance config
      recordCanvas: this.config.recordCanvas ?? false,  // expensive; off by default
      collectFonts: false,
      inlineImages: false,  // don't inline image data — links only
      sampling: {
        mousemove: 50,  // throttle to every 50ms
        scroll: 150,    // throttle scroll events
        input: 'last',  // only final input value, not every keystroke
      },
    });

    // Periodic flush
    this.scheduleFlush();

    // Flush on page hide
    window.addEventListener('pagehide', this.onPageHide);
  }

  stop(): void {
    this.stopRecording?.();
    clearTimeout(this.flushTimer);
    window.removeEventListener('pagehide', this.onPageHide);
  }

  /** Force an immediate flush (e.g. when an error is captured) */
  flush(): void {
    if (this.events.length === 0) return;
    const batch = this.events.splice(0);
    this.bufferBytes = 0;
    this.onFlush(batch);
  }

  // ─── Private ──────────────────────────────────────────────────────────────

  private onEvent = (event: eventWithTime): void => {
    const eventSize = roughSizeOf(event);
    this.events.push(event);
    this.bufferBytes += eventSize;

    // Trim old events if buffer too large
    while (this.bufferBytes > MAX_BUFFER_BYTES && this.events.length > 1) {
      const removed = this.events.shift()!;
      this.bufferBytes -= roughSizeOf(removed);
    }

    // Trim events older than MAX_BUFFER_AGE_MS
    const cutoff = event.timestamp - MAX_BUFFER_AGE_MS;
    while (this.events.length > 1 && this.events[0].timestamp < cutoff) {
      const removed = this.events.shift()!;
      this.bufferBytes -= roughSizeOf(removed);
    }
  };

  private onPageHide = (): void => {
    this.flush();
  };

  private scheduleFlush(): void {
    this.flushTimer = setTimeout(() => {
      this.flush();
      this.scheduleFlush();
    }, PERIODIC_FLUSH_INTERVAL_MS);
  }
}

/** Rough JSON size estimate without full serialisation */
function roughSizeOf(obj: unknown): number {
  return JSON.stringify(obj).length * 2; // 2 bytes per char (UTF-16)
}
```

---

## rrweb Event Types Reference

| `EventType` | Description | Size |
|---|---|---|
| `DomContentLoaded` | Initial page event | Tiny |
| `Load` | Window load | Tiny |
| `FullSnapshot` | Complete DOM serialisation | Large (10–200KB) |
| `IncrementalSnapshot` | DOM mutations, mouse, scroll, input | Small–Medium |
| `Meta` | Session metadata | Tiny |
| `Custom` | App-emitted custom events | Variable |

A full snapshot is re-emitted every time the recording restarts (e.g. after BFCache restore), ensuring the player can seek to any point.

---

## Edge Cases

| Case | Handling |
|---|---|
| Tab hidden for > 60s | Buffer age trimming discards old events; on restore, full snapshot re-emitted |
| BFCache restore | `record()` called again on `pulse:bfcache-restore`; new full snapshot emitted |
| `record()` called twice | `stopRecording()` must be called before re-starting |
| Buffer full before flush | Oldest events dropped (rolling window) |
| rrweb throws on complex DOM (e.g. iframes, shadow DOM) | Caught in `emit` wrapper; recording continues |
| Canvas recording enabled | Significantly increases buffer size; monitor with `bufferBytes` metric |
| `pagehide` and `sendBeacon` size limit | `sendBeacon` payload limit is ~64KB; transport layer (04.3) splits if needed |

---

## Config Interface

```typescript
export interface ReplayConfig {
  maskAllInputs?: boolean;          // Default: true
  maskInputOptions?: Record<string, boolean>;
  maskTextClass?: string;           // Default: 'pulse-mask'
  blockClass?: string;              // Default: 'pulse-block'
  recordCanvas?: boolean;           // Default: false
  sampleRate?: number;              // 0.0–1.0; default: 1.0 (100%)
}
```

---

## Testing

### Unit Tests (Vitest + JSDOM)

```typescript
it('starts recording and emits events to onFlush callback', async () => {
  vi.useFakeTimers();
  const batches: eventWithTime[][] = [];
  const recorder = new ReplayRecorder(batch => batches.push(batch), {});
  recorder.start();

  // Simulate DOM mutation
  document.body.appendChild(document.createElement('div'));

  // Trigger periodic flush
  vi.advanceTimersByTime(30_000);
  expect(batches.length).toBeGreaterThan(0);
  vi.useRealTimers();
});

it('flushes on pagehide', () => {
  const batches: eventWithTime[][] = [];
  const recorder = new ReplayRecorder(batch => batches.push(batch), {});
  recorder.start();
  // Add some events manually
  (recorder as any).events.push({ type: 3, timestamp: Date.now(), data: {} });

  window.dispatchEvent(new PageTransitionEvent('pagehide'));

  expect(batches.length).toBe(1);
});

it('trims buffer when exceeding MAX_BUFFER_BYTES', () => {
  const recorder = new ReplayRecorder(vi.fn(), {});
  recorder.start();

  // Add many large events to exceed 5MB
  const largeEvent = { type: 3, timestamp: Date.now(), data: { x: 'a'.repeat(100_000) } };
  for (let i = 0; i < 60; i++) {
    (recorder as any).onEvent({ ...largeEvent, timestamp: Date.now() + i });
  }

  expect((recorder as any).bufferBytes).toBeLessThanOrEqual(5 * 1024 * 1024);
});
```

---

## Done Criteria

- [ ] `start()` begins rrweb recording with privacy config applied
- [ ] Events buffered in memory with rolling 5MB / 60s window
- [ ] Buffer trimmed (oldest events dropped) when limit exceeded
- [ ] `flush()` emits current buffer to `onFlush` callback and clears buffer
- [ ] Periodic flush every 30s
- [ ] `pagehide` triggers immediate flush
- [ ] `stop()` disconnects rrweb and clears timers
- [ ] Mouse moves throttled to 50ms, scroll to 150ms, inputs to final value
- [ ] All unit tests passing
