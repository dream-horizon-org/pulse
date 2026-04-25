# 02.1 — Error Tracking Instrumentation

**Goal:** Capture every unhandled JavaScript error and promise rejection as a log record, with full stack trace, and route it to the correct `pulse.type` so it appears in the Pulse crashes dashboard.

**File:** `src/instrumentations/errors.ts`
**Android equivalent:** `CrashInstrumentation`, `NonFatalReporter`

---

## Signals Produced

### `pulse.type: device.crash` — unhandled JS errors

Triggered by `window.addEventListener('error', ...)`.

| Attribute | Type | Source | Required |
|---|---|---|---|
| `pulse.type` | string | `"device.crash"` | ✅ |
| `exception.type` | string | `error.name` (e.g. `"TypeError"`) | ✅ |
| `exception.message` | string | `error.message` | ✅ |
| `exception.stacktrace` | string | `error.stack` | ✅ |
| `error.filename` | string | `ErrorEvent.filename` | ✅ |
| `error.lineno` | long | `ErrorEvent.lineno` | ✅ |
| `error.colno` | long | `ErrorEvent.colno` | ✅ |
| `url.path` | string | `window.location.pathname` | ✅ |
| `battery.percent` | double | `navigator.getBattery()` result | optional |

### `pulse.type: non_fatal` — unhandled promise rejections + manual `reportException()`

Triggered by `window.addEventListener('unhandledrejection', ...)` and `PulseWeb.reportException()`.

| Attribute | Type | Source | Required |
|---|---|---|---|
| `pulse.type` | string | `"non_fatal"` | ✅ |
| `exception.type` | string | `error.name` | ✅ |
| `exception.message` | string | `error.message` | ✅ |
| `exception.stacktrace` | string | `error.stack` | ✅ |
| `url.path` | string | `window.location.pathname` | ✅ |
| `non_fatal.is_manual` | boolean | `true` when called via `reportException()` | ✅ |

### `pulse.type: non_fatal` — console.error patch (opt-in, default off)

| Attribute | Type | Source |
|---|---|---|
| `pulse.type` | string | `"non_fatal"` |
| `exception.message` | string | `args.join(' ')` |
| `console.level` | string | `"error"` or `"warn"` |

---

## Implementation

```typescript
// src/instrumentations/errors.ts

export class ErrorInstrumentation {
  private prevOnError?: OnErrorEventHandler;

  install(): void {
    // 1. Unhandled JS errors
    this.prevOnError = window.onerror;
    window.addEventListener('error', this.onError);

    // 2. Unhandled promise rejections
    window.addEventListener('unhandledrejection', this.onRejection);
  }

  uninstall(): void {
    window.removeEventListener('error', this.onError);
    window.removeEventListener('unhandledrejection', this.onRejection);
  }

  private onError = (e: ErrorEvent): void => {
    // Ignore cross-origin script errors (no useful info available)
    if (e.message === 'Script error.' && !e.filename) return;

    const attrs: Record<string, unknown> = {
      'pulse.type':           'device.crash',
      'exception.type':       e.error?.name ?? 'Error',
      'exception.message':    e.message,
      'exception.stacktrace': e.error?.stack ?? '',
      'error.filename':       e.filename,
      'error.lineno':         e.lineno,
      'error.colno':          e.colno,
      'url.path':             window.location.pathname,
    };

    // Battery (async, best-effort)
    if ('getBattery' in navigator) {
      (navigator as any).getBattery().then((b: any) => {
        attrs['battery.percent'] = b.level * 100;
        emitLogRecord(attrs);
      }).catch(() => emitLogRecord(attrs));
    } else {
      emitLogRecord(attrs);
    }
  };

  private onRejection = (e: PromiseRejectionEvent): void => {
    const error = e.reason instanceof Error
      ? e.reason
      : new Error(String(e.reason));

    emitLogRecord({
      'pulse.type':           'non_fatal',
      'exception.type':       error.name,
      'exception.message':    error.message,
      'exception.stacktrace': error.stack ?? '',
      'non_fatal.is_manual':  false,
      'url.path':             window.location.pathname,
    });
  };
}

// Public API — called by PulseWeb.reportException()
export function reportException(
  error: Error | string,
  isFatal = false,
  attributes: Record<string, string> = {}
): void {
  const err = typeof error === 'string' ? new Error(error) : error;
  emitLogRecord({
    'pulse.type':           isFatal ? 'device.crash' : 'non_fatal',
    'exception.type':       err.name,
    'exception.message':    err.message,
    'exception.stacktrace': err.stack ?? '',
    'non_fatal.is_manual':  true,
    'url.path':             window.location.pathname,
    ...attributes,
  });
}

// Optional — console.error patch
export function patchConsoleError(): void {
  const original = console.error.bind(console);
  console.error = (...args: unknown[]) => {
    emitLogRecord({
      'pulse.type':        'non_fatal',
      'exception.message': args.map(String).join(' '),
      'console.level':     'error',
    });
    original(...args);
  };
}
```

