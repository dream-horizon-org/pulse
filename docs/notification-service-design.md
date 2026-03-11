# Notification Service - Technical Design Document

## Overview

A multi-tenant notification service that supports Email, Slack (OAuth & Webhook), and Microsoft Teams channels with event-driven channel mappings, global template management, and reliable delivery via SQS.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Data Model](#data-model)
3. [API Design](#api-design)
4. [Component Design](#component-design)
5. [Configuration](#configuration)
6. [Security Considerations](#security-considerations)

---

## Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              API Layer                                       │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────┐           │
│  │NotificationCtrl │  │ChannelCtrl       │  │TemplateCtrl      │           │
│  │  /send          │  │ /channels        │  │ /templates       │           │
│  │  /send/async    │  │ /channels/{id}   │  │ /templates/{id}  │           │
│  │  /logs          │  └──────────────────┘  └──────────────────┘           │
│  └────────┬────────┘                                                        │
│           │           ┌──────────────────┐  ┌──────────────────┐           │
│           │           │MappingCtrl       │  │SlackOAuthCtrl    │           │
│           │           │ /channels/       │  │ /integrations/   │           │
│           │           │   mappings       │  │   slack          │           │
│           │           └──────────────────┘  └──────────────────┘           │
│           │                                                                 │
│           │           ┌──────────────────┐                                  │
│           │           │SesWebhookCtrl    │                                  │
│           │           │ /webhooks/ses    │                                  │
│           │           └──────────────────┘                                  │
└───────────┼─────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Service Layer                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      NotificationService                               │  │
│  │  • Resolve mappings (by ID or by event + channel types)                │  │
│  │  • Resolve recipients (mapping DB + request payload)                   │  │
│  │  • Check email suppression list                                        │  │
│  │  • Handle idempotency (INSERT IGNORE per recipient)                    │  │
│  │  • Delegate to providers (sync) or SQS queue (async)                   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐       │
│  │  TemplateService  │  │ SlackOAuthService │  │ SesWebhookHandler │       │
│  │  • Render text    │  │ • Install URL     │  │ • Bounce handling │       │
│  │  • Render JSON    │  │ • Token exchange  │  │ • Complaint       │       │
│  │  • {{params}}     │  │ • Channel create  │  │ • Suppression     │       │
│  └───────────────────┘  └───────────────────┘  └───────────────────┘       │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      SQS Integration                                   │  │
│  │  SqsNotificationQueue → NotificationWorker → NotificationRetryPolicy  │  │
│  │                                              → DlqHandler              │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Provider Layer                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    NotificationProviderFactory                         │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│       │                │                  │                │                 │
│       ▼                ▼                  ▼                ▼                 │
│  ┌──────────┐  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │ Email    │  │ Slack       │  │ SlackWebhook │  │ Teams        │        │
│  │ (SES)    │  │ (OAuth API) │  │ (Webhook)    │  │ (Workflows)  │        │
│  └──────────┘  └─────────────┘  └──────────────┘  └──────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Data Layer                                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │ Channels │ │Templates │ │ Mappings │ │   Logs   │ │Suppression│         │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Design Principles

| Principle | Implementation |
|-----------|----------------|
| **Multi-tenancy** | Project-scoped channels & mappings; shared EMAIL channels; `X-Project-Id` header |
| **Event-driven** | Channel-event mappings link events → channels → recipients |
| **Idempotency** | `INSERT IGNORE` on `(project_id, idempotency_key, channel_type, recipient)` |
| **Extensibility** | Provider pattern for channels; polymorphic `TemplateBody` and `ChannelConfig` |
| **Reliability** | SQS async send, retry with exponential backoff, DLQ for failures |
| **Security** | Encrypted credentials, email suppression list, channel scoping validation |

---

## Data Model

### Entity Relationship Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                                                                           │
│  ┌──────────────────────────┐                                             │
│  │ notification_channels    │──────────────────────────┐                  │
│  │──────────────────────────│                          │                  │
│  │ id (PK)                  │                          │                  │
│  │ project_id (nullable)    │  ← NULL = shared (EMAIL) │                  │
│  │ channel_type (ENUM)      │                          │                  │
│  │ name                     │                          │                  │
│  │ config (JSON)            │                          │                  │
│  │ is_active                │                          │                  │
│  └───────┬──────────────────┘                          │                  │
│          │                                              │                  │
│          │ 1:N                                          │ FK               │
│          │                                              │                  │
│          ▼                                              │                  │
│  ┌──────────────────────────┐    ┌──────────────────────┴─────┐           │
│  │ channel_event_mapping    │    │ notification_logs           │           │
│  │──────────────────────────│    │────────────────────────────│           │
│  │ id (PK)                  │    │ id (PK)                    │           │
│  │ project_id               │    │ project_id                 │           │
│  │ channel_id (FK)          │    │ idempotency_key            │           │
│  │ event_name               │    │ channel_type               │           │
│  │ recipient (nullable)     │    │ channel_id (FK)            │           │
│  │ recipient_name           │    │ template_id (FK)           │           │
│  │ is_active                │    │ recipient                  │           │
│  └──────────────────────────┘    │ subject                    │           │
│                                  │ status (ENUM)              │           │
│  ┌──────────────────────────┐    │ attempt_count              │           │
│  │ notification_templates   │    │ error_message              │           │
│  │──────────────────────────│    │ external_id                │           │
│  │ id (PK)                  │    │ latency_ms                 │           │
│  │ event_name               │    └────────────────────────────┘           │
│  │ channel_type (ENUM)      │                                             │
│  │ version                  │    ┌────────────────────────────┐           │
│  │ body (JSON)              │    │ email_suppression_list     │           │
│  │ is_active                │    │────────────────────────────│           │
│  │ (global, no project_id)  │    │ id (PK)                    │           │
│  └──────────────────────────┘    │ project_id (nullable)      │           │
│                                  │ email                       │           │
│                                  │ reason (ENUM)               │           │
│                                  │ bounce_type                 │           │
│                                  │ source_message_id           │           │
│                                  └────────────────────────────┘           │
└───────────────────────────────────────────────────────────────────────────┘
```

### Database Schema

```sql
-- 1. Notification Channels
--    project_id IS NULL → shared channel (EMAIL)
--    project_id IS NOT NULL → project-scoped (SLACK, TEAMS)
CREATE TABLE notification_channels (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64),
    channel_type ENUM('SLACK', 'SLACK_WEBHOOK', 'EMAIL', 'TEAMS') NOT NULL,
    name VARCHAR(255) NOT NULL,
    config JSON NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY unique_project_channel_type (project_id, channel_type),
    INDEX idx_channels_type (channel_type)
);

-- 2. Notification Templates (global, not project-scoped)
--    Keyed by (event_name, channel_type)
--    body is polymorphic JSON (EmailTemplateBody, SlackTemplateBody, TeamsTemplateBody)
CREATE TABLE notification_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_name VARCHAR(255) NOT NULL,
    channel_type ENUM('SLACK', 'SLACK_WEBHOOK', 'EMAIL', 'TEAMS') NULL,
    version INT NOT NULL DEFAULT 1,
    body JSON NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY unique_template (event_name, channel_type),
    INDEX idx_templates_event (event_name)
);

-- 3. Channel-Event Mappings
--    Links (project, channel, event) with optional static recipient
CREATE TABLE channel_event_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL,
    channel_id BIGINT NOT NULL,
    event_name VARCHAR(255) NOT NULL,
    recipient VARCHAR(500),
    recipient_name VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (channel_id) REFERENCES notification_channels(id) ON DELETE CASCADE,
    UNIQUE KEY unique_mapping (channel_id, event_name, recipient_name)
);

-- 4. Notification Logs (with idempotency)
CREATE TABLE notification_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(255),
    channel_type ENUM('SLACK', 'SLACK_WEBHOOK', 'EMAIL', 'TEAMS') NOT NULL,
    channel_id BIGINT,
    template_id BIGINT,
    recipient VARCHAR(500) NOT NULL,
    subject VARCHAR(500),
    status ENUM('PENDING', 'QUEUED', 'PROCESSING', 'SENT', 'DELIVERED',
                'FAILED', 'RETRYING', 'SKIPPED', 'PERMANENT_FAILURE',
                'BOUNCED', 'COMPLAINED') NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP NULL,
    error_message TEXT,
    error_code VARCHAR(50),
    external_id VARCHAR(255),
    provider_response JSON,
    latency_ms INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,

    UNIQUE KEY uniq_dedupe (project_id, idempotency_key, channel_type, recipient),
    INDEX idx_logs_project (project_id),
    INDEX idx_logs_status (status)
);

-- 5. Email Suppression List
--    project_id IS NULL → suppressed across all projects
CREATE TABLE email_suppression_list (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64),
    email VARCHAR(255) NOT NULL,
    reason ENUM('BOUNCE', 'COMPLAINT', 'MANUAL') NOT NULL,
    bounce_type VARCHAR(50),
    source_message_id VARCHAR(255),
    suppressed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY unique_project_email (project_id, email)
);
```

### Channel Configuration Models

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = EmailChannelConfig.class, name = "EMAIL"),
    @Type(value = SlackChannelConfig.class, name = "SLACK"),
    @Type(value = SlackWebhookChannelConfig.class, name = "SLACK_WEBHOOK"),
    @Type(value = TeamsChannelConfig.class, name = "TEAMS")
})
public abstract class ChannelConfig {
    public abstract ChannelType getChannelType();
}

// Email (AWS SES) — shared, no project_id
public class EmailChannelConfig extends ChannelConfig {
    private String fromAddress;
    private String fromName;
    private String replyToAddress;
    private String configurationSetName;
}

// Slack (OAuth) — project-scoped
public class SlackChannelConfig extends ChannelConfig {
    private String accessToken;   // Encrypted OAuth bot token
    private String workspaceId;
    private String botName;
    private String iconEmoji;
}

// Slack Webhook — project-scoped
public class SlackWebhookChannelConfig extends ChannelConfig {
    private String botName;
    private String iconEmoji;
    // recipient = webhook URL (per mapping or per request)
}

// Microsoft Teams — project-scoped
public class TeamsChannelConfig extends ChannelConfig {
    private String workflowUrl;
    private String defaultTitle;
}
```

