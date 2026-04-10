# Interaction Instrumentation - Internal Documentation

## Overview

The Interaction Instrumentation tracks user flows (sequences of events) in an application. It matches events tracked via `PulseKit.trackEvent()` against server-configured interaction sequences. When a sequence completes, it creates an OpenTelemetry span with timing and event data.

## Architecture

### Key Components

1. **InteractionInstrumentation** - Main entry point, manages lifecycle
2. **InteractionManager** - Orchestrates config fetching, tracker creation, and event processing
3. **InteractionEventsTracker** - Tracks events for a single interaction configuration
4. **InteractionEventQueue** - Thread-safe queue for real-time event processing
5. **InteractionUtil** - Core matching logic (sequence matching, property matching, APDEX calculation)
6. **InteractionLogListener** - LogRecordProcessor that intercepts user events
7. **InteractionAttributesSpanAppender** - SpanProcessor that adds interaction attributes to spans

### Data Flow

```
User Event (PulseKit.trackEvent())
    ↓
InteractionLogListener (LogRecordProcessor)
    ↓
InteractionManager.addEvent()
    ↓
InteractionEventQueue
    ↓
InteractionEventsTracker (for each config)
    ↓
InteractionUtil.matchSequence()
    ↓
InteractionRunningStatus (state update)
    ↓
InteractionInstrumentation.handleInteractionStates()
    ↓
createInteractionSpan() → OpenTelemetry Span
```

## Initialization Flow

### 1. SDK Initialization (`PulseKit.initialize`)

```
PulseKit.shared.initialize {
    $0.interaction {
        $0.enabled(true)
        $0.setConfigUrl { "http://..." }
    }
}
```

**Steps:**
1. User configures interaction instrumentation via DSL
2. `PulseKit.initialize()` creates `InteractionInstrumentation` instance early (if enabled)
   - Instance is stored statically via `sharedInstance`
   - This allows processors to access it later
3. `InteractionInstrumentationConfig.initialize()` is called
   - Reuses existing instance from `getInstance()`
   - Calls `instrumentation.install()`

### 2. Instrumentation Installation (`InteractionInstrumentation.install()`)

**Steps:**
1. **Initialize Manager** (async):
   ```swift
   Task {
       await interactionManager.initialize()
   }
   ```
   - Fetches interaction configs from server (or mock)
   - Creates `InteractionEventsTracker` for each config
   - Starts event processing tasks
   - Starts state observation task

2. **Observe State Changes**:
   ```swift
   stateObservationTask = Task {
       for await states in interactionManager.interactionTrackerStatesStream {
           handleInteractionStates(states)
       }
   }
   ```
   - Listens for completed interactions
   - Creates spans when interactions complete

3. **Register Processors**:
   - `InteractionAttributesSpanAppender` is registered as SpanProcessor
   - `InteractionLogListener` is already registered during SDK initialization

### 4. InteractionAttributesSpanAppender Details

**Purpose**: A `SpanProcessor` that enriches spans with interaction context and forwards span events to InteractionManager.

#### onStart() - Adding Interaction Attributes to ALL Spans

**When**: Called for **every span** created in the app (no filtering)

**What Attributes Are Added**:
- `pulse.interaction.names` (Array<String>) - Names of all currently running interactions
- `pulse.interaction.ids` (Array<String>) - IDs of all currently running interactions

**Example**:
```
User starts "CheckoutFlow" interaction
  ↓
User makes a network request (HTTP GET)
  ↓
onStart() is called for the network span
  ↓
Network span gets:
  - pulse.interaction.names: ["CheckoutFlow"]
  - pulse.interaction.ids: ["abc-123"]
  - ... (other network attributes)
```

**Purpose**: Correlation - All spans during an interaction are tagged with that interaction's info, allowing you to filter all spans by interaction ID in your backend.

#### onEnd() - Forwarding Specific Span Events

**When**: Called for **every span** that ends, but **only processes specific spans** (filtered)

**Which Spans Are Processed**:
- Must have `pulse.type` attribute set
- AND `pulse.type` must be one of:
  - `"network"` - Network request spans
  - `"screen_load"` - Screen creation spans
  - `"app_start"` - App start spans
  - `"screen_session"` - Screen session spans

