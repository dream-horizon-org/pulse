# Pulse - Project Understanding Document

> A comprehensive reference document for understanding the Pulse observability platform architecture, components, and data flow.

---

## 1. Project Overview

**Pulse** is an open-source **Digital Experience Platform (DXP)** for mobile applications that provides:
- Real-time user monitoring and analytics
- Distributed tracing with OpenTelemetry
- Crash and ANR reporting
- Performance monitoring
- Alerting system

### Core Philosophy
- **OpenTelemetry Native**: Built on OTel standards for vendor-neutral observability
- **Open Source**: Apache 2.0 licensed
- **Self-Hosted**: Full control over data and infrastructure

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              MOBILE SDKs                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │
│  │  Android Java   │  │  Android RN     │  │  iOS (planned)  │              │
│  │  (Kotlin/OTel)  │  │  (React Native) │  │                 │              │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘              │
└───────────┼───────────────────┼───────────────────┼─────────────────────────┘
            │                   │                   │
            │ OTLP/HTTP         │ OTLP/HTTP         │ OTLP/HTTP
            ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         OTEL COLLECTOR                                       │
│  • Receives traces, logs, metrics via OTLP                                  │
│  • Routes to appropriate backends                                           │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │
            ┌────────────────────┼────────────────────┐
            ▼                    ▼                    ▼
┌───────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   ClickHouse      │  │   MySQL         │  │   Object Store  │
│   (Analytics)     │  │   (Metadata)    │  │   (Configs)     │
│                   │  │                 │  │                 │
│ • otel_traces     │  │ • interactions  │  │ • SDK configs   │
│ • otel_logs       │  │ • alerts        │  │                 │
│ • otel_metrics    │  │ • sdk_configs   │  │                 │
│ • stack_traces    │  │ • users         │  │                 │
└───────────────────┘  └─────────────────┘  └─────────────────┘
            │                    │
            └──────────┬─────────┘
                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PULSE SERVER (Vert.x/Java)                          │
│  • RESTful APIs                                                             │
│  • Reactive/non-blocking                                                    │
│  • Query aggregation                                                        │
│  • SDK configuration management                                             │
│  • Alert evaluation                                                         │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PULSE UI (React/TypeScript)                         │
│  • Dashboard with analytics                                                 │
│  • Session replay/timeline                                                  │
│  • Critical interaction tracking                                            │
│  • Alert management                                                         │
│  • SDK configuration                                                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Repository Structure

```
pulse/
├── backend/
│   ├── server/              # Main Java/Vert.x backend
│   ├── pulse-alerts-cron/   # Alert evaluation cron job
│   └── ingestion/           # OTel collector config, ClickHouse schema
├── pulse-android-otel/      # Android SDK (Kotlin, OpenTelemetry-based)
├── pulse-react-native-otel/ # React Native SDK
├── pulse-ui/                # React/TypeScript dashboard
└── deploy/                  # Docker, scripts, DB migrations
```

---

## 4. SDK Architecture

### 4.1 Android SDK (`pulse-android-otel/`)

**Built on top of OpenTelemetry Android SDK**, providing:

#### Auto-Instrumentation Modules:
| Module | Purpose |
|--------|---------|
| `crash/` | Java/Kotlin crash reporting |
| `anr/` | Application Not Responding detection |
| `activity/` | Activity lifecycle tracing |
| `fragment/` | Fragment lifecycle tracing |
| `interaction/` | User interaction tracking (clicks, scrolls) |
| `network/` | Network state monitoring |
| `okhttp3/` | OkHttp request instrumentation |
| `slowrendering/` | Slow/frozen frame detection |
| `startup/` | App startup time measurement |
| `sessions/` | Session management |

#### SDK Core Components:
- **`PulseSDK`**: Main entry point for initialization
- **`SessionIdRatioBasedSampler`**: Session-based sampling (samples entire sessions, not individual events)
- **`PulseSignalProcessor`**: Processes and exports telemetry

#### Telemetry Types:
- **Traces** (spans): Performance, interactions, network calls
- **Logs**: Crashes, ANRs, custom events
- **Metrics**: Gauges for performance data

### 4.2 React Native SDK (`pulse-react-native-otel/`)

Wraps the native Android SDK with JavaScript bindings for React Native apps.

---

## 5. Backend Architecture

### 5.1 Pulse Server (`backend/server/`)

**Tech Stack**: Java 17, Vert.x (reactive), MySQL, ClickHouse