### Template Body Models

Templates store a polymorphic JSON `body` that varies by channel type:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = EmailTemplateBody.class, name = "EMAIL"),
    @Type(value = SlackTemplateBody.class, name = "SLACK"),
    @Type(value = TeamsTemplateBody.class, name = "TEAMS")
})
public abstract class TemplateBody {}

public class EmailTemplateBody extends TemplateBody {
    private String subject;   // "Alert: {{alertName}} - {{severity}}"
    private String html;      // HTML body with {{params}}
    private String text;      // Plain text fallback
}

public class SlackTemplateBody extends TemplateBody {
    private String text;      // Fallback text
    private JsonNode blocks;  // Block Kit JSON with {{params}}
}

public class TeamsTemplateBody extends TemplateBody {
    private String title;
    private String text;        // Simple text
    private JsonNode body;      // Adaptive Card body
    private JsonNode actions;   // Adaptive Card actions
}
```

---

## API Design

### Base URL

```
/v1/notifications
```

Project context is provided via the `X-Project-Id` header where applicable.

### Endpoints Summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| **Send** | | |
| POST | `/send` | Send notification (sync) |
| POST | `/send/async` | Queue notification via SQS (async) |
| **Logs** | | |
| GET | `/logs` | Get notification logs by project |
| GET | `/logs/idempotency/{key}` | Get logs by idempotency key |
| **Channels** | | |
| GET | `/channels` | List channels (filter by projectId, channelType) |
| GET | `/channels/{channelId}` | Get channel by ID |
| POST | `/channels` | Create channel |
| PUT | `/channels/{channelId}` | Update channel |
| DELETE | `/channels/{channelId}` | Delete channel |
| **Templates** | | |
| GET | `/templates` | List templates (filter by channelType) |
| GET | `/templates/{templateId}` | Get template by ID |
| POST | `/templates` | Create template (auto-increments version) |
| PUT | `/templates/{templateId}` | Update template |
| DELETE | `/templates/{templateId}` | Delete template |
| **Mappings** | | |
| GET | `/channels/mappings` | List mappings by project |
| POST | `/channels/mappings` | Create mapping |
| POST | `/channels/mappings/batch` | Batch create mappings |
| PUT | `/channels/mappings/{mappingId}` | Update mapping |
| DELETE | `/channels/mappings/{mappingId}` | Delete mapping |
| **Integrations** | | |
| GET | `/integrations/slack/install` | Get Slack OAuth install URL |
| GET | `/integrations/slack/callback` | Slack OAuth callback |
| GET | `/integrations/slack/channels` | List Slack workspace channels |
| **Webhooks** | | |
| POST | `/webhooks/ses` | SES bounce/complaint webhook (SNS) |

### Send Notification API

Notifications can be sent in two modes:

1. **By mapping ID** — provide `mappingId` to target a specific channel-event mapping
2. **By event** — provide `eventName` + `channelTypes` to resolve all matching mappings for a project

**Request:**
```http
POST /v1/notifications/send
X-Project-Id: project-123
Content-Type: application/json