**What Happens**:
- The span end is forwarded as an event to `InteractionManager`
- Event name = the `pulse.type` value (e.g., `"network"`)
- Event params include `pulse.span.id` (the span ID)

**Example**:
```
Network request completes
  ↓
HTTP POST span ends
  ↓
onEnd() checks: Does span have pulse.type = "network"? ✅ YES
  ↓
Forwards to InteractionManager:
  manager.addEvent(
      eventName: "network",
      params: ["pulse.span.id": "xyz-789"],
      eventTimeInNano: ...
  )
  ↓
InteractionManager processes event
  ↓
Checks if "network" matches any interaction config
```

**Purpose**: Event forwarding - Convert relevant span ends into interaction events that can match interaction configurations.

#### Summary Table

| Method | When Called | What It Does | Which Spans |
|--------|-------------|--------------|-------------|
| **`onStart()`** | **Every span start** | Adds interaction attributes (`pulse.interaction.names`, `pulse.interaction.ids`) | **ALL spans** |
| **`onEnd()`** | **Every span end** | Checks if span should be forwarded as interaction event | **Only spans with `pulse.type` in: `["network", "screen_load", "app_start", "screen_session"]`** |

### 4. Manager Initialization (`InteractionManager.initialize()`)

**Steps:**
1. **Fetch Configs**:
   ```swift
   guard let configs = try await interactionFetcher.getConfigs() else { return }
   ```
   - Uses `InteractionConfigRestFetcher` (real API) or `InteractionConfigMockFetcher` (testing)

2. **Create Trackers**:
   ```swift
   self.interactionTrackers = configs.map { config in
       InteractionEventsTracker(interactionConfig: config)
   }
   ```
   - One tracker per interaction configuration

3. **Start Event Processing**:
   ```swift
   // Process local events
   Task {
       for await event in eventQueue.localEventsStream {
           for tracker in trackers {
               tracker.checkAndAdd(event: event)
           }
       }
   }
   
   // Process marker events
   Task {
       for await markerEvent in eventQueue.markerEventsStream {
           for tracker in trackers {
               tracker.addMarker(markerEvent)
           }
       }
   }
   ```

4. **Start State Observation**:
   ```swift
   Task {
       while !Task.isCancelled {
           let newStates = trackers.map { $0.currentStatus }
           if newStates != interactionTrackerStates {
               interactionTrackerStates = newStates
               stateContinuation?.yield(newStates)
           }
           try? await Task.sleep(nanoseconds: 100_000_000) // Poll every 100ms
       }
   }
   ```

## Event Flow (When User Taps Event)

### Example: User calls `PulseKit.shared.trackEvent(name: "event1", ...)`

