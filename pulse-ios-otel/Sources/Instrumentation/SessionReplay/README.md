# Session Replay Instrumentation

Automatic screen recording for iOS applications. Captures screenshots at configurable intervals, applies privacy masking, and sends replay data to the backend for visualization.

**UI framework:** The implementation is **UIKit-first** (windows and `UIView` screenshots). **SwiftUI is not supported** for session replay today; SwiftUI-only or SwiftUI-driven screens may not capture reliably.

## Consent and `Pulse.initialize`

Replay respects `dataCollectionState` on `Pulse.initialize` and `Pulse.setDataCollectionState`:

- **`.allowed`:** install + start capturing when the app is active; disk cache may upload.
- **`.pending`:** install only — no screenshots, no cached replay upload until consent becomes `.allowed` (then resume without resetting snapshot state).
- **`.denied` at init:** Pulse never builds the SDK (no replay).
- **`.allowed` → `.pending`:** pause capture and periodic upload; on-disk batches in `pulse-replay/` are retained.
- **`.pending` → `.allowed`:** consent buffers flush for OTLP; replay resumes with `resumeAfterConsent()` (same session semantics as `resumeCurrent=true` on other platforms).

See also [Consent README](../../PulseKit/Consent/README.md). For a short logical overview (including consent), see [WORKFLOW.md](./WORKFLOW.md).

## Features

- **Automatic Screen Capture** - Captures screenshots at configurable intervals using CADisplayLink
- **Privacy Controls** - Granular masking of text, inputs, and images with multiple privacy levels
- **Class-Level Masking** - Register view classes to always mask or unmask
- **Instance-Level Masking** - Per-view masking controls via UIKit view extensions
- **Automatic Batching** - Groups replay events into batches for efficient transmission
- **Persistent Storage** - Caches replay data on disk to survive app termination
- **Lifecycle Management** - Automatically flushes data on app background/termination
- **Compression** - **WebP** when `libwebp` is linked (PulseKit depends on it via SPM); **JPEG** fallback if WebP is unavailable or encoding fails; configurable `compressionQuality`

## Setup

Session Replay instrumentation is configured via PulseSDK initialization in your app's `AppDelegate.swift`:

```swift
import PulseSDK

PulseSDK.initialize(
    endpointBaseUrl: "https://your-endpoint.com",
    apiKey: "your-project-id",
    instrumentations: { config in
        config.sessionReplay { replayConfig in
            replayConfig.enabled(true) // opt in (SDK default is false)
            replayConfig.configure { localConfig in
                localConfig.captureIntervalMs = 1000  // 1 second (default)
                localConfig.compressionQuality = 0.3  // 0.3 (default)
                localConfig.textAndInputPrivacy = .maskAllInputs
                localConfig.imagePrivacy = .maskNone
                localConfig.screenshotScale = 1.0  // 1.0 (default)
                localConfig.flushIntervalSeconds = 60  // 60 seconds (default)
                localConfig.flushAt = 10  // 10 batches (default)
                localConfig.maxBatchSize = 50  // 50 batches (default)
                localConfig.replayEndpointBaseUrl = "https://your-replay-endpoint.com"
            }
        }
    }
)
```

## Configuration

Session Replay configuration options:

| Field                        | Type                    | Description                                                          | Default              |
| ---------------------------- | ----------------------- | -------------------------------------------------------------------- | -------------------- |
| `captureIntervalMs`         | `Int`                   | Interval between frame captures in milliseconds                      | `1000` (1 second)   |
| `compressionQuality`        | `CGFloat`               | Image compression quality (0.0-1.0)                                 | `0.3`                |
| `textAndInputPrivacy`       | `TextAndInputPrivacy`   | Privacy level for text and input fields                              | `.maskAll`           |
| `imagePrivacy`              | `ImagePrivacy`          | Privacy level for images                                             | `.maskAll`           |
| `screenshotScale`           | `CGFloat`               | Scale factor for screenshots (0.0-1.0)                               | `1.0`                |
| `flushIntervalSeconds`      | `TimeInterval`          | Time-based flush interval in seconds                                 | `60`                 |
| `flushAt`                   | `Int`                   | Number of batches to queue before triggering flush                   | `10`                 |
| `maxBatchSize`              | `Int`                   | Maximum number of batches to send per flush                          | `50`                 |
| `replayEndpointBaseUrl`     | `String?`               | Custom endpoint URL for replay data (overrides main endpoint)        | `nil`                |
| `maskViewClasses`           | `Set<String>`           | View class names to always mask                                      | `[]`                 |
| `unmaskViewClasses`         | `Set<String>`           | View class names to always unmask                                    | `[]`                 |