{
  "eventName": "alert-triggered",
  "channelTypes": ["EMAIL", "SLACK"],
  "idempotencyKey": "alert-123-run-1",
  "recipients": {
    "emails": ["user@example.com"],
    "slackChannelIds": ["C0123456789"],
    "slackUserIds": ["U0123456789"],
    "slackWebhookUrls": ["https://hooks.slack.com/..."],
    "teamsWorkflowUrls": ["https://..."]
  },
  "params": {
    "alertName": "High CPU Usage",
    "severity": "critical",
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

Alternatively, send by mapping ID:
```http
POST /v1/notifications/send
X-Project-Id: project-123
Content-Type: application/json

{
  "mappingId": 42,
  "params": { "alertName": "High CPU" }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "idempotencyKey": "alert-123-run-1",
    "totalRecipients": 3,
    "queued": 2,
    "failed": 0,
    "results": [
      {
        "recipient": "user@example.com",
        "channelType": "EMAIL",
        "status": "SENT",
        "externalId": "ses-message-id"
      },
      {
        "recipient": "C0123456789",
        "channelType": "SLACK",
        "status": "SENT",
        "externalId": "slack-ts-id"
      },
      {
        "recipient": "suppressed@example.com",
        "channelType": "EMAIL",
        "status": "SKIPPED",
        "errorMessage": "Email is suppressed"
      }
    ]
  }
}
```

### Template Rendering

Templates support `{{variable}}` syntax with nested object access via `TemplateService`:

- **Text rendering**: `TemplateService.renderText(text, params)` — replaces `{{key}}` and `{{nested.key}}`
- **JSON rendering**: `TemplateService.renderJson(jsonNode, params)` — recursively renders all text nodes in JSON structures

```
Subject: Alert: {{alertName}} - {{severity}}

HTML body:
<h1>Alert: {{alertName}}</h1>
<p>Metric: {{details.metric}} at {{details.value}} (threshold: {{details.threshold}})</p>

Slack Block Kit JSON (rendered in-place):
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
// Channel types
public enum ChannelType {
    SLACK,           // OAuth-based Slack integration
    SLACK_WEBHOOK,   // Slack incoming webhook
    EMAIL,           // AWS SES
    TEAMS,           // Power Automate workflow
    ALL              // Used in API filtering only
}

// Notification statuses
public enum NotificationStatus {
    PENDING, QUEUED, PROCESSING, SENT, DELIVERED,
    FAILED, RETRYING, SKIPPED, PERMANENT_FAILURE,
    BOUNCED, COMPLAINED
}

public enum SuppressionReason { BOUNCE, COMPLAINT, MANUAL }

@Data @Builder
public class NotificationChannel {
    private Long id;
    private String projectId;       // null = shared channel
    private ChannelType channelType;
    private String name;
    private ChannelConfig config;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}

@Data @Builder
public class NotificationTemplate {
    private Long id;
    private String eventName;
    private ChannelType channelType;
    private Integer version;
    private TemplateBody body;      // Polymorphic JSON
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}

@Data @Builder
public class ChannelEventMapping {
    private Long id;
    private String projectId;
    private Long channelId;
    private String eventName;
    private String recipient;       // Optional static recipient
    private String recipientName;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}

@Data @Builder
public class NotificationMessage {
    private Long logId;
    private String projectId;
    private String idempotencyKey;
    private ChannelType channelType;
    private Long channelId;
    private ChannelConfig channelConfig;
    private Long templateId;
    private TemplateBody templateBody;
    private String recipient;
    private String subject;
    private Map<String, Object> params;
    private Map<String, Object> metadata;
}

@Data @Builder
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
    Single<NotificationBatchResponseDto> sendNotification(String projectId, SendNotificationRequestDto request);
    Single<NotificationBatchResponseDto> sendNotificationAsync(String projectId, SendNotificationRequestDto request);

    // Channels
    Single<List<NotificationChannelDto>> getChannels(String projectId, ChannelType channelType);
    Maybe<NotificationChannelDto> getChannel(Long channelId);
    Single<NotificationChannelDto> createChannel(CreateChannelRequestDto request);
    Single<NotificationChannelDto> updateChannel(Long channelId, UpdateChannelRequestDto request);
    Single<Boolean> deleteChannel(Long channelId);

    // Templates (global)
    Single<List<NotificationTemplateDto>> getTemplates(ChannelType channelType);
    Maybe<NotificationTemplateDto> getTemplate(Long templateId);
    Single<NotificationTemplateDto> createTemplate(CreateTemplateRequestDto request);
    Single<NotificationTemplateDto> updateTemplate(Long templateId, UpdateTemplateRequestDto request);
    Single<Boolean> deleteTemplate(Long templateId);

    // Mappings
    Single<List<ChannelEventMappingDto>> getMappings(String projectId);
    Single<ChannelEventMappingDto> createMapping(String projectId, CreateMappingRequestDto request);
    Single<List<ChannelEventMappingDto>> createMappingsBatch(String projectId, BatchCreateMappingRequestDto request);
    Single<ChannelEventMappingDto> updateMapping(Long mappingId, UpdateMappingRequestDto request);
    Single<Boolean> deleteMapping(Long mappingId);

    // Logs
    Single<NotificationLogsResponseDto> getLogs(String projectId, int limit, int offset);
    Single<NotificationLogsResponseDto> getLogsByIdempotencyKey(String projectId, String idempotencyKey);
}
```

### Provider Pattern

```java
public interface NotificationProvider {
    ChannelType getChannelType();
    Single<NotificationResult> send(NotificationMessage message, NotificationTemplate template);
}

public class NotificationProviderFactory {
    private final Map<ChannelType, NotificationProvider> providers;

    public Optional<NotificationProvider> getProvider(ChannelType channelType) {
        return Optional.ofNullable(providers.get(channelType));
    }
}
```

**Implemented Providers:**

| Provider | Channel Type | Integration |
|----------|-------------|-------------|
| `EmailNotificationProvider` | EMAIL | AWS SES: renders subject/html/text from `EmailTemplateBody` |
| `SlackNotificationProvider` | SLACK | `chat.postMessage` with OAuth Bearer token; uses `SlackPayloadBuilder` |
| `SlackWebhookNotificationProvider` | SLACK_WEBHOOK | POST to webhook URL (recipient = URL) |
| `TeamsNotificationProvider` | TEAMS | POST to workflow URL; simple text or Adaptive Card |

### Send Flow (Mapping-Driven)

```
1. Resolve send mode:
   a. If mappingId provided → buildAndSendByMappingId
   b. Else validate (eventName, projectId, channelTypes) → buildAndSendByEvent

2. buildAndSendByMappingId:
   a. Load mapping + joined channel data
   b. Verify mapping belongs to project
   c. Merge recipients: mapping.recipient + request.recipients
   d. Resolve template by (eventName, channelType)
   e. Dispatch to recipients

3. buildAndSendByEvent:
   a. Load all active mappings for (projectId, eventName) with channel data
   b. Group by channel_id
   c. For each channel (filtered by requested channelTypes):
      i.   Merge recipients from all mappings + request
      ii.  Resolve template by (eventName, channelType)
      iii. Dispatch to recipients
   d. Aggregate all results

4. dispatchToRecipients (per channel):
   a. If async → enqueueRecipients (SQS)
   b. If sync  → sendToRecipients (direct)

5. sendToRecipients:
   For each recipient:
     a. Check email suppression (EMAIL only)
     b. INSERT IGNORE log entry (idempotency check)
     c. If duplicate → SKIPPED
     d. Call provider.send(message, template)
     e. Update log with result (SENT / FAILED / PERMANENT_FAILURE)

6. enqueueRecipients:
   For each recipient:
     a. Check email suppression
     b. Check idempotency via existing log lookup
     c. If duplicate → SKIPPED
     d. Insert log (QUEUED)
     e. Push NotificationMessage to SQS
```

### SQS Async Processing

```
SqsNotificationQueue:
  • enqueue(message) → send to SQS with message attributes
  • receive(maxMessages) → poll from queue
  • delete(receiptHandle) → acknowledge processing
  • changeVisibility(receiptHandle, timeout) → delay retry

NotificationWorker (background poller):
  1. Poll SQS for messages
  2. Load template by templateId
  3. Resolve provider by channelType
  4. Call provider.send()
  5. Update notification_log
  6. On failure → check NotificationRetryPolicy
     a. shouldRetry? → change visibility timeout (exponential backoff)
     b. Permanent failure or max attempts? → move to DLQ

NotificationRetryPolicy:
  • maxAttempts, initialDelayMs, maxDelayMs, multiplier
  • shouldRetry(attemptCount, isPermanentFailure)
  • getVisibilityTimeoutSeconds(attemptCount)

DlqHandler:
  • Processes DLQ messages
  • analyzeFailure → currently DISCARD
  • Can requeue or discard
```

### Slack OAuth Flow

```
1. GET /integrations/slack/install?projectId=xxx
   → SlackOAuthService.generateInstallUrl(projectId)
   → Returns install URL with state=projectId

2. User authorizes the Slack app in their workspace

3. GET /integrations/slack/callback?code=xxx&state=projectId
   → SlackOAuthService.exchangeCodeForToken(code)
   → Returns SlackOAuthResult(accessToken, workspaceId, workspaceName, botUserId)
   → SlackOAuthService.createOrUpdateSlackChannel(projectId, oauthResult)
   → Creates/updates notification_channels entry with SLACK type

4. GET /integrations/slack/channels?projectId=xxx
   → SlackOAuthService.listWorkspaceChannels(projectId)
   → Calls conversations.list with stored token
   → Returns list of SlackChannelListDto(id, name, isPrivate, isMember)
```

### SES Webhook Handler

```
POST /webhooks/ses (SNS notification)

SesWebhookHandler:
  1. Parse SNS envelope
  2. If SubscriptionConfirmation → confirm subscription
  3. If Notification → parse SES event:
     a. Bounce → addToSuppressionListAllProjects(email, BOUNCE, bounceType)
                → updateLogStatusByExternalId(messageId, BOUNCED)
     b. Complaint → addToSuppressionListAllProjects(email, COMPLAINT)
                  → updateLogStatusByExternalId(messageId, COMPLAINED)
     c. Delivery → updateLogStatusByExternalId(messageId, DELIVERED)
```

### Channel Scoping Rules

| Channel Type | Scoping | `project_id` | Access Rule |
|---|---|---|---|
| EMAIL | Shared | NULL | Accessible by all projects |
| SLACK | Project-scoped | Required | Only accessible by owning project |
| SLACK_WEBHOOK | Project-scoped | Required | Only accessible by owning project |
| TEAMS | Project-scoped | Required | Only accessible by owning project |

- `getChannelsAccessibleByProject(projectId)` returns channels where `project_id = ? OR project_id IS NULL`
- Creating an EMAIL channel with a `projectId` is rejected
- Creating a SLACK/TEAMS channel without a `projectId` is rejected

---

## Components Summary

| Layer | Components |
|-------|------------|
| **Database** | `notification_channels`, `notification_templates`, `channel_event_mapping`, `notification_logs`, `email_suppression_list` |
| **Models** | `ChannelType`, `NotificationStatus`, `SuppressionReason`, `NotificationChannel`, `NotificationTemplate`, `ChannelEventMapping`, `NotificationLog`, `EmailSuppression`, `NotificationMessage`, `NotificationResult`, `QueuedNotification` |
| **Config Models** | `ChannelConfig`, `EmailChannelConfig`, `SlackChannelConfig`, `SlackWebhookChannelConfig`, `TeamsChannelConfig` |
| **Template Models** | `TemplateBody`, `EmailTemplateBody`, `SlackTemplateBody`, `TeamsTemplateBody` |
| **DAOs** | `NotificationChannelDao`, `NotificationTemplateDao`, `ChannelEventMappingDao`, `NotificationLogDao`, `EmailSuppressionDao` |
| **Services** | `NotificationService`, `NotificationServiceImpl`, `TemplateService`, `SlackOAuthService`, `SesWebhookHandler` |
| **Queue** | `SqsNotificationQueue`, `NotificationWorker`, `NotificationRetryPolicy`, `DlqHandler` |
| **Providers** | `NotificationProvider`, `NotificationProviderFactory`, `EmailNotificationProvider`, `SlackNotificationProvider`, `SlackWebhookNotificationProvider`, `TeamsNotificationProvider`, `SlackPayloadBuilder` |
| **Controllers** | `NotificationController`, `NotificationChannelController`, `NotificationTemplateController`, `ChannelMappingController`, `SlackOAuthController`, `SesWebhookController` |

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
| **Credential Storage** | Channel configs stored as encrypted JSON |
| **Channel Scoping** | EMAIL is shared (no projectId); SLACK/TEAMS require projectId; validated on create |
| **Project Access** | `X-Project-Id` header; mapping ownership verified before send |
| **Email Abuse** | Suppression list prevents sending to bounced/complained; SES webhook auto-manages |
| **Rate Limiting** | Per-channel rate limits to prevent provider abuse |
| **Idempotency** | `INSERT IGNORE` on `(project_id, idempotency_key, channel_type, recipient)` |
| **Slack OAuth** | Tokens encrypted at rest; state parameter validates project context on callback |

---

## Appendix: Status Codes

| Status | Description |
|--------|-------------|
| `PENDING` | Created but not yet processed |
| `QUEUED` | Enqueued in SQS for async processing |
| `PROCESSING` | Currently being sent by a provider |
| `SENT` | Successfully delivered to provider API |
| `DELIVERED` | Confirmed delivery (via SES webhook) |
| `FAILED` | Temporary failure, may retry |
| `RETRYING` | Scheduled for retry via SQS visibility timeout |
| `SKIPPED` | Duplicate (idempotency) or suppressed |
| `PERMANENT_FAILURE` | Will not retry (invalid address, rejected, etc.) |
| `BOUNCED` | Email bounced (updated via SES webhook) |
| `COMPLAINED` | Spam complaint received (updated via SES webhook) |
