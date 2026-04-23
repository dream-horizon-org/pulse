# Expo integration

## Pulse ships an **Expo config plugin** so Android / iOS native SDKs are wired from **`app.json`**.

## Quick setup

### 1. Install

```bash
npx expo install @dreamhorizonorg/pulse-react-native
```

### 2. Add the plugin to `app.json`

```json
{
  "expo": {
    "plugins": [
      [
        "@dreamhorizonorg/pulse-react-native",
        {
          "endpointBaseUrl": "https://otel.example.com",
          "apiKey": "your-api-key"
        }
      ]
    ]
  }
}
```

`endpointBaseUrl` and `apiKey` are required.

### 3. Start Pulse in JavaScript (as early as possible)

Call **`Pulse.start`** once at **module scope** in your root file (e.g. `app/_layout.tsx` or `index.js`) so it runs before the rest of the app:

```tsx
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

Pulse.start();
```

### 4. Build native apps

```bash
npx expo prebuild --clean
npx expo run:ios
# or
npx expo run:android
```

Change **`app.json`** → run **`prebuild`** again (or a new EAS build).

---

## Platform overrides

Top-level plugin fields apply to both platforms. Override per OS with **`android`** / **`ios`**:

```json
{
  "endpointBaseUrl": "https://otel.example.com",
  "apiKey": "your-api-key",
  "android": {
    "endpointBaseUrl": "http://10.0.2.2:4318"
  },
  "ios": {
    "globalAttributes": { "env": "production" }
  }
}
```

`globalAttributes`, `instrumentation`, and `configuration` belong **inside** `android` or `ios`, not at the root of the plugin block.

---

## Full `app.json` plugin example

```json
[
  "@dreamhorizonorg/pulse-react-native",
  {
    "endpointBaseUrl": "https://otel.example.com",
    "apiKey": "your-api-key",
    "dataCollectionState": "PENDING",
    "endpointHeaders": { "X-Custom-Header": "value" },
    "configEndpointUrl": "https://otel.example.com/v1/configs/active/",
    "customEventCollectorUrl": "https://otel.example.com/v1/logs",
    "android": {
      "endpointBaseUrl": "http://10.0.2.2:4318",
      "globalAttributes": { "platform": "android" },
      "instrumentation": {
        "crash": { "enabled": true },
        "network": { "enabled": true },
        "activity": { "enabled": true },
        "interaction": {
          "enabled": true,
          "url": "https://api.example.com/v1/interactions"
        }
      }
    },
    "ios": {
      "globalAttributes": { "platform": "ios" },
      "configuration": {
        "includeScreenAttributes": true,
        "includeNetworkAttributes": true,
        "includeGlobalAttributes": true
      },
      "instrumentation": {
        "crash": { "enabled": true },
        "screenLifecycle": { "enabled": true },
        "interaction": {
          "enabled": true,
          "configUrl": "https://api.example.com/v1/interactions"
        }
      }
    }
  }
]
```

---

## Plugin options reference

### Top level

| Option                    | Required | Type   | Description                       |
| ------------------------- | -------- | ------ | --------------------------------- |
| `endpointBaseUrl`         | **Yes**  | string | OTLP / Pulse backend URL          |
| `apiKey`                  | **Yes**  | string | Project API key                   |
| `dataCollectionState`     | **Yes**  | string | `PENDING`, `ALLOWED`, or `DENIED` |
| `endpointHeaders`         | No       | object | Extra HTTP headers                |
| `configEndpointUrl`       | No       | string | Remote SDK config URL             |
| `customEventCollectorUrl` | No       | string | Custom events / logs URL          |
| `android`                 | No       | object | Android overrides                 |
| `ios`                     | No       | object | iOS overrides                     |

### Android — `android.instrumentation`

