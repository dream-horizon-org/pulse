package org.dreamhorizon.pulseserver.service.notification.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.dao.notification.NotificationLogDao;
import org.dreamhorizon.pulseserver.dao.notification.NotificationTemplateDao;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationMessage;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationResult;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.notification.models.EmailTemplateBody;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate;
import org.dreamhorizon.pulseserver.service.notification.models.QueuedNotification;
import org.dreamhorizon.pulseserver.service.notification.models.SlackTemplateBody;
import org.dreamhorizon.pulseserver.service.notification.provider.NotificationProvider;
import org.dreamhorizon.pulseserver.service.notification.provider.NotificationProviderFactory;
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
class NotificationQueueTest {

  @Mock
  SqsNotificationQueue queue;

  @Mock
  NotificationProviderFactory providerFactory;

  @Mock
  NotificationTemplateDao templateDao;

  @Mock
  NotificationLogDao logDao;

  @Mock
  NotificationProvider notificationProvider;

  ObjectMapper objectMapper = new ObjectMapper();

  @Nested
  class NotificationWorkerTest {

    NotificationWorker worker;
    NotificationConfig config;

    @BeforeEach
    void setUp() {
      config = new NotificationConfig();
      config.setSqs(new NotificationConfig.SqsConfig());
      config.getSqs().setQueueUrl("https://sqs.us-east-1.amazonaws.com/123/queue");
      config.setWorker(new NotificationConfig.WorkerConfig());
      config.getWorker().setEnabled(true);
      config.getWorker().setBatchSize(5);
      config.getWorker().setVisibilityTimeoutSeconds(60);
      config.getWorker().setPollIntervalSeconds(1);
      config.setRetry(new NotificationConfig.RetryConfig());
      config.getRetry().setMaxAttempts(3);

      NotificationRetryPolicy retryPolicy = new NotificationRetryPolicy(config);
      worker = new NotificationWorker(
          queue, providerFactory, templateDao, logDao, retryPolicy, config);
    }

    @Test
    void shouldReturnNotRunningInitially() {
      assertThat(worker.isRunning()).isFalse();
    }

    @Test
    void shouldNotStartWhenQueueDisabled() {
      config.setSqs(null);
      NotificationRetryPolicy retryPolicy = new NotificationRetryPolicy(config);
      worker = new NotificationWorker(
          queue, providerFactory, templateDao, logDao, retryPolicy, config);

      worker.start();

      assertThat(worker.isRunning()).isFalse();
    }

    @Test
    void shouldNotStartWhenWorkerDisabled() {
      config.getWorker().setEnabled(false);
      NotificationRetryPolicy retryPolicy = new NotificationRetryPolicy(config);
      worker = new NotificationWorker(
          queue, providerFactory, templateDao, logDao, retryPolicy, config);

      worker.start();

      assertThat(worker.isRunning()).isFalse();
    }

    @Test
    void shouldStartAndStop() {
      when(queue.isEnabled()).thenReturn(true);

      worker.start();

      assertThat(worker.isRunning()).isTrue();

      worker.stop();

      assertThat(worker.isRunning()).isFalse();
    }

    @Test
    void shouldCompleteWhenNoMessagesReceived() throws Exception {
      when(queue.isEnabled()).thenReturn(true);
      when(queue.receiveMessages(eq(5), eq(60))).thenReturn(Single.just(Collections.emptyList()));

      worker.start();
      Thread.sleep(500);
      worker.stop();

      verify(queue).receiveMessages(5, 60);
    }

    @Test
    void shouldProcessMessageSuccessfullyAndDeleteFromQueue() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .recipient("user@test.com")
          .logId(100L)
          .templateId(1L)
          .build();
      QueuedNotification queuedNotification = QueuedNotification.builder()
          .message(message)
          .receiptHandle("rh-123")
          .messageId("msg-1")
          .receiveCount(1)
          .build();

