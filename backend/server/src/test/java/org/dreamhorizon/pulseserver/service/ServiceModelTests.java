package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyInfo;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyPublicInfo;
import org.dreamhorizon.pulseserver.service.apikey.models.CreateApiKeyRequest;
import org.dreamhorizon.pulseserver.service.apikey.models.RevokeApiKeyRequest;
import org.dreamhorizon.pulseserver.service.tier.models.CreateTierRequest;
import org.dreamhorizon.pulseserver.service.tier.models.TierInfo;
import org.dreamhorizon.pulseserver.service.tier.models.TierPublicInfo;
import org.dreamhorizon.pulseserver.service.tier.models.TierType;
import org.dreamhorizon.pulseserver.service.tier.models.UpdateTierRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.DataType;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitPublicInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ResetLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.SetCustomLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitParameter;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitPublicValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.WindowType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServiceModelTests {

  @Nested
  class TierModels {

    @Test
    void createTierRequest_shouldHaveAllFields() {
      Map<String, UsageLimitValue> defaults = Map.of(
          "key", UsageLimitValue.builder().value(100L).build());
      CreateTierRequest req = CreateTierRequest.builder()
          .name("premium")
          .displayName("Premium Tier")
          .isCustomLimitsAllowed(true)
          .usageLimitDefaults(defaults)
          .build();

      assertThat(req.getName()).isEqualTo("premium");
      assertThat(req.getDisplayName()).isEqualTo("Premium Tier");
      assertThat(req.getIsCustomLimitsAllowed()).isTrue();
      assertThat(req.getUsageLimitDefaults()).isEqualTo(defaults);
    }

    @Test
    void tierInfo_shouldHaveAllFields() {
      Map<String, UsageLimitValue> defaults = Map.of(
          "key", UsageLimitValue.builder().value(500L).build());
      Instant createdAt = Instant.now();
      TierInfo info = TierInfo.builder()
          .tierId(1)
          .name("free")
          .displayName("Free Tier")
          .isCustomLimitsAllowed(false)
          .usageLimitDefaults(defaults)
          .isActive(true)
          .createdAt(createdAt)
          .build();

      assertThat(info.getTierId()).isEqualTo(1);
      assertThat(info.getName()).isEqualTo("free");
      assertThat(info.getDisplayName()).isEqualTo("Free Tier");
      assertThat(info.getIsCustomLimitsAllowed()).isFalse();
      assertThat(info.getUsageLimitDefaults()).isEqualTo(defaults);
      assertThat(info.getIsActive()).isTrue();
      assertThat(info.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void tierPublicInfo_shouldHaveAllFields() {
      Map<String, UsageLimitPublicValue> limits = Map.of(
          "max_events", UsageLimitPublicValue.builder()
              .displayName("Max Events")
              .windowType("MONTHLY")
              .value(1000L)
              .build());
      TierPublicInfo info = TierPublicInfo.builder()
          .tierId(2)
          .name("enterprise")
          .displayName("Enterprise")
          .isCustomLimitsAllowed(true)
          .usageLimits(limits)
          .build();

      assertThat(info.getTierId()).isEqualTo(2);
      assertThat(info.getName()).isEqualTo("enterprise");
      assertThat(info.getDisplayName()).isEqualTo("Enterprise");
      assertThat(info.getIsCustomLimitsAllowed()).isTrue();
      assertThat(info.getUsageLimits()).isEqualTo(limits);
    }

    @Test
    void updateTierRequest_shouldHaveAllFields() {
      Map<String, UsageLimitValue> defaults = Map.of(
          "key", UsageLimitValue.builder().value(200L).build());
      UpdateTierRequest req = UpdateTierRequest.builder()
          .tierId(1)
          .displayName("Updated Display")
          .isCustomLimitsAllowed(true)
          .usageLimitDefaults(defaults)
          .build();

      assertThat(req.getTierId()).isEqualTo(1);
      assertThat(req.getDisplayName()).isEqualTo("Updated Display");
      assertThat(req.getIsCustomLimitsAllowed()).isTrue();
      assertThat(req.getUsageLimitDefaults()).isEqualTo(defaults);
    }

    @Test
    void tierType_shouldHaveAllValues() {
      assertThat(TierType.FREE.getValue()).isEqualTo("free");
      assertThat(TierType.ENTERPRISE.getValue()).isEqualTo("enterprise");

      assertThat(TierType.valueOf("FREE")).isEqualTo(TierType.FREE);
      assertThat(TierType.valueOf("ENTERPRISE")).isEqualTo(TierType.ENTERPRISE);

      assertThat(TierType.fromString("free")).isEqualTo(TierType.FREE);
      assertThat(TierType.fromString("ENTERPRISE")).isEqualTo(TierType.ENTERPRISE);
      assertThat(TierType.fromString("  enterprise  ")).isEqualTo(TierType.ENTERPRISE);
      assertThat(TierType.fromString(null)).isNull();

      assertThrows(IllegalArgumentException.class, () -> TierType.fromString("invalid"));
    }
  }

  @Nested
  class ApiKeyModels {

    @Test
    void apiKeyInfo_shouldHaveAllFields() {
      Instant now = Instant.now();
      ApiKeyInfo info = ApiKeyInfo.builder()
          .apiKeyId(1L)
          .projectId("proj-1")
          .displayName("Default")
          .rawApiKey("raw_key")
          .isActive(true)
          .expiresAt(now)
          .gracePeriodEndsAt(now)
          .createdBy("user@test.com")
          .createdAt(now)
          .deactivatedAt(now)
          .deactivatedBy("admin@test.com")
          .deactivationReason("revoked")
          .build();

      assertThat(info.getApiKeyId()).isEqualTo(1L);
      assertThat(info.getProjectId()).isEqualTo("proj-1");
      assertThat(info.getDisplayName()).isEqualTo("Default");
      assertThat(info.getRawApiKey()).isEqualTo("raw_key");
      assertThat(info.getIsActive()).isTrue();
      assertThat(info.getExpiresAt()).isEqualTo(now);
      assertThat(info.getGracePeriodEndsAt()).isEqualTo(now);
      assertThat(info.getCreatedBy()).isEqualTo("user@test.com");
      assertThat(info.getCreatedAt()).isEqualTo(now);
      assertThat(info.getDeactivatedAt()).isEqualTo(now);
      assertThat(info.getDeactivatedBy()).isEqualTo("admin@test.com");
      assertThat(info.getDeactivationReason()).isEqualTo("revoked");
    }

    @Test
    void apiKeyPublicInfo_shouldHaveAllFields() {
      Instant now = Instant.now();
      ApiKeyPublicInfo info = ApiKeyPublicInfo.builder()
          .apiKeyId(2L)
          .projectId("proj-2")
          .displayName("Secondary Key")
          .rawApiKey("raw_key_2")
          .isActive(true)
          .expiresAt(now)
          .gracePeriodEndsAt(now)
          .createdBy("creator@test.com")
          .createdAt(now)
          .deactivatedAt(null)
          .deactivatedBy(null)
          .deactivationReason(null)
          .build();

      assertThat(info.getApiKeyId()).isEqualTo(2L);
      assertThat(info.getProjectId()).isEqualTo("proj-2");
      assertThat(info.getDisplayName()).isEqualTo("Secondary Key");
      assertThat(info.getRawApiKey()).isEqualTo("raw_key_2");
      assertThat(info.getIsActive()).isTrue();
    }

    @Test
    void createApiKeyRequest_shouldHaveAllFields() {
      Instant expiresAt = Instant.now().plusSeconds(86400);
      CreateApiKeyRequest req = CreateApiKeyRequest.builder()
          .projectId("proj-1")
          .displayName("My Key")
          .expiresAt(expiresAt)
          .createdBy("user@test.com")
          .build();

      assertThat(req.getProjectId()).isEqualTo("proj-1");
      assertThat(req.getDisplayName()).isEqualTo("My Key");
      assertThat(req.getExpiresAt()).isEqualTo(expiresAt);
      assertThat(req.getCreatedBy()).isEqualTo("user@test.com");
    }

    @Test
    void revokeApiKeyRequest_shouldHaveAllFields() {
      RevokeApiKeyRequest req = RevokeApiKeyRequest.builder()
          .projectId("proj-1")
          .apiKeyId(1L)
          .gracePeriodDays(7)
          .revokedBy("admin@test.com")
          .build();

      assertThat(req.getProjectId()).isEqualTo("proj-1");
      assertThat(req.getApiKeyId()).isEqualTo(1L);
      assertThat(req.getGracePeriodDays()).isEqualTo(7);
      assertThat(req.getRevokedBy()).isEqualTo("admin@test.com");
    }
  }

  @Nested
  class UsageLimitModels {

    @Test
    void dataType_shouldHaveAllValues() {
      assertThat(DataType.INTEGER.getValue()).isEqualTo("INTEGER");
      assertThat(DataType.BOOLEAN.getValue()).isEqualTo("BOOLEAN");

      assertThat(DataType.valueOf("INTEGER")).isEqualTo(DataType.INTEGER);
      assertThat(DataType.valueOf("BOOLEAN")).isEqualTo(DataType.BOOLEAN);

      assertThat(DataType.fromString("integer")).isEqualTo(DataType.INTEGER);
      assertThat(DataType.fromString("BOOLEAN")).isEqualTo(DataType.BOOLEAN);
      assertThat(DataType.fromString(null)).isNull();

      assertThrows(IllegalArgumentException.class, () -> DataType.fromString("unknown"));
    }

    @Test
    void windowType_shouldHaveAllValues() {
      assertThat(WindowType.WEEKLY.getValue()).isEqualTo("weekly");
      assertThat(WindowType.MONTHLY.getValue()).isEqualTo("monthly");
      assertThat(WindowType.YEARLY.getValue()).isEqualTo("yearly");
      assertThat(WindowType.TOTAL.getValue()).isEqualTo("total");

      assertThat(WindowType.fromString("weekly")).isEqualTo(WindowType.WEEKLY);
      assertThat(WindowType.fromString("MONTHLY")).isEqualTo(WindowType.MONTHLY);
      assertThat(WindowType.fromString(null)).isNull();

      assertThrows(IllegalArgumentException.class, () -> WindowType.fromString("daily"));
    }

    @Test
    void usageLimitParameter_shouldHaveAllValues() {
      for (UsageLimitParameter param : UsageLimitParameter.values()) {
        assertThat(param.getKey()).isNotBlank();
        assertThat(param.getDisplayName()).isNotBlank();
        assertThat(param.getRedisCreditKey()).isNotBlank();
        assertThat(param.getDefaultWindowType()).isNotNull();
        assertThat(param.getDataType()).isNotNull();
      }

      assertThat(UsageLimitParameter.fromKey("max_user_sessions_per_project"))
          .isEqualTo(UsageLimitParameter.MAX_USER_SESSIONS_PER_PROJECT);
      assertThat(UsageLimitParameter.fromKey("max_events_per_project"))
          .isEqualTo(UsageLimitParameter.MAX_EVENTS_PER_PROJECT);

      assertThrows(IllegalArgumentException.class,
          () -> UsageLimitParameter.fromKey("unknown_key"));

      assertThat(UsageLimitParameter.fromRedisCreditKey("remaining_session_credit"))
          .hasValue(UsageLimitParameter.MAX_USER_SESSIONS_PER_PROJECT);
      assertThat(UsageLimitParameter.fromRedisCreditKey("remaining_event_credit"))
          .hasValue(UsageLimitParameter.MAX_EVENTS_PER_PROJECT);
      assertThat(UsageLimitParameter.fromRedisCreditKey("unknown")).isEmpty();
    }

    @Test
    void usageLimitValue_shouldHaveAllFields() {
      UsageLimitValue val = UsageLimitValue.builder()
          .displayName("Max Events")
          .windowType("MONTHLY")
          .dataType("NUMBER")
          .value(1000L)
          .overage(10)
          .finalThreshold(1100L)
          .build();

      assertThat(val.getDisplayName()).isEqualTo("Max Events");
      assertThat(val.getWindowType()).isEqualTo("MONTHLY");
      assertThat(val.getDataType()).isEqualTo("NUMBER");
      assertThat(val.getValue()).isEqualTo(1000L);
      assertThat(val.getOverage()).isEqualTo(10);
      assertThat(val.getFinalThreshold()).isEqualTo(1100L);
    }

    @Test
    void usageLimitPublicValue_shouldHaveAllFields() {
      UsageLimitPublicValue val = UsageLimitPublicValue.builder()
          .displayName("Max Events")
          .windowType("MONTHLY")
          .value(1000L)
          .build();

      assertThat(val.getDisplayName()).isEqualTo("Max Events");
      assertThat(val.getWindowType()).isEqualTo("MONTHLY");
      assertThat(val.getValue()).isEqualTo(1000L);
    }

    @Test
    void projectUsageLimitInfo_shouldHaveAllFields() {
      Map<String, UsageLimitValue> limits = Map.of(
          "key", UsageLimitValue.builder().value(500L).build());
      Instant now = Instant.now();
      ProjectUsageLimitInfo info = ProjectUsageLimitInfo.builder()
          .projectUsageLimitId(1L)
          .projectId("proj-1")
          .usageLimits(limits)
          .isActive(true)
          .createdAt(now)
          .createdBy("system")
          .disabledAt(now)
          .disabledBy("admin")
          .disabledReason("reset")
          .build();

      assertThat(info.getProjectUsageLimitId()).isEqualTo(1L);
      assertThat(info.getProjectId()).isEqualTo("proj-1");
      assertThat(info.getUsageLimits()).isEqualTo(limits);
      assertThat(info.getIsActive()).isTrue();
      assertThat(info.getCreatedAt()).isEqualTo(now);
      assertThat(info.getCreatedBy()).isEqualTo("system");
      assertThat(info.getDisabledAt()).isEqualTo(now);
      assertThat(info.getDisabledBy()).isEqualTo("admin");
      assertThat(info.getDisabledReason()).isEqualTo("reset");
    }

    @Test
    void projectUsageLimitPublicInfo_shouldHaveAllFields() {
      Map<String, UsageLimitPublicValue> limits = Map.of(
          "key", UsageLimitPublicValue.builder().value(500L).build());
      ProjectUsageLimitPublicInfo info = ProjectUsageLimitPublicInfo.builder()
          .projectId("proj-1")
          .usageLimits(limits)
          .build();

      assertThat(info.getProjectId()).isEqualTo("proj-1");
      assertThat(info.getUsageLimits()).isEqualTo(limits);
    }

    @Test
    void resetLimitsRequest_shouldHaveAllFields() {
      ResetLimitsRequest req = ResetLimitsRequest.builder()
          .projectId("proj-1")
          .tierId(2)
          .performedBy("admin@test.com")
          .build();

      assertThat(req.getProjectId()).isEqualTo("proj-1");
      assertThat(req.getTierId()).isEqualTo(2);
      assertThat(req.getPerformedBy()).isEqualTo("admin@test.com");
    }

    @Test
    void setCustomLimitsRequest_shouldHaveAllFields() {
      Map<String, UsageLimitValue> limits = Map.of(
          "max_events", UsageLimitValue.builder().value(5000L).build());
      SetCustomLimitsRequest req = SetCustomLimitsRequest.builder()
          .projectId("proj-1")
          .limits(limits)
          .performedBy("admin@test.com")
          .build();

      assertThat(req.getProjectId()).isEqualTo("proj-1");
      assertThat(req.getLimits()).isEqualTo(limits);
      assertThat(req.getPerformedBy()).isEqualTo("admin@test.com");
    }
  }
}
