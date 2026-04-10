# iOS Lifecycle Instrumentation — Signals Reference

What we track, why we track it, and how it maps to Android.

---

## Spans

### 1. `Created`

| Field              | Value |
|--------------------|-------|
| **What it measures** | Time taken for a screen to load from scratch and become fully visible |
| **Start**          | Beginning of `viewDidLoad` (fires only on first load) |
| **End**            | End of `viewDidAppear` |
| **`pulse.type`**   | `screen_load` |
| **Attributes**     | `view_controller.name`, `screen.name` |
| **Events**         | `ViewDidLoad` → `ViewWillAppear` → `ViewIsAppearing` → `ViewDidAppear` |
| **Android equivalent** | `Created` span (`onActivityPreCreated` → `onActivityPostResumed`) |

**Why:** This is the most important lifecycle span. It captures how long it takes for a screen to go from "loading into memory" to "visible and interactive". On Android, the `Created` span starts at `onActivityPreCreated` and ends at `onActivityPostResumed`. Our iOS span does the same: from `viewDidLoad` through to `viewDidAppear`. This includes view setup, layout, and animation time — the full fresh-load cost.

---

### 2. `Restarted`

| Field              | Value |
|--------------------|-------|
| **What it measures** | Time taken for an already-loaded screen to become visible again (re-appearance) |
| **Start**          | Beginning of `viewWillAppear` (when `viewDidLoad` did NOT fire) |
| **End**            | End of `viewDidAppear` |
| **`pulse.type`**   | *(none — not a fresh screen load)* |
| **Attributes**     | `view_controller.name`, `screen.name` |
| **Events**         | `ViewWillAppear` → `ViewIsAppearing` → `ViewDidAppear` |
| **Android equivalent** | `Restarted` span (`onActivityPreStarted` → `onActivityPostResumed`) |

**Why:** When a user navigates back (e.g. pops a navigation stack), the VC is still in memory — `viewDidLoad` does NOT fire, only `viewWillAppear` → `viewDidAppear`. This is **much faster** than a fresh `Created` load. Using a separate span name prevents re-appearances from skewing your screen load time metrics downward. On Android, the same distinction exists: `Created` for first creation, `Restarted` for returning to a stopped Activity.

**How they're distinguished:** If `viewDidLoad` fires → `Created`. If only `viewWillAppear` fires (no prior `viewDidLoad` for this appearance cycle) → `Restarted`.

---

### 3. `ViewControllerSession`

| Field              | Value |
|--------------------|-------|
| **What it measures** | How long the user stays on a screen (visible and interactive time) |
| **Start**          | After `viewDidAppear` completes (after `Created` span ends) |
| **End**            | When `viewWillDisappear` is called (user is leaving the screen) |
| **`pulse.type`**   | `screen_session` |
| **Attributes**     | `view_controller.name`, `screen.name` |
| **Android equivalent** | `ActivitySession` span (`onActivityResumed` → `onActivityPaused`) |

**Why:** Measures user engagement per screen. Start and end points match Android exactly — after the screen is fully visible, until the user navigates away.

---

### 4. `Stopped`

| Field              | Value |
|--------------------|-------|
| **What it measures** | The disappearance transition — time from when a screen starts leaving to when it's fully gone |
| **Start**          | Beginning of `viewWillDisappear` |
| **End**            | End of `viewDidDisappear` |
| **`pulse.type`**   | *(none — not a screen_load or screen_session)* |
| **Attributes**     | `view_controller.name`, `screen.name` |
| **Events**         | `ViewWillDisappear` → `ViewDidDisappear` |
| **Android equivalent** | The combined `Paused` + `Stopped` phase |

**Why:** On Android, the disappearance flow has separate `Paused` (`onActivityPrePaused` → `onActivityPostPaused`) and `Stopped` (`onActivityPreStopped` → `onActivityPostStopped`) spans — but both are **near-instant** since the pre/post callbacks fire within the same method frame. On iOS, `viewWillDisappear` → `viewDidDisappear` captures the actual animation/transition duration, making it a single span with **meaningful duration**. We name it `Stopped` to match the final state — the view is no longer visible, same semantic as Android's `Stopped`.

---

### 5. `AppStart`

| Field              | Value |
|--------------------|-------|
| **What it measures** | Cold app startup time |
| **Start**          | SDK initialization (`Pulse.shared.initialize(...)`) |
| **End**            | First `viewDidAppear` fires |
| **`pulse.type`**   | `app_start` |
| **Attributes**     | `start.type` = `"cold"` |
| **Android equivalent** | `AppStart` span (SDK init → first `onActivityPostResumed`) |

**Why:** Identical concept on both platforms. Measures how long it takes from SDK init until the user sees the first screen.

---

## Logs