      NotificationTemplate template = NotificationTemplate.builder()
          .id(1L)
          .channelType(ChannelType.SLACK)
          .body(SlackTemplateBody.builder().text("Hello").build())
          .build();
      NotificationResult successResult = NotificationResult.builder()
          .success(true)
          .externalId("ext-123")
          .build();

      when(queue.receiveMessages(eq(5), eq(60))).thenReturn(Single.just(List.of(queuedNotification)));
      when(templateDao.getTemplateById(eq(1L))).thenReturn(io.reactivex.rxjava3.core.Maybe.just(template));
      when(providerFactory.getProvider(eq(ChannelType.SLACK))).thenReturn(Optional.of(notificationProvider));
      when(notificationProvider.send(any(NotificationMessage.class), any(NotificationTemplate.class)))
          .thenReturn(Single.just(successResult));
      when(logDao.updateLogStatus(eq(100L), eq(NotificationStatus.SENT), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));
      when(queue.deleteMessage(eq("rh-123"))).thenReturn(Completable.complete());

      java.lang.reflect.Method pollAndProcess = NotificationWorker.class.getDeclaredMethod("pollAndProcess");
      pollAndProcess.setAccessible(true);
      ((io.reactivex.rxjava3.core.Completable) pollAndProcess.invoke(worker)).blockingAwait();

      verify(queue).deleteMessage("rh-123");
      verify(logDao).updateLogStatus(eq(100L), eq(NotificationStatus.SENT), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldHandleNoProviderWithPermanentFailure() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.EMAIL)
          .recipient("user@test.com")
          .logId(101L)
          .templateId(2L)
          .build();
      QueuedNotification queuedNotification = QueuedNotification.builder()
          .message(message)
          .receiptHandle("rh-456")
          .messageId("msg-2")
          .receiveCount(1)
          .build();

      NotificationTemplate template = NotificationTemplate.builder()
          .id(2L)
          .channelType(ChannelType.EMAIL)
          .body(EmailTemplateBody.builder().subject("Test").text("Hello").build())
          .build();

      when(queue.receiveMessages(eq(5), eq(60))).thenReturn(Single.just(List.of(queuedNotification)));
      when(templateDao.getTemplateById(eq(2L))).thenReturn(io.reactivex.rxjava3.core.Maybe.just(template));
      when(providerFactory.getProvider(eq(ChannelType.EMAIL))).thenReturn(Optional.empty());
      when(logDao.updateLogStatus(eq(101L), eq(NotificationStatus.PERMANENT_FAILURE), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));
      when(queue.deleteMessage(eq("rh-456"))).thenReturn(Completable.complete());

      java.lang.reflect.Method pollAndProcess = NotificationWorker.class.getDeclaredMethod("pollAndProcess");
      pollAndProcess.setAccessible(true);
      ((io.reactivex.rxjava3.core.Completable) pollAndProcess.invoke(worker)).blockingAwait();

      verify(queue).deleteMessage("rh-456");
      verify(logDao).updateLogStatus(eq(101L), eq(NotificationStatus.PERMANENT_FAILURE), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRetryOnTransientFailure() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .recipient("user@test.com")
          .logId(102L)
          .templateId(1L)
          .build();
      QueuedNotification queuedNotification = QueuedNotification.builder()
          .message(message)
          .receiptHandle("rh-789")
          .messageId("msg-3")
          .receiveCount(1)
          .build();

      NotificationTemplate template = NotificationTemplate.builder()
          .id(1L)
          .channelType(ChannelType.SLACK)
          .body(SlackTemplateBody.builder().text("Hello").build())
          .build();
      NotificationResult failResult = NotificationResult.builder()
          .success(false)
          .errorMessage("Rate limited")
          .permanentFailure(false)
          .build();

      when(queue.receiveMessages(eq(5), eq(60))).thenReturn(Single.just(List.of(queuedNotification)));
      when(templateDao.getTemplateById(eq(1L))).thenReturn(io.reactivex.rxjava3.core.Maybe.just(template));
      when(providerFactory.getProvider(eq(ChannelType.SLACK))).thenReturn(Optional.of(notificationProvider));
      when(notificationProvider.send(any(NotificationMessage.class), any(NotificationTemplate.class)))
          .thenReturn(Single.just(failResult));
      when(logDao.updateLogStatus(eq(102L), eq(NotificationStatus.RETRYING), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));
      when(queue.changeMessageVisibility(eq("rh-789"), anyInt())).thenReturn(Completable.complete());

      java.lang.reflect.Method pollAndProcess = NotificationWorker.class.getDeclaredMethod("pollAndProcess");
      pollAndProcess.setAccessible(true);
      ((io.reactivex.rxjava3.core.Completable) pollAndProcess.invoke(worker)).blockingAwait();

      verify(queue).changeMessageVisibility(eq("rh-789"), anyInt());
      verify(queue, never()).deleteMessage(any());
    }

