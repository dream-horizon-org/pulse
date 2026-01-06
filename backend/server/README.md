# Pulse Server - Backend Application

<div align="center">

**High-performance reactive backend built with Java and Vert.x**

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Vert.x](https://img.shields.io/badge/Vert.x-4.5.10-purple.svg)](https://vertx.io/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)

</div>

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Architecture](#-architecture)
- [Features](#-features)
- [Technology Stack](#-technology-stack)
- [Getting Started](#-getting-started)
- [API Documentation](#api-documentation)
    - [Authentication](#authentication)
    - [Metrics](#metrics)
    - [OpenTelemetry Logs Ingestion](#opentelemetry-logs-ingestion)

    - [Symbol File Upload](#symbol-file-upload)
    - [Critical Interactions](#critical-interactions)
    - [Alerts](#alerts)
- [Database Schema](#-database-schema)
- [Configuration](#-configuration)
- [Testing](#testing)

## 🌟 Overview

Pulse Server is a reactive, high-performance backend service built with Vert.x for the Pulse Observability Platform. It
provides RESTful APIs for managing users, alerts, critical interactions, and analytics queries. The service handles
real-time data processing and integrates with multiple databases for optimal performance.

## 🏗️ Architecture

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Pulse Server (Vert.x)                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────┐  ┌───────────────┐  ┌──────────────┐     │
│  │  HTTP Router  │  │  Auth Service │  │ Alert Service│     │
│  └───────┬───────┘  └───────┬───────┘  └──────┬───────┘     │
│          │                  │                 │             │
│  ┌───────▼──────────────────▼─────────────────▼───────-──┐  │
│  │              Service Layer (Business Logic)           │  │
│  └───────┬───────────────────┬──────────────────-────────┘  │
│          │                   │                              │
│  ┌───────▼───────┐  ┌────────▼────────┐                     |
│  │  MySQL Client │  │ClickHouse Client│                     |
│  └───────┬───────┘  └────────┬────────┘                     |
└──────────┼───────────────────┼────────────────----──────────┘
           │                   │                   
     ┌─────▼─────┐        ┌────▼───────┐       
     │   MySQL   │        │ClickHouse  │       
     │ (Metadata)│        │(Analytics) │       
     └───────────┘        └────────────┘       
```

## ✨ Features

### Core Features

- **🔐 Authentication & Authorization**
    - Google OAuth 2.0 integration
    - JWT token-based authentication
    - Session management


- **📊 Critical Interactions**
    - Track key user interactions
    - Interaction analytics
    - Performance monitoring
    - Custom event tracking

### Technical Features

- **Reactive Architecture**: Built on Vert.x event loop for high concurrency
- **Multi-Database**: MySQL for metadata, ClickHouse for analytics
- **Connection Pooling**: Optimized database connections
- **Async Processing**: Non-blocking I/O operations
- **Error Handling**: Comprehensive error handling and recovery
- **Metrics**: Built-in metrics with Dropwizard
- **Health Checks**: Endpoint for service health monitoring
- **Dependency Injection**: Google Guice for DI

## 🛠️ Technology Stack

### Core Technologies

- **Java**: 17 (LTS)
- **Vert.x**: 4.5.10 - Reactive framework
- **Maven**: Build and dependency management

### Frameworks & Libraries

- **vertx-rest**: 1.1.0 - REST API framework
- **vertx-rx-java3**: Reactive extensions
- **Google Guice**: 5.1.0 - Dependency injection
- **Lombok**: 1.18.30 - Boilerplate reduction

### Databases

- **MySQL**: 8.0 - Relational database for metadata
- **ClickHouse**: Time-series database for analytics
- **R2DBC**: Reactive database connectivity

### Authentication & Security

- **Google OAuth**: OAuth 2.0 integration
- **JJWT**: 0.12.5 - JWT token generation and validation
- **Google API Client**: 2.2.0 - ID token verification

### Monitoring & Metrics

- **Dropwizard Metrics**: 4.0.2 - Application metrics
- **StatsD**: Metrics reporting

### Testing

- **JUnit**: 5.10.2 - Unit testing
- **Mockito**: 4.11.0 - Mocking framework
- **AssertJ**: 3.24.2 - Fluent assertions
- **Vert.x JUnit5**: Integration testing

### Integration

<!-- 
- **Slack API**: 1.42.0 - Slack notifications
- **AWS SDK**: 2.20.8 - AWS services integration -->

- **OpenTelemetry**: Protocol buffer support

## 🚀 Getting Started

### Prerequisites

- **Java**: 17 or higher
- **Maven**: 3.8+
- **MySQL**: 8.0+
- **ClickHouse**: Latest version
- **Docker**: (optional) for containerized deployment

### Installation

1. **Clone the repository**

```bash
cd pulse/backend/server
```

2. **Install dependencies**

```bash
mvn clean install
```

3. **Configure environment**

Set environment variables or update configuration files in `src/main/resources/config/`.

4. **Build the project**

```bash
mvn clean package
```

The output JAR will be in `target/pulse-server/pulse-server.jar`.

### Running Locally

```bash
# specify the env variables and run the application
java -jar target/pulse-server/pulse-server.jar
```

The server will start on `http://localhost:8080`.

### Running with Docker

```bash
# Build Docker image
docker build -t pulse-server:latest .

# Run container
docker run -p 8080:8080 \
  -e CONFIG_SERVICE_APPLICATION_MYSQL_HOST=mysql \
  -e CONFIG_SERVICE_APPLICATION_CLICKHOUSE_HOST=clickhouse \
  pulse-server:latest
```

### Verify Installation

```bash
# Health check
curl http://localhost:8080/healthcheck

# Should return: {"status":"UP"}
```

# API Documentation

Complete API reference for Pulse backend services.

## Base URL

```
http://localhost:8080
```

Most endpoints require authentication using JWT tokens. Include the token in the Authorization header:

```
Authorization: Bearer {token}
```

## Authentication

### Social Authenticate

**Description:** Authenticates user via Google OAuth, validates ID token and returns access/refresh tokens.

```http
POST /v1/auth/social/authenticate
Content-Type: application/json

{
  "responseType": "token",
  "grantType": "authorization_code",
  "identifier": "google-id-token",
  "idProvider": "google",
  "resources": ["pulse"]
}
```

Response:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEyMzQ1NiJ9...",
  "refreshToken": "refresh-token-12345",
  "tokenType": "Bearer"
}
```

### Verify Auth Token

**Description:** Verifies if the provided authorization token is valid.

```http
GET /v1/auth/token/verify
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Response:

```json
{
  "isAuthTokenValid": true
}
```

### Refresh Token

**Description:** Gets a new access token using a refresh token.

```http
POST /v1/auth/token/refresh
Content-Type: application/json

{
  "refreshToken": "refresh-token-12345"
}
```

Response:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "refreshToken": "refresh-token-12345",
  "tokenType": "Bearer"
}
```

## Metrics

### Performance Metric Distribution

**Description:** Queries telemetry data with custom aggregations and filters for analytics. Builds ClickHouse SQL query
from request parameters.

#### Request Fields

**dataType** (required): Specifies which ClickHouse table to query. Accepts `TRACES`, `METRICS`, `LOGS`, or`EXCEPTIONS`.
Determines source table: `otel_traces`, `otel_metrics`, `otel_logs`, or `stack_trace_events`.

**timeRange** (required): ISO-8601 UTC timestamps for filtering data. `start` and `end` fields define the time window
for query execution.

**select** (required): Array of functions to calculate metrics. Each item contains:

- `function`: Predefined metric function (see available functions below)
- `param`: Optional parameters specific to function (e.g., field name for COL, expression for CUSTOM, bucket size for
  TIME_BUCKET)
- `alias`: Optional custom name for the result column

**filters** (optional): Array of WHERE clause conditions. Each filter contains:

- `field`: Column name to filter on (e.g., "span.name", "page.url")
- `operator`: Comparison operator - `IN` (matches any value in list), `LIKE` (pattern matching), `EQ` (equals),
  `ADDITIONAL` (raw query)
- `value`: Array of values to match against

**groupBy** (optional): Array of field names or aliases to group results by. Used for aggregation, must match
non-aggregated select items.

**orderBy** (optional): Array of sorting specifications. Each contains:

- `field`: Field or alias name to sort by
- `direction`: `ASC` (ascending) or `DESC` (descending)

**limit** (optional): Maximum number of rows to return. Defaults to 100 if not specified.

#### Available Functions

**Duration & Performance Metrics:**

- **APDEX**: Calculates average APDEX score from span attributes, excluding error status codes (value range: [0,1])
- **DURATION_P99**: 99th percentile duration in milliseconds, excluding errors
- **DURATION_P95**: 95th percentile duration in milliseconds, excluding errors
- **DURATION_P50**: 50th percentile duration in milliseconds, excluding errors

**Frame Metrics:**

- **FROZEN_FRAME**: Sum of frozen frame counts from span attributes
- **ANALYSED_FRAME**: Sum of analysed frame counts from span attributes
- **UNANALYSED_FRAME**: Sum of unanalysed frame counts from span attributes
- **FROZEN_FRAME_RATE**: Percentage of frozen frames to total frames (value range: 0-100%)

**Error & Crash Metrics:**

- **CRASH**: Count of crash events (device.crash)
- **ANR**: Count of Application Not Responding events (device.anr)
- **CRASH_RATE**: Percentage of crash events to total events (value range: 0-100%)
- **ANR_RATE**: Percentage of ANR events to total events (value range: 0-100%)
- **ERROR_RATE**: Percentage of error status codes to total interactions (value range: 0-100%)

**Interaction Metrics:**

- **INTERACTION_SUCCESS_COUNT**: Count of successful interactions (non-error status codes)
- **INTERACTION_ERROR_COUNT**: Count of failed interactions (error status codes)
- **INTERACTION_ERROR_DISTINCT_USERS**: Count of distinct users who encountered errors

**User Category Metrics:**

- **USER_CATEGORY_EXCELLENT**: Count of users in EXCELLENT category
- **USER_CATEGORY_GOOD**: Count of users in GOOD category
- **USER_CATEGORY_AVERAGE**: Count of users in AVERAGE category
- **USER_CATEGORY_POOR**: Count of users in POOR category
- **EXCELLENT_USER_RATE**: Percentage of excellent users to total users (value range: 0-100%)
- **GOOD_USER_RATE**: Percentage of good users to total users (value range: 0-100%)
- **AVERAGE_USER_RATE**: Percentage of average users to total users (value range: 0-100%)
- **POOR_USER_RATE**: Percentage of poor users to total users (value range: 0-100%)

**Network Metrics:**

- **NET_0**: Sum of network connection errors (network.0 events)
- **NET_2XX**: Sum of successful HTTP responses (2xx status codes)
- **NET_3XX**: Sum of redirect responses (3xx status codes)
- **NET_4XX**: Sum of client error responses (4xx status codes)
- **NET_5XX**: Sum of server error responses (5xx status codes)
- **NET_4XX_RATE**: Percentage of 4xx responses to total network requests (value range: 0-100%)
- **NET_5XX_RATE**: Percentage of 5xx responses to total network requests (value range: 0-100%)
- **NET_COUNT**: Total count of network requests
- **DURATION_P99**: 99th percentile duration in milliseconds for network requests, excluding errors
- **DURATION_P95**: 95th percentile duration in milliseconds for network requests, excluding errors
- **DURATION_P50**: 50th percentile duration in milliseconds for network requests, excluding errors
- **ERROR_RATE**: Percentage of error status codes to total network requests (value range: 0-100%)

**Screen Metrics:**

- **SCREEN_DAILY_USERS**: Count of distinct daily users for screen events
- **ERROR_RATE**: Percentage of error status codes to total screen events (value range: 0-100%)
- **SCREEN_TIME**: Average time spent on screen sessions in milliseconds
- **LOAD_TIME**: Average time for screen load events in milliseconds

**App Vitals Metrics (EXCEPTIONS data type):**

- **CRASH_FREE_USERS_PERCENTAGE**: Percentage of users without crash events (value range: 0-100%)
- **CRASH_FREE_SESSIONS_PERCENTAGE**: Percentage of sessions without crash events (value range: 0-100%)
- **CRASH_USERS**: Count of distinct users with crash events (value >= 0)
- **CRASH_SESSIONS**: Count of distinct sessions with crash events (value >= 0)
- **ALL_USERS**: Total count of distinct users (value >= 0)
- **ALL_SESSIONS**: Total count of distinct sessions (value >= 0)
- **ANR_FREE_USERS_PERCENTAGE**: Percentage of users without ANR events (value range: 0-100%)
- **ANR_FREE_SESSIONS_PERCENTAGE**: Percentage of sessions without ANR events (value range: 0-100%)
- **ANR_USERS**: Count of distinct users with ANR events (value >= 0)
- **ANR_SESSIONS**: Count of distinct sessions with ANR events (value >= 0)
- **NON_FATAL_FREE_USERS_PERCENTAGE**: Percentage of users without non-fatal errors (value range: 0-100%)
- **NON_FATAL_FREE_SESSIONS_PERCENTAGE**: Percentage of sessions without non-fatal errors (value range: 0-100%)
- **NON_FATAL_USERS**: Count of distinct users with non-fatal errors (value >= 0)
- **NON_FATAL_SESSIONS**: Count of distinct sessions with non-fatal errors (value >= 0)

**Utility Functions:**

- **COL**: Selects a column directly. Requires `param.field` with column name
- **TIME_BUCKET**: Groups timestamps into time buckets. Requires `param.bucket` (e.g., "1d", "1h") and `param.field` (
  timestamp column)
- **CUSTOM**: Executes custom ClickHouse expression. Requires `param.expression` with SQL expression
- **ARR_TO_STR**: Converts array to comma-separated string. Requires `param.field` with array column name

#### Example Request

```http
POST /v1/interactions/performance-metric/distribution
Content-Type: application/json

{
  "dataType": "TRACES",
  "timeRange": {
    "start": "2025-11-07T08:40:00Z",
    "end": "2025-11-12T14:40:00Z"
  },
  "select": [
    { "function": "COL", "param": {"field": "os.version"}, "alias": "osVersion"},
    { "function": "DURATION_P99", "alias": "duration_p99"},
    { "function": "APDEX"},
    { "function": "TIME_BUCKET", "param": {"bucket": "1d", "field": "Timestamp"}, "alias": "t1"},
    { "function": "CUSTOM", "param": { "expression": "countIf(StatusCode!='Error')"}, "alias": "success_count"}
  ],
  "filters": [
    { "field": "span.name", "operator": "IN", "value": ["page_load"] },
    { "field": "page.url", "operator": "LIKE", "value": ["https%://my-store.com/checkout/%"] }
  ],
  "groupBy": [
    "osVersion"
  ],
  "orderBy": [
    { "field": "t1", "direction": "ASC" },
    { "field": "apdex", "direction": "ASC" }
  ],
  "limit": 1000
}
```

Response:

```json
{
  "status": 200,
  "data": {
    "fields": [
      "osVersion",
      "duration_p99",
      "apdex",
      "t1",
      "success_count"
    ],
    "rows": [
      [
        "1.0_1",
        "0.547",
        "0.68",
        "2025-11-08T08:40:00Z",
        "24"
      ],
      [
        "1.0_2",
        "0.566",
        "0.677",
        "2025-11-09T08:40:00Z",
        "23"
      ]
    ]
  },
  "error": null
}
```

#### Example: App Vitals Crash Metrics

```http
POST /v1/interactions/performance-metric/distribution
Content-Type: application/json

{
  "dataType": "EXCEPTIONS",
  "timeRange": {
    "start": "2025-12-05T00:00:00.000Z",
    "end": "2025-12-12T23:59:59.999Z"
  },
  "select": [
    { "function": "CUSTOM", "param": { "expression": "uniqCombinedIf(UserId, EventName = 'device.crash')" }, "alias": "crash_users" },
    { "function": "CUSTOM", "param": { "expression": "uniqCombinedIf(SessionId, EventName = 'device.crash')" }, "alias": "crash_sessions" },
    { "function": "CUSTOM", "param": { "expression": "uniqCombined(UserId)" }, "alias": "all_users" },
    { "function": "CUSTOM", "param": { "expression": "uniqCombined(SessionId)" }, "alias": "all_sessions" },
    { "function": "CRASH_FREE_USERS_PERCENTAGE", "alias": "crash_free_users_percentage" },
    { "function": "CRASH_FREE_SESSIONS_PERCENTAGE", "alias": "crash_free_sessions_percentage" }
  ]
}
```

Response:

```json
{
  "status": 200,
  "data": {
    "fields": [
      "crash_users",
      "crash_sessions",
      "all_users",
      "all_sessions",
      "crash_free_users_percentage",
      "crash_free_sessions_percentage"
    ],
    "rows": [
      [
        "150",
        "320",
        "10000",
        "25000",
        "0.985",
        "0.9872"
      ]
    ]
  },
  "error": null
}
```

#### Example: App Vitals ANR Metrics

```http
POST /v1/interactions/performance-metric/distribution
Content-Type: application/json

{
  "dataType": "EXCEPTIONS",
  "timeRange": {
    "start": "2025-12-05T00:00:00.000Z",
    "end": "2025-12-12T23:59:59.999Z"
  },
  "select": [
    { "function": "ANR_USERS", "alias": "anr_users" },
    { "function": "ANR_SESSIONS", "alias": "anr_sessions" },
    { "function": "ALL_USERS", "alias": "all_users" },
    { "function": "ALL_SESSIONS", "alias": "all_sessions" },
    { "function": "ANR_FREE_USERS_PERCENTAGE", "alias": "anr_free_users_percentage" },
    { "function": "ANR_FREE_SESSIONS_PERCENTAGE", "alias": "anr_free_sessions_percentage" }
  ]
}
```

Response:

```json
{
  "status": 200,
  "data": {
    "fields": [
      "anr_users",
      "anr_sessions",
      "all_users",
      "all_sessions",
      "anr_free_users_percentage",
      "anr_free_sessions_percentage"
    ],
    "rows": [
      [
        "75",
        "180",
        "10000",
        "25000",
        "0.9925",
        "0.9928"
      ]
    ]
  },
  "error": null
}
```

#### Example: App Vitals Non-Fatal Metrics

```http
POST /v1/interactions/performance-metric/distribution
Content-Type: application/json

{
  "dataType": "EXCEPTIONS",
  "timeRange": {
    "start": "2025-12-05T00:00:00.000Z",
    "end": "2025-12-12T23:59:59.999Z"
  },
  "filters": [
    { "field": "EventName", "operator": "EQ", "value": ["non_fatal"] }
  ],
  "select": [
    { "function": "NON_FATAL_USERS", "alias": "non_fatal_users" },
    { "function": "NON_FATAL_SESSIONS", "alias": "non_fatal_sessions" },
    { "function": "ALL_USERS", "alias": "all_users" },
    { "function": "ALL_SESSIONS", "alias": "all_sessions" },
    { "function": "NON_FATAL_FREE_USERS_PERCENTAGE", "alias": "non_fatal_free_users_percentage" },
    { "function": "NON_FATAL_FREE_SESSIONS_PERCENTAGE", "alias": "non_fatal_free_sessions_percentage" }
  ]
}
```

Response:

```json
{
  "status": 200,
  "data": {
    "fields": [
      "non_fatal_users",
      "non_fatal_sessions",
      "all_users",
      "all_sessions",
      "non_fatal_free_users_percentage",
      "non_fatal_free_sessions_percentage"
    ],
    "rows": [
      [
        "500",
        "1200",
        "10000",
        "25000",
        "0.95",
        "0.952"
      ]
    ]
  },
  "error": null
}
```

## OpenTelemetry Logs Ingestion

### Export Logs

**Description:** Receives OpenTelemetry log data via OTLP (OpenTelemetry Protocol) and processes stack trace events for
error grouping and analysis. This endpoint implements the OTLP/HTTP logs exporter specification, accepting
protobuf-encoded log data and processing it through the error grouping service for symbolication and storage in
ClickHouse.

**Business Logic:** The endpoint receives OpenTelemetry `ExportLogsServiceRequest` containing log records with stack
traces. It processes these logs through the `ErrorGroupingService`, which:

- Extracts stack trace events from log records
- Identifies the platform/lane (JavaScript, Java, or NDK) for each stack trace
- Symbolicates stack traces (converts obfuscated/minified code to readable format)
- Groups similar errors together for analysis
- Stores processed events in ClickHouse for analytics and error tracking

The service supports automatic gzip compression/decompression for efficient data transfer. On success, it returns an
empty protobuf response. On error, it returns a Google RPC Status message with sanitized error details.

```http
POST /v1/logs
Content-Type: application/x-protobuf
Content-Encoding: gzip (optional)
Accept-Encoding: gzip (optional)

[Protobuf-encoded ExportLogsServiceRequest body]
```

**Request Headers:**

- `Content-Type`: Must be `application/x-protobuf`
- `Content-Encoding`: Optional. If set to `gzip`, the request body will be automatically decompressed
- `Accept-Encoding`: Optional. If set to `gzip`, the response will be compressed

**Request Body:**
The request body must be a protobuf-encoded `ExportLogsServiceRequest` message following the OpenTelemetry Protocol
specification. The body can be optionally gzip-compressed if `Content-Encoding: gzip` header is present.

**Success Response:**

```http
HTTP/1.1 200 OK
Content-Type: application/x-protobuf
Content-Encoding: gzip (if Accept-Encoding: gzip was sent)

[Empty protobuf response]
```

**Error Response:**

```http
HTTP/1.1 400 Bad Request
Content-Type: application/x-protobuf
Content-Encoding: gzip (if Accept-Encoding: gzip was sent)

[Google RPC Status protobuf message with error details]
```

**Example Request (using curl):**

```bash
# Without compression
curl -X POST http://localhost:8080/v1/logs \
  -H "Content-Type: application/x-protobuf" \
  --data-binary @logs_request.pb

# With gzip compression
curl -X POST http://localhost:8080/v1/logs \
  -H "Content-Type: application/x-protobuf" \
  -H "Content-Encoding: gzip" \
  -H "Accept-Encoding: gzip" \
  --data-binary @logs_request.pb.gz
```

**Notes:**

- This endpoint follows the OTLP/HTTP specification for log ingestion
- Error messages in responses are sanitized (newlines and carriage returns are replaced with spaces)
- The endpoint processes logs asynchronously and returns a completion stage
- Stack traces are automatically symbolicated based on the detected platform (JS, Java, or NDK)

## Query Service

The query service provides a generic interface for executing SQL queries against various query engines (AWS Athena, BigQuery, etc.) with pagination support. Queries are automatically optimized with partition filters for better performance.

**Base Path:** `/query`

**Authentication:** These endpoints may require authentication depending on your server configuration.

**Note:** The service is designed to be engine-agnostic. Currently, AWS Athena is the default implementation, but the architecture supports plugging in other query engines (e.g., BigQuery, GCP) by implementing the `QueryClient` and `QueryJobDao` interfaces.

### Submit Query

**Description:** Submits a SQL query for execution. The service validates the query, enriches it with timestamp-based partition filters for optimal performance, and executes it. If the query completes within 3 seconds, results are returned immediately. Otherwise, a job ID is returned for status checking and result retrieval.

**Business Logic:** The endpoint:

- Validates SQL query syntax and ensures it's a safe SELECT query
- Automatically enriches queries with partition filters (year, month, day, hour) based on timestamp conditions
- Extracts timestamp from `TIMESTAMP 'YYYY-MM-DD HH:MM:SS'` literals in WHERE clauses if present
- Submits query to the configured query engine and polls for completion (up to 3 seconds)
- Returns results immediately if query completes within 3 seconds
- Returns job ID for asynchronous status checking if query takes longer

```http
POST /query
Content-Type: application/json
```

**Request Body:**

```json
{
  "queryString": "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00' AND \"timestamp\" <= TIMESTAMP '2025-12-23 11:59:59' LIMIT 10",
  "parameters": [],
  "timestamp": "2025-12-23 11:00:00"
}
```

**Request Fields:**

- `queryString` (required): SQL query string. Must be a SELECT query. The query can include
  `TIMESTAMP 'YYYY-MM-DD HH:MM:SS'` literals in WHERE clauses, which will be automatically used to add partition
  filters.
- `parameters` (optional): Query parameters array (currently not used, pass empty array)
- `timestamp` (optional): Timestamp string in format "YYYY-MM-DD HH:MM:SS" or "H:M:S". If provided, partition filters
  will be added based on this timestamp. If not provided but the query contains `TIMESTAMP` literals, those will be used
  instead.

**Success Response (Query completed within 3 seconds):**

```json
{
  "data": {
    "jobId": "e8a98c57-c987-40bd-b1f5-a6f534e371df",
    "status": "COMPLETED",
    "message": "Query completed successfully within 3 seconds",
    "queryExecutionId": "5e7ea4ab-9e26-48f0-9a5c-abb9702d3d1d",
    "resultLocation": "s3://puls-otel-config/5e7ea4ab-9e26-48f0-9a5c-abb9702d3d1d.csv",
    "resultData": [
      {
        "column1": "value1",
        "column2": "value2"
      }
    ],
    "nextToken": "AXzl4c3EaYUFTNQNmBrZsT9jIA...",
    "dataScannedInBytes": 2639,
    "createdAt": 1766738293000,
    "completedAt": 1766738295000
  },
  "error": null
}
```

**Success Response (Query still running after 3 seconds):**

```json
{
  "data": {
    "jobId": "e8a98c57-c987-40bd-b1f5-a6f534e371df",
    "status": "RUNNING",
    "message": "Query submitted successfully. Use GET /athena/job/{jobId} to check status and get results.",
    "queryExecutionId": "5e7ea4ab-9e26-48f0-9a5c-abb9702d3d1d",
    "dataScannedInBytes": null,
    "createdAt": 1766738293000
  },
  "error": null
}
```

**Response Fields:**

- `jobId`: Unique identifier for the query job
- `status`: Job status (COMPLETED, RUNNING, FAILED, CANCELLED)
- `message`: Human-readable status message
- `queryExecutionId`: AWS Athena query execution ID
- `resultLocation`: S3 location where results are stored (if completed)
- `resultData`: Array of result rows (if completed within 3 seconds)
- `nextToken`: Token for pagination (if more results available)
- `dataScannedInBytes`: Amount of data scanned by the query in bytes
- `createdAt`: Job creation timestamp
- `completedAt`: Job completion timestamp (if completed)

**Error Response:**

```json
{
  "data": null,
  "error": {
    "message": "Invalid SQL query: Query must include timestamp filter in WHERE clause (year, month, day, hour)",
    "code": "UNKNOWN_EXCEPTION"
  }
}
```

**Example Request (using curl):**

```bash
curl --location 'http://localhost:8080/query' \
  --header 'Content-Type: application/json' \
  --data '{
    "queryString": "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '\''2025-12-23 11:00:00'\'' AND \"timestamp\" <= TIMESTAMP '\''2025-12-23 11:59:59'\'' LIMIT 10",
    "parameters": []
  }'
```

**Notes:**

- Queries are automatically enriched with partition filters (`year`, `month`, `day`, `hour`) for optimal performance
- If the query contains `TIMESTAMP 'YYYY-MM-DD HH:MM:SS'` literals, those are automatically extracted and used to add
  partition filters
- Queries that complete within 3 seconds return results immediately in the response
- For longer-running queries, use the job ID to check status and retrieve results
- The service includes retry logic to handle cases where results aren't immediately available after query completion
- The service architecture is engine-agnostic and supports multiple query engines through pluggable implementations

### Get Job Status

**Description:** Retrieves the status and results of a query job. If the job is completed, results can be
retrieved with pagination support.

```http
GET /query/job/{jobId}?maxResults=100&nextToken=AXzl4c3EaYUFTNQNmBrZsT9jIA...
```

**Path Parameters:**

- `jobId` (required): The job ID returned from the submit query endpoint

**Query Parameters:**

- `maxResults` (optional): Maximum number of results to return (1-1000). Defaults to 1000 if not specified.
- `nextToken` (optional): Token for pagination. Use the `nextToken` from the previous response to get the next page of
  results.

**Success Response:**

```json
{
  "data": {
    "jobId": "e8a98c57-c987-40bd-b1f5-a6f534e371df",
    "status": "COMPLETED",
    "queryString": "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11 AND \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00' AND \"timestamp\" <= TIMESTAMP '2025-12-23 11:59:59' LIMIT 10",
    "queryExecutionId": "5e7ea4ab-9e26-48f0-9a5c-abb9702d3d1d",
    "resultLocation": "s3://puls-otel-config/5e7ea4ab-9e26-48f0-9a5c-abb9702d3d1d.csv",
    "resultData": [
      {
        "column1": "value1",
        "column2": "value2"
      }
    ],
    "nextToken": "AXzl4c3EaYUFTNQNmBrZsT9jIA...",
    "dataScannedInBytes": 2639,
    "createdAt": 1766738293000,
    "completedAt": 1766738295000
  },
  "error": null
}
```

**Example Request (using curl):**

```bash
curl --location 'http://localhost:8080/query/job/e8a98c57-c987-40bd-b1f5-a6f534e371df?maxResults=100&nextToken=AXzl4c3EaYUFTNQNmBrZsT9jIA...'
```

**Notes:**

- Results are fetched from the query engine API at runtime, not stored in the database
- Pagination is supported using `maxResults` and `nextToken` parameters
- The `nextToken` is URL-encoded and should be passed as-is in subsequent requests
- If the job is still running, the status will be "RUNNING" and `resultData` will be null

## Symbol File Upload

### Upload Mapping/Symbol Files

**Description:** Uploads symbol files (such as ProGuard mapping files, source maps, or other symbolication files) that
are used to convert obfuscated or minified stack traces back to readable format. This endpoint accepts multipart form
data containing both the file content and metadata describing the file's context (app version, platform, framework
type).

**Business Logic:** The endpoint receives symbol/mapping files along with metadata through a multipart form upload. The
service:

- Validates that both file content and metadata are provided
- Matches each uploaded file with its corresponding metadata entry by filename
- Stores files in MySQL database with metadata (app version, version code, platform, framework type)
- Uses `ON DUPLICATE KEY UPDATE` to replace existing files for the same app version/platform/framework combination
- These uploaded files are later retrieved during error symbolication process when processing stack traces from logs
- Supports multiple file uploads in a single request, processing them concurrently

The symbol files are essential for the error grouping service to properly symbolicate stack traces, converting
obfuscated class names, method names, and line numbers back to their original readable format for better error analysis
and debugging.

```http
POST /v1/symbolicate/file/upload
Content-Type: multipart/form-data
```

**Request Body (Multipart Form Data):**

The request must contain two parts:

1. **`fileContent`** (one or more file parts): The actual symbol/mapping file(s) to upload
    - Each file part should have a `Content-Disposition` header with a `filename` parameter
    - Multiple files can be uploaded in a single request

2. **`metadata`** (JSON string): An array of metadata objects describing each file
   ```json
   [
     {
       "type": "proguard",
       "appVersion": "1.2.3",
       "fileName": "mapping.txt",
       "platform": "Android",
       "versionCode": "123"
     },
     {
       "type": "sourcemap",
       "appVersion": "1.2.3",
       "fileName": "bundle.js.map",
       "platform": "iOS",
       "versionCode": "123"
     }
   ]
   ```

**Metadata Fields:**

- `type` (required): Framework/symbol file type (e.g., "proguard", "sourcemap", "retrace")
- `appVersion` (required): Application version string
- `fileName` (required): Name of the file (must match the filename in the file part)
- `platform` (required): Platform identifier (e.g., "Android", "iOS")
- `versionCode` (required): Version code/build number

**Success Response:**

```json
{
  "status": 200,
  "data": true,
  "error": null
}
```

**Error Response:**

```json
{
  "status": 200,
  "data": false,
  "error": null
}
```

Note: The endpoint returns `false` in the data field if:

- Metadata is empty or invalid
- File parts are missing
- Filename doesn't match any metadata entry
- Upload fails for any file

**Example Request (using curl):**

```bash
curl -X POST http://localhost:8080/v1/symbolicate/file/upload \
  -F "fileContent=@mapping.txt" \
  -F "fileContent=@bundle.js.map" \
  -F 'metadata=[{"type":"proguard","appVersion":"1.2.3","fileName":"mapping.txt","platform":"Android","versionCode":"123"},{"type":"sourcemap","appVersion":"1.2.3","fileName":"bundle.js.map","platform":"iOS","versionCode":"123"}]'
```

**Notes:**

- File names in the `fileContent` parts must exactly match the `fileName` field in the metadata array
- Files with unknown filenames or without matching metadata are skipped
- If any file upload fails, the entire operation returns `false`
- Files are stored in MySQL and can be retrieved later for symbolication based on app version, platform, and framework
  type
- Existing files for the same app version/platform/framework combination are automatically replaced

## Critical Interactions

See [Interaction.md](Interaction.md), to know more about Interactions.

All endpoints require user-email in header.

```
user-email: user@example.com
```

### Get Interactions

**Description:** Retrieves paginated list of critical interactions with filtering options. Requires page and size query
params, optional interactionName and userEmail for filtering.

```http
GET /v1/interactions?page=0&size=10&interactionName=ContestJoinSuccess&userEmail=user@example.com
```

Response:

```json
{
  data: {
    interactions: [
      {
        "interactionName": "ContestJoinSuccess",
        "id": 123456,
        "description": "Some description",
        "uptimeLowerLimitInMs": 100,
        // in Ms
        "uptimeMidLimitInMs": 200,
        // in Ms
        "uptimeUpperLimitInMs": 300,
        // in Ms
        "thresholdInMs": 60000,
        // in Ms
        "status": "RUNNING"
        /
        "STOPPED"
        "events": [
          {
            "name": "event1",
            "props": [
              {
                "name": "property1",
                "value": "value1",
                "operator": "EQUALS"
              },
              {
                "name": "property2",
                "value": "value2",
                "operator": "CONTAINS"
                // default EQUALS
              }
            ],
            "isBlacklisted": true/false/null
          },
          {
            "name": "event2",
            "props": [
              {
                "name": "property3",
                "value": "value3",
                "operator": "NOTEQUALS"
                // default EQUALS
              }
            ],
            "isBlacklisted": true/false/null
          }
        ],
        "globalBlacklistedEvents": [
          {
            "name": "blacklisted_event",
            "props": [
              {
                "name": "property4",
                "value": "value4",
                "operator": "NOTCONTAINS"
              }
            ],
            "isBlacklisted": true/true
          }
        ],
        "createdAt": 17874817100,
        "createdBy": "user@example.com",
        "updatedAt": "17874817100",
        "updatedBy": "user@example.com"
      }
    ],
    "totalInteractions": 100
  }
}
```

### Get All Interactions

**Description:** Returns complete list of all interactions without pagination. No parameters required, used for dropdown
selection or bulk operations.

```http
GET /v1/interactions/all
```

### Get Interaction Details

**Description:** Fetches complete interaction configuration including events, thresholds, and blacklisted events.
Requires name path parameter to identify specific interaction.

Request:

```http
GET /v1/interactions/{name}
```

Response:

```json
{
  data: {
    "name": "example_interaction",
    "description": "Some description",
    "id": 123456,
    "uptimeLowerLimitInMs": 100,
    // in Ms
    "uptimeMidLimitInMs": 200,
    // in Ms
    "uptimeUpperLimitInMs": 300,
    // in Ms
    "thresholdInMS": 60000,
    // in Ms
    "status": "RUNNING"
    /
    "STOPPED"
    "events": [
      {
        "name": "event1",
        "props": [
          {
            "name": "property1",
            "value": "value1",
            "operator": "EQUALS"
          },
          {
            "name": "property2",
            "value": "value2",
            "operator": "CONTAINS"
            // default EQUALS
          }
        ],
        "isBlacklisted": true/false/null
      },
      {
        "name": "event2",
        "props": [
          {
            "name": "property3",
            "value": "value3",
            "operator": "NOTEQUALS"
            // default EQUALS
          }
        ],
        "isBlacklisted": true/false/null
      }
    ],
    "globalBlacklistedEvents": [
      {
        "name": "blacklisted_event",
        "props": [
          {
            "name": "property4",
            "value": "value4",
            "operator": "NOTCONTAINS"
          }
        ],
        "isBlacklisted": true/true
      }
    ],
    "createdAt": "17874817100,
    "createdBy": "user@example.com",
    "updatedAt": "17874817100",
    "updatedBy": "huser@example.com"
  }
}
```

### Create Interaction

**Description:** Creates new critical interaction definition for monitoring user flows. Requires name, events array,
uptime limits, and thresholdInMs fields in request body.

Request:

```http
POST /v1/interactions
Content-Type: application/json
{
  "name": "example_interaction",
  "description": "Some description",
  "uptimeLowerLimitInMs": 100, // in Ms
  "uptimeMidLimitInMS": 200, // in Ms
  "uptimeUpperLimitInMs": 300, // in Ms
  "thresholdInMs": 60000, // in Ms
  "events": [
    {
      "name": "event1",
      "props": [
        {
          "name": "property1",
          "value": "value1",
          "operator": "EQUALS"
        },
        {
          "name": "property2",
          "value": "value2",
          "operator": "CONTAINS" // default EQUALS
        }
      ],
      "isBlacklisted": true/false/null
    },
    {
      "name": "event2",
      "props": [
        {
          "vame": "property3",
          "value": "value3",
          "operator": "NOTEQUALS" // default EQUALS
        }
      ],
      "isBlacklisted": true/false/null
    }
  ],
  "globalBlacklistedEvents": [
    {
      "name": "blacklisted_event",
      "props": [
        {
          "name": "property4",
          "value": "value4",
          "operator": "NOTCONTAINS"
        }
      ],
      "isBlacklisted": true/true
    }
  ]
}
```

Response:

```json
{
  data: {
    "id": 123
  }
}
```

### Update Interaction

**Description:** Modifies existing interaction configuration with new events or thresholds. Requires name path parameter
to identify interaction, updated fields in body to change configuration.

```http
PUT /v1/interactions/{name}
Content-Type: application/json
```

### Delete Interaction

**Description:** Removes critical interaction definition from system permanently. Requires name path parameter to
specify which interaction to delete.

```http
DELETE /v1/interactions/{name}
```

## Interaction Filter Options

**Description:** Returns available filter options for interaction queries. No parameters required, provides statuses and
creator emails for filtering interactions.

```http
GET /v1/interactions/filter-options
```

Response:

```json
{
  "data": {
    "statuses": [
      "RUNNING",
      "STOPPED",
      "DELETED"
    ],
    "createdBy": [
      "user1@example.com",
      "user2@example.com",
      "user3@example.com"
    ]
  }
}
```

## Telemetry Filter Options

**Description:** Returns available telemetry filter values for data queries. No parameters required, provides app
versions, device models, platforms, and OS versions for filtering.

```http
GET /v1/interactions/telemetry-filters
```

Response:

```json
{
  "data": {
    "appVersionCodes": [
      "1.0.0",
      "1.1.0",
      "1.2.0",
      "2.0.0"
    ],
    "deviceModels": [
      "iPhone 14",
      "iPhone 14 Pro",
      "OnePlus 11",
      "Samsung Galaxy S23"
    ],
    "networkProviders": [
      "Airtel",
      "Jio",
      "Vodafone"
    ],
    "platforms": [
      "Android",
      "iOS"
    ],
    "osVersions": [
      "Android 13",
      "Android 14",
      "iOS 16.5",
      "iOS 17.0"
    ],
    "states": [
      "IN-DL",
      "IN-KA",
      "IN-MH",
      "IN-TN"
    ]
  }
}
```

## Alerts

Complete API reference for managing alerts, alert scopes, metrics, evaluation history, and alert-related configurations.

All alert endpoints require authentication using JWT tokens. Include the token in the Authorization header:

```
Authorization: Bearer {token}
```

### Get Alerts (Paginated)

**Description:** Retrieves a paginated list of alerts with optional filtering by name, scope, created_by, or updated_by.

```http
GET /v1/alert?name=alert_name&scope=interaction&created_by=user@example.com&limit=10&offset=0
```

**Query Parameters:**

- `name` (optional): Filter by alert name
- `scope` (optional): Filter by scope (interaction, screen, app_vitals, network_api)
- `created_by` (optional): Filter by creator email
- `updated_by` (optional): Filter by last updater email
- `limit` (optional, default: 10): Number of results per page
- `offset` (optional, default: 0): Pagination offset

**Response:**

```json
{
  "status": 200,
  "data": {
    "alerts": [
      {
        "id": 1,
        "name": "High Error Rate Alert",
        "description": "Alert for high error rates",
        "scope": "interaction",
        "evaluation_period": 1000,
        "evaluation_interval": 60,
        "severity_id": 4,
        "notification_channel_id": 1,
        "created_by": "user@example.com",
        "updated_by": "user@example.com",
        "created_at": "2025-01-01T00:00:00Z",
        "updated_at": "2025-01-01T00:00:00Z"
      }
    ],
    "total": 100
  },
  "error": null
}
```

### Get All Alerts

**Description:** Returns complete list of all alerts without pagination. Used for dropdown selection or bulk operations.

```http
GET /v1/alerts
```

**Response:**

```json
{
  "status": 200,
  "data": {
    "alerts": [
      {
        "id": 1,
        "name": "High Error Rate Alert",
        "description": "Alert for high error rates",
        "scope": "interaction"
      }
    ]
  },
  "error": null
}
```

### Get Alert Details

**Description:** Fetches complete alert configuration including scopes, conditions, and evaluation settings.

```http
GET /v1/alert/{id}
```

**Path Parameters:**

- `id` (required): Alert ID

**Response:**

```json
{
  "status": 200,
  "data": {
    "id": 1,
    "name": "High Error Rate Alert",
    "description": "Alert for high error rates",
    "scope": {
      "name": "interaction",
      "conditions": {
        "interactionNames": [
          "page_load",
          "checkout"
        ]
      }
    },
    "dimension_filters": "AppVersion = '1.0.0'",
    "condition_expression": "A AND B",
    "alerts": [
      {
        "alias": "A",
        "metric": "ERROR_RATE",
        "metric_operator": "GREATER_THAN",
        "threshold": {
          "interaction": 0.05
        }
      }
    ],
    "evaluation_period": 1000,
    "evaluation_interval": 60,
    "severity_id": 4,
    "notification_channel_id": 1,
    "created_by": "user@example.com",
    "updated_by": "user@example.com",
    "created_at": "2025-01-01T00:00:00Z",
    "updated_at": "2025-01-01T00:00:00Z"
  },
  "error": null
}
```

### Create Alert

**Description:** Creates a new alert with specified scope, conditions, metrics, and evaluation settings.

```http
POST /v1/alert
Content-Type: application/json

{
  "name": "High Error Rate Alert",
  "description": "Alert for high error rates",
  "scope": {
    "name": "interaction",
    "conditions": {
      "interactionNames": ["page_load", "checkout"]
    }
  },
  "dimension_filters": "AppVersion = '1.0.0'",
  "condition_expression": "A AND B",
  "alerts": [
    {
      "alias": "A",
      "metric": "ERROR_RATE",
      "metric_operator": "GREATER_THAN",
      "threshold": {
        "interaction": 0.05
      }
    }
  ],
  "evaluation_period": 1000,
  "evaluation_interval": 60,
  "severity_id": 4,
  "notification_channel_id": 1,
  "created_by": "user@example.com",
  "updated_by": "user@example.com"
}
```

**Request Fields:**

- `name` (required): Alert name
- `description` (required): Alert description
- `scope` (required): Alert scope object with name and conditions
- `dimension_filters` (optional): Additional dimension filters as SQL-like string
- `condition_expression` (required): Boolean expression combining alert conditions (e.g., "A AND B")
- `alerts` (required): Array of alert conditions with alias, metric, operator, and threshold
- `evaluation_period` (required): Evaluation period in milliseconds
- `evaluation_interval` (required): Evaluation interval in seconds
- `severity_id` (required): Severity level ID
- `notification_channel_id` (required): Notification channel ID
- `created_by` (required): Creator email
- `updated_by` (required): Updater email

**Response:**

```json
{
  "status": 200,
  "data": {
    "id": 1
  },
  "error": null
}
```

### Update Alert

**Description:** Updates an existing alert configuration. Same request structure as Create Alert, but requires alert ID
in the request body.

```http
PUT /v1/alert
Content-Type: application/json

{
  "id": 1,
  "name": "Updated Alert Name",
  "description": "Updated description",
  "scope": {
    "name": "interaction",
    "conditions": {
      "interactionNames": ["page_load"]
    }
  },
  "condition_expression": "A",
  "alerts": [
    {
      "alias": "A",
      "metric": "ERROR_RATE",
      "metric_operator": "GREATER_THAN",
      "threshold": {
        "interaction": 0.1
      }
    }
  ],
  "evaluation_period": 2000,
  "evaluation_interval": 120,
  "severity_id": 4,
  "notification_channel_id": 1,
  "updated_by": "user@example.com"
}
```

**Response:**

```json
{
  "status": 200,
  "data": {
    "id": 1
  },
  "error": null
}
```

### Delete Alert

**Description:** Deletes an alert permanently from the system.

```http
DELETE /v1/alert/{id}
```

**Path Parameters:**

- `id` (required): Alert ID

**Response:**

```json
{
  "status": 200,
  "data": true,
  "error": null
}
```

### Get Alert Scopes

**Description:** Returns available alert scopes with their IDs, names, and labels.

```http
GET /v1/alert/scopes
```

**Response:**

```json
{
  "status": 200,
  "data": {
    "scopes": [
      {
        "id": 1,
        "name": "interaction",
        "label": "Interactions"
      },
      {
        "id": 2,
        "name": "network_api",
        "label": "Network APIs"
      },
      {
        "id": 3,
        "name": "screen",
        "label": "Screen"
      },
      {
        "id": 4,
        "name": "app_vitals",
        "label": "App Vitals"
      }
    ]
  },
  "error": null
}
```

### Get Alert Metrics

**Description:** Returns available metrics for a specific scope.

```http
GET /v1/alert/metrics?scope=interaction
```

**Query Parameters:**

- `scope` (required): Scope name (interaction, screen, app_vitals, network_api)

**Response:**

```json
{
  "status": 200,
  "data": {
    "scope": "interaction",
    "metrics": [
      {
        "id": 1,
        "name": "APDEX",
        "label": "APDEX value [0,1]"
      },
      {
        "id": 2,
        "name": "CRASH",
        "label": "CRASH value >= 0"
      },
      {
        "id": 3,
        "name": "ERROR_RATE",
        "label": "ERROR_RATE value [0,1]"
      }
    ]
  },
  "error": null
}
```

### Get Alert Evaluation History

**Description:** Returns evaluation history for all scopes associated with an alert, grouped by scope.

```http
GET /v1/alert/{id}/evaluationHistory
```

**Path Parameters:**

- `id` (required): Alert ID

**Response:**

```json
{
  "status": 200,
  "data": [
    {
      "scope_id": 1,
      "scope_name": "interaction",
      "evaluation_history": [
        {
          "evaluation_id": 100,
          "evaluation_result": "TRIGGERED",
          "state": "ALERT",
          "evaluated_at": "2025-01-01T12:00:00Z"
        },
        {
          "evaluation_id": 99,
          "evaluation_result": "NORMAL",
          "state": "NORMAL",
          "evaluated_at": "2025-01-01T11:00:00Z"
        }
      ]
    }
  ],
  "error": null
}
```

### Evaluate and Trigger Alert

**Description:** Manually triggers evaluation of an alert. The evaluation is performed asynchronously, and this endpoint
returns immediately with the alert ID. The actual evaluation results are processed in the background and stored in the
evaluation history.

```http
GET /v1/alert/evaluateAndTriggerAlert?alertId=1
```

**Query Parameters:**

- `alertId` (required): Alert ID to evaluate

**Response:**

```json
{
  "status": 200,
  "data": {
    "alert_id": "1"
  },
  "error": null
}
```

**Note:** To view the evaluation results, use the [Get Alert Evaluation History](#get-alert-evaluation-history) endpoint
after the evaluation completes.

### Snooze Alert

**Description:** Snoozes an alert for a specified time period. During the snooze period, the alert will not trigger
notifications.

```http
POST /v1/alert/{id}/snooze
Content-Type: application/json
Authorization: Bearer {token}

{
  "snoozeFrom": 1765751323047,
  "snoozeUntil": 1765837723047
}
```

**Path Parameters:**

- `id` (required): Alert ID

**Request Body:**

- `snoozeFrom` (required): Start time in milliseconds (epoch timestamp)
- `snoozeUntil` (required): End time in milliseconds (epoch timestamp)

**Response:**

```json
{
  "status": 200,
  "data": {
    "isSnoozed": true,
    "snoozedFrom": 1765751323047,
    "snoozedUntil": 1765837723047
  },
  "error": null
}
```

### Delete Snooze

**Description:** Removes the snooze period from an alert, allowing it to trigger notifications again.

```http
DELETE /v1/alert/{id}/snooze
Authorization: Bearer {token}
```

**Path Parameters:**

- `id` (required): Alert ID

**Response:**

```json
{
  "status": 200,
  "data": {
    "message": "success"
  },
  "error": null
}
```

### Get Alert Filters

**Description:** Returns available filter options for alert queries, including creators, updaters, and alert states.

```http
GET /v1/alert/filters
```

**Response:**

```json
{
  "status": 200,
  "data": {
    "job_id": null,
    "created_by": [
      "user1@example.com",
      "user2@example.com"
    ],
    "updated_by": [
      "user1@example.com",
      "user2@example.com"
    ],
    "current_state": [
      "NORMAL",
      "FIRING",
      "NO_DATA"
    ]
  },
  "error": null
}
```

**Response Fields:**

- `job_id`: List of job IDs (currently not populated, returns null)
- `created_by`: List of unique creator email addresses
- `updated_by`: List of unique updater email addresses
- `current_state`: List of unique alert states (NORMAL, FIRING, NO_DATA, etc.)

### Get Alert Severity List

**Description:** Returns list of available alert severity levels.

```http
GET /v1/alert/severity
```

**Response:**

```json
{
  "status": 200,
  "data": [
    {
      "severity_id": 1,
      "name": 1,
      "description": "Critical: Production outage or severe degradation with significant user impact. Requires immediate action and incident management."
    },
    {
      "severity_id": 2,
      "name": 2,
      "description": "Warning: Degraded performance, elevated errors, or risk of user impact. Should be investigated soon but is not a full outage."
    },
    {
      "severity_id": 3,
      "name": 3,
      "description": "Info: Informational or low-risk condition. No immediate action required; useful for visibility, trend analysis, or validation of changes."
    }
  ],
  "error": null
}
```

**Response Fields:**

- `severity_id`: Unique identifier for the severity level
- `name`: Severity level number (1, 2, 3, etc.)
- `description`: Detailed description of the severity level

### Create Alert Severity

**Description:** Creates a new alert severity level.

```http
POST /v1/alert/severity
Content-Type: application/json

{
  "name": 4,
  "description": "Critical: Production outage or severe degradation with significant user impact. Requires immediate action and incident management."
}
```

**Request Body:**

- `name` (required): Severity level number (Integer, e.g., 1, 2, 3, 4)
- `description` (required): Detailed description of the severity level

**Response:**

```json
{
  "status": 200,
  "data": true,
  "error": null
}
```

### Get Alert Notification Channels

**Description:** Returns list of available notification channels for alerts.

```http
GET /v1/alert/notificationChannels
```

**Response:**

```json
{
  "status": 200,
  "data": [
    {
      "notification_channel_id": 1,
      "name": "Incident management",
      "notification_webhook_url": "http://whistlebot.local/declare-incident"
    }
  ],
  "error": null
}
```

**Response Fields:**

- `notification_channel_id`: Unique identifier for the notification channel
- `name`: Name of the notification channel
- `notification_webhook_url`: Webhook URL for sending notifications

### Create Alert Notification Channel

**Description:** Creates a new notification channel for alerts.

```http
POST /v1/alert/notificationChannels
Content-Type: application/json

{
  "name": "PagerDuty",
  "config": "http://whistlebot.local/declare-incident"
}
```

**Request Body:**

- `name` (required): Channel name
- `config` (required): Notification webhook URL (stored as `notification_webhook_url` in the database)

**Response:**

```json
{
  "status": 200,
  "data": true,
  "error": null
}
```

## 🗄️ Database Schema

### ClickHouse Schema

See [../ingestion/clickhouse-otel-schema.sql](../ingestion/clickhouse-otel-schema.sql) for complete schema.

## ⚙️ Configuration

### Application Configuration

Configuration is done via environment variables with the prefix `CONFIG_SERVICE_APPLICATION_*`.

**MySQL Configuration**

```bash
CONFIG_SERVICE_APPLICATION_MYSQL_HOST=localhost
CONFIG_SERVICE_APPLICATION_MYSQL_PORT=3306
CONFIG_SERVICE_APPLICATION_MYSQL_DATABASE=pulse_db
CONFIG_SERVICE_APPLICATION_MYSQL_USER=pulse_user
CONFIG_SERVICE_APPLICATION_MYSQL_PASSWORD=pulse_password
```

**ClickHouse Configuration**

```bash
CONFIG_SERVICE_APPLICATION_CLICKHOUSE_HOST=localhost
CONFIG_SERVICE_APPLICATION_CLICKHOUSE_PORT=8123
CONFIG_SERVICE_APPLICATION_CLICKHOUSE_DATABASE=otel
CONFIG_SERVICE_APPLICATION_CLICKHOUSE_USER=default
CONFIG_SERVICE_APPLICATION_CLICKHOUSE_PASSWORD=
```

**Server Configuration**

```bash
CONFIG_SERVICE_APPLICATION_SERVER_PORT=8080
CONFIG_SERVICE_APPLICATION_SERVER_HOST=0.0.0.0
```

**Authentication Configuration**

```bash
VAULT_SERVICE_GOOGLE_CLIENT_ID=your-google-client-id
VAULT_SERVICE_JWT_SECRET=your-jwt-secret
```

**Query Engine Configuration**

The query service supports multiple query engines through pluggable implementations. Currently, AWS Athena is the default implementation.

**Athena Configuration:**

Configuration is done via `src/main/resources/conf/athena-default.conf` or environment-specific config files:

```hocon
athena {
  athenaRegion = "ap-south-1"
  database = "pulse_athena_db"
  outputLocation = "s3://puls-otel-config/"
}
```

**AWS Credentials:**

The application uses AWS SDK's default credential provider chain, which automatically looks for credentials in the
following order:

1. Environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`)
2. Java system properties
3. Web identity token from AWS STS
4. Shared credentials file (`~/.aws/credentials`)
5. EC2 instance profile credentials
6. ECS container credentials
7. Lambda execution environment credentials

**Note:** Credentials are not configured in the config file. Use environment variables, IAM roles, or AWS credential
files for authentication.

**Adding Support for Other Query Engines:**

To add support for a new query engine (e.g., BigQuery, GCP):

1. Implement the `QueryClient` interface with engine-specific logic
2. Implement the `QueryJobDao` interface (or reuse existing implementation if compatible)
3. Create a Guice module that binds `QueryClient` and `QueryJobDao` to your implementations
4. The `QueryService` implementation will automatically work with your new engine

The service architecture is designed to be engine-agnostic, making it easy to switch between or support multiple query engines simultaneously.

### Running Tests

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# Run integration tests
mvn verify
```

### Code Coverage

Code coverage reports are generated with JaCoCo:

```bash
mvn clean test jacoco:report
```

## 📄 License

Part of the Pulse Observability Platform. See [LICENSE](../../LICENSE) for details.

---

**Built with ❤️ by the Pulse Backend Team**
