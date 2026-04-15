# 02.8 — Visibility & Online/Offline Instrumentation

**Goal:** Track tab visibility changes and network connectivity transitions as log records — enabling session-aware analytics (time spent in foreground vs background) and offline-mode debugging.

**File:** `src/instrumentations/visibility-online.ts`
**Android equivalent:** Partially — `app.background`/`app.foreground` lifecycle events in Android SDK

---

## Signals Produced

### `pulse.type: app.visibility` — tab becomes hidden or visible

| Attribute | Type | Source | Android Equivalent |
|---|---|---|---|
| `pulse.type` | string | `"app.visibility"` | `app.background` / `app.foreground` |
| `app.visibility.state` | string | `"visible"` \| `"hidden"` | `app.background` / `app.foreground` |
| `app.visibility.duration` | long | Time in previous state (ms) | — |
| `url.path` | string | `window.location.pathname` | — |

### `pulse.type: network.change` — connectivity change

| Attribute | Type | Source | Android Equivalent |
|---|---|---|---|
| `pulse.type` | string | `"network.change"` | — |
| `network.status` | string | `"online"` \| `"offline"` | — |
| `network.connection.type` | string | `navigator.connection?.type` | `network.connection.type` |
| `network.effective_type` | string | `navigator.connection?.effectiveType` | `network.effective_type` |
| `url.path` | string | `window.location.pathname` | — |

---

## Implementation

```typescript
// src/instrumentations/visibility-online.ts

export class VisibilityOnlineInstrumentation {
  private visibilityStart = Date.now();
  private lastState: DocumentVisibilityState = document.visibilityState;

  install(): void {
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    window.addEventListener('online', this.onOnline);
    window.addEventListener('offline', this.onOffline);
  }

  uninstall(): void {
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
    window.removeEventListener('online', this.onOnline);
    window.removeEventListener('offline', this.onOffline);
  }

  // ─── Visibility ──────────────────────────────────────────────────────────────

  private onVisibilityChange = (): void => {
    const now = Date.now();
    const duration = now - this.visibilityStart;

    // Emit record for the state we're *leaving*
    emitLogRecord({
      'pulse.type':                'app.visibility',
      'app.visibility.state':      this.lastState,        // state that just ended
      'app.visibility.duration':   Math.round(duration),
      'url.path':                  window.location.pathname,
    });

    this.lastState = document.visibilityState;
    this.visibilityStart = now;
  };

  // ─── Online / Offline ─────────────────────────────────────────────────────

  private onOnline = (): void => {
    this.emitNetworkChange('online');
  };

  private onOffline = (): void => {
    this.emitNetworkChange('offline');
  };

  private emitNetworkChange(status: 'online' | 'offline'): void {
    const conn = (navigator as any).connection;
    emitLogRecord({
      'pulse.type':               'network.change',
      'network.status':           status,
      'network.connection.type':  conn?.type ?? '',
      'network.effective_type':   conn?.effectiveType ?? '',
      'url.path':                 window.location.pathname,
    });
  }
}
```

---

## Visibility State Lifecycle

```
Tab opens → state: "visible"
                ↓
User switches tab → visibilitychange fires
  → emit { state: "visible", duration: 30000 }   (was visible for 30s)
  → state becomes "hidden"
                ↓
User returns → visibilitychange fires
  → emit { state: "hidden", duration: 15000 }    (was hidden for 15s)
  → state becomes "visible"
```

Each emit describes the state that **just ended** and how long it lasted. This makes it easy to sum `app.visibility.duration` by state to get total foreground vs background time.

---

## Edge Cases

| Case | Handling |
|---|---|
| Page load — initial state is `"hidden"` | `lastState` initialised from `document.visibilityState` at construction time |
| `pagehide` (tab close/navigation) | Emitting on `pagehide` is handled by the navigation instrumentation (02.5) — not duplicated here |
| Rapid tab switching | Each transition emits a record; sub-second durations are valid and expected |
| `navigator.connection` not supported (Firefox) | `conn?.type` returns `undefined` → stored as `''` |
| Device goes offline during page load | `offline` event fires; `online` fires on reconnect — both captured |
| SSR environment | Guard with `typeof window !== 'undefined'` before installing |

---

## Global Attribute Updates

The `network.connection.type` and `network.effective_type` are also used as **global span attributes** (set on all spans via the `PulseGlobalAttributesProcessor` defined in 01-foundation). When a `network.change` event fires, update the global attribute processor:

```typescript
private emitNetworkChange(status: 'online' | 'offline'): void {
  const conn = (navigator as any).connection;
  // Update global attributes for subsequent spans
  globalAttributeStore.set('network.connection.type', conn?.type ?? '');
  globalAttributeStore.set('network.effective_type',  conn?.effectiveType ?? '');

  // Emit the change event itself
  emitLogRecord({ ... });
}
```

---

## Testing

### Unit Tests (Vitest + JSDOM)

```typescript
it('emits app.visibility when tab hidden', () => {
  const records = captureLogRecords();
  const inst = new VisibilityOnlineInstrumentation();
  inst.install();

  // Simulate visibility change: visible → hidden
  Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
  document.dispatchEvent(new Event('visibilitychange'));

  expect(records[0]['pulse.type']).toBe('app.visibility');
  expect(records[0]['app.visibility.state']).toBe('visible');  // state that ended
  expect(records[0]['app.visibility.duration']).toBeGreaterThanOrEqual(0);
});

it('emits network.change when going offline', () => {
  const records = captureLogRecords();
  const inst = new VisibilityOnlineInstrumentation();
  inst.install();

  window.dispatchEvent(new Event('offline'));

  expect(records[0]['pulse.type']).toBe('network.change');
  expect(records[0]['network.status']).toBe('offline');
});

it('emits network.change when coming back online', () => {
  const records = captureLogRecords();
  const inst = new VisibilityOnlineInstrumentation();
  inst.install();

  window.dispatchEvent(new Event('online'));

  expect(records[0]['network.status']).toBe('online');
});

it('handles missing navigator.connection gracefully', () => {
  const records = captureLogRecords();
  delete (navigator as any).connection;
  const inst = new VisibilityOnlineInstrumentation();
  inst.install();

  window.dispatchEvent(new Event('offline'));

  expect(records[0]['network.connection.type']).toBe('');
});
```

### E2E (Playwright)

```typescript
test('switching tabs emits app.visibility', async ({ page, context }) => {
  await page.goto('/test-page');
  // Open a second page to trigger hidden state
  const page2 = await context.newPage();
  await page2.goto('about:blank');

  const record = await waitForLog(receiver, 'app.visibility', {
    'app.visibility.state': 'visible',
  });
  expect(record['app.visibility.duration']).toBeGreaterThan(0);
});
```

---

## Done Criteria

- [ ] `app.visibility` record emitted on every `visibilitychange` event
- [ ] `app.visibility.state` reflects the state that *just ended* (not the new state)
- [ ] `app.visibility.duration` is the time spent in that state
- [ ] `network.change` emitted on `online` and `offline` events
- [ ] `network.connection.type` and `network.effective_type` populated where supported
- [ ] `network.connection.type` global attribute updated after each connectivity change
- [ ] No error thrown when `navigator.connection` is absent
- [ ] All unit tests passing
