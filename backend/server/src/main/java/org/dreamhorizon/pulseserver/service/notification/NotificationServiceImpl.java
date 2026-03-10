package org.dreamhorizon.pulseserver.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Row;
import java.util.*;
import java.util.stream.Collectors;
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
  private final ChannelEventMappingDao mappingDao;
  private final NotificationProviderFactory providerFactory;
  private final SqsNotificationQueue notificationQueue;
  private final ObjectMapper objectMapper;

  // ==================== Send (mapping-driven) ====================

  @Override
  public Single<NotificationBatchResponseDto> sendNotification(
      String projectId, SendNotificationRequestDto request) {
    return buildAndSend(projectId, request, resolveIdempotencyKey(request), false);
  }

  @Override
  public Single<NotificationBatchResponseDto> sendNotificationAsync(
      String projectId, SendNotificationRequestDto request) {
    return buildAndSend(projectId, request, resolveIdempotencyKey(request), true);
  }

  private String resolveIdempotencyKey(SendNotificationRequestDto request) {
    return request.getIdempotencyKey() != null
        ? request.getIdempotencyKey()
        : UUID.randomUUID().toString();
  }

  private Single<NotificationBatchResponseDto> buildAndSend(
      String projectId,
      SendNotificationRequestDto request,
      String idempotencyKey,
      boolean async) {

    if (request.getMappingId() != null) {
      return buildAndSendByMappingId(projectId, request, idempotencyKey, async);
    }

    Optional<Single<NotificationBatchResponseDto>> validationError =
        validateEventBasedRequest(projectId, request);
    if (validationError.isPresent()) {
      return validationError.get();
    }

    return buildAndSendByEvent(projectId, request, idempotencyKey, async);
  }

  private Optional<Single<NotificationBatchResponseDto>> validateEventBasedRequest(
      String projectId, SendNotificationRequestDto request) {
    Map<String, Boolean> rules = new LinkedHashMap<>();
    rules.put("eventName", request.getEventName() == null || request.getEventName().isBlank());
    rules.put("projectId (X-Project-Id header)", projectId == null || projectId.isBlank());
    rules.put(
        "channelTypes",
        request.getChannelTypes() == null || request.getChannelTypes().isEmpty());

    List<String> missing =
        rules.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();

    if (missing.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        Single.error(
            ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
                "When mappingId is not provided, the following are required: "
                    + String.join(", ", missing))));
  }

  private Single<NotificationBatchResponseDto> buildAndSendByMappingId(
      String projectId,
      SendNotificationRequestDto request,
      String idempotencyKey,
      boolean async) {

    return mappingDao
        .getActiveMappingWithChannelById(request.getMappingId())
        .switchIfEmpty(
            Maybe.error(
                ServiceError.NOT_FOUND.getCustomException(
                    "Mapping not found or inactive for id: " + request.getMappingId())))
        .toSingle()
        .flatMap(
            row -> {
              String mappingProjectId = row.getString("project_id");
              if (!mappingProjectId.equals(projectId)) {
                return Single.error(
                    ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
                        "Mapping does not belong to project: " + projectId));
              }

              Long channelId = row.getLong("channel_id");
              ChannelType channelType = ChannelType.valueOf(row.getString("channel_type"));
              ChannelConfig channelConfig = extractConfig(row);
              String eventName = row.getString("event_name");

              List<String> dbRecipients = new ArrayList<>();
              String mappingRecipient = row.getString("recipient");
              if (mappingRecipient != null && !mappingRecipient.isBlank()) {
                dbRecipients.add(mappingRecipient);
              }

              Set<String> allRecipients =
                  resolveRecipients(request.getRecipients(), channelType, dbRecipients);

              if (allRecipients.isEmpty()) {
                return Single.error(
                    ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
                        "No recipients found: provide recipients in request or configure them in the mapping"));
              }

              NotificationChannel channel =
                  NotificationChannel.builder()
                      .id(channelId)
                      .channelType(channelType)
                      .config(channelConfig)
                      .build();

              return templateDao
                  .getTemplateByEventNameAndChannel(eventName, channelType)
                  .switchIfEmpty(
                      Maybe.error(
                          ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
                              "No template found for event: "
                                  + eventName
                                  + " and channel: "
                                  + channelType)))
                  .toSingle()
                  .flatMap(
                      template ->
                          dispatchToRecipients(
                                  async,
                                  projectId,
                                  request,
                                  channel,
                                  template,
                                  idempotencyKey,
                                  new ArrayList<>(allRecipients))
                              .map(results -> buildBatchResponse(idempotencyKey, results)));
            });
  }

  private Single<NotificationBatchResponseDto> buildAndSendByEvent(
      String projectId,
      SendNotificationRequestDto request,
      String idempotencyKey,
      boolean async) {

    return mappingDao
        .getActiveMappingsWithChannelByProjectAndEvent(projectId, request.getEventName())
        .flatMap(
            rows -> {
              if (rows.isEmpty()) {
                return Single.error(
                    ServiceError.NOT_FOUND.getCustomException(
                        "No active channel mappings found for event: "
                            + request.getEventName()));
              }

              Map<Long, List<Row>> groupedByChannel =
                  rows.stream()
                      .collect(
                          Collectors.groupingBy(
                              row -> row.getLong("channel_id"),
                              LinkedHashMap::new,
                              Collectors.toList()));

              List<Single<List<NotificationResultDto>>> operations = new ArrayList<>();

              for (var entry : groupedByChannel.entrySet()) {
                Long channelId = entry.getKey();
                List<Row> channelRows = entry.getValue();
                Row firstRow = channelRows.get(0);

                ChannelType channelType =
                    ChannelType.valueOf(firstRow.getString("channel_type"));
                ChannelConfig channelConfig = extractConfig(firstRow);

                if (!request.getChannelTypes().contains(channelType)) {
                  continue;
                }

                List<String> dbRecipients =
                    channelRows.stream()
                        .map(r -> r.getString("recipient"))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

                Set<String> allRecipients =
                    resolveRecipients(request.getRecipients(), channelType, dbRecipients);

                if (allRecipients.isEmpty()) {
                  continue;
                }

                NotificationChannel channel =
                    NotificationChannel.builder()
                        .id(channelId)
                        .channelType(channelType)
                        .config(channelConfig)
                        .build();

                operations.add(
                    templateDao
                        .getTemplateByEventNameAndChannel(
                            request.getEventName(), channelType)
                        .switchIfEmpty(
                            Maybe.error(
                                ServiceError
                                    .INCORRECT_OR_MISSING_BODY_PARAMETERS
                                    .getCustomException(
                                        "No template found for event: "
                                            + request.getEventName()
                                            + " and channel: "
                                            + channelType)))
                        .toSingle()
                        .flatMap(
                            template ->
                                dispatchToRecipients(
                                    async,
                                    projectId,
                                    request,
                                    channel,
                                    template,
                                    idempotencyKey,
                                    new ArrayList<>(allRecipients))));
              }

              if (operations.isEmpty()) {
                if (hasAnyRecipients(request.getRecipients())) {
                  return Single.error(
                      ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
                          "No recipients resolved: mappings have no recipients and request recipients "
                              + "don't match the resolved channel types"));
                }
                return Single.just(
                    buildBatchResponse(idempotencyKey, Collections.emptyList()));
              }

              return Single.zip(
                  operations,
                  resultArrays -> {
                    List<NotificationResultDto> results = new ArrayList<>();
                    for (Object resultArray : resultArrays) {
                      @SuppressWarnings("unchecked")
                      List<NotificationResultDto> partial =
                          (List<NotificationResultDto>) resultArray;
                      results.addAll(partial);
                    }
                    return buildBatchResponse(idempotencyKey, results);
                  });
            });
  }

  // ==================== Dispatch / Send / Enqueue ====================

  private Single<List<NotificationResultDto>> dispatchToRecipients(
      boolean async,
      String projectId,
      SendNotificationRequestDto request,
      NotificationChannel channel,
      NotificationTemplate template,
      String idempotencyKey,
      List<String> recipients) {
    return async
        ? enqueueRecipients(projectId, request, channel, template, idempotencyKey, recipients)
        : sendToRecipients(projectId, request, channel, template, idempotencyKey, recipients);
  }

  private Single<List<NotificationResultDto>> sendToRecipients(
      String projectId,
      SendNotificationRequestDto request,
      NotificationChannel channel,
      NotificationTemplate template,
      String idempotencyKey,
      List<String> recipients) {

    ChannelType channelType = channel.getChannelType();

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
              String recipientKey =
                  idempotencyKey + ":" + channelType + ":" + recipient;

              Single<NotificationResultDto> sendChain =
                  sendSingleNotification(
                      projectId,
                      recipient,
                      recipientKey,
                      channel,
                      template,
                      subject,
                      provider,
                      request.getParams());

              return withSuppressionCheck(
                  projectId, recipient, channelType, sendChain);
            })
        .toList();
  }

  private Single<NotificationResultDto> sendSingleNotification(
      String projectId,
      String recipient,
      String idempotencyKey,
      NotificationChannel channel,
      NotificationTemplate template,
      String subject,
      NotificationProvider provider,
      Map<String, Object> params) {

    ChannelType channelType = channel.getChannelType();

    NotificationMessage message =
        NotificationMessage.builder()
            .projectId(projectId)
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
                    skippedResult(
                        recipient, channelType, "Duplicate notification (idempotency)"));
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
              return failedResult(recipient, channelType, e.getMessage());
            });
  }

  private Single<List<NotificationResultDto>> enqueueRecipients(
      String projectId,
      SendNotificationRequestDto request,
      NotificationChannel channel,
      NotificationTemplate template,
      String idempotencyKey,
      List<String> recipients) {

    ChannelType channelType = channel.getChannelType();
    String subject = extractSubjectFromBody(template.getBody());

    return Observable.fromIterable(recipients)
        .flatMapSingle(
            recipient -> {
              String recipientKey =
                  idempotencyKey + ":" + channelType + ":" + recipient;

              Single<NotificationResultDto> processingChain =
                  logDao
                      .getLogByIdempotency(
                          projectId, recipientKey, channelType, recipient)
                      .flatMapSingle(
                          existingLog -> {
                            log.debug(
                                "Skipping duplicate notification for recipient: {}",
                                recipient);
                            return Single.just(
                                skippedResult(
                                    recipient,
                                    channelType,
                                    "Duplicate notification (idempotency)"));
                          })
                      .switchIfEmpty(
                          Single.defer(
                              () ->
                                  logDao
                                      .insertLog(
                                          NotificationLog.builder()
                                              .projectId(projectId)
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
                                                            .status(
                                                                NotificationStatus.QUEUED)
                                                            .externalId(messageId)
                                                            .build());
                                          })));

              return withSuppressionCheck(
                      projectId, recipient, channelType, processingChain)
                  .onErrorReturn(
                      e -> failedResult(recipient, channelType, e.getMessage()));
            })
        .toList();
  }

  // ==================== Channels ====================

  @Override
  public Single<List<NotificationChannelDto>> getChannels(
      String projectId, ChannelType channelType) {
    Single<List<NotificationChannel>> query;
    if (projectId != null && channelType != null) {
      query = channelDao.getChannelsAccessibleByProjectAndType(projectId, channelType);
    } else if (projectId != null) {
      query = channelDao.getChannelsAccessibleByProject(projectId);
    } else {
      query = channelDao.getSharedChannels();
    }
    return query.map(channels -> channels.stream().map(this::toChannelDto).toList());
  }

  @Override
  public Maybe<NotificationChannelDto> getChannel(Long channelId) {
    return channelDao.getChannelById(channelId).map(this::toChannelDto);
  }

  @Override
  public Single<NotificationChannelDto> createChannel(CreateChannelRequestDto request) {
    validateChannelProjectId(request.getChannelType(), request.getProjectId());

    String projectId = request.getProjectId();

    return channelDao
        .getActiveChannelByProjectAndType(
            projectId != null ? projectId : "", request.getChannelType())
        .isEmpty()
        .flatMap(
            noActiveChannel -> {
              if (!noActiveChannel) {
                return Single.<NotificationChannelDto>error(
                    ServiceError.DUPLICATE_CHANNEL_TYPE.getCustomException(
                        "An active "
                            + request.getChannelType()
                            + " channel already exists"));
              }

              NotificationChannel channel =
                  NotificationChannel.builder()
                      .projectId(projectId)
                      .channelType(request.getChannelType())
                      .name(request.getName())
                      .config(request.getConfig())
                      .isActive(true)
                      .build();

              return channelDao
                  .createChannel(channel)
                  .flatMap(
                      channelId -> {
                        Single<NotificationChannelDto> result =
                            channelDao
                                .getChannelById(channelId)
                                .toSingle()
                                .map(this::toChannelDto);

                        if (request.getEventNames() != null
                            && !request.getEventNames().isEmpty()
                            && projectId != null) {
                          return Observable.fromIterable(request.getEventNames())
                              .flatMapSingle(
                                  eventName ->
                                      mappingDao.createMapping(
                                          ChannelEventMapping.builder()
                                              .projectId(projectId)
                                              .channelId(channelId)
                                              .eventName(eventName)
                                              .isActive(true)
                                              .build()))
                              .toList()
                              .flatMap(ids -> result);
                        }

                        return result;
                      });
            });
  }

  private void validateChannelProjectId(ChannelType channelType, String projectId) {
    boolean projectScoped =
        channelType == ChannelType.SLACK
            || channelType == ChannelType.TEAMS;

    if (projectScoped && (projectId == null || projectId.isBlank())) {
      throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          channelType + " channels require a projectId");
    }
    if (channelType == ChannelType.EMAIL && projectId != null && !projectId.isBlank()) {
      throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "EMAIL channels must not have a projectId (they are shared)");
    }
  }

  @Override
  public Single<NotificationChannelDto> updateChannel(
      Long channelId, UpdateChannelRequestDto request) {
    return channelDao
        .getChannelById(channelId)
        .switchIfEmpty(
            Maybe.error(ServiceError.NOT_FOUND.getCustomException("Channel not found")))
        .toSingle()
        .flatMap(
            existing -> {
              NotificationChannel updated =
                  NotificationChannel.builder()
                      .name(
                          request.getName() != null
                              ? request.getName()
                              : existing.getName())
                      .config(
                          request.getConfig() != null
                              ? request.getConfig()
                              : existing.getConfig())
                      .isActive(
                          request.getIsActive() != null
                              ? request.getIsActive()
                              : existing.getIsActive())
                      .build();

              return channelDao
                  .updateChannel(channelId, updated)
                  .flatMap(
                      count -> channelDao.getChannelById(channelId).toSingle())
                  .map(this::toChannelDto);
            });
  }

  @Override
  public Single<Boolean> deleteChannel(Long channelId) {
    return channelDao.deleteChannel(channelId).map(count -> count > 0);
  }

  // ==================== Templates (global) ====================

  @Override
  public Single<List<NotificationTemplateDto>> getTemplates(ChannelType channelType) {
    Single<List<NotificationTemplate>> query =
        channelType != null
            ? templateDao.getTemplatesByChannelType(channelType)
            : templateDao.getAllTemplates();
    return query.map(
        templates -> templates.stream().map(this::toTemplateDto).toList());
  }

  @Override
  public Maybe<NotificationTemplateDto> getTemplate(Long templateId) {
    return templateDao.getTemplateById(templateId).map(this::toTemplateDto);
  }

  @Override
  public Single<NotificationTemplateDto> createTemplate(CreateTemplateRequestDto request) {
    return templateDao
        .getLatestVersion(request.getEventName(), request.getChannelType())
        .flatMap(
            latestVersion -> {
              NotificationTemplate template =
                  NotificationTemplate.builder()
                      .eventName(request.getEventName())
                      .channelType(request.getChannelType())
                      .version(latestVersion + 1)
                      .body(request.getBody())
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
        .switchIfEmpty(
            Maybe.error(ServiceError.NOT_FOUND.getCustomException("Template not found")))
        .toSingle()
        .flatMap(
            existing -> {
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
                      .body(
                          request.getBody() != null
                              ? request.getBody()
                              : existing.getBody())
                      .isActive(
                          request.getIsActive() != null
                              ? request.getIsActive()
                              : existing.getIsActive())
                      .build();

              return templateDao
                  .updateTemplate(templateId, updated)
                  .flatMap(
                      count -> templateDao.getTemplateById(templateId).toSingle())
                  .map(this::toTemplateDto);
            });
  }

  @Override
  public Single<Boolean> deleteTemplate(Long templateId) {
    return templateDao.deleteTemplate(templateId).map(count -> count > 0);
  }

  // ==================== Mappings ====================

  @Override
  public Single<List<ChannelEventMappingDto>> getMappings(String projectId) {
    return mappingDao
        .getMappingsByProject(projectId)
        .flatMap(
            mappings ->
                Observable.fromIterable(mappings)
                    .flatMapSingle(this::enrichMappingDto)
                    .toList());
  }

  @Override
  public Single<ChannelEventMappingDto> createMapping(
      String projectId, CreateMappingRequestDto request) {
    return validateMappingEventName(request.getChannelId(), request.getEventName())
        .flatMap(
            ignored -> {
              ChannelEventMapping mapping =
                  ChannelEventMapping.builder()
                      .projectId(projectId)
                      .channelId(request.getChannelId())
                      .eventName(request.getEventName())
                      .recipient(request.getRecipient())
                      .recipientName(request.getRecipientName())
                      .isActive(true)
                      .build();

              return mappingDao
                  .createMapping(mapping)
                  .flatMap(id -> mappingDao.getMappingById(id).toSingle())
                  .flatMap(this::enrichMappingDto);
            });
  }

  @Override
  public Single<List<ChannelEventMappingDto>> createMappingsBatch(
      String projectId, BatchCreateMappingRequestDto request) {
    return Observable.fromIterable(request.getMappings())
        .flatMapSingle(req -> createMapping(projectId, req))
        .toList();
  }

  @Override
  public Single<ChannelEventMappingDto> updateMapping(
      Long mappingId, UpdateMappingRequestDto request) {
    return mappingDao
        .getMappingById(mappingId)
        .switchIfEmpty(
            Maybe.error(ServiceError.NOT_FOUND.getCustomException("Mapping not found")))
        .toSingle()
        .flatMap(
            existing -> {
              ChannelEventMapping updated =
                  ChannelEventMapping.builder()
                      .recipient(
                          request.getRecipient() != null
                              ? request.getRecipient()
                              : existing.getRecipient())
                      .recipientName(
                          request.getRecipientName() != null
                              ? request.getRecipientName()
                              : existing.getRecipientName())
                      .isActive(
                          request.getIsActive() != null
                              ? request.getIsActive()
                              : existing.getIsActive())
                      .build();

              return mappingDao
                  .updateMapping(mappingId, updated)
                  .flatMap(
                      count -> mappingDao.getMappingById(mappingId).toSingle())
                  .flatMap(this::enrichMappingDto);
            });
  }

  @Override
  public Single<Boolean> deleteMapping(Long mappingId) {
    return mappingDao.deleteMapping(mappingId).map(count -> count > 0);
  }

  private Single<ChannelEventMappingDto> enrichMappingDto(ChannelEventMapping mapping) {
    ChannelEventMappingDto.ChannelEventMappingDtoBuilder base =
        ChannelEventMappingDto.builder()
            .id(mapping.getId())
            .projectId(mapping.getProjectId())
            .channelId(mapping.getChannelId())
            .eventName(mapping.getEventName())
            .recipient(mapping.getRecipient())
            .recipientName(mapping.getRecipientName())
            .isActive(mapping.getIsActive())
            .createdAt(mapping.getCreatedAt())
            .updatedAt(mapping.getUpdatedAt());

    return channelDao
        .getChannelById(mapping.getChannelId())
        .map(
            channel ->
                base.channelType(channel.getChannelType())
                    .channelName(channel.getName())
                    .build())
        .defaultIfEmpty(base.build());
  }

  // ==================== Logs ====================

  @Override
  public Single<NotificationLogsResponseDto> getLogs(
      String projectId, int limit, int offset) {
    return logDao
        .getLogsByProject(projectId, limit, offset)
        .map(
            logs ->
                NotificationLogsResponseDto.builder()
                    .logs(logs.stream().map(this::toLogDto).toList())
                    .build());
  }

  @Override
  public Single<NotificationLogsResponseDto> getLogsByIdempotencyKey(
      String projectId, String idempotencyKey) {
    return logDao
        .getLogsByIdempotencyKey(projectId, idempotencyKey)
        .map(
            logs ->
                NotificationLogsResponseDto.builder()
                    .logs(logs.stream().map(this::toLogDto).toList())
                    .build());
  }

  // ==================== Mappers ====================

  private NotificationChannelDto toChannelDto(NotificationChannel channel) {
    return NotificationChannelDto.builder()
        .id(channel.getId())
        .projectId(channel.getProjectId())
        .channelType(channel.getChannelType())
        .name(channel.getName())
        .config(channel.getConfig())
        .isActive(channel.getIsActive())
        .createdAt(channel.getCreatedAt())
        .updatedAt(channel.getUpdatedAt())
        .build();
  }

  private NotificationTemplateDto toTemplateDto(NotificationTemplate template) {
    return NotificationTemplateDto.builder()
        .id(template.getId())
        .eventName(template.getEventName())
        .channelType(template.getChannelType())
        .version(template.getVersion())
        .body(template.getBody())
        .isActive(template.getIsActive())
        .createdAt(template.getCreatedAt())
        .updatedAt(template.getUpdatedAt())
        .build();
  }

  private NotificationLogDto toLogDto(NotificationLog log) {
    return NotificationLogDto.builder()
        .id(log.getId())
        .idempotencyKey(log.getIdempotencyKey())
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

  // ==================== Helpers ====================

  private NotificationBatchResponseDto buildBatchResponse(
      String idempotencyKey, List<NotificationResultDto> results) {
    int queued =
        (int)
            results.stream()
                .filter(
                    r ->
                        NotificationStatus.SENT.equals(r.getStatus())
                            || NotificationStatus.QUEUED.equals(r.getStatus()))
                .count();
    int failed =
        (int)
            results.stream()
                .filter(r -> NotificationStatus.FAILED.equals(r.getStatus()))
                .count();

    return NotificationBatchResponseDto.builder()
        .idempotencyKey(idempotencyKey)
        .totalRecipients(results.size())
        .queued(queued)
        .failed(failed)
        .results(results)
        .build();
  }

  private Single<NotificationResultDto> withSuppressionCheck(
      String projectId,
      String recipient,
      ChannelType channelType,
      Single<NotificationResultDto> downstream) {
    if (channelType != ChannelType.EMAIL) {
      return downstream;
    }
    return suppressionDao
        .isEmailSuppressed(projectId, recipient)
        .flatMap(
            suppressed -> {
              if (suppressed) {
                log.info("Skipping suppressed email: {}", recipient);
                return Single.just(
                    skippedResult(recipient, channelType, "Email is suppressed"));
              }
              return downstream;
            });
  }

  private NotificationResultDto skippedResult(
      String recipient, ChannelType channelType, String reason) {
    return NotificationResultDto.builder()
        .recipient(recipient)
        .channelType(channelType)
        .status(NotificationStatus.SKIPPED)
        .errorMessage(reason)
        .build();
  }

  private NotificationResultDto failedResult(
      String recipient, ChannelType channelType, String error) {
    return NotificationResultDto.builder()
        .recipient(recipient)
        .channelType(channelType)
        .status(NotificationStatus.FAILED)
        .errorMessage(error)
        .build();
  }

  private ChannelConfig extractConfig(Row row) {
    Object configValue = row.getValue("config");
    if (configValue instanceof io.vertx.core.json.JsonObject jsonObject) {
      return objectMapper.convertValue(jsonObject.getMap(), ChannelConfig.class);
    }
    return null;
  }

  private boolean hasAnyRecipients(RecipientsDto recipients) {
    if (recipients == null) {
      return false;
    }
    return (recipients.getEmails() != null && !recipients.getEmails().isEmpty())
        || (recipients.getSlackChannelIds() != null
            && !recipients.getSlackChannelIds().isEmpty())
        || (recipients.getSlackUserIds() != null
            && !recipients.getSlackUserIds().isEmpty())
        || (recipients.getSlackWebhookUrls() != null
            && !recipients.getSlackWebhookUrls().isEmpty())
        || (recipients.getTeamsWorkflowUrls() != null
            && !recipients.getTeamsWorkflowUrls().isEmpty());
  }

  private Set<String> resolveRecipients(
      RecipientsDto requestRecipients,
      ChannelType channelType,
      List<String> dbRecipients) {
    List<String> dynamicRecipients =
        getRecipientsForChannel(requestRecipients, channelType);
    if (!dynamicRecipients.isEmpty()) {
      return new LinkedHashSet<>(dynamicRecipients);
    }
    return new LinkedHashSet<>(dbRecipients);
  }

  private List<String> getRecipientsForChannel(
      RecipientsDto recipients, ChannelType channelType) {
    if (recipients == null) {
      return Collections.emptyList();
    }

    return switch (channelType) {
      case EMAIL ->
          recipients.getEmails() != null
              ? recipients.getEmails()
              : Collections.emptyList();
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
      case SLACK_WEBHOOK ->
          recipients.getSlackWebhookUrls() != null
              ? recipients.getSlackWebhookUrls()
              : Collections.emptyList();
      case TEAMS ->
          recipients.getTeamsWorkflowUrls() != null
              ? recipients.getTeamsWorkflowUrls()
              : Collections.emptyList();
      default -> Collections.emptyList();
    };
  }

  private Single<Boolean> validateMappingEventName(Long channelId, String eventName) {
    return channelDao
        .getChannelById(channelId)
        .switchIfEmpty(
            Maybe.error(
                ServiceError.NOT_FOUND.getCustomException(
                    "Channel not found: " + channelId)))
        .toSingle()
        .flatMap(
            channel ->
                templateDao
                    .getTemplateByEventNameAndChannel(
                        eventName, channel.getChannelType())
                    .switchIfEmpty(
                        Maybe.error(
                            ServiceError
                                .INCORRECT_OR_MISSING_BODY_PARAMETERS
                                .getCustomException(
                                    "No template found for event '"
                                        + eventName
                                        + "' and channel type "
                                        + channel.getChannelType()
                                        + ". Verify the event name is correct and a template exists.")))
                    .toSingle()
                    .map(template -> true));
  }

  private String extractSubjectFromBody(TemplateBody body) {
    if (body == null) {
      return null;
    }
    if (body instanceof EmailTemplateBody email) {
      return email.getSubject();
    }
    if (body instanceof TeamsTemplateBody teams) {
      return teams.getTitle() != null ? teams.getTitle() : teams.getText();
    }
    if (body instanceof SlackTemplateBody slack) {
      return slack.getText();
    }
    return null;
  }
}
