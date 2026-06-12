package org.dreamhorizon.pulseserver.service.notification.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.reactivex.rxjava3.core.Single;
import java.net.InetSocketAddress;
import org.dreamhorizon.pulseserver.dao.notification.EmailSuppressionDao;
import org.dreamhorizon.pulseserver.dao.notification.NotificationLogDao;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.notification.models.SuppressionReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SesWebhookHandlerTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock
  EmailSuppressionDao suppressionDao;

  @Mock
  NotificationLogDao logDao;

  SesWebhookHandler handler;

  HttpServer httpServer;

  @BeforeEach
  void setUp() {
    handler = new SesWebhookHandler(suppressionDao, logDao, OBJECT_MAPPER);
  }

  @AfterEach
  void tearDown() {
    if (httpServer != null) {
      httpServer.stop(0);
    }
  }

  @Nested
  class SubscriptionConfirmation {

    @Test
    void shouldConfirmSubscriptionSuccessfully() throws Exception {
      httpServer = HttpServer.create(new InetSocketAddress(0), 0);
      httpServer.createContext("/confirm", exchange -> {
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
      });
      httpServer.start();
      int port = ((InetSocketAddress) httpServer.getAddress()).getPort();
      String subscribeUrl = "http://localhost:" + port + "/confirm";

      String payload =
          String.format("{\"Type\":\"SubscriptionConfirmation\",\"SubscribeURL\":\"%s\"}", subscribeUrl);

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
      assertThat(result.message()).isEqualTo("Subscription confirmed");
    }

    @Test
    void shouldReturnFailureWhenSubscribeUrlMissing() {
      String payload = "{\"Type\":\"SubscriptionConfirmation\"}";

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isFalse();
      assertThat(result.message()).isEqualTo("No SubscribeURL in confirmation");
    }

    @Test
    void shouldReturnFailureWhenSubscriptionConfirmationReturnsNon2xx() throws Exception {
      httpServer = HttpServer.create(new InetSocketAddress(0), 0);
      httpServer.createContext("/fail", exchange -> {
        exchange.sendResponseHeaders(500, -1);
        exchange.close();
      });
      httpServer.start();
      int port = ((InetSocketAddress) httpServer.getAddress()).getPort();
      String subscribeUrl = "http://localhost:" + port + "/fail";

      String payload =
          String.format("{\"Type\":\"SubscriptionConfirmation\",\"SubscribeURL\":\"%s\"}", subscribeUrl);

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isFalse();
      assertThat(result.message()).isEqualTo("Subscription confirmation failed");
    }

    @Test
    void shouldReturnFailureWhenTypeIsNull() {
      String payload = "{}";

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isFalse();
      assertThat(result.message()).isEqualTo("Unknown message type");
    }

    @Test
    void shouldReturnFailureWhenSubscriptionRequestFails() {
      String payload =
          "{\"Type\":\"SubscriptionConfirmation\",\"SubscribeURL\":\"http://127.0.0.1:65535/confirm\"}";

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isFalse();
      assertThat(result.message()).isEqualTo("Subscription confirmation error");
    }
  }

  @Nested
  class HandleSnsNotification {

    @Test
    void shouldReturnUnknownMessageTypeForUnknownType() {
      String payload = "{\"Type\":\"UnknownType\"}";

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isFalse();
      assertThat(result.message()).isEqualTo("Unknown message type");
    }

    @Test
    void shouldReturnFailureForInvalidJson() {
      var result = handler.handleSnsNotification("not-json").blockingGet();

      assertThat(result.success()).isFalse();
      assertThat(result.message()).startsWith("Failed to process:");
    }

    @Test
    void shouldReturnFailureWhenNoMessageInNotification() {
      String payload = "{\"Type\": \"Notification\"}";

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isFalse();
      assertThat(result.message()).isEqualTo("No message in notification");
    }

    @Test
    void shouldReturnFailureWhenMessageNotValidJson() {
      String payload = "{\"Type\": \"Notification\", \"Message\": \"invalid-json\"}";

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isFalse();
      assertThat(result.message()).isEqualTo("Notification handling error");
    }

    @Test
    void shouldReturnFailureWhenNotificationTypeMissing() {
      String payload = "{\"Type\": \"Notification\", \"Message\": \"{\\\"mail\\\": {}}\"}";

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isFalse();
      assertThat(result.message()).isEqualTo("Missing notification type");
    }

    @Test
    void shouldHandleDeliveryNotification() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Delivery\\\", "
              + "\\\"mail\\\": {\\\"messageId\\\": \\\"msg-123\\\"}}\"}";

      when(logDao.updateLogStatusByExternalId(eq("msg-123"), eq(NotificationStatus.DELIVERED), eq("Successfully delivered")))
          .thenReturn(Single.just(1));

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
      assertThat(result.message()).isEqualTo("Delivery processed");
      verify(logDao).updateLogStatusByExternalId("msg-123", NotificationStatus.DELIVERED, "Successfully delivered");
    }

    @Test
    void shouldHandlePermanentBounceAndSuppressEmail() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Bounce\\\", "
              + "\\\"bounce\\\": {\\\"bounceType\\\": \\\"Permanent\\\", \\\"bounceSubType\\\": \\\"General\\\", "
              + "\\\"bouncedRecipients\\\": [{\\\"emailAddress\\\": \\\"bounced@test.com\\\"}]}, "
              + "\\\"mail\\\": {\\\"messageId\\\": \\\"msg-456\\\"}}\"}";

      when(suppressionDao.addToSuppressionListAllProjects(
          eq("bounced@test.com"),
          eq(SuppressionReason.BOUNCE),
          eq("General"),
          eq("msg-456")))
          .thenReturn(Single.just(true));
      when(logDao.updateLogStatusByExternalId(
          eq("msg-456"),
          eq(NotificationStatus.BOUNCED),
          eq("Bounce: Permanent/General")))
          .thenReturn(Single.just(1));

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
      assertThat(result.message()).isEqualTo("Bounce processed");
      verify(suppressionDao).addToSuppressionListAllProjects(
          "bounced@test.com", SuppressionReason.BOUNCE, "General", "msg-456");
      verify(logDao).updateLogStatusByExternalId("msg-456", NotificationStatus.BOUNCED, "Bounce: Permanent/General");
    }

    @Test
    void shouldHandleSoftBounceWithoutSuppression() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Bounce\\\", "
              + "\\\"bounce\\\": {\\\"bounceType\\\": \\\"Transient\\\", \\\"bounceSubType\\\": \\\"General\\\", "
              + "\\\"bouncedRecipients\\\": [{\\\"emailAddress\\\": \\\"soft@test.com\\\"}]}, "
              + "\\\"mail\\\": {\\\"messageId\\\": \\\"msg-789\\\"}}\"}";

      when(logDao.updateLogStatusByExternalId(
          eq("msg-789"),
          eq(NotificationStatus.SENT),
          eq("Bounce: Transient/General")))
          .thenReturn(Single.just(1));

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
      verify(suppressionDao, never()).addToSuppressionListAllProjects(any(), any(), any(), any());
      verify(logDao).updateLogStatusByExternalId("msg-789", NotificationStatus.SENT, "Bounce: Transient/General");
    }

    @Test
    void shouldHandleComplaintAndSuppressEmail() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Complaint\\\", "
              + "\\\"complaint\\\": {\\\"complaintFeedbackType\\\": \\\"abuse\\\", "
              + "\\\"complainedRecipients\\\": [{\\\"emailAddress\\\": \\\"complained@test.com\\\"}]}, "
              + "\\\"mail\\\": {\\\"messageId\\\": \\\"msg-999\\\"}}\"}";

      when(suppressionDao.addToSuppressionListAllProjects(
          eq("complained@test.com"),
          eq(SuppressionReason.COMPLAINT),
          eq("abuse"),
          eq("msg-999")))
          .thenReturn(Single.just(true));
      when(logDao.updateLogStatusByExternalId(
          eq("msg-999"),
          eq(NotificationStatus.COMPLAINED),
          eq("Complaint: abuse")))
          .thenReturn(Single.just(1));

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
      assertThat(result.message()).isEqualTo("Complaint processed");
      verify(suppressionDao).addToSuppressionListAllProjects(
          "complained@test.com", SuppressionReason.COMPLAINT, "abuse", "msg-999");
      verify(logDao).updateLogStatusByExternalId("msg-999", NotificationStatus.COMPLAINED, "Complaint: abuse");
    }

    @Test
    void shouldIgnoreUnhandledNotificationType() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Open\\\", \\\"mail\\\": {}}\"}";

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
      assertThat(result.message()).isEqualTo("Ignored: Open");
    }

    @Test
    void shouldHandleBounceWithNullBouncedRecipients() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Bounce\\\", "
              + "\\\"bounce\\\": {\\\"bounceType\\\": \\\"Permanent\\\", \\\"bouncedRecipients\\\": null}, "
              + "\\\"mail\\\": {\\\"messageId\\\": \\\"msg-1\\\"}}\"}";

      when(logDao.updateLogStatusByExternalId(eq("msg-1"), eq(NotificationStatus.BOUNCED), any()))
          .thenReturn(Single.just(1));

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
      assertThat(result.message()).isEqualTo("Bounce processed");
      verify(suppressionDao, never()).addToSuppressionListAllProjects(any(), any(), any(), any());
    }

    @Test
    void shouldHandleBounceWithRecipientMissingEmailAddress() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Bounce\\\", "
              + "\\\"bounce\\\": {\\\"bounceType\\\": \\\"Permanent\\\", "
              + "\\\"bouncedRecipients\\\": [{\\\"status\\\": \\\"5.1.1\\\"}]}, "
              + "\\\"mail\\\": {\\\"messageId\\\": \\\"msg-2\\\"}}\"}";

      when(logDao.updateLogStatusByExternalId(eq("msg-2"), eq(NotificationStatus.BOUNCED), any()))
          .thenReturn(Single.just(1));

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
      verify(suppressionDao, never()).addToSuppressionListAllProjects(any(), any(), any(), any());
    }

    @Test
    void shouldHandleDeliveryWithNullMessageId() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Delivery\\\", "
              + "\\\"mail\\\": {}}\"}";

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
      assertThat(result.message()).isEqualTo("Delivery processed");
      verify(logDao, never()).updateLogStatusByExternalId(any(), any(), any());
    }

    @Test
    void shouldContinueWhenSuppressionDaoThrows() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Bounce\\\", "
              + "\\\"bounce\\\": {\\\"bounceType\\\": \\\"Permanent\\\", \\\"bounceSubType\\\": \\\"General\\\", "
              + "\\\"bouncedRecipients\\\": [{\\\"emailAddress\\\": \\\"fail@test.com\\\"}]}, "
              + "\\\"mail\\\": {\\\"messageId\\\": \\\"msg-fail\\\"}}\"}";

      when(suppressionDao.addToSuppressionListAllProjects(
          eq("fail@test.com"),
          eq(SuppressionReason.BOUNCE),
          eq("General"),
          eq("msg-fail")))
          .thenReturn(Single.error(new RuntimeException("DB error")));
      when(logDao.updateLogStatusByExternalId(eq("msg-fail"), eq(NotificationStatus.BOUNCED), any()))
          .thenReturn(Single.just(1));

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
      assertThat(result.message()).isEqualTo("Bounce processed");
    }

    @Test
    void shouldContinueWhenLogDaoThrows() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Delivery\\\", "
              + "\\\"mail\\\": {\\\"messageId\\\": \\\"msg-log-fail\\\"}}\"}";

      when(logDao.updateLogStatusByExternalId(eq("msg-log-fail"), eq(NotificationStatus.DELIVERED), any()))
          .thenReturn(Single.error(new RuntimeException("DB error")));

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
      assertThat(result.message()).isEqualTo("Delivery processed");
    }

    @Test
    void shouldHandleBounceWithMissingBounceSubType() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Bounce\\\", "
              + "\\\"bounce\\\": {\\\"bounceType\\\": \\\"Permanent\\\", "
              + "\\\"bouncedRecipients\\\": [{\\\"emailAddress\\\": \\\"bounced@test.com\\\"}]}, "
              + "\\\"mail\\\": {\\\"messageId\\\": \\\"msg-null\\\"}}\"}";

      when(suppressionDao.addToSuppressionListAllProjects(
          eq("bounced@test.com"),
          eq(SuppressionReason.BOUNCE),
          eq(null),
          eq("msg-null")))
          .thenReturn(Single.just(true));
      when(logDao.updateLogStatusByExternalId(eq("msg-null"), eq(NotificationStatus.BOUNCED), any()))
          .thenReturn(Single.just(1));

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
    }

    @Test
    void shouldHandleComplaintWithDefaultFeedbackType() {
      String payload =
          "{\"Type\": \"Notification\", \"Message\": \"{\\\"notificationType\\\": \\\"Complaint\\\", "
              + "\\\"complaint\\\": {\\\"complainedRecipients\\\": [{\\\"emailAddress\\\": \\\"c@test.com\\\"}]}, "
              + "\\\"mail\\\": {\\\"messageId\\\": \\\"msg-complaint\\\"}}\"}";

      when(suppressionDao.addToSuppressionListAllProjects(
          eq("c@test.com"),
          eq(SuppressionReason.COMPLAINT),
          eq("Unknown"),
          eq("msg-complaint")))
          .thenReturn(Single.just(true));
      when(logDao.updateLogStatusByExternalId(eq("msg-complaint"), eq(NotificationStatus.COMPLAINED), any()))
          .thenReturn(Single.just(1));

      var result = handler.handleSnsNotification(payload).blockingGet();

      assertThat(result.success()).isTrue();
    }
  }

  @Nested
  class WebhookResultRecord {

    @Test
    void shouldCreateWebhookResult() {
      var result = new SesWebhookHandler.WebhookResult(true, "OK");

      assertThat(result.success()).isTrue();
      assertThat(result.message()).isEqualTo("OK");
    }
  }
}
