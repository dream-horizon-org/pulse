# Pulse Alerts Cron - Scheduled Alert Evaluation Service

<div align="center">

**Cron-based alert evaluation scheduler built with Java and Vert.x**

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
- [Configuration](#-configuration)
- [How It Works](#-how-it-works)

## 🌟 Overview

Pulse Alerts Cron is a dedicated service that manages scheduled alert evaluations for the Pulse Observability Platform. It provides a cron-like scheduling system that periodically triggers alert evaluations by calling the Pulse Server's evaluation endpoints. The service groups alerts by their evaluation intervals for efficient execution and includes automatic retry logic for failed evaluations.

## 🏗️ Architecture

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              Pulse Alerts Cron Service (Vert.x)              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────┐  ┌───────────────┐  ┌──────────────┐     │
│  │  REST API     │  │ Cron Manager  │  │Alert Service │     │
│  │  (Port 4000)  │  │               │  │              │     │
│  └───────┬───────┘  └───────┬───────┘  └──────┬───────┘     │
│          │                  │                 │             │
│  ┌───────▼──────────────────▼─────────────────▼───────┐     │
│  │         Timer-based Task Execution                  │     │
│  │  (Groups alerts by evaluation interval)             │     │
│  └───────┬───────────────────┬────────────────────────┘     │
│          │                   │                              │
│  ┌───────▼───────┐  ┌────────▼────────┐                     │
│  │  WebClient    │  │   MySQL Client  │                     │
│  └───────┬───────┘  └────────┬────────┘                     │
└──────────┼───────────────────┼──────────────────────────────┘
           │                   │                   
     ┌─────▼─────┐        ┌────▼───────┐       
     │Pulse      │        │   MySQL    │       
     │Server     │        │ (Health)   │       
     │(8080)     │        └────────────┘       
     └───────────┘                              
```

## ✨ Features

### Core Features

- **⏰ Scheduled Alert Evaluation**: Automatically triggers alert evaluations at configured intervals
- **📊 Interval-based Grouping**: Efficiently groups alerts by evaluation interval to minimize timer overhead
- **🔄 Automatic Retry Logic**: Retries failed evaluations with exponential backoff (up to 3 attempts)
- **🌐 REST API**: Provides endpoints to manage cron tasks (add, update, delete)
- **🚀 Auto-initialization**: Automatically loads and schedules all active alerts from Pulse Server on startup

### Technical Features

- **Reactive Architecture**: Built on Vert.x event loop for high concurrency
- **Timer Management**: Uses Vert.x periodic timers for efficient scheduling
- **Concurrent Task Execution**: Thread-safe task management with `ConcurrentHashMap`
- **Error Handling**: Comprehensive error handling with retry mechanisms
- **Health Checks**: MySQL connection health monitoring
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
- **Jackson**: JSON processing

### Dependencies

- **Google Guava**: 33.0.0-jre
- **Netty**: 4.1.125.Final (security patched)

## 🚀 Getting Started

### Prerequisites

- **Java**: 17 or higher
- **Maven**: 3.8+
- **MySQL**: 8.0+ (for health checks)
- **Pulse Server**: Must be running and accessible

### Installation

1. **Navigate to the project directory**

```bash
cd backend/pulse-alerts-cron
```

2. **Install dependencies**

```bash
mvn clean install
```

3. **Build the project**

```bash
mvn clean package
```

The output JAR will be in `target/pulse-alerts-cron/pulse-alerts-cron.jar`.

### Running Locally

```bash
# Set environment variables
export CONFIG_SERVICE_APPLICATION_PULSESERVERURL=http://localhost:8080

# Run the application
java -jar target/pulse-alerts-cron/pulse-alerts-cron.jar
```

The service will start on `http://localhost:4000`.

### Running with Docker

```bash
# Build Docker image
docker build -t pulse-alerts-cron:latest .

# Run container
docker run -p 4000:4000 \
  -e CONFIG_SERVICE_APPLICATION_PULSESERVERURL=http://pulse-server:8080 \
  pulse-alerts-cron:latest
```

### Verify Installation

```bash
# Health check (if health endpoint exists)
curl http://localhost:4000/healthcheck

# Check if service is running
curl http://localhost:4000/cron
```

## API Documentation

Complete API reference for Pulse Alerts Cron service.

### Base URL

```
http://localhost:4000
```

## Cron Management

### Add Cron Task

**Description:** Adds a new scheduled task for alert evaluation. The task will be executed at the specified interval.

```http
POST /cron
Content-Type: application/json

{
  "id": 1,
  "url": "http://localhost:8080/v1/alert/evaluateAndTriggerAlert?alertId=1",
  "interval": 60
}
```

**Request Body:**
- `id` (required): Alert ID
- `url` (required): Full URL to the alert evaluation endpoint
- `interval` (required): Evaluation interval in seconds

**Response:**
```json
{
  "status": 200,
  "data": {
    "status": "success"
  },
  "error": null
}
```

**Error Response:**
```json
{
  "status": 200,
  "data": {
    "status": "failure",
    "failureReason": "Error message"
  },
  "error": null
}
```

### Update Cron Task

**Description:** Updates an existing cron task with a new URL and/or interval. The old task is removed and a new one is created.

```http
PUT /cron
Content-Type: application/json

{
  "id": 1,
  "url": "http://localhost:8080/v1/alert/evaluateAndTriggerAlert?alertId=1",
  "newInterval": 120,
  "oldInterval": 60
}
```

**Request Body:**
- `id` (required): Alert ID
- `url` (required): New evaluation URL
- `newInterval` (required): New evaluation interval in seconds
- `oldInterval` (required): Previous evaluation interval in seconds

**Response:**
```json
{
  "status": 200,
  "data": {
    "status": "success"
  },
  "error": null
}
```

### Delete Cron Task

**Description:** Removes a scheduled cron task. The timer for the interval will be cancelled if no other tasks exist for that interval.

```http
DELETE /cron
Content-Type: application/json

{
  "id": 1,
  "interval": 60
}
```

**Request Body:**
- `id` (required): Alert ID to remove
- `interval` (required): Evaluation interval in seconds

**Response:**
```json
{
  "status": 200,
  "data": {
    "status": "success"
  },
  "error": null
}
```

## ⚙️ Configuration

### Application Configuration

Configuration is done via environment variables with the prefix `CONFIG_SERVICE_APPLICATION_*`.

**Pulse Server URL**

```bash
CONFIG_SERVICE_APPLICATION_PULSESERVERURL=http://localhost:8080
```

**Shutdown Grace Period**

```bash
CONFIG_SERVICE_APPLICATION_SHUTDOWNGRACEPERIOD=5
```

### MySQL Configuration (for health checks)

```bash
CONFIG_SERVICE_APPLICATION_MYSQL_HOST=localhost
CONFIG_SERVICE_APPLICATION_MYSQL_PORT=3306
CONFIG_SERVICE_APPLICATION_MYSQL_DATABASE=pulse_db
CONFIG_SERVICE_APPLICATION_MYSQL_USER=pulse_user
CONFIG_SERVICE_APPLICATION_MYSQL_PASSWORD=pulse_password
```

## 🔧 How It Works

### Initialization Flow

1. **Service Startup**: On startup, the service connects to MySQL and starts the REST server on port 4000
2. **Alert Loading**: Fetches all active alerts from Pulse Server via `/alerts` endpoint
3. **Task Scheduling**: For each alert, creates a cron task with the alert's evaluation interval
4. **Timer Creation**: Groups tasks by interval and creates a single periodic timer per interval

### Execution Flow

1. **Timer Trigger**: Vert.x periodic timer fires at the configured interval
2. **Task Execution**: All tasks for that interval are executed concurrently
3. **HTTP Request**: Each task makes an HTTP GET request to the alert evaluation URL
4. **Retry Logic**: If the request fails (5xx errors or network issues), retries up to 3 times with exponential backoff
5. **Logging**: All execution attempts, successes, and failures are logged

### Task Grouping

Tasks are grouped by evaluation interval for efficiency:
- **Same Interval**: Multiple alerts with the same interval share a single timer
- **Resource Optimization**: Reduces the number of active timers
- **Automatic Cleanup**: Timers are cancelled when no tasks remain for an interval

### Retry Mechanism

- **Max Attempts**: 3 retry attempts
- **Initial Delay**: 1 second
- **Backoff Strategy**: Exponential backoff (1s, 2s, 4s)
- **Timeout**: 30 seconds per request
- **Client Errors (4xx)**: Not retried (considered permanent failures)
- **Server Errors (5xx)**: Retried with backoff
- **Network Errors**: Retried with backoff

### Example Timeline

For an alert with `interval: 60` seconds:

```
00:00:00 - Timer created for 60s interval
00:01:00 - First evaluation triggered
00:02:00 - Second evaluation triggered
00:03:00 - Third evaluation triggered
...
```

## 📊 Monitoring

### Logs

The service logs important events:

- **Task Addition**: `"cron added: {id} for interval: {interval}"`
- **Task Execution**: `"Executing task: {id}"`
- **Evaluation Success**: `"✅ Evaluation successful for url: {url} | Status: {status} | Duration: {ms}ms | Attempts: {n}"`
- **Evaluation Failure**: `"❌ Evaluation failed for url: {url} | Duration: {ms}ms | Attempts: {n} | Error: {error}"`
- **Retry Attempts**: `"🔄 Retry attempt {n} for url: {url}"`

### Metrics

Monitor the following:
- Number of active cron tasks
- Number of timers (intervals)
- Success/failure rates
- Average evaluation duration
- Retry attempt counts

## 🔒 Security Considerations

- The service makes HTTP requests to Pulse Server - ensure proper network security
- No authentication is implemented in the REST API - consider adding authentication for production
- MySQL credentials should be stored securely (use secrets management)

## 🐛 Troubleshooting

### Service Not Starting

- Check MySQL connection configuration
- Verify Pulse Server URL is accessible
- Check port 4000 is not already in use

### Alerts Not Being Evaluated

- Verify alerts are being added via the REST API
- Check logs for execution errors
- Ensure Pulse Server is accessible from the cron service
- Verify alert URLs are correct

### High Memory Usage

- Monitor the number of active timers
- Check for memory leaks in task management
- Review concurrent task execution patterns

## 📄 License

Part of the Pulse Observability Platform. See [LICENSE](../../LICENSE) for details.

---

**Built with ❤️ by the Pulse Backend Team**

