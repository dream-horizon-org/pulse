# Pulse Session Replay – Architecture

Session Replay mirrors **PostHog Android SDK** behavior: same flow (Curtains, touch interception, screenshot + wireframe), same event shapes, with your own package names and [ReplayEventEmitter] for delivery.

## High-level architecture

Session Replay captures the UI state of the app over time so it can be replayed later. The design follows a **capture → process → emit** pipeline:

1. **Capture**: When the screen is drawn, we take a snapshot (screenshot and/or wireframe).
2. **Process**: Snapshots are masked, optionally passed through custom processors, and encoded.
3. **Emit**: Structured replay events are delivered to an `ReplayEventEmitter` (no built-in upload; you plug in your pipeline).

Replay is **throttled** (e.g. one snapshot per `throttleDelayMs`) and runs **off the main thread** for capture/encoding to avoid jank. Lifecycle is driven by **Activity** windows and **ViewTreeObserver.OnDrawListener** so we only capture when there is something to show.

---

## Package structure

```
com.pulse.android.sdk.replay/
├── SessionReplayConfig.kt          # Public config (masking, throttle, screenshot/wireframe)
├── SessionReplayController.kt      # Public start/stop/isActive
├── SessionReplayIntegration.kt     # Main integration (Curtains, touch, capture → emit)
├── ReplayEventEmitter.kt           # Public callback interface (emit List<ReplayEvent>)
├── ReplayConstants.kt              # MASK_TAG, UNMASK_TAG
├── LoggingReplayEventEmitter.kt    # Debug emitter
├── events/                         # Event and model types
│   ├── ReplayEvent.kt              # Base event (type, timestamp, data)
│   ├── ReplayEventType.kt          # Meta, FullSnapshot, IncrementalSnapshot, Custom, etc.
│   ├── ReplayWireframe.kt          # Snapshot node (bounds, type, base64, children)
│   ├── ReplayStyle.kt              # Wireframe styling
│   ├── ScreenSizeInfo.kt          # Screen dimensions for meta
│   ├── ReplayMetaEvent.kt         # Meta event (width, height, href)
│   ├── ReplayFullSnapshotEvent.kt
│   ├── ReplayIncrementalSnapshotEvent.kt
│   ├── ReplayCustomEvent.kt       # e.g. keyboard open/close
│   ├── ReplayIncrementalMutationData.kt / ReplayMutatedNode / ReplayRemovedNode
│   ├── ReplayIncrementalMouseInteractionEvent.kt / ReplayIncrementalMouseInteractionData.kt
│   ├── ReplayMouseInteraction.kt / ReplayMousePosition.kt / ReplayIncrementalSource.kt
│   └── ...
├── encoding/                       # Serialization
│   └── ReplayEventPayloadEncoder.kt # JSON encoding for replay events
├── ui/                             # Compose / UI utilities
│   └── PulseReplayMask.kt          # Modifier.pulseReplayMask() + semantics key
└── internal/
    ├── capture/                   # UI capture and masking
    │   ├── ScreenshotCapture.kt    # PixelCopy → bitmap → WebP base64
    │   ├── WireframeCapture.kt     # View tree → wireframe
    │   └── MaskingCollector.kt     # Collect mask rects (Views + Compose semantics)
    ├── pipeline/                   # Event building
    │   └── SnapshotPipeline.kt     # Wireframe → Meta/Full/Incremental/Custom events
    ├── scheduling/                 # When to capture
    │   ├── Throttler.kt            # Rate limit by throttleDelayMs
    │   ├── NextDrawListener.kt     # OnDrawListener + throttle callback
    │   └── ViewTreeSnapshotStatus.kt # Per-window state (full sent, last snapshot)
    └── util/                       # Shared utilities
        ├── DateProvider.kt         # Time abstraction (testing)
        ├── BitmapUtils.kt          # Bitmap.isValid(), webpBase64()
        └── ScreenSizeUtils.kt      # screenSize(context) for meta
```

---

## Core components and responsibilities

