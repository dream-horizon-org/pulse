package org.dreamhorizon.pulseserver.service.notification.provider;

import static org.dreamhorizon.pulseserver.constant.NotificationConstants.*;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.error.ServiceError;
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
  private final TemplateService templateService;
  private final NotificationConfig notificationConfig;

  @Inject
  public EmailNotificationProvider(
      TemplateService templateService,
      NotificationConfig notificationConfig) {
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
            if (!(message.getChannelConfig() instanceof EmailChannelConfig config)) {
              throw ServiceError.INCORRECT_OR_MISSING_HEADER_PARAMETERS.getCustomException("Expected EmailChannelConfig but got:"+ (message.getChannelConfig() != null ? message.getChannelConfig().getClass().getSimpleName() : "null"));
            }

            if (!(template.getBody() instanceof EmailTemplateBody emailBody)) {
              throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
                  "Expected EmailTemplateBody but got: "
                      + (template.getBody() != null ? template.getBody().getClass().getSimpleName() : "null"));
            }

            String subject = templateService.renderText(
                emailBody.getSubject() != null ? emailBody.getSubject() : DEFAULT_SUBJECT,
                message.getParams());

            String html = emailBody.getHtml() != null
                ? templateService.renderText(emailBody.getHtml(), message.getParams())
                : null;
            String text = emailBody.getText() != null
                ? templateService.renderText(emailBody.getText(), message.getParams())
                : null;

            Body.Builder bodyBuilder = Body.builder();
            if (html != null) {
              bodyBuilder.html(
                  Content.builder().data(html).charset(DEFAULT_CHARSET).build());
            }
            if (text != null) {
              bodyBuilder.text(
                  Content.builder().data(text).charset(DEFAULT_CHARSET).build());
            }

            SendEmailRequest.Builder requestBuilder =
                SendEmailRequest.builder()
                    .destination(Destination.builder().toAddresses(message.getRecipient()).build())
                    .message(
                        Message.builder()
                            .subject(
                                Content.builder()
                                    .data(subject)
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
}
