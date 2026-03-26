package org.dreamhorizon.pulseserver.resources.usagelimits;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.resources.tiers.models.UsageLimitValueRestDto;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ResetLimitsRestRequest;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.SetCustomLimitsRestRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitPublicInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ResetLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.SetCustomLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitPublicValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotification;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotificationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UsageLimitMapperTest {

  private final UsageLimitMapper mapper = UsageLimitMapper.INSTANCE;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Nested
  class ToSetCustomLimitsRequest {

    @Test
    void shouldMapSetCustomLimitsRequest() {
      SetCustomLimitsRestRequest restRequest = SetCustomLimitsRestRequest.builder()
          .limits(Map.of(
              "traces_per_day",
              UsageLimitValueRestDto.builder()
                  .displayName("Traces")
                  .windowType("day")
                  .dataType("count")
                  .value(50000L)
                  .build()))
          .build();

      SetCustomLimitsRequest result = mapper.toSetCustomLimitsRequest(
          "project-1", restRequest, "admin@example.com");

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("project-1");
      assertThat(result.getPerformedBy()).isEqualTo("admin@example.com");
      assertThat(result.getLimits()).isNotEmpty();
    }
  }

  @Nested
  class ToResetLimitsRequest {

    @Test
    void shouldMapResetLimitsRequest() {
      ResetLimitsRestRequest restRequest = ResetLimitsRestRequest.builder()
          .tierId(2)
          .build();

      ResetLimitsRequest result = mapper.toResetLimitsRequest(
          "project-1", restRequest, "admin@example.com");

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("project-1");
      assertThat(result.getTierId()).isEqualTo(2);
      assertThat(result.getPerformedBy()).isEqualTo("admin@example.com");
    }
  }

  @Nested
  class ToRestResponse {

    @Test
    void shouldMapProjectUsageLimitInfoToRestResponse() {
      ProjectUsageLimitInfo info = ProjectUsageLimitInfo.builder()
          .projectUsageLimitId(1L)
          .projectId("project-1")
          .usageLimits(Map.of(
              "traces_per_day",
              UsageLimitValue.builder()
                  .displayName("Traces")
                  .windowType("day")
                  .dataType("count")
                  .value(10000L)
                  .build()))
          .isActive(true)
          .createdAt(Instant.now())
          .createdBy("admin@example.com")
          .disabledAt(null)
          .disabledBy(null)
          .disabledReason(null)
          .build();

      var result = mapper.toRestResponse(info);

      assertThat(result).isNotNull();
      assertThat(result.getProjectUsageLimitId()).isEqualTo(1L);
      assertThat(result.getProjectId()).isEqualTo("project-1");
      assertThat(result.getIsActive()).isTrue();
      assertThat(result.getUsageLimits()).isNotNull();
    }

    @Test
    void shouldReturnNullWhenInfoIsNull() {
      assertThat(mapper.toRestResponse(null)).isNull();
    }

    @Test
    void shouldMapNotificationStatusWhenPresent() {
      ObjectNode thresholds = objectMapper.createObjectNode().put("50", true);
      NotificationStatus ns = NotificationStatus.builder()
          .thresholdsNotified(thresholds)
          .createdAt(Instant.parse("2025-01-15T10:00:00Z"))
          .build();

      ProjectUsageLimitInfo info = ProjectUsageLimitInfo.builder()
          .projectUsageLimitId(1L)
          .projectId("project-1")
          .usageLimits(Map.of())
          .isActive(true)
          .createdAt(Instant.now())
          .createdBy("admin")
          .notificationStatus(ns)
          .build();

      var result = mapper.toRestResponse(info);

      assertThat(result.getNotificationStatus()).isNotNull();
      assertThat(result.getNotificationStatus().getThresholdsNotified()).isEqualTo(thresholds);
      assertThat(result.getNotificationStatus().getCreatedAt()).isEqualTo(ns.getCreatedAt());
    }
  }

  @Nested
  class ToPublicRestResponse {

    @Test
    void shouldMapProjectUsageLimitPublicInfoToRestResponse() {
      ProjectUsageLimitPublicInfo info = ProjectUsageLimitPublicInfo.builder()
          .projectId("project-1")
          .usageLimits(Map.of(
              "traces_per_day",
              UsageLimitPublicValue.builder()
                  .displayName("Traces")
                  .windowType("day")
                  .value(10000L)
                  .build()))
          .build();

      var result = mapper.toPublicRestResponse(info);

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("project-1");
      assertThat(result.getUsageLimits()).isNotNull();
    }

    @Test
    void shouldReturnNullWhenInfoIsNull() {
      assertThat(mapper.toPublicRestResponse(null)).isNull();
    }
  }

  @Nested
  class ListMappings {

    @Test
    void shouldMapToListRestResponse() {
      ProjectUsageLimitInfo info = ProjectUsageLimitInfo.builder()
          .projectUsageLimitId(1L)
          .projectId("project-1")
          .usageLimits(Map.of())
          .isActive(true)
          .createdAt(Instant.now())
          .createdBy("admin")
          .build();

      var result = mapper.toListRestResponse(List.of(info));

      assertThat(result).isNotNull();
      assertThat(result.getLimits()).hasSize(1);
      assertThat(result.getTotalCount()).isEqualTo(1);
    }

    @Test
    void shouldMapToHistoryRestResponse() {
      ProjectUsageLimitInfo info = ProjectUsageLimitInfo.builder()
          .projectUsageLimitId(1L)
          .projectId("project-1")
          .usageLimits(Map.of())
          .isActive(true)
          .createdAt(Instant.now())
          .createdBy("admin")
          .build();

      var result = mapper.toHistoryRestResponse("project-1", List.of(info));

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("project-1");
      assertThat(result.getHistory()).hasSize(1);
      assertThat(result.getTotalCount()).isEqualTo(1);
    }
  }

  @Nested
  class UsageLimitValueMappings {

    @Test
    void shouldMapUsageLimitValueRestDtoToUsageLimitValue() {
      UsageLimitValueRestDto dto = UsageLimitValueRestDto.builder()
          .displayName("Traces")
          .windowType("day")
          .dataType("count")
          .value(10000L)
          .build();

      UsageLimitValue result = mapper.toUsageLimitValue(dto);

      assertThat(result).isNotNull();
      assertThat(result.getDisplayName()).isEqualTo("Traces");
      assertThat(result.getWindowType()).isEqualTo("day");
      assertThat(result.getValue()).isEqualTo(10000L);
    }

    @Test
    void shouldReturnNullForNullMap() {
      assertThat(mapper.toUsageLimitValueMap(null)).isNull();
      assertThat(mapper.toUsageLimitValueRestDtoMap(null)).isNull();
      assertThat(mapper.toUsageLimitPublicRestDtoMap(null)).isNull();
    }
  }

  @Nested
  class UsageNotificationMappings {

    @Test
    void shouldReturnNullWhenUsageNotificationResultIsNull() {
      assertThat(mapper.toUsageNotificationResponse(null)).isNull();
    }

    @Test
    void shouldReturnNullWhenUsageNotificationIsNull() {
      assertThat(mapper.toUsageNotificationDto(null)).isNull();
    }

    @Test
    void shouldMapUsageNotificationResultToRestResponse() {
      UsageNotification notification = UsageNotification.builder()
          .projectId("proj-1")
          .projectName("Proj")
          .threshold(90)
          .notifyFor("events")
          .templateName("USAGE_LIMIT_THRESHOLD")
          .eventsUsed(9L)
          .eventsLimit(10L)
          .tenantId("t1")
          .build();

      UsageNotificationResult result = UsageNotificationResult.builder()
          .notifications(List.of(notification))
          .totalProjectsChecked(3)
          .notificationsDue(1)
          .checkedAt(Instant.parse("2025-03-01T12:00:00Z"))
          .build();

      var rest = mapper.toUsageNotificationResponse(result);

      assertThat(rest).isNotNull();
      assertThat(rest.getNotifications()).hasSize(1);
      assertThat(rest.getNotifications().get(0).getProjectId()).isEqualTo("proj-1");
      assertThat(rest.getTotalProjectsChecked()).isEqualTo(3);
      assertThat(rest.getNotificationsDue()).isEqualTo(1);
      assertThat(rest.getCheckedAt()).isEqualTo("2025-03-01T12:00:00Z");
    }

    @Test
    void shouldMapUsageNotificationResultWithNullCheckedAt() {
      UsageNotificationResult result = UsageNotificationResult.builder()
          .notifications(List.of())
          .totalProjectsChecked(0)
          .notificationsDue(0)
          .checkedAt(null)
          .build();

      var rest = mapper.toUsageNotificationResponse(result);

      assertThat(rest.getCheckedAt()).isNull();
    }
  }
}
