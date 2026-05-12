# Android — Instrumentation: Interaction (taps + server-driven flows)

## Purpose

Two related capabilities sharing the "interaction" module name:

1. **Tap capture** (`pulse.type=app.click`) — View hierarchy taps, Compose taps, rage/dead-click classification.
2. **Server-driven multi-step interactions** (`pulse.type=interaction`) — flows defined remotely via the Interaction Config API (`/v1/interactions/all-active-interactions`).

## Source location

- `instrumentation/view-click/` — classic `View` tap interception (@AutoService instrumentation).
- `instrumentation/compose/` — Jetpack Compose tap modifier.
- `instrumentation/click-common/` — shared click attribute extraction (rage cluster, dead-click detection, normalized coordinates).
- `instrumentation/interaction/library/src/main/java/io/opentelemetry/android/instrumentation/interaction/library/`:
  - `InteractionInstrumentation.kt` (@AutoService).
  - `InteractionAttributesSpanAppender.kt`, `InteractionDefaultAttributesExtractor.kt`, `InteractionLogListener.kt`.
- `instrumentation/interaction/core/src/main/java/com/pulse/android/core/`:
  - `InteractionManager.kt`, `InteractionEventsTracker.kt`, `InteractionEventQueue.kt`, `InteractionLocalEvent.kt`, `InteractionConstant.kt`, `InteractionErrorType.kt`, `InteractionUtil.kt`.
  - `config/InteractionConfigFetcher.kt`, `config/InteractionConfigRestFetcher.kt`.
- `instrumentation/interaction/remote/src/main/java/com/pulse/android/remote/` — Retrofit client, DTOs (`InteractionConfig`, `InteractionEvent`, `InteractionStatus`, `InteractionAttrsEntry`).

## Public surface

DSL:

```kotlin
interaction {
    enabled(true)
    setConfigUrl { "https://.../v1/interactions/all-active-interactions" }
}
```

Compose taps and view taps are enabled by their own DSL hooks (`click-common`, `view-click`, `compose`). Rage clustering knobs in `RageConfiguration` (`android-agent/.../dsl/instrumentation/RageConfiguration.kt`).

## Internal design

- **Tap capture**: view-click installs a `View.OnTouchListener` (or Compose modifier). `InteractionDefaultAttributesExtractor` resolves a human label and writes structured `app.click.context` (e.g. `label=Add to Cart`) via `PulseAttributes.AppClickContext.buildContext`. Coordinates are normalized to [0..1] (`APP_SCREEN_COORDINATE_NX/NY`) so resolution-independent heatmaps work. `click-common` classifies `click.type` (`good`/`dead`) and detects rage clusters (`click.is_rage`, `click.rage_count`).
- **Server-driven interactions**: `InteractionConfigFetcher` pulls the active interaction definitions from the configured URL; `InteractionManager` matches incoming spans/logs against config rules; `InteractionEventsTracker` builds an `InteractionLocalEvent`, queues it in `InteractionEventQueue`, and emits a log record with `pulse.type=interaction` plus matched-step metadata.

## Dependencies

- `pulse-semconv` (click + interaction attributes).
- Retrofit (remote subproject).
- Compose UI (for compose tap).

## Data contracts

- Tap logs: `pulse.type=app.click`, `click.type`, `app.click.context`, `app.screen.coordinate.{x,y,nx,ny}`, optional `click.is_rage`, `click.rage_count`.
- Interaction logs: `pulse.type=interaction`, interaction id, step id, status, configured custom attrs.

## Tests

- `instrumentation/view-click/src/test/`, `instrumentation/compose/click/src/test/`, `instrumentation/click-common/src/test/`.
- `instrumentation/interaction/core/src/test/`, `instrumentation/interaction/remote/src/test/`.
- Fakes: `InteractionFakeUtils.kt`, `InteractionLocalEventFakeUtils.kt`, `InteractionRemoteFakeUtils.kt`.

## History / decisions

- Normalized coordinates chosen so heatmap aggregation is device-size independent.
- Server-driven interactions decoupled from local tap capture so the same config feeds iOS / web.

## Rebuild recipe

1. Install global tap interceptors (View + Compose). Extract label, classify, emit `app.click` log.
2. Add `InteractionConfigFetcher` polling the config URL; cache by interaction id.
3. Subscribe to span/log streams; for each event matching an active config, queue an `InteractionLocalEvent` and emit an `interaction` log.
4. Forward rage thresholds from `RageConfiguration` into `click-common`.