| Component | Responsibility |
|-----------|-----------------|
| **SessionReplayConfig** | Masking (textAndInputPrivacy, imagePrivacy, addMaskViewClass/addUnmaskViewClass), throttleDelayMs, screenshot vs wireframe, drawable converter; flushIntervalSeconds, flushAt, maxBatchSize for persistence. |
| **SessionReplayController** | start/stop/isActive; clears or preserves snapshot state when (re)starting. |
| **Curtains** | Root view discovery (`Curtains.rootViews`, `onRootViewsChangedListeners`), `window.onDecorViewReady`, `view.phoneWindow`, `window.touchEventInterceptors` (same as PostHog). |
| **NextDrawListener** | ViewTreeObserver.OnDrawListener that fires once per draw; uses **Throttler** so the actual capture runs at most every `throttleDelayMs`. |
| **Throttler** | Ensures a runnable runs at most once per throttle window (time since last run ≥ throttleDelayMs); schedules on main handler. |
| **ViewTreeSnapshotStatus** | Per–decor-view state: sentFullSnapshot, sentMetaEvent, keyboardVisible, lastSnapshot (for incremental diff). |
| **ScreenshotCapture** | PixelCopy from Window to Bitmap; optional masking (rects over sensitive areas); encode to base64 (e.g. WebP). |
| **MaskingCollector** | Walks View tree (and Compose semantics when available) to collect Rect list for “mask” (overlay) and “no-capture” (hide) using config + view tags (e.g. `pulse-mask`, `pulse-unmask`). |
| **ReplayEvent** / **ReplayWireframe** | Data classes for full snapshot, incremental snapshot, meta, custom (e.g. keyboard); wireframe holds bounds, type, optional base64 image. |
| **ReplayEventEmitter** | Interface to emit list of ReplayEvent (e.g. to memory, file, or your upload pipeline). **Extension point.** |
| **PersistingReplayEmitter** (SDK) | Wraps an emitter with file-based persistence: each batch is written to disk and queued; periodic flush and send-on-startup of cached events so replay survives app kill (PostHog-style). |
| **SnapshotPipeline** (internal.pipeline) | Builds replay events from wireframe: Meta, FullSnapshot, IncrementalSnapshot, Custom (keyboard); handles full vs incremental. |

---

## Data flow

```
Curtains.onRootViewsChangedListeners → addView(rootView, added)
    → view.phoneWindow → window.onDecorViewReady { decorView → … }
    → NextDrawListener on decorView, touchEventInterceptors += listener
OnDraw (throttled)
    → decorView.post { MaskRectCache collects mask rects on main thread }
    → executor.submit { generateSnapshot(decorView, window, maskRects) }
    → ScreenshotCapture.capture(window, decorView, maskRects) or WireframeCapture.capture(decorView)
    → Pre-collected mask rects applied to bitmap
    → Build ReplayEvent list (Meta, FullSnapshot or IncrementalSnapshot, Custom)
    → ReplayEventEmitter.emit(sessionId, events)
```

**With PersistingReplayEmitter (default SDK path):**
```
emit(sessionId, events)
    → queue executor: build envelope JSON → write to file (<timestamp>_<uuid>.replay) → add File to deque
    → if deque.size >= flushAt → flush (read files, realSend each, delete)
    → timer every flushIntervalSeconds → same flush
realSend(payload) → OTLP log (body = envelope) → backend
On next app launch → sendCachedEvents(): list .replay files, sort by lastModified, read → realSend → delete each
```
Capture runs on the replay executor; persist + send runs on a separate queue thread. The capture thread never waits on disk or network.

---

## Performance impact of batching

- **Capture path**: `emit()` only posts a task to the queue executor and returns. Snapshot generation and the replay executor are **not blocked** by persistence or network.
- **Queue thread**: Envelope build (JSON encode) and file write run on a dedicated thread. Same CPU work as immediate send; extra cost is **one file write per batch** (disk I/O). Flush and OTLP send also run on this thread, so slow network can backlog the queue without affecting UI or capture.
- **Memory**: Only `File` references are kept in the deque; payloads live on disk. During flush we read one file at a time, send, then delete.
- **Tuning**: Increase `flushIntervalSeconds` to send less often (fewer network calls, more batches per run); decrease `flushAt` to flush sooner and avoid long queues. Defaults (60s, flushAt 10) keep work off the capture path and limit in-memory queue size.

