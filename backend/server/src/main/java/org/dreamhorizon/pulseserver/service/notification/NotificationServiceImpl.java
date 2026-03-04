package org.dreamhorizon.pulseserver.service.notification;

import static org.dreamhorizon.pulseserver.constant.NotificationConstants.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.notification.*;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.notification.models.*;
import org.dreamhorizon.pulseserver.service.notification.models.*;
import org.dreamhorizon.pulseserver.service.notification.provider.NotificationProvider;
import org.dreamhorizon.pulseserver.service.notification.provider.NotificationProviderFactory;
import org.dreamhorizon.pulseserver.service.notification.queue.SqsNotificationQueue;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class NotificationServiceImpl implements NotificationService {

  private final NotificationChannelDao channelDao;
  private final NotificationTemplateDao templateDao;
  private final NotificationLogDao logDao;
  private final EmailSuppressionDao suppressionDao;
  private final NotificationProviderFactory providerFactory;
  private final SqsNotificationQueue notificationQueue;
  private final ObjectMapper objectMapper;

  @Override
  public Single<NotificationBatchResponseDto> sendNotification(
      String projectId, SendNotificationRequestDto request) {
    String batchId = UUID.randomUUID().toString();
    String idempotencyKey =
        request.getIdempotencyKey() != null
            ? request.getIdempotencyKey()
            : UUID.randomUUID().toString();

    return processChannelNotifications(projectId, request, batchId, idempotencyKey);
  }

  @Override
  public Single<NotificationBatchResponseDto> sendNotificationAsync(
      String projectId, SendNotificationRequestDto request) {
    String batchId = UUID.randomUUID().toString();
    String idempotencyKey =
        request.getIdempotencyKey() != null
            ? request.getIdempotencyKey()
            : UUID.randomUUID().toString();

    return queueChannelNotifications(projectId, request, batchId, idempotencyKey);
  }

  private Single<NotificationBatchResponseDto> queueChannelNotifications(
      String projectId, SendNotificationRequestDto request, String batchId, String idempotencyKey) {

    return Observable.fromIterable(request.getChannelTypes())
        .flatMapSingle(
            channelType ->
                channelDao
                    .getActiveChannelByType(projectId, channelType)
                    .map(channel -> Map.entry(channelType, Optional.of(channel)))
                    .defaultIfEmpty(Map.entry(channelType, Optional.empty())))
        .toList()
        .flatMap(
            channelEntries -> {
              List<NotificationResultDto> results = new ArrayList<>();
              List<Single<List<NotificationResultDto>>> queueOperations = new ArrayList<>();

              for (var entry : channelEntries) {
                ChannelType channelType = entry.getKey();
                Optional<NotificationChannel> channelOpt = entry.getValue();

                if (channelOpt.isEmpty()) {
                  log.debug(
                      "No active {} channel configured for project {}, skipping",
                      channelType,
                      projectId);
                  continue;
                }

                NotificationChannel channel = channelOpt.get();

                queueOperations.add(
                    templateDao
                        .getTemplateByEventNameAndChannel(
                            projectId, request.getEventName(), channelType)
                        .switchIfEmpty(
                            Maybe.error(
                                ServiceError.NOT_FOUND.getCustomException(
                                    "No template found for event: "
                                        + request.getEventName()
                                        + " and channel: "
                                        + channelType)))
                        .toSingle()
                        .flatMap(
                            template ->
                                enqueueRecipients(
                                    projectId,
                                    request,
                                    channel,
                                    template,
                                    batchId,
                                    idempotencyKey,
                                    channelType)));
              }

              if (queueOperations.isEmpty()) {
                return Single.just(
                    NotificationBatchResponseDto.builder()
                        .batchId(batchId)
                        .totalRecipients(0)
                        .queued(0)
                        .failed(0)
                        .results(results)
                        .build());
              }

              return Single.zip(
                  queueOperations,
                  resultArrays -> {
                    for (Object resultArray : resultArrays) {
                      @SuppressWarnings("unchecked")
                      List<NotificationResultDto> partialResults =
                          (List<NotificationResultDto>) resultArray;
                      results.addAll(partialResults);
                    }

                    int queued =
                        (int) results.stream().filter(r -> NotificationStatus.QUEUED.equals(r.getStatus())).count();
                    int failed =
                        (int) results.stream().filter(r -> NotificationStatus.FAILED.equals(r.getStatus())).count();

                    return NotificationBatchResponseDto.builder()
                        .batchId(batchId)
                        .totalRecipients(results.size())
                        .queued(queued)
                        .failed(failed)
                        .results(results)
                        .build();
                  });
            });
  }

  private Single<List<NotificationResultDto>> enqueueRecipients(
      String projectId,
      SendNotificationRequestDto request,
      NotificationChannel channel,
      NotificationTemplate template,
      String batchId,
      String idempotencyKey,
      ChannelType channelType) {

    List<String> recipients = getRecipientsForChannel(request.getRecipients(), channelType);

    if (recipients.isEmpty()) {
      return Single.just(Collections.emptyList());
    }

    String subject = extractSubjectFromBody(template.getBody());

    return Observable.fromIterable(recipients)
        .flatMapSingle(
            recipient -> {
              String recipientKey = idempotencyKey + ":" + channelType + ":" + recipient;

              Single<NotificationResultDto> processingChain =
                  logDao
                      .getLogByIdempotency(projectId, recipientKey, channelType, recipient)
                      .flatMapSingle(
                          existingLog -> {
                            log.debug(
                                "Skipping duplicate notification for recipient: {}, idempotencyKey: {}",
                                recipient,
                                recipientKey);
                            return Single.just(
                                NotificationResultDto.builder()
                                    .recipient(recipient)
                                    .channelType(channelType)
                                    .status(NotificationStatus.SKIPPED)
                                    .errorMessage("Duplicate notification (idempotency)")
                                    .build());
                          })
                      .switchIfEmpty(
                          Single.defer(
                              () ->
                                  logDao
                                      .insertLog(
                                          NotificationLog.builder()
                                              .projectId(projectId)
                                              .batchId(batchId)
                                              .idempotencyKey(recipientKey)
                                              .channelType(channelType)
                                              .channelId(channel.getId())
                                              .templateId(template.getId())
                                              .recipient(recipient)
                                              .subject(subject)
                                              .status(NotificationStatus.QUEUED)
                                              .attemptCount(0)
                                              .build())
                                      .flatMap(
                                          logId -> {
                                            NotificationMessage message =
                                                NotificationMessage.builder()
                                                    .logId(logId)
                                                    .projectId(projectId)
                                                    .batchId(batchId)
                                                    .idempotencyKey(recipientKey)
                                                    .channelType(channelType)
                                                    .channelId(channel.getId())
                                                    .channelConfig(channel.getConfig())
                                                    .templateId(template.getId())
                                                    .templateBody(template.getBody())
                                                    .recipient(recipient)
                                                    .subject(subject)
                                                    .params(request.getParams())
                                                    .metadata(request.getMetadata())
                                                    .build();

                                            return notificationQueue
                                                .enqueue(message)
                                                .map(
                                                    messageId ->
                                                        NotificationResultDto.builder()
                                                            .recipient(recipient)
                                                            .channelType(channelType)
                                                            .status(NotificationStatus.QUEUED)
                                                            .externalId(messageId)
                                                            .build());
                                          })));

              if (channelType == ChannelType.EMAIL) {
                return suppressionDao
                    .isEmailSuppressed(projectId, recipient)
                    .flatMap(
                        suppressed -> {
                          if (suppressed) {
                            log.info("Skipping suppressed email: {}", recipient);
                            return Single.just(
                                NotificationResultDto.builder()
                                    .recipient(recipient)
                                    .channelType(channelType)
                                    .status(NotificationStatus.SKIPPED)
                                    .errorMessage("Email is suppressed")
                                    .build());
                          }
                          return processingChain;
                        })
                    .onErrorReturn(
                        e ->
                            NotificationResultDto.builder()
                                .recipient(recipient)
                                .channelType(channelType)
                                .status(NotificationStatus.FAILED)
                                .errorMessage(e.getMessage())
                                .build());
              }

              return processingChain.onErrorReturn(
                  e ->
                      NotificationResultDto.builder()
                          .recipient(recipient)
                          .channelType(channelType)
                          .status(NotificationStatus.FAILED)
                          .errorMessage(e.getMessage())
                          .build());
            })
        .toList();
  }

  private Single<NotificationBatchResponseDto> processChannelNotifications(
      String projectId, SendNotificationRequestDto request, String batchId, String idempotencyKey) {

    return Observable.fromIterable(request.getChannelTypes())
        .flatMapSingle(
            channelType ->
                channelDao
                    .getActiveChannelByType(projectId, channelType)
                    .map(channel -> Map.entry(channelType, Optional.of(channel)))
                    .defaultIfEmpty(Map.entry(channelType, Optional.empty())))
        .toList()
        .flatMap(
            channelEntries -> {
              List<NotificationResultDto> results = new ArrayList<>();
              List<Single<List<NotificationResultDto>>> sendOperations = new ArrayList<>();

              for (var entry : channelEntries) {
                ChannelType channelType = entry.getKey();
                Optional<NotificationChannel> channelOpt = entry.getValue();

                if (channelOpt.isEmpty()) {
                  log.debug(
                      "No active {} channel configured for project {}, skipping",
                      channelType,
                      projectId);
                  continue;
                }

                NotificationChannel channel = channelOpt.get();

                sendOperations.add(
                    templateDao
                        .getTemplateByEventNameAndChannel(
                            projectId, request.getEventName(), channelType)
                        .switchIfEmpty(
                            Maybe.error(
                                ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
                                    "No template found for event: "
                                        + request.getEventName()
                                        + " and channel: "
                                        + channelType)))
                        .toSingle()
                        .flatMap(
                            template ->
                                sendToRecipients(
                                    projectId,
                                    request,
                                    channel,
                                    template,
                                    batchId,
                                    idempotencyKey,
                                    channelType)));
              }

              if (sendOperations.isEmpty()) {
                return Single.just(
                    NotificationBatchResponseDto.builder()
                        .batchId(batchId)
                        .totalRecipients(0)
                        .queued(0)
                        .failed(0)
                        .results(results)
                        .build());
              }

              return Single.zip(
                  sendOperations,
                  resultArrays -> {
                    for (Object resultArray : resultArrays) {
                      @SuppressWarnings("unchecked")
                      List<NotificationResultDto> partialResults =
                          (List<NotificationResultDto>) resultArray;
                      results.addAll(partialResults);
                    }

                    int sent =
                        (int) results.stream().filter(r -> NotificationStatus.SENT.equals(r.getStatus())).count();
                    int failed =
                        (int) results.stream().filter(r -> NotificationStatus.FAILED.equals(r.getStatus())).count();

                    return NotificationBatchResponseDto.builder()
                        .batchId(batchId)
                        .totalRecipients(results.size())
                        .queued(sent)
                        .failed(failed)
                        .results(results)
                        .build();
                  });
            });
  }

  private Single<List<NotificationResultDto>> sendToRecipients(
      String projectId,
      SendNotificationRequestDto request,
      NotificationChannel channel,
      NotificationTemplate template,
      String batchId,
      String idempotencyKey,
      ChannelType channelType) {

    List<String> recipients = getRecipientsForChannel(request.getRecipients(), channelType);

    if (recipients.isEmpty()) {
      return Single.just(Collections.emptyList());
    }

    Optional<NotificationProvider> providerOpt = providerFactory.getProvider(channelType);
    if (providerOpt.isEmpty()) {
      log.warn("No provider registered for channel type: {}", channelType);
      return Single.just(Collections.emptyList());
    }

    NotificationProvider provider = providerOpt.get();
    String subject = extractSubjectFromBody(template.getBody());

    return Observable.fromIterable(recipients)
        .flatMapSingle(
            recipient -> {
              String recipientKey = idempotencyKey + ":" + channelType + ":" + recipient;

              if (channelType == ChannelType.EMAIL) {
                return suppressionDao
                    .isEmailSuppressed(projectId, recipient)
                    .flatMap(
                        suppressed -> {
                          if (suppressed) {
                            log.info("Skipping suppressed email: {}", recipient);
                            return Single.just(
                                NotificationResultDto.builder()
                                    .recipient(recipient)
                                    .channelType(channelType)
                                    .status(NotificationStatus.SKIPPED)
                                    .errorMessage("Email is suppressed")
                                    .build());
                          }
                          return sendSingleNotification(
                              projectId,
                              recipient,
                              recipientKey,
                              channel,
                              template,
                              batchId,
                              subject,
                              channelType,
                              provider,
                              request.getParams());
                        });
              }

              return sendSingleNotification(
                  projectId,
                  recipient,
                  recipientKey,
                  channel,
                  template,
                  batchId,
                  subject,
                  channelType,
                  provider,
                  request.getParams());
            })
        .toList();
  }

  private Single<NotificationResultDto> sendSingleNotification(
      String projectId,
      String recipient,
      String idempotencyKey,
      NotificationChannel channel,
      NotificationTemplate template,
      String batchId,
      String subject,
      ChannelType channelType,
      NotificationProvider provider,
      Map<String, Object> params) {

    NotificationMessage message =
        NotificationMessage.builder()
            .projectId(projectId)
            .batchId(batchId)
            .idempotencyKey(idempotencyKey)
            .channelType(channelType)
            .channelId(channel.getId())
            .channelConfig(channel.getConfig())
            .templateId(template.getId())
            .templateBody(template.getBody())
            .recipient(recipient)
            .subject(subject)
            .params(params)
            .build();

    return logDao
        .insertLogIfNotExists(
            NotificationLog.builder()
                .projectId(projectId)
                .batchId(batchId)
                .idempotencyKey(idempotencyKey)
                .channelType(channelType)
                .channelId(channel.getId())
                .templateId(template.getId())
                .recipient(recipient)
                .subject(subject)
                .status(NotificationStatus.PROCESSING)
                .attemptCount(1)
                .build())
        .flatMap(
            inserted -> {
              if (!inserted) {
                return Single.just(
                    NotificationResultDto.builder()
                        .recipient(recipient)
                        .channelType(channelType)
                        .status(NotificationStatus.SKIPPED)
                        .errorMessage("Duplicate notification (idempotency)")
                        .build());
              }

              return logDao
                  .getLogByIdempotency(projectId, idempotencyKey, channelType, recipient)
                  .toSingle()
                  .flatMap(
                      logEntry ->
                          provider
                              .send(message, template)
                              .flatMap(
                                  result -> {
                                    NotificationStatus status =
                                        result.isSuccess()
                                            ? NotificationStatus.SENT
                                            : (result.isPermanentFailure()
                                                ? NotificationStatus.PERMANENT_FAILURE
                                                : NotificationStatus.FAILED);

                                    return logDao
                                        .updateLogStatus(
                                            logEntry.getId(),
                                            status,
                                            1,
                                            result.getErrorMessage(),
                                            result.getErrorCode(),
                                            result.getExternalId(),
                                            result.getProviderResponse(),
                                            (int) result.getLatencyMs())
                                        .map(
                                            updated ->
                                                NotificationResultDto.builder()
                                                    .recipient(recipient)
                                                    .channelType(channelType)
                                                    .status(status)
                                                    .externalId(result.getExternalId())
                                                    .errorMessage(result.getErrorMessage())
                                                    .build());
                                  }));
            })
        .onErrorReturn(
            e -> {
              log.error("Error sending notification to {}", recipient, e);
              return NotificationResultDto.builder()
                  .recipient(recipient)
                  .channelType(channelType)
                  .status(NotificationStatus.FAILED)
                  .errorMessage(e.getMessage())
                  .build();
            });
  }

  private List<String> getRecipientsForChannel(RecipientsDto recipients, ChannelType channelType) {
    if (recipients == null) {
      return Collections.emptyList();
    }

    return switch (channelType) {
      case EMAIL ->
          recipients.getEmails() != null ? recipients.getEmails() : Collections.emptyList();
      case SLACK -> {
        List<String> slackRecipients = new ArrayList<>();
        if (recipients.getSlackChannelIds() != null) {
          slackRecipients.addAll(recipients.getSlackChannelIds());
        }
        if (recipients.getSlackUserIds() != null) {
          slackRecipients.addAll(recipients.getSlackUserIds());
        }
        yield slackRecipients;
      }
      case TEAMS ->
          recipients.getTeamsWorkflowUrls() != null
              ? recipients.getTeamsWorkflowUrls()
              : Collections.emptyList();
      default -> Collections.emptyList();
    };
  }

  @Override
  public Single<List<NotificationChannelDto>> getChannels(String projectId) {
    return channelDao
        .getChannelsByProject(projectId)
        .map(channels -> channels.stream().map(this::toChannelDto).toList());
  }

  @Override
  public Maybe<NotificationChannelDto> getChannel(Long channelId) {
    return channelDao.getChannelById(channelId).map(this::toChannelDto);
  }

  @Override
  public Single<NotificationChannelDto> createChannel(
      String projectId, CreateChannelRequestDto request) {
    return channelDao
        .getActiveChannelByProjectAndType(projectId, request.getChannelType())
        .isEmpty()
        .flatMap(
            noActiveChannel -> {
              if (!noActiveChannel) {
                return Single.<NotificationChannelDto>error(
                    ServiceError.DUPLICATE_CHANNEL_TYPE.getCustomException(
                        "An active " + request.getChannelType() + " channel already exists for this project"));
              }

              String configJson = serializeBody(request.getConfig());

              NotificationChannel channel =
                  NotificationChannel.builder()
                      .projectId(projectId)
                      .channelType(request.getChannelType())
                      .name(request.getName())
                      .config(configJson)
                      .isActive(true)
                      .build();

              return channelDao
                  .createChannel(channel)
                  .flatMap(id -> channelDao.getChannelById(id).toSingle())
                  .map(this::toChannelDto);
            });
  }

  @Override
  public Single<NotificationChannelDto> updateChannel(
      Long channelId, UpdateChannelRequestDto request) {
    return channelDao
        .getChannelById(channelId)
        .switchIfEmpty(Maybe.error(ServiceError.NOT_FOUND.getCustomException("Channel not found")))
        .toSingle()
        .flatMap(
            existing -> {
              String configJson =
                  request.getConfig() != null
                      ? serializeBody(request.getConfig())
                      : existing.getConfig();

              NotificationChannel updated =
                  NotificationChannel.builder()
                      .name(request.getName() != null ? request.getName() : existing.getName())
                      .config(configJson)
                      .isActive(
                          request.getIsActive() != null
                              ? request.getIsActive()
                              : existing.getIsActive())
                      .build();

              return channelDao
                  .updateChannel(channelId, updated)
                  .flatMap(count -> channelDao.getChannelById(channelId).toSingle())
                  .map(this::toChannelDto);
            });
  }

  @Override
  public Single<Boolean> deleteChannel(Long channelId) {
    return channelDao.deleteChannel(channelId).map(count -> count > 0);
  }

  @Override
  public Single<List<NotificationTemplateDto>> getTemplates(String projectId) {
    return templateDao
        .getTemplatesByProject(projectId)
        .map(templates -> templates.stream().map(this::toTemplateDto).toList());
  }

  @Override
  public Maybe<NotificationTemplateDto> getTemplate(Long templateId) {
    return templateDao.getTemplateById(templateId).map(this::toTemplateDto);
  }

  @Override
  public Single<NotificationTemplateDto> createTemplate(
      String projectId, CreateTemplateRequestDto request) {
    return templateDao
        .getLatestVersion(projectId, request.getEventName(), request.getChannelType())
        .flatMap(
            latestVersion -> {
              String bodyJson = serializeBody(request.getBody());

              NotificationTemplate template =
                  NotificationTemplate.builder()
                      .projectId(projectId)
                      .eventName(request.getEventName())
                      .channelType(request.getChannelType())
                      .version(latestVersion + 1)
                      .body(bodyJson)
                      .isActive(true)
                      .build();

              return templateDao
                  .createTemplate(template)
                  .flatMap(id -> templateDao.getTemplateById(id).toSingle())
                  .map(this::toTemplateDto);
            });
  }

  @Override
  public Single<NotificationTemplateDto> updateTemplate(
      Long templateId, UpdateTemplateRequestDto request) {
    return templateDao
        .getTemplateById(templateId)
        .switchIfEmpty(Maybe.error(ServiceError.NOT_FOUND.getCustomException("Template not found")))
        .toSingle()
        .flatMap(
            existing -> {
              String bodyJson =
                  request.getBody() != null ? serializeBody(request.getBody()) : existing.getBody();

              NotificationTemplate updated =
                  NotificationTemplate.builder()
                      .eventName(
                          request.getEventName() != null
                              ? request.getEventName()
                              : existing.getEventName())
                      .channelType(
                          request.getChannelType() != null
                              ? request.getChannelType()
                              : existing.getChannelType())
                      .body(bodyJson)
                      .isActive(
                          request.getIsActive() != null
                              ? request.getIsActive()
                              : existing.getIsActive())
                      .build();

              return templateDao
                  .updateTemplate(templateId, updated)
                  .flatMap(count -> templateDao.getTemplateById(templateId).toSingle())
                  .map(this::toTemplateDto);
            });
  }

  @Override
  public Single<Boolean> deleteTemplate(Long templateId) {
    return templateDao.deleteTemplate(templateId).map(count -> count > 0);
  }

  @Override
  public Single<NotificationLogsResponseDto> getLogs(String projectId, int limit, int offset) {
    return logDao
        .getLogsByProject(projectId, limit, offset)
        .map(
            logs ->
                NotificationLogsResponseDto.builder()
                    .logs(logs.stream().map(this::toLogDto).toList())
                    .build());
  }

  @Override
  public Single<NotificationLogsResponseDto> getLogsByBatch(String projectId, String batchId) {
    return logDao
        .getLogsByBatch(projectId, batchId)
        .map(
            logs ->
                NotificationLogsResponseDto.builder()
                    .logs(logs.stream().map(this::toLogDto).toList())
                    .build());
  }

  private NotificationChannelDto toChannelDto(NotificationChannel channel) {
    Object config = parseBodyToObject(channel.getConfig());

    return NotificationChannelDto.builder()
        .id(channel.getId())
        .projectId(channel.getProjectId())
        .channelType(channel.getChannelType())
        .name(channel.getName())
        .config(config)
        .isActive(channel.getIsActive())
        .createdAt(channel.getCreatedAt())
        .updatedAt(channel.getUpdatedAt())
        .build();
  }

  private NotificationTemplateDto toTemplateDto(NotificationTemplate template) {
    Object bodyObject = parseBodyToObject(template.getBody());

    return NotificationTemplateDto.builder()
        .id(template.getId())
        .projectId(template.getProjectId())
        .eventName(template.getEventName())
        .channelType(template.getChannelType())
        .version(template.getVersion())
        .body(bodyObject)
        .isActive(template.getIsActive())
        .createdAt(template.getCreatedAt())
        .updatedAt(template.getUpdatedAt())
        .build();
  }

  private NotificationLogDto toLogDto(NotificationLog log) {
    return NotificationLogDto.builder()
        .id(log.getId())
        .batchId(log.getBatchId())
        .channelType(log.getChannelType())
        .recipient(log.getRecipient())
        .subject(log.getSubject())
        .status(log.getStatus().name())
        .attemptCount(log.getAttemptCount())
        .errorMessage(log.getErrorMessage())
        .externalId(log.getExternalId())
        .latencyMs(log.getLatencyMs())
        .createdAt(log.getCreatedAt())
        .sentAt(log.getSentAt())
        .build();
  }

  private String extractSubjectFromBody(String bodyJson) {
    if (bodyJson == null || bodyJson.isEmpty()) {
      return null;
    }
    try {
      JsonNode body = objectMapper.readTree(bodyJson);
      if (body.has(KEY_SUBJECT)) {
        return body.get(KEY_SUBJECT).asText();
      }
      if (body.has(KEY_TITLE)) {
        return body.get(KEY_TITLE).asText();
      }
      if (body.has(KEY_TEXT)) {
        return body.get(KEY_TEXT).asText();
      }
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse template body JSON for subject extraction", e);
    }
    return null;
  }

  private String serializeBody(Object body) {
    if (body == null) {
      return null;
    }
    if (body instanceof String s) {
      return s;
    }
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw ServiceError.INVALID_REQUEST_BODY.getCustomException(
          "Invalid body format: " + e.getMessage());
    }
  }

  private Object parseBodyToObject(String bodyJson) {
    if (bodyJson == null || bodyJson.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.readValue(bodyJson, Object.class);
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse template body JSON", e);
      return bodyJson;
    }
  }
}
