package org.dreamhorizon.pulseserver.service.tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tier.TierDao;
import org.dreamhorizon.pulseserver.dao.tier.models.Tier;
import org.dreamhorizon.pulseserver.service.tier.models.CreateTierRequest;
import org.dreamhorizon.pulseserver.service.tier.models.TierInfo;
import org.dreamhorizon.pulseserver.service.tier.models.TierPublicInfo;
import org.dreamhorizon.pulseserver.service.tier.models.UpdateTierRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitParameter;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TierServiceTest {

  @Mock
  TierDao tierDao;

  @Mock
  TenantDao tenantDao;

  ObjectMapper objectMapper;
  TierService tierService;

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
    tierService = new TierService(tierDao, tenantDao, objectMapper);
  }

  private Map<String, UsageLimitValue> createValidUsageLimitDefaults() {
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

  private String serializeDefaults(Map<String, UsageLimitValue> defaults) {
    try {
      return objectMapper.writeValueAsString(defaults);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Tier createMockTier() {
    return Tier.builder()
        .tierId(1)
        .name("free")
        .displayName("Free Tier")
        .isCustomLimitsAllowed(false)
        .usageLimitDefaults(serializeDefaults(createValidUsageLimitDefaults()))
        .isActive(true)
        .createdAt(Instant.now())
        .build();
  }

  private Tier createMockTierWithAllParams() {
    Map<String, UsageLimitValue> defaults = createValidUsageLimitDefaults();
    return Tier.builder()
        .tierId(2)
        .name("enterprise")
        .displayName("Enterprise Tier")
        .isCustomLimitsAllowed(true)
        .usageLimitDefaults(serializeDefaults(defaults))
        .isActive(true)
        .createdAt(Instant.now())
        .build();
  }

  // ==================== CREATE TIER TESTS ====================

  @Nested
  class TestCreateTier {

    @Test
    void shouldCreateTierSuccessfully() {
      Map<String, UsageLimitValue> defaults = createValidUsageLimitDefaults();
      CreateTierRequest request = CreateTierRequest.builder()
          .name("premium")
          .displayName("Premium Tier")
          .isCustomLimitsAllowed(true)
          .usageLimitDefaults(defaults)
          .build();

      Tier mockTier = createMockTierWithAllParams();
      mockTier.setName("premium");
      mockTier.setDisplayName("Premium Tier");

      when(tierDao.tierNameExists("premium")).thenReturn(Single.just(false));
      when(tierDao.createTier(any(Tier.class))).thenReturn(Single.just(mockTier));

      TierInfo result = tierService.createTier(request).blockingGet();

      assertNotNull(result);
      assertEquals("premium", result.getName());
      assertEquals("Premium Tier", result.getDisplayName());
      assertTrue(result.getIsCustomLimitsAllowed());
      verify(tierDao).createTier(any(Tier.class));
    }

    @Test
    void shouldFailWhenTierNameAlreadyExists() {
      Map<String, UsageLimitValue> defaults = createValidUsageLimitDefaults();
      CreateTierRequest request = CreateTierRequest.builder()
          .name("free")
          .displayName("Free Tier")
          .usageLimitDefaults(defaults)
          .build();

      when(tierDao.tierNameExists("free")).thenReturn(Single.just(true));

      Exception ex = assertThrows(IllegalArgumentException.class,
          () -> tierService.createTier(request).blockingGet());
      assertTrue(ex.getMessage().contains("already exists"));
      verify(tierDao, never()).createTier(any());
    }

    @Test
    void shouldFailWhenValueIsNegative() {
      Map<String, UsageLimitValue> defaults = createValidUsageLimitDefaults();
      defaults.get(UsageLimitParameter.MAX_EVENTS_PER_PROJECT.getKey()).setValue(-100L);

      CreateTierRequest request = CreateTierRequest.builder()
          .name("test")
          .displayName("Test Tier")
          .usageLimitDefaults(defaults)
          .build();

      Exception ex = assertThrows(IllegalArgumentException.class,
          () -> tierService.createTier(request).blockingGet());
      assertTrue(ex.getMessage().contains("non-negative"));
    }

    @Test
    void shouldFailWhenOverageExceeds100() {
      Map<String, UsageLimitValue> defaults = createValidUsageLimitDefaults();
      defaults.get(UsageLimitParameter.MAX_EVENTS_PER_PROJECT.getKey()).setOverage(150);

      CreateTierRequest request = CreateTierRequest.builder()
          .name("test")
          .displayName("Test Tier")
          .usageLimitDefaults(defaults)
          .build();

      Exception ex = assertThrows(IllegalArgumentException.class,
          () -> tierService.createTier(request).blockingGet());
      assertTrue(ex.getMessage().contains("between 0 and 100"));
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      Map<String, UsageLimitValue> defaults = createValidUsageLimitDefaults();
      CreateTierRequest request = CreateTierRequest.builder()
          .name("test")
          .displayName("Test")
          .usageLimitDefaults(defaults)
          .build();

      when(tierDao.tierNameExists("test")).thenReturn(Single.just(false));
      when(tierDao.createTier(any(Tier.class)))
          .thenReturn(Single.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tierService.createTier(request).blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  // ==================== UPDATE TIER TESTS ====================

  @Nested
  class TestUpdateTier {

    @Test
    void shouldUpdateTierSuccessfully() {
      Tier existingTier = createMockTierWithAllParams();
      Map<String, UsageLimitValue> newDefaults = createValidUsageLimitDefaults();
      newDefaults.get(UsageLimitParameter.MAX_EVENTS_PER_PROJECT.getKey()).setValue(5000L);

      UpdateTierRequest request = UpdateTierRequest.builder()
          .tierId(2)
          .displayName("Updated Enterprise")
          .isCustomLimitsAllowed(true)
          .usageLimitDefaults(newDefaults)
          .build();

      when(tierDao.getTierById(2)).thenReturn(Maybe.just(existingTier));
      when(tierDao.updateTier(any(Tier.class))).thenAnswer(invocation -> {
        Tier tier = invocation.getArgument(0);
        return Single.just(tier);
      });

      TierInfo result = tierService.updateTier(request).blockingGet();

      assertNotNull(result);
      assertEquals("Updated Enterprise", result.getDisplayName());
      verify(tierDao).updateTier(any(Tier.class));
    }

    @Test
    void shouldPreserveNameOnUpdate() {
      Tier existingTier = createMockTierWithAllParams();
      existingTier.setName("original_name");

      UpdateTierRequest request = UpdateTierRequest.builder()
          .tierId(2)
          .displayName("New Display Name")
          .build();

      when(tierDao.getTierById(2)).thenReturn(Maybe.just(existingTier));
      when(tierDao.updateTier(any(Tier.class))).thenAnswer(invocation -> {
        Tier tier = invocation.getArgument(0);
        assertEquals("original_name", tier.getName());
        return Single.just(tier);
      });

      tierService.updateTier(request).blockingGet();
      verify(tierDao).updateTier(any(Tier.class));
    }

    @Test
    void shouldFailWhenTierNotFound() {
      UpdateTierRequest request = UpdateTierRequest.builder()
          .tierId(999)
          .displayName("Test")
          .build();

      when(tierDao.getTierById(999)).thenReturn(Maybe.empty());

      Exception ex = assertThrows(RuntimeException.class,
          () -> tierService.updateTier(request).blockingGet());
      assertTrue(ex.getMessage().contains("Tier not found"));
    }
  }

  // ==================== DEACTIVATE TIER TESTS ====================

  @Nested
  class TestDeactivateTier {

    @Test
    void shouldDeactivateTierSuccessfully() {
      Tier activeTier = createMockTier();
      activeTier.setIsActive(true);

      Tier deactivatedTier = createMockTier();
      deactivatedTier.setIsActive(false);

      when(tierDao.getTierById(1)).thenReturn(Maybe.just(activeTier));
      when(tierDao.deactivateTier(1)).thenReturn(Single.just(deactivatedTier));

      TierInfo result = tierService.deactivateTier(1).blockingGet();

      assertNotNull(result);
      assertFalse(result.getIsActive());
      verify(tierDao).deactivateTier(1);
    }

    @Test
    void shouldReturnTierWhenAlreadyInactive() {
      Tier inactiveTier = createMockTier();
      inactiveTier.setIsActive(false);

      when(tierDao.getTierById(1)).thenReturn(Maybe.just(inactiveTier));

      TierInfo result = tierService.deactivateTier(1).blockingGet();

      assertNotNull(result);
      assertFalse(result.getIsActive());
      verify(tierDao, never()).deactivateTier(1);
    }

    @Test
    void shouldFailWhenTierNotFound() {
      when(tierDao.getTierById(999)).thenReturn(Maybe.empty());

      Exception ex = assertThrows(RuntimeException.class,
          () -> tierService.deactivateTier(999).blockingGet());
      assertTrue(ex.getMessage().contains("Tier not found"));
    }
  }

  // ==================== ACTIVATE TIER TESTS ====================

  @Nested
  class TestActivateTier {

    @Test
    void shouldActivateTierSuccessfully() {
      Tier inactiveTier = createMockTier();
      inactiveTier.setIsActive(false);

      Tier activatedTier = createMockTier();
      activatedTier.setIsActive(true);

      when(tierDao.getTierById(1)).thenReturn(Maybe.just(inactiveTier));
      when(tierDao.activateTier(1)).thenReturn(Single.just(activatedTier));

      TierInfo result = tierService.activateTier(1).blockingGet();

      assertNotNull(result);
      assertTrue(result.getIsActive());
      verify(tierDao).activateTier(1);
    }

    @Test
    void shouldReturnTierWhenAlreadyActive() {
      Tier activeTier = createMockTier();
      activeTier.setIsActive(true);

      when(tierDao.getTierById(1)).thenReturn(Maybe.just(activeTier));

      TierInfo result = tierService.activateTier(1).blockingGet();

      assertNotNull(result);
      assertTrue(result.getIsActive());
      verify(tierDao, never()).activateTier(1);
    }

    @Test
    void shouldFailWhenTierNotFound() {
      when(tierDao.getTierById(999)).thenReturn(Maybe.empty());

      Exception ex = assertThrows(RuntimeException.class,
          () -> tierService.activateTier(999).blockingGet());
      assertTrue(ex.getMessage().contains("Tier not found"));
    }
  }

  // ==================== GET TIER TESTS ====================

  @Nested
  class TestGetTierById {

    @Test
    void shouldGetTierByIdSuccessfully() {
      Tier mockTier = createMockTier();
      when(tierDao.getTierById(1)).thenReturn(Maybe.just(mockTier));

      TierInfo result = tierService.getTierById(1).blockingGet();

      assertNotNull(result);
      assertEquals(1, result.getTierId());
      assertEquals("free", result.getName());
    }

    @Test
    void shouldReturnEmptyWhenTierNotFound() {
      when(tierDao.getTierById(999)).thenReturn(Maybe.empty());

      TierInfo result = tierService.getTierById(999).blockingGet();

      assertEquals(null, result);
    }
  }

  @Nested
  class TestGetTierPublicById {

    @Test
    void shouldGetPublicTierInfoSuccessfully() {
      Tier mockTier = createMockTier();
      mockTier.setIsActive(true);
      when(tierDao.getTierById(1)).thenReturn(Maybe.just(mockTier));

      TierPublicInfo result = tierService.getTierPublicById(1).blockingGet();

      assertNotNull(result);
      assertEquals(1, result.getTierId());
      assertEquals("free", result.getName());
    }

    @Test
    void shouldReturnEmptyForInactiveTier() {
      Tier inactiveTier = createMockTier();
      inactiveTier.setIsActive(false);
      when(tierDao.getTierById(1)).thenReturn(Maybe.just(inactiveTier));

      TierPublicInfo result = tierService.getTierPublicById(1).blockingGet();

      assertEquals(null, result);
    }
  }

  @Nested
  class TestGetTierNameById {

    @Test
    void shouldGetTierNameSuccessfully() {
      Tier mockTier = createMockTier();
      mockTier.setName("enterprise");
      when(tierDao.getTierById(2)).thenReturn(Maybe.just(mockTier));

      String result = tierService.getTierNameById(2).blockingGet();

      assertEquals("enterprise", result);
    }

    @Test
    void shouldReturnFreeWhenTierIdIsNull() {
      String result = tierService.getTierNameById(null).blockingGet();

      assertEquals("free", result);
    }
  }

  // ==================== LIST TIERS TESTS ====================

  @Nested
  class TestGetAllActiveTiersPublic {

    @Test
    void shouldGetAllActiveTiersSuccessfully() {
      Tier tier1 = createMockTier();
      Tier tier2 = createMockTier();
      tier2.setTierId(2);
      tier2.setName("enterprise");

      when(tierDao.getAllActiveTiers()).thenReturn(Flowable.just(tier1, tier2));

      List<TierPublicInfo> result = tierService.getAllActiveTiersPublic().toList().blockingGet();

      assertNotNull(result);
      assertEquals(2, result.size());
    }

    @Test
    void shouldReturnEmptyListWhenNoActiveTiers() {
      when(tierDao.getAllActiveTiers()).thenReturn(Flowable.empty());

      List<TierPublicInfo> result = tierService.getAllActiveTiersPublic().toList().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class TestGetAllTiers {

    @Test
    void shouldGetAllTiersIncludingInactive() {
      Tier activeTier = createMockTier();
      Tier inactiveTier = createMockTier();
      inactiveTier.setTierId(2);
      inactiveTier.setIsActive(false);

      when(tierDao.getAllTiers()).thenReturn(Flowable.just(activeTier, inactiveTier));

      List<TierInfo> result = tierService.getAllTiers().toList().blockingGet();

      assertNotNull(result);
      assertEquals(2, result.size());
    }
  }

  // ==================== GET FREE TIER DEFAULTS TESTS ====================

  @Nested
  class TestGetFreeTierDefaults {

    @Test
    void shouldGetFreeTierDefaultsSuccessfully() {
      Tier freeTier = createMockTier();
      when(tierDao.getTierById(1)).thenReturn(Maybe.just(freeTier));

      Map<String, UsageLimitValue> result = tierService.getFreeTierDefaults().blockingGet();

      assertNotNull(result);
      assertFalse(result.isEmpty());
    }

    @Test
    void shouldFailWhenFreeTierNotFound() {
      when(tierDao.getTierById(1)).thenReturn(Maybe.empty());

      Exception ex = assertThrows(RuntimeException.class,
          () -> tierService.getFreeTierDefaults().blockingGet());
      assertTrue(ex.getMessage().contains("Free tier not found"));
    }
  }
}
