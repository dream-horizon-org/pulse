# 02.1 — Error Tracking Instrumentation

> Lifecycle rerun docs now live in `web-sdk-plan/v1-errors/` (`README.md` entrypoint).
> This file is retained as legacy design notes.

**Goal:** Capture every unhandled JavaScript error and promise rejection as a log record, with full stack trace, and route it to the correct `pulse.type` so it appears in the Pulse crashes dashboard.

**File:** `src/instrumentations/errors.ts`

**Android equivalent:** `CrashInstrumentation` `CrashReporter.kt`), `NonFatalReporter`

---

## Signals Produced

### `pulse.type: device.crash` — unhandled JS errors

Triggered by `window.addEventListener('error', ...)`.

> OTel alignment: emitted as a log record (not a span event) per current OTel spec direction.

> `severityNumber: FATAL` per OTel Logs data model.

> `context: context.active()` wired for trace correlation.

> `timestamp: Date.now()` set explicitly — captures exact moment of error, not batch process time (mirrors Android `setObservedTimestamp`).

| Attribute              | Type   | Source                                             | Required                       |

| ---------------------- | ------ | -------------------------------------------------- | ------------------------------ |

| `pulse.type`           | string | `"device.crash"`                                   | ✅                              |

| `exception.type`       | string | `error.name` (e.g. `"TypeError"`)                  | ✅                              |

| `exception.message`    | string | `error.message`                                    | ✅                              |

| `exception.stacktrace` | string | `error.stack`                                      | ✅                              |

| `error.filename`       | string | `ErrorEvent.filename`                              | ✅                              |

| `error.lineno`         | long   | `ErrorEvent.lineno`                                | ✅                              |

| `error.colno`          | long   | `ErrorEvent.colno`                                 | ✅                              |

| `url.path`             | string | `window.location.pathname`                         | ✅                              |

| `battery.percent`      | double | `navigator.getBattery()` — cached on install       | optional (Chrome/Edge only)    |

| `storage.free`         | long   | `navigator.storage.estimate()` — cached on install | optional (all modern browsers) |

**OTel log record fields (beyond attributes):**

| Field            | Value                                                                            |

| ---------------- | -------------------------------------------------------------------------------- |

| `timestamp`      | `Date.now()` — exact moment error fired (Android parity: `setObservedTimestamp`) |

| `severityNumber` | `SeverityNumber.FATAL` (17)                                                      |

| `severityText`   | `"FATAL"`                                                                        |

| `context`        | `context.active()` — links to active trace span if present                       |

| `body`           | `error.message`                                                                  |

---

### `pulse.type: non_fatal` — unhandled promise rejections

Triggered by `window.addEventListener('unhandledrejection', ...)`.

> `severityNumber: WARN` per OTel Logs data model.

> `timestamp: Date.now()` set explicitly — same as `device.crash`.

| Attribute              | Type    | Source                     | Required |

| ---------------------- | ------- | -------------------------- | -------- |

| `pulse.type`           | string  | `"non_fatal"`              | ✅        |

| `exception.type`       | string  | `error.name`               | ✅        |

| `exception.message`    | string  | `error.message`            | ✅        |

| `exception.stacktrace` | string  | `error.stack`              | ✅        |

| `url.path`             | string  | `window.location.pathname` | ✅        |

| `non_fatal.is_manual`  | boolean | `false` (auto-detected)    | ✅        |

**OTel log record fields:**

| Field            | Value                                       |

| ---------------- | ------------------------------------------- |

| `timestamp`      | `Date.now()` — exact moment rejection fired |

| `severityNumber` | `SeverityNumber.WARN` (13)                  |

| `severityText`   | `"WARN"`                                    |

| `context`        | `context.active()`                          |

| `body`           | `error.message`                             |

---

### `pulse.type: non_fatal` — manual `reportException()` (existing in sdk.ts)

Called explicitly by app code. Note: `PulseErrorBoundary` calls `reportDeviceCrash()` (not `reportException()`) so React render errors land as `device.crash`, not `non_fatal`.

Same attribute contract as auto-detected non_fatal above, except `non_fatal.is_manual: true`.

This path already exists in `sdk.ts` — updated to include `severityNumber` and `url.path`.

---

### `pulse.type: non_fatal` — console.error patch (opt-in, default off)

| Attribute           | Type   | Source                |

