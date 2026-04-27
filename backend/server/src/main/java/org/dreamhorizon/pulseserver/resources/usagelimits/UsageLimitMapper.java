package org.dreamhorizon.pulseserver.resources.usagelimits;

import org.dreamhorizon.pulseserver.resources.tiers.models.UsageLimitPublicRestDto;
import org.dreamhorizon.pulseserver.resources.tiers.models.UsageLimitValueRestDto;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.NotificationStatusRestDto;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.NotificationStatusRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectLimitHistoryRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitListRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitPublicRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ResetLimitsRestRequest;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.SetCustomLimitsRestRequest;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.UsageNotificationRestDto;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.UsageNotificationRestResponse;
import org.dreamhorizon.pulseserver.service.usagelimit.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitPublicInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ResetLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.SetCustomLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitPublicValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService.NotificationStatusResponse;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotification;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotificationResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper
public abstract class UsageLimitMapper {

  public static final UsageLimitMapper INSTANCE = Mappers.getMapper(UsageLimitMapper.class);

  // Request mappings
  @Mapping(target = "projectId", source = "projectId")
  @Mapping(target = "limits", source = "request.limits")
  @Mapping(target = "performedBy", source = "performedBy")
  public abstract SetCustomLimitsRequest toSetCustomLimitsRequest(
      String projectId, SetCustomLimitsRestRequest request, String performedBy);

  @Mapping(target = "projectId", source = "projectId")
  @Mapping(target = "tierId", source = "request.tierId")
  @Mapping(target = "performedBy", source = "performedBy")
  public abstract ResetLimitsRequest toResetLimitsRequest(
      String projectId, ResetLimitsRestRequest request, String performedBy);

  // Response mappings
  public ProjectUsageLimitRestResponse toRestResponse(ProjectUsageLimitInfo info) {
    if (info == null) {
      return null;
    }
    return ProjectUsageLimitRestResponse.builder()
        .projectUsageLimitId(info.getProjectUsageLimitId())
        .projectId(info.getProjectId())
        .usageLimits(toUsageLimitValueRestDtoMap(info.getUsageLimits()))
        .isActive(info.getIsActive())
        .createdAt(info.getCreatedAt())
        .createdBy(info.getCreatedBy())
        .disabledAt(info.getDisabledAt())
        .disabledBy(info.getDisabledBy())
        .disabledReason(info.getDisabledReason())
        .notificationStatus(mapNotificationStatus(info.getNotificationStatus()))
        .build();
  }

  private NotificationStatusRestDto mapNotificationStatus(NotificationStatus notificationStatus) {
    if (notificationStatus == null) {
      return null;
    }
    return NotificationStatusRestDto.builder()
        .thresholdsNotified(notificationStatus.getThresholdsNotified())
        .projectUsageLimitId(notificationStatus.getProjectUsageLimitId())
        .notificationActive(notificationStatus.getNotificationActive())
        .createdAt(notificationStatus.getCreatedAt())
        .build();
  }

  public ProjectUsageLimitPublicRestResponse toPublicRestResponse(ProjectUsageLimitPublicInfo info) {
    if (info == null) {
      return null;
    }
    return ProjectUsageLimitPublicRestResponse.builder()
        .projectId(info.getProjectId())
        .usageLimits(toUsageLimitPublicRestDtoMap(info.getUsageLimits()))
        .build();
  }

  // List response mappings
  public ProjectUsageLimitListRestResponse toListRestResponse(List<ProjectUsageLimitInfo> infos) {
    List<ProjectUsageLimitRestResponse> responses = infos.stream()
        .map(this::toRestResponse)
        .collect(Collectors.toList());
    return ProjectUsageLimitListRestResponse.builder()
        .limits(responses)
        .totalCount(responses.size())
        .build();
  }

  public ProjectLimitHistoryRestResponse toHistoryRestResponse(String projectId, List<ProjectUsageLimitInfo> infos) {
    List<ProjectUsageLimitRestResponse> responses = infos.stream()
        .map(this::toRestResponse)
        .collect(Collectors.toList());
    return ProjectLimitHistoryRestResponse.builder()
        .projectId(projectId)
        .history(responses)
        .totalCount(responses.size())
        .build();
  }

