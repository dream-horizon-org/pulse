package org.dreamhorizon.pulseserver.service.notification.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.service.notification.TemplateService;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.EmailChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.EmailTemplateBody;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationMessage;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationResult;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailNotificationProviderTest {

  @Mock
  SesClient sesClient;

  @Mock
  TemplateService templateService;

  @Mock
  NotificationConfig notificationConfig;

  EmailNotificationProvider provider;

  @BeforeEach
  void setUp() {
    when(templateService.renderText(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
    when(notificationConfig.getRegion()).thenReturn("us-east-1");
    provider = new EmailNotificationProvider(templateService, notificationConfig, sesClient);
  }

  @Nested
  class GetChannelType {

    @Test
    void shouldReturnEmailChannelType() {
      assertThat(provider.getChannelType()).isEqualTo(ChannelType.EMAIL);
    }
  }

  @Nested
  class Send {

    @Test
    void shouldSendEmailSuccessfully() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.EMAIL)
          .channelConfig(EmailChannelConfig.builder()
              .fromAddress("noreply@example.com")
              .fromName("Pulse")
              .build())
          .recipient("user@example.com")
          .params(Map.of("name", "User"))
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(EmailTemplateBody.builder()
              .subject("Test Subject")
              .html("<p>Hello {{name}}</p>")
              .text("Hello {{name}}")
              .build())
          .build();

      when(sesClient.sendEmail(any(SendEmailRequest.class)))
          .thenReturn(SendEmailResponse.builder().messageId("msg-123").build());

      NotificationResult result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getExternalId()).isEqualTo("msg-123");
      assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldRenderTemplateWithParams() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .channelConfig(EmailChannelConfig.builder()
              .fromAddress("noreply@example.com")
              .fromName("Pulse")
              .build())
          .recipient("user@example.com")
          .params(Map.of("name", "Alice"))
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(EmailTemplateBody.builder()
              .subject("Hi {{name}}")
              .html("<p>Hello {{name}}</p>")
              .build())
          .build();

      when(templateService.renderText("Hi {{name}}", Map.of("name", "Alice"))).thenReturn("Hi Alice");
      when(templateService.renderText("<p>Hello {{name}}</p>", Map.of("name", "Alice")))
          .thenReturn("<p>Hello Alice</p>");
      when(sesClient.sendEmail(any(SendEmailRequest.class)))
          .thenReturn(SendEmailResponse.builder().messageId("msg-456").build());

      NotificationResult result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldReturnErrorWhenChannelConfigInvalid() {
      NotificationMessage message = NotificationMessage.builder()
          .channelConfig(null)
          .recipient("user@example.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(EmailTemplateBody.builder()
              .subject("Hi")
              .html("<p>Hello</p>")
              .build())
          .build();

      NotificationResult result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void shouldHandlePlainTextTemplateWhenJsonParseFails() {
      NotificationMessage message = NotificationMessage.builder()
          .channelConfig(EmailChannelConfig.builder()
              .fromAddress("noreply@example.com")
              .fromName("Pulse")
              .build())
          .recipient("user@example.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(null)
          .build();

      NotificationResult result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void shouldReturnFailureOnSesException() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .channelConfig(EmailChannelConfig.builder()
              .fromAddress("noreply@example.com")
              .fromName("Pulse")
              .build())
          .recipient("user@example.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(EmailTemplateBody.builder()
              .subject("Test Subject")
              .html("<p>Hello {{name}}</p>")
              .text("Hello {{name}}")
              .build())
          .build();

      when(sesClient.sendEmail(any(SendEmailRequest.class)))
          .thenThrow(SesException.builder()
              .message("MessageRejected")
              .awsErrorDetails(AwsErrorDetails.builder()
                  .errorCode("MessageRejected")
                  .errorMessage("Email rejected")
                  .build())
              .build());

      NotificationResult result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorCode()).isEqualTo("MessageRejected");
      assertThat(result.getErrorMessage()).contains("MessageRejected");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldReturnNonPermanentFailureOnTransientSesError() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .channelConfig(EmailChannelConfig.builder()
              .fromAddress("noreply@example.com")
              .fromName("Pulse")
              .build())
          .recipient("user@example.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(EmailTemplateBody.builder()
              .subject("Test Subject")
              .html("<p>Hello {{name}}</p>")
              .text("Hello {{name}}")
              .build())
          .build();

      when(sesClient.sendEmail(any(SendEmailRequest.class)))
          .thenThrow(SesException.builder()
              .message("ServiceUnavailable")
              .awsErrorDetails(AwsErrorDetails.builder()
                  .errorCode("ServiceUnavailable")
                  .errorMessage("Temporarily unavailable")
                  .build())
              .build());

      NotificationResult result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldReturnFailureOnGenericException() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .channelConfig(EmailChannelConfig.builder()
              .fromAddress("noreply@example.com")
              .fromName("Pulse")
              .build())
          .recipient("user@example.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(EmailTemplateBody.builder()
              .subject("Test Subject")
              .html("<p>Hello {{name}}</p>")
              .text("Hello {{name}}")
              .build())
          .build();

      when(sesClient.sendEmail(any(SendEmailRequest.class)))
          .thenThrow(new RuntimeException("Unexpected error"));

      NotificationResult result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Unexpected error");
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldIncludeReplyToAndConfigSetWhenProvided() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .channelConfig(EmailChannelConfig.builder()
              .fromAddress("noreply@example.com")
              .replyToAddress("support@example.com")
              .configurationSetName("my-config-set")
              .build())
          .recipient("user@example.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(EmailTemplateBody.builder()
              .subject("Hi")
              .html("<p>Hello</p>")
              .build())
          .build();

      when(sesClient.sendEmail(any(SendEmailRequest.class)))
          .thenReturn(SendEmailResponse.builder().messageId("msg-reply").build());

      NotificationResult result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldFormatSenderWithFromName() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .channelConfig(EmailChannelConfig.builder()
              .fromAddress("noreply@example.com")
              .fromName("Pulse Alerts")
              .build())
          .recipient("user@example.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(EmailTemplateBody.builder()
              .subject("Hi")
              .html("<p>Hello</p>")
              .build())
          .build();

      when(sesClient.sendEmail(any(SendEmailRequest.class)))
          .thenReturn(SendEmailResponse.builder().messageId("msg-sender").build());

      NotificationResult result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }
  }
}