## Privacy Levels

### Text and Input Privacy

| Level                  | Description                                                          |
| ---------------------- | -------------------------------------------------------------------- |
| `.maskAll`             | Mask ALL text content — static labels, input fields, hints (default) |
| `.maskAllInputs`       | Mask only user-editable inputs (UITextField, UITextView). Static UILabel content is shown |
| `.maskSensitiveInputs` | Mask only sensitive input types: password, email, phone. All other text and inputs are shown |

### Image Privacy

| Level        | Description                                    |
| ------------ | ---------------------------------------------- |
| `.maskAll`   | Replace all images with a solid black mask (default) |
| `.maskNone`  | Show all images without masking                |

## Class-Level Masking

Register view classes to always mask or unmask, regardless of privacy settings:

```swift
config.maskViewClasses = Set([
    "MyApp.PrivateSecureView",
    "MyApp.PrivateDataLabel"
])

config.unmaskViewClasses = Set([
    "MyApp.PublicInfoView",
    "MyApp.SafePublicLabel"
])
```

## Instance-Level Masking

### UIKit

Use extensions to mark individual views:

```swift
// Mask a specific view
myPrivateView.pulseReplayMask()

// Unmask a specific view
myPublicView.pulseReplayUnmask()
```

## Replay Events

Session Replay emits snapshot events in the following format:

### Meta Event (Type 4)

Emitted once per window when recording starts.

| Field      | Type   | Description                    |
| ---------- | ------ | ------------------------------ |
| `type`     | int    | Event type (4)                 |
| `timestamp`| int64  | Timestamp in milliseconds      |
| `data`     | object | Contains `href` (screen name), `width`, `height` |

### Full Snapshot Event (Type 2)

Emitted once per window for the first frame.

| Field      | Type   | Description                    |
| ---------- | ------ | ------------------------------ |
| `type`     | int    | Event type (2)                 |
| `timestamp`| int64  | Timestamp in milliseconds      |
| `data`     | object | Contains `wireframes` array with screenshot data |

### Incremental Snapshot Event (Type 3)

Emitted for subsequent frames when content changes.

| Field      | Type   | Description                    |
| ---------- | ------ | ------------------------------ |
| `type`     | int    | Event type (3)                 |
| `timestamp`| int64  | Timestamp in milliseconds      |
| `data`     | object | Contains `source` and `updates` array with wireframe changes |

## Batching and Flushing

Replay events are automatically batched and sent to the backend:

- **Size-Based Flush**: Triggers when queue reaches `flushAt` batches
- **Time-Based Flush**: Triggers every `flushIntervalSeconds` via timer
- **Lifecycle Flush**: Automatically flushes on app background/termination
- **Persistent Storage**: Batches are cached on disk and sent on next app launch if transmission fails

## App Lifecycle Behavior

- **App Active**: Recording starts automatically when app becomes active
- **App Background**: Recording stops and pending batches are flushed
- **App Termination**: Pending batches are flushed before termination
- **App Launch**: Cached batches from previous session are sent automatically

## Thread Safety

All Session Replay components are thread-safe:
- Frame capture uses main thread for UI operations
- Batching and network operations use background queues
- Thread-safe locks protect shared state

## Performance Considerations

- **Capture Interval**: Lower intervals (e.g., 500ms) increase data volume and battery usage
- **Compression Quality**: Lower quality (e.g., 0.2) reduces payload size but degrades image quality
- **Screenshot Scale**: Lower scale (e.g., 0.5) reduces image size and processing time
- **Batch Size**: Larger batches reduce network requests but increase memory usage
