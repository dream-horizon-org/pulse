# Notification Service - Technical Design Document

## Overview

A multi-tenant notification service that supports Email, Slack, and Microsoft Teams channels with project-scoped configuration, template management, and reliable delivery.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Data Model](#data-model)
3. [API Design](#api-design)
4. [Component Design](#component-design)
5. [Phase Breakdown](#phase-breakdown)
6. [Configuration](#configuration)
7. [Security Considerations](#security-considerations)

---

## Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              API Layer                                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │
│  │ NotificationAPI │  │  ChannelAPI     │  │  TemplateAPI    │              │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘              │
└───────────┼─────────────────────┼─────────────────────┼─────────────────────┘
            │                     │                     │
            ▼                     ▼                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Service Layer                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      NotificationService                             │    │
│  │  • Validate project access                                           │    │
│  │  • Resolve channels & templates                                      │    │
│  │  • Check suppression list                                            │    │
│  │  • Handle idempotency                                                │    │
│  │  • Delegate to providers                                             │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌───────────────────┐  ┌───────────────────┐                              │
│  │  TemplateService  │  │   SqsQueue (P2)   │                              │
│  │  • Render params  │  │ • Queue messages  │                              │
│  └───────────────────┘  └───────────────────┘                              │
└─────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Provider Layer                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    NotificationProviderFactory                         │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│           │                      │                      │                    │
│           ▼                      ▼                      ▼                    │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐          │
│  │ EmailProvider   │    │ SlackProvider   │    │ TeamsProvider   │          │
│  │ (AWS SES)       │    │ (Web API)       │    │ (Workflows)     │          │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘          │
└─────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Data Layer                                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ Projects │ │ Channels │ │Templates │ │   Logs   │ │Suppression│          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Design Principles

| Principle | Implementation |
|-----------|----------------|
| **Multi-tenancy** | Project-scoped resources under tenant hierarchy |
| **Idempotency** | Unique constraint on `(project_id, idempotency_key, channel_type, recipient)` |
| **Extensibility** | Provider pattern for adding new channels |
| **Reliability** | Retry with exponential backoff, DLQ for failures |
| **Security** | Encrypted credentials, suppression lists |

---

## Data Model

### Entity Relationship Diagram

```
┌─────────────┐
│   tenants   │
│─────────────│
│ tenant_id   │◄─────────────────────────────────┐
└─────────────┘                                  │
                                                 │
┌─────────────────────────────────────────────────┼───────────────────────────┐
│                                                 │                           │
│  ┌─────────────┐       ┌────────────────────────┴───┐                       │
│  │  projects   │       │                            │                       │
│  │─────────────│       │                            │                       │
│  │ id (PK)     │       │  FK: tenant_id             │                       │
│  │ tenant_id   │───────┘                            │                       │
│  │ name        │                                    │                       │
│  │ is_active   │                                    │                       │
│  └──────┬──────┘                                    │                       │
│         │                                           │                       │
│         │ 1:N                                       │                       │
│         │                                           │                       │
│         ▼                                           │                       │
│  ┌──────────────────────────┐    ┌──────────────────────────┐               │
│  │ project_notification_    │    │ project_notification_    │               │
│  │ channels                 │    │ templates                │               │
│  │──────────────────────────│    │──────────────────────────│               │
│  │ id (PK)                  │    │ id (PK)                  │               │
│  │ project_id (FK)          │    │ project_id (FK)          │               │
│  │ channel_type (ENUM)      │    │ name                     │               │
│  │ name                     │    │ channel_type (ENUM/NULL) │               │
│  │ config (JSON)            │    │ version                  │               │
│  │ is_active                │    │ subject                  │               │
│  └──────────┬───────────────┘    │ body                     │               │
│             │                    │ is_active                │               │
│             │                    └──────────────────────────┘               │
│             │                                                               │
│             │ 1:N                                                           │
│             ▼                                                               │
│  ┌──────────────────────────┐    ┌──────────────────────────┐               │
│  │ notification_logs        │    │ email_suppression_list   │               │
│  │──────────────────────────│    │──────────────────────────│               │
│  │ id (PK)                  │    │ id (PK)                  │               │
│  │ project_id (FK)          │    │ project_id (FK)          │               │
│  │ batch_id                 │    │ email                    │               │
│  │ idempotency_key          │    │ reason (ENUM)            │               │
│  │ channel_type             │    │ bounce_type              │               │
│  │ channel_id (FK)          │    │ expires_at               │               │
│  │ template_name            │    └──────────────────────────┘               │
│  │ recipient                │                                               │
│  │ status (ENUM)            │                                               │
│  │ attempt_count            │                                               │
│  │ error_message            │                                               │
│  │ external_id              │                                               │
│  │ latency_ms               │                                               │
│  └──────────────────────────┘                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Database Schema

```sql
-- 1. Projects (project-scoped multi-tenancy)
CREATE TABLE projects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    UNIQUE KEY unique_tenant_project (tenant_id, name),
    INDEX idx_projects_tenant (tenant_id)
);

-- 2. Notification Channels
CREATE TABLE project_notification_channels (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    channel_type ENUM('SLACK', 'EMAIL', 'TEAMS') NOT NULL,
    name VARCHAR(255) NOT NULL,
    config JSON NOT NULL,  -- Encrypted sensitive fields
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    UNIQUE KEY unique_project_channel (project_id, name),
    INDEX idx_channels_type (channel_type)
);

-- 3. Notification Templates
CREATE TABLE project_notification_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    channel_type ENUM('SLACK', 'EMAIL', 'TEAMS') NULL,  -- NULL = generic template
    version INT NOT NULL DEFAULT 1,
    subject VARCHAR(500),
    body TEXT NOT NULL,                -- Content format depends on channel_type
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    UNIQUE KEY unique_template (project_id, name, channel_type, version),
    INDEX idx_templates_name (project_id, name)
);

-- 4. Notification Logs (with idempotency)
CREATE TABLE notification_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    batch_id VARCHAR(50),
    idempotency_key VARCHAR(128),
    channel_type ENUM('SLACK', 'EMAIL', 'TEAMS') NOT NULL,
    channel_id BIGINT,
    template_name VARCHAR(255),
    recipient VARCHAR(500) NOT NULL,
    subject VARCHAR(500),
    status ENUM('QUEUED', 'PROCESSING', 'SENT', 'FAILED', 'RETRYING', 'SKIPPED', 'PERMANENT_FAILURE') NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP NULL,
    error_message TEXT,
    error_code VARCHAR(50),
    external_id VARCHAR(255),         -- Provider message ID
    provider_response JSON,
    latency_ms INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    UNIQUE KEY uniq_dedupe (project_id, idempotency_key, channel_type, recipient),
    INDEX idx_logs_batch (project_id, batch_id),
    INDEX idx_logs_status (status)
);

-- 5. Email Suppression List
CREATE TABLE email_suppression_list (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    reason ENUM('BOUNCE', 'COMPLAINT', 'MANUAL') NOT NULL,
    bounce_type VARCHAR(50),
    source_message_id VARCHAR(255),
    suppressed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    UNIQUE KEY unique_project_email (project_id, email)
);
```

### Channel Configuration Models

```java
// Base configuration (polymorphic)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = EmailChannelConfig.class, name = "EMAIL"),
    @Type(value = SlackChannelConfig.class, name = "SLACK"),
    @Type(value = TeamsChannelConfig.class, name = "TEAMS")
})
public abstract class ChannelConfig {
    public abstract ChannelType getChannelType();
}

// Email (AWS SES)
public class EmailChannelConfig extends ChannelConfig {
    private String fromAddress;        // "noreply@example.com"
    private String fromName;           // "Pulse Notifications"
    private String replyToAddress;
    private String configurationSetName;  // SES configuration set
}

// Slack
public class SlackChannelConfig extends ChannelConfig {
    private String accessToken;        // Encrypted OAuth token
    private String defaultChannelId;   // Default channel
    private String botName;
    private String iconEmoji;
}

// Microsoft Teams
public class TeamsChannelConfig extends ChannelConfig {
    private String workflowUrl;        // Power Automate workflow URL
    private String defaultTitle;
}
```

---

## API Design

### Base URL

```
/v1/projects/{projectId}/notifications
```

### Endpoints Summary

| Method | Endpoint | Description | Phase |
|--------|----------|-------------|-------|
| POST | `/send` | Send notification (sync) | 1 |
| POST | `/send-async` | Queue notification (async) | 2 |
| POST | `/batch` | Batch send | 4 |
| GET | `/logs` | Get notification logs | 1 |
| GET | `/logs/batch/{batchId}` | Get logs by batch | 1 |
| GET | `/../notification-channels` | List channels | 1 |
| POST | `/../notification-channels` | Create channel | 1 |
| GET | `/../notification-channels/{id}` | Get channel | 1 |
| PUT | `/../notification-channels/{id}` | Update channel | 1 |
| DELETE | `/../notification-channels/{id}` | Delete channel | 1 |
| GET | `/../notification-templates` | List templates | 1 |
| POST | `/../notification-templates` | Create template | 1 |
| GET | `/../notification-templates/{id}` | Get template | 1 |
| PUT | `/../notification-templates/{id}` | Update template | 1 |
| DELETE | `/../notification-templates/{id}` | Delete template | 1 |
| POST | `/webhooks/ses` | SES bounce/complaint webhook | 5 |

### Send Notification API

**Request:**
```http
POST /v1/projects/{projectId}/notifications/send
Content-Type: application/json

{
  "channelTypes": ["EMAIL", "SLACK"],    // or ["ALL"]
  "templateName": "alert-triggered",
  "idempotencyKey": "alert-123-user-456", // Optional, auto-generated if not provided
  "recipients": {
    "emails": ["user@example.com"],
    "slackChannelIds": ["C0123456789"],
    "slackUserIds": ["U0123456789"],
    "teamsWorkflowUrls": ["https://..."]
  },
  "params": {
    "alertName": "High CPU Usage",
    "severity": "critical",
    "timestamp": "2024-01-15T10:30:00Z",
    "details": {
      "metric": "cpu_usage",
      "value": 95.5,
      "threshold": 80
    }
  },
  "metadata": {
    "alertId": "alert-123",
    "source": "monitoring"
  }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "batchId": "batch-uuid-123",
    "idempotencyKey": "alert-123-user-456",
    "status": "SUCCESS",           // SUCCESS | PARTIAL | FAILED
    "totalSent": 2,
    "totalFailed": 0,
    "totalSkipped": 0              // Duplicates or suppressed
  }
}
```

### Template with Parameters

Templates support `{{variable}}` syntax with nested object access:

```
Subject: Alert: {{alertName}} - {{severity}}

