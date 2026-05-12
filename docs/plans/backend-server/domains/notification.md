# domains / notification

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [alert](alert.md), [incident](incident.md)

## Purpose

Notification channels (Slack/Email/Webhook), templates, channel-event
mappings, send (sync + async via SQS), logs, and SES bounce webhook.

## Source

- Controllers:
  - `resources/notification/NotificationController.java` (`/v1/notifications`)
  - `resources/notification/NotificationChannelController.java`
    (`/v1/notifications/channels`)
  - `resources/notification/NotificationTemplateController.java`
    (`/v1/notifications/templates`)
  - `resources/notification/ChannelMappingController.java`
    (`/v1/notifications/channels/mappings`)
  - `resources/notification/SlackOAuthController.java`
    (`/v1/notifications/integrations/slack`)
  - `resources/notification/SesWebhookController.java` (`/webhooks/ses`)
- `resources/notification/models/`
- `service/notification/NotificationService.java`,
  `NotificationServiceImpl.java`, `TemplateService.java`
- `service/notification/oauth/SlackOAuthService.java`
- `service/notification/provider/*` (Slack/Email/Webhook providers)
- `service/notification/queue/`: `SqsNotificationQueue.java`,
  `NotificationWorker.java`, `NotificationRetryPolicy.java`,
  `DlqHandler.java`
- `service/notification/webhook/SesWebhookHandler.java`
- `service/notification/models/`
- `dao/notification/`: `NotificationChannelDao.java`,
  `NotificationTemplateDao.java`, `ChannelEventMappingDao.java`,
  `NotificationLogDao.java`, `EmailSuppressionDao.java`,
  `NotificationQueries.java`

## Public surface

| Method | Path |
|---|---|
| POST | `/v1/notifications/send` |
| POST | `/v1/notifications/send/async` |
| GET | `/v1/notifications/logs` |
| GET | `/v1/notifications/logs/idempotency/{idempotencyKey}` |
| POST | `/v1/notifications/contact-us` |
| GET / POST | `/v1/notifications/channels` |
| GET / PUT / DELETE | `/v1/notifications/channels/{channelId}` |
| GET / POST | `/v1/notifications/templates` |
| GET / PUT / DELETE | `/v1/notifications/templates/{templateId}` |
| GET / POST | `/v1/notifications/channels/mappings` |
| POST | `/v1/notifications/channels/mappings/batch` |
| PUT / DELETE | `/v1/notifications/channels/mappings/{mappingId}` |
| GET | `/v1/notifications/integrations/slack/install` |
| GET | `/v1/notifications/integrations/slack/callback` |
| GET | `/v1/notifications/integrations/slack/channels` |
| POST | `/webhooks/ses` |

## Internal design

- Send-sync uses provider directly; send-async enqueues to SQS, consumed by
  `NotificationWorker` with `NotificationRetryPolicy` and `DlqHandler`.
- Templates rendered via `TemplateService`.
- Slack OAuth flow handled in `SlackOAuthService` + `SlackOAuthController`.
- SES bounce webhook updates `EmailSuppressionDao`.
- Channel-event mapping ties alerts/incidents to channels.

## Dependencies

MySQL notification tables; AWS SQS; AWS SES (bounces); Slack API; Email
provider. [alert](alert.md) and [incident](incident.md) consume `send`.

## Data contracts

MySQL: `notification_channels`, `notification_templates`,
`channel_event_mappings`, `notification_logs`, `email_suppressions`.
Errors: `DUPLICATE_CHANNEL_TYPE` (409), `INVALID_SLACK_CODE` (400).

## Tests

`src/test/java/.../resources/notification/*`,
`.../service/notification/**`.

## Rebuild recipe

1. Six controllers as above; bind providers in `MainModule`.
2. `NotificationService` interface + impl.
3. SQS queue + `NotificationWorker` verticle/thread + retry/DLQ.
4. SES webhook handler maintains suppression list.
5. Slack OAuth install/callback persists workspace token.
