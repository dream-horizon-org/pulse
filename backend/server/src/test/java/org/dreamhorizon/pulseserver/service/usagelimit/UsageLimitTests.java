package org.dreamhorizon.pulseserver.service.usagelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tier.TierDao;
import org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitDao;
import org.dreamhorizon.pulseserver.dao.usagelimit.models.ProjectUsageLimit;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.dao.tier.models.Tier;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitPublicInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ResetLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.SetCustomLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitParameter;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsageLimitTests {

  @Mock
  ProjectUsageLimitDao usageLimitDao;

  @Mock
  ProjectDao projectDao;

  @Mock
  TenantDao tenantDao;

  @Mock
  TierDao tierDao;

  @Mock
  TierService tierService;

  @Mock
  SqlConnection sqlConnection;

  ObjectMapper objectMapper;
  UsageLimitService usageLimitService;

  private Map<String, UsageLimitValue> createValidDefaults() {
    Map<String, UsageLimitValue> defaults = new HashMap<>();
    for (UsageLimitParameter param : UsageLimitParameter.values()) {
      defaults.put(param.getKey(), UsageLimitValue.builder()
          .displayName(param.getDisplayName())
          .windowType("MONTHLY")
          .dataType("NUMBER")
          .value(1000L)
          .overage(10)
          .build());
    }
    return defaults;
  }

  private ProjectUsageLimit createMockLimit(String projectId) {
    try {
      String json = new ObjectMapper().writeValueAsString(createValidDefaults());
      return ProjectUsageLimit.builder()
          .projectUsageLimitId(1L)
          .projectId(projectId)
          .usageLimits(json)
          .isActive(true)
          .createdAt(Instant.now())
          .createdBy("system")
          .build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
    usageLimitService = new UsageLimitService(
        usageLimitDao, projectDao, tenantDao, tierDao, tierService, objectMapper);
  }

  @Nested
  class GetProjectLimitsPublic {

    @Test
    void shouldGetProjectLimitsPublicSuccessfully() {
      ProjectUsageLimit limit = createMockLimit("proj-1");
      when(usageLimitDao.getActiveLimitByProjectId("proj-1")).thenReturn(Maybe.just(limit));

      ProjectUsageLimitPublicInfo result =
          usageLimitService.getProjectLimitsPublic("proj-1").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("proj-1");
      assertThat(result.getUsageLimits()).isNotEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNoActiveLimit() {
      when(usageLimitDao.getActiveLimitByProjectId("proj-none")).thenReturn(Maybe.empty());

      ProjectUsageLimitPublicInfo result =
          usageLimitService.getProjectLimitsPublic("proj-none").blockingGet();

      assertThat(result).isNull();
    }
  }

  @Nested
  class GetProjectLimits {

    @Test
    void shouldGetProjectLimitsSuccessfully() {
      ProjectUsageLimit limit = createMockLimit("proj-2");
      when(usageLimitDao.getActiveLimitByProjectId("proj-2")).thenReturn(Maybe.just(limit));

      ProjectUsageLimitInfo result = usageLimitService.getProjectLimits("proj-2").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("proj-2");
      assertThat(result.getUsageLimits()).isNotEmpty();
      assertThat(result.getIsActive()).isTrue();
    }
  }

  @Nested
  class GetAllActiveLimits {

    @Test
    void shouldGetAllActiveLimits() {
      ProjectUsageLimit limit1 = createMockLimit("proj-a");
      ProjectUsageLimit limit2 = createMockLimit("proj-b");
      when(usageLimitDao.getAllActiveLimits()).thenReturn(Flowable.just(limit1, limit2));

      List<ProjectUsageLimitInfo> result =
          usageLimitService.getAllActiveLimits().toList().blockingGet();

      assertThat(result).hasSize(2);
    }

    @Test
    void shouldReturnEmptyWhenNoActiveLimits() {
      when(usageLimitDao.getAllActiveLimits()).thenReturn(Flowable.empty());

      List<ProjectUsageLimitInfo> result =
          usageLimitService.getAllActiveLimits().toList().blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GetAllLimits {

    @Test
    void shouldGetAllLimitsIncludingInactive() {
      ProjectUsageLimit limit = createMockLimit("proj-c");
      limit.setIsActive(false);
      when(usageLimitDao.getAllLimits()).thenReturn(Flowable.just(limit));

      List<ProjectUsageLimitInfo> result =
          usageLimitService.getAllLimits().toList().blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getIsActive()).isFalse();
    }
  }

  @Nested
  class GetProjectLimitHistory {

    @Test
    void shouldGetLimitHistoryForProject() {
      ProjectUsageLimit limit = createMockLimit("proj-d");
      limit.setIsActive(false);
      when(usageLimitDao.getLimitHistoryByProjectId("proj-d")).thenReturn(Flowable.just(limit));

      List<ProjectUsageLimitInfo> result =
          usageLimitService.getProjectLimitHistory("proj-d").toList().blockingGet();

      assertThat(result).hasSize(1);
    }
  }

  @Nested
  class ResetToDefaults {

    @Test
    void shouldResetToFreeTierDefaultsWhenTierIdNull() throws Exception {
      Tier freeTier = Tier.builder()
          .tierId(1)
          .name("free")
          .isActive(true)
          .usageLimitDefaults(objectMapper.writeValueAsString(createValidDefaults()))
          .build();
      ProjectUsageLimit updatedLimit = createMockLimit("proj-reset");
      when(tierDao.getTierById(1)).thenReturn(Maybe.just(freeTier));
      when(usageLimitDao.updateUsageLimits(
          eq("proj-reset"), anyString(), eq("admin"), eq("reset_to_defaults")))
          .thenReturn(Single.just(updatedLimit));

      ProjectUsageLimitInfo result = usageLimitService.resetToDefaults(
          ResetLimitsRequest.builder()
              .projectId("proj-reset")
              .performedBy("admin")
              .build()).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("proj-reset");
      verify(tierDao).getTierById(1);
    }

    @Test
    void shouldResetToSpecifiedTier() throws Exception {
      Tier enterpriseTier = Tier.builder()
          .tierId(2)
          .name("enterprise")
          .isActive(true)
          .usageLimitDefaults(objectMapper.writeValueAsString(createValidDefaults()))
          .build();
      ProjectUsageLimit updatedLimit = createMockLimit("proj-reset2");
      when(tierDao.getTierById(2)).thenReturn(Maybe.just(enterpriseTier));
      when(usageLimitDao.updateUsageLimits(
          eq("proj-reset2"), anyString(), eq("admin"), eq("reset_to_defaults")))
          .thenReturn(Single.just(updatedLimit));

      ProjectUsageLimitInfo result = usageLimitService.resetToDefaults(
          ResetLimitsRequest.builder()
              .projectId("proj-reset2")
              .tierId(2)
              .performedBy("admin")
              .build()).blockingGet();

      assertThat(result).isNotNull();
      verify(tierDao).getTierById(2);
    }

    @Test
    void shouldFailWhenTierNotFound() {
      when(tierDao.getTierById(999)).thenReturn(Maybe.empty());

      assertThrows(RuntimeException.class, () ->
          usageLimitService.resetToDefaults(
              ResetLimitsRequest.builder()
                  .projectId("proj")
                  .tierId(999)
                  .performedBy("admin")
                  .build()).blockingGet());
    }

    @Test
    void shouldFailWhenTierInactive() {
      Tier inactiveTier = Tier.builder()
          .tierId(3)
          .name("inactive")
          .isActive(false)
          .usageLimitDefaults("{}")
          .build();
      when(tierDao.getTierById(3)).thenReturn(Maybe.just(inactiveTier));

      assertThrows(IllegalStateException.class, () ->
          usageLimitService.resetToDefaults(
              ResetLimitsRequest.builder()
                  .projectId("proj")
                  .tierId(3)
                  .performedBy("admin")
                  .build()).blockingGet());
    }
  }

  @Nested
  class SetCustomLimits {

    @Test
    void shouldThrowWhenProjectNotFound() {
      when(projectDao.getProjectByProjectId("proj-missing")).thenReturn(Maybe.empty());

      SetCustomLimitsRequest request = SetCustomLimitsRequest.builder()
          .projectId("proj-missing")
          .limits(Map.of(
              UsageLimitParameter.MAX_EVENTS_PER_PROJECT.getKey(),
              UsageLimitValue.builder().value(5000L).build()))
          .performedBy("admin")
          .build();

      assertThrows(RuntimeException.class, () ->
          usageLimitService.setCustomLimits(request).blockingGet());
    }

    @Test
    void shouldThrowWhenTenantNotFound() {
      Project project = Project.builder().projectId("proj").tenantId("tenant-1").build();
      when(projectDao.getProjectByProjectId("proj")).thenReturn(Maybe.just(project));
      when(tenantDao.getTenantById("tenant-1")).thenReturn(Maybe.empty());

      SetCustomLimitsRequest request = SetCustomLimitsRequest.builder()
          .projectId("proj")
          .limits(Map.of())
          .performedBy("admin")
          .build();

      assertThrows(RuntimeException.class, () ->
          usageLimitService.setCustomLimits(request).blockingGet());
    }

    @Test
    void shouldThrowWhenCustomLimitsNotAllowed() {
      Project project = Project.builder().projectId("proj").tenantId("tenant-1").build();
      Tenant tenant = Tenant.builder().tenantId("tenant-1").tierId(1).build();
      Tier freeTier = Tier.builder()
          .tierId(1)
          .name("free")
          .isCustomLimitsAllowed(false)
          .build();
      ProjectUsageLimit limit = createMockLimit("proj");

      when(projectDao.getProjectByProjectId("proj")).thenReturn(Maybe.just(project));
      when(tenantDao.getTenantById("tenant-1")).thenReturn(Maybe.just(tenant));
      when(tierDao.getTierById(1)).thenReturn(Maybe.just(freeTier));
      when(usageLimitDao.getActiveLimitByProjectId("proj")).thenReturn(Maybe.just(limit));

      SetCustomLimitsRequest request = SetCustomLimitsRequest.builder()
          .projectId("proj")
          .limits(Map.of(
              UsageLimitParameter.MAX_EVENTS_PER_PROJECT.getKey(),
              UsageLimitValue.builder().value(5000L).build()))
          .performedBy("admin")
          .build();

      assertThrows(org.dreamhorizon.pulseserver.rest.exception.ForbiddenOperationException.class, () ->
          usageLimitService.setCustomLimits(request).blockingGet());
    }
  }

  @Nested
  class CreateInitialLimits {

    @Test
    void shouldCreateInitialLimitsWithinTransaction() {
      Map<String, UsageLimitValue> defaults = createValidDefaults();
      ProjectUsageLimit created = createMockLimit("new-proj");
      when(tierService.getFreeTierDefaults()).thenReturn(Single.just(defaults));
      when(usageLimitDao.createUsageLimit(
          eq(sqlConnection), eq("new-proj"), anyString(), eq("creator")))
          .thenReturn(Single.just(created));

      ProjectUsageLimit result = usageLimitService.createInitialLimits(
          sqlConnection, "new-proj", "creator").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("new-proj");
      verify(usageLimitDao).createUsageLimit(eq(sqlConnection), eq("new-proj"), anyString(), eq("creator"));
    }
  }
}
