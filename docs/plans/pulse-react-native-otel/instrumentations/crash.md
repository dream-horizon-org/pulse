# RN · Crash

Captures JS uncaught errors + forwards native crashes from the host SDKs.

Brief: [../../../components/pulse-react-native-otel.md](../../../components/pulse-react-native-otel.md) · Peers: [../core/facade](../core/facade.md), [session](./session.md).

## Source location

- `pulse-react-native-otel/src/errorHandler.ts` — installs `ErrorUtils.setGlobalHandler`.
- `pulse-react-native-otel/src/errorBoundary.tsx` — React `ErrorBoundary` wrapper (class component).
- Native bridge: crash capture for signals happens inside the host SDK (pulse-android-otel / pulse-ios-otel); the RN layer only consumes the emitted log on next launch.

## Public surface

- `Pulse.captureError(error, extras?)` — manual non-fatal.
- `<Pulse.ErrorBoundary fallback={...} onError={...}>` — React tree guard.

## Internal design

JS path:
1. On `init`, call `ErrorUtils.setGlobalHandler` — chain the prior handler so RN's red-box UI still fires in dev.
2. For each error, normalize via `errorHandler.normalize(e)` → `{type, message, stack}`.
3. Emit log with `pulse.type = non_fatal` (or `device.crash` if `isFatal=true`).

Native path:
- Android/iOS crash handlers persist a record and emit it on next launch (see [../../pulse-android-otel/instrumentations/crash.md](../../pulse-android-otel/instrumentations/crash.md), [../../pulse-ios-otel/instrumentations/crash.md](../../pulse-ios-otel/instrumentations/crash.md)). RN does not re-process those.

## Data contracts

- `pulse.type`: `non_fatal` or `device.crash`
- `exception.type`, `exception.message`, `exception.stacktrace`
- `platform = react-native`
- `component.stack` (for `ErrorBoundary`)

## Tests

`src/__tests__/errorHandler.test.ts` — simulates `ErrorUtils` invocation and asserts emitted shape. ErrorBoundary test renders a throwing child and asserts `onError` + emit.

## History / decisions

Left native crashes to the host SDKs rather than duplicating on the JS side — avoids race between JS handler and the native signal crash.

## Rebuild recipe

1. In `initialization.ts`, call `ErrorUtils.setGlobalHandler((err, isFatal) => {...})` chaining the previous handler.
2. Normalize stacks with `stacktrace-parser`.
3. Build the `ErrorBoundary` class component with `componentDidCatch`.
4. Document that native fatal crashes come through the host SDK, not this layer.