| ------------------- | ------ | --------------------- |

| `pulse.type`        | string | `"non_fatal"`         |

| `exception.message` | string | `args.join(' ')`      |

| `console.level`     | string | `"error"` or `"warn"` |

Not installed by default. Must be enabled via `instrumentations.errors.patchConsole: true`.

---

## Android Parity

| Aspect                        | Android `CrashReporter.kt`)                                          | Web                                                                       |

| ----------------------------- | --------------------------------------------------------------------- | ------------------------------------------------------------------------- |

| Capture mechanism             | `Thread.setDefaultUncaughtExceptionHandler`                           | `window.addEventListener('error')` — browser equivalent                   |

| Non-fatal                     | Separate `NonFatalReporter`                                           | `unhandledrejection` listener — browser equivalent                        |

| Signal type                   | Log record via `logRecordBuilder()`                                   | Log record via `logger.emit()` ✅                                          |

| Event name                    | `setEventName("device.crash")`                                        | `pulse.type = "device.crash"` — JS SDK has no stable `setEventName()` API |

| `exception.type`              | `throwable.javaClass.name`                                            | `error.name` ✅                                                            |

| `exception.message`           | `throwable.message`                                                   | `error.message` ✅                                                         |

| `exception.stacktrace`        | `throwable.stackTraceToString()`                                      | `error.stack` ✅                                                           |

| `observedTimestamp`           | `setObservedTimestamp(ms, MILLISECONDS)`                              | `timestamp: Date.now()` ✅                                                 |

| `context` / trace correlation | `Context.current()` via extractor                                     | `context.active()` ✅                                                      |

| Thread info                   | `thread.id`, `thread.name`                                            | `error.filename`, `error.lineno`, `error.colno` — no threads in browser   |

| `battery.percent`             | ✅ via `RuntimeDetailsExtractor` `BatteryManager` broadcast)          | ✅ `navigator.getBattery()` cached on install — Chrome/Edge only           |

| `storage.free`                | ✅ via `RuntimeDetailsExtractor` `filesDir.freeSpace`)                | ✅ `navigator.storage.estimate()` cached on install — all modern browsers  |

| `heap.free`                   | ✅ via `RuntimeDetailsExtractor` `Runtime.getRuntime().freeMemory()`) | ❌ `performance.memory` Chrome-only, deprecated — skipped                  |

| Force flush after crash       | `forceFlush(10s)` blocking                                            | `pagehide` keepalive flush — browser has no blocking flush                |

---

## Deduplication

Same error firing repeatedly (e.g. inside `setInterval`, or a hot render loop) would flood the pipeline.

**Strategy:** fingerprint = `exception.type + exception.message + error.filename + error.lineno`.

If the same fingerprint is seen again within **5 seconds**, skip the emit.

After 5 seconds the window resets — a genuinely recurring error is still captured.

```typescript

private dedupeCache = new Map<string, number>(); // fingerprint → last emit timestamp

private readonly DEDUPE_WINDOW_MS = 5_000;

private isDuplicate(fingerprint: string): boolean {

  const last = this.dedupeCache.get(fingerprint);

  const now = [Date.now](http://Date.now)();

  if (last !== undefined && now - last < this.DEDUPE_WINDOW_MS) return true;

  this.dedupeCache.set(fingerprint, now);

  return false;

}

```

Deduplication applies to **auto-detected** errors only `window.onerror`, `unhandledrejection`).

Manual `reportException()` and `reportDeviceCrash()` calls are never deduplicated — app code is intentional.

---

## Device State Caching (battery + storage)

Android's `RuntimeDetailsExtractor` listens to `BatteryManager` broadcasts in the background so battery level is **synchronously available** at crash time — no async needed.

Web mirrors this with an on-install prefetch + event listener cache:

```typescript

// Cached on install — available synchronously when crash fires

private batteryPercent: number | undefined;

private storageFreeBytes: number | undefined;

private async prefetchDeviceState(): Promise<void> {

  // Battery — Chrome/Edge only, graceful no-op elsewhere

  if ("getBattery" in navigator) {

    try {

      const battery = await (navigator as any).getBattery();

      this.batteryPercent = battery.level * 100;

      battery.addEventListener("levelchange", () => {

        this.batteryPercent = battery.level * 100;

      });

    } catch { /* not supported — skip */ }

  }

  // Storage free — all modern browsers

  if ("storage" in navigator && "estimate" in [navigator.storage](http://navigator.storage)) {

    try {

      const { quota = 0, usage = 0 } = await [navigator.storage](http://navigator.storage).estimate();

      this.storageFreeBytes = quota - usage;

    } catch { /* not supported — skip */ }

  }

}

```

