# Design Summary — Screen navigation signals (v4-screen-signals)

## What

Emit three Pulse span types to track user navigation and time on screen:
- **`screen_load`**: page load + SPA route changes with timing data (`page.load_time`, `ttfb`, `tti`, etc.)
- **`screen_interactive`**: time-to-interactive (DOM interactive milestone)
- **`screen_session`**: time spent on screen before navigating away

Unblocks: UI Screens tab + web vitals per screen.

## Why

- **Android parity**: matches `ActivityInstrumentation` span types
- **OTel aligned**: spans are the right semantic for timed events with ordering
- **UI ready**: ClickHouse already has `ScreenName` materialized column; queries written (waiting for data)
- **Session context**: every span on a screen automatically carries `screen.name` via global processor

## How (quick checklist)

| Phase | Artifact | Owner | Status |
|---|---|---|---|
| **0–3** | Research + ADR + PLAN-B + touchpoints | Claude | ✅ Done |
| **3→** | Grill with team | User | ⏳ Next (this skill) |
| **4–5** | Implementation + E2E + Ralph loop | Ralph agent | After grill |
| **6** | PR review + merge | Team | Final |

## Reading order

1. **Quick start**: this file + [README.md](./README.md)
2. **Research**: [01-ecosystem.md](./01-research-screen-signals-ecosystem-and-industry.md) + [02-otel-pulse.md](./02-research-otel-js-browser-and-pulse-sdk.md)
3. **Spec**: [PLAN-B-screen-navigation-spans.md](./PLAN-B-screen-navigation-spans.md) (lifecycle, attributes, unit matrix, E2E cases)
4. **Decision**: [ADR-screen-navigation.md](./ADR-screen-navigation.md) (why spans vs metrics, grill summary)
5. **Rejected**: [PLAN-A-metrics-histogram.md](./PLAN-A-metrics-histogram.md) (why not metrics)
6. **Touchpoints**: [03-touchpoints-matrix.md](./03-touchpoints-matrix.md) (all files touched, cross-package)

## Key decisions locked

✅ **Span type**: OTel trace spans (logs w/ structured attrs) not metrics  
✅ **Gate**: `PulseFeature.SCREEN_NAVIGATION` (backend controls)  
✅ **Flush**: `loggerProvider.forceFlush()` on visibility + pagehide  
✅ **SPA detection**: Framework integration + History API patch fallback  
✅ **Screen name resolution**: Manual > pattern > heuristic > pathname (4-step fallback)  
✅ **Cross-package**: SDK + backend (`Features.java`) + UI (no schema changes)  

## Follow-up features (unblocked by this)

Once screen signals land:
- **Web vitals per screen**: add LCP/CLS/FID to `screen_load` attributes
- **Session funnel**: query: user saw screens A → B → C then churned
- **Slow-screen detection**: alert when mean `page.load_time` spikes on checkout

## Known limitations (v1)

- **Hash routes** (`/#/...`): fallback config needed (not auto-detected)
- **BFCache**: tracked but no special visualization
- **Remote sampling**: all-or-nothing; per-screen sampling deferred
