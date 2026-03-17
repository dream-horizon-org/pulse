# Pulse Platform Architecture

## Table of Contents

1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Component Details](#component-details)
4. [Data Flow](#data-flow)
5. [API Architecture](#api-architecture)
6. [Database Schema](#database-schema)
7. [Deployment Architecture](#deployment-architecture)
8. [Security Architecture](#security-architecture)
9. [Scalability & Performance](#scalability--performance)

---

## Overview

Pulse is a digital experience platform built on OpenTelemetry standards that provides real-time monitoring, distributed tracing, and analytics for mobile and web applications. The platform combines behavioral, technical, and business insights to drive better user experiences.

### Key Principles

- **OpenTelemetry Native**: Built on industry-standard OpenTelemetry protocols
- **Reactive Architecture**: Event-driven, non-blocking I/O using Vert.x
- **Microservices**: Modular, independently deployable services
- **Scalable Data Pipeline**: High-throughput ingestion with Kafka and ClickHouse
- **Multi-Platform Support**: Native SDKs for Android and React Native

---

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Applications                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Android Apps │  │ React Native │  │  Web Apps    │          │
│  │   (Kotlin)   │  │  (TypeScript)│  │  (Optional)  │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                  │                 │                  │
│         └──────────────────┼─────────────────┘                 │
│                            │                                    │
│         ┌──────────────────▼──────────────────┐                │
│         │   Pulse Android/RN SDKs              │                │
│         │   (OpenTelemetry Instrumentation)    │                │
│         │   - Traces, Metrics, Logs           │                │
│         │   - Custom Events (separate endpoint)│                │
│         └──────┬───────────────┬───────────────┘                │
└────────────────┼───────────────┼────────────────────────────────┘
                 │               │
        OTLP     │               │ OTLP (Custom Events)
    (gRPC/HTTP)  │               │ (HTTP to Vector)
                 │               │
┌────────────────────────────▼────────────────────────────────────┐
│                    Data Ingestion Layer                          │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │         OpenTelemetry Collector 1                         │ │
│  │  - Receives OTLP data (traces, logs, metrics)            │ │
│  │  - Validates and transforms                              │ │
│  │  - Routes to Kafka topics:                               │ │
│  │    * Traces → pulse.traces                               │ │
│  │    * Metrics → pulse.metrics                             │ │
│  │    * ANR/Crash logs → pulse.logs.anr_crash               │ │
│  │    * Other logs → pulse.logs.other                       │ │
│  └──────────────────┬─────────────────────────────────────────┘ │
│                     │                                           │
│                     │ Kafka Topics                               │
│                     │  - pulse.traces                           │
│                     │  - pulse.metrics                          │
│                     │  - pulse.logs.anr_crash                   │
│                     │  - pulse.logs.other                      │
│                     │                                           │
│  ┌──────────────────▼─────────────────────────────────────────┐ │
│  │         OpenTelemetry Collector 2                         │ │
│  │  - Consumes from Kafka                                    │ │
│  │  - Processes and enriches                                 │ │
│  │  - Writes to ClickHouse                                   │ │
│  └──────────────────┬─────────────────────────────────────────┘ │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │         Vector (Log Collector)                            │ │
│  │  - Receives custom events (OTLP logs)                    │ │
│  │  - Separate endpoint from OTEL Collector                 │ │
│  │  - Processes and buffers                                 │ │
│  │  - Writes directly to S3                                 │ │
│  └──────────────────┬─────────────────────────────────────────┘ │
└─────────────────────┼───────────────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        │             │               │
┌───────▼──────┐ ┌───▼────┐ ┌────────▼────────┐ ┌───────┐
│  ClickHouse  │ │  Kafka │ │      MySQL       │ │  S3   │
│  (Analytics) │ │ (Queue)│ │   (Metadata)    │ │(Custom│
│              │ │        │ │                  │ │Events)│
└───────┬──────┘ └────────┘ └────────┬────────┘ └───────┘
        │                            │
        │                            │
┌───────▼────────────────────────────▼────────┐
│          Pulse Server (Backend)              │
│  ┌────────────────────────────────────────┐ │
│  │  REST API Layer (Vert.x)              │ │
│  │  - Authentication                     │ │
│  │  - Interactions Management            │ │
│  │  - Alerts Management                  │ │
│  │  - Query Service                     │ │
│  │  - Session Service                   │ │
│  └──────────────────┬───────────────────┘ │
│                     │                     │
│  ┌──────────────────▼───────────────────┐ │
│  │  Service Layer                       │ │
│  │  - AuthService                      │ │
│  │  - InteractionService               │ │
│  │  - AlertEvaluationService           │ │
│  │  - QueryService                     │ │
│  │  - SessionService                   │ │
│  └──────────────────┬───────────────────┘ │
│                     │                     │
│  ┌──────────────────▼───────────────────┐ │
│  │  Data Access Layer                   │ │
│  │  - MysqlClient                      │ │
│  │  - ClickhouseClient                 │ │
│  │  - AthenaClient                     │ │
│  │  - S3BucketClient                    │ │
│  └──────────────────────────────────────┘ │
└─────────────────────┬───────────────────────┘
                      │
┌─────────────────────▼───────────────────────┐
│          Pulse UI (Frontend)                │
│  ┌────────────────────────────────────────┐ │
│  │  React 18 + TypeScript                │ │
│  │  - Dashboard                           │ │
│  │  - Analytics                          │ │
│  │  - Alert Management                   │ │
│  │  - Session Replay                     │ │
│  └────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│     Pulse Alerts Cron Service               │
│  - Scheduled alert evaluation               │
│  - Webhook notifications                    │
└─────────────────────────────────────────────┘
```

---

## Component Details

### 1. Backend Server (`backend/server/`)

**Technology Stack:**
- **Language**: Java 17
- **Framework**: Vert.x 4.5.10 (Reactive)
- **Build Tool**: Maven
- **Dependency Injection**: Google Guice
- **HTTP Server**: Vert.x Web

**Architecture Pattern:**
- Event-driven, reactive architecture
- Non-blocking I/O throughout
- Verticle-based deployment (multiple instances for scalability)

**Key Components:**

#### Main Application (`MainApplication.java`)
- Entry point extending Vert.x `Launcher`
- Configures Vert.x options (event loop pool, worker pool)
- Initializes Google Guice modules
- Handles graceful shutdown

#### Verticles:
- **MainVerticle**: Initializes database connections, deploys other verticles
- **RestVerticle**: HTTP server, CORS handling, route registration
- **AnrCrashLogConsumerVerticle**: Kafka consumer for ANR/crash logs (optional)

#### Modules (Google Guice):
- **MainModule**: Core bindings
- **ConfigModule**: Configuration management
- **InteractionModule**: Critical interactions business logic
- **QueryEngineModule**: Query engine abstraction (Athena/GCP)
- **UploadInteractionDetailModule**: Interaction detail uploads
- **ValidationModule**: Request validation

#### Services:
- **AuthService**: Google OAuth 2.0, JWT token management
- **InteractionService**: CRUD operations for critical interactions
- **AlertEvaluationService**: Alert evaluation and triggering
- **QueryService**: SQL query execution (Athena/BigQuery)
- **SessionService**: Session retrieval and management

#### Data Clients:
- **MysqlClient**: MySQL operations (read/write)
- **ClickhouseReadClient** / **ClickhouseWriteClient**: ClickHouse analytics queries
- **AthenaClient**: AWS Athena query execution (queries S3 for custom events)
- **S3BucketClient**: S3 operations (config storage, custom events storage)
- **CloudFrontClient**: CloudFront integration

---

### 2. Frontend UI (`pulse-ui/`)

**Technology Stack:**
- **Framework**: React 18
- **Language**: TypeScript
- **UI Library**: Mantine UI v7
- **State Management**: Zustand, React Query
- **Build Tool**: Webpack
- **Routing**: React Router v6

**Architecture Pattern:**
- Component-based architecture
- Server state via React Query
- Client state via Zustand
- Custom hooks for business logic

**Key Components:**

#### Application Structure:
```
src/
├── App.tsx                 # Root component, routing
├── components/             # Reusable UI components
├── screens/                # Page-level components
├── hooks/                  # Custom React hooks
├── helpers/                # Utility functions
├── clients/                # API clients
├── stores/                 # Zustand stores
└── constants/              # Constants and routes
```

#### Main Screens:
- **Home**: Dashboard overview
- **User Engagement**: User analytics
- **Critical Interactions**: Interaction management
- **App Vitals**: Performance metrics
- **Session Timeline**: Session replay
- **Alerts**: Alert management
- **Universal Querying**: Custom SQL queries
- **Network APIs**: Network monitoring
- **Screens**: Screen analytics

#### State Management:
- **React Query**: Server state, caching, synchronization
- **Zustand**: Client state (filters, UI state)
- **Context API**: Theme, authentication

---

### 3. Android SDK (`pulse-android-otel/`)

**Technology Stack:**
- **Language**: Kotlin
- **Base**: OpenTelemetry Android SDK
- **Build Tool**: Gradle
- **Min SDK**: Android API 21+

**Architecture:**
- Modular instrumentation library
- Auto-instrumentation for common Android components
- Manual instrumentation API

**Key Modules:**
- **Core**: Base SDK functionality
- **Instrumentation**: Auto-instrumentation modules
  - Activity lifecycle
  - Fragment lifecycle
  - Network (OkHttp, HttpURLConnection)
  - ANR detection
  - Crash reporting
  - Slow rendering
  - User interactions
- **Services**: Background services
- **Session**: Session tracking
- **Custom Events**: Manual event tracking API
  - `trackEvent(eventName, attributes)` - Track custom business events
  - Events sent as OTLP logs with `pulse.type="custom_event"`
  - **Routed to Vector collector** (separate from OTEL Collector)
  - Configurable via feature flags (`custom_events`)
  - Endpoint configured via `customEventCollectorUrl`

---

### 4. React Native SDK (`pulse-react-native-otel/`)

**Technology Stack:**
- **Language**: TypeScript/JavaScript
- **Platform**: iOS & Android
- **Build Tool**: React Native Builder Bob

**Architecture:**
- Cross-platform SDK
- Native modules for platform-specific features
- JavaScript bridge for React Native integration

**Key Features:**
- Custom event tracking via `trackEvent()` API
- Events sent as OTLP logs to **separate Vector endpoint** (not OTEL Collector)
- Feature flag support (`custom_events`)
- Custom events routed to `customEventCollectorUrl` configuration

---

### 5. Vector Log Collector (Custom Events)

**Technology Stack:**
- **Tool**: Vector.dev (log collection and routing)
- **Deployment**: AWS EC2 instances (via Terraform)
- **Purpose**: Dedicated log collector for custom events

**Architecture:**
- Receives OTLP logs via HTTP endpoint
- Separate from OTEL Collector pipeline
- Processes and buffers custom events
- Writes directly to S3 bucket

**Key Features:**
- High-throughput log ingestion
- Automatic batching and buffering
- Converts logs to Parquet format before writing to S3
- Direct S3 integration
- Scalable via AWS Auto Scaling Group
- Load balanced via Network Load Balancer

**Configuration:**
- Endpoint configured via `customEventCollectorUrl`
- SDK routes custom events (`pulse.type="custom_event"`) to Vector
- Vector writes to S3 in Parquet format with partitioning by date/time
- Parquet format provides efficient compression and columnar access for analytics

---

### 6. Alerting Service (`backend/pulse-alerts-cron/`)

**Technology Stack:**
- **Language**: Java 17
- **Framework**: Vert.x
- **Purpose**: Scheduled alert evaluation

**Functionality:**
- Cron-based alert evaluation
- Webhook notifications
- Integration with Pulse Server

---

## Data Flow

### 1. Telemetry Data Ingestion Flow (Traces, Metrics, Logs)

```
Mobile App
    │
    │ (OTLP gRPC/HTTP)
    │  - Traces
    │  - Metrics
    │  - Logs (ANR, Crash, Other Logs)
    │  Note: Custom events use separate pipeline
    ▼
OTEL Collector 1
    │
    │ (Kafka Topics)
    │  - pulse.traces
    │  - pulse.metrics
    │  - pulse.logs.anr_crash
    │  - pulse.logs.other
    ▼
OTEL Collector 2
    │
    │ (ClickHouse Native Protocol)
    ▼
ClickHouse (Analytics Database)
    │
    ├─► otel_traces (traces)
    ├─► otel_metrics_gauge (metrics)
    └─► otel_logs (logs: ANR, crashes, other logs)
```

### 1.1. Custom Events Data Ingestion Flow

```
Mobile App (Android/React Native)
    │
    │ SDK tracks custom event
    │ trackEvent("event_name", { attributes })
    │
    │ SDK routes custom events to separate endpoint
    │ (OTLP Log with pulse.type="custom_event")
    │
    │ (OTLP HTTP to customEventCollectorUrl)
    ▼
Vector (Log Collector)
    │
    │ Receives custom events via OTLP
    │ Processes and buffers logs
    │ Converts to Parquet format
    │
    │ (Direct S3 Upload - Parquet format)
    ▼
S3 Bucket
    │
    │ Custom events stored as:
    │ - Parquet files (columnar format)
    │ - Partitioned by date/time
    │ - Event name in attributes
    │ - Custom attributes preserved
    │ - Timestamp
    │ - User/session context
    │ - Optimized for analytics queries
    │
    │ Note: Custom events are NOT stored in ClickHouse
    │       They are stored in S3 (Parquet) for later processing/querying
```

### 2. Custom Events Query Flow

```
Frontend UI / API Client
    │
    │ (REST API - Query Service)
    │ POST /query
    │ Query S3 data (via Athena or direct S3 query)
    │ WHERE event_type = 'custom_event'
    ▼
Pulse Server (Query Service)
    │
    │ (Query S3 via Athena or S3 Select)
    │ OR
    │ (Direct S3 access for batch processing)
    ▼
S3 Bucket
    │
    │ Returns custom events from stored Parquet files:
    │ - Event name
    │ - Attributes
    │ - Timestamp
    │ - User/session context
    │
    │ Note: Query engine (Athena/GCP) reads Parquet files from S3
    │       Parquet format enables efficient columnar queries
    ▼
Frontend UI (Display Results)
```

### 3. User Interaction Flow

```
Frontend UI
    │
    │ (REST API)
    ▼
Pulse Server
    │
    ├─► MySQL (Metadata)
    └─► ClickHouse (Query Results)
```

### 4. Alert Evaluation Flow

```
Pulse Alerts Cron
    │
    │ (Scheduled Evaluation)
    ▼
Pulse Server (Query Service)
    │
    │ (Query ClickHouse/Athena)
    ▼
Alert Evaluation Service
    │
    │ (Webhook/Notification)
    ▼
External Systems
```

---

## API Architecture

### REST API Design

**Base URL**: `http://localhost:8080`

### Authentication Endpoints (`/v1/auth`)

- `POST /v1/auth/social/authenticate` - Google OAuth authentication
- `GET /v1/auth/token/verify` - Verify JWT token
- `POST /v1/auth/token/refresh` - Refresh access token

### Interaction Endpoints (`/v1/interactions`)

- `GET /v1/interactions` - List all interactions
- `POST /v1/interactions` - Create interaction
- `PUT /v1/interactions/:id` - Update interaction
- `DELETE /v1/interactions/:id` - Delete interaction
- `GET /v1/interactions/all-active-interactions` - Get active interactions

### Log Ingestion (`/v1/logs`)

- `POST /v1/logs` - OTLP log ingestion (protobuf)
  - Accepts logs (ANR, crashes, other logs)
  - **Note**: Custom events are NOT ingested through this endpoint
  - Custom events are sent directly to Vector collector endpoint
  - Routes to appropriate Kafka topics based on log type

### Query Service (`/query`)

- `POST /query` - Submit SQL query
- `GET /query/:jobId` - Get query job status
- `GET /query/:jobId/results` - Get query results

### Session Service (`/api/v1/sessions`)

- `POST /api/v1/sessions` - Get session data

### Alert Endpoints (`/v1/alerts`)

- `GET /v1/alerts` - List alerts
- `POST /v1/alerts` - Create alert
- `GET /v1/alerts/:id` - Get alert details
- `PUT /v1/alerts/:id` - Update alert
- `DELETE /v1/alerts/:id` - Delete alert

---

## Database Schema

### MySQL (Metadata Database)

**Purpose**: Store application metadata, user data, configurations

**Key Tables:**
- `users` - User accounts
- `alerts` - Alert configurations
- `interactions` - Critical interaction definitions
- `interaction_details` - Interaction detail configurations
- `alert_scopes` - Alert scope definitions
- `groups` - User groups
- `sessions` - Session metadata

### ClickHouse (Analytics Database)

**Purpose**: High-performance analytics queries on telemetry data

**Key Tables:**
- `otel_traces` - Distributed traces
- `otel_logs` - Application logs (ANR, crashes, other logs)
- `otel_metrics_gauge` - Performance metrics

**Custom Events Storage:**
- **Custom events are NOT stored in ClickHouse**
- Custom events are stored in **S3** via Vector log collector
- Events are stored in **Parquet format** (columnar storage)
- Partitioned by date/time for efficient querying
- Event name stored in log attributes
- Custom attributes preserved in log body/attributes
- Parquet format optimized for analytics queries (compression, columnar access)
- Queryable via AWS Athena (supports Parquet natively) or S3 Select

**Schema**: Defined in `backend/ingestion/clickhouse-otel-schema.sql`

---

## Deployment Architecture

### Docker Compose Services

```
┌─────────────────────────────────────────────────────────┐
│                    Docker Network                        │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐│
│  │  MySQL   │  │ClickHouse│  │  Kafka   │  │   S3     ││
│  │  :3307   │  │  :8123   │  │  :9094   │  │(Custom   ││
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  │Events)   ││
│       │            │              │        └────┬─────┘│
│  ┌────▼────────────▼──────────────▼─────┐      │      │
│  │     OTEL Collector 1                 │      │      │
│  │     (:4317, :4318)                   │      │      │
│  └────┬─────────────────────────────────┘      │      │
│       │                                        │      │
│  ┌────▼─────────────────────────────────┐      │      │
│  │     OTEL Collector 2                │      │      │
│  └────┬─────────────────────────────────┘      │      │
│       │                                        │      │
│  ┌────▼────────────────────────────────────────▼─────┐│
│  │     Vector (Log Collector)                        ││
│  │     (:4318) - Custom Events Only                  ││
│  │     Direct to S3                                  ││
│  └───────────────────────────────────────────────────┘│
│       │                                              │
│  ┌────▼──────────────┐  ┌──────────────────────┐  │
│  │  Pulse Server     │  │  Pulse Alerts Cron    │  │
│  │  (:8080)          │  │  (:4000)              │  │
│  └────┬──────────────┘  └──────────────────────┘  │
│       │                                              │
│  ┌────▼──────────────────────────────────────────┐  │
│  │     Pulse UI                                   │  │
│  │     (:3000)                                    │  │
│  └────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────┘
```

### Service Dependencies

1. **MySQL** → All services depend on MySQL for metadata
2. **ClickHouse** → Pulse Server, OTEL Collector 2
3. **Kafka** → OTEL Collectors
4. **S3** → Vector (custom events storage)
5. **Vector** → Receives custom events from SDKs, writes to S3
6. **Pulse Server** → Pulse UI, Pulse Alerts Cron

### Health Checks

All services implement health check endpoints:
- Pulse Server: `GET /healthcheck`
- Pulse Alerts Cron: `GET /healthcheck`
- Pulse UI: `GET /healthcheck.txt`
- OTEL Collector: `GET :13133/` (health check port)

---

## Security Architecture

### Authentication Flow

```
User
  │
  │ (Google OAuth)
  ▼
Frontend
  │
  │ (ID Token)
  ▼
Pulse Server (AuthService)
  │
  │ (Verify with Google)
  ▼
JWT Token Generation
  │
  │ (Access Token + Refresh Token)
  ▼
Frontend (Stored in Cookies)
```

### Security Features

1. **JWT Authentication**: Access tokens and refresh tokens
2. **Google OAuth 2.0**: Social authentication
3. **CORS**: Configured CORS headers
4. **Token Refresh**: Automatic token refresh mechanism
5. **Authorization**: Role-based access control (planned)

### Data Security

- Database credentials via environment variables
- S3 access via AWS credentials
- HTTPS/TLS in production (configured via reverse proxy)

---

## Scalability & Performance

### Horizontal Scaling

**Pulse Server:**
- Multiple Vert.x instances (based on CPU cores)
- Stateless design enables easy horizontal scaling
- Load balancer required for multiple instances

**OTEL Collectors:**
- Can run multiple instances
- Kafka provides load distribution

**Frontend:**
- Static files served via Nginx
- CDN integration (CloudFront) for production

### Performance Optimizations

1. **Reactive Architecture**: Non-blocking I/O throughout
2. **Connection Pooling**: Database connection pools
3. **Caching**: React Query caching on frontend
4. **ClickHouse**: Optimized for analytical queries
5. **Kafka**: High-throughput message queue

### Monitoring & Observability

- Health check endpoints on all services
- OpenTelemetry instrumentation (self-monitoring)
- Log aggregation via OTEL Collector
- Metrics collection (Prometheus-compatible)

---

## Technology Decisions

### Why Vert.x?

- **Non-blocking I/O**: Handles high concurrency
- **Reactive**: Event-driven architecture
- **Lightweight**: Lower resource consumption than traditional frameworks
- **Polyglot**: Can integrate with other JVM languages

### Why ClickHouse?

- **Analytical Queries**: Optimized for OLAP workloads
- **High Performance**: Columnar storage, compression
- **Scalability**: Horizontal scaling support
- **OpenTelemetry Support**: Native OTLP ingestion

### Why Kafka?

- **High Throughput**: Handles millions of events per second
- **Durability**: Persistent message storage
- **Decoupling**: Separates producers and consumers
- **Scalability**: Distributed, horizontally scalable

### Why React + TypeScript?

- **Type Safety**: Catch errors at compile time
- **Developer Experience**: Excellent tooling
- **Ecosystem**: Rich library ecosystem
- **Performance**: Virtual DOM, code splitting

---

## Future Enhancements

### Planned Features

1. **Real-time Dashboards**: WebSocket-based real-time updates
2. **Advanced Analytics**: ML-based anomaly detection
3. **Multi-tenancy**: Support for multiple organizations
4. **API Rate Limiting**: Protect backend from abuse
5. **GraphQL API**: Alternative to REST API
6. **iOS Native SDK**: Native iOS instrumentation

### Infrastructure Improvements

1. **Kubernetes Deployment**: Container orchestration
2. **Service Mesh**: Istio/Linkerd for microservices
3. **Monitoring Stack**: Prometheus + Grafana
4. **Log Aggregation**: ELK stack or Loki
5. **CI/CD Pipeline**: Automated testing and deployment

---

## References

- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Vert.x Documentation](https://vertx.io/docs/)
- [ClickHouse Documentation](https://clickhouse.com/docs)
- [React Documentation](https://react.dev/)
- [Project README](../README.md)

---

**Last Updated**: February 2026  
**Version**: 1.0
