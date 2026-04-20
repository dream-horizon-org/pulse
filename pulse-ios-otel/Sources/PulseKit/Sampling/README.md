# Pulse Sampling

Remote configuration for the Pulse iOS SDK that controls session sampling, signal filtering, collector URLs, and instrumenation flags. Config is fetched from a backend API and applied per launch.

---

## Overview

The sampling feature is implemented in this directory and integrates with the rest of PulseKit.

---

## Configure

**Config endpoint**

- By default, the SDK derives the config URL from `endpointBaseUrl`: it replaces port `4318` with `8080` and appends `/v1/configs/active/`.
- To use a different config endpoint, pass `configEndpointUrl`:

```swift
PulseKit.shared.initialize(
    endpointBaseUrl: "https://your-collector.com",
    apiKey: "your-api-key",
    configEndpointUrl: "https://config.example.com/v1/configs/active/"
)
```

---

## Capabilities

Capabilities are applied in this order (config at init, then per-signal pipeline):

1. **Collector URLs** – Override OTLP endpoints from config at init (`signals.logsCollectorUrl`, `spanCollectorUrl`, etc.).
2. **Feature flags** – Enable/disable instrumentations at init (crash, interaction, network, etc.) via `signals.features` and `getEnabledFeatures()`.
3. **Session sampling** – Each app session is either fully sampled or not, based on rules (device attributes + sample rate) and a random draw. When exporting, the batch is first reduced to either all signals (session sampled) or only critical-event signals (session not sampled).
4. **Critical events** – When the session is not sampled, only signals matching `criticalEventPolicies.alwaysSend` are kept; all others are dropped.
5. **Signal filtering** – Whitelist/blacklist spans, logs, and metrics by name and attributes (`signals.filters`).
6. **Attribute drop** – Remove attributes on matching spans and logs (`signals.attributesToDrop`).
7. **Attribute add** – Add attributes on matching spans and logs (`signals.attributesToAdd`).
8. **Exporter selection** – Route each processed signal to the first matching exporter (e.g. custom events to a dedicated collector).

**How they interact**

- **Feature flags** and **session sampling** are independent: feature flags (`signals.features`) control which instrumentations run; session sampling (`sampling.rules`, `sampling.default`) has its own config and decides per session whether to keep all signals or only critical-event ones. One does not affect the other.
- **Signal filtering** (`signals.filters`, both whitelist and blacklist) runs **after** session sampling. It only sees the batch that passed the session/critical-event step. So when session sampling is 0%, filters do **not** see all signals—they only see the signals that matched `criticalEventPolicies.alwaysSend` (or an empty batch). When the session is sampled, filters see the full batch. In both cases, filters then further allow or block each of those signals.

---

## Payload structure

The backend returns a JSON object for `GET /v1/configs/active/` (or the configured URL). The iOS SDK decodes it into `PulseSdkConfig` and related types in `PulseSdkConfigModels.swift`.

```json
{
  "version": 1,
  "description": "Production config",
  "sampling": {
    "default": { "sessionSampleRate": 0.5 },
    "rules": [],
    "criticalEventPolicies": null
  },
  "signals": {
    "scheduleDurationMs": 0,
    "logsCollectorUrl": "",
    "metricCollectorUrl": "",
    "spanCollectorUrl": "",
    "customEventCollectorUrl": "",
    "filters": { "mode": "whitelist", "values": [] },
    "attributesToDrop": [],
    "attributesToAdd": []
  },
  "interaction": { ... },
  "features": []
}
```

---

## Payload sections (iOS)

### version (Int)