```
1. PulseKit.trackEvent()
   ↓
   logger.logRecordBuilder()
       .setBody(AttributeValue.string("event1"))
       .setAttributes(...)
       .emit()
   ↓

2. InteractionLogListener.onEmit(logRecord:)
   ↓
   - Extracts event name from log body: "event1"
   - Extracts params from log attributes
   - Gets timestamp
   ↓
   interactionManager.addEvent(
       eventName: "event1",
       params: [...],
       eventTimeInNano: ...
   )
   ↓

3. InteractionManager.addEvent()
   ↓
   - Creates InteractionLocalEvent
   - Adds to eventQueue
   ↓
   eventQueue.addEvent(event)
   ↓

4. InteractionEventQueue.addEvent()
   ↓
   - Thread-safe emission via DispatchQueue
   - Yields event to localEventsStream
   ↓

5. InteractionManager Event Processing Task
   ↓
   - Receives event from localEventsStream
   - Forwards to all trackers
   ↓
   for tracker in trackers {
       tracker.checkAndAdd(event: event)
   }
   ↓

6. InteractionEventsTracker.checkAndAdd()
   ↓
   a. Check if event matches config:
      - matchesAny(event, config.events) OR
      - matchesAny(event, config.globalBlacklistedEvents)
   ↓
   b. If matches, insert event into sorted list (by time)
   ↓
   c. Generate interaction ID:
      - If interaction closed: new UUID
      - If ongoing: reuse existing ID
      - Otherwise: new UUID
   ↓
   d. Match sequence:
      InteractionUtil.matchSequence(
          ongoingMatchInteractionId: id,
          localEvents: localEvents,
          localMarkers: localMarkers,
          interactionConfig: config
      )
   ↓

7. InteractionUtil.matchSequence()
   ↓
   - Iterates through local events
   - Matches against config event sequence
   - Handles blacklisted events
   - Returns MatchResult with:
     * shouldTakeFirstEvent: Bool
     * shouldResetList: Bool
     * interactionStatus: InteractionRunningStatus
   ↓

8. InteractionEventsTracker processes MatchResult
   ↓
   - Updates interactionRunningStatus
   - Handles reset logic
   - Launches/resets timeout timer
   ↓

9. State Observation Task (in InteractionManager)
   ↓
   - Polls trackers every 100ms
   - Detects state changes
   - Yields new states to stream
   ↓

10. InteractionInstrumentation.handleInteractionStates()
    ↓
    - Receives state updates
    - Checks for completed interactions (interaction != nil)
    ↓
    if case .ongoingMatch(_, _, let config, let interaction) = state,
       let interaction = interaction {
        createInteractionSpan(interaction: interaction, config: config)
    }
    ↓

11. createInteractionSpan()
    ↓
    a. Create span builder:
       - Name: interaction.name
       - No parent
       - Start time: first event time
    ↓
    b. Add attributes:
       - All props from interaction.props (via putAttributesFrom)
       - pulse.type = "interaction"
       - interaction.name, id, configId
       - Custom attributes from extractor (if provided)
    ↓
    c. Add span events:
       - All events from interaction.events
       - All marker events from interaction.markerEvents
    ↓
    d. Set status:
       - ERROR if interaction.isErrored
       - OK otherwise
    ↓
    e. End span:
       - End time: last event time
    ↓

12. Span is exported via OpenTelemetry exporters
```

## Sequence Matching Logic

### How It Works

1. **Event Sequence**: Config defines sequence like `[event1, event2, event3]`
2. **Real-time Matching**: Events are matched as they arrive (sorted by time)
3. **State Machine**:
   - `NoOngoingMatch`: No active match
   - `OngoingMatch(interaction: nil)`: Match in progress, waiting for next event
   - `OngoingMatch(interaction: Interaction)`: Match completed (success or error)

### Matching Rules

1. **Exact Sequence Match**: Events must match config sequence in order
2. **Blacklisted Events**: If blacklisted event matches during ongoing match, reset
3. **Property Matching**: Events can have properties that must match:
   - Operators: EQUALS, CONTAINS, STARTS_WITH, etc.
4. **Timeout**: If sequence doesn't complete within `thresholdInMs`, mark as error

### Example

**Config:**
```json
{
  "name": "LoginFlow",
  "events": [
    {"name": "login_start"},
    {"name": "login_success"}
  ],
  "thresholdInMs": 5000
}
```

**User Events:**
1. `trackEvent("login_start")` → OngoingMatch(index: 0, interaction: nil)
2. `trackEvent("login_success")` → OngoingMatch(index: 1, interaction: Interaction(success))

**Result**: Span created with both events, APDEX score calculated.

## Timeout Handling

### How It Works

1. When `OngoingMatch(interaction: nil)` is set, a timer is launched
2. Timer duration: `config.thresholdInMs + 10ms` (small buffer)
3. If timer completes before sequence finishes:
   - Create error interaction with current events
   - Mark as errored
   - Clear event list
   - Set `isInteractionClosed = true`

### Example

**Config:** `thresholdInMs: 5000`
**Events:**
1. `event1` at T=0 → Timer starts (5000ms)
2. No `event2` arrives
3. Timer fires at T=5000ms → Error interaction created

## APDEX Score Calculation

### Formula

```
if timeDifferenceInMs <= lowerLimit:
    apdexScore = 1.0
else if timeDifferenceInMs <= midLimit:
    apdexScore = 1.0 - (0.5 * (time - lowerLimit) / (midLimit - lowerLimit))
else if timeDifferenceInMs <= upperLimit:
    apdexScore = 0.5 - (0.5 * (time - midLimit) / (upperLimit - midLimit))
else:
    apdexScore = 0.0
```

