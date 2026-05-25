# expo-example-rn076

Minimal Expo SDK 52 / RN 0.76.9 smoke target. Exists to keep CI honest about Kotlin-1.9
consumer support after PR #727.

**Not** for daily development — use `expo-example/` (Expo SDK 54 / RN 0.81) for that.

## What this sample exercises

- RN 0.76 ships **Kotlin 1.9.25** by default. Pulse SDK is compiled with Kotlin 2.0.21
  and transitively pulls libraries with Kotlin 2.1.x `.kotlin_module` metadata, which a
  1.9 compiler cannot read.
- The Pulse config-plugin prop `android.kotlin19Compat: true` (in this app's
  `app.json`) tells prebuild to write `PulseReactNativeOtel_kotlin19Compat=true` into
  `android/gradle.properties`. The SDK's `android/build.gradle` reads that flag and
  activates a `constraints { strictly("[1.9, 2.1)") }` block that caps every transitive
  Kotlin runtime artifact to a 1.9-readable version.
- The `withMavenLocal` plugin adds `mavenLocal()` to the generated Android root
  `build.gradle` so CI's `m2-publish` job-produced AARs are visible.

## CI

Built on every PR by `.github/workflows/rn-expo-android-rn076.yml`, orchestrated from
`.github/workflows/ci-react-native.yml` alongside the existing `rn-expo` job.

## Manual run

```bash
cd pulse-react-native-otel
yarn install
cd expo-example-rn076
npm install
npx expo prebuild --platform android --clean
cd android
./gradlew :app:assembleDebug
```

A `BUILD SUCCESSFUL` means the Kotlin-1.9 compat path is intact.
