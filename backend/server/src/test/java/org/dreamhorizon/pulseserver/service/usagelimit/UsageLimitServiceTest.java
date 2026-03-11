package org.dreamhorizon.pulseserver.service.usagelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.dao.tier.TierDao;
import org.dreamhorizon.pulseserver.dao.tier.models.Tier;
import org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitDao;
import org.dreamhorizon.pulseserver.dao.usagelimit.models.ProjectUsageLimit;
import org.dreamhorizon.pulseserver.rest.exception.ForbiddenOperationException;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitPublicInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ResetLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.SetCustomLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class UsageLimitServiceTest {

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

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
    usageLimitService = new UsageLimitService(usageLimitDao, projectDao, tenantDao, tierDao, tierService, objectMapper);
  }

  private ProjectUsageLimit createMockUsageLimit() throws Exception {
    Map<String, UsageLimitValue> limits = new HashMap<>();
    limits.put("max_events", UsageLimitValue.builder()
        .displayName("Max Events")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(1000L)
        .overage(10)
        .build());
    limits.put("max_projects", UsageLimitValue.builder()
        .displayName("Max Projects")
        .windowType("LIFETIME")
        .dataType("NUMBER")
        .value(5L)
        .overage(0)
        .build());

    String limitsJson = objectMapper.writeValueAsString(limits);
    return ProjectUsageLimit.builder()
        .projectUsageLimitId(1L)
        .projectId("test-project")
        .usageLimits(limitsJson)
        .isActive(true)
        .createdBy("creator@example.com")
        .createdAt(Instant.now())
        .build();
  }

  private Project createMockProject() {
    return Project.builder()
        .projectId("test-project")
        .name("Test Project")
        .tenantId("test-tenant")
        .isActive(true)
        .build();
  }

  private Tenant createMockTenant(Integer tierId) {
    return Tenant.builder()
        .tenantId("test-tenant")
        .name("Test Tenant")
        .tierId(tierId)
        .isActive(true)
        .build();
  }

  private Tier createMockTier(boolean customLimitsAllowed) throws Exception {
    Map<String, UsageLimitValue> defaults = new HashMap<>();
    defaults.put("max_events", UsageLimitValue.builder().value(5000L).build());
    String json = objectMapper.writeValueAsString(defaults);

    return Tier.builder()
        .tierId(2)
        .name("enterprise")
        .displayName("Enterprise")
        .isCustomLimitsAllowed(customLimitsAllowed)
        .usageLimitDefaults(json)
        .isActive(true)
        .build();
  }

  // ==================== GET PROJECT LIMITS (PUBLIC) TESTS ====================

  @Nested
  class TestGetProjectLimitsPublic {

    @Test
    void shouldGetPublicLimitsSuccessfully() throws Exception {
      ProjectUsageLimit mockLimit = createMockUsageLimit();
      when(usageLimitDao.getActiveLimitByProjectId("test-project"))
          .thenReturn(Maybe.just(mockLimit));

      ProjectUsageLimitPublicInfo result = usageLimitService.getProjectLimitsPublic("test-project").blockingGet();

      assertNotNull(result);
      assertEquals("test-project", result.getProjectId());
      assertNotNull(result.getUsageLimits());
      assertTrue(result.getUsageLimits().containsKey("max_events"));
    }

    @Test
    void shouldReturnEmptyWhenNoLimitsFound() {
      when(usageLimitDao.getActiveLimitByProjectId("non-existent"))
          .thenReturn(Maybe.empty());

      ProjectUsageLimitPublicInfo result = usageLimitService.getProjectLimitsPublic("non-existent").blockingGet();

      assertEquals(null, result);
    }

    @Test
    void shouldMapToPublicFormatWithSimplifiedFields() throws Exception {
      ProjectUsageLimit mockLimit = createMockUsageLimit();
      when(usageLimitDao.getActiveLimitByProjectId("test-project"))
          .thenReturn(Maybe.just(mockLimit));

      ProjectUsageLimitPublicInfo result = usageLimitService.getProjectLimitsPublic("test-project").blockingGet();

      assertNotNull(result.getUsageLimits().get("max_events").getDisplayName());
      assertNotNull(result.getUsageLimits().get("max_events").getWindowType());
      assertNotNull(result.getUsageLimits().get("max_events").getValue());
    }
  }

  // ==================== GET PROJECT LIMITS (INTERNAL) TESTS ====================

  @Nested
  class TestGetProjectLimits {

    @Test
    void shouldGetInternalLimitsSuccessfully() throws Exception {
      ProjectUsageLimit mockLimit = createMockUsageLimit();
      when(usageLimitDao.getActiveLimitByProjectId("test-project"))
          .thenReturn(Maybe.just(mockLimit));

      ProjectUsageLimitInfo result = usageLimitService.getProjectLimits("test-project").blockingGet();

      assertNotNull(result);
      assertEquals("test-project", result.getProjectId());
      assertEquals(1L, result.getProjectUsageLimitId());
      assertTrue(result.getIsActive());
      assertEquals("creator@example.com", result.getCreatedBy());
    }

    @Test
    void shouldComputeFinalThresholdWithOverage() throws Exception {
      ProjectUsageLimit mockLimit = createMockUsageLimit();
      when(usageLimitDao.getActiveLimitByProjectId("test-project"))
          .thenReturn(Maybe.just(mockLimit));

      ProjectUsageLimitInfo result = usageLimitService.getProjectLimits("test-project").blockingGet();

      UsageLimitValue maxEvents = result.getUsageLimits().get("max_events");
      assertEquals(1000L, maxEvents.getValue());
      assertEquals(10, maxEvents.getOverage());
      assertEquals(1100L, maxEvents.getFinalThreshold());
    }

    @Test
    void shouldHandleZeroOverage() throws Exception {
      ProjectUsageLimit mockLimit = createMockUsageLimit();
      when(usageLimitDao.getActiveLimitByProjectId("test-project"))
          .thenReturn(Maybe.just(mockLimit));

      ProjectUsageLimitInfo result = usageLimitService.getProjectLimits("test-project").blockingGet();

      UsageLimitValue maxProjects = result.getUsageLimits().get("max_projects");
      assertEquals(5L, maxProjects.getValue());
      assertEquals(0, maxProjects.getOverage());
      assertEquals(5L, maxProjects.getFinalThreshold());
    }
  }

  // ==================== GET ALL LIMITS TESTS ====================

  @Nested
  class TestGetAllLimits {

    @Test
    void shouldGetAllActiveLimitsSuccessfully() throws Exception {
      ProjectUsageLimit limit1 = createMockUsageLimit();
      ProjectUsageLimit limit2 = createMockUsageLimit();
      limit2.setProjectUsageLimitId(2L);
      limit2.setProjectId("another-project");

      when(usageLimitDao.getAllActiveLimits()).thenReturn(Flowable.just(limit1, limit2));

      List<ProjectUsageLimitInfo> result = usageLimitService.getAllActiveLimits().toList().blockingGet();

      assertNotNull(result);
      assertEquals(2, result.size());
    }

    @Test
    void shouldGetAllLimitsIncludingInactive() throws Exception {
      ProjectUsageLimit activeLimit = createMockUsageLimit();
      ProjectUsageLimit inactiveLimit = createMockUsageLimit();
      inactiveLimit.setIsActive(false);

      when(usageLimitDao.getAllLimits()).thenReturn(Flowable.just(activeLimit, inactiveLimit));

      List<ProjectUsageLimitInfo> result = usageLimitService.getAllLimits().toList().blockingGet();

      assertEquals(2, result.size());
    }

    @Test
    void shouldReturnEmptyListWhenNoLimits() {
      when(usageLimitDao.getAllActiveLimits()).thenReturn(Flowable.empty());

      List<ProjectUsageLimitInfo> result = usageLimitService.getAllActiveLimits().toList().blockingGet();

      assertTrue(result.isEmpty());
    }
  }

  // ==================== GET PROJECT LIMIT HISTORY TESTS ====================

  @Nested
  class TestGetProjectLimitHistory {

    @Test
    void shouldGetLimitHistorySuccessfully() throws Exception {
      ProjectUsageLimit historicalLimit = createMockUsageLimit();
      historicalLimit.setIsActive(false);
      historicalLimit.setDisabledAt(Instant.now());
      historicalLimit.setDisabledBy("admin@example.com");
      historicalLimit.setDisabledReason("custom_override");

      when(usageLimitDao.getLimitHistoryByProjectId("test-project"))
          .thenReturn(Flowable.just(historicalLimit));

      List<ProjectUsageLimitInfo> result = usageLimitService.getProjectLimitHistory("test-project").toList().blockingGet();

      assertEquals(1, result.size());
      assertFalse(result.get(0).getIsActive());
      assertEquals("custom_override", result.get(0).getDisabledReason());
    }
  }

  // ==================== SET CUSTOM LIMITS TESTS ====================

  @Nested
  class TestSetCustomLimits {

    @Test
    void shouldSetCustomLimitsSuccessfully() throws Exception {
      Project project = createMockProject();
      Tenant tenant = createMockTenant(2);
      Tier tier = createMockTier(true);
      ProjectUsageLimit currentLimit = createMockUsageLimit();
      ProjectUsageLimit updatedLimit = createMockUsageLimit();

      Map<String, UsageLimitValue> newLimits = new HashMap<>();
      newLimits.put("max_events", UsageLimitValue.builder().value(5000L).build());

      SetCustomLimitsRequest request = SetCustomLimitsRequest.builder()
          .projectId("test-project")
          .limits(newLimits)
          .performedBy("admin@example.com")
          .build();

      when(projectDao.getProjectByProjectId("test-project")).thenReturn(Maybe.just(project));
      when(tenantDao.getTenantById("test-tenant")).thenReturn(Maybe.just(tenant));
      when(tierDao.getTierById(2)).thenReturn(Maybe.just(tier));
      when(usageLimitDao.getActiveLimitByProjectId("test-project")).thenReturn(Maybe.just(currentLimit));
      when(usageLimitDao.updateUsageLimits(eq("test-project"), anyString(), eq("admin@example.com"), eq("custom_override")))
          .thenReturn(Single.just(updatedLimit));

      ProjectUsageLimitInfo result = usageLimitService.setCustomLimits(request).blockingGet();

      assertNotNull(result);
      verify(usageLimitDao).updateUsageLimits(eq("test-project"), anyString(), eq("admin@example.com"), eq("custom_override"));
    }

    @Test
    void shouldMergeLimitsPartially() throws Exception {
      Project project = createMockProject();
      Tenant tenant = createMockTenant(2);
      Tier tier = createMockTier(true);
      ProjectUsageLimit currentLimit = createMockUsageLimit();

      Map<String, UsageLimitValue> newLimits = new HashMap<>();
      newLimits.put("max_events", UsageLimitValue.builder().value(9999L).build());

      SetCustomLimitsRequest request = SetCustomLimitsRequest.builder()
          .projectId("test-project")
          .limits(newLimits)
          .performedBy("admin@example.com")
          .build();

      when(projectDao.getProjectByProjectId("test-project")).thenReturn(Maybe.just(project));
      when(tenantDao.getTenantById("test-tenant")).thenReturn(Maybe.just(tenant));
      when(tierDao.getTierById(2)).thenReturn(Maybe.just(tier));
      when(usageLimitDao.getActiveLimitByProjectId("test-project")).thenReturn(Maybe.just(currentLimit));
      when(usageLimitDao.updateUsageLimits(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(invocation -> {
            String limitsJson = invocation.getArgument(1);
            assertTrue(limitsJson.contains("9999"));
            assertTrue(limitsJson.contains("max_projects"));
            return Single.just(currentLimit);
          });

      usageLimitService.setCustomLimits(request).blockingGet();
    }

    @Test
    void shouldFailWhenProjectNotFound() {
      SetCustomLimitsRequest request = SetCustomLimitsRequest.builder()
          .projectId("non-existent")
          .limits(new HashMap<>())
          .performedBy("admin@example.com")
          .build();

      when(projectDao.getProjectByProjectId("non-existent")).thenReturn(Maybe.empty());

      Exception ex = assertThrows(RuntimeException.class,
          () -> usageLimitService.setCustomLimits(request).blockingGet());
      assertTrue(ex.getMessage().contains("Project not found"));
    }

    @Test
    void shouldFailWhenTenantNotFound() {
      Project project = createMockProject();
      SetCustomLimitsRequest request = SetCustomLimitsRequest.builder()
          .projectId("test-project")
          .limits(new HashMap<>())
          .performedBy("admin@example.com")
          .build();

      when(projectDao.getProjectByProjectId("test-project")).thenReturn(Maybe.just(project));
      when(tenantDao.getTenantById("test-tenant")).thenReturn(Maybe.empty());

      Exception ex = assertThrows(RuntimeException.class,
          () -> usageLimitService.setCustomLimits(request).blockingGet());
      assertTrue(ex.getMessage().contains("Tenant not found"));
    }

    @Test
    void shouldFailWhenTenantHasNoTierAssigned() throws Exception {
      Project project = createMockProject();
      Tenant tenant = createMockTenant(null);
      ProjectUsageLimit currentLimit = createMockUsageLimit();

      SetCustomLimitsRequest request = SetCustomLimitsRequest.builder()
          .projectId("test-project")
          .limits(new HashMap<>())
          .performedBy("admin@example.com")
          .build();

      when(projectDao.getProjectByProjectId("test-project")).thenReturn(Maybe.just(project));
      when(tenantDao.getTenantById("test-tenant")).thenReturn(Maybe.just(tenant));
      when(usageLimitDao.getActiveLimitByProjectId("test-project")).thenReturn(Maybe.just(currentLimit));

      Exception ex = assertThrows(ForbiddenOperationException.class,
          () -> usageLimitService.setCustomLimits(request).blockingGet());
      assertTrue(ex.getMessage().contains("no tier assigned"));
    }

    @Test
    void shouldFailWhenCustomLimitsNotAllowedForTier() throws Exception {
      Project project = createMockProject();
      Tenant tenant = createMockTenant(1);
      Tier freeTier = createMockTier(false);
      ProjectUsageLimit currentLimit = createMockUsageLimit();

      SetCustomLimitsRequest request = SetCustomLimitsRequest.builder()
          .projectId("test-project")
          .limits(new HashMap<>())
          .performedBy("admin@example.com")
          .build();

      when(projectDao.getProjectByProjectId("test-project")).thenReturn(Maybe.just(project));
      when(tenantDao.getTenantById("test-tenant")).thenReturn(Maybe.just(tenant));
      when(tierDao.getTierById(1)).thenReturn(Maybe.just(freeTier));
      when(usageLimitDao.getActiveLimitByProjectId("test-project")).thenReturn(Maybe.just(currentLimit));

      Exception ex = assertThrows(ForbiddenOperationException.class,
          () -> usageLimitService.setCustomLimits(request).blockingGet());
      assertTrue(ex.getMessage().contains("enterprise tier"));
    }

    @Test
    void shouldValidateNegativeValue() throws Exception {
      Project project = createMockProject();
      Tenant tenant = createMockTenant(2);
      Tier tier = createMockTier(true);
      ProjectUsageLimit currentLimit = createMockUsageLimit();

      Map<String, UsageLimitValue> invalidLimits = new HashMap<>();
      invalidLimits.put("max_events", UsageLimitValue.builder().value(-100L).build());

      SetCustomLimitsRequest request = SetCustomLimitsRequest.builder()
          .projectId("test-project")
          .limits(invalidLimits)
          .performedBy("admin@example.com")
          .build();

      when(projectDao.getProjectByProjectId("test-project")).thenReturn(Maybe.just(project));
      when(tenantDao.getTenantById("test-tenant")).thenReturn(Maybe.just(tenant));
      when(tierDao.getTierById(2)).thenReturn(Maybe.just(tier));
      when(usageLimitDao.getActiveLimitByProjectId("test-project")).thenReturn(Maybe.just(currentLimit));

      Exception ex = assertThrows(IllegalArgumentException.class,
          () -> usageLimitService.setCustomLimits(request).blockingGet());
      assertTrue(ex.getMessage().contains("non-negative"));
    }

    @Test
    void shouldValidateOverageRange() throws Exception {
      Project project = createMockProject();
      Tenant tenant = createMockTenant(2);
      Tier tier = createMockTier(true);
      ProjectUsageLimit currentLimit = createMockUsageLimit();

      Map<String, UsageLimitValue> invalidLimits = new HashMap<>();
      invalidLimits.put("max_events", UsageLimitValue.builder().value(1000L).overage(150).build());

      SetCustomLimitsRequest request = SetCustomLimitsRequest.builder()
          .projectId("test-project")
          .limits(invalidLimits)
          .performedBy("admin@example.com")
          .build();

      when(projectDao.getProjectByProjectId("test-project")).thenReturn(Maybe.just(project));
      when(tenantDao.getTenantById("test-tenant")).thenReturn(Maybe.just(tenant));
      when(tierDao.getTierById(2)).thenReturn(Maybe.just(tier));
      when(usageLimitDao.getActiveLimitByProjectId("test-project")).thenReturn(Maybe.just(currentLimit));

      Exception ex = assertThrows(IllegalArgumentException.class,
          () -> usageLimitService.setCustomLimits(request).blockingGet());
      assertTrue(ex.getMessage().contains("between 0 and 100"));
    }
  }

  // ==================== CREATE INITIAL LIMITS (TRANSACTIONAL) TESTS ====================

  @Nested
  class TestCreateInitialLimits {

    @Test
    void shouldCreateInitialLimitsWithFreeTierDefaults() throws Exception {
      Map<String, UsageLimitValue> freeTierDefaults = new HashMap<>();
      freeTierDefaults.put("max_events", UsageLimitValue.builder().value(1000L).build());

      ProjectUsageLimit createdLimit = createMockUsageLimit();

      when(tierService.getFreeTierDefaults()).thenReturn(Single.just(freeTierDefaults));
      when(usageLimitDao.createUsageLimit(eq(sqlConnection), eq("test-project"), anyString(), eq("creator@example.com")))
          .thenReturn(Single.just(createdLimit));

      ProjectUsageLimit result = usageLimitService.createInitialLimits(sqlConnection, "test-project", "creator@example.com").blockingGet();

      assertNotNull(result);
      verify(tierService).getFreeTierDefaults();
      verify(usageLimitDao).createUsageLimit(eq(sqlConnection), eq("test-project"), anyString(), eq("creator@example.com"));
    }

    @Test
    void shouldFailWhenFreeTierNotFound() {
      when(tierService.getFreeTierDefaults())
          .thenReturn(Single.error(new RuntimeException("Free tier not found")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> usageLimitService.createInitialLimits(sqlConnection, "test-project", "creator@example.com").blockingGet());
      assertTrue(ex.getMessage().contains("Free tier not found"));
    }
  }

  // ==================== RESET TO DEFAULTS TESTS ====================

  @Nested
  class TestResetToDefaults {

    @Test
    void shouldResetToSpecificTierDefaults() throws Exception {
      Tier enterpriseTier = createMockTier(true);
      ProjectUsageLimit updatedLimit = createMockUsageLimit();

      ResetLimitsRequest request = ResetLimitsRequest.builder()
          .projectId("test-project")
          .tierId(2)
          .performedBy("admin@example.com")
          .build();

      when(tierDao.getTierById(2)).thenReturn(Maybe.just(enterpriseTier));
      when(usageLimitDao.updateUsageLimits(eq("test-project"), anyString(), eq("admin@example.com"), eq("reset_to_defaults")))
          .thenReturn(Single.just(updatedLimit));

      ProjectUsageLimitInfo result = usageLimitService.resetToDefaults(request).blockingGet();

      assertNotNull(result);
      verify(tierDao).getTierById(2);
    }

    @Test
    void shouldDefaultToFreeTierWhenTierIdNull() throws Exception {
      Tier freeTier = Tier.builder()
          .tierId(1)
          .name("free")
          .isCustomLimitsAllowed(false)
          .usageLimitDefaults("{\"max_events\":{\"value\":1000}}")
          .isActive(true)
          .build();
      ProjectUsageLimit updatedLimit = createMockUsageLimit();

      ResetLimitsRequest request = ResetLimitsRequest.builder()
          .projectId("test-project")
          .tierId(null)
          .performedBy("admin@example.com")
          .build();

      when(tierDao.getTierById(1)).thenReturn(Maybe.just(freeTier));
      when(usageLimitDao.updateUsageLimits(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(Single.just(updatedLimit));

      usageLimitService.resetToDefaults(request).blockingGet();

      verify(tierDao).getTierById(1);
    }

    @Test
    void shouldFailWhenTierNotFound() {
      ResetLimitsRequest request = ResetLimitsRequest.builder()
          .projectId("test-project")
          .tierId(999)
          .performedBy("admin@example.com")
          .build();

      when(tierDao.getTierById(999)).thenReturn(Maybe.empty());

      Exception ex = assertThrows(RuntimeException.class,
          () -> usageLimitService.resetToDefaults(request).blockingGet());
      assertTrue(ex.getMessage().contains("Tier not found"));
    }

    @Test
    void shouldFailWhenTierIsInactive() throws Exception {
      Tier inactiveTier = createMockTier(true);
      inactiveTier.setIsActive(false);

      ResetLimitsRequest request = ResetLimitsRequest.builder()
          .projectId("test-project")
          .tierId(2)
          .performedBy("admin@example.com")
          .build();

      when(tierDao.getTierById(2)).thenReturn(Maybe.just(inactiveTier));

      Exception ex = assertThrows(IllegalStateException.class,
          () -> usageLimitService.resetToDefaults(request).blockingGet());
      assertTrue(ex.getMessage().contains("inactive tier"));
    }
  }

  // ==================== EDGE CASES ====================

  @Nested
  class TestEdgeCases {

    @Test
    void shouldHandleEmptyUsageLimitsJson() {
      ProjectUsageLimit limitWithEmptyJson = ProjectUsageLimit.builder()
          .projectUsageLimitId(1L)
          .projectId("test-project")
          .usageLimits("")
          .isActive(true)
          .build();

      when(usageLimitDao.getActiveLimitByProjectId("test-project"))
          .thenReturn(Maybe.just(limitWithEmptyJson));

      ProjectUsageLimitInfo result = usageLimitService.getProjectLimits("test-project").blockingGet();

      assertNotNull(result);
      assertTrue(result.getUsageLimits().isEmpty());
    }

    @Test
    void shouldHandleNullUsageLimitsJson() {
      ProjectUsageLimit limitWithNullJson = ProjectUsageLimit.builder()
          .projectUsageLimitId(1L)
          .projectId("test-project")
          .usageLimits(null)
          .isActive(true)
          .build();

      when(usageLimitDao.getActiveLimitByProjectId("test-project"))
          .thenReturn(Maybe.just(limitWithNullJson));

      ProjectUsageLimitInfo result = usageLimitService.getProjectLimits("test-project").blockingGet();

      assertNotNull(result);
      assertTrue(result.getUsageLimits().isEmpty());
    }

    @Test
    void shouldHandleNullOverageInFinalThresholdComputation() throws Exception {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_events", UsageLimitValue.builder()
          .displayName("Max Events")
          .value(1000L)
          .overage(null)
          .build());

      ProjectUsageLimit mockLimit = ProjectUsageLimit.builder()
          .projectUsageLimitId(1L)
          .projectId("test-project")
          .usageLimits(objectMapper.writeValueAsString(limits))
          .isActive(true)
          .build();

      when(usageLimitDao.getActiveLimitByProjectId("test-project"))
          .thenReturn(Maybe.just(mockLimit));

      ProjectUsageLimitInfo result = usageLimitService.getProjectLimits("test-project").blockingGet();

      assertEquals(1000L, result.getUsageLimits().get("max_events").getFinalThreshold());
    }

    @Test
    void shouldHandleNullValueInFinalThresholdComputation() throws Exception {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_events", UsageLimitValue.builder()
          .displayName("Max Events")
          .value(null)
          .overage(10)
          .build());

      ProjectUsageLimit mockLimit = ProjectUsageLimit.builder()
          .projectUsageLimitId(1L)
          .projectId("test-project")
          .usageLimits(objectMapper.writeValueAsString(limits))
          .isActive(true)
          .build();

      when(usageLimitDao.getActiveLimitByProjectId("test-project"))
          .thenReturn(Maybe.just(mockLimit));

      ProjectUsageLimitInfo result = usageLimitService.getProjectLimits("test-project").blockingGet();

      assertEquals(null, result.getUsageLimits().get("max_events").getFinalThreshold());
    }
  }
}
