# App Lifecycle Instrumentation

Automatically emits OpenTelemetry log events when the app transitions between lifecycle states.

## How it works

`AppStateWatcher` observes `UIApplication` lifecycle notifications and forwards state changes to registered listeners. `AppLifecycleInstrumentation` listens for these changes and emits a `device.app.lifecycle` log event for each transition.

### Tracked transitions

| UIKit Notification | `ios.app.state` |
|--------------------|-----------------|
| `didFinishLaunching` | `created` |
| `willEnterForeground` | `foreground` |
| `didEnterBackground` | `background` |

The `created` state is emitted at most once per app launch. If the app is already active when the SDK initializes, it fires immediately.

## Log format

Each log event has the following shape:

| Field | Value |
|-------|-------|
| Event name | `device.app.lifecycle` |
| `ios.app.state` | `"created"` \| `"foreground"` \| `"background"` |