### `device.app.lifecycle`

| Field              | Value |
|--------------------|-------|
| **Event name**     | `device.app.lifecycle` |
| **Attribute key**  | `ios.app.state` |
| **Android equivalent** | `device.app.lifecycle` log with `android.app.state` |

| State Value    | Triggered By | Android Equivalent |
|---------------|-------------|-------------------|
| `"created"`   | `UIApplication.didFinishLaunchingNotification` (first activation) | `"created"` (first `onActivityStarted`) |
| `"foreground"` | `UIApplication.willEnterForegroundNotification` (returning from background) | `"foreground"` (subsequent `onActivityStarted` after background) |
| `"background"` | `UIApplication.didEnterBackgroundNotification` | `"background"` (`onActivityStopped` with no remaining started activities) |

**Why:** Tracks app-level state transitions. The three states (`created`, `foreground`, `background`) match Android exactly, just with the platform-appropriate attribute key (`ios.app.state` vs `android.app.state`).

---

## Swizzled Methods

Every swizzled method has a specific purpose. We use `imp_implementationWithBlock` + `method_setImplementation` (not `method_exchangeImplementations`) so the swizzle is safe even if the original method is inherited from `UIViewController`.

| # | Method | Why We Swizzle It |
|---|--------|-------------------|
| 1 | `viewDidLoad` | **Start the `Created` span** on first load. This is the earliest point where we know a new screen is being set up. Also adds the `ViewDidLoad` event. |
| 2 | `viewWillAppear(_:)` | **Start the `Created` span** on re-appearances (when `viewDidLoad` didn't fire). Adds the `ViewWillAppear` event to the span. |
| 3 | `viewIsAppearing(_:)` | Adds the `ViewIsAppearing` event to the `Created` span. This iOS 17+ callback fires between `viewWillAppear` and `viewDidAppear`, giving us a mid-transition timing data point. |
| 4 | `viewDidAppear(_:)` | **End the `Created` span** (screen is now visible). **Start the `ViewControllerSession` span** (user is now on this screen). **End the `AppStart` span** (first screen only). |
| 5 | `viewWillDisappear(_:)` | **End the `ViewControllerSession` span** (user is leaving). **Start the `Stopped` span** (disappearance transition begins). |
| 6 | `viewDidDisappear(_:)` | **End the `Stopped` span** (screen is fully gone). |

### Filtering

Not every `UIViewController` is tracked. We filter out:
- VCs not in `Bundle.main` (system/UIKit internal VCs)
- `UINavigationController` (container, not a real screen)
- `UITabBarController` (container)
- `UISplitViewController` (container)

This ensures we only track app-owned content screens.

---

## Android Spans We Do NOT Have (and Why)

| Android Span | Why Not on iOS |
|-------------|---------------|
| `Resumed` | On Android: `onActivityPreResumed` → `onActivityPostResumed` — **near-instant**, fires within the same method frame. On iOS, the equivalent would be a span wrapping just the `viewDidAppear` call itself. This produces zero meaningful duration. Our `Created` span already ends at `viewDidAppear`, capturing the same lifecycle transition. |
| `Paused` | On Android: `onActivityPrePaused` → `onActivityPostPaused` — **near-instant**. iOS has no pre/post split for `viewWillDisappear`. A span wrapping just the method body has no useful duration. Our `Stopped` span covers `viewWillDisappear` → `viewDidDisappear` which captures real transition time. |
| `Destroyed` | `deinit` cannot be swizzled in Swift/ObjC. We use `NSMapTable.weakToStrongObjects` (or similar weak references) to auto-clean trackers when VCs are deallocated. No span is needed — deallocation is not an actionable signal. |
| `Restarted` | **Implemented.** When a VC re-appears without `viewDidLoad` firing (e.g. navigation pop-back), we emit a `Restarted` span instead of `Created`, matching Android's distinction. |

### Key Insight: Android's Near-Instant Spans

On Android API 29+, the lifecycle has `pre`/`post` variants for each callback (e.g. `onActivityPrePaused` / `onActivityPostPaused`). These let Android create spans that wrap a single lifecycle method call. However, **these spans are near-instant** (sub-millisecond duration) because `pre` and `post` fire within the same stack frame. They exist primarily to carry span events, not to measure meaningful durations.

iOS does NOT have this pre/post split. UIKit lifecycle methods (`viewWillAppear`, `viewDidAppear`, etc.) are the only hooks available. Rather than creating artificial instant spans, we capture **meaningful durations**:

| What We Measure | iOS Span | Duration | Android Span | Duration |
|----------------|----------|----------|-------------|----------|
| Fresh screen load | `Created` | Real (viewDidLoad → viewDidAppear) | `Created` | Real (onPreCreated → onPostResumed) |
| Screen re-appearance | `Restarted` | Real (viewWillAppear → viewDidAppear) | `Restarted` | Real (onPreStarted → onPostResumed) |
| User time on screen | `ViewControllerSession` | Real (viewDidAppear → viewWillDisappear) | `ActivitySession` | Real (onResumed → onPaused) |
| Screen disappearing | `Stopped` | Real (viewWillDisappear → viewDidDisappear) | `Paused` then `Stopped` | Each near-instant |
| App startup | `AppStart` | Real (SDK init → first viewDidAppear) | `AppStart` | Real (SDK init → first onPostResumed) |

---

## Observed App Notifications (AppStateWatcher)

These are NOT swizzled — they use standard `NotificationCenter` observation.

| Notification | What It Tells Us | Action Taken |
|-------------|-----------------|-------------|
| `UIApplication.didFinishLaunchingNotification` | App has launched for the first time | Emit `device.app.lifecycle` log with `ios.app.state = "created"` |
| `UIApplication.willEnterForegroundNotification` | App is returning from background | Emit `device.app.lifecycle` log with `ios.app.state = "foreground"` |
| `UIApplication.didEnterBackgroundNotification` | App has gone to background | Emit `device.app.lifecycle` log with `ios.app.state = "background"` |

---

## Signal Flow: Screen Navigation

### First Screen Load (Cold Start)

```
SDK init ─────────────────────── AppStart span STARTS (start.type=cold)
│
viewDidLoad() ────────────────── Created span STARTS
│                                 + event: ViewDidLoad
│
viewWillAppear(_:) ───────────── + event: ViewWillAppear
│
viewIsAppearing(_:) ──────────── + event: ViewIsAppearing (iOS 17+)
│
viewDidAppear(_:) ────────────── + event: ViewDidAppear
│                                 Created span ENDS
│                                 ViewControllerSession span STARTS
│                                 AppStart span ENDS
│
── (user interacts with screen) ──
│
viewWillDisappear(_:) ────────── ViewControllerSession span ENDS
│                                 Stopped span STARTS
│                                 + event: ViewWillDisappear
│
viewDidDisappear(_:) ─────────── + event: ViewDidDisappear
│                                 Stopped span ENDS
```

### Push New Screen (VC-A → VC-B)

```
VC-A: viewWillDisappear ──────── VC-A: Session ENDS, Stopped STARTS
VC-B: viewDidLoad ────────────── VC-B: Created STARTS (+ ViewDidLoad event)
VC-B: viewWillAppear ─────────── VC-B: + ViewWillAppear event
VC-A: viewDidDisappear ──────── VC-A: Stopped ENDS
VC-B: viewDidAppear ──────────── VC-B: Created ENDS, Session STARTS
```

### Pop Back (VC-B → VC-A)

```
VC-B: viewWillDisappear ──────── VC-B: Session ENDS, Stopped STARTS
VC-A: viewWillAppear ─────────── VC-A: Restarted STARTS (+ ViewWillAppear event)
                                  (viewDidLoad does NOT fire — VC-A is still in memory)
VC-B: viewDidDisappear ──────── VC-B: Stopped ENDS
VC-A: viewDidAppear ──────────── VC-A: Restarted ENDS, Session STARTS
```

### App Backgrounding / Foregrounding

```
── (user switches to another app) ──
│
LOG: device.app.lifecycle (ios.app.state = "background")
│
── (user returns to the app) ──
│
LOG: device.app.lifecycle (ios.app.state = "foreground")
```

---

## Attribute Reference

| Attribute Key | Set On | Description |
|--------------|--------|-------------|
| `view_controller.name` | All VC spans | Swift class name of the UIViewController (e.g. `"HomeViewController"`) |
| `screen.name` | All VC spans | Same as `view_controller.name` (used for cross-platform consistency) |
| `pulse.type` | `Created`, `ViewControllerSession`, `AppStart` | Signal classification: `screen_load`, `screen_session`, `app_start` |
| `start.type` | `AppStart` | Start type: `"cold"` |
| `ios.app.state` | `device.app.lifecycle` log | App state: `"created"`, `"foreground"`, `"background"` |

---

## `pulse.type` Mapping

| Span Name | `pulse.type` Value | Set By |
|-----------|-------------------|--------|
| `Created` | `screen_load` | `PulseSignalProcessor` (inferred from span name on start) |
| `Restarted` | *(none)* | Not a fresh screen load — no pulse.type assigned (same as Android) |
| `ViewControllerSession` | `screen_session` | `PulseSignalProcessor` (inferred from span name on start) |
| `AppStart` | `app_start` | `AppStartupTimer` (set at span creation) |
| `Stopped` | *(none)* | Not classified — disappearance transitions are not a primary signal |
