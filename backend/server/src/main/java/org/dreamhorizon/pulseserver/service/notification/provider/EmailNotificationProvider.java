package org.dreamhorizon.pulseserver.service.notification.provider;

import static org.dreamhorizon.pulseserver.constant.NotificationConstants.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.service.notification.TemplateService;
import org.dreamhorizon.pulseserver.service.notification.models.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Slf4j
@Singleton
public class EmailNotificationProvider implements NotificationProvider {

  private final SesClient sesClient;
  private final ObjectMapper objectMapper;
  private final TemplateService templateService;
  private final NotificationConfig notificationConfig;

  @Inject
  public EmailNotificationProvider(
      ObjectMapper objectMapper,
      TemplateService templateService,
      NotificationConfig notificationConfig) {
    this.objectMapper = objectMapper;
    this.templateService = templateService;
    this.notificationConfig = notificationConfig;

    this.sesClient = SesClient.builder()
        .region(Region.of(notificationConfig.getRegion()))
        .httpClient(UrlConnectionHttpClient.builder().build())
        .build();

    log.info(
        "Email notification provider initialized with region: {}", notificationConfig.getRegion());
  }

  @Override
  public ChannelType getChannelType() {
    return ChannelType.EMAIL;
  }

  @Override
  public Single<NotificationResult> send(
      NotificationMessage message, NotificationTemplate template) {
    return Single.fromCallable(
        () -> {
          long startTime = System.currentTimeMillis();

          try {
            EmailChannelConfig config =
                objectMapper.readValue(message.getChannelConfig(), EmailChannelConfig.class);

            EmailContent emailContent = parseEmailContent(template.getBody(), message.getParams());

            Body.Builder bodyBuilder = Body.builder();
            if (emailContent.html != null) {
              bodyBuilder.html(
                  Content.builder().data(emailContent.html).charset(DEFAULT_CHARSET).build());
            }
            if (emailContent.text != null) {
              bodyBuilder.text(
                  Content.builder().data(emailContent.text).charset(DEFAULT_CHARSET).build());
            }

            SendEmailRequest.Builder requestBuilder =
                SendEmailRequest.builder()
                    .destination(Destination.builder().toAddresses(message.getRecipient()).build())
                    .message(
                        Message.builder()
                            .subject(
                                Content.builder()
                                    .data(emailContent.subject)
                                    .charset(DEFAULT_CHARSET)
                                    .build())
                            .body(bodyBuilder.build())
                            .build())
                    .source(formatSender(config));

            if (config.getReplyToAddress() != null) {
              requestBuilder.replyToAddresses(config.getReplyToAddress());
            }

            String configSetName = config.getConfigurationSetName();
            if (configSetName == null && notificationConfig.getSes() != null) {
              configSetName = notificationConfig.getSes().getConfigurationSetName();
            }
            if (configSetName != null) {
              requestBuilder.configurationSetName(configSetName);
            }

            SendEmailResponse response = sesClient.sendEmail(requestBuilder.build());
            long latency = System.currentTimeMillis() - startTime;

            return NotificationResult.builder()
                .success(true)
                .externalId(response.messageId())
                .latencyMs(latency)
                .build();

          } catch (SesException e) {
            long latency = System.currentTimeMillis() - startTime;
            boolean isPermanent = isPermanentFailure(e);

            log.error("SES error sending email to {}: {}", message.getRecipient(), e.getMessage());

            return NotificationResult.builder()
                .success(false)
                .errorCode(e.awsErrorDetails().errorCode())
                .errorMessage(e.getMessage())
                .permanentFailure(isPermanent)
                .latencyMs(latency)
                .build();

          } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Error sending email to {}", message.getRecipient(), e);

            return NotificationResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .permanentFailure(false)
                .latencyMs(latency)
                .build();
          }
        });
  }

  private String formatSender(EmailChannelConfig config) {
    if (config.getFromName() != null && !config.getFromName().isEmpty()) {
      return String.format("%s <%s>", config.getFromName(), config.getFromAddress());
    }
    return config.getFromAddress();
  }

  private boolean isPermanentFailure(SesException e) {
    String errorCode = e.awsErrorDetails().errorCode();
    return Email.PERMANENT_ERROR_CODES.contains(errorCode);
  }

  private EmailContent parseEmailContent(String bodyJson, java.util.Map<String, Object> params) {
    try {
      JsonNode body = objectMapper.readTree(bodyJson);

      String subject = body.has(KEY_SUBJECT) ? body.get(KEY_SUBJECT).asText() : DEFAULT_SUBJECT;
      String html = body.has(KEY_HTML) ? body.get(KEY_HTML).asText() : null;
      String text = body.has(KEY_TEXT) ? body.get(KEY_TEXT).asText() : null;

      String renderedSubject = templateService.renderText(subject, params);
      String renderedHtml = html != null ? templateService.renderText(html, params) : null;
      String renderedText = text != null ? templateService.renderText(text, params) : null;

      if (renderedHtml == null && renderedText == null) {
        renderedHtml = templateService.renderText(bodyJson, params);
      }

      return new EmailContent(renderedSubject, renderedHtml, renderedText);
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse email template body as JSON, using as plain HTML", e);
      String rendered = templateService.renderText(bodyJson, params);
      return new EmailContent(DEFAULT_SUBJECT, rendered, null);
    }
  }

  private record EmailContent(String subject, String html, String text) {}
}
