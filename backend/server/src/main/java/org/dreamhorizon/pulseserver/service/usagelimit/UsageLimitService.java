package org.dreamhorizon.pulseserver.service.usagelimit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitDao;
import org.dreamhorizon.pulseserver.rest.exception.ForbiddenOperationException;
import org.dreamhorizon.pulseserver.dao.usagelimit.models.ProjectUsageLimit;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.UserService;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.dao.tier.TierDao;
import org.dreamhorizon.pulseserver.dao.tier.models.Tier;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.dreamhorizon.pulseserver.service.usagelimit.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitPublicInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ResetLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.SetCustomLimitsRequest;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitParameter;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitPublicValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotification;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotificationResult;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageStats;
import org.dreamhorizon.pulseserver.constant.NotificationConstants;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class UsageLimitService {

  private static final int FREE_TIER_ID = 1;
  private static final String DISABLED_REASON_CUSTOM_OVERRIDE = "custom_override";
  private static final String DISABLED_REASON_RESET_TO_DEFAULTS = "reset_to_defaults";
  private static final List<Integer> NOTIFICATION_THRESHOLDS = List.of(50, 75, 100);

  private final ProjectUsageLimitDao usageLimitDao;
  private final ProjectDao projectDao;
  private final TenantDao tenantDao;
  private final TierDao tierDao;
  private final TierService tierService;
  private final ObjectMapper objectMapper;
  private final ClickhouseQueryService clickhouseQueryService;
  private final OpenFgaService openFgaService;
  private final UserService userService;

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

    // Map notification status
    NotificationStatus notificationStatus = null;
    if (limit.getThresholdsNotified() != null) {
      notificationStatus = NotificationStatus.builder()
          .thresholdsNotified(limit.getThresholdsNotified())
          .createdAt(limit.getNotificationCreatedAt())
          .build();
    }

    return ProjectUsageLimitInfo.builder()
        .projectUsageLimitId(limit.getProjectUsageLimitId())
        .projectId(limit.getProjectId())
        .projectName(limit.getProjectName())
        .tenantId(limit.getTenantId())
        .usageLimits(usageLimits)
        .isActive(limit.getIsActive())
        .createdAt(limit.getCreatedAt())
        .createdBy(limit.getCreatedBy())
        .disabledAt(limit.getDisabledAt())
        .disabledBy(limit.getDisabledBy())
        .disabledReason(limit.getDisabledReason())
        .notificationStatus(notificationStatus)
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


  /**
   * Mark specific thresholds as notified for the current month.
   */
  public Single<NotificationStatusResponse> markThresholdsNotified(String projectId, List<Integer> thresholds) {
    return usageLimitDao.markThresholdsNotified(projectId, thresholds)
        .map(record -> {
          String month = record.getCreatedAt() != null 
              ? record.getCreatedAt().atZone(java.time.ZoneOffset.UTC)
                  .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
              : null;
          
          return NotificationStatusResponse.builder()
              .projectId(record.getProjectId())
              .month(month)
              .thresholdsNotified(record.getThresholdsNotified())
              .createdAt(record.getCreatedAt())
              .updatedAt(record.getUpdatedAt())
              .build();
        });
  }


  /**
   * Analyzes all projects and determines which usage notifications need to be sent.
   * Does NOT send notifications - just returns the list.
   */
  public Single<UsageNotificationResult> getUsageNotifications() {
    log.info("=== Analyzing usage limits for notifications ===");
    Instant startTime = Instant.now();

    return getAllActiveLimits()
        .toList()
        .flatMap(limits -> {
          log.info("Found {} active project limits", limits.size());

          return clickhouseQueryService.getCurrentMonthUsage()
              .flatMap(usageStatsMap -> {
                List<UsageNotification> notifications = collectDueNotifications(limits, usageStatsMap);
                Instant endTime = Instant.now();
                log.info("✅ Analysis complete: {} notifications due across {} projects (took {}ms)",
                    notifications.size(), limits.size(),
                    java.time.Duration.between(startTime, endTime).toMillis());

                if (notifications.isEmpty()) {
                  return Single.just(UsageNotificationResult.builder()
                      .notifications(notifications)
                      .totalProjectsChecked(limits.size())
                      .notificationsDue(0)
                      .checkedAt(endTime)
                      .build());
                }

                return attachRecipientsAndBuildResult(notifications, limits, endTime);
              });
        })
        .doOnError(error ->
            log.error("❌ Failed to analyze usage notifications", error)
        );
  }

  /**
   * Builds the list of notifications that are due (without recipient resolution).
   */
  private List<UsageNotification> collectDueNotifications(
      List<ProjectUsageLimitInfo> limits,
      Map<String, UsageStats> usageStatsMap) {
    List<UsageNotification> notifications = new ArrayList<>();
    for (ProjectUsageLimitInfo limit : limits) {
      UsageStats usage = usageStatsMap.get(limit.getProjectId());
      UsageNotification notification = buildNotificationIfDue(limit, usage);
      if (notification != null) {
        notifications.add(notification);
      }
    }
    return notifications;
  }

  /**
   * When usage exists and a threshold notification is due, builds the notification; otherwise null.
   */
  private UsageNotification buildNotificationIfDue(ProjectUsageLimitInfo limit, UsageStats usage) {
    String projectId = limit.getProjectId();
    if (usage == null) {
      log.debug("No usage data found for project: {}", projectId);
      return null;
    }

    Set<Integer> alreadyNotified = parseNotifiedThresholds(limit.getNotificationStatus());

    Long eventLimit = getEventLimit(limit);
    Long sessionLimit = getSessionLimit(limit);
    Integer eventsOverage = getEventOverage(limit);
    Integer sessionsOverage = getSessionOverage(limit);

    Integer eventsPercentage = null;
    Integer sessionsPercentage = null;

    if (eventLimit != null && eventLimit > 0) {
      eventsPercentage = calculatePercentage(usage.getEventsUsed(), eventLimit);
    }

    if (sessionLimit != null && sessionLimit > 0) {
      sessionsPercentage = calculatePercentage(usage.getSessionsUsed(), sessionLimit);
    }

    int maxOverage = Math.max(eventsOverage != null ? eventsOverage : 0,
        sessionsOverage != null ? sessionsOverage : 0);
    int overageLimit = 100 + maxOverage;
    List<Integer> thresholdsToCheck = new ArrayList<>(NOTIFICATION_THRESHOLDS);
    if (maxOverage > 0 && !thresholdsToCheck.contains(overageLimit)) {
      thresholdsToCheck.add(overageLimit);
    }

    Optional<UsageNotificationChosenTarget> chosenOpt = chooseNotificationTarget(
        thresholdsToCheck, alreadyNotified, eventsPercentage, sessionsPercentage);
    if (chosenOpt.isEmpty()) {
      return null;
    }
    UsageNotificationChosenTarget chosen = chosenOpt.get();

    UsageLimitTemplateResolution template = resolveUsageLimitTemplate(
        eventsPercentage, sessionsPercentage, overageLimit, maxOverage);

    UsageNotification notification = buildUsageLimitNotification(
        limit, usage, chosen, template, eventLimit, sessionLimit, eventsOverage, sessionsOverage);

    log.info("📊 Notification due: {} - {} at {}% using template {} (events: {}%, sessions: {}%)",
        projectId, chosen.notifyFor(), chosen.threshold(), template.templateName(),
        template.displayEventsPercentage(), template.displaySessionsPercentage());

    return notification;
  }

  // Picks events vs sessions by highest newly crossed threshold; empty if none due.
  private Optional<UsageNotificationChosenTarget> chooseNotificationTarget(
      List<Integer> thresholdsToCheck,
      Set<Integer> alreadyNotified,
      Integer eventsPercentage,
      Integer sessionsPercentage) {
    List<Integer> eventsCrossed = new ArrayList<>();
    List<Integer> sessionsCrossed = new ArrayList<>();

    if (eventsPercentage != null) {
      for (Integer threshold : thresholdsToCheck) {
        if (eventsPercentage >= threshold && !alreadyNotified.contains(threshold)) {
          eventsCrossed.add(threshold);
        }
      }
    }
    if (sessionsPercentage != null) {
      for (Integer threshold : thresholdsToCheck) {
        if (sessionsPercentage >= threshold && !alreadyNotified.contains(threshold)) {
          sessionsCrossed.add(threshold);
        }
      }
    }

    int eventsHighest = eventsCrossed.isEmpty() ? 0 : eventsCrossed.stream().max(Integer::compareTo).orElse(0);
    int sessionsHighest = sessionsCrossed.isEmpty() ? 0 : sessionsCrossed.stream().max(Integer::compareTo).orElse(0);

    if (eventsHighest >= sessionsHighest && eventsHighest > 0) {
      return Optional.of(new UsageNotificationChosenTarget(
          "events", eventsHighest, new ArrayList<>(eventsCrossed)));
    }
    if (sessionsHighest > 0) {
      return Optional.of(new UsageNotificationChosenTarget(
          "sessions", sessionsHighest, new ArrayList<>(sessionsCrossed)));
    }
    return Optional.empty();
  }

  // Selects notification template and capped display percentages from usage vs overage state.
  private UsageLimitTemplateResolution resolveUsageLimitTemplate(
      Integer eventsPercentage,
      Integer sessionsPercentage,
      int overageLimit,
      int maxOverage) {
    boolean eventsBlocked = eventsPercentage != null && eventsPercentage >= overageLimit;
    boolean sessionsBlocked = sessionsPercentage != null && sessionsPercentage >= overageLimit;
    boolean eventsAtLimit = eventsPercentage != null && eventsPercentage >= 100
        && eventsPercentage < overageLimit && maxOverage > 0;
    boolean sessionsAtLimit = sessionsPercentage != null && sessionsPercentage >= 100
        && sessionsPercentage < overageLimit && maxOverage > 0;

    int displayEventsPercentage = eventsPercentage != null ? eventsPercentage : 0;
    int displaySessionsPercentage = sessionsPercentage != null ? sessionsPercentage : 0;
    String templateName;

    if (eventsBlocked || sessionsBlocked) {
      templateName = NotificationConstants.Platform.EVENT_USAGE_LIMIT_BLOCKED;
      if (!eventsBlocked) {
        displayEventsPercentage = Math.min(displayEventsPercentage, 100);
      }
      if (!sessionsBlocked) {
        displaySessionsPercentage = Math.min(displaySessionsPercentage, 100);
      }
    } else if (eventsAtLimit || sessionsAtLimit) {
      templateName = NotificationConstants.Platform.EVENT_USAGE_LIMIT_REACHED;
      displayEventsPercentage = Math.min(displayEventsPercentage, 100);
      displaySessionsPercentage = Math.min(displaySessionsPercentage, 100);
    } else {
      templateName = NotificationConstants.Platform.EVENT_USAGE_LIMIT_THRESHOLD;
    }

    return new UsageLimitTemplateResolution(
        templateName,
        displayEventsPercentage,
        displaySessionsPercentage,
        eventsBlocked,
        sessionsBlocked,
        eventsAtLimit,
        sessionsAtLimit);
  }

  // Maps chosen threshold and template resolution into a UsageNotification (recipients unset).
  private UsageNotification buildUsageLimitNotification(
      ProjectUsageLimitInfo limit,
      UsageStats usage,
      UsageNotificationChosenTarget chosen,
      UsageLimitTemplateResolution tmpl,
      Long eventLimit,
      Long sessionLimit,
      Integer eventsOverage,
      Integer sessionsOverage) {
    String projectId = limit.getProjectId();
    String projectName = limit.getProjectName() != null ? limit.getProjectName() : projectId;
    long sessionsUsedVal = usage.getSessionsUsed() != null ? usage.getSessionsUsed() : 0;
    long eventsUsedVal = usage.getEventsUsed() != null ? usage.getEventsUsed() : 0;

    return UsageNotification.builder()
        .projectId(projectId)
        .projectName(projectName)
        .tenantId(limit.getTenantId())
        .threshold(chosen.threshold())
        .thresholdsToMark(chosen.thresholdsToMark())
        .notifyFor(chosen.notifyFor())
        .templateName(tmpl.templateName())
        .sessionsUsed(usage.getSessionsUsed())
        .sessionsLimit(sessionLimit)
        .sessionsPercentage(tmpl.displaySessionsPercentage())
        .sessionsPercentageDisplay(
            toPercentageDisplay(sessionsUsedVal, sessionLimit, tmpl.displaySessionsPercentage()))
        .sessionsOverage(sessionsOverage)
        .sessionsBlocked(tmpl.sessionsBlocked())
        .sessionsAtLimit(tmpl.sessionsAtLimit())
        .eventsUsed(usage.getEventsUsed())
        .eventsLimit(eventLimit)
        .eventsPercentage(tmpl.displayEventsPercentage())
        .eventsPercentageDisplay(
            toPercentageDisplay(eventsUsedVal, eventLimit, tmpl.displayEventsPercentage()))
        .eventsOverage(eventsOverage)
        .eventsBlocked(tmpl.eventsBlocked())
        .eventsAtLimit(tmpl.eventsAtLimit())
        .recipientEmails(null)
        .build();
  }

  /**
   * Resolves recipient emails per project (OpenFGA admins, then creator fallback) and builds the result.
   */
  private Single<UsageNotificationResult> attachRecipientsAndBuildResult(
      List<UsageNotification> notifications,
      List<ProjectUsageLimitInfo> limits,
      Instant endTime) {
    Set<String> projectIds = notifications.stream()
        .map(UsageNotification::getProjectId)
        .collect(Collectors.toSet());
    Map<String, ProjectUsageLimitInfo> limitByProject = limits.stream()
        .collect(Collectors.toMap(ProjectUsageLimitInfo::getProjectId, l -> l));

    return Flowable.fromIterable(projectIds)
        .flatMapSingle(projectId ->
            openFgaService.getProjectAdmins(projectId)
                .flatMap(adminIds -> {
                  if (adminIds == null || adminIds.isEmpty()) {
                    ProjectUsageLimitInfo limit = limitByProject.get(projectId);
                    String createdBy = limit != null ? limit.getCreatedBy() : null;
                    List<String> fallback = (createdBy != null && createdBy.contains("@"))
                        ? List.of(createdBy) : List.of();
                    return Single.just(new AbstractMap.SimpleEntry<>(projectId, fallback));
                  }
                  return userService.getUsersByIds(new ArrayList<>(adminIds))
                      .map(users -> users.stream()
                          .map(User::getEmail)
                          .filter(e -> e != null && e.contains("@"))
                          .distinct()
                          .toList())
                      .map(emails -> new AbstractMap.SimpleEntry<>(projectId, emails));
                }))
        .toList()
        .map(entries -> {
          Map<String, List<String>> projectToEmails = new HashMap<>();
          for (var e : entries) {
            projectToEmails.put(e.getKey(), e.getValue());
          }
          for (UsageNotification n : notifications) {
            n.setRecipientEmails(projectToEmails.getOrDefault(n.getProjectId(), List.of()));
          }
          return UsageNotificationResult.builder()
              .notifications(notifications)
              .totalProjectsChecked(limits.size())
              .notificationsDue(notifications.size())
              .checkedAt(endTime)
              .build();
        });
  }


  /**
   * Calculate percentage used.
   */
  private int calculatePercentage(long used, long limit) {
    if (limit == 0) {
      return 0;
    }
    return (int) ((used * 100) / limit);
  }

  /**
   * Returns display string for template: "&lt;1" when used&gt;0 but percentage rounds to 0,
   * otherwise the numeric percentage as string.
   */
  private String toPercentageDisplay(long used, Long limit, int displayPercentage) {
    if (used > 0 && limit != null && limit > 0 && displayPercentage == 0) {
      return "<1";
    }
    return String.valueOf(displayPercentage);
  }

  /**
   * Parse which thresholds have already been notified from notification status.
   */
  private Set<Integer> parseNotifiedThresholds(NotificationStatus status) {
    if (status == null || status.getThresholdsNotified() == null) {
      return Collections.emptySet();
    }
    
    Set<Integer> notified = new HashSet<>();
    JsonNode node = status.getThresholdsNotified();
    node.fieldNames().forEachRemaining(key -> {
      try {
        notified.add(Integer.parseInt(key));
      } catch (NumberFormatException e) {
        log.warn("Invalid threshold key: {}", key);
      }
    });
    
    return notified;
  }

  /**
   * Extract session limit from project usage limit info.
   */
  private Long getSessionLimit(ProjectUsageLimitInfo limit) {
    if (limit.getUsageLimits() == null) {
      return null;
    }
    UsageLimitValue sessionLimit =
        limit.getUsageLimits().get(UsageLimitParameter.MAX_USER_SESSIONS_PER_PROJECT.getKey());
    if (sessionLimit == null || sessionLimit.getValue() == null) {
      return null;
    }
    return sessionLimit.getValue().longValue();
  }

  /**
   * Extract event limit from project usage limit info.
   */
  private Long getEventLimit(ProjectUsageLimitInfo limit) {
    if (limit.getUsageLimits() == null) {
      return null;
    }
    UsageLimitValue eventLimit =
        limit.getUsageLimits().get(UsageLimitParameter.MAX_EVENTS_PER_PROJECT.getKey());
    if (eventLimit == null || eventLimit.getValue() == null) {
      return null;
    }
    return eventLimit.getValue().longValue();
  }

  /**
   * Extract session overage from project usage limit info.
   */
  private Integer getSessionOverage(ProjectUsageLimitInfo limit) {
    if (limit.getUsageLimits() == null) {
      return 0;
    }
    UsageLimitValue sessionLimit =
        limit.getUsageLimits().get(UsageLimitParameter.MAX_USER_SESSIONS_PER_PROJECT.getKey());
    if (sessionLimit == null || sessionLimit.getOverage() == null) {
      return 0;
    }
    return sessionLimit.getOverage();
  }

  /**
   * Extract event overage from project usage limit info.
   */
  private Integer getEventOverage(ProjectUsageLimitInfo limit) {
    if (limit.getUsageLimits() == null) {
      return 0;
    }
    UsageLimitValue eventLimit =
        limit.getUsageLimits().get(UsageLimitParameter.MAX_EVENTS_PER_PROJECT.getKey());
    if (eventLimit == null || eventLimit.getOverage() == null) {
      return 0;
    }
    return eventLimit.getOverage();
  }

  private record UsageNotificationChosenTarget(
      String notifyFor,
      int threshold,
      List<Integer> thresholdsToMark) {}

  private record UsageLimitTemplateResolution(
      String templateName,
      int displayEventsPercentage,
      int displaySessionsPercentage,
      boolean eventsBlocked,
      boolean sessionsBlocked,
      boolean eventsAtLimit,
      boolean sessionsAtLimit) {}

  @lombok.Data
  @lombok.Builder
  @lombok.NoArgsConstructor
  @lombok.AllArgsConstructor
  public static class NotificationStatusResponse {
    private String projectId;
    private String month;
    private com.fasterxml.jackson.databind.JsonNode thresholdsNotified;
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;
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

