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
- [Database Schema](#-database-schema)
- [Configuration](#-configuration)
- [Testing](#testing)

## 🌟 Overview

Pulse Server is a reactive, high-performance backend service built with Vert.x for the Pulse Observability Platform. It provides RESTful APIs for managing users, alerts, critical interactions, and analytics queries. The service handles real-time data processing and integrates with multiple databases for optimal performance.

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

**Description:** Queries telemetry data with custom aggregations and filters for analytics. Builds ClickHouse SQL query from request parameters.

#### Request Fields

**dataType** (required): Specifies which ClickHouse table to query. Accepts `TRACES`, `METRICS`, `LOGS`, or `EXCEPTIONS`. Determines source table: `otel_traces`, `otel_metrics`, `otel_logs`, or `stack_trace_events`.

**timeRange** (required): ISO-8601 UTC timestamps for filtering data. `start` and `end` fields define the time window for query execution.

**select** (required): Array of functions to calculate metrics. Each item contains:
- `function`: Predefined metric function (see available functions below)
- `param`: Optional parameters specific to function (e.g., field name for COL, expression for CUSTOM, bucket size for TIME_BUCKET)
- `alias`: Optional custom name for the result column

**filters** (optional): Array of WHERE clause conditions. Each filter contains:
- `field`: Column name to filter on (e.g., "span.name", "page.url")
- `operator`: Comparison operator - `IN` (matches any value in list), `LIKE` (pattern matching), `EQ` (equals), `ADDITIONAL` (raw query)
- `value`: Array of values to match against

**groupBy** (optional): Array of field names or aliases to group results by. Used for aggregation, must match non-aggregated select items.

**orderBy** (optional): Array of sorting specifications. Each contains:
- `field`: Field or alias name to sort by
- `direction`: `ASC` (ascending) or `DESC` (descending)

**limit** (optional): Maximum number of rows to return. Defaults to 100 if not specified.

#### Available Functions

- **APDEX**: Calculates average APDEX score from span attributes, excluding error status codes
- **DURATION_P99**: 99th percentile duration in seconds, excluding errors
- **DURATION_P95**: 95th percentile duration in seconds, excluding errors
- **DURATION_P50**: 50th percentile (median) duration in seconds, excluding errors
- **CRASH**: Count of crash events (device.crash)
- **ANR**: Count of Application Not Responding events (device.anr)
- **FROZEN_FRAME**: Sum of frozen frame counts from span attributes
- **ANALYSED_FRAME**: Sum of analysed frame counts from span attributes
- **UNANALYSED_FRAME**: Sum of unanalysed frame counts from span attributes
- **COL**: Selects a column directly. Requires `param.field` with column name
- **TIME_BUCKET**: Groups timestamps into time buckets. Requires `param.bucket` (e.g., "1d", "1h") and `param.field` (timestamp column)
- **CUSTOM**: Executes custom ClickHouse expression. Requires `param.expression` with SQL expression
- **INTERACTION_SUCCESS_COUNT**: Count of successful interactions (non-error status codes)
- **INTERACTION_ERROR_COUNT**: Count of failed interactions (error status codes)
- **INTERACTION_ERROR_DISTINCT_USERS**: Count of distinct users who encountered errors
- **USER_CATEGORY_EXCELLENT**: Count of users in EXCELLENT category
- **USER_CATEGORY_GOOD**: Count of users in GOOD category
- **USER_CATEGORY_AVERAGE**: Count of users in AVERAGE category
- **USER_CATEGORY_POOR**: Count of users in POOR category
- **NET_0**: Sum of network connection errors (network.0 events)
- **NET_2XX**: Sum of successful HTTP responses (2xx status codes)
- **NET_3XX**: Sum of redirect responses (3xx status codes)
- **NET_4XX**: Sum of client error responses (4xx status codes)
- **NET_5XX**: Sum of server error responses (5xx status codes)
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
        "fields": ["osVersion","duration_p99","apdex","t1","success_count"],
        "rows": [
                  ["1.0_1","0.547","0.68","2025-11-08T08:40:00Z","24"],
                  ["1.0_2","0.566","0.677","2025-11-09T08:40:00Z","23"]
                ]
    },
    "error": null
}
```

## OpenTelemetry Logs Ingestion

### Export Logs

**Description:** Receives OpenTelemetry log data via OTLP (OpenTelemetry Protocol) and processes stack trace events for error grouping and analysis. This endpoint implements the OTLP/HTTP logs exporter specification, accepting protobuf-encoded log data and processing it through the error grouping service for symbolication and storage in ClickHouse.

**Business Logic:** The endpoint receives OpenTelemetry `ExportLogsServiceRequest` containing log records with stack traces. It processes these logs through the `ErrorGroupingService`, which:
- Extracts stack trace events from log records
- Identifies the platform/lane (JavaScript, Java, or NDK) for each stack trace
- Symbolicates stack traces (converts obfuscated/minified code to readable format)
- Groups similar errors together for analysis
- Stores processed events in ClickHouse for analytics and error tracking

The service supports automatic gzip compression/decompression for efficient data transfer. On success, it returns an empty protobuf response. On error, it returns a Google RPC Status message with sanitized error details.

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
The request body must be a protobuf-encoded `ExportLogsServiceRequest` message following the OpenTelemetry Protocol specification. The body can be optionally gzip-compressed if `Content-Encoding: gzip` header is present.

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

## Symbol File Upload

### Upload Mapping/Symbol Files

**Description:** Uploads symbol files (such as ProGuard mapping files, source maps, or other symbolication files) that are used to convert obfuscated or minified stack traces back to readable format. This endpoint accepts multipart form data containing both the file content and metadata describing the file's context (app version, platform, framework type).

**Business Logic:** The endpoint receives symbol/mapping files along with metadata through a multipart form upload. The service:
- Validates that both file content and metadata are provided
- Matches each uploaded file with its corresponding metadata entry by filename
- Stores files in MySQL database with metadata (app version, version code, platform, framework type)
- Uses `ON DUPLICATE KEY UPDATE` to replace existing files for the same app version/platform/framework combination
- These uploaded files are later retrieved during error symbolication process when processing stack traces from logs
- Supports multiple file uploads in a single request, processing them concurrently

The symbol files are essential for the error grouping service to properly symbolicate stack traces, converting obfuscated class names, method names, and line numbers back to their original readable format for better error analysis and debugging.

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
- Files are stored in MySQL and can be retrieved later for symbolication based on app version, platform, and framework type
- Existing files for the same app version/platform/framework combination are automatically replaced

## Critical Interactions
See [Interaction.md](Interaction.md), to know more about Interactions.


All endpoints require user-email in header.
```
user-email: user@example.com
```

### Get Interactions

**Description:** Retrieves paginated list of critical interactions with filtering options. Requires page and size query params, optional interactionName and userEmail for filtering.

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
          "uptimeLowerLimitInMs": 100, // in Ms
          "uptimeMidLimitInMs": 200, // in Ms
          "uptimeUpperLimitInMs": 300, // in Ms
          "thresholdInMs": 60000, // in Ms
          "status": "RUNNING"/"STOPPED"
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
                  "name": "property3",
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

**Description:** Returns complete list of all interactions without pagination. No parameters required, used for dropdown selection or bulk operations.

```http
GET /v1/interactions/all
```

### Get Interaction Details

**Description:** Fetches complete interaction configuration including events, thresholds, and blacklisted events. Requires name path parameter to identify specific interaction.

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
    "uptimeLowerLimitInMs": 100, // in Ms
    "uptimeMidLimitInMs": 200, // in Ms
    "uptimeUpperLimitInMs": 300, // in Ms
    "thresholdInMS": 60000, // in Ms
    "status": "RUNNING"/"STOPPED"
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
            "name": "property3",
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
    ],
    "createdAt": "17874817100,
    "createdBy": "user@example.com",
    "updatedAt": "17874817100",
    "updatedBy": "huser@example.com"
  }
}
```
### Create Interaction

**Description:** Creates new critical interaction definition for monitoring user flows. Requires name, events array, uptime limits, and thresholdInMs fields in request body.

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

**Description:** Modifies existing interaction configuration with new events or thresholds. Requires name path parameter to identify interaction, updated fields in body to change configuration.

```http
PUT /v1/interactions/{name}
Content-Type: application/json
```

### Delete Interaction

**Description:** Removes critical interaction definition from system permanently. Requires name path parameter to specify which interaction to delete.

```http
DELETE /v1/interactions/{name}
```

## Interaction Filter Options

**Description:** Returns available filter options for interaction queries. No parameters required, provides statuses and creator emails for filtering interactions.

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

**Description:** Returns available telemetry filter values for data queries. No parameters required, provides app versions, device models, platforms, and OS versions for filtering.

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
