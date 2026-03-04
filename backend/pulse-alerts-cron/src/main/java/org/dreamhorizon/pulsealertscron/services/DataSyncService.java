package org.dreamhorizon.pulsealertscron.services;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.client.PulseServerApiClient;
import org.dreamhorizon.pulsealertscron.dto.response.ApiKeysResponse;
import org.dreamhorizon.pulsealertscron.dto.response.ProjectUsageResult;
import org.dreamhorizon.pulsealertscron.dto.response.UsageLimitsApiResponse;
import org.dreamhorizon.pulsealertscron.dto.response.UsageStats;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DataSyncService {
  private final ClickhouseService clickhouseService;
  private final PulseServerApiClient apiClient;
  private final RedisService redisService;

  @Inject
  public DataSyncService(
      ClickhouseService clickhouseService,
      PulseServerApiClient apiClient,
      RedisService redisService
  ) {
    this.clickhouseService = clickhouseService;
    this.apiClient = apiClient;
    this.redisService = redisService;
  }

  public Completable processUsageLimits() {
    log.info("=== Starting Usage Limits Processing ===");
    long startTime = System.currentTimeMillis();
    
    return clickhouseService.getCurrentMonthUsage()
        .flatMap(clickhouseStats -> {
          log.info("✅ Got ClickHouse data for {} tenants", clickhouseStats.size());
          
          return apiClient.getActiveLimits()
              .map(apiResponse -> {
                log.info("✅ Got API limits for {} projects", apiResponse.getCount());
                
                List<ProjectUsageResult> results = new ArrayList<>();
                
                for (UsageLimitsApiResponse.ProjectLimit limit : apiResponse.getLimits()) {
                  String projectId = limit.getProjectId();
                  
                  // Find matching ClickHouse data by tenant
                  UsageStats chStats = clickhouseStats.stream()
                      .filter(s -> s.getTenant().equals(projectId))
                      .findFirst()
                      .orElse(UsageStats.builder()
                          .tenant(projectId)
                          .eventsUsed(0L)
                          .sessionsUsed(0L)
                          .build());
                  
                  // Get finalThreshold from API (pre-calculated hard limit)
                  UsageLimitsApiResponse.LimitMetric sessionLimit = limit.getUsageLimits()
                      .get("max_user_sessions_per_project");
                  UsageLimitsApiResponse.LimitMetric eventLimit = limit.getUsageLimits()
                      .get("max_events_per_project");
                  
                  Integer sessionThreshold = sessionLimit.getFinalThreshold();
                  Integer eventThreshold = eventLimit.getFinalThreshold();
                  
                  // Calculate remaining using hard limit
                  long sessionsUsed = chStats.getSessionsUsed();
                  long eventsUsed = chStats.getEventsUsed();
                  long sessionsRemaining = sessionThreshold - sessionsUsed;
                  long eventsRemaining = eventThreshold - eventsUsed;
                  
                  ProjectUsageResult result = ProjectUsageResult.builder()
                      .projectId(projectId)
                      .sessionFinalThreshold(sessionThreshold)
                      .sessionsUsed(sessionsUsed)
                      .sessionsRemaining(sessionsRemaining)
                      .eventFinalThreshold(eventThreshold)
                      .eventsUsed(eventsUsed)
                      .eventsRemaining(eventsRemaining)
                      .updatedAt(System.currentTimeMillis())
                      .build();
                  
                  results.add(result);
                  
                  log.info("📊 Project: {} | Sessions: {}/{} ({} remaining) | Events: {}/{} ({} remaining)",
                      projectId,
                      sessionsUsed, sessionThreshold, sessionsRemaining,
                      eventsUsed, eventThreshold, eventsRemaining);
                }
                
                return results;
              });
        })
        .flatMapCompletable(results -> {
          long duration = System.currentTimeMillis() - startTime;
          log.info("✅ Usage processing completed in {}ms for {} projects", 
              duration, results.size());
          
          // Log summary
          long totalSessionsUsed = results.stream()
              .mapToLong(ProjectUsageResult::getSessionsUsed)
              .sum();
          long totalEventsUsed = results.stream()
              .mapToLong(ProjectUsageResult::getEventsUsed)
              .sum();
          
          log.info("📈 Total across all projects: {} sessions, {} events", 
              totalSessionsUsed, totalEventsUsed);
          
          // Save to Redis
          return redisService.saveUsageLimits(results);
        })
        .doOnError(error -> {
          long duration = System.currentTimeMillis() - startTime;
          log.error("❌ Usage processing failed after {}ms", duration, error);
        });
  }

  public Completable syncApiKeys() {
    log.info("=== Starting API Keys Sync ===");
    long startTime = System.currentTimeMillis();
    
    return apiClient.getValidApiKeys()
        .flatMapCompletable(response -> {
          log.info("✅ Got {} valid API keys from API", response.getCount());
          
          // Log each API key mapping
          for (ApiKeysResponse.ApiKey apiKey : response.getApiKeys()) {
            log.info("🔑 API Key: {} → Project: {} (Active: {})", 
                apiKey.getApiKey(), 
                apiKey.getProjectId(), 
                apiKey.getIsActive());
          }
          
          long duration = System.currentTimeMillis() - startTime;
          log.info("✅ API keys sync completed in {}ms for {} keys", 
              duration, response.getCount());
          
          // Save to Redis
          return redisService.saveApiKeyMappings(response.getApiKeys());
        })
        .doOnError(error -> {
          long duration = System.currentTimeMillis() - startTime;
          log.error("❌ API keys sync failed after {}ms", duration, error);
        });
  }
}
