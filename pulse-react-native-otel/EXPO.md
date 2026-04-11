# Expo Integration

Pulse ships a **config plugin** for Expo apps with native code. It wires up the Android and iOS SDKs from `app.json` — no Kotlin or Swift edits needed on each prebuild.

**Requires:** `expo prebuild`, EAS Build, `expo run:android`, or `expo run:ios`.  
**Not supported:** Expo Go.

---

## Quick setup

### 1. Install

```bash
npx expo install @dreamhorizonorg/pulse-react-native
```

### 2. Add to `app.json`

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

Only `endpointBaseUrl` and `apiKey` are required. Everything else is optional.

### 3. Rebuild

```bash
npx expo prebuild --clean
npx expo run:ios   # or run:android
```

For EAS Build, trigger a new build after any config change.

---

## Platform overrides

Top-level values apply to both platforms. Use `android` / `ios` blocks to override per platform:

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

> `globalAttributes`, `instrumentation`, and `configuration` must be inside `android` or `ios` — not at the root.

---

## Full example

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
        "interaction": { "enabled": true, "url": "https://api.example.com/v1/interactions" }
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
        "interaction": { "enabled": true, "configUrl": "https://api.example.com/v1/interactions" }
      }
    }
  }
]
```

---

## Options reference

### Top level


| Option                    | Required | Type                                   | Description                          |
| ------------------------- | -------- | -------------------------------------- | ------------------------------------ |
| `endpointBaseUrl`         | **Yes**  | string                                 | Pulse backend URL                    |
| `apiKey`                  | **Yes**  | string                                 | Project API key                      |
| `dataCollectionState`     | No       | `"PENDING"` | `"ALLOWED"` | `"DENIED"` | Initial consent state                |
| `endpointHeaders`         | No       | `{ [key: string]: string }`            | Extra HTTP headers for all traffic   |
| `configEndpointUrl`       | No       | string                                 | Remote SDK config URL                |
| `customEventCollectorUrl` | No       | string                                 | Full URL for custom event collection |
| `android`                 | No       | object                                 | Android overrides (see below)        |
| `ios`                     | No       | object                                 | iOS overrides (see below)            |


---

### Android (`android`)

Any top-level field can be overridden here. Additionally:

#### `android.instrumentation`


| Key             | Shape                                    | Description                                   |
| --------------- | ---------------------------------------- | --------------------------------------------- |
| `crash`         | `{ "enabled": boolean }`                 | Crash reporting                               |
| `network`       | `{ "enabled": boolean }`                 | Network monitoring                            |
| `activity`      | `{ "enabled": boolean }`                 | Activity lifecycle                            |
| `fragment`      | `{ "enabled": boolean }`                 | Fragment lifecycle                            |
| `anr`           | `{ "enabled": boolean }`                 | ANR detection                                 |
| `slowRendering` | `{ "enabled": boolean }`                 | Slow frame detection                          |
| `interaction`   | `{ "enabled": boolean, "url"?: string }` | Interaction tracking; `url` for remote config |


#### `android.globalAttributes`

Attach metadata to all Android telemetry. Supported value types: `string`, `number`, `boolean`, and arrays of each.

---

### iOS (`ios`)

Any top-level field can be overridden here. Additionally:

#### `ios.configuration`


| Key                        | Type    | Description                     |
| -------------------------- | ------- | ------------------------------- |
| `includeScreenAttributes`  | boolean | Attach screen info to telemetry |
| `includeNetworkAttributes` | boolean | Attach network info             |
| `includeGlobalAttributes`  | boolean | Attach global attributes        |


#### `ios.instrumentation`

**Simple on/off** — all accept `{ "enabled": boolean }`:


| Key               | Description                |
| ----------------- | -------------------------- |
| `crash`           | Crash reporting            |
| `appLifecycle`    | App lifecycle events       |
| `screenLifecycle` | UIViewController lifecycle |
| `appStartup`      | Startup timing             |
| `location`        | Location events            |
| `signPost`        | OS signpost integration    |


`**urlSession`**


| Field                  | Type    | Description                                         |
| ---------------------- | ------- | --------------------------------------------------- |
| `enabled`              | boolean | URLSession instrumentation                          |
| `excludeOtlpEndpoints` | boolean | Exclude your `endpointBaseUrl` from instrumentation |


`**sessions**`


| Field                                | Type    | Description                          |
| ------------------------------------ | ------- | ------------------------------------ |
| `enabled`                            | boolean | Session tracking                     |
| `maxLifetimeSeconds`                 | number  | Max session duration                 |
| `backgroundInactivityTimeoutSeconds` | number  | Inactivity timeout when backgrounded |
| `shouldPersist`                      | boolean | Persist sessions across launches     |


`**interaction**`


| Field       | Type    | Description                       |
| ----------- | ------- | --------------------------------- |
| `enabled`   | boolean | Interaction tracking              |
| `configUrl` | string  | URL for remote interaction config |


`**uiKitTap**`


| Field            | Type    | Description                                 |
| ---------------- | ------- | ------------------------------------------- |
| `enabled`        | boolean | Automatic tap/click logging                 |
| `captureContext` | boolean | Extract label from view hierarchy           |
| `rage`           | object  | `{ timeWindowMs, rageThreshold, radiusPt }` |


`**sessionReplay**`


| Field                   | Type                                                      | Description                |
| ----------------------- | --------------------------------------------------------- | -------------------------- |
| `enabled`               | boolean                                                   | Session replay             |
| `replayEndpointBaseUrl` | string                                                    | Replay upload URL          |
| `textAndInputPrivacy`   | `"maskAll"` | `"maskAllInputs"` | `"maskSensitiveInputs"` | Text masking level         |
| `imagePrivacy`          | `"maskAll"` | `"maskNone"`                                | Image masking              |
| `maskViewClasses`       | string[]                                                  | Class names to always mask |
| `unmaskViewClasses`     | string[]                                                  | Class names to never mask  |
| `captureIntervalMs`     | number                                                    | Screenshot interval        |
| `compressionQuality`    | number                                                    | Image quality (0–1)        |
| `screenshotScale`       | number                                                    | Screenshot scale factor    |
| `flushIntervalSeconds`  | number                                                    | Upload interval            |
| `flushAt`               | number                                                    | Batch flush size           |
| `maxBatchSize`          | number                                                    | Max events per batch       |


---

## What can't be configured from `app.json`

These require native code and are not generated by the plugin:

- Before-send hooks (spans, logs, metrics)
- Custom OpenTelemetry resource blocks
- Custom tracer/logger provider pipelines
- Per-request URL filtering for iOS URLSession

---

## Troubleshooting

**Prebuild fails with a plugin error**  
Usually means missing `endpointBaseUrl`/`apiKey`, empty strings, or `globalAttributes`/`instrumentation`/`configuration` placed at the root instead of inside `android`/`ios`.

**iOS: Pulse missing in AppDelegate**  
The plugin targets the default Expo Swift AppDelegate. If you use a custom entry point, call `PulseSDK.initialize(...)` manually before React Native starts.

**Android: Pulse missing in MainApplication**  
The plugin expects the standard Expo `MainApplication`. If yours is custom, call `Pulse.initialize(...)` manually.

**More help**  
[React Native overview](https://pulse.dreamhorizon.org/docs/sdk/react-native/overview) · [Installation](https://pulse.dreamhorizon.org/docs/sdk/react-native/installation)