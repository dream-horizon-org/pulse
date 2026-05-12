# RN · Screen

Hooks React Navigation / Expo Router to emit `screen_load`, `screen_interactive`, `screen_session` signals.

Brief: [../../../components/pulse-react-native-otel.md](../../../components/pulse-react-native-otel.md) · Peers: [../core/semconv](../core/semconv.md), [session](./session.md).

## Source location

- `pulse-react-native-otel/src/navigation/` — React Navigation listener + Expo Router bridge.
- Facade API: `Pulse.startScreen(name)` / `Pulse.endScreen(name)` from `src/index.tsx`.

## Public surface

- Auto mode: pass `navigationRef` to a helper (`PulseReactNavigationInstrumentation(navigationRef)`).
- Manual mode: call `Pulse.startScreen('Checkout')` / `Pulse.endScreen('Checkout')`.

## Internal design

1. On `focus` of a route: close prior `screen_session`, emit new `screen_load` (start) + `screen_session` (start).
2. When Nav transition completes (`transitionEnd`): close `screen_load`, start `screen_interactive` (closes when the next commit idles).
3. On `blur` / navigation away: close `screen_session` with duration.

For Expo Router, hook the `segments` changes; for React Navigation, use `addListener('state', ...)` against the `navigationRef`.

## Data contracts

| Signal | Attrs |
|---|---|
| `screen_load` | `screen.name`, `duration_ms` |
| `screen_interactive` | `screen.name`, `time_to_interactive_ms` |
| `screen_session` | `screen.name`, `session_duration_ms` |

All carry `platform=react-native`, `session.id`.

## Tests

`src/__tests__/navigation.test.ts` — stubs a navigation ref and asserts span ordering.

## History / decisions

Listener-based approach instead of wrapping navigator components so apps can keep idiomatic routers.

## Rebuild recipe

1. Create a registrar that accepts a nav ref, subscribes to state changes.
2. Track open spans per route id.
3. Expose a manual API for apps with custom routers.