### User Categories

- **Excellent**: apdexScore >= 0.95
- **Good**: apdexScore >= 0.85
- **Average**: apdexScore >= 0.7
- **Poor**: apdexScore < 0.7

## Span Attributes

### Default Attributes (Always Added)

- `pulse.interaction.name`: Interaction name
- `pulse.interaction.id`: Unique interaction ID
- `pulse.interaction.config.id`: Config ID
- `pulse.type`: `"interaction"`
- All props from `interaction.props`:
  - `pulse.interaction.apdex_score`: Double
  - `pulse.interaction.user_category`: String
  - `pulse.interaction.complete_time`: Int64
  - `pulse.interaction.last_event_time`: Int64
  - etc.

### Custom Attributes

Users can provide `attributeExtractor` closure to add custom attributes:

```swift
InteractionInstrumentationConfiguration(
    attributeExtractor: { interaction in
        return [
            "custom.attr": AttributeValue.string("value")
        ]
    }
)
```

## Debugging Tips

### Check if Events Are Being Captured

1. Verify `InteractionLogListener` is registered:
   - Check `PulseKit.initialize` logs
   - Ensure interaction is enabled

2. Check if Manager is Initialized:
   - Look for `[PulseKit] Interaction: Failed to initialize` logs
   - Verify configs are fetched (check tracker count)

3. Check Event Flow:
   - Events should appear in `InteractionEventQueue`
   - Trackers should receive events via `checkAndAdd()`

### Check if Interactions Are Matching

1. Verify Config:
   - Check event names match exactly
   - Check property matching rules
   - Verify threshold is reasonable

2. Check State:
   - Monitor `interactionTrackerStatesStream`
   - Look for `OngoingMatch` states
   - Check if `interaction` is set (completed)

### Check if Spans Are Created

1. Verify Span Creation:
   - Check `createInteractionSpan()` is called
   - Verify `timeSpanInNanos` is not nil
   - Check span attributes are set

2. Verify Export:
   - Check OpenTelemetry exporter logs
   - Verify spans appear in backend

## Common Issues

### 1. Tracker Count is Zero

**Cause**: Manager not initialized or configs not fetched
**Fix**: Check initialization logs, verify API endpoint

### 2. Events Not Matching

**Cause**: Event names don't match or properties don't match
**Fix**: Verify config event names and property rules

### 3. Spans Not Created

**Cause**: `timeSpanInNanos` is nil (less than 2 events)
**Fix**: Ensure sequence has at least 2 events

### 4. Timeout Not Working

**Cause**: Timer not launched or cancelled prematurely
**Fix**: Check `launchResetTimer()` logic

## File Structure

```
Interaction/
├── InteractionInstrumentation.swift          # Main entry point
├── InteractionInstrumentationConfiguration.swift
├── InteractionLogListener.swift             # LogRecordProcessor
├── InteractionAttributesSpanAppender.swift  # SpanProcessor
├── Core/
│   ├── InteractionManager.swift             # Orchestrator
│   ├── InteractionEventsTracker.swift       # Per-config tracker
│   ├── InteractionEventQueue.swift          # Event queue
│   ├── InteractionUtil.swift                # Matching logic
│   ├── Interaction.swift                    # Interaction model
│   ├── InteractionRunningStatus.swift       # State enum
│   └── InteractionAttributes.swift            # Constants
├── Models/
│   ├── InteractionConfig.swift
│   ├── InteractionEvent.swift
│   └── ...
└── API/
    ├── InteractionConfigFetcher.swift
    ├── InteractionConfigRestFetcher.swift
    └── InteractionConfigMockFetcher.swift
```

## Key Design Decisions

1. **Lazy Initialization**: Manager is created lazily, only when needed
2. **Static Instance**: Allows processors to access instrumentation instance
3. **Real-time Processing**: Events processed as they arrive (not batched)
4. **State Polling**: Trackers polled every 100ms for state changes
5. **Thread Safety**: Event queue uses DispatchQueue for thread-safe emission
6. **Default Attributes**: All interaction props added to spans automatically

