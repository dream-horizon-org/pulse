# Expo example (shop + Pulse)

Shop demo (DummyJSON) + **Pulse** in `app/_layout.tsx`. Uses **native** Pulse — **not Expo Go**. Use a development build.

```bash
cd pulse-react-native-otel/expo-example
npm install
npm run prebuild
npm run ios       # or: npm run android
npx expo start    # JS against the dev client you built
```

Plugin reference: [`../EXPO.md`](../EXPO.md).

---

## iOS — how to build

### Option 1 — PulseKit **from source** (default, CI)

1. By default this points to source code of iOS written in `../../pulse-ios-otel`.
2. After **`npm run prebuild`**, the Podfile gets `pod 'PulseKit', :path => ../../../pulse-ios-otel` (monorepo: `expo-example/ios` → **`pulse-ios-otel`**).
3. **`npm run ios`**.

**SDK not at the default relative path?** In `app.json`:

```json
[
  "./plugins/withPulseKitLocalPod",
  { "pulseKitLocalPath": "/absolute/path/to/pulse-ios-otel" }
]
```

---

### Option 2 — PulseKit **from xcframeworks**

1. Do **Option 2** in [`../../pulse-ios-otel/Examples/PulseIOSExample/README.md`](../../pulse-ios-otel/Examples/PulseIOSExample/README.md) (build xcframeworks, edit **`pulse-ios-otel/PulseKit.podspec`**, etc.).
2. From **`expo-example`**: **`npm run prebuild`** (use **`--clean`** if you switched modes).
3. **`npm run ios`**.
4. When you go back to **Option 1**, revert **`pulse-ios-otel/PulseKit.podspec`** (e.g. `git checkout -- pulse-ios-otel/PulseKit.podspec`).

---

## Android — how to build

### Option 1 — **Maven Local** (default, uses `~/.m2`)

1. Leave **`"./plugins/withMavenLocal"`** in **`app.json`** `plugins` (adds `mavenLocal()` to the generated project).
2. Publish the Android SDK once (from **`pulse-android-otel`**):

   ```bash
   ./gradlew publishToMavenLocal
   ```

3. From **`expo-example`**: **`npm run prebuild`** then **`npm run android`**.

---

### Option 2 — **Hosted** Maven (no local publish)

1. Remove **`"./plugins/withMavenLocal"`** from **`app.json`** `plugins`.
2. **`npm run prebuild -- --clean`**
3. **`npm run android`**

Gradle resolves Pulse from published coordinates in **`pulse-react-native-otel/android/build.gradle`**, not `mavenLocal()`.
