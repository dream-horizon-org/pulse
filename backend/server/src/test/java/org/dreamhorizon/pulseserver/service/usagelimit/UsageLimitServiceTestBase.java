package org.dreamhorizon.pulseserver.service.usagelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.dao.tier.TierDao;
import org.dreamhorizon.pulseserver.dao.tier.models.Tier;
import org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitDao;
import org.dreamhorizon.pulseserver.dao.usagelimit.models.ProjectUsageLimit;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.UserService;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Shared mocks, {@link UsageLimitService} wiring, and test fixtures for {@link UsageLimitService} tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class UsageLimitServiceTestBase {

  @Mock
  protected ProjectUsageLimitDao usageLimitDao;

  @Mock
  protected ProjectDao projectDao;

  @Mock
  protected TenantDao tenantDao;

  @Mock
  protected TierDao tierDao;

  @Mock
  protected TierService tierService;

  @Mock
  protected ClickhouseQueryService clickhouseQueryService;

  @Mock
  protected OpenFgaService openFgaService;

  @Mock
  protected UserService userService;

  @Mock
  protected SqlConnection sqlConnection;

  protected ObjectMapper objectMapper;
  protected UsageLimitService usageLimitService;

  @BeforeEach
  void setUpUsageLimitService() {
    objectMapper = new ObjectMapper();
    usageLimitService = new UsageLimitService(usageLimitDao, projectDao, tenantDao, tierDao, tierService,
        objectMapper, clickhouseQueryService, openFgaService, userService);
  }

  protected ProjectUsageLimit createMockUsageLimit() throws Exception {
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

  protected Project createMockProject() {
    return Project.builder()
        .projectId("test-project")
        .name("Test Project")
        .tenantId("test-tenant")
        .isActive(true)
        .build();
  }

  protected Tenant createMockTenant(Integer tierId) {
    return Tenant.builder()
        .tenantId("test-tenant")
        .name("Test Tenant")
        .tierId(tierId)
        .isActive(true)
        .build();
  }

  protected Tier createMockTier(boolean customLimitsAllowed) throws Exception {
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
}
