package org.dreamhorizon.pulseserver.service.kong;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageStats;

/**
 * Materializes per-project remaining session/event credits into Redis for Kong
 * (same keys as legacy pulse-alerts-cron {@code RedisService.saveUsageLimits}).
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class KongUsageCreditsRedisSyncService {

  private static final String MAX_USER_SESSIONS = "max_user_sessions_per_project";
  private static final String MAX_EVENTS = "max_events_per_project";

  private final ClickhouseQueryService clickhouseQueryService;
  private final UsageLimitService usageLimitService;
  private final Vertx vertx;
  private final ApplicationConfig applicationConfig;

  private volatile Redis redisClient;
  private volatile RedisAPI redisApi;

  /**
   * Loads current-month usage from ClickHouse, merges with active MySQL limits, writes
   * {@code project:{id}:credit} hashes in Redis.
   *
   * @return number of projects written
   */
  public Single<Integer> syncUsageCreditsToRedis() {
    if (!isRedisConfigured()) {
      return Single.error(new IllegalStateException(
          "Redis is not configured (redisHost / redisPort). Cannot sync usage credits to Redis."));
    }
    ensureRedisClient();

    return Single.zip(
        clickhouseQueryService.getCurrentMonthUsage(),
        usageLimitService.getAllActiveLimits().toList(),
        this::buildProjectCredits)
        .flatMap(credits -> saveCreditsToRedis(credits).toSingleDefault(credits.size()));
  }

  private boolean isRedisConfigured() {
    String host = applicationConfig.getRedisHost();
    Integer port = applicationConfig.getRedisPort();
    return host != null && !host.trim().isEmpty() && port != null;
  }

  private synchronized void ensureRedisClient() {
    if (redisClient != null) {
      return;
    }
    String host = applicationConfig.getRedisHost().trim();
    int port = applicationConfig.getRedisPort();
    RedisOptions options = new RedisOptions()
        .setConnectionString("redis://" + host + ":" + port)
        .setMaxPoolSize(32)
        .setMaxPoolWaiting(128);
    redisClient = Redis.createClient(vertx, options);
    redisApi = RedisAPI.api(redisClient);
    log.info("KongUsageCreditsRedisSyncService: Redis client initialized for {}:{}", host, port);
  }

  private List<ProjectCredit> buildProjectCredits(
      Map<String, UsageStats> chByProject,
      List<ProjectUsageLimitInfo> limits) {
    List<ProjectCredit> credits = new ArrayList<>();
    for (ProjectUsageLimitInfo limit : limits) {
      String projectId = limit.getProjectId();
      UsageStats ch = chByProject != null ? chByProject.get(projectId) : null;
      long sessionsUsed = ch != null && ch.getSessionsUsed() != null ? ch.getSessionsUsed() : 0L;
      long eventsUsed = ch != null && ch.getEventsUsed() != null ? ch.getEventsUsed() : 0L;

      Map<String, UsageLimitValue> usageLimits = limit.getUsageLimits();
      UsageLimitValue sessionLimit = usageLimits != null ? usageLimits.get(MAX_USER_SESSIONS) : null;
      UsageLimitValue eventLimit = usageLimits != null ? usageLimits.get(MAX_EVENTS) : null;

      long sessionThreshold = thresholdOrZero(sessionLimit);
      long eventThreshold = thresholdOrZero(eventLimit);

      long sessionsRemaining = sessionThreshold - sessionsUsed;
      long eventsRemaining = eventThreshold - eventsUsed;

      credits.add(new ProjectCredit(projectId, sessionsRemaining, eventsRemaining));

      log.debug(
          "Project {} | sessions {}/{} ({} remaining) | events {}/{} ({} remaining)",
          projectId,
          sessionsUsed,
          sessionThreshold,
          sessionsRemaining,
          eventsUsed,
          eventThreshold,
          eventsRemaining);
    }
    return credits;
  }

  private static long thresholdOrZero(UsageLimitValue metric) {
    if (metric == null || metric.getFinalThreshold() == null) {
      return 0L;
    }
    return metric.getFinalThreshold();
  }

  private Completable saveCreditsToRedis(List<ProjectCredit> credits) {
    if (credits.isEmpty()) {
      log.info("No active usage limits; skipping Redis credit writes");
      return Completable.complete();
    }
    RedisAPI api = this.redisApi;
    List<Completable> writes = new ArrayList<>();
    for (ProjectCredit c : credits) {
      String key = "project:" + c.projectId() + ":credit";
      List<String> args = List.of(
          key,
          "session_credit",
          String.valueOf(c.sessionsRemaining()),
          "event_credit",
          String.valueOf(c.eventsRemaining()));
      writes.add(Completable.create(emitter -> api.hset(args)
          .onSuccess(v -> emitter.onComplete())
          .onFailure(emitter::onError)));
    }
    log.info("Writing usage credits to Redis for {} projects", credits.size());
    return Completable.merge(writes)
        .doOnComplete(() -> log.info("Saved usage credits for {} projects in Redis", credits.size()));
  }

  private record ProjectCredit(String projectId, long sessionsRemaining, long eventsRemaining) {}
}