Body (text):
Alert "{{alertName}}" triggered at {{timestamp}}.

Details:
- Metric: {{details.metric}}
- Current Value: {{details.value}}
- Threshold: {{details.threshold}}

Body (HTML):
<h1>Alert: {{alertName}}</h1>
<p><strong>Severity:</strong> {{severity}}</p>
<table>
  <tr><td>Metric</td><td>{{details.metric}}</td></tr>
  <tr><td>Value</td><td>{{details.value}}</td></tr>
</table>

Body (Slack Block Kit JSON):
{
  "blocks": [
    {
      "type": "header",
      "text": {"type": "plain_text", "text": "🚨 {{alertName}}"}
    },
    {
      "type": "section",
      "fields": [
        {"type": "mrkdwn", "text": "*Severity:*\n{{severity}}"},
        {"type": "mrkdwn", "text": "*Value:*\n{{details.value}}"}
      ]
    }
  ]
}
```

---

## Component Design

### Core Models

```java
// Enums
public enum ChannelType { SLACK, EMAIL, TEAMS, ALL }
public enum NotificationStatus { QUEUED, PROCESSING, SENT, FAILED, RETRYING, SKIPPED, PERMANENT_FAILURE }
public enum SuppressionReason { BOUNCE, COMPLAINT, MANUAL }