    @Test
    void shouldDeleteMessageOnPermanentFailure() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .recipient("user@test.com")
          .logId(103L)
          .templateId(1L)
          .build();
      QueuedNotification queuedNotification = QueuedNotification.builder()
          .message(message)
          .receiptHandle("rh-perm")
          .messageId("msg-4")
          .receiveCount(1)
          .build();

      NotificationTemplate template = NotificationTemplate.builder()
          .id(1L)
          .channelType(ChannelType.SLACK)
          .body(SlackTemplateBody.builder().text("Hello").build())
          .build();
      NotificationResult permFailResult = NotificationResult.builder()
          .success(false)
          .errorMessage("Invalid auth")
          .permanentFailure(true)
          .build();

      when(queue.receiveMessages(eq(5), eq(60))).thenReturn(Single.just(List.of(queuedNotification)));
      when(templateDao.getTemplateById(eq(1L))).thenReturn(io.reactivex.rxjava3.core.Maybe.just(template));
      when(providerFactory.getProvider(eq(ChannelType.SLACK))).thenReturn(Optional.of(notificationProvider));
      when(notificationProvider.send(any(NotificationMessage.class), any(NotificationTemplate.class)))
          .thenReturn(Single.just(permFailResult));
      when(logDao.updateLogStatus(eq(103L), eq(NotificationStatus.PERMANENT_FAILURE), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));
      when(queue.deleteMessage(eq("rh-perm"))).thenReturn(Completable.complete());

      java.lang.reflect.Method pollAndProcess = NotificationWorker.class.getDeclaredMethod("pollAndProcess");
      pollAndProcess.setAccessible(true);
      ((io.reactivex.rxjava3.core.Completable) pollAndProcess.invoke(worker)).blockingAwait();

      verify(queue).deleteMessage("rh-perm");
    }

    @Test
    void shouldHandleTemplateNotFoundErrorAndRetry() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .recipient("user@test.com")
          .logId(104L)
          .templateId(999L)
          .build();
      QueuedNotification queuedNotification = QueuedNotification.builder()
          .message(message)
          .receiptHandle("rh-err")
          .messageId("msg-5")
          .receiveCount(1)
          .build();

      when(queue.receiveMessages(eq(5), eq(60))).thenReturn(Single.just(List.of(queuedNotification)));
      when(templateDao.getTemplateById(eq(999L))).thenReturn(io.reactivex.rxjava3.core.Maybe.empty());
      when(logDao.updateLogStatus(eq(104L), eq(NotificationStatus.RETRYING), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));
      when(queue.changeMessageVisibility(eq("rh-err"), anyInt())).thenReturn(Completable.complete());

      java.lang.reflect.Method pollAndProcess = NotificationWorker.class.getDeclaredMethod("pollAndProcess");
      pollAndProcess.setAccessible(true);
      ((io.reactivex.rxjava3.core.Completable) pollAndProcess.invoke(worker)).blockingAwait();

