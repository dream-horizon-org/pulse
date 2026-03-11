package org.dreamhorizon.pulseserver.service.notification;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.resources.notification.models.*;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;

public interface NotificationService {

  Single<NotificationBatchResponseDto> sendNotification(
      String projectId, SendNotificationRequestDto request);

  Single<NotificationBatchResponseDto> sendNotificationAsync(
      String projectId, SendNotificationRequestDto request);

  // Channels
  Single<List<NotificationChannelDto>> getChannels(String projectId, ChannelType channelType);

  Maybe<NotificationChannelDto> getChannel(Long channelId);

  Single<NotificationChannelDto> createChannel(CreateChannelRequestDto request);

  Single<NotificationChannelDto> updateChannel(Long channelId, UpdateChannelRequestDto request);

  Single<Boolean> deleteChannel(Long channelId);

  // Templates (global, no projectId)
  Single<List<NotificationTemplateDto>> getTemplates(ChannelType channelType);

  Maybe<NotificationTemplateDto> getTemplate(Long templateId);

  Single<NotificationTemplateDto> createTemplate(CreateTemplateRequestDto request);

  Single<NotificationTemplateDto> updateTemplate(Long templateId, UpdateTemplateRequestDto request);

  Single<Boolean> deleteTemplate(Long templateId);

  // Mappings
  Single<List<ChannelEventMappingDto>> getMappings(String projectId);

  Single<ChannelEventMappingDto> createMapping(String projectId, CreateMappingRequestDto request);

  Single<List<ChannelEventMappingDto>> createMappingsBatch(
      String projectId, BatchCreateMappingRequestDto request);

  Single<ChannelEventMappingDto> updateMapping(Long mappingId, UpdateMappingRequestDto request);

  Single<Boolean> deleteMapping(Long mappingId);

  // Logs
  Single<NotificationLogsResponseDto> getLogs(String projectId, int limit, int offset);

  Single<NotificationLogsResponseDto> getLogsByIdempotencyKey(
      String projectId, String idempotencyKey);
}
