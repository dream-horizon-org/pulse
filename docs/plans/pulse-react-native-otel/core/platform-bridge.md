# RN · Platform Bridge

Turbo-module spec + native glue. Lets JS call into the host-native SDK (pulse-android-otel / pulse-ios-otel).

Brief: [../../../components/pulse-react-native-otel.md](../../../components/pulse-react-native-otel.md) · Peers: [facade](./facade.md).

## Source location

- Spec: `pulse-react-native-otel/src/NativePulseReactNativeOtel.ts` (CodeGen turbo-module).
- Android impl: `pulse-react-native-otel/android/src/main/java/.../PulseReactNativeOtelModule.kt`.
- iOS impl: `pulse-react-native-otel/ios/PulseReactNativeOtel.swift` (+ `.mm` wrapper).
- C++ shared: `pulse-react-native-otel/cpp/` (rare — only if turbomodule uses JSI shared code).
- Expo config plugin: `pulse-react-native-otel/plugin/` + `app.plugin.js`.
- Autolink config: `pulse-react-native-otel/react-native.config.js`.

## Public surface (spec)

```ts
interface Spec extends TurboModule {
  init(config: object): Promise<void>;
  emitSpan(span: object): void;
  emitLog(log: object): void;
  setUser(user: object | null): void;
  setGlobalAttribute(key: string, value: string): void;
  flush(): Promise<void>;
}
```

## Internal design

- **Android:** module extends `ReactContextBaseJavaModule`, lazily initializes `com.pulse.android.PulseAndroid` on `init`. Method calls are synchronous (fire-and-forget) except `init`/`flush`.
- **iOS:** `@objc` Swift class inheriting `RCTEventEmitter` (turbo-module); forwards to `PulseKit.shared`.
- The JS side never touches these directly — it goes through [facade.md](./facade.md). The `isSupportedPlatform()` guard in `src/utility.ts` returns `false` on Web / when `TurboModuleRegistry.get(...)` returns null.
- Exporter runs **inside the host SDK** (not JS). JS side just funnels signal shapes across the bridge.

## Data contracts

Serialized shapes (JSON) are flat objects mirroring OTel span/log JSON. See [semconv.md](./semconv.md).

## Tests

- Android: `android/src/test/...PulseModuleTest.kt` verifies the bridge delegates to the core SDK.
- iOS: `ios/Tests/...` uses a mock `PulseKit`.
- JS: mocks `TurboModuleRegistry.getEnforcing(...)` and asserts calls.

## History / decisions

Turbo-modules (CodeGen) over legacy bridge so interop works under the new architecture and sync methods are available; avoids double-serialization vs. the legacy event emitter.

## Rebuild recipe

1. Define spec in `src/NativePulseReactNativeOtel.ts` and include it in `codegenConfig` of `package.json`.
2. Implement Android module using the host Android SDK; register in a `ReactPackage`.
3. Implement iOS module delegating to PulseKit.
4. Add `react-native.config.js` for autolinking.
5. Ship an Expo config plugin (`plugin/`) that inserts the needed Info.plist / AndroidManifest entries for crash handlers.