      verify(queue).changeMessageVisibility(eq("rh-err"), anyInt());
    }

    @Test
    void shouldDeleteMessageWhenExhaustedRetries() throws Exception {
      config.getRetry().setMaxAttempts(3);
      NotificationRetryPolicy retryPolicy = new NotificationRetryPolicy(config);
      worker = new NotificationWorker(queue, providerFactory, templateDao, logDao, retryPolicy, config);

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .recipient("user@test.com")
          .logId(105L)
          .templateId(1L)
          .build();
      QueuedNotification queuedNotification = QueuedNotification.builder()
          .message(message)
          .receiptHandle("rh-exhausted")
          .messageId("msg-6")
          .receiveCount(3)
          .build();

      NotificationTemplate template = NotificationTemplate.builder()
          .id(1L)
          .channelType(ChannelType.SLACK)
          .body(SlackTemplateBody.builder().text("Hello").build())
          .build();
      NotificationResult failResult = NotificationResult.builder()
          .success(false)
          .errorMessage("Transient")
          .permanentFailure(false)
          .build();

      when(queue.receiveMessages(eq(5), eq(60))).thenReturn(Single.just(List.of(queuedNotification)));
      when(templateDao.getTemplateById(eq(1L))).thenReturn(io.reactivex.rxjava3.core.Maybe.just(template));
      when(providerFactory.getProvider(eq(ChannelType.SLACK))).thenReturn(Optional.of(notificationProvider));
      when(notificationProvider.send(any(NotificationMessage.class), any(NotificationTemplate.class)))
          .thenReturn(Single.just(failResult));
      when(logDao.updateLogStatus(eq(105L), eq(NotificationStatus.FAILED), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));
      when(queue.deleteMessage(eq("rh-exhausted"))).thenReturn(Completable.complete());

      java.lang.reflect.Method pollAndProcess = NotificationWorker.class.getDeclaredMethod("pollAndProcess");
      pollAndProcess.setAccessible(true);
      ((io.reactivex.rxjava3.core.Completable) pollAndProcess.invoke(worker)).blockingAwait();

      verify(queue).deleteMessage("rh-exhausted");
    }

    @Test
    void shouldSkipLogUpdateWhenLogIdIsNull() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .recipient("user@test.com")
          .logId(null)
          .templateId(1L)
          .build();
      QueuedNotification queuedNotification = QueuedNotification.builder()
          .message(message)
          .receiptHandle("rh-null-log")
          .messageId("msg-7")
          .receiveCount(1)
          .build();

      NotificationTemplate template = NotificationTemplate.builder()
          .id(1L)
          .channelType(ChannelType.SLACK)
          .body(SlackTemplateBody.builder().text("Hello").build())
          .build();
      NotificationResult successResult = NotificationResult.builder()
          .success(true)
          .externalId("ext-456")
          .build();

      when(queue.receiveMessages(eq(5), eq(60))).thenReturn(Single.just(List.of(queuedNotification)));
      when(templateDao.getTemplateById(eq(1L))).thenReturn(io.reactivex.rxjava3.core.Maybe.just(template));
      when(providerFactory.getProvider(eq(ChannelType.SLACK))).thenReturn(Optional.of(notificationProvider));
      when(notificationProvider.send(any(NotificationMessage.class), any(NotificationTemplate.class)))
          .thenReturn(Single.just(successResult));
      when(queue.deleteMessage(eq("rh-null-log"))).thenReturn(Completable.complete());

      java.lang.reflect.Method pollAndProcess = NotificationWorker.class.getDeclaredMethod("pollAndProcess");
      pollAndProcess.setAccessible(true);
      ((io.reactivex.rxjava3.core.Completable) pollAndProcess.invoke(worker)).blockingAwait();

      verify(queue).deleteMessage("rh-null-log");
      verify(logDao, never()).updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), any());
    }
  }

  @Nested
  class SqsNotificationQueueTest {

    NotificationConfig config;
    SqsNotificationQueue sqsQueue;

    @BeforeEach
    void setUp() {
      config = new NotificationConfig();
      config.setAws(new NotificationConfig.AwsConfig("us-east-1"));
    }

    @Test
    void shouldReturnDisabledWhenSqsNotConfigured() {
      config.setSqs(null);
      sqsQueue = new SqsNotificationQueue(objectMapper, config);

      assertThat(sqsQueue.isEnabled()).isFalse();
    }

    @Test
    void shouldReturnEnabledWhenQueueUrlConfigured() {
      config.setSqs(new NotificationConfig.SqsConfig());
      config.getSqs().setQueueUrl("https://sqs.us-east-1.amazonaws.com/123/queue");
      sqsQueue = new SqsNotificationQueue(objectMapper, config);

      assertThat(sqsQueue.isEnabled()).isTrue();
    }

    @Test
    void shouldReturnDefaultVisibilityTimeoutWhenSqsNull() {
      config.setSqs(null);
      sqsQueue = new SqsNotificationQueue(objectMapper, config);

      assertThat(sqsQueue.getVisibilityTimeoutSeconds()).isEqualTo(300);
    }

    @Test
    void shouldReturnConfiguredVisibilityTimeout() {
      config.setSqs(new NotificationConfig.SqsConfig());
      config.getSqs().setQueueUrl("https://sqs.example.com/queue");
      config.getSqs().setVisibilityTimeoutSeconds(120);
      config.setAws(new NotificationConfig.AwsConfig("us-east-1"));
      sqsQueue = new SqsNotificationQueue(objectMapper, config);

      assertThat(sqsQueue.getVisibilityTimeoutSeconds()).isEqualTo(120);
    }

    @Test
    void shouldThrowWhenEnqueueAndSqsDisabled() {
      config.setSqs(null);
      sqsQueue = new SqsNotificationQueue(objectMapper, config);

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .recipient("user@test.com")
          .build();

      assertThatThrownBy(() -> sqsQueue.enqueue(message).blockingGet())
          .hasMessageContaining("not configured");
    }

    @Test
    void shouldReturnEmptyListWhenReceiveMessagesAndSqsDisabled() {
      config.setSqs(null);
      sqsQueue = new SqsNotificationQueue(objectMapper, config);

      List<QueuedNotification> result = sqsQueue.receiveMessages(5, 60).blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldCompleteWhenDeleteMessageAndSqsDisabled() {
      config.setSqs(null);
      sqsQueue = new SqsNotificationQueue(objectMapper, config);

      sqsQueue.deleteMessage("rh-123").blockingAwait();
    }

    @Test
    void shouldCompleteWhenChangeMessageVisibilityAndSqsDisabled() {
      config.setSqs(null);
      sqsQueue = new SqsNotificationQueue(objectMapper, config);

      sqsQueue.changeMessageVisibility("rh-123", 120).blockingAwait();
    }

    @Test
    void shouldReturnEmptyListWhenReceiveDlqMessagesAndDlqDisabled() {
      config.setSqs(new NotificationConfig.SqsConfig());
      config.getSqs().setQueueUrl("https://sqs.example.com/queue");
      config.getSqs().setDlqUrl(null);
      sqsQueue = new SqsNotificationQueue(objectMapper, config);

      List<QueuedNotification> result = sqsQueue.receiveDlqMessages(5).blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldCompleteWhenDeleteDlqMessageAndDlqDisabled() {
      config.setSqs(new NotificationConfig.SqsConfig());
      config.getSqs().setQueueUrl("https://sqs.example.com/queue");
      config.getSqs().setDlqUrl(null);
      sqsQueue = new SqsNotificationQueue(objectMapper, config);

      sqsQueue.deleteDlqMessage("rh-dlq").blockingAwait();
    }
  }

  @Nested
  class SqsNotificationQueueWithMockClientTest {

    @Mock
    software.amazon.awssdk.services.sqs.SqsClient mockSqsClient;

    @Test
    void shouldEnqueueAndReturnMessageId() throws Exception {
      NotificationConfig config = new NotificationConfig();
      config.setAws(new NotificationConfig.AwsConfig("us-east-1"));
      config.setSqs(new NotificationConfig.SqsConfig());
      config.getSqs().setQueueUrl("https://sqs.us-east-1.amazonaws.com/123/queue");

      SqsNotificationQueue sqsQueue = new SqsNotificationQueue(objectMapper, config);
      java.lang.reflect.Field clientField = SqsNotificationQueue.class.getDeclaredField("sqsClient");
      clientField.setAccessible(true);
      clientField.set(sqsQueue, mockSqsClient);

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .recipient("user@test.com")
          .build();

      when(mockSqsClient.sendMessage(any(software.amazon.awssdk.services.sqs.model.SendMessageRequest.class)))
          .thenReturn(software.amazon.awssdk.services.sqs.model.SendMessageResponse.builder()
              .messageId("msg-abc-123")
              .build());

      String messageId = sqsQueue.enqueue(message).blockingGet();

      assertThat(messageId).isEqualTo("msg-abc-123");
    }

    @Test
    void shouldReceiveAndDeserializeMessages() throws Exception {
      NotificationConfig config = new NotificationConfig();
      config.setAws(new NotificationConfig.AwsConfig("us-east-1"));
      config.setSqs(new NotificationConfig.SqsConfig());
      config.getSqs().setQueueUrl("https://sqs.us-east-1.amazonaws.com/123/queue");
      config.getSqs().setWaitTimeSeconds(20);

      SqsNotificationQueue sqsQueue = new SqsNotificationQueue(objectMapper, config);
      java.lang.reflect.Field clientField = SqsNotificationQueue.class.getDeclaredField("sqsClient");
      clientField.setAccessible(true);
      clientField.set(sqsQueue, mockSqsClient);

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .recipient("user@test.com")
          .templateId(1L)
          .build();
      String body = objectMapper.writeValueAsString(message);

      software.amazon.awssdk.services.sqs.model.Message sqsMessage =
          software.amazon.awssdk.services.sqs.model.Message.builder()
              .body(body)
              .receiptHandle("rh-xyz")
              .messageId("msg-xyz")
              .build();

      when(mockSqsClient.receiveMessage(any(software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest.class)))
          .thenReturn(software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse.builder()
              .messages(sqsMessage)
              .build());

      List<QueuedNotification> result = sqsQueue.receiveMessages(5, 60).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getMessage().getProjectId()).isEqualTo("proj-1");
      assertThat(result.get(0).getReceiptHandle()).isEqualTo("rh-xyz");
    }

    @Test
    void shouldDeleteMessage() throws Exception {
      NotificationConfig config = new NotificationConfig();
      config.setAws(new NotificationConfig.AwsConfig("us-east-1"));
      config.setSqs(new NotificationConfig.SqsConfig());
      config.getSqs().setQueueUrl("https://sqs.us-east-1.amazonaws.com/123/queue");

      SqsNotificationQueue sqsQueue = new SqsNotificationQueue(objectMapper, config);
      java.lang.reflect.Field clientField = SqsNotificationQueue.class.getDeclaredField("sqsClient");
      clientField.setAccessible(true);
      clientField.set(sqsQueue, mockSqsClient);

      when(mockSqsClient.deleteMessage(any(software.amazon.awssdk.services.sqs.model.DeleteMessageRequest.class)))
          .thenReturn(software.amazon.awssdk.services.sqs.model.DeleteMessageResponse.builder().build());

      sqsQueue.deleteMessage("rh-123").blockingAwait();

      verify(mockSqsClient).deleteMessage(any(software.amazon.awssdk.services.sqs.model.DeleteMessageRequest.class));
    }

    @Test
    void shouldChangeMessageVisibility() throws Exception {
      NotificationConfig config = new NotificationConfig();
      config.setAws(new NotificationConfig.AwsConfig("us-east-1"));
      config.setSqs(new NotificationConfig.SqsConfig());
      config.getSqs().setQueueUrl("https://sqs.us-east-1.amazonaws.com/123/queue");

      SqsNotificationQueue sqsQueue = new SqsNotificationQueue(objectMapper, config);
      java.lang.reflect.Field clientField = SqsNotificationQueue.class.getDeclaredField("sqsClient");
      clientField.setAccessible(true);
      clientField.set(sqsQueue, mockSqsClient);

      when(mockSqsClient.changeMessageVisibility(
          any(software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest.class)))
          .thenReturn(software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse.builder().build());

      sqsQueue.changeMessageVisibility("rh-456", 90).blockingAwait();

      verify(mockSqsClient).changeMessageVisibility(
          any(software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest.class));
    }

    @Test
    void shouldReceiveDlqMessages() throws Exception {
      NotificationConfig config = new NotificationConfig();
      config.setAws(new NotificationConfig.AwsConfig("us-east-1"));
      config.setSqs(new NotificationConfig.SqsConfig());
      config.getSqs().setQueueUrl("https://sqs.us-east-1.amazonaws.com/123/queue");
      config.getSqs().setDlqUrl("https://sqs.us-east-1.amazonaws.com/123/dlq");

      SqsNotificationQueue sqsQueue = new SqsNotificationQueue(objectMapper, config);
      java.lang.reflect.Field clientField = SqsNotificationQueue.class.getDeclaredField("sqsClient");
      clientField.setAccessible(true);
      clientField.set(sqsQueue, mockSqsClient);

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.EMAIL)
          .recipient("user@test.com")
          .build();
      String body = objectMapper.writeValueAsString(message);

      software.amazon.awssdk.services.sqs.model.Message sqsMessage =
          software.amazon.awssdk.services.sqs.model.Message.builder()
              .body(body)
              .receiptHandle("rh-dlq")
              .messageId("msg-dlq")
              .build();

      when(mockSqsClient.receiveMessage(any(software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest.class)))
          .thenReturn(software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse.builder()
              .messages(sqsMessage)
              .build());

      List<QueuedNotification> result = sqsQueue.receiveDlqMessages(5).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getReceiptHandle()).isEqualTo("rh-dlq");
    }

    @Test
    void shouldDeleteDlqMessage() throws Exception {
      NotificationConfig config = new NotificationConfig();
      config.setAws(new NotificationConfig.AwsConfig("us-east-1"));
      config.setSqs(new NotificationConfig.SqsConfig());
      config.getSqs().setQueueUrl("https://sqs.us-east-1.amazonaws.com/123/queue");
      config.getSqs().setDlqUrl("https://sqs.us-east-1.amazonaws.com/123/dlq");

      SqsNotificationQueue sqsQueue = new SqsNotificationQueue(objectMapper, config);
      java.lang.reflect.Field clientField = SqsNotificationQueue.class.getDeclaredField("sqsClient");
      clientField.setAccessible(true);
      clientField.set(sqsQueue, mockSqsClient);

      when(mockSqsClient.deleteMessage(any(software.amazon.awssdk.services.sqs.model.DeleteMessageRequest.class)))
          .thenReturn(software.amazon.awssdk.services.sqs.model.DeleteMessageResponse.builder().build());

      sqsQueue.deleteDlqMessage("rh-dlq").blockingAwait();

      verify(mockSqsClient).deleteMessage(any(software.amazon.awssdk.services.sqs.model.DeleteMessageRequest.class));
    }
  }

  @Nested
  class DlqHandlerTest {

    DlqHandler dlqHandler;

    @BeforeEach
    void setUp() {
      dlqHandler = new DlqHandler(queue, logDao);
    }

    @Test
    void shouldReturnZeroCountsWhenNoMessages() {
      when(queue.receiveDlqMessages(eq(10))).thenReturn(Single.just(List.of()));

      var result = dlqHandler.processDlqMessages(10).blockingGet();

      assertThat(result.processed()).isEqualTo(0);
      assertThat(result.requeued()).isEqualTo(0);
      assertThat(result.discarded()).isEqualTo(0);
    }

    @Test
    void shouldDiscardMessageWithLogId() {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.EMAIL)
          .recipient("user@test.com")
          .logId(100L)
          .build();
      QueuedNotification queuedNotification = QueuedNotification.builder()
          .message(message)
          .receiptHandle("rh-123")
          .messageId("msg-1")
          .receiveCount(1)
          .build();

      when(queue.receiveDlqMessages(eq(10)))
          .thenReturn(Single.just(List.of(queuedNotification)));
      when(logDao.updateLogStatus(eq(100L), eq(NotificationStatus.PERMANENT_FAILURE), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));
      when(queue.deleteDlqMessage(eq("rh-123"))).thenReturn(Completable.complete());

      var result = dlqHandler.processDlqMessages(10).blockingGet();

      assertThat(result.processed()).isEqualTo(1);
      assertThat(result.discarded()).isEqualTo(1);
      verify(queue).deleteDlqMessage("rh-123");
    }

    @Test
    void shouldHandleDlqActionEnum() {
      assertThat(DlqHandler.DlqAction.REQUEUE).isNotNull();
      assertThat(DlqHandler.DlqAction.DISCARD).isNotNull();
      assertThat(DlqHandler.DlqAction.SKIP).isNotNull();
    }

    @Test
    void shouldDiscardMessageWithNullLogId() {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.EMAIL)
          .recipient("orphan@test.com")
          .logId(null)
          .build();
      QueuedNotification queuedNotification = QueuedNotification.builder()
          .message(message)
          .receiptHandle("rh-null-log")
          .messageId("msg-null")
          .receiveCount(1)
          .build();

      when(queue.receiveDlqMessages(eq(5)))
          .thenReturn(Single.just(List.of(queuedNotification)));
      when(queue.deleteDlqMessage(eq("rh-null-log"))).thenReturn(Completable.complete());

      var result = dlqHandler.processDlqMessages(5).blockingGet();

      assertThat(result.processed()).isEqualTo(1);
      assertThat(result.discarded()).isEqualTo(1);
      assertThat(result.requeued()).isEqualTo(0);
      verify(queue).deleteDlqMessage("rh-null-log");
    }

    @Test
    void shouldProcessMultipleMessages() {
      NotificationMessage msg1 = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.EMAIL)
          .recipient("a@test.com")
          .logId(1L)
          .build();
      NotificationMessage msg2 = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.EMAIL)
          .recipient("b@test.com")
          .logId(2L)
          .build();
      QueuedNotification qn1 = QueuedNotification.builder()
          .message(msg1)
          .receiptHandle("rh-1")
          .messageId("m1")
          .receiveCount(1)
          .build();
      QueuedNotification qn2 = QueuedNotification.builder()
          .message(msg2)
          .receiptHandle("rh-2")
          .messageId("m2")
          .receiveCount(1)
          .build();

      when(queue.receiveDlqMessages(eq(10)))
          .thenReturn(Single.just(List.of(qn1, qn2)));
      when(logDao.updateLogStatus(anyLong(), eq(NotificationStatus.PERMANENT_FAILURE), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));
      when(queue.deleteDlqMessage(any())).thenReturn(Completable.complete());

      var result = dlqHandler.processDlqMessages(10).blockingGet();

      assertThat(result.processed()).isEqualTo(2);
      assertThat(result.discarded()).isEqualTo(2);
      assertThat(result.requeued()).isEqualTo(0);
    }

    @Test
    void shouldExposeDlqProcessResultFields() {
      DlqHandler.DlqProcessResult result = new DlqHandler.DlqProcessResult(3, 1, 2);
      assertThat(result.processed()).isEqualTo(3);
      assertThat(result.requeued()).isEqualTo(1);
      assertThat(result.discarded()).isEqualTo(2);
    }
  }
}
