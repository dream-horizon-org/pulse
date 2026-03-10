package org.dreamhorizon.pulseserver.service.notification;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.resources.notification.models.*;

public interface NotificationService {

  Single<NotificationBatchResponseDto> sendNotification(
      String projectId, SendNotificationRequestDto request);

  Single<NotificationBatchResponseDto> sendNotificationAsync(
      String projectId, SendNotificationRequestDto request);

  Single<List<NotificationChannelDto>> getChannels(String projectId);

  Maybe<NotificationChannelDto> getChannel(Long channelId);

  Single<NotificationChannelDto> createChannel(String projectId, CreateChannelRequestDto request);

  Single<NotificationChannelDto> updateChannel(Long channelId, UpdateChannelRequestDto request);

  Single<Boolean> deleteChannel(Long channelId);

  Single<List<NotificationTemplateDto>> getTemplates(String projectId);

  Maybe<NotificationTemplateDto> getTemplate(Long templateId);

  Single<NotificationTemplateDto> createTemplate(String projectId, CreateTemplateRequestDto request);

  Single<NotificationTemplateDto> updateTemplate(Long templateId, UpdateTemplateRequestDto request);

  Single<Boolean> deleteTemplate(Long templateId);

  Single<NotificationLogsResponseDto> getLogs(String projectId, int limit, int offset);

  Single<NotificationLogsResponseDto> getLogsByBatch(String projectId, String batchId);
}
