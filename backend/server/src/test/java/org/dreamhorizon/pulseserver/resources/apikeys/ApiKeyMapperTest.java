package org.dreamhorizon.pulseserver.resources.apikeys;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.dreamhorizon.pulseserver.resources.apikeys.models.CreateApiKeyRestRequest;
import org.dreamhorizon.pulseserver.resources.apikeys.models.RevokeApiKeyRestRequest;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyInfo;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyPublicInfo;
import org.dreamhorizon.pulseserver.service.apikey.models.CreateApiKeyRequest;
import org.dreamhorizon.pulseserver.service.apikey.models.RevokeApiKeyRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ApiKeyMapperTest {

  private final ApiKeyMapper mapper = ApiKeyMapper.INSTANCE;

  @Nested
  class ToCreateApiKeyRequest {

    @Test
    void shouldMapCreateRequest() {
      CreateApiKeyRestRequest restRequest = CreateApiKeyRestRequest.builder()
          .displayName("My API Key")
          .expiresAt(Instant.now().plusSeconds(86400))
          .build();

      CreateApiKeyRequest result = mapper.toCreateApiKeyRequest("project-1", restRequest, "user@example.com");

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("project-1");
      assertThat(result.getDisplayName()).isEqualTo("My API Key");
      assertThat(result.getExpiresAt()).isNotNull();
      assertThat(result.getCreatedBy()).isEqualTo("user@example.com");
    }
  }

  @Nested
  class ToRevokeApiKeyRequest {

    @Test
    void shouldMapRevokeRequest() {
      RevokeApiKeyRestRequest restRequest = RevokeApiKeyRestRequest.builder()
          .gracePeriodDays(7)
          .build();

      RevokeApiKeyRequest result = mapper.toRevokeApiKeyRequest("project-1", 42L, restRequest, "admin@example.com");

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("project-1");
      assertThat(result.getApiKeyId()).isEqualTo(42L);
      assertThat(result.getGracePeriodDays()).isEqualTo(7);
      assertThat(result.getRevokedBy()).isEqualTo("admin@example.com");
    }
  }

  @Nested
  class ToCreateApiKeyRestResponse {

    @Test
    void shouldMapApiKeyInfoToCreateResponse() {
      ApiKeyInfo info = ApiKeyInfo.builder()
          .apiKeyId(1L)
          .projectId("project-1")
          .displayName("My Key")
          .rawApiKey("proj_abc123")
          .expiresAt(Instant.now().plusSeconds(86400))
          .createdAt(Instant.now())
          .build();

      var result = mapper.toCreateApiKeyRestResponse(info);

      assertThat(result).isNotNull();
      assertThat(result.getApiKeyId()).isEqualTo(1L);
      assertThat(result.getProjectId()).isEqualTo("project-1");
      assertThat(result.getDisplayName()).isEqualTo("My Key");
      assertThat(result.getApiKey()).isEqualTo("proj_abc123");
      assertThat(result.getExpiresAt()).isNotNull();
      assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldReturnNullWhenInfoIsNull() {
      assertThat(mapper.toCreateApiKeyRestResponse(null)).isNull();
    }
  }

  @Nested
  class ToApiKeyRestResponse {

    @Test
    void shouldMapApiKeyPublicInfoToRestResponse() {
      ApiKeyPublicInfo info = ApiKeyPublicInfo.builder()
          .apiKeyId(1L)
          .projectId("project-1")
          .displayName("My Key")
          .rawApiKey("proj_abc123")
          .isActive(true)
          .expiresAt(null)
          .gracePeriodEndsAt(null)
          .createdBy("user@example.com")
          .createdAt(Instant.now())
          .deactivatedAt(null)
          .deactivatedBy(null)
          .deactivationReason(null)
          .build();

      var result = mapper.toApiKeyRestResponse(info);

      assertThat(result).isNotNull();
      assertThat(result.getApiKeyId()).isEqualTo(1L);
      assertThat(result.getProjectId()).isEqualTo("project-1");
      assertThat(result.getDisplayName()).isEqualTo("My Key");
      assertThat(result.getApiKey()).isEqualTo("proj_abc123");
      assertThat(result.getIsActive()).isTrue();
      assertThat(result.getCreatedBy()).isEqualTo("user@example.com");
    }

    @Test
    void shouldReturnNullWhenInfoIsNull() {
      assertThat(mapper.toApiKeyRestResponse(null)).isNull();
    }
  }

  @Nested
  class ToValidApiKeyRestResponse {

    @Test
    void shouldMapApiKeyInfoToValidResponse() {
      ApiKeyInfo info = ApiKeyInfo.builder()
          .apiKeyId(1L)
          .projectId("project-1")
          .rawApiKey("proj_abc123")
          .isActive(true)
          .expiresAt(null)
          .gracePeriodEndsAt(null)
          .build();

      var result = mapper.toValidApiKeyRestResponse(info);

      assertThat(result).isNotNull();
      assertThat(result.getApiKeyId()).isEqualTo(1L);
      assertThat(result.getProjectId()).isEqualTo("project-1");
      assertThat(result.getApiKey()).isEqualTo("proj_abc123");
      assertThat(result.getIsActive()).isTrue();
    }

    @Test
    void shouldReturnNullWhenInfoIsNull() {
      assertThat(mapper.toValidApiKeyRestResponse(null)).isNull();
    }
  }

  @Nested
  class ListMappings {

    @Test
    void shouldMapToApiKeyListRestResponse() {
      ApiKeyPublicInfo info = ApiKeyPublicInfo.builder()
          .apiKeyId(1L)
          .projectId("project-1")
          .displayName("Key 1")
          .rawApiKey("key1")
          .isActive(true)
          .build();

      var result = mapper.toApiKeyListRestResponse(List.of(info));

      assertThat(result).isNotNull();
      assertThat(result.getApiKeys()).hasSize(1);
      assertThat(result.getCount()).isEqualTo(1);
      assertThat(result.getApiKeys().get(0).getApiKeyId()).isEqualTo(1L);
    }

    @Test
    void shouldMapToValidApiKeyListRestResponse() {
      ApiKeyInfo info = ApiKeyInfo.builder()
          .apiKeyId(1L)
          .projectId("project-1")
          .rawApiKey("key1")
          .isActive(true)
          .build();

      var result = mapper.toValidApiKeyListRestResponse(List.of(info));

      assertThat(result).isNotNull();
      assertThat(result.getApiKeys()).hasSize(1);
      assertThat(result.getCount()).isEqualTo(1);
    }
  }
}
