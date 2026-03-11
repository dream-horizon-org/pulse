package org.dreamhorizon.pulseserver.resources.tiers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TierMapperTest {

  private static final TierMapper MAPPER = TierMapper.INSTANCE;

  @Nested
  class ToCreateTierRequest {

    @Test
    void shouldMapCreateTierRestRequestToCreateTierRequest() {
      UsageLimitValueRestDto limitDto = UsageLimitValueRestDto.builder()
          .displayName("Events")
          .windowType("monthly")
          .dataType("count")
          .value(1000L)
          .overage(10)
          .build();
      CreateTierRestRequest restRequest = CreateTierRestRequest.builder()
          .name("pro")
          .displayName("Professional")
          .isCustomLimitsAllowed(true)
          .usageLimitDefaults(Map.of("events", limitDto))
          .build();

      CreateTierRequest result = MAPPER.toCreateTierRequest(restRequest);

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo("pro");
      assertThat(result.getDisplayName()).isEqualTo("Professional");
      assertThat(result.getIsCustomLimitsAllowed()).isTrue();
      assertThat(result.getUsageLimitDefaults()).containsKey("events");
      assertThat(result.getUsageLimitDefaults().get("events").getDisplayName()).isEqualTo("Events");
    }

    @Test
    void shouldReturnNullWhenRequestNull() {
      assertThat(MAPPER.toCreateTierRequest(null)).isNull();
    }
  }

  @Nested
  class ToUpdateTierRequest {

    @Test
    void shouldMapUpdateTierRestRequestToUpdateTierRequest() {
      UpdateTierRestRequest restRequest = UpdateTierRestRequest.builder()
          .displayName("Updated Display")
          .isCustomLimitsAllowed(false)
          .build();

      UpdateTierRequest result = MAPPER.toUpdateTierRequest(42, restRequest);

      assertThat(result).isNotNull();
      assertThat(result.getTierId()).isEqualTo(42);
      assertThat(result.getDisplayName()).isEqualTo("Updated Display");
      assertThat(result.getIsCustomLimitsAllowed()).isFalse();
    }

    @Test
    void shouldReturnNullWhenBothNull() {
      assertThat(MAPPER.toUpdateTierRequest(null, null)).isNull();
    }
  }

  @Nested
  class ToTierRestResponse {

    @Test
    void shouldMapTierInfoToTierRestResponse() {
      UsageLimitValue limitValue = UsageLimitValue.builder()
          .displayName("Events")
          .windowType("monthly")
          .dataType("count")
          .value(500L)
          .build();
      TierInfo info = TierInfo.builder()
          .tierId(1)
          .name("free")
          .displayName("Free Tier")
          .isCustomLimitsAllowed(false)
          .usageLimitDefaults(Map.of("events", limitValue))
          .isActive(true)
          .createdAt(Instant.now())
          .build();

      TierRestResponse result = MAPPER.toTierRestResponse(info);

      assertThat(result).isNotNull();
      assertThat(result.getTierId()).isEqualTo(1);
      assertThat(result.getName()).isEqualTo("free");
      assertThat(result.getDisplayName()).isEqualTo("Free Tier");
      assertThat(result.getIsCustomLimitsAllowed()).isFalse();
      assertThat(result.getUsageLimitDefaults()).containsKey("events");
      assertThat(result.getIsActive()).isTrue();
      assertThat(result.getCreatedAt()).isEqualTo(info.getCreatedAt());
    }

    @Test
    void shouldReturnNullWhenInfoNull() {
      assertThat(MAPPER.toTierRestResponse(null)).isNull();
    }
  }

  @Nested
  class ToTierPublicRestResponse {

    @Test
    void shouldMapTierPublicInfoToTierPublicRestResponse() {
      UsageLimitPublicValue limitValue = UsageLimitPublicValue.builder()
          .displayName("Events")
          .windowType("monthly")
          .value(1000L)
          .build();
      TierPublicInfo info = TierPublicInfo.builder()
          .tierId(2)
          .name("pro")
          .displayName("Professional")
          .isCustomLimitsAllowed(true)
          .usageLimits(Map.of("events", limitValue))
          .build();

      TierPublicRestResponse result = MAPPER.toTierPublicRestResponse(info);

      assertThat(result).isNotNull();
      assertThat(result.getTierId()).isEqualTo(2);
      assertThat(result.getName()).isEqualTo("pro");
      assertThat(result.getDisplayName()).isEqualTo("Professional");
      assertThat(result.getUsageLimits()).containsKey("events");
    }

    @Test
    void shouldReturnNullWhenInfoNull() {
      assertThat(MAPPER.toTierPublicRestResponse(null)).isNull();
    }
  }

  @Nested
  class UsageLimitMappings {

    @Test
    void shouldMapUsageLimitValueRestDtoToUsageLimitValue() {
      UsageLimitValueRestDto dto = UsageLimitValueRestDto.builder()
          .displayName("Sessions")
          .windowType("daily")
          .dataType("count")
          .value(100L)
          .overage(20)
          .finalThreshold(120L)
          .build();

      UsageLimitValue result = MAPPER.toUsageLimitValue(dto);

      assertThat(result).isNotNull();
      assertThat(result.getDisplayName()).isEqualTo("Sessions");
      assertThat(result.getWindowType()).isEqualTo("daily");
      assertThat(result.getDataType()).isEqualTo("count");
      assertThat(result.getValue()).isEqualTo(100L);
      assertThat(result.getOverage()).isEqualTo(20);
      assertThat(result.getFinalThreshold()).isEqualTo(120L);
    }

    @Test
    void shouldMapUsageLimitValueToUsageLimitValueRestDto() {
      UsageLimitValue value = UsageLimitValue.builder()
          .displayName("Errors")
          .windowType("monthly")
          .dataType("count")
          .value(50L)
          .overage(0)
          .build();

      UsageLimitValueRestDto result = MAPPER.toUsageLimitValueRestDto(value);

      assertThat(result).isNotNull();
      assertThat(result.getDisplayName()).isEqualTo("Errors");
      assertThat(result.getWindowType()).isEqualTo("monthly");
      assertThat(result.getValue()).isEqualTo(50L);
    }

    @Test
    void shouldMapUsageLimitPublicValueToUsageLimitPublicRestDto() {
      UsageLimitPublicValue value = UsageLimitPublicValue.builder()
          .displayName("Traces")
          .windowType("monthly")
          .value(10000L)
          .build();

      UsageLimitPublicRestDto result = MAPPER.toUsageLimitPublicRestDto(value);

      assertThat(result).isNotNull();
      assertThat(result.getDisplayName()).isEqualTo("Traces");
      assertThat(result.getWindowType()).isEqualTo("monthly");
      assertThat(result.getValue()).isEqualTo(10000L);
    }
  }

  @Nested
  class MapConversions {

    @Test
    void shouldConvertUsageLimitValueMap() {
      UsageLimitValueRestDto dto = UsageLimitValueRestDto.builder()
          .displayName("Events")
          .windowType("monthly")
          .dataType("count")
          .value(1000L)
          .build();
      Map<String, UsageLimitValue> result = MAPPER.toUsageLimitValueMap(Map.of("events", dto));

      assertThat(result).isNotNull().hasSize(1);
      assertThat(result.get("events").getDisplayName()).isEqualTo("Events");
    }

    @Test
    void shouldReturnNullWhenDtoMapNull() {
      assertThat(MAPPER.toUsageLimitValueMap(null)).isNull();
    }

    @Test
    void shouldConvertUsageLimitValueRestDtoMap() {
      UsageLimitValue value = UsageLimitValue.builder()
          .displayName("Events")
          .windowType("monthly")
          .dataType("count")
          .value(500L)
          .build();
      Map<String, UsageLimitValueRestDto> result =
          MAPPER.toUsageLimitValueRestDtoMap(Map.of("events", value));

      assertThat(result).isNotNull().hasSize(1);
      assertThat(result.get("events").getDisplayName()).isEqualTo("Events");
    }

    @Test
    void shouldReturnNullWhenValueMapNull() {
      assertThat(MAPPER.toUsageLimitValueRestDtoMap(null)).isNull();
    }

    @Test
    void shouldConvertUsageLimitPublicRestDtoMap() {
      UsageLimitPublicValue value = UsageLimitPublicValue.builder()
          .displayName("Events")
          .windowType("monthly")
          .value(100L)
          .build();
      Map<String, UsageLimitPublicRestDto> result =
          MAPPER.toUsageLimitPublicRestDtoMap(Map.of("events", value));

      assertThat(result).isNotNull().hasSize(1);
      assertThat(result.get("events").getDisplayName()).isEqualTo("Events");
    }

    @Test
    void shouldReturnNullWhenPublicValueMapNull() {
      assertThat(MAPPER.toUsageLimitPublicRestDtoMap(null)).isNull();
    }
  }

  @Nested
  class ListMappings {

    @Test
    void shouldMapTierListToTierListRestResponse() {
      TierInfo info = TierInfo.builder()
          .tierId(1)
          .name("free")
          .displayName("Free")
          .build();
      TierListRestResponse result = MAPPER.toTierListRestResponse(List.of(info));

      assertThat(result).isNotNull();
      assertThat(result.getTiers()).hasSize(1);
      assertThat(result.getTotalCount()).isEqualTo(1);
      assertThat(result.getTiers().get(0).getName()).isEqualTo("free");
    }

    @Test
    void shouldMapTierPublicListToTierPublicListRestResponse() {
      TierPublicInfo info = TierPublicInfo.builder()
          .tierId(1)
          .name("free")
          .displayName("Free")
          .build();
      TierPublicListRestResponse result = MAPPER.toTierPublicListRestResponse(List.of(info));

      assertThat(result).isNotNull();
      assertThat(result.getTiers()).hasSize(1);
      assertThat(result.getTotalCount()).isEqualTo(1);
    }
  }
}