| Key             | Shape                                    | Description                        |
| --------------- | ---------------------------------------- | ---------------------------------- |
| `crash`         | `{ "enabled": boolean }`                 | Crashes                            |
| `network`       | `{ "enabled": boolean }`                 | Network                            |
| `activity`      | `{ "enabled": boolean }`                 | Activity lifecycle                 |
| `fragment`      | `{ "enabled": boolean }`                 | Fragments                          |
| `anr`           | `{ "enabled": boolean }`                 | ANR                                |
| `slowRendering` | `{ "enabled": boolean }`                 | Slow frames                        |
| `interaction`   | `{ "enabled": boolean, "url"?: string }` | Interactions + optional config URL |

### Android — `android.globalAttributes`

Values: strings, numbers, booleans, or arrays of those types.

### Android — `android.coreLibraryDesugaring` (optional)

| Field     | Type    | Default | Description                                                                                                                                                                                      |
| --------- | ------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `enabled` | boolean | `false` | When `true`, the config plugin adds `compileOptions { coreLibraryDesugaringEnabled true }` and `coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:…'` to **`android/app/build.gradle`**. |
| `version` | string  | `2.1.4` | Desugar JDK libs version; only used when `enabled` is `true`.                                                                                                                                    |

**Note:** Turn **`enabled`** on only when you need it (for example Java 8+ APIs on **older `minSdkVersion`**). Typical case: **`minSdkVersion` below 26** (API 25 and lower). If your Expo / app **`minSdkVersion` is 26 or higher**, you usually **do not** need core library desugaring for this reason—leave it **`false`** to avoid extra desugar work and dependency surface.

Example:

```json
"android": {
  "coreLibraryDesugaring": {
    "enabled": true,
    "version": "2.1.4"
  }
}
```

Omit `version` to use the default `2.1.4`.

### Android — `android.okHttpInstrumentation` (optional)