---

## Payload format (envelope)

Events are emitted with a **session id** (UUID) and encoded for backend compatibility:

- **Session ID**: Generated when `start(resumeCurrent = false)` is called; same for the lifetime of the replay session until the next `start(false)`.
- **ReplayEventEmitter**: Receives `emit(sessionId: String, events: List<ReplayEvent>)`. Implementations should send the envelope below (or equivalent) to a capture endpoint.
- **Envelope** (built by the SDK when using the default OTLP path):  
  `{ "event": "snapshot", "timestamp": "<ISO-8601>", "properties": { "session_id": "<uuid>", "snapshot_data": [ ... ], "snapshot_source": "android" } }`
- **snapshot_data** items: Each has `type` (integer: 2=FullSnapshot, 3=IncrementalSnapshot, 4=Meta, 5=Custom), `timestamp` (ms), and `data` (object). Incremental and mouse/touch payloads are serialized as full JSON objects (not strings).

---

## Extension points

- **ReplayEventEmitter**: Replace or wrap to send events to your backend, buffer, or analytics.
- **ReplaySnapshotProcessor**: Optional list of processors that can modify wireframes or events before emit.
- **ReplaySnapshotEncoder**: Swap default base64 WebP encoder for another format or compression.
- **SessionReplayConfig.drawableConverter**: Custom Drawable → Bitmap for wireframe mode (e.g. custom views).

---

## Threading and safety

- **Main thread**: Activity lifecycle, registering OnDrawListener, Throttler scheduling, Compose semantics (if used).
- **Background executor**: `generateSnapshot`, PixelCopy callback (Handler on dedicated thread), bitmap masking, encoding. No heavy work on main thread.
- **Memory**: Bitmaps created for screenshot are recycled after encode; WeakReference for View/Window; per-window state cleared when view is detached.

---

## Replay start/stop conditions

- **Start**: `SessionReplayController.start(resumeCurrent)`. If `!resumeCurrent`, all ViewTreeSnapshotStatus are reset, a new session ID (UUID) is generated, and the next capture is a full snapshot.
- **Stop**: `SessionReplayController.stop()`. No new snapshots; in-flight work can still complete and emit.
- **Active**: Only when `SessionReplayController.isActive()` is true are new snapshots generated and emitted.

---

## Throttling and batching

For the full batching strategy, when batches are sent, and all configurable parameters, see **[BATCHING.md](BATCHING.md)**.

- **Throttling**: One snapshot per decor view per `throttleDelayMs` (via Throttler + OnDrawListener). Reduces CPU and battery when the screen is animating or updating often.
- **Batching**: A single “frame” can produce multiple events (e.g. Meta + FullSnapshot + Custom). They are emitted together as one `List<ReplayEvent>` to the emitter; the emitter can batch further (e.g. by time or size) if desired.

---

## Persistence and app kill

When the default SDK path is used, replay batches are **persisted to disk** so they survive process death (e.g. user kills the app):

- **On emit**: Each batch is written to a file under a fixed directory (`context.filesDir/pulse_replay`) and added to an in-memory queue. A background timer flushes the queue every `flushIntervalSeconds`; flush also runs when the queue size reaches `flushAt`.
- **On next launch**: Before starting replay, the SDK runs **send cached events**: it lists leftover `.replay` files from the previous run, sorts by modification time, sends each envelope to the same OTLP path, then deletes the file. Order is preserved so the backend sees a consistent replay stream.
- **Config**: `flushIntervalSeconds` (default 60), `flushAt` (default 10), `maxBatchSize` (default 50). Storage directory and encryption are fixed by the SDK (not configurable).
- **On shutdown**: The SDK calls `flush()` on the persisting emitter so pending batches are sent (best-effort; no wait for completion).
- **Encryption**: Replay files are **encrypted by default** (AES-256-GCM). The SDK uses a key stored in app-private SharedPreferences. Not configurable.

