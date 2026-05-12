# RN · Facade (`Pulse`)

Single public entry point exported from `pulse-react-native-otel/src/index.tsx`.

Brief: [../../../components/pulse-react-native-otel.md](../../../components/pulse-react-native-otel.md) · Peers: [platform-bridge](./platform-bridge.md), [semconv](./semconv.md).

## Purpose

Expose a minimal, stable JS API that apps call. Every method delegates to `NativePulseReactNativeOtel` (turbo-module) or, when offline/unsupported, to a JS-only fallback.

## Source location

- `pulse-react-native-otel/src/index.tsx` — the facade (re-exports + the `Pulse` object).
- `pulse-react-native-otel/src/initialization.ts` — `init` implementation.
- `pulse-react-native-otel/src/config.ts` — config type + defaults.
- `pulse-react-native-otel/src/events.ts`, `src/trace.ts` — low-level emit helpers.

## Public surface (from `src/index.tsx`)

```ts
Pulse.init(config: PulseConfig): Promise<void>
Pulse.setUser(user: PulseUser | null): void
Pulse.setGlobalAttribute(key: string, value: AttributeValue): void
Pulse.captureError(error: unknown, extras?: object): void
Pulse.startScreen(name: string): void
Pulse.endScreen(name: string): void
Pulse.flush(): Promise<void>
Pulse.ErrorBoundary: React.ComponentType          // from errorBoundary.tsx
Pulse.isSupportedPlatform(): boolean
```

Types live in `src/pulse.interface.ts`.

## Internal design

- Every public method first calls `isSupportedPlatform()` — on web/test-renderer it's a no-op returning a resolved promise.
- `init` chains: validate config → call native `init` → register JS handlers (error, network, nav listener if present).
- `captureError` uses `src/errorHandler.ts` to normalize the error into an OTel exception record, then emits a log with `pulse.type = non_fatal`.
- Global attrs merged by `src/globalAttributes.ts` on every emission.

## Dependencies

- `react-native` peer.
- Turbo-module spec: `NativePulseReactNativeOtel.ts`.
- Optional peers: `@react-navigation/native`, `expo-router` (for screen instr).

## Data contracts

See [semconv.md](./semconv.md). Every emission carries `platform=react-native`, `session.id`, `app.build_name`, plus any user/global attrs.

## Tests

- `src/__tests__/` — JS unit tests (Jest) covering: unsupported-platform guard, init validation, setUser → native call, captureError normalizer.
- Example apps exercise end-to-end on device.

## History / decisions

Single facade (vs. multiple named exports) to keep autocomplete clean and lock the public surface — internal modules are free to refactor.

## Rebuild recipe

1. Create `src/index.tsx` exporting a frozen `Pulse` object.
2. Mirror the signatures above, each delegating to the turbo-module.
3. Wrap every method in a try/catch + `isSupportedPlatform` guard.
4. Freeze the object with `Object.freeze(Pulse)`.