React Native on Android uses **OkHttp** for most HTTP traffic. This block exists so **native OkHttp** can be **instrumented for network telemetry** (Pulse's `okhttp3-library` / `okhttp3-agent` plus the **Byte Buddy** Gradle plugin). If you do not need outbound HTTP spans from the native stack, ** Omit the `okHttpInstrumentation` key entirely.

| Field | Type | Default | Description |
| ----- | ---- | ------- | ----------- |
| `enabled` | boolean | `false` | When `true`, prebuild merges Byte Buddy + OkHttp instrumentation into Gradle. |
| `libraryVersion` | string | `0.0.10-alpha` | Version for **both** `okhttp3-library` and `okhttp3-agent` in **`android/app/build.gradle`**. |
| `byteBuddyGradlePluginVersion` | string | `1.17.8` | Version for `net.bytebuddy:byte-buddy-gradle-plugin` on the **root** `android/build.gradle` `buildscript` classpath. |

**Note:** To prevent duplicate declarations and build conflicts, the plugin **skips** its own injection and logs a **`console.warn`** if it detects that:

- The Byte Buddy **buildscript** classpath is already defined in the **root** `android/build.gradle`, or  
- **Both** the instrumentation (`okhttp3-library` and `okhttp3-agent`) are already present in **`android/app/build.gradle`**.

Example:

```json
"android": {
  "okHttpInstrumentation": {
    "enabled": true,
    "libraryVersion": "0.0.10-alpha",
    "byteBuddyGradlePluginVersion": "1.17.8"
  }
}
```

### iOS — `ios.configuration`

| Key                        | Type    | Description                  |
| -------------------------- | ------- | ---------------------------- |
| `includeScreenAttributes`  | boolean | Screen metadata on telemetry |
| `includeNetworkAttributes` | boolean | Network metadata             |
| `includeGlobalAttributes`  | boolean | Global attributes            |

### iOS — `ios.instrumentation` (simple toggles)

`{ "enabled": boolean }` each: `crash`, `appLifecycle`, `screenLifecycle`, `appStartup`, `location`, `signPost`.

#### `urlSession`

| Field     | Type    | Description           |
| --------- | ------- | --------------------- |
| `enabled` | boolean | Instrument URLSession |

OTLP export URLs on the collector origin are skipped automatically in PulseKit (no Expo field).

#### `sessions`

| Field                                | Type    | Description             |
| ------------------------------------ | ------- | ----------------------- |
| `enabled`                            | boolean | Sessions                |
| `maxLifetimeSeconds`                 | number  | Max session length      |
| `backgroundInactivityTimeoutSeconds` | number  | Background timeout      |
| `shouldPersist`                      | boolean | Persist across launches |

#### `interaction`

| Field     | Type    | Description  |
| --------- | ------- | ------------ |
| `enabled` | boolean | Interactions |

Interaction config URL comes from remote SDK config, not `app.json`.

#### `uiKitTap`

| Field            | Type    | Description              |
| ---------------- | ------- | ------------------------ |
| `enabled`        | boolean | UIKit tap events         |
| `captureContext` | boolean | Label from hierarchy     |
| `rage`           | object  | Optional rage-tap config |

#### `sessionReplay`

| Field                   | Type     | Description                           |
| ----------------------- | -------- | ------------------------------------- |
| `enabled`               | boolean  | Replay on/off                         |
| `replayEndpointBaseUrl` | string   | Upload base URL                       |
| `textAndInputPrivacy`   | string   | e.g. `maskAll`, `maskSensitiveInputs` |
| `imagePrivacy`          | string   | e.g. `maskAll`, `maskNone`            |
| `maskViewClasses`       | string[] | Always mask                           |
| `unmaskViewClasses`     | string[] | Never mask                            |
| `captureIntervalMs`     | number   | Capture interval                      |
| `compressionQuality`    | number   | 0–1                                   |
| `screenshotScale`       | number   | Scale                                 |
| `flushIntervalSeconds`  | number   | Flush interval                        |
| `flushAt`               | number   | Batch size hint                       |
| `maxBatchSize`          | number   | Max batch                             |

---

## Not in `app.json`

Requires native or app code: before-send hooks, custom OTel resources, custom providers, per-request iOS URL filtering.

---

## Troubleshooting

| Issue                    | Fix                                                                                                                      |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------ |
| Plugin error on prebuild | Set `endpointBaseUrl` + `apiKey`; keep `instrumentation` / `configuration` / `globalAttributes` under `android` / `ios`. |
| iOS: Pulse not starting  | Default Expo `AppDelegate` is patched. Custom entry → call **`PulseSDK.initialize(...)`** before RN boots.               |
| Android: same            | Default **`MainApplication`**. Custom → **`Pulse.initialize(...)`** manually.                                            |
| Old JS from monorepo     | From **`pulse-react-native-otel`**: **`npx bob build`**, then **`expo start --clear`**.                                  |

[React Native overview](https://pulse.dreamhorizon.org/docs/sdk/react-native/overview) · [Installation](https://pulse.dreamhorizon.org/docs/sdk/react-native/installation)

---

## Custom Instrumentations

### Expo Router

Wire Pulse to Expo Router’s root navigation ref and set `registerWhenContainerReady: true` so registration runs when the container is ready, without duplicating `NavigationContainer` `onReady` logic

```tsx
import { Stack, useNavigationContainerRef } from 'expo-router';
import { useNavigationTracking } from '@dreamhorizonorg/pulse-react-native';

function Root() {
  const navigationRef = useNavigationContainerRef();
  useNavigationTracking(navigationRef, {
    registerWhenContainerReady: true,
  });
  return <Stack />;
}
```

### Custom events

In order to emit analytics event we can use `trackEvent` API.

```tsx
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

Pulse.trackEvent('order_placed', { orderId: '42', total: 99.5 });
```

### Screen interactive — content ready (when you use that API)

```tsx
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

// After first meaningful paint on the current screen
Pulse.markContentReady();
```

### Android — click modules (Gradle)

`app.json` enables Pulse wiring; **view clicks** still need OTel artifacts in **`android/app/build.gradle`** (after `prebuild`):

```kotlin
// XML / classic views (pulse-android-otel/instrumentation/view-click)
implementation("org.dreamhorizon.instrumentation:view-click:0.0.8-alpha")
```

### iOS — UIKit taps

Only **`app.json`** — under **`ios.instrumentation.uiKitTap`**, for example:

```json
"uiKitTap": {
  "enabled": true,
  "captureContext": true
}
```