// Simplified NotificationMessage (passed to providers)
@Data
@Builder
public class NotificationMessage {
    private Long logId;
    private Long projectId;
    private ChannelType channelType;
    private Long channelId;
    private String channelConfig;
    private String recipient;
    private Long templateId;              // Reference to DB template
    private Map<String, Object> params;   // For rendering
    private Map<String, Object> metadata;
}

// Provider result
@Data
@Builder
public class NotificationResult {
    private boolean success;
    private String externalId;
    private String errorCode;
    private String errorMessage;
    private boolean permanentFailure;
    private String providerResponse;
    private long latencyMs;
}
```

### Service Layer

```java
public interface NotificationService {
    // Send
    Single<NotificationBatchResponseDto> sendNotification(Long projectId, SendNotificationRequestDto request);
    
    // Channels
    Single<List<NotificationChannelDto>> getChannels(Long projectId);
    Single<NotificationChannelDto> createChannel(Long projectId, CreateChannelRequestDto request);
    Single<NotificationChannelDto> updateChannel(Long channelId, UpdateChannelRequestDto request);
    Single<Boolean> deleteChannel(Long channelId);
    
    // Templates
    Single<List<NotificationTemplateDto>> getTemplates(Long projectId);
    Single<NotificationTemplateDto> createTemplate(Long projectId, CreateTemplateRequestDto request);
    Single<NotificationTemplateDto> updateTemplate(Long templateId, UpdateTemplateRequestDto request);
    Single<Boolean> deleteTemplate(Long templateId);
    