Both values are **best-effort optional** — if the API is unsupported or the prefetch hasn't resolved yet, the attribute is simply omitted from the log record.

---

## Implementation

```typescript

// src/instrumentations/errors.ts

import { logs, SeverityNumber } from "@opentelemetry/api-logs";

import { context } from "@opentelemetry/api";

import type { PulseInstrumentation, SdkContext } from "../instrumentation-registry";

import { PulseWebSemconv } from "../semconv";

export class ErrorInstrumentation implements PulseInstrumentation {

  readonly name = "errors";

  private onErrorHandler?: (e: ErrorEvent) => void;

  private onRejectionHandler?: (e: PromiseRejectionEvent) => void;

  private dedupeCache = new Map<string, number>();

  private readonly DEDUPE_WINDOW_MS = 5_000;

  // Device state — prefetched on install, kept fresh via event listeners

  private batteryPercent: number | undefined;

  private storageFreeBytes: number | undefined;

  install(_sdk: SdkContext): void {

    const K = PulseWebSemconv.AttributeKey;

    const T = PulseWebSemconv.PulseType;

    const logger = logs.getLogger("pulse-web-errors");

    // Prefetch device state in background — available on next crash

    void this.prefetchDeviceState();

    this.onErrorHandler = (e: ErrorEvent) => {

      // Skip cross-origin errors — browser blocks stack access for security

      if (e.message === "Script error." && !e.filename) return;

      const error = e.error instanceof Error ? e.error : new Error(e.message);

      const fingerprint = `${error.name}:${error.message}:${e.filename}:${e.lineno}`;

      if (this.isDuplicate(fingerprint)) return;

      logger.emit({

        body: error.message,

        timestamp: [Date.now](http://Date.now)(),           // exact moment error fired

        severityNumber: SeverityNumber.FATAL,

        severityText: "FATAL",

        context: context.active(),

        attributes: {

          [K.PULSE_TYPE]:            T.DEVICE_CRASH,

          [K.EXCEPTION_TYPE]:        [error.name](http://error.name),

          [K.EXCEPTION_MESSAGE]:     error.message,

          [K.EXCEPTION_STACKTRACE]:  error.stack ?? "",

          [K.ERROR_FILENAME]:        e.filename || "",

          [K.ERROR_LINENO]:          e.lineno,

          [K.ERROR_COLNO]:           e.colno,

          [K.URL_PATH]:              window.location.pathname,

          // Device state — optional, omitted if not available

          ...(this.batteryPercent !== undefined && { [K.BATTERY_PERCENT]: this.batteryPercent }),

          ...(this.storageFreeBytes !== undefined && { [[K.STORAGE](http://K.STORAGE)_FREE]: this.storageFreeBytes }),

        },

      });

    };

    this.onRejectionHandler = (e: PromiseRejectionEvent) => {

      const error = e.reason instanceof Error

        ? e.reason

        : new Error(String(e.reason ?? "Unknown rejection"));

      const fingerprint = `${error.name}:${error.message}`;

      if (this.isDuplicate(fingerprint)) return;

      logger.emit({

        body: error.message,

        timestamp: [Date.now](http://Date.now)(),           // exact moment rejection fired

        severityNumber: SeverityNumber.WARN,

        severityText: "WARN",

        context: context.active(),

        attributes: {

          [K.PULSE_TYPE]:            T.NON_FATAL,

          [K.EXCEPTION_TYPE]:        [error.name](http://error.name),

          [K.EXCEPTION_MESSAGE]:     error.message,

          [K.EXCEPTION_STACKTRACE]:  error.stack ?? "",

          [K.URL_PATH]:              window.location.pathname,

          [K.NON_FATAL_IS_MANUAL]:   false,

        },

      });

    };

    window.addEventListener("error", this.onErrorHandler);

    window.addEventListener("unhandledrejection", this.onRejectionHandler);

  }

  uninstall(): void {

    if (this.onErrorHandler) {

      window.removeEventListener("error", this.onErrorHandler);

    }

    if (this.onRejectionHandler) {

      window.removeEventListener("unhandledrejection", this.onRejectionHandler);

    }

    this.dedupeCache.clear();

    this.batteryPercent = undefined;

    this.storageFreeBytes = undefined;

  }

  private async prefetchDeviceState(): Promise<void> {

    if ("getBattery" in navigator) {

      try {

        const battery = await (navigator as any).getBattery();

        this.batteryPercent = battery.level * 100;

        battery.addEventListener("levelchange", () => {

          this.batteryPercent = battery.level * 100;

        });

      } catch { /* not supported */ }

    }

    if ("storage" in navigator && "estimate" in [navigator.storage](http://navigator.storage)) {

      try {

        const { quota = 0, usage = 0 } = await [navigator.storage](http://navigator.storage).estimate();

        this.storageFreeBytes = quota - usage;

      } catch { /* not supported */ }

    }

  }

  private isDuplicate(fingerprint: string): boolean {

    const last = this.dedupeCache.get(fingerprint);

    const now = [Date.now](http://Date.now)();

    if (last !== undefined && now - last < this.DEDUPE_WINDOW_MS) return true;

    this.dedupeCache.set(fingerprint, now);

    return false;

  }

}

```

