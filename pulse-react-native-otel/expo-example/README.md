# Pulse Expo Example

Example Expo app for the Pulse React Native SDK and its config plugin.

## Run it

```bash
npm install
npm run prebuild
npm run ios   # or npm run android
```

## iOS: local Pulse vs hosted PulseKit

### Option 1: Local Pulse iOS (monorepo testing)
Keep the `withPulseKitLocalPod` plugin in `app.json`. It adds `pod 'PulseKit', :path => …` to the generated `ios/Podfile`.

- Default: `pulse-ios-otel` next to **`pulse-react-native-otel`** inside the same Pulse monorepo (`../../../pulse-ios-otel` from `ios/`).
- Custom folder: use the plugin tuple and pass **`pulseKitLocalPath`** as an **absolute** path to the folder that contains `PulseKit.podspec`:

```json
"plugins": [
  ["./plugins/withPulseKitLocalPod", { "pulseKitLocalPath": "/Users/you/repos/pulse-ios-otel" }],
  ...
]
```

Then `npm run prebuild` (or `pod install` under `ios/`).

>**Dynamic frameworks (this example only)**  
`app.json` includes **`expo-build-properties`** with **`ios.useFrameworks: "dynamic"`**. That way **local source** `pulse-ios-otel` (Swift + CocoaPods `libwebp`) does not hit Expo’s default static-library + modular-headers issue. It only applies to this dev app’s prebuild output, not to published customer apps.

### Option 2: Hosted PulseKit
Remove `withPulseKitLocalPod` from `app.json` and run `npm run prebuild` again. CocoaPods uses **PulseKit** from the podspec. You can remove or keep `expo-build-properties`; keeping **dynamic** frameworks is still valid for the example.

## Android: local Pulse vs hosted Maven

### Option 1: Local Pulse Android
Keep `./plugins/withMavenLocal` in `app.json`. After `expo prebuild`, the Android project’s root `build.gradle` includes `mavenLocal()`, so dependencies can be satisfied from your machine’s `~/.m2/repository`.

1. **Publish the Android SDK version the RN bridge expects** (must match `pulse_version` in `pulse-react-native-otel/android/build.gradle`, e.g. `0.0.8-alpha`). From the **Pulse monorepo root**:

   ```bash
   cd pulse-android-otel
   ./gradlew publishToMavenLocal -Pfinal=true
   ```

2. Run `npm run prebuild` and then `npm run android`.

### Option 2: Hosted Pulse Android
Remove `./plugins/withMavenLocal` from `app.json` and run `npm run prebuild` again. The app resolves `org.dreamhorizon:*` artifacts from **Maven Central** only, as in the RN library’s `build.gradle` (no `mavenLocal()`).
