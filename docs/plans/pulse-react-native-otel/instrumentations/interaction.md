# RN · Interaction

Touch capture → `pulse.type = app.click`.

Brief: [../../../components/pulse-react-native-otel.md](../../../components/pulse-react-native-otel.md) · Peers: [../core/semconv](../core/semconv.md), [screen](./screen.md).

## Source location

- JS: custom `GestureHandlerRootHOC`-style wrapper or a `Touchable` patch in `src/` (folder TBD if not yet landed).
- Native listeners live in the host SDKs; this file documents the JS path.

## Public surface

- Auto: install a root `TapCapture` in `App.tsx` via `Pulse.wrapApp(App)` (if exposed).
- Manual: `Pulse.captureClick({ target, screen })` for custom gestures.

## Internal design

1. Tap propagates through the RN responder chain; interceptor records:
   - deepest handler component displayName
   - `accessibilityLabel`
   - `testID` (stable id)
2. Emit log per tap, debounced 50 ms on `(testID, screen.name)`.
3. Screen attribution from the current active screen (see [screen.md](./screen.md)).

## Data contracts

- `pulse.type = app.click`
- `target.id` (testID), `target.type`, `target.label`
- `screen.name`
- `platform = react-native`

## Tests

Component tests mount a pressable and assert emit on press; debounce verified by fake timers.

## History / decisions

`testID` as stable target id because it's a widely-used RN convention and survives minification.

## Rebuild recipe

1. Install a root handler that observes `PanResponder` `release` events.
2. Build target path via `findNodeHandle` + component displayName walk.
3. Emit with the contract above.