    // Logs
    Single<NotificationLogsResponseDto> getLogs(Long projectId, int limit, int offset);
    Single<NotificationLogsResponseDto> getLogsByBatch(Long projectId, String batchId);
}
```

### Provider Pattern

```java
public interface NotificationProvider {
    ChannelType getChannelType();
    Single<NotificationResult> send(NotificationMessage message);
}

// Factory for provider lookup
public class NotificationProviderFactory {
    private final Map<ChannelType, NotificationProvider> providers;
    
    public Optional<NotificationProvider> getProvider(ChannelType channelType) {
        return Optional.ofNullable(providers.get(channelType));
    }
}
```

### Send Flow

```
1. Validate project access (tenant context)
2. Generate batch ID
3. For each channel type:
   a. Get active channels for project
   b. Get recipients for channel type
   c. Resolve template (channel-specific or generic fallback)
   d. Filter suppressed recipients (email)
   e. For each recipient:
      i.   Check idempotency (insert log if not exists)
      ii.  If duplicate → skip
      iii. Fetch template, render with params
      iv.  Build NotificationMessage
      v.   Call provider.send()
      vi.  Update log with result
4. Aggregate results → return response
```

---

## Phase Breakdown

### Phase 1: Core Infrastructure (Email Only)

**Scope:**
- Database schema (4 tables, uses existing `projects` table)
- All models and DTOs
- All DAOs
- TemplateService (param rendering)
- EmailNotificationProvider (AWS SES)
- NotificationService (synchronous send)
- REST APIs (Channel CRUD, Template CRUD, Send, Logs)
- Idempotency via DB unique constraint
- Email suppression check

**Deliverables:**
| Layer | Components |
|-------|------------|
| Database | `projects`, `project_notification_channels`, `project_notification_templates`, `notification_logs`, `email_suppression_list` |
| Models | `ChannelType`, `NotificationStatus`, `SuppressionReason`, `NotificationChannel`, `NotificationTemplate`, `NotificationLog`, `EmailSuppression`, `NotificationMessage`, `NotificationResult` |
| Config Models | `ChannelConfig`, `EmailChannelConfig`, `SlackChannelConfig`, `TeamsChannelConfig` |
| DAOs | `NotificationChannelDao`, `NotificationTemplateDao`, `NotificationLogDao`, `EmailSuppressionDao` |
| Services | `TemplateService`, `NotificationService`, `NotificationServiceImpl` |
| Providers | `NotificationProvider`, `NotificationProviderFactory`, `EmailNotificationProvider` |
| Controllers | `NotificationController`, `NotificationChannelController`, `NotificationTemplateController` |

---

### Phase 2: Async Processing (SQS)

**Scope:**
- SQS queue integration
- Async send API (returns immediately)
- Background worker for processing
- Retry with exponential backoff
- Dead Letter Queue (DLQ) handling

**New Components:**
| Component | Description |
|-----------|-------------|
| `SqsNotificationQueue` | Queue/dequeue notification messages |
| `NotificationWorker` | Background worker polling SQS |
| `NotificationRetryPolicy` | Configurable retry with backoff |
| `DlqHandler` | Process failed messages from DLQ |

**Configuration:**
```hocon
notification {
  sqs {
    queueUrl = ${?NOTIFICATION_QUEUE_URL}
    dlqUrl = ${?NOTIFICATION_DLQ_URL}
    visibilityTimeout = 30
    maxReceiveCount = 3
  }
  retry {
    maxAttempts = 3
    initialDelayMs = 1000
    maxDelayMs = 30000
    multiplier = 2.0
  }
}
```

**Flow Change:**
```
Send API (async):
1. Validate & create log entry (status: QUEUED)
2. Push to SQS
3. Return batch ID immediately

