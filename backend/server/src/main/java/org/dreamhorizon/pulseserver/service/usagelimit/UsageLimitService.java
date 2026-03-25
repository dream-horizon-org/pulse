package org.dreamhorizon.pulseserver.service.usagelimit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitDao;
import org.dreamhorizon.pulseserver.rest.exception.ForbiddenOperationException;
import org.dreamhorizon.pulseserver.dao.usagelimit.models.ProjectUsageLimit;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.dao.tier.TierDao;
import org.dreamhorizon.pulseserver.dao.tier.models.Tier;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitPublicInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ResetLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.SetCustomLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitPublicValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class UsageLimitService {

  private static final int FREE_TIER_ID = 1;
  private static final String DISABLED_REASON_CUSTOM_OVERRIDE = "custom_override";
  private static final String DISABLED_REASON_RESET_TO_DEFAULTS = "reset_to_defaults";

  private final ProjectUsageLimitDao usageLimitDao;
  private final ProjectDao projectDao;
  private final TenantDao tenantDao;
  private final TierDao tierDao;
  private final TierService tierService;
  private final ObjectMapper objectMapper;

  // ==================== PUBLIC API ====================

  /**
   * Gets project usage limits (public, simplified info).
   */
  public Maybe<ProjectUsageLimitPublicInfo> getProjectLimitsPublic(String projectId) {
    return usageLimitDao.getActiveLimitByProjectId(projectId)
        .map(this::mapToPublicInfo)
        .doOnError(error -> log.error("Failed to get public limits for project: {}", projectId, error));
  }

  // ==================== INTERNAL API ====================

  /**
   * Gets project usage limits (full info for internal use).
   */
  public Maybe<ProjectUsageLimitInfo> getProjectLimits(String projectId) {
    return usageLimitDao.getActiveLimitByProjectId(projectId)
        .map(this::mapToInfo)
        .doOnError(error -> log.error("Failed to get limits for project: {}", projectId, error));
  }

  /**
   * Gets all active project usage limits.
   */
  public Flowable<ProjectUsageLimitInfo> getAllActiveLimits() {
    return usageLimitDao.getAllActiveLimits()
        .map(this::mapToInfo)
        .doOnError(error -> log.error("Failed to get all active limits", error));
  }

  /**
   * Gets all project usage limits including inactive.
   */
  public Flowable<ProjectUsageLimitInfo> getAllLimits() {
    return usageLimitDao.getAllLimits()
        .map(this::mapToInfo)
        .doOnError(error -> log.error("Failed to get all limits", error));
  }

  /**
   * Gets limit history for a project (inactive records).
   */
  public Flowable<ProjectUsageLimitInfo> getProjectLimitHistory(String projectId) {
    return usageLimitDao.getLimitHistoryByProjectId(projectId)
        .map(this::mapToInfo)
        .doOnError(error -> log.error("Failed to get limit history for project: {}", projectId, error));
  }

  /**
   * Sets custom limits for a project.
   * Validates that the project's tenant is on an enterprise tier (custom limits allowed).
   * Supports partial updates - only provided limits are changed.
   */
  public Single<ProjectUsageLimitInfo> setCustomLimits(SetCustomLimitsRequest request) {
    log.info("Setting custom limits for project: {} by: {}", request.getProjectId(), request.getPerformedBy());

    return buildCustomLimitContext(request.getProjectId())
        .flatMap(ctx -> {
          // Pure validation - no DB calls
          validateCustomLimitsAllowed(ctx);

          // Parse current limits and merge with new limits (partial update)
          Map<String, UsageLimitValue> currentLimits = parseUsageLimits(ctx.getCurrentLimit().getUsageLimits());
          Map<String, UsageLimitValue> mergedLimits = mergeLimits(currentLimits, request.getLimits());

          // Validate merged limits using shared validator
          UsageLimitValidator.validateLimitValues(mergedLimits);

          // Serialize and update
          String limitsJson;
          try {
            limitsJson = objectMapper.writeValueAsString(mergedLimits);
          } catch (JsonProcessingException e) {
            return Single.error(new RuntimeException("Failed to serialize limits", e));
          }

          return usageLimitDao.updateUsageLimits(
              request.getProjectId(),
              limitsJson,
              request.getPerformedBy(),
              DISABLED_REASON_CUSTOM_OVERRIDE);
        })
        .map(this::mapToInfo)
        .doOnSuccess(info -> log.info("Set custom limits for project: {}", request.getProjectId()))
        .doOnError(error -> log.error("Failed to set custom limits for project: {}", request.getProjectId(), error));
  }

  // ==================== TRANSACTIONAL METHODS ====================

  /**
   * Creates initial usage limits for a new project within a transaction.
   * Fetches free tier defaults internally and serializes them.
   *
   * @param conn      The SQL connection for the transaction
   * @param projectId The project ID
   * @param createdBy The user creating the project
   * @return Single containing the created usage limit
   */
  public Single<ProjectUsageLimit> createInitialLimits(SqlConnection conn, String projectId, String createdBy) {
    log.debug("Creating initial usage limits for project: {} within transaction", projectId);

    return tierService.getFreeTierDefaults()
        .flatMap(defaults -> {
          String usageLimitsJson;
          try {
            usageLimitsJson = objectMapper.writeValueAsString(defaults);
          } catch (JsonProcessingException e) {
            return Single.error(new RuntimeException("Failed to serialize usage limits", e));
          }
          return usageLimitDao.createUsageLimit(conn, projectId, usageLimitsJson, createdBy);
        })
        .doOnSuccess(limit -> log.debug("Created initial usage limits for project: {} within transaction", projectId))
        .doOnError(error -> log.error("Failed to create initial usage limits for project: {} within transaction", projectId, error));
  }

  /**
   * Resets project limits to tier defaults.
   * If tierId is not provided, defaults to free tier (1).
   */
  public Single<ProjectUsageLimitInfo> resetToDefaults(ResetLimitsRequest request) {
    int tierId = request.getTierId() != null ? request.getTierId() : FREE_TIER_ID;
    log.info("Resetting limits for project: {} to tier: {} by: {}",
        request.getProjectId(), tierId, request.getPerformedBy());

    return tierDao.getTierById(tierId)
        .switchIfEmpty(Single.error(new RuntimeException("Tier not found: " + tierId)))
        .flatMap(tier -> {
          if (!tier.getIsActive()) {
            return Single.error(new IllegalStateException("Cannot reset to inactive tier: " + tierId));
          }

          String defaultsJson = tier.getUsageLimitDefaults();
          return usageLimitDao.updateUsageLimits(
              request.getProjectId(),
              defaultsJson,
              request.getPerformedBy(),
              DISABLED_REASON_RESET_TO_DEFAULTS);
        })
        .map(this::mapToInfo)
        .doOnSuccess(info -> log.info("Reset limits for project: {} to tier: {}", request.getProjectId(), tierId))
        .doOnError(error -> log.error("Failed to reset limits for project: {}", request.getProjectId(), error));
  }

  // ==================== CONTEXT BUILDERS ====================

  /**
   * Builds context for custom limit operations.
   * Fetches project, tenant, tier, and current limits in a single chain.
   */
  private Single<CustomLimitContext> buildCustomLimitContext(String projectId) {
    return projectDao.getProjectByProjectId(projectId)
        .switchIfEmpty(Single.error(new RuntimeException("Project not found: " + projectId)))
        .flatMap(project -> tenantDao.getTenantById(project.getTenantId())
            .switchIfEmpty(Single.error(new RuntimeException("Tenant not found: " + project.getTenantId())))
            .flatMap(tenant -> {
              Integer tierId = tenant.getTierId();
              if (tierId == null) {
                // No tier assigned - return context with null tier
                return usageLimitDao.getActiveLimitByProjectId(projectId)
                    .switchIfEmpty(Single.error(new RuntimeException(
                        "No active limits found for project: " + projectId)))
                    .map(limit -> new CustomLimitContext(project, tenant, null, limit));
              }
              return tierDao.getTierById(tierId)
                  .toSingle()
                  .onErrorResumeNext(e -> Single.error(
                      new RuntimeException("Tier not found: " + tierId)))
                  .flatMap(tier -> usageLimitDao.getActiveLimitByProjectId(projectId)
                      .switchIfEmpty(Single.error(new RuntimeException(
                          "No active limits found for project: " + projectId)))
                      .map(limit -> new CustomLimitContext(project, tenant, tier, limit)));
            }));
  }

  // ==================== VALIDATORS (Pure, No DB) ====================

  /**
   * Validates that custom limits are allowed for the project's tenant.
   * Pure validation - uses already-fetched context.
   *
   * @throws ForbiddenOperationException if custom limits are not allowed
   */
  private void validateCustomLimitsAllowed(CustomLimitContext ctx) {
    if (ctx.getTier() == null) {
      throw new ForbiddenOperationException(
          "CUSTOM_LIMITS_NOT_ALLOWED",
          "Custom limits not allowed. Tenant has no tier assigned.");
    }
    if (!ctx.getTier().getIsCustomLimitsAllowed()) {
      throw new ForbiddenOperationException(
          "CUSTOM_LIMITS_NOT_ALLOWED",
          "Custom limits not allowed for this project. Tenant must be on enterprise tier.");
    }
  }

  // ==================== HELPER METHODS ====================

  /**
   * Merges new limits into current limits (partial update).
   */
  private Map<String, UsageLimitValue> mergeLimits(
      Map<String, UsageLimitValue> currentLimits,
      Map<String, UsageLimitValue> newLimits) {

    Map<String, UsageLimitValue> merged = new HashMap<>(currentLimits);

    for (Map.Entry<String, UsageLimitValue> entry : newLimits.entrySet()) {
      String key = entry.getKey();
      UsageLimitValue newValue = entry.getValue();
      UsageLimitValue currentValue = merged.get(key);

      if (currentValue != null) {
        // Merge individual fields - only override non-null values from new
        UsageLimitValue mergedValue = UsageLimitValue.builder()
            .displayName(newValue.getDisplayName() != null ? newValue.getDisplayName() : currentValue.getDisplayName())
            .windowType(newValue.getWindowType() != null ? newValue.getWindowType() : currentValue.getWindowType())
            .dataType(newValue.getDataType() != null ? newValue.getDataType() : currentValue.getDataType())
            .value(newValue.getValue() != null ? newValue.getValue() : currentValue.getValue())
            .overage(newValue.getOverage() != null ? newValue.getOverage() : currentValue.getOverage())
            .build();
        merged.put(key, mergedValue);
      } else {
        // New limit key - add as-is
        merged.put(key, newValue);
      }
    }

    return merged;
  }

  private ProjectUsageLimitInfo mapToInfo(ProjectUsageLimit limit) {
    Map<String, UsageLimitValue> usageLimits = parseUsageLimits(limit.getUsageLimits());

    return ProjectUsageLimitInfo.builder()
        .projectUsageLimitId(limit.getProjectUsageLimitId())
        .projectId(limit.getProjectId())
        .usageLimits(usageLimits)
        .isActive(limit.getIsActive())
        .createdAt(limit.getCreatedAt())
        .createdBy(limit.getCreatedBy())
        .disabledAt(limit.getDisabledAt())
        .disabledBy(limit.getDisabledBy())
        .disabledReason(limit.getDisabledReason())
        .build();
  }

  private ProjectUsageLimitPublicInfo mapToPublicInfo(ProjectUsageLimit limit) {
    Map<String, UsageLimitValue> fullLimits = parseUsageLimits(limit.getUsageLimits());

    // Convert to simplified public format
    Map<String, UsageLimitPublicValue> publicLimits = new HashMap<>();
    for (Map.Entry<String, UsageLimitValue> entry : fullLimits.entrySet()) {
      UsageLimitValue value = entry.getValue();
      publicLimits.put(entry.getKey(), UsageLimitPublicValue.builder()
          .displayName(value.getDisplayName())
          .windowType(value.getWindowType())
          .value(value.getValue())
          .build());
    }

    return ProjectUsageLimitPublicInfo.builder()
        .projectId(limit.getProjectId())
        .usageLimits(publicLimits)
        .build();
  }

  private Map<String, UsageLimitValue> parseUsageLimits(String json) {
    if (json == null || json.isEmpty()) {
      return new HashMap<>();
    }
    try {
      Map<String, UsageLimitValue> limits = objectMapper.readValue(json, new TypeReference<Map<String, UsageLimitValue>>() {});
      // Compute finalThreshold for each limit
      limits.values().forEach(this::computeFinalThreshold);
      return limits;
    } catch (JsonProcessingException e) {
      log.error("Failed to parse usage limits JSON", e);
      return new HashMap<>();
    }
  }

  /**
   * Computes the finalThreshold for a usage limit value.
   * finalThreshold = value + (value * overage / 100)
   * This represents the actual threshold including the overage allowance.
   */
  private void computeFinalThreshold(UsageLimitValue limitValue) {
    if (limitValue.getValue() == null) {
      limitValue.setFinalThreshold(null);
      return;
    }
    int overage = limitValue.getOverage() != null ? limitValue.getOverage() : 0;
    // Using Math.addExact and Math.multiplyExact would throw on overflow, but for our use case
    // simple arithmetic is sufficient since values are within reasonable bounds
    long threshold = limitValue.getValue() + (limitValue.getValue() * overage / 100);
    limitValue.setFinalThreshold(threshold);
  }

  // ==================== CONTEXT CLASSES ====================

  /**
   * Context for custom limit operations.
   * Contains all entities needed for validation and operation.
   */
  @RequiredArgsConstructor
  @Getter
  private static class CustomLimitContext {
    private final Project project;
    private final Tenant tenant;
    private final Tier tier;
    private final ProjectUsageLimit currentLimit;
  }
}