#### Key Layers:
```
Controller (REST) → Service (Business Logic) → DAO (Data Access)
```

#### Main Domains:

| Domain | Purpose |
|--------|---------|
| **Configs** | SDK configuration management |
| **Interactions** | Critical interaction definitions and metrics |
| **Alerts** | Alert rules, evaluation, notifications |
| **Sessions** | Session timeline and replay |
| **Analytics** | Aggregated metrics and queries |

### 5.2 SDK Configuration System (`/v1/configs`)

The SDK configuration system allows remote control of SDK behavior:

#### API Endpoints:
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/v1/configs` | List all config versions |
| GET | `/v1/configs/active` | Get current active config |
| GET | `/v1/configs/{version}` | Get specific version |
| POST | `/v1/configs` | Create new version (auto-activates) |
| GET | `/v1/configs/rules-features` | Get available rules/features enums |
| GET | `/v1/configs/scopes-sdks` | Get available scopes/SDKs enums |

#### Configuration Schema (`PulseConfig`):

```java
PulseConfig {
  version: Long                    // Auto-assigned
  description: String              // User-provided description
  
  sampling: SamplingConfig {
    default: { sessionSampleRate: Double }  // 0.0 to 1.0
    rules: [SamplingRule]          // Conditional sampling
    criticalEventPolicies: { alwaysSend: [CriticalPolicyRule] }
    criticalSessionPolicies: { alwaysSend: [CriticalPolicyRule] }
  }
  
  signals: SignalsConfig {
    filters: FilterConfig {
      mode: 'blacklist' | 'whitelist'
      values: [EventFilter]
    }
    scheduleDurationMs: Int        // Batch duration
    logsCollectorUrl: String       // Auto-filled by backend
    metricCollectorUrl: String     // Auto-filled by backend
    spanCollectorUrl: String       // Auto-filled by backend
    
    // Attribute manipulation:
    attributesToDrop: [EventFilter]    // Remove attributes matching conditions
    attributesToAdd: [AttributeToAdd]  // Enrich with additional attributes
  }
  
  interaction: InteractionConfig {
    collectorUrl: String           // Auto-filled by backend
    configUrl: String              // Auto-filled by backend
    beforeInitQueueSize: Int
  }
  
  features: [FeatureConfig]        // Feature toggles per SDK
}
```

### 5.3 Available Enums (Backend-Defined)

#### SDKs (`Sdk.java`):
- `android_java` - Native Android (Kotlin/Java)
- `android_rn` - Android React Native
- `ios_native` - Native iOS (planned)
- `ios_rn` - iOS React Native (planned)

#### Scopes (`Scope.java`):
- `logs` - Log records
- `traces` - Span/trace data
- `metrics` - Metric data
- `baggage` - Context propagation

#### Sampling Rules (`rules.java`):
Device parameter-based sampling rules:
- `os_version` - Operating system version
- `app_version` - Application version
- `country` - Geo country (ISO code from OTel collector)
- `platform` - Device platform
- `state` - Geo state/region
- `device` - Device model
- `network` - Network type

#### Features (`Features.java`):
SDK features that can be enabled/disabled:
- `interaction` - User interaction tracking
- `java_crash` - Java/Kotlin crash reporting
- `java_anr` - ANR detection
- `network_change` - Network state monitoring
- `network_instrumentation` - HTTP request tracking
- `screen_session` - Screen view tracking
- `custom_events` - Custom event API

---

## 6. Database Schema

### 6.1 MySQL (Metadata)

| Table | Purpose |
|-------|---------|
| `interaction` | Critical interaction definitions |
| `pulse_sdk_configs` | SDK configuration versions |
| `alerts` | Alert rule definitions |
| `alert_scope` | Alert subject scopes |
| `alert_evaluation_history` | Alert evaluation results |
| `notification_channels` | Alert notification targets |
| `symbol_files` | Crash symbolication files |

### 6.2 ClickHouse (Analytics)

| Table | Purpose |
|-------|---------|
| `otel_traces` | Span data (interactions, network, etc.) |
| `otel_logs` | Log records (crashes, events) |
| `otel_metrics_gauge` | Metric data points |
| `stack_trace_events` | Processed crash/ANR data |

#### Key Materialized Columns (extracted from attributes):
- `SessionId` - Session identifier
- `AppVersion` - Application build version
- `Platform` - OS name (android/ios)
- `OsVersion` - OS version
- `DeviceModel` - Device model name
- `GeoCountry` - Country ISO code
- `GeoState` - State/region ISO code
- `UserId` - User identifier
- `PulseType` - Event type classification

---

## 7. Frontend Architecture

### 7.1 Tech Stack
- React 18 with TypeScript
- Mantine UI component library
- React Query for data fetching
- CSS Modules for styling

### 7.2 Key Screens

| Screen | Path | Purpose |
|--------|------|---------|
| Home | `/` | Overview dashboard |
| App Vitals | `/app-vitals` | Crash-free rates, stability metrics |
| Critical Interactions | `/interactions` | User journey tracking |
| Screens | `/screens` | Screen performance |
| Network | `/network` | API performance |
| Sessions | `/sessions` | Session timeline/replay |
| Alerts | `/alerts` | Alert management |
| Settings | `/settings` | SDK configuration |

### 7.3 SDK Configuration UI (`/screens/SamplingConfig/`)

Components:
- `ConfigVersionList` - List all config versions
- `ConfigEditor` - Create/view configurations
- `SamplingRulesConfig` - Default rate + conditional rules
- `FiltersConfig` - Event blacklist/whitelist
- `CriticalEventsConfig` - Events that bypass sampling
- `FeatureToggles` - Enable/disable SDK features
- `InfraConfig` - Read-only infrastructure settings

---

## 8. Data Flow

### 8.1 Telemetry Flow

```
1. SDK captures event (crash, interaction, network call)
2. Event is enriched with:
   - Session ID
   - Device attributes (model, OS version)
   - Geo attributes (from collector)
   - User ID (if set)