Worker:
1. Poll SQS for messages
2. Fetch template, render
3. Call provider
4. Update log
5. On failure: check retry policy → requeue or move to DLQ
```

---

### Phase 3: Additional Channels

**Scope:**
- Slack provider (OAuth, Block Kit)
- Teams provider (Workflows, Adaptive Cards)
- Channel-specific template rendering

**New Components:**
| Component | Description |
|-----------|-------------|
| `SlackNotificationProvider` | Slack Web API integration |
| `TeamsNotificationProvider` | Teams Workflow webhook integration |

**Slack Integration:**
```java
public class SlackNotificationProvider implements NotificationProvider {
    // Uses Slack Web API
    // Supports: chat.postMessage
    // Auth: OAuth Bot Token
    // Format: Block Kit JSON or plain text fallback
}
```

**Teams Integration:**
```java
public class TeamsNotificationProvider implements NotificationProvider {
    // Uses Power Automate Workflow webhooks
    // Format: Adaptive Card JSON
    // Auth: URL contains authentication
}
```

---

### Phase 4: Advanced Features

**Scope:**
- Template versioning & rollback
- Batch send optimization
- Rate limiting per channel
- Metrics & monitoring
- Delivery webhooks

**New Components:**
| Component | Description |
|-----------|-------------|
| `TemplateVersionService` | Manage template versions, activate/rollback |
| `BatchNotificationService` | Optimized batch sending |
| `RateLimiter` | Per-channel rate limits |
| `NotificationMetricsService` | Latency, success/failure tracking |
| `DeliveryWebhookService` | Callback on delivery status |

**Batch API:**
```http
POST /v1/projects/{projectId}/notifications/batch

{
  "notifications": [
    {
      "templateName": "welcome-email",
      "recipients": {"emails": ["user1@example.com"]},
      "params": {"name": "User 1"}
    },
    {
      "templateName": "welcome-email",
      "recipients": {"emails": ["user2@example.com"]},
      "params": {"name": "User 2"}
    }
  ]
}
```

---

### Phase 5: Reliability & Scale

**Scope:**
- SES bounce/complaint webhook handling
- Automatic suppression list management
- Template caching (Redis)
- Circuit breaker for providers
- Multi-region support

**New Components:**
| Component | Description |
|-----------|-------------|
| `SesWebhookHandler` | Process SNS notifications for bounces/complaints |
| `SuppressionAutoManager` | Auto-add bounced emails to suppression |
| `TemplateCache` | Redis-based template caching |
| `ProviderCircuitBreaker` | Fail-fast when provider is unhealthy |

**SES Webhook Flow:**
```
SNS → /webhooks/ses → Parse notification → 
  If bounce/complaint → Add to suppression list
  Update notification_log with delivery status
```

---

## Configuration

```hocon
notification {
  aws {
    ses {
      region = ${?AWS_REGION}
      region = "us-east-1"
    }
  }
  
  slack {
    clientId = ${?SLACK_CLIENT_ID}
    clientSecret = ${?SLACK_CLIENT_SECRET}
  }
  
  
  retry {
    maxAttempts = 3
    initialDelayMs = 1000
    maxDelayMs = 30000
    multiplier = 2.0
  }
  
  sqs {
    queueUrl = ${?NOTIFICATION_QUEUE_URL}
    dlqUrl = ${?NOTIFICATION_DLQ_URL}
  }
}
```

---

## Security Considerations

| Concern | Mitigation |
|---------|------------|
| **Credential Storage** | Channel configs encrypted with AES-256-GCM |
| **Multi-tenancy** | All queries scoped by tenant_id via TenantContext |
| **API Access** | Project access validated before operations |
| **Email Abuse** | Suppression list prevents sending to bounced/complained |
| **Rate Limiting** | Per-channel limits prevent provider abuse |
| **Idempotency** | DB constraint prevents duplicate sends |

---

## Summary

| Phase | Focus | Key Deliverables |
|-------|-------|------------------|
| **1** | Core | Email sending, templates, channels, logs |
| **2** | Async | SQS queue, background worker, retries |
| **3** | Channels | Slack & Teams providers |
| **4** | Features | Batching, rate limits, metrics |
| **5** | Scale | Webhooks, caching, circuit breakers |

---

## Appendix: Status Codes

| Status | Description |
|--------|-------------|
| `QUEUED` | Message queued for processing |
| `PROCESSING` | Currently being sent |
| `SENT` | Successfully delivered to provider |
| `FAILED` | Temporary failure, may retry |
| `RETRYING` | Scheduled for retry |
| `SKIPPED` | Duplicate or suppressed |
| `PERMANENT_FAILURE` | Will not retry (invalid address, etc.) |
