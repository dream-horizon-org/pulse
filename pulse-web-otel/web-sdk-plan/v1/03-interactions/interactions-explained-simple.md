# Pulse Interactions — Simple Explanation

This document explains how web interactions work in plain language, including all important success, failure, and edge cases.

---

## What is an interaction?

An interaction is a multi-step user journey, for example:
- `cart_viewed` -> `checkout_started` -> `order_placed`

The SDK listens to `PulseWeb.trackEvent(...)` calls and tries to match those events against server-provided interaction definitions.  
When a journey completes (or fails), the SDK emits one `pulse.type = interaction` span.

---

## Big Picture Flow

```mermaid
flowchart TD
  sdkStart[PulseWeb.start] --> gateCheck{Interaction feature enabled and consent allowed}
  gateCheck -->|No| interactionOff[Interaction subsystem stays off]
  gateCheck -->|Yes| modeCheck{Dev API key isLocalEnvironment}

  modeCheck -->|Yes| restFetch[Fetch REST config /v1/interaction-configs with X-API-KEY]
  modeCheck -->|No| prodFetch[Fetch prod config /config/projects/projectId/interaction-config.json]

  restFetch --> parseConfig[Parse and validate InteractionConfig array]
  prodFetch --> parseConfig
  parseConfig --> cacheUpdate[Update cache and notify coordinator]
  cacheUpdate --> readyTrackers[Create trackers one per interaction config]

  appEvent[App calls PulseWeb.trackEvent] --> customEventPath[Existing custom event log path]
  appEvent --> interactionPath[Interaction coordinator fan-out]
  interactionPath --> trackerEval[Each tracker evaluates same event]
  trackerEval --> resultCheck{Completed or error result?}
  resultCheck -->|Still ongoing| waitMore[Keep matching with inter-step timer]
  resultCheck -->|Yes| buildSpan[Build interaction span with pulse.interaction keys]
  buildSpan --> samplingCheck{ExportSamplingGate allows export?}
  samplingCheck -->|No| dropped[Span dropped before export]
  samplingCheck -->|Yes| exported[Export OTLP span]
```

---

## Runtime Behavior (All Cases)

```mermaid
flowchart TD
  eventIn[trackEvent name props timestampMs] --> hasTracker{Tracker is ongoing?}

  hasTracker -->|No| firstMatch{Matches first required step?}
  firstMatch -->|No| idleNoop[Ignore for this tracker]
  firstMatch -->|Yes| startOngoing[Start interaction set step timer]

  hasTracker -->|Yes| globalBlacklist{Event in globalBlacklistedEvents?}
  globalBlacklist -->|Yes| silentReset[Reset to IDLE silently no error span]
  globalBlacklist -->|No| expectedMatch{Matches expected next step and props?}

  expectedMatch -->|Yes| advanceStep[Advance step reset inter-step timer]
  advanceStep --> skipOptional[Skip over any optional steps to next required]
  skipOptional --> doneCheck{All required steps matched?}
  doneCheck -->|No| keepOngoing[Stay ONGOING]
  doneCheck -->|Yes| completeResult[Emit completed interaction result]

  expectedMatch -->|No| firstStepAgain{Matches first required step?}
  firstStepAgain -->|Yes| restartFlow[Emit SEQUENCE_VIOLATION for old flow then restart]
  firstStepAgain -->|No| seqViolation[Emit SEQUENCE_VIOLATION and reset IDLE]

  timerExpire[Inter-step timer expires] --> timeoutResult[Emit TIMEOUT error and reset IDLE]
```

---

## Important Rules

- **Inter-step timeout (not whole-flow timeout)**: timer resets after each matched step.
- **Global blacklist event**: if seen during an ongoing interaction, tracker resets silently.
- **Wrong next event**: emits `SEQUENCE_VIOLATION` error interaction.
- **First-step restart during ongoing flow**: emits `SEQUENCE_VIOLATION` for old flow, then starts a new flow from that event.
- **Synchronous matching**: every `trackEvent` is immediately evaluated by all trackers.
- **Per-event timestamp support**: `trackEvent(name, attrs?, timestampMs?)` uses provided time or `Date.now()`. Note: `timestampMs` is a new optional parameter added in M2 — the pre-M2 signature is `trackEvent(name, attrs?)` only.

---

## How scoring and span fields work

For every completed or errored result, the span builder emits:
- `pulse.type = interaction`
- `pulse.interaction.id`
- `pulse.interaction.name`
- `pulse.interaction.config.id`
- `pulse.interaction.config.name`
- `pulse.interaction.complete_time` (nanoseconds)
- `pulse.interaction.apdex_score`
- `pulse.interaction.user_category` (`Excellent`, `Good`, `Average`, `Poor`)
- `pulse.interaction.is_error`
- `pulse.interaction.error.type` and `pulse.interaction.error.message` (error only)

Scoring:
- <= lower limit -> `Excellent`, score `1.0`
- <= mid limit -> `Good`, score `0.75`
- <= upper limit -> `Average`, score `0.5`
- > upper limit -> `Poor`, score `0.0`
- any error -> always `Poor`, score `0.0`

Unit rule:
- `pulse.interaction.complete_time` must be in nanoseconds (`durationMs * 1_000_000`), not milliseconds.

---

## Non-Happy Paths and Safety

- **Config fetch failure**: no crash; interactions remain disabled or continue with cached config.
- **Missing CDN base URL in non-local mode**: interactions disable silently; SDK does not throw.
- **Corrupt cache or storage errors**: ignored safely; runtime continues.
- **Consent denied**: interaction tracking is not started.
- **Feature gate off**: interaction tracking is not started.
- **Sampling drop**: interaction span can be built but dropped before export.
- **Config refresh while flow is ongoing**: old trackers are shut down; in-flight match is discarded silently; new trackers start with new config.
- **Shutdown**: clears interaction timers and refresh timers.

---

## What app developers need to do

- Call `PulseWeb.start(...)` once.
- Call `PulseWeb.trackEvent(name, attrs?, timestampMs?)` at meaningful flow steps.
- Keep event names and props aligned with interaction definitions from backend config.

No extra interaction API is needed beyond `trackEvent`.

---

## Quick Debug Checklist

- No interaction spans at all:
  - check feature gate `interaction`
  - check consent state
  - check config fetch success (REST/CDN mode)
  - check network request to `pulse-otel-collector.pulse-ux.com/config/projects/{projectId}/interaction-config.json`
- Spans exist but wrong analytics:
  - verify all keys are `pulse.interaction.*` (not bare `interaction.*`)
  - verify `complete_time` is nanos
  - verify user category strings are `Excellent/Good/Average/Poor`
- Flows failing unexpectedly:
  - check `globalBlacklistedEvents`
  - check property filters/operator matches
  - check timeout threshold for each step