3. Sampler decides if session is sampled (session-level sampling)
4. Events batched and exported via OTLP/HTTP
5. OTel Collector receives and routes to ClickHouse
6. Backend queries ClickHouse for analytics
7. UI displays aggregated data
```

### 8.2 Configuration Flow

```
1. User creates/updates config in UI
2. Backend validates and saves to MySQL
3. Config cached in memory (Caffeine cache)
4. Config optionally saved to object store
5. SDK fetches config on initialization
6. SDK applies sampling/filtering rules
```

---

## 9. Key Concepts

### 9.1 Session-Based Sampling
Unlike event-level sampling, Pulse samples entire sessions. Once a session is selected (based on sample rate), ALL events in that session are recorded. This ensures:
- Complete user journeys for sampled sessions
- Consistent debugging context
- No partial session data

### 9.2 Critical Events/Sessions
Events or sessions marked as "critical" bypass all sampling rules and are always sent. Use for:
- Crashes and ANRs
- Business-critical events (payments, errors)
- Events that must never be missed

### 9.3 Conditional Sampling Rules
Apply different sample rates based on device parameters:
- Sample 100% of beta users (app_version matches "beta")
- Sample 10% in high-traffic countries
- Sample 100% on specific device models for debugging

### 9.4 Event Filtering
- **Blacklist mode**: Block specific events from being sent
- **Whitelist mode**: Only allow specific events (block everything else)

Filters can match on:
- Event name (exact or regex)
- Event properties (key-value with regex matching)
- Scope (logs, traces, metrics)
- SDK platform

---

## 10. Development Notes

### 10.1 Running Locally
```bash
cd deploy
./scripts/quickstart.sh  # Starts all services via docker-compose
```

### 10.2 Key Ports
- Backend API: `8080`
- UI: `3000`
- OTel Collector: `4318` (OTLP/HTTP)
- ClickHouse: `8123` (HTTP), `9000` (native)
- MySQL: `3306`

### 10.3 Backend Configuration
Environment variables or `src/main/resources/config/` files.

### 10.4 Frontend API Integration
- All API calls use `makeRequest` helper
- React Query hooks in `/src/hooks/`
- Constants in `/src/constants/Constants.ts`

---

## 11. TODOs / Future Considerations

1. **iOS SDK**: Currently planned, not implemented
2. **SDK Configuration Polling**: SDK should periodically poll for config updates
3. **Config Versioning UI**: Show diff between versions
4. **Feature Configuration**: Uses `sessionSampleRate` (0 = off, 1 = on) instead of `enabled` boolean. UI shows as on/off toggle but saves as 0 or 1
5. **Attribute Enrichment**: `attributesToAdd` and `attributesToDrop` in signals config are now fully supported in the UI

---

*Last Updated: December 2024*

