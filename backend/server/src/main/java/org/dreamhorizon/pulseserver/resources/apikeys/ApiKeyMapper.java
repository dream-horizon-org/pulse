package org.dreamhorizon.pulseserver.resources.apikeys;

import org.dreamhorizon.pulseserver.resources.apikeys.models.ApiKeyListRestResponse;
import org.dreamhorizon.pulseserver.resources.apikeys.models.ApiKeyRestResponse;
import org.dreamhorizon.pulseserver.resources.apikeys.models.CreateApiKeyRestRequest;
import org.dreamhorizon.pulseserver.resources.apikeys.models.CreateApiKeyRestResponse;
import org.dreamhorizon.pulseserver.resources.apikeys.models.RevokeApiKeyRestRequest;
import org.dreamhorizon.pulseserver.resources.apikeys.models.ValidApiKeyListRestResponse;
import org.dreamhorizon.pulseserver.resources.apikeys.models.ValidApiKeyRestResponse;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyInfo;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyPublicInfo;
import org.dreamhorizon.pulseserver.service.apikey.models.CreateApiKeyRequest;
import org.dreamhorizon.pulseserver.service.apikey.models.RevokeApiKeyRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public abstract class ApiKeyMapper {

  public static final ApiKeyMapper INSTANCE = Mappers.getMapper(ApiKeyMapper.class);

  // ==================== Request Mappings ====================

  @Mapping(target = "projectId", source = "projectId")
  @Mapping(target = "displayName", source = "request.displayName")
  @Mapping(target = "expiresAt", source = "request.expiresAt")
  @Mapping(target = "createdBy", source = "createdBy")
  public abstract CreateApiKeyRequest toCreateApiKeyRequest(
      String projectId,
      CreateApiKeyRestRequest request,
      String createdBy
  );

  @Mapping(target = "projectId", source = "projectId")
  @Mapping(target = "apiKeyId", source = "apiKeyId")
  @Mapping(target = "gracePeriodDays", source = "request.gracePeriodDays")
  @Mapping(target = "revokedBy", source = "revokedBy")
  public abstract RevokeApiKeyRequest toRevokeApiKeyRequest(
      String projectId,
      Long apiKeyId,
      RevokeApiKeyRestRequest request,
      String revokedBy
  );

  // ==================== Response Mappings ====================

  /**
   * Maps ApiKeyInfo to CreateApiKeyRestResponse (includes raw key).
   */
  public CreateApiKeyRestResponse toCreateApiKeyRestResponse(ApiKeyInfo info) {
    if (info == null) {
      return null;
    }
    return CreateApiKeyRestResponse.builder()
        .apiKeyId(info.getApiKeyId())
        .projectId(info.getProjectId())
        .displayName(info.getDisplayName())
        .apiKey(info.getRawApiKey())
        .expiresAt(info.getExpiresAt())
        .createdAt(info.getCreatedAt())
        .build();
  }

  /**
   * Maps ApiKeyPublicInfo to ApiKeyRestResponse.
   */
  public ApiKeyRestResponse toApiKeyRestResponse(ApiKeyPublicInfo info) {
    if (info == null) {
      return null;
    }
    return ApiKeyRestResponse.builder()
        .apiKeyId(info.getApiKeyId())
        .projectId(info.getProjectId())
        .displayName(info.getDisplayName())
        .apiKey(info.getRawApiKey())
        .isActive(info.getIsActive())
        .expiresAt(info.getExpiresAt())
        .gracePeriodEndsAt(info.getGracePeriodEndsAt())
        .createdBy(info.getCreatedBy())
        .createdAt(info.getCreatedAt())
        .deactivatedAt(info.getDeactivatedAt())
        .deactivatedBy(info.getDeactivatedBy())
        .deactivationReason(info.getDeactivationReason())
        .build();
  }

  /**
   * Maps ApiKeyInfo to ValidApiKeyRestResponse (includes raw key for internal use).
   */
  public ValidApiKeyRestResponse toValidApiKeyRestResponse(ApiKeyInfo info) {
    if (info == null) {
      return null;
    }
    return ValidApiKeyRestResponse.builder()
        .apiKeyId(info.getApiKeyId())
        .projectId(info.getProjectId())
        .apiKey(info.getRawApiKey())
        .isActive(info.getIsActive())
        .expiresAt(info.getExpiresAt())
        .gracePeriodEndsAt(info.getGracePeriodEndsAt())
        .build();
  }

  // ==================== List Mappings ====================

  public ApiKeyListRestResponse toApiKeyListRestResponse(List<ApiKeyPublicInfo> apiKeys) {
    List<ApiKeyRestResponse> responses = apiKeys.stream()
        .map(this::toApiKeyRestResponse)
        .collect(Collectors.toList());
    return ApiKeyListRestResponse.builder()
        .apiKeys(responses)
        .count(responses.size())
        .build();
  }

  public ValidApiKeyListRestResponse toValidApiKeyListRestResponse(List<ApiKeyInfo> apiKeys) {
    List<ValidApiKeyRestResponse> responses = apiKeys.stream()
        .map(this::toValidApiKeyRestResponse)
        .collect(Collectors.toList());
    return ValidApiKeyListRestResponse.builder()
        .apiKeys(responses)
        .count(responses.size())
        .build();
  }
}