---

## Edge Cases

| Case | Handling |
|---|---|
| Cross-origin script error (`"Script error."`) | Skip — no actionable info; security restriction prevents stack access |
| `error.stack` is undefined (some older browsers) | Use empty string `""`, don't crash |
| `e.reason` in rejection is a string not an Error | Wrap in `new Error(String(reason))` |
| `getBattery()` not supported | Skip battery attribute, still emit the record |
| `reportException()` called before `PulseWeb.start()` | Queue and flush once initialized (same pattern as Module 1 singleton) |
| Same error thrown repeatedly (e.g. in `setInterval`) | No deduplication in v1 — emit every occurrence |

---

## Testing

### Unit Tests (Vitest + JSDOM)

```typescript
it('emits device.crash on window error event', () => {
  const records = captureLogRecords();
  window.dispatchEvent(new ErrorEvent('error', {
    message: 'ReferenceError: foo is not defined',
    filename: 'app.js',
    lineno: 42,
    colno: 5,
    error: new ReferenceError('foo is not defined'),
  }));
  expect(records[0]['pulse.type']).toBe('device.crash');
  expect(records[0]['exception.type']).toBe('ReferenceError');
  expect(records[0]['error.lineno']).toBe(42);
});

it('emits non_fatal on unhandled rejection', () => {
  const records = captureLogRecords();
  window.dispatchEvent(new PromiseRejectionEvent('unhandledrejection', {
    promise: Promise.reject(),
    reason: new TypeError('Cannot read property'),
  }));
  expect(records[0]['pulse.type']).toBe('non_fatal');
  expect(records[0]['exception.type']).toBe('TypeError');
});

it('skips cross-origin script errors', () => {
  const records = captureLogRecords();
  window.dispatchEvent(new ErrorEvent('error', { message: 'Script error.' }));
  expect(records).toHaveLength(0);
});

it('reportException marks non_fatal.is_manual true', () => {
  const records = captureLogRecords();
  reportException(new Error('oops'));
  expect(records[0]['non_fatal.is_manual']).toBe(true);
});
```

### E2E (Playwright)

```typescript
test('JS error lands in mock OTLP receiver', async ({ page }) => {
  await page.goto('/test-page');
  await page.evaluate(() => { throw new Error('test crash'); });
  const log = await waitForLog(receiver, 'device.crash');
  expect(log['exception.message']).toBe('test crash');
  expect(log['exception.stacktrace']).toContain('test-page');
});
```

---

## Done Criteria

- [ ] `device.crash` emitted with `exception.type`, `exception.message`, `exception.stacktrace`, `error.lineno`, `url.path`
- [ ] `non_fatal` emitted for unhandled promise rejections
- [ ] Cross-origin `"Script error."` silently skipped
- [ ] `reportException(error)` produces `non_fatal` with `non_fatal.is_manual: true`
- [ ] `reportException(error, true)` produces `device.crash`
- [ ] Console error patch off by default; works correctly when enabled
- [ ] All unit tests passing
