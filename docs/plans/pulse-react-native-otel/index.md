# pulse-react-native-otel — Plan Handbook

Cross-platform RN SDK: JS-side facade + native Android/iOS bridges. Every signal carries `platform = react-native`.

Brief: [../../components/pulse-react-native-otel.md](../../components/pulse-react-native-otel.md)

## Sub-files

### core/

| File | Topic |
|---|---|
| [facade.md](./core/facade.md) | `Pulse` object exported from `src/index.tsx` — public API surface |
| [platform-bridge.md](./core/platform-bridge.md) | `NativePulseReactNativeOtel` turbo-module + Android/iOS glue |
| [semconv.md](./core/semconv.md) | Attribute keys + `pulse.type` enum, parity with other SDKs |

### instrumentations/

| File | Topic |
|---|---|
| [crash.md](./instrumentations/crash.md) | JS uncaught + native crash forwarding |
| [network.md](./instrumentations/network.md) | XHR/fetch interceptor → `pulse.type = http` spans |
| [session.md](./instrumentations/session.md) | `session.start` / `session.end` lifecycle |
| [screen.md](./instrumentations/screen.md) | React Navigation + Expo Router screen signals |
| [interaction.md](./instrumentations/interaction.md) | Touch handler → `app.click` logs |

## Reading order

1. Brief.
2. `core/facade.md` → understand the public API.
3. `core/platform-bridge.md` → why calls are guarded by `isSupportedPlatform()`.
4. `core/semconv.md` for the contract.
5. Specific instrumentation file for the change at hand.

## Rebuild checklist

1. `yarn create @react-native-community/library pulse-rn` (or equivalent turbo-module template).
2. Define the turbo-module spec in `NativePulseReactNativeOtel.ts` — methods: `init`, `emitSpan`, `emitLog`, `setUser`, `flush`.
3. Implement Android Kotlin module + iOS Swift module that forwards to the native SDKs.
4. On JS: build `Pulse` facade in `src/index.tsx` wrapping the turbo-module, with `isSupportedPlatform` guard.
5. Add instrumentations one at a time (order: session → network → error → screen → interaction).
6. Wire React Navigation listener (optional peer) for screen events.
7. Publish with a `react-native.config.js` so autolinking works.
