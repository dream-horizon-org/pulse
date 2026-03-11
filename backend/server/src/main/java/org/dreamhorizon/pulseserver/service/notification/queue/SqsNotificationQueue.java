package org.dreamhorizon.pulseserver.service.notification.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationMessage;
import org.dreamhorizon.pulseserver.service.notification.models.QueuedNotification;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

@Slf4j
@Singleton
public class SqsNotificationQueue {

  private final SqsClient sqsClient;
  private final ObjectMapper objectMapper;
  private final NotificationConfig config;

  @Inject
  public SqsNotificationQueue(ObjectMapper objectMapper, NotificationConfig config) {
    this.objectMapper = objectMapper;
    this.config = config;

    this.sqsClient = SqsClient.builder()
        .region(Region.of(config.getRegion()))
        .httpClient(UrlConnectionHttpClient.builder().build())
        .build();

    if (!config.isSqsEnabled()) {
      log.warn("SQS queue URL not configured - async notifications disabled");
    } else {
      log.info("SQS notification queue initialized: {}", config.getSqs().getQueueUrl());
    }
  }

  public Single<String> enqueue(NotificationMessage message) {
    return Single.fromCallable(
        () -> {
          if (!config.isSqsEnabled()) {
            throw new IllegalStateException("Notification queue URL not configured");
          }

          String queueUrl = config.getSqs().getQueueUrl();
          String messageBody = objectMapper.writeValueAsString(message);

          SendMessageRequest.Builder requestBuilder =
              SendMessageRequest.builder()
                  .queueUrl(queueUrl)
                  .messageBody(messageBody)
                  .messageAttributes(buildMessageAttributes(message));

          SendMessageResponse response = sqsClient.sendMessage(requestBuilder.build());
          log.debug("Enqueued notification message: {}", response.messageId());
          return response.messageId();
        });
  }

  public Single<List<QueuedNotification>> receiveMessages(
      int maxMessages, int visibilityTimeoutSeconds) {
    return Single.fromCallable(
        () -> {
          if (!config.isSqsEnabled()) {
            return List.<QueuedNotification>of();
          }

          ReceiveMessageRequest request =
              ReceiveMessageRequest.builder()
                  .queueUrl(config.getSqs().getQueueUrl())
                  .maxNumberOfMessages(Math.min(maxMessages, 10))
                  .visibilityTimeout(visibilityTimeoutSeconds)
                  .waitTimeSeconds(config.getSqs().getWaitTimeSeconds())
                  .messageAttributeNames("All")
                  .attributeNames(QueueAttributeName.ALL)
                  .build();

          ReceiveMessageResponse response = sqsClient.receiveMessage(request);

          return response.messages().stream()
              .map(this::toQueuedNotification)
              .filter(qn -> qn != null)
              .collect(Collectors.toList());
        });
  }

  public Completable deleteMessage(String receiptHandle) {
    return Completable.fromAction(
        () -> {
          if (!config.isSqsEnabled()) {
            return;
          }

          DeleteMessageRequest request =
              DeleteMessageRequest.builder()
                  .queueUrl(config.getSqs().getQueueUrl())
                  .receiptHandle(receiptHandle)
                  .build();

          sqsClient.deleteMessage(request);
          log.debug("Deleted message with receipt handle: {}", receiptHandle);
        });
  }

  public Completable changeMessageVisibility(String receiptHandle, int visibilityTimeoutSeconds) {
    return Completable.fromAction(
        () -> {
          if (!config.isSqsEnabled()) {
            return;
          }

          ChangeMessageVisibilityRequest request =
              ChangeMessageVisibilityRequest.builder()
                  .queueUrl(config.getSqs().getQueueUrl())
                  .receiptHandle(receiptHandle)
                  .visibilityTimeout(visibilityTimeoutSeconds)
                  .build();

          sqsClient.changeMessageVisibility(request);
        });
  }

  public Single<List<QueuedNotification>> receiveDlqMessages(int maxMessages) {
    return Single.fromCallable(
        () -> {
          if (!config.isDlqEnabled()) {
            log.warn("DLQ URL not configured");
            return List.<QueuedNotification>of();
          }

          ReceiveMessageRequest request =
              ReceiveMessageRequest.builder()
                  .queueUrl(config.getSqs().getDlqUrl())
                  .maxNumberOfMessages(Math.min(maxMessages, 10))
                  .waitTimeSeconds(5)
                  .messageAttributeNames("All")
                  .attributeNames(QueueAttributeName.ALL)
                  .build();

          ReceiveMessageResponse response = sqsClient.receiveMessage(request);

          return response.messages().stream()
              .map(this::toQueuedNotification)
              .filter(qn -> qn != null)
              .collect(Collectors.toList());
        });
  }

  public Completable deleteDlqMessage(String receiptHandle) {
    return Completable.fromAction(
        () -> {
          if (!config.isDlqEnabled()) {
            return;
          }

          DeleteMessageRequest request =
              DeleteMessageRequest.builder()
                  .queueUrl(config.getSqs().getDlqUrl())
                  .receiptHandle(receiptHandle)
                  .build();

          sqsClient.deleteMessage(request);
        });
  }

  public boolean isEnabled() {
    return config.isSqsEnabled();
  }

  public int getVisibilityTimeoutSeconds() {
    return config.getSqs() != null ? config.getSqs().getVisibilityTimeoutSeconds() : 300;
  }

  private Map<String, MessageAttributeValue> buildMessageAttributes(NotificationMessage message) {
    return Map.of(
        "projectId",
            MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(message.getProjectId())
                .build(),
        "channelType",
            MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(message.getChannelType().name())
                .build(),
        "recipient",
            MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(message.getRecipient())
                .build());
  }

  private String buildDeduplicationId(NotificationMessage message) {
    return String.format(
        "%s-%s-%s-%d",
        message.getProjectId(),
        message.getChannelType().name(),
        message.getRecipient(),
        message.getLogId() != null ? message.getLogId() : System.currentTimeMillis());
  }

  private QueuedNotification toQueuedNotification(Message sqsMessage) {
    try {
      NotificationMessage message =
          objectMapper.readValue(sqsMessage.body(), NotificationMessage.class);

      int receiveCount = 1;
      if (sqsMessage
          .attributes()
          .containsKey(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)) {
        receiveCount =
            Integer.parseInt(
                sqsMessage.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT));
      }

      return QueuedNotification.builder()
          .message(message)
          .receiptHandle(sqsMessage.receiptHandle())
          .messageId(sqsMessage.messageId())
          .receiveCount(receiveCount)
          .build();
    } catch (JsonProcessingException e) {
      log.error("Failed to deserialize notification message: {}", sqsMessage.body(), e);
      return null;
    }
  }
}
