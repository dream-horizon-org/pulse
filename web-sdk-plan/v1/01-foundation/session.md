# 01.1 — Session Instrumentation

**Goal:** Emit `session.start` and `session.end` log records when a session begins and ends. Session management (ID generation, rotation, storage) lives in the SDK core — this instrumentation is responsible only for the observable signals.

**File:** `src/instrumentations/session.ts`
**Android equivalent:** `SessionInstrumentation` + `SessionIdEventSender`
**iOS equivalent:** `SessionsInstrumentationConfig` + `SessionEventInstrumentation`

---

## Why Session is an Instrumentation, Not Core Logic

On Android, `SessionInstrumentation` is a standalone module auto-discovered via `@AutoService`. On iOS, `SessionsInstrumentationConfig` is one entry in `InstrumentationConfiguration` — toggled the same way as crash or network.

Separating signal emission from ID management means:
- Session signals can be disabled without breaking session ID stamping (every span still carries `session.id`)
- The instrumentation follows the same `install()` / `uninstall()` contract as every other instrumentation
- Remote SDK Config can gate it like any other feature

---

## Signals Produced

### `session.start` — emitted when a new session begins

| Attribute | Value |
|---|---|
| `pulse.type` | `session.start` |
| `session.id` | The new session ID |
| `session.previous_id` | Previous session ID (empty string on first session) |
| `session.start_reason` | `'new_user'` \| `'inactivity_timeout'` \| `'max_lifetime'` \| `'sdk_init'` |

### `session.end` — emitted when a session ends

| Attribute | Value |
|---|---|
| `pulse.type` | `session.end` |
| `session.id` | The ending session ID |
| `session.duration_ms` | Time since session start (ms) |
| `session.end_reason` | `'inactivity_timeout'` \| `'shutdown'` \| `'page_unload'` \| `'max_lifetime'` |

---

## Config

```typescript
instrumentations: {
  session: {
    enabled: true,
    inactivityTimeoutMs: 30 * 60 * 1000,  // 30 min (matches Android/iOS default)
    maxLifetimeMs: undefined,               // no hard cap by default
  }
}
```

- `inactivityTimeoutMs` — session rotates after this period of no signal activity (default 30 min, matches mobile)
- `maxLifetimeMs` — optional hard cap on session length regardless of activity (iOS supports this; useful for long-lived sessions)

---

## Implementation

```typescript
export class SessionInstrumentation implements PulseInstrumentation {
  readonly name = 'session';
  private unsubscribe?: () => void;

  install(sdk: PulseWebSDK): void {
    // Subscribe to session lifecycle from the SessionProvider in core
    this.unsubscribe = sdk.sessionProvider.onSessionChange((event) => {
      if (event.type === 'start') {
        sdk.logger.emit({
          'pulse.type':           'session.start',
          'session.id':           event.newSessionId,
          'session.previous_id':  event.previousSessionId ?? '',
          'session.start_reason': event.reason,
        });
      } else if (event.type === 'end') {
        sdk.logger.emit({
          'pulse.type':           'session.end',
          'session.id':           event.sessionId,
          'session.duration_ms':  event.durationMs,
          'session.end_reason':   event.reason,
        });
      }
    });
  }

  uninstall(): void {
    this.unsubscribe?.();
  }
}
```

The `SessionProvider` in core is responsible for:
- Generating and rotating session IDs
- Publishing `start` / `end` events to subscribers
- Persisting `session.id` + last-activity timestamp to `sessionStorage`

The instrumentation only subscribes and emits — it has no storage or rotation logic.

---

## Edge Cases

| Case | Handling |
|---|---|
| `pagehide` (real unload, not BFCache) | Emit `session.end` with reason `'page_unload'` synchronously before flush |
| `pagehide(persisted=true)` (BFCache freeze) | Do NOT emit `session.end` — session continues when page restores |
| `pageshow(persisted=true)` (BFCache restore) | Session resumes; update last-activity timestamp, no `session.start` |
| Session rotates mid-tab due to inactivity | `session.end` emitted for old ID, `session.start` emitted for new ID |
| `session.enabled: false` | No `session.start` / `session.end` emitted; `session.id` stamping on spans unaffected |

---

## Done Criteria

- [ ] `session.start` log emitted on `PulseWeb.start()` with correct `session.id`
- [ ] `session.start` emitted with `session.previous_id` when session rotates after inactivity
- [ ] `session.end` emitted on `PulseWeb.shutdown()`
- [ ] `session.end` emitted on `pagehide` (non-BFCache)
- [ ] No `session.end` on BFCache freeze (`pagehide` with `persisted=true`)
- [ ] `session.enabled: false` suppresses signals; `session.id` on spans still populated
- [ ] `session.start_reason` correctly set for all rotation causes
