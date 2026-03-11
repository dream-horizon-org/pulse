package org.dreamhorizon.pulseserver.service.tier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tier.TierDao;
import org.dreamhorizon.pulseserver.dao.tier.models.Tier;
import org.dreamhorizon.pulseserver.service.tier.models.CreateTierRequest;
import org.dreamhorizon.pulseserver.service.tier.models.TierInfo;
import org.dreamhorizon.pulseserver.service.tier.models.TierPublicInfo;
import org.dreamhorizon.pulseserver.service.tier.models.UpdateTierRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitValidator;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitParameter;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitPublicValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TierService {

  private final TierDao tierDao;
  private final TenantDao tenantDao;
  private final ObjectMapper objectMapper;

  /**
   * Creates a new tier with the given configuration.
   * Validates that all required usage limit parameters are present.
   */
  public Single<TierInfo> createTier(CreateTierRequest request) {
    log.info("Creating tier: {}", request.getName());

    return validateUsageLimitDefaults(request.getUsageLimitDefaults())
        .andThen(tierDao.tierNameExists(request.getName()))
        .flatMap(exists -> {
          if (exists) {
            return Single.error(new IllegalArgumentException("Tier with name '" + request.getName() + "' already exists"));
          }

          String usageLimitDefaultsJson;
          try {
            usageLimitDefaultsJson = objectMapper.writeValueAsString(request.getUsageLimitDefaults());
          } catch (JsonProcessingException e) {
            return Single.error(new RuntimeException("Failed to serialize usage limit defaults", e));
          }

          Tier tier = Tier.builder()
              .name(request.getName().toLowerCase().trim())
              .displayName(request.getDisplayName())
              .isCustomLimitsAllowed(request.getIsCustomLimitsAllowed() != null ? request.getIsCustomLimitsAllowed() : false)
              .usageLimitDefaults(usageLimitDefaultsJson)
              .isActive(true)
              .build();

          return tierDao.createTier(tier);
        })
        .map(this::mapToTierInfo)
        .doOnSuccess(tier -> log.info("Created tier: {} with ID: {}", tier.getName(), tier.getTierId()))
        .doOnError(error -> log.error("Failed to create tier: {}", request.getName(), error));
  }

  /**
   * Updates an existing tier.
   * Name cannot be changed.
   */
  public Single<TierInfo> updateTier(UpdateTierRequest request) {
    log.info("Updating tier: {}", request.getTierId());

    return tierDao.getTierById(request.getTierId())
        .switchIfEmpty(Single.error(new RuntimeException("Tier not found: " + request.getTierId())))
        .flatMap(existingTier -> {
          // Validate usage limit defaults if provided
          Completable validation = request.getUsageLimitDefaults() != null
              ? validateUsageLimitDefaults(request.getUsageLimitDefaults())
              : Completable.complete();

          return validation.andThen(Single.just(existingTier));
        })
        .flatMap(existingTier -> {
          String usageLimitDefaultsJson = existingTier.getUsageLimitDefaults();
          if (request.getUsageLimitDefaults() != null) {
            try {
              usageLimitDefaultsJson = objectMapper.writeValueAsString(request.getUsageLimitDefaults());
            } catch (JsonProcessingException e) {
              return Single.error(new RuntimeException("Failed to serialize usage limit defaults", e));
            }
          }

          Tier updatedTier = Tier.builder()
              .tierId(existingTier.getTierId())
              .name(existingTier.getName()) // Name cannot be changed
              .displayName(request.getDisplayName() != null ? request.getDisplayName() : existingTier.getDisplayName())
              .isCustomLimitsAllowed(request.getIsCustomLimitsAllowed() != null
                  ? request.getIsCustomLimitsAllowed()
                  : existingTier.getIsCustomLimitsAllowed())
              .usageLimitDefaults(usageLimitDefaultsJson)
              .isActive(existingTier.getIsActive())
              .createdAt(existingTier.getCreatedAt())
              .build();

          return tierDao.updateTier(updatedTier);
        })
        .map(this::mapToTierInfo)
        .doOnSuccess(tier -> log.info("Updated tier: {}", tier.getTierId()))
        .doOnError(error -> log.error("Failed to update tier: {}", request.getTierId(), error));
  }

  /**
   * Deactivates a tier (soft-delete).
   * New tenants cannot be assigned to this tier, but existing tenants are not affected.
   */
  public Single<TierInfo> deactivateTier(Integer tierId) {
    log.info("Deactivating tier: {}", tierId);

    return tierDao.getTierById(tierId)
        .switchIfEmpty(Single.error(new RuntimeException("Tier not found: " + tierId)))
        .flatMap(tier -> {
          if (!tier.getIsActive()) {
            log.warn("Tier {} is already inactive", tierId);
            return Single.just(tier);
          }
          return tierDao.deactivateTier(tierId);
        })
        .map(this::mapToTierInfo)
        .doOnSuccess(tier -> log.info("Deactivated tier: {}", tierId))
        .doOnError(error -> log.error("Failed to deactivate tier: {}", tierId, error));
  }

  /**
   * Reactivates a previously deactivated tier.
   */
  public Single<TierInfo> activateTier(Integer tierId) {
    log.info("Activating tier: {}", tierId);

    return tierDao.getTierById(tierId)
        .switchIfEmpty(Single.error(new RuntimeException("Tier not found: " + tierId)))
        .flatMap(tier -> {
          if (tier.getIsActive()) {
            log.warn("Tier {} is already active", tierId);
            return Single.just(tier);
          }
          return tierDao.activateTier(tierId);
        })
        .map(this::mapToTierInfo)
        .doOnSuccess(tier -> log.info("Activated tier: {}", tierId))
        .doOnError(error -> log.error("Failed to activate tier: {}", tierId, error));
  }

  /**
   * Gets a tier by ID (full info for internal use).
   */
  public Maybe<TierInfo> getTierById(Integer tierId) {
    return tierDao.getTierById(tierId)
        .map(this::mapToTierInfo)
        .doOnError(error -> log.error("Failed to get tier: {}", tierId, error));
  }

  /**
   * Gets a tier by ID (public simplified info).
   */
  public Maybe<TierPublicInfo> getTierPublicById(Integer tierId) {
    return tierDao.getTierById(tierId)
        .filter(Tier::getIsActive) // Only return active tiers for public API
        .map(this::mapToTierPublicInfo)
        .doOnError(error -> log.error("Failed to get public tier: {}", tierId, error));
  }

  /**
   * Maps tier ID to tier name string for API responses.
   * Returns "free" or "enterprise" based on tier ID.
   * Falls back to "free" if tier not found.
   * 
   * @param tierId The tier ID from tenant
   * @return Maybe<String> containing tier name ("free", "enterprise", etc.)
   */
  public Maybe<String> getTierNameById(Integer tierId) {
    if (tierId == null) {
      log.warn("Tier ID is null, defaulting to 'free'");
      return Maybe.just("free");
    }
    
    return tierDao.getTierById(tierId)
        .map(Tier::getName)
        .doOnSuccess(name -> log.debug("Mapped tier ID {} to name: {}", tierId, name))
        .doOnError(error -> log.error("Failed to get tier name for ID: {}", tierId, error));
  }

  /**
   * Gets all active tiers (public simplified info).
   */
  public Flowable<TierPublicInfo> getAllActiveTiersPublic() {
    return tierDao.getAllActiveTiers()
        .map(this::mapToTierPublicInfo)
        .doOnError(error -> log.error("Failed to get all active tiers", error));
  }

  /**
   * Gets all tiers including inactive (full info for internal use).
   */
  public Flowable<TierInfo> getAllTiers() {
    return tierDao.getAllTiers()
        .map(this::mapToTierInfo)
        .doOnError(error -> log.error("Failed to get all tiers", error));
  }

  /**
   * Gets the free tier defaults (tier_id = 1).
   */
  public Single<Map<String, UsageLimitValue>> getFreeTierDefaults() {
    return tierDao.getTierById(1) // Free tier is always ID 1
        .switchIfEmpty(Single.error(new RuntimeException("Free tier not found")))
        .map(tier -> parseUsageLimitDefaults(tier.getUsageLimitDefaults()));
  }

  /**
   * Validates that all required UsageLimitParameter keys are present in the defaults.
   */
  private Completable validateUsageLimitDefaults(Map<String, UsageLimitValue> defaults) {
    if (defaults == null || defaults.isEmpty()) {
      return Completable.error(new IllegalArgumentException("Usage limit defaults cannot be empty"));
    }

    Set<String> requiredKeys = Stream.of(UsageLimitParameter.values())
        .map(UsageLimitParameter::getKey)
        .collect(Collectors.toSet());

    Set<String> providedKeys = defaults.keySet();

    Set<String> missingKeys = requiredKeys.stream()
        .filter(key -> !providedKeys.contains(key))
        .collect(Collectors.toSet());

    if (!missingKeys.isEmpty()) {
      return Completable.error(new IllegalArgumentException(
          "Missing required usage limit parameters: " + String.join(", ", missingKeys)));
    }

    // Use shared validator for limit values
    UsageLimitValidator.validateLimitValues(defaults);

    return Completable.complete();
  }

  private TierInfo mapToTierInfo(Tier tier) {
    Map<String, UsageLimitValue> usageLimitDefaults = parseUsageLimitDefaults(tier.getUsageLimitDefaults());

    return TierInfo.builder()
        .tierId(tier.getTierId())
        .name(tier.getName())
        .displayName(tier.getDisplayName())
        .isCustomLimitsAllowed(tier.getIsCustomLimitsAllowed())
        .usageLimitDefaults(usageLimitDefaults)
        .isActive(tier.getIsActive())
        .createdAt(tier.getCreatedAt())
        .build();
  }

  private TierPublicInfo mapToTierPublicInfo(Tier tier) {
    Map<String, UsageLimitValue> fullDefaults = parseUsageLimitDefaults(tier.getUsageLimitDefaults());

    // Convert to simplified public format
    Map<String, UsageLimitPublicValue> publicLimits = new HashMap<>();
    for (Map.Entry<String, UsageLimitValue> entry : fullDefaults.entrySet()) {
      UsageLimitValue value = entry.getValue();
      publicLimits.put(entry.getKey(), UsageLimitPublicValue.builder()
          .displayName(value.getDisplayName())
          .windowType(value.getWindowType())
          .value(value.getValue())
          .build());
    }

    return TierPublicInfo.builder()
        .tierId(tier.getTierId())
        .name(tier.getName())
        .displayName(tier.getDisplayName())
        .isCustomLimitsAllowed(tier.getIsCustomLimitsAllowed())
        .usageLimits(publicLimits)
        .build();
  }

  private Map<String, UsageLimitValue> parseUsageLimitDefaults(String json) {
    if (json == null || json.isEmpty()) {
      return new HashMap<>();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, UsageLimitValue>>() {});
    } catch (JsonProcessingException e) {
      log.error("Failed to parse usage limit defaults JSON", e);
      return new HashMap<>();
    }
  }
}

