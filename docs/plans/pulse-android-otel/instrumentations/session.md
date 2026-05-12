# Android — Instrumentation: Session

## Purpose

Maintain a stable `session.id` across signals, emit `session.start` / `session.end` events, persist session metadata across cold launches, and roll the session over on timeout or app-state transitions.

## Source location

- `instrumentation/sessions/src/main/kotlin/io/opentelemetry/android/instrumentation/sessions/`:
  - `SessionInstrumentation.kt` (@AutoService).
  - `SessionIdEventSender.kt`.
- `android-agent/src/main/kotlin/io/opentelemetry/android/agent/session/`:
  - `SessionManager.kt`, `SessionConfig.kt`, `SessionIdTimeoutHandler.kt`, `DefaultSessionIdGenerator.kt`, `InMemorySessionStorage.kt`, `PersistentSessionStorage.kt`.
- `session/` Gradle module — shared session interfaces.

## Public surface

DSL options through `SessionConfig` (timeout, storage). Apps don't manage session IDs directly; they're attached as a resource attribute on every signal.

## Internal design

1. `SessionManager` owns the current `session.id` and decides when to roll over (idle timeout via `SessionIdTimeoutHandler`, app-foreground transitions).
2. `PersistentSessionStorage` writes the active session to disk so an immediate cold-restart resumes the same session when the timeout hasn't elapsed; `InMemorySessionStorage` is the test/default fallback.
3. `SessionIdEventSender` emits `session.start` and `session.end` log records (`pulse.type=session.start` / `session.end`) on rollover.
4. Every span/log gets `session.id` attached via an OTel processor.

## Dependencies

- `pulse-semconv` (`APP_SESSION_START`, `APP_SESSION_END`).
- OTel Android `core` + `common-api`.

## Data contracts

- Resource attribute: `session.id`.
- Log records: `pulse.type=session.start` / `session.end` with `session.id` and previous `session.id` on end.

## Tests

- `android-agent/src/test/kotlin/io/opentelemetry/android/agent/session/SessionManagerTest.kt`, `SessionIdTimeoutHandlerTest.kt`.
- `instrumentation/sessions/src/test/`.

## History / decisions

- Persistent storage chosen so crash-then-relaunch within the timeout window appears as one session for accurate retention metrics.
- Rollover is event-driven, not periodic — no timer wakes up the app.

## Rebuild recipe

1. Define `SessionIdGenerator` + `SessionStorage` interfaces.
2. Implement `SessionManager` that observes app lifecycle + idle timeout.
3. Emit `session.start` / `session.end` records via `SessionIdEventSender`.
4. Add an OTel span/log processor that stamps `session.id` on every record.
