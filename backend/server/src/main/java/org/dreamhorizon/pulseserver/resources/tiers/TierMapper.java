package org.dreamhorizon.pulseserver.resources.tiers;

import org.dreamhorizon.pulseserver.resources.tiers.models.CreateTierRestRequest;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierListRestResponse;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierPublicListRestResponse;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierPublicRestResponse;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierRestResponse;
import org.dreamhorizon.pulseserver.resources.tiers.models.UpdateTierRestRequest;
import org.dreamhorizon.pulseserver.resources.tiers.models.UsageLimitPublicRestDto;
import org.dreamhorizon.pulseserver.resources.tiers.models.UsageLimitValueRestDto;
import org.dreamhorizon.pulseserver.service.tier.models.CreateTierRequest;
import org.dreamhorizon.pulseserver.service.tier.models.TierInfo;
import org.dreamhorizon.pulseserver.service.tier.models.TierPublicInfo;
import org.dreamhorizon.pulseserver.service.tier.models.UpdateTierRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitPublicValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper
public abstract class TierMapper {

  public static final TierMapper INSTANCE = Mappers.getMapper(TierMapper.class);

  // Request mappings
  public abstract CreateTierRequest toCreateTierRequest(CreateTierRestRequest request);

  @Mapping(target = "tierId", source = "tierId")
  @Mapping(target = "displayName", source = "request.displayName")
  @Mapping(target = "isCustomLimitsAllowed", source = "request.isCustomLimitsAllowed")
  @Mapping(target = "usageLimitDefaults", source = "request.usageLimitDefaults")
  public abstract UpdateTierRequest toUpdateTierRequest(Integer tierId, UpdateTierRestRequest request);

  // Response mappings
  public TierRestResponse toTierRestResponse(TierInfo info) {
    if (info == null) {
      return null;
    }
    return TierRestResponse.builder()
        .tierId(info.getTierId())
        .name(info.getName())
        .displayName(info.getDisplayName())
        .isCustomLimitsAllowed(info.getIsCustomLimitsAllowed())
        .usageLimitDefaults(toUsageLimitValueRestDtoMap(info.getUsageLimitDefaults()))
        .isActive(info.getIsActive())
        .createdAt(info.getCreatedAt())
        .build();
  }

  public TierPublicRestResponse toTierPublicRestResponse(TierPublicInfo info) {
    if (info == null) {
      return null;
    }
    return TierPublicRestResponse.builder()
        .tierId(info.getTierId())
        .name(info.getName())
        .displayName(info.getDisplayName())
        .isCustomLimitsAllowed(info.getIsCustomLimitsAllowed())
        .usageLimits(toUsageLimitPublicRestDtoMap(info.getUsageLimits()))
        .build();
  }

  // List response mappings
  public TierListRestResponse toTierListRestResponse(List<TierInfo> tiers) {
    List<TierRestResponse> responses = tiers.stream()
        .map(this::toTierRestResponse)
        .collect(Collectors.toList());
    return TierListRestResponse.builder()
        .tiers(responses)
        .totalCount(responses.size())
        .build();
  }

  public TierPublicListRestResponse toTierPublicListRestResponse(List<TierPublicInfo> tiers) {
    List<TierPublicRestResponse> responses = tiers.stream()
        .map(this::toTierPublicRestResponse)
        .collect(Collectors.toList());
    return TierPublicListRestResponse.builder()
        .tiers(responses)
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

