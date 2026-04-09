# Session Instrumentation

Automatic session tracking for OpenTelemetry Swift applications. Creates unique session identifiers, tracks session lifecycle events, and automatically adds session context to all telemetry data.

## Features

- **Automatic Session Management** - Creates and manages session lifecycles with configurable timeouts
- **Session Events** - Emits OpenTelemetry log records for session start/end events
- **Span Attribution** - Automatically adds session IDs to all spans via span processor
- **Log Attribution** - Automatically adds session IDs to all log records via log processor
- **Configurable Persistence** - Sessions can be in-memory or persisted across app restarts
- **Background Inactivity Timeout** - Sessions can expire when app goes to background
- **Fixed Lifetime Expiration** - Sessions expire after a fixed duration from start time
- **Thread Safety** - All components are thread-safe for concurrent access

## Setup

Session instrumentation is configured via PulseSDK initialization in your app's `AppDelegate.swift`:

```swift
import PulseSDK

PulseSDK.initialize(
    endpointBaseUrl: "https://your-endpoint.com",
    projectId: "your-project-id",
    instrumentations: { config in
        config.sessions { sessionsConfig in
            sessionsConfig.enabled(true) // true (default)
            sessionsConfig.maxLifetime(4 * 60 * 60)  // 4 hours (default)
            sessionsConfig.backgroundInactivityTimeout(15 * 60)  // 15 minutes (default)
            sessionsConfig.shouldPersist(false)  // In-memory (default)
        }
    }
)
```

## Configuration

Session configuration options:

| Field                        | Type            | Description                                                          | Default              |
| ---------------------------- | --------------- | -------------------------------------------------------------------- | -------------------- |
| `maxLifetime`                | `TimeInterval?` | Fixed duration in seconds after which session expires from start time | `14400` (4 hours)   |
| `backgroundInactivityTimeout` | `TimeInterval?` | Duration in seconds after which session expires when app is in background | `900` (15 min) |
| `shouldPersist`              | `Bool`          | Whether session should persist across app restarts                    | `false` (in-memory)  |

## Session Events

Session lifecycle events are emitted as OpenTelemetry log records:

### Session Start Event

Emitted when a new session begins.

| Attribute             | Type   | Description                                   |
| --------------------- | ------ | --------------------------------------------- |
| `session.id`          | string | Unique identifier for the current session     |
| `session.start_time`  | double | Session start time in nanoseconds since epoch |
| `session.previous_id` | string | Identifier of the previous session (if any)   |

### Session End Event

Emitted when a session expires.

| Attribute             | Type   | Description                                   |
| --------------------- | ------ | --------------------------------------------- |
| `session.id`          | string | Unique identifier for the ended session       |
| `session.start_time`  | double | Session start time in nanoseconds since epoch |
| `session.end_time`    | double | Session end time in nanoseconds since epoch   |
| `session.duration`    | double | Session duration in nanoseconds               |
| `session.previous_id` | string | Identifier of the previous session (if any)   |

**Timestamp Behavior**: 
- For normal expiration: `session.end_time` is set to the session's expiration time (start time + maxLifetime)
- For background expiration: `session.end_time` is set to the time when the app went to background.

## Span and Log Attribution

Session attributes are automatically added to all spans and log records:

| Attribute             | Type   | Description                                  |
| --------------------- | ------ | -------------------------------------------- |
| `session.id`          | string | Current active session identifier            |
| `session.previous_id` | string | Previous session identifier (when available) |

Session lifecycle events (`session.start` and `session.end`) already have their session attributes set and are not modified by the processors.

## Session Behavior

### Session Creation

Sessions are created when the first span or log is emitted after SDK initialization or when an existing session expires.

### Expiration

Sessions expire when either condition is met:
- **Fixed Lifetime**: Current time exceeds the session's `expireTime` (start time + maxLifetime). The expiration time is fixed at session creation and does not extend with activity.
- **Background Inactivity**: App is in background longer than `backgroundInactivityTimeout`. The session expires when the app returns to foreground after exceeding the timeout.

### App Kill Scenarios

- **App Killed (shouldPersist: false)**: Session is lost. On next launch, a new session is created when the first span/log is emitted.
- **App Killed (shouldPersist: true)**: 
  - If the persisted session is **not expired**: Session is restored and continues with the same ID. No events are emitted.
  - If the persisted session is **expired**: Session is not restored. A new session is created with `session.end` event for the old session (if expired by maxLifetime) and `session.start` for the new session.

### Persistence

When `shouldPersist: true`, sessions are stored in UserDefaults and restored on app launch. Expired sessions are never restored. The `previous_id` attribute links consecutive sessions when a session expires.

### Session ID Format

32-character hexadecimal string (TraceId format, no hyphens)