This matches PostHog Android's behavior: file-per-batch persistence and a dedicated "send cached events" step on startup.

---

## Masking

- **Global config**: `textAndInputPrivacy` (MASK_ALL / MASK_ALL_INPUTS / MASK_SENSITIVE_INPUTS) and `imagePrivacy` (MASK_ALL / MASK_NONE) in SessionReplayConfig. Passwords, emails, and phone input types are always masked regardless of config.
- **Per-view class**: `config.addMaskViewClass("com.example.Foo")` / `config.addUnmaskViewClass(...)` — hierarchy-aware (includes subclasses).
- **Per-view instance**: View tag or contentDescription containing `pulse-mask` / `pulse-unmask`, or Kotlin extensions `view.pulseReplayMask()` / `view.pulseReplayUnmask()`. Compose: `Modifier.pulseReplayMask(true/false)`.
- **Priority**: Instance override > Class registration > Global config. Masked parent forces children masked unless child has explicit unmask.
- **Screenshot mode**: Mask rects are drawn (rounded black rects) over the bitmap before encoding.
- **Wireframe mode**: Text/value replaced with "***", images as placeholder; no-capture views excluded from tree.

---

## Compose support

- **Screenshots**: Supported. Compose UI is rendered into the same window; PixelCopy captures it; masking can use Compose semantics (MaskingCollector).
- **Wireframes**: Not implemented for Compose (view hierarchy is not a classic View tree). Screenshot-only for Compose is the intended design.

**Public API imports:** Most app code only needs `com.pulse.android.sdk.replay.SessionReplayConfig` and `ReplayEventEmitter`. For Compose masking use `com.pulse.android.sdk.replay.ui.pulseReplayMask()`. Event types and encoding live under `replay.events` and `replay.encoding` for use by custom emitters or processors.

**PostHog parity:** Curtains (1.2.5), touch events → `ReplayIncrementalMouseInteractionEvent`, full View→wireframe (`WireframeCapture.toWireframe`), screenshot + wireframe modes, theme background for wireframe, `screenSize()` for meta, same throttling and lifecycle. Optional Logcat capture is not implemented; add via custom processor or separate integration if needed.

---

## Usage

**With Pulse SDK (recommended):** enable session replay via the instrumentations block, same as activity, fragment, crashReporter, etc.:

```kotlin
PulseSDK.INSTANCE.initialize(
    application = application,
    endpointBaseUrl = "https://...",
    projectId = "your-project",
    instrumentations = {
        activity { enabled(true) }
        fragment { enabled(true) }
        sessionReplay {
            screenshot = true
            throttleDelayMs = 1000L
            replayApiBaseUrl = "https://your-replay-endpoint"
        }
    }
)
```

**Standalone (custom pipeline):**

```kotlin
// 1. Create config and emitter
val config = SessionReplayConfig(
    textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL,
    imagePrivacy = ImagePrivacy.MASK_ALL,
    screenshot = true,
    throttleDelayMs = 1000L,
)
val emitter = ReplayEventEmitter { sessionId, events -> /* build envelope and send to your pipeline */ }

// 2. Create and install integration
val replay = SessionReplayIntegration(context, config, emitter, logger = { Log.d("Replay", it) })
replay.install()

// 3. Start/stop replay
replay.start(resumeCurrent = false)  // full snapshot on next capture
// ... later ...
replay.stop()
```

**Quick manual testing:** use [LoggingReplayEventEmitter] to print every batch to Logcat (e.g. `SessionReplayIntegration(context, config, LoggingReplayEventEmitter("Replay"))`), then run the app and filter Logcat by that tag. See [TESTING.md](TESTING.md) for full flow and unit-test ideas.

**Per-view masking (Views):** set `view.tag = "pulse-mask"` or include `pulse-mask` in `contentDescription` to mask; `pulse-unmask` to override global and not mask.

**Compose:** use `Modifier.pulseReplayMask(true)` to mask, `pulseReplayMask(false)` to unmask.