---

## Edge Cases

| Case                                                        | Handling                                                                  |

| ----------------------------------------------------------- | ------------------------------------------------------------------------- |

| Cross-origin script error `"Script error."`)               | Skip — no actionable info; security restriction prevents stack access     |

| `error.stack` is undefined (some older browsers)            | Use empty string `""`, don't crash                                        |

| `e.reason` in rejection is a string not an Error            | Wrap in `new Error(String(reason))`                                       |

| `e.reason` is `undefined` / `null`                          | Wrap in `new Error("Unknown rejection")`                                  |

| Same error repeated within 5s (e.g. `setInterval`)          | Deduplicated — only first emit goes through                               |

| Same error after 5s window                                  | Emitted again — recurring errors are still captured                       |

| Manual `reportException()` called before `PulseWeb.start()` | No-op — sdk guard returns early; app code should call after `start()`     |

| `window.onerror` already set by another library             | We use `addEventListener` not the `onerror` property — no conflict        |

| `getBattery()` not supported (Firefox, Safari)              | `batteryPercent` stays `undefined` — attribute omitted from log record    |

| `storage.estimate()` throws                                 | `storageFreeBytes` stays `undefined` — attribute omitted from log record  |

| Crash fires before prefetch resolves                        | Device state attrs omitted — crash still emitted immediately without them |

| Battery level changes mid-session                           | `levelchange` listener keeps `batteryPercent` fresh throughout session    |

---

## Testing

### Unit Tests (Vitest + JSDOM)

```typescript

it('emits device.crash on window error event', () => {

  window.dispatchEvent(new ErrorEvent('error', {

    message: 'ReferenceError: foo is not defined',

    filename: 'app.js',

    lineno: 42,

    colno: 5,

    error: new ReferenceError('foo is not defined'),

  }));

  expect(mockEmit).toHaveBeenCalledWith(expect.objectContaining({

    severityNumber: SeverityNumber.FATAL,

    attributes: expect.objectContaining({

      'pulse.type': 'device.crash',

      'exception.type': 'ReferenceError',

      'error.lineno': 42,

      'url.path': '/',

    }),

  }));

});

it('emits non_fatal on unhandled rejection', () => {

  window.dispatchEvent(new PromiseRejectionEvent('unhandledrejection', {

    promise: Promise.reject(),

    reason: new TypeError('Cannot read property'),

  }));

  expect(mockEmit).toHaveBeenCalledWith(expect.objectContaining({

    severityNumber: SeverityNumber.WARN,

    attributes: expect.objectContaining({

      'pulse.type': 'non_fatal',

      'non_[fatal.is](http://fatal.is)_manual': false,

    }),

  }));

});

it('skips cross-origin script errors', () => {

  window.dispatchEvent(new ErrorEvent('error', { message: 'Script error.' }));

  expect(mockEmit).not.toHaveBeenCalled();

});

it('deduplicates same error within 5s', () => {

  const err = new ErrorEvent('error', {

    message: 'same error', filename: 'app.js', lineno: 1, colno: 1,

    error: new Error('same error'),

  });

  window.dispatchEvent(err);

  window.dispatchEvent(err);

  window.dispatchEvent(err);

  expect(mockEmit).toHaveBeenCalledTimes(1);

});

it('allows same error after 5s window', () => {

  vi.useFakeTimers();

  const err = new ErrorEvent('error', {

    message: 'recurring', filename: 'app.js', lineno: 1, colno: 1,

    error: new Error('recurring'),

  });

  window.dispatchEvent(err);

  vi.advanceTimersByTime(6_000);

  window.dispatchEvent(err);

  expect(mockEmit).toHaveBeenCalledTimes(2);

  vi.useRealTimers();

});

it('wraps string rejection reason in Error', () => {

  window.dispatchEvent(new PromiseRejectionEvent('unhandledrejection', {

    promise: Promise.reject(),

    reason: 'plain string rejection',

  }));

  expect(mockEmit).toHaveBeenCalledWith(expect.objectContaining({

    attributes: expect.objectContaining({

      'exception.type': 'Error',

      'exception.message': 'plain string rejection',

    }),

  }));

});

it('uninstall removes both listeners', () => {

  const instr = new ErrorInstrumentation();

  instr.install(mockSdk);

  instr.uninstall();

  window.dispatchEvent(new ErrorEvent('error', { message: 'after uninstall', error: new Error() }));

  expect(mockEmit).not.toHaveBeenCalled();

});

```

