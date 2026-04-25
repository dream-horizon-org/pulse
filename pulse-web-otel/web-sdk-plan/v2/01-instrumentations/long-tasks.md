# 02.6 — Long Tasks Instrumentation

**Goal:** Detect main-thread blocks longer than 50ms and emit them as `app.jank.slow` log records — the web equivalent of Android's slow frame / ANR detection.

**File:** `src/instrumentations/long-tasks.ts`
**Android equivalent:** `SlowRenderingInstrumentation`, `ANRInstrumentation`

---

## Signals Produced

### `pulse.type: app.jank.slow` — one log record per long task

| Attribute | Type | Source | Android Equivalent |
|---|---|---|---|
| `pulse.type` | string | `"app.jank.slow"` | `pulse.type: app.jank.slow` |
| `app.jank.type` | string | `"long_task"` | `app.jank.type` |
| `duration` | double | `entry.duration` (ms) | replaces `app.jank.period` |
| `url.path` | string | `window.location.pathname` | replaces `activity.name` |

**Not available on web (Android-only):** `app.jank.frame_count`, `app.jank.threshold`, `app.interaction.slow_frame_count`, `app.interaction.frozen_frame_count`.

`app.jank.frozen` (frozen frames > 700ms) maps to INP metric in 02.4 rather than this instrumentation.

**Browser support:** Chromium only. Feature-detect; silent no-op in Firefox/Safari.

---

## Implementation

```typescript
// src/instrumentations/long-tasks.ts

export class LongTaskInstrumentation {
  private observer?: PerformanceObserver;

  install(): void {
    // Feature detect — longtask is Chromium-only
    if (!PerformanceObserver.supportedEntryTypes?.includes('longtask')) return;

    this.observer = new PerformanceObserver(list => {
      for (const entry of list.getEntries()) {
        emitLogRecord({
          'pulse.type':    'app.jank.slow',
          'app.jank.type': 'long_task',
          'duration':       entry.duration,
          'url.path':       window.location.pathname,
        });
      }
    });

    this.observer.observe({ type: 'longtask', buffered: true });
  }

  uninstall(): void {
    this.observer?.disconnect();
  }
}
```

---

## Edge Cases

| Case | Handling |
|---|---|
| Firefox / Safari | `supportedEntryTypes` check returns false — silently skipped |
| Very frequent long tasks (thrashing page) | Emit every one; let sampling / remote config throttle if needed |
| SDK init itself causes a long task | Buffered observation (`buffered: true`) captures tasks before observer attached |
| Long task during session replay recording | Expected — rrweb serialization can itself cause long tasks |

---

## Testing

### Unit Tests (Vitest)

```typescript
it('emits app.jank.slow when PerformanceObserver fires', () => {
  const records = captureLogRecords();
  // Simulate PerformanceObserver callback
  simulateLongTask({ duration: 120 });
  expect(records[0]['pulse.type']).toBe('app.jank.slow');
  expect(records[0]['duration']).toBe(120);
});

it('does not throw in Firefox where longtask is unsupported', () => {
  // Remove longtask from supportedEntryTypes
  vi.spyOn(PerformanceObserver, 'supportedEntryTypes', 'get')
    .mockReturnValue(['paint', 'navigation']);
  expect(() => new LongTaskInstrumentation().install()).not.toThrow();
});
```

---

## Done Criteria

- [ ] Long task > 50ms emits `app.jank.slow` with `duration` and `url.path`
- [ ] No error thrown in Firefox or Safari
- [ ] Buffered tasks (pre-observer) are captured
- [ ] All unit tests passing
