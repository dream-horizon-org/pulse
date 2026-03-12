package org.dreamhorizon.pulseserver.service.notification.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.notification.EmailSuppressionDao;
import org.dreamhorizon.pulseserver.dao.notification.NotificationLogDao;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.notification.models.SuppressionReason;

@Slf4j
@Singleton
public class SesWebhookHandler {

  private final EmailSuppressionDao suppressionDao;
  private final NotificationLogDao logDao;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  @Inject
  public SesWebhookHandler(
      EmailSuppressionDao suppressionDao, NotificationLogDao logDao, ObjectMapper objectMapper) {
    this.suppressionDao = suppressionDao;
    this.logDao = logDao;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  public Single<WebhookResult> handleSnsNotification(String requestBody) {
    return Single.fromCallable(
        () -> {
          try {
            JsonNode payload = objectMapper.readTree(requestBody);
            String type = payload.has("Type") ? payload.get("Type").asText() : null;

            if ("SubscriptionConfirmation".equals(type)) {
              return handleSubscriptionConfirmation(payload);
            } else if ("Notification".equals(type)) {
              return handleNotification(payload);
            } else {
              log.warn("Unknown SNS message type: {}", type);
              return new WebhookResult(false, "Unknown message type");
            }
          } catch (Exception e) {
            log.error("Failed to process SNS notification", e);
            return new WebhookResult(false, "Failed to process: " + e.getMessage());
          }
        });
  }

  private WebhookResult handleSubscriptionConfirmation(JsonNode payload) {
    try {
      String subscribeUrl =
          payload.has("SubscribeURL") ? payload.get("SubscribeURL").asText() : null;

      if (subscribeUrl != null) {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(subscribeUrl))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          log.info("Successfully confirmed SNS subscription");
          return new WebhookResult(true, "Subscription confirmed");
        } else {
          log.error("Failed to confirm SNS subscription: {}", response.statusCode());
          return new WebhookResult(false, "Subscription confirmation failed");
        }
      }

      return new WebhookResult(false, "No SubscribeURL in confirmation");
    } catch (Exception e) {
      log.error("Error confirming SNS subscription", e);
      return new WebhookResult(false, "Subscription confirmation error");
    }
  }

  private WebhookResult handleNotification(JsonNode payload) {
    try {
      String messageStr = payload.has("Message") ? payload.get("Message").asText() : null;
      if (messageStr == null) {
        return new WebhookResult(false, "No message in notification");
      }

      JsonNode message = objectMapper.readTree(messageStr);
      String notificationType =
          message.has("notificationType") ? message.get("notificationType").asText() : null;

      if (notificationType == null) {
        log.warn("No notification type in SES message");
        return new WebhookResult(false, "Missing notification type");
      }

      return switch (notificationType) {
        case "Bounce" -> handleBounce(message);
        case "Complaint" -> handleComplaint(message);
        case "Delivery" -> handleDelivery(message);
        default -> {
          log.debug("Unhandled SES notification type: {}", notificationType);
          yield new WebhookResult(true, "Ignored: " + notificationType);
        }
      };
    } catch (Exception e) {
      log.error("Error handling SES notification", e);
      return new WebhookResult(false, "Notification handling error");
    }
  }

  private WebhookResult handleBounce(JsonNode message) {
    try {
      JsonNode bounce = message.get("bounce");
      String bounceType = bounce.has("bounceType") ? bounce.get("bounceType").asText() : "Unknown";
      String bounceSubType =
          bounce.has("bounceSubType") ? bounce.get("bounceSubType").asText() : null;

      JsonNode mail = message.get("mail");
      String messageId = mail.has("messageId") ? mail.get("messageId").asText() : null;

      JsonNode bouncedRecipients = bounce.get("bouncedRecipients");
      if (bouncedRecipients != null && bouncedRecipients.isArray()) {
        for (JsonNode recipient : bouncedRecipients) {
          String email =
              recipient.has("emailAddress") ? recipient.get("emailAddress").asText() : null;

          if (email != null) {
            if ("Permanent".equals(bounceType)) {
              suppressEmail(email, SuppressionReason.BOUNCE, bounceSubType, messageId);
              log.info("Added {} to suppression list due to hard bounce", email);
            } else {
              log.info("Soft bounce for {}: {}/{}", email, bounceType, bounceSubType);
            }
          }
        }
      }

      if (messageId != null) {
        NotificationStatus status =
            "Permanent".equals(bounceType) ? NotificationStatus.BOUNCED : NotificationStatus.SENT;
        updateLogStatus(messageId, status, "Bounce: " + bounceType + "/" + bounceSubType);
      }

      return new WebhookResult(true, "Bounce processed");
    } catch (Exception e) {
      log.error("Error processing bounce", e);
      return new WebhookResult(false, "Bounce processing error");
    }
  }

  private WebhookResult handleComplaint(JsonNode message) {
    try {
      JsonNode complaint = message.get("complaint");
      String complaintType =
          complaint.has("complaintFeedbackType")
              ? complaint.get("complaintFeedbackType").asText()
              : "Unknown";

      JsonNode mail = message.get("mail");
      String messageId = mail.has("messageId") ? mail.get("messageId").asText() : null;

      JsonNode complainedRecipients = complaint.get("complainedRecipients");
      if (complainedRecipients != null && complainedRecipients.isArray()) {
        for (JsonNode recipient : complainedRecipients) {
          String email =
              recipient.has("emailAddress") ? recipient.get("emailAddress").asText() : null;

          if (email != null) {
            suppressEmail(email, SuppressionReason.COMPLAINT, complaintType, messageId);
            log.info("Added {} to suppression list due to complaint", email);
          }
        }
      }

      if (messageId != null) {
        updateLogStatus(messageId, NotificationStatus.COMPLAINED, "Complaint: " + complaintType);
      }

      return new WebhookResult(true, "Complaint processed");
    } catch (Exception e) {
      log.error("Error processing complaint", e);
      return new WebhookResult(false, "Complaint processing error");
    }
  }

  private WebhookResult handleDelivery(JsonNode message) {
    try {
      JsonNode mail = message.get("mail");
      String messageId = mail.has("messageId") ? mail.get("messageId").asText() : null;

      if (messageId != null) {
        updateLogStatus(messageId, NotificationStatus.DELIVERED, "Successfully delivered");
        log.debug("Delivery confirmed for message: {}", messageId);
      }

      return new WebhookResult(true, "Delivery processed");
    } catch (Exception e) {
      log.error("Error processing delivery", e);
      return new WebhookResult(false, "Delivery processing error");
    }
  }

  private void suppressEmail(
      String email, SuppressionReason reason, String subType, String messageId) {
    try {
      suppressionDao
          .addToSuppressionListAllProjects(email, reason, subType, messageId)
          .blockingGet();
    } catch (Exception e) {
      log.error("Failed to add {} to suppression list", email, e);
    }
  }

  private void updateLogStatus(String externalId, NotificationStatus status, String message) {
    try {
      logDao.updateLogStatusByExternalId(externalId, status, message).blockingGet();
    } catch (Exception e) {
      log.warn("Failed to update log status for externalId {}: {}", externalId, e.getMessage());
    }
  }

  public record WebhookResult(boolean success, String message) {}
}