### E2E (Playwright)

```typescript

test('JS error lands in OTLP receiver as device.crash', async ({ page }) => {

  await page.goto('/error-demo');

  await [page.click](http://page.click)('[data-testid="throw-uncaught"]');

  const log = await waitForLog(receiver, 'device.crash');

  expect(log.attributes['exception.message']).toContain('Demo uncaught error');

  expect(log.attributes['exception.stacktrace']).toBeTruthy();

  expect(log.attributes['error.lineno']).toBeGreaterThan(0);

  expect(log.severityNumber).toBe(SeverityNumber.FATAL);

});

test('Unhandled rejection lands as non_fatal', async ({ page }) => {

  await page.goto('/error-demo');

  await [page.click](http://page.click)('[data-testid="throw-promise"]');

  const log = await waitForLog(receiver, 'non_fatal');

  expect(log.attributes['non_[fatal.is](http://fatal.is)_manual']).toBe(false);

  expect(log.severityNumber).toBe(SeverityNumber.WARN);

});

```

---

## Manual Test Cases

Use the ecommerce demo (`yarn demo`) with the local ingest stack running (`deploy/scripts/start.sh`). Open the **Error Demo** page at `/error-demo`.

| # | Test | Steps to Reproduce | Expected | Status | Comment |
|---|---|---|---|---|---|
| 1 | Uncaught JS error emits device.crash | 1. Open `/error-demo` 2. Click **Throw uncaught error** 3. Check Pulse dashboard Crashes tab | Log record appears with `pulse.type = device.crash`, `exception.type = Error`, `exception.message = Demo uncaught error from ErrorDemo`, `error.lineno > 0`, `severityNumber = 21 (FATAL)` | | |
| 2 | Unhandled promise rejection emits non_fatal | 1. Open `/error-demo` 2. Click **Reject unhandled promise** 3. Check Pulse dashboard Non-Fatals tab | Log record appears with `pulse.type = non_fatal`, `exception.message = Demo unhandled rejection from ErrorDemo`, `non_fatal.is_manual = false`, `severityNumber = 13 (WARN)` | | |
| 3 | React render error caught by PulseErrorBoundary | 1. Open `/error-demo` 2. Click **Throw in render** 3. Check dashboard | Fallback UI renders on page; log record with `pulse.type = device.crash`, `exception.message = Intentional render error from ErrorDemo` appears in dashboard | | |
| 4 | Manual reportException emits non_fatal with is_manual true | 1. Open `/error-demo` 2. Click **Report exception** 3. Check dashboard | Log record with `pulse.type = non_fatal`, `non_fatal.is_manual = true`, `exception.message = Manually reported error` | | |
| 5 | url.path stamped on every error log | 1. Open `/error-demo` 2. Trigger any error type 3. Inspect log record attributes | `url.path = /error-demo` present on every emitted log record | | |
| 6 | Deduplication — same error within 5s emitted once | 1. Open `/error-demo` 2. Open browser console 3. Run `for(let i=0;i<5;i++) window.dispatchEvent(new ErrorEvent('error',{message:'dup',filename:'x.js',lineno:1,error:new Error('dup')}))` 4. Check dashboard | Exactly 1 log record in dashboard, not 5 | | |
| 7 | Deduplication window resets after 5s | 1. Trigger uncaught error 2. Wait 6 seconds 3. Trigger the exact same error again 4. Check dashboard | 2 separate log records appear | | |
| 8 | Two different errors not deduplicated | 1. Open console 2. Dispatch error with message `err-a` 3. Dispatch error with message `err-b` 4. Check dashboard | 2 separate log records appear | | |
| 9 | battery.percent included on Chrome/Edge | 1. Open in Chrome or Edge 2. Trigger uncaught error 3. Inspect log record attributes | `battery.percent` attribute present with value 0–100 | | Chrome/Edge only; getBattery() API required |
| 10 | battery.percent absent on Firefox/Safari — error still captured | 1. Open in Firefox or Safari 2. Trigger uncaught error 3. Inspect log record | `battery.percent` attribute absent; all other attributes present; error captured correctly | | Expected — getBattery() not supported in Firefox/Safari |
| 11 | storage.free included in all modern browsers | 1. Open in any modern browser 2. Trigger uncaught error 3. Inspect attributes | `storage.free` attribute present with value > 0 | | |
| 12 | Cross-origin script error silently skipped | 1. Add `<script src="https://cross-origin.example.com/throws.js">` to page 2. Script throws 3. Check dashboard | No log record emitted; browser shows `Script error.` in console only | | Security — browser blocks stack for cross-origin scripts |
| 13 | Error dispatched before SDK init is ignored | 1. Do NOT call `PulseWeb.start()` 2. Dispatch `new ErrorEvent('error', {message:'early', error: new Error('early')})` 3. Check dashboard | No log record emitted | | Listeners not attached before init |
| 14 | reportException before SDK init is a no-op | 1. Do NOT call `PulseWeb.start()` 2. Call `PulseWeb.reportException(new Error('early'))` 3. Check dashboard | No log record emitted | | SDK guard returns early if not initialized |
| 15 | String promise rejection reason wrapped in Error | 1. Open console 2. Run `Promise.reject('something went wrong')` 3. Check dashboard | Log record with `exception.type = Error`, `exception.message = something went wrong` | | String wrapped in new Error() |
| 16 | Undefined rejection reason handled gracefully | 1. Open console 2. Run `Promise.reject(undefined)` 3. Check dashboard | Log record with `exception.message = Unknown rejection` | | |
| 17 | timestamp reflects exact time of error | 1. Note wall clock time 2. Trigger any error 3. Inspect `timestamp` field in raw OTLP payload | `timestamp` within 1000ms of noted wall clock time | | Exact event time — not batch flush time |
| 18 | No conflict with pre-existing window.onerror handler | 1. Before calling `PulseWeb.start()` set `window.onerror = () => console.log('existing handler fired')` 2. Start SDK 3. Trigger uncaught error | Console shows `existing handler fired`; Pulse log record also emitted in dashboard | | SDK uses addEventListener — does not overwrite onerror property |

---

## Done Criteria

- `device.crash` emitted on `window.onerror` with `exception.type`, `exception.message`, `exception.stacktrace`, `error.filename`, `error.lineno`, `error.colno`, `url.path`

- `non_fatal` emitted for `unhandledrejection` with `non_fatal.is_manual: false`

- `timestamp: Date.now()` set on every log record — exact event time, not batch time

- `severityNumber: FATAL` on `device.crash`, `severityNumber: WARN` on `non_fatal`

- `context.active()` passed on every emit for trace correlation

- `battery.percent` present on `device.crash` when `getBattery()` supported, absent otherwise

- `storage.free` present on `device.crash` when `storage.estimate()` supported, absent otherwise

- Cross-origin `"Script error."` silently skipped

- Same error within 5s deduplicated — only first emit goes through

- Same error after 5s window — emitted again

- Manual `reportException()` and `reportDeviceCrash()` never deduplicated

- `e.reason` is string → wrapped in `new Error()`

- `uninstall()` removes both listeners and clears device state cache

- `window.onerror` property not touched — `addEventListener` used (no conflict with other libraries)

- All unit tests passing