  // Usage limit value mappings
  public abstract UsageLimitValue toUsageLimitValue(UsageLimitValueRestDto dto);

  public abstract UsageLimitValueRestDto toUsageLimitValueRestDto(UsageLimitValue value);

  public abstract UsageLimitPublicRestDto toUsageLimitPublicRestDto(UsageLimitPublicValue value);

  // Map conversions
  public Map<String, UsageLimitValue> toUsageLimitValueMap(Map<String, UsageLimitValueRestDto> dtoMap) {
    if (dtoMap == null) {
      return null;
    }
    return dtoMap.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> toUsageLimitValue(entry.getValue())
        ));
  }

  public Map<String, UsageLimitValueRestDto> toUsageLimitValueRestDtoMap(Map<String, UsageLimitValue> valueMap) {
    if (valueMap == null) {
      return null;
    }
    return valueMap.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> toUsageLimitValueRestDto(entry.getValue())
        ));
  }

  public Map<String, UsageLimitPublicRestDto> toUsageLimitPublicRestDtoMap(Map<String, UsageLimitPublicValue> valueMap) {
    if (valueMap == null) {
      return null;
    }
    return valueMap.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> toUsageLimitPublicRestDto(entry.getValue())
        ));
  }

  // Usage notification mappings
  public NotificationStatusRestResponse toNotificationStatusRestResponse(NotificationStatusResponse response) {
    if (response == null) {
      return null;
    }
    return NotificationStatusRestResponse.builder()
        .projectId(response.getProjectId())
        .month(response.getMonth())
        .thresholdsNotified(response.getThresholdsNotified())
        .projectUsageLimitId(response.getProjectUsageLimitId())
        .notificationActive(response.getNotificationActive())
        .createdAt(response.getCreatedAt())
        .updatedAt(response.getUpdatedAt())
        .build();
  }

  public UsageNotificationRestResponse toUsageNotificationResponse(UsageNotificationResult result) {
    if (result == null) {
      return null;
    }
    
    List<UsageNotificationRestDto> notificationDtos = result.getNotifications().stream()
        .map(this::toUsageNotificationDto)
        .collect(Collectors.toList());
    
    String checkedAt = result.getCheckedAt() != null 
        ? result.getCheckedAt().toString()
        : null;
    
    return UsageNotificationRestResponse.builder()
        .notifications(notificationDtos)
        .totalProjectsChecked(result.getTotalProjectsChecked())
        .notificationsDue(result.getNotificationsDue())
        .checkedAt(checkedAt)
        .build();
  }

  public UsageNotificationRestDto toUsageNotificationDto(UsageNotification notification) {
    if (notification == null) {
      return null;
    }
    return UsageNotificationRestDto.builder()
        .projectId(notification.getProjectId())
        .projectName(notification.getProjectName())
        .threshold(notification.getThreshold())
        .thresholdsToMark(notification.getThresholdsToMark())
        .notifyFor(notification.getNotifyFor())
        .templateName(notification.getTemplateName())
        .sessionsUsed(notification.getSessionsUsed())
        .sessionsLimit(notification.getSessionsLimit())
        .sessionsPercentage(notification.getSessionsPercentage())
        .sessionsOverage(notification.getSessionsOverage())
        .sessionsBlocked(notification.getSessionsBlocked())
        .sessionsAtLimit(notification.getSessionsAtLimit())
        .eventsUsed(notification.getEventsUsed())
        .eventsLimit(notification.getEventsLimit())
        .eventsPercentage(notification.getEventsPercentage())
        .eventsOverage(notification.getEventsOverage())
        .eventsBlocked(notification.getEventsBlocked())
        .eventsAtLimit(notification.getEventsAtLimit())
        .eventsPercentageDisplay(notification.getEventsPercentageDisplay())
        .sessionsPercentageDisplay(notification.getSessionsPercentageDisplay())
        .recipientEmails(notification.getRecipientEmails())
        .tenantId(notification.getTenantId())
        .build();
  }
}