Identifies the config revision. When a fetch returns a config with a different `version` than the one in storage, the new config is persisted and used on the **next** launch. See [Loading & persistence](#loading--persistence).

### description (String)

Human-readable description.

### sampling.default.sessionSampleRate (Float)

Default session sample rate in `[0, 1]` when **no rule** matches.

- **1.0** – All sessions sampled.
- **0.5** – ~50% of sessions sampled (random draw).
- **0** – No sessions sampled (only critical-event signals are exported).

### sampling.rules (Array)

Ordered rules; **first match wins**. Each rule is evaluated only if the current SDK is in `sdks`; then the device attribute value is matched against `value` (regex). Implemented in `PulseSessionConfigParser` and `PulseSessionSamplingRule.matches(deviceContext:)`.

| Field | Type | Description |
|-------|------|-------------|
| `name` | enum | Device attribute: `os_version`, `app_version`, `country`, `platform`, `state` |
| `value` | string | Regex pattern to match the attribute value |
| `sdks` | array | SDKs this rule applies to (e.g. `["pulse_ios_swift", "pulse_ios_rn"]`) |
| `sessionSampleRate` | float | Rate to use when this rule matches |

**Device attributes (iOS)** – provided by `PulseDeviceContext`:

| name | Source |
|------|--------|
| `os_version` | `UIDevice.current.systemVersion` (e.g. "18.4") |
| `app_version` | `CFBundleShortVersionString` or `CFBundleVersion` |
| `country` | `Locale.current.region?.identifier` (e.g. "IN", "US") |
| `platform` | `"pulse_ios_swift"` (native iOS) |
| `state` | Not implemented; returns nil; rule does not match |

**SDK names:** `pulse_ios_swift`, `pulse_ios_rn`, `pulse_android_java`, `pulse_android_rn` (see `PulseSdkName`).

**Example rule** – sample 100% when OS is 18.x:

```json
{
  "name": "os_version",
  "value": "18.*",
  "sdks": ["pulse_ios_swift"],
  "sessionSampleRate": 1.0
}
```

### sampling.criticalEventPolicies (optional)

When the session is **not** sampled, only signals matching `alwaysSend` conditions are exported. Each condition matches by signal name (regex), attributes (props), scope (logs/traces/metrics), and SDK. **Within a single condition**: name AND scope AND sdk AND (all props) must match. **Between conditions**: OR.

`signals.filters` still apply to critical-event signals: with whitelist, critical events must match at least one filter; with blacklist, they are dropped if they match any blacklist condition.

### signals.filters

Controls which signals are exported (sampled sessions and critical-event-only sessions). Implemented in `PulseSamplingSignalProcessors` via `PulseSignalMatcher`.

| mode | Behaviour |
|------|------------|
| `whitelist` | Export only signals that match at least one condition |
| `blacklist` | Export all signals except those matching any condition |

Each condition has `name` (regex), `props` (attribute key/value pairs), `scopes`, and `sdks`. Within one condition: AND. Between conditions: whitelist = OR; blacklist = export if none match. Empty `values` with whitelist = export all; with blacklist = export none.

### signals.attributesToDrop

Remove attributes from spans/logs whose name and condition match. Each entry has a **condition** (name, props, scopes, sdks) and **values** (attribute names to drop). Within a condition: AND. Between entries: OR; dropped keys are unioned.

Attribute drop is applied in `SampledSpanExporter` and `SampledLogExporter` using config from `getDroppedAttributesConfig(scope:)`.

### signals.attributesToAdd

Add attributes to spans/logs when the condition matches. Each entry has **condition** and **values** (name, value, type). Within a condition: AND. Between entries: OR; all matching entries add their attributes.

Implemented in `SampledSpanExporter` and `SampledLogExporter` via `getAddedAttributesConfig(scope:)`.

### signals.metricsToAdd

Derives metrics from spans and logs that match the condition. Each entry specifies a **target** (what to record) and a **condition** (which signals to match).

**Target types**

- **name** – Use the signal name (span name or log body) as the data point; counters add 1 per match.
- **attribute** – Record the numeric value of an attribute (e.g. `response_time_ms`) as a histogram or gauge. If the condition matches multiple attribute values, **all those values are aggregated** into the same metric.

**shouldAddPropNameAsSuffix** (attribute target only)

- **false (default)** – One metric for all matching attribute values. All values are aggregated into `<name>`.
- **true** – A separate metric per matched attribute **key**. Metric names become `<name>.<key_name_of_the_prop_matched>`. Example: condition matching `http.method` and `http.status_code` emits `http_count.http.method` and `http_count.http.status_code`.

The API accepts both `shouldAddPropNameAsSuffix` and `addPropNameAsSuffix`; the canonical key is `shouldAddPropNameAsSuffix`.

**Metric type × shouldAddPropNameAsSuffix behavior**

| Type | shouldAddPropNameAsSuffix: false | shouldAddPropNameAsSuffix: true |
|------|----------------------------------|---------------------------------|
| **Counter** | One metric `<name>`. Each match adds 1 (counts occurrences). E.g. 3 spans with `http.duration` → `http_req_count` = 3. | One metric per attr key `<name>.<key>`. Each match adds 1. E.g. spans with `http.method` (GET) and `http.status_code` (200) → `http_count.http.method` = 1, `http_count.http.status_code` = 1. If 2 spans have `http.method` → `http_count.http.method` = 2. |
| **Gauge** | One metric `<name>`. Each match records the attr value (e.g. battery level). Multiple values from different spans/logs all feed the same gauge. | One metric per attr key `<name>.<key>`. E.g. `battery_level.battery.percent` and `battery_level.battery.temp` if condition matches both keys. |
| **Histogram** | One metric `<name>`. Each match records the attr value as a bucket entry. E.g. 3 spans with `http.duration`: 100, 200, 300 → one histogram with 3 observations. | One metric per attr key `<name>.<key>`. E.g. `http_duration.http.duration` (same as false if only one key matches). If condition matches `http.duration` and `response.size` → `latency.http.duration` and `latency.response.size`. |
| **Sum** | One metric `<name>`. Each match adds the attr value. E.g. 3 spans with `bytes_sent`: 100, 200, 50 → sum = 350. | One metric per attr key `<name>.<key>`. E.g. `bytes.http.request.content_length` and `bytes.http.response.content_length`. |

**Examples**

- **Counter, suffix false:** `name: "span_count"`, target: name → counts all matching spans in one metric.
- **Counter, suffix true:** `name: "http_count"`, target: attribute matching `http.method` and `http.status_code` → `http_count.http.method` (count of spans with http.method), `http_count.http.status_code` (count of spans with http.status_code).
- **Histogram, suffix false:** `name: "http_duration"`, target: attribute `http.duration` → one histogram aggregating all duration values from matching spans.
- **Histogram, suffix true:** `name: "latency"`, target: attribute matching `http.duration` and `db.duration` → `latency.http.duration`, `latency.db.duration`.

See `PulseMetricsToAddModels.swift` for target/type structure and `PulseSamplingSignalProcessors.recordMetricsForSignal` for implementation.

### signals.features and getEnabledFeatures()

`PulseSamplingSignalProcessors.getEnabledFeatures()` returns feature names where:

- `sessionSampleRate == 1`
- Current SDK is in `sdks`

Features not in the list are treated as disabled. Example names: `ios_crash`, `interaction`, `network_instrumentation`, `screen_session`, `custom_events` (see `PulseFeatureName`).

---

## Multiple conditions – OR vs first match

| Feature | Semantics |
|---------|-----------|
| **sampling.rules** | **First match wins** – rules evaluated in order; first matching rule supplies the session sample rate. |
| **signals.filters** (whitelist) | **OR** – export if the signal matches at least one condition. |
| **signals.filters** (blacklist) | **None match** – export if the signal matches no condition. |
| **criticalEventPolicies.alwaysSend** | **OR** – export if the signal matches any condition. |
| **attributesToDrop** | **OR** – all matching conditions contribute; dropped keys are unioned. |
| **attributesToAdd** | **OR** – all matching entries add their attributes. |
| **SelectedLogExporter / SelectedSpanExporter** | **First match wins** – first (condition, exporter) that matches is used; order in config matters. |

---

## Loading & persistence

### On app launch (sync)

1. `PulseSdkConfigCoordinator.loadCurrentConfig()` reads from `PulseSdkConfigStorage` (UserDefaults suite `pulse_sdk_config`, key `sdk_config`).
2. Decode to `PulseSdkConfig?`. If none or decode fails → use `Pulse.initialize` defaults (no sampling).
3. If config exists → apply sampling, collector URLs, and feature flags for this launch.

### Background fetch (async)

1. After init, PulseKit can call `startBackgroundFetch(configEndpointUrl:endpointHeaders:currentConfigVersion:)`.
2. GET config from the API via `PulseSdkConfigRestProvider`.
3. If fetch succeeds and `newConfig.version != currentConfig.version` → persist via `PulseSdkConfigStorage.saveSync`.
4. New config is **not** applied in the current run; it is used on the **next** launch.

### Summary

| Scenario | Behaviour |
|----------|-----------|
| No config in storage | Use init defaults; no sampling |
| Config in storage | Use it for this launch |
| Fetch fails (network, 4xx, 5xx) | Keep existing config; no overwrite |
| Fetch returns same version | No persist |
| Fetch returns new version | Persist; use on next launch |

Config is always applied on **next** launch; the running app does not switch mid-session.
