# pulse-react-native-otel — Component Brief

## What

React Native RUM SDK that wraps the Pulse Android and Pulse iOS SDKs behind a single TypeScript facade. JS-side handles JS exceptions, network interception (fetch + XHR), React Navigation tracking, error boundaries and span APIs; the native bridge delegates to the underlying mobile SDKs for crashes, sessions, ANR, lifecycle and exporting.

## Path & tech stack

- Path: `/Users/ujjwal.bagrania/Desktop/pulse/pulse-react-native-otel/`
- Stack: TypeScript strict + react-native-builder-bob, TurboModule native bridge (`Spec extends TurboModule`), Lefthook pre-commit (`lefthook.yml`).
- Distribution: `@dreamhorizonorg/pulse-react-native` on npm.
- Architectures: supports both Old and New Architecture (turbo-module + codegen).
- Expo: optional config plugin (`app.plugin.js`, `plugin/`, `EXPO.md`).

## Build commands

From `pulse-react-native-otel/`:

```bash
yarn install
yarn prepare          # `npm run build:plugin && bob build` — produces lib/ and plugin/build/
yarn test             # jest
yarn typecheck        # tsc
yarn lint             # eslint
yarn example ios      # run example app on iOS
yarn example android  # run example app on Android
yarn clean
```

See `pulse-react-native-otel/README.md`, `pulse-react-native-otel/package.json`, `pulse-react-native-otel/EXPO.md`.

## Single Pulse facade

The library exports exactly one entry point — `Pulse` — from `src/index.tsx`:

```ts
export const Pulse = {
  start, shutdown, isInitialized, setDataCollectionState,
  useNavigationTracking, markContentReady,
  trackEvent, reportException, trackSpan, startSpan,
  setGlobalAttribute, setUserId, setUserProperty, setUserProperties,
  ErrorBoundary, withErrorBoundary,
};
```

Plus types (`PulseConfig`, `Span`, `NetworkHeaderConfig`, navigation types) and enums (`SpanStatusCode`, `PulseDataCollectionConsent`, `PulseLogLevel`). `PulseMask` / `PulseUnmask` for session replay are exported separately.

## `isSupportedPlatform()` guard

Every native call funnels through `src/initialization.ts`:

```ts
export function isSupportedPlatform(): boolean {
  return Platform.OS === 'android' || Platform.OS === 'ios';
}
```

`isInitialized()` and the rest of the facade short-circuit when this returns false, so the package is safe to import under web / unsupported platforms.

## Key files

- `src/index.tsx` — public facade.
- `src/NativePulseReactNativeOtel.ts` — TurboModule spec (`Spec extends TurboModule`) plus `PulseDataCollectionConsent` enum.
- `src/config.ts` — `start/shutdown/setDataCollectionState`, feature gating from `getFeaturesFromRemoteConfig`.
- `src/initialization.ts` — platform guard + cached `isInitialized()`.
- `src/errorHandler.ts`, `src/errorBoundary.tsx` — JS exception capture.
- `src/network-interceptor/` — fetch + XHR interception.
- `src/navigation/` — React Navigation hook (`useNavigationTracking`) + integration.
- `src/trace.ts`, `src/events.ts`, `src/user.ts`, `src/globalAttributes.ts`.
- `src/sessionReplay/` — `PulseMask`, `PulseUnmask` view wrappers.
- `src/redaction.ts`, `src/remoteFeatures.ts`, `src/sessionState.ts`.
- `android/src/` — Kotlin TurboModule impl bridging into `pulse-android-sdk`.
- `ios/PulseSDK.swift`, `PulseReactNativeOtel.mm`, `PulseReactNativeOtelTracer.swift`, `PulseReactNativeOtelLogger.swift` — Swift/ObjC bridging into PulseKit.
- `plugin/` + `app.plugin.js` — Expo config plugin source.

## Cross-SDK parity contract

Native SDKs do most of the work; the RN bridge only ensures `pulse.type` and `platform` are set consistently across the JS + native boundary. JS-originated signals match the same enum used by Android / iOS / web:

| `pulse.type` | Source |
|---|---|
| `session.start` / `session.end` | Native (Android + iOS session instrumentation) |
| `device.crash` | Native; JS fatal exceptions promoted via `reportException(..., isFatal=true)` |
| `device.anr` | Android-only, emitted by the native Android SDK |
| `non_fatal` | `Pulse.reportException` (JS) + native non-fatal API |
| `network` | `src/network-interceptor/` (fetch + XHR) |
| `app.click` | Native (Android view-click / iOS UIKitTap) |
| `screen_load` / `screen_session` | `src/navigation/` + native swizzling — coordinated via `ReactNativeScreenNameTracker.swift` / Android screen attribute appenders |
| `custom_event` | `Pulse.trackEvent` → `trackEvent` TurboModule call |

The native side carries `telemetry.sdk.name` = `pulse_android_rn` or `pulse_ios_rn` (see `PulseAttributes.PulseSdkNames`).

## Plan handbook

See `/Users/ujjwal.bagrania/Desktop/pulse/docs/plans/pulse-react-native-otel/index.md`.
