package org.dreamhorizon.pulseserver.resources.usagelimits;

import org.dreamhorizon.pulseserver.resources.tiers.models.UsageLimitPublicRestDto;
import org.dreamhorizon.pulseserver.resources.tiers.models.UsageLimitValueRestDto;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectLimitHistoryRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitListRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitPublicRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ResetLimitsRestRequest;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.SetCustomLimitsRestRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitPublicInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ResetLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.SetCustomLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitPublicValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
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
}

