package org.dreamhorizon.pulseserver.service.cron;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.constant.CronJobType;
import org.dreamhorizon.pulseserver.dao.cronjobhistory.CronJobHistoryDao;
import org.dreamhorizon.pulseserver.resources.internal.models.CronRedisSyncJobAcceptedRestResponse;
import org.dreamhorizon.pulseserver.service.kong.KongApiKeyRedisSyncService;
import org.dreamhorizon.pulseserver.service.kong.KongUsageCreditsRedisSyncService;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CronRedisMaterializationJobService {

  static final String STALE_IN_PROGRESS_RECLAIMED = "stale_in_progress_reclaimed";
  private static final Duration IN_PROGRESS_STALE_AFTER = Duration.ofMinutes(2);

  private final CronJobHistoryDao cronJobHistoryDao;
  private final KongApiKeyRedisSyncService kongApiKeyRedisSyncService;
  private final KongUsageCreditsRedisSyncService kongUsageCreditsRedisSyncService;

  public Single<CronRedisSyncJobAcceptedRestResponse> acceptApiKeysSyncToRedis() {
    return enqueueAndMaybeRun(
        CronJobType.API_KEYS_TO_REDIS,
        this::runApiKeysJob);
  }

  public Single<CronRedisSyncJobAcceptedRestResponse> acceptUsageCreditsSyncToRedis() {
    return enqueueAndMaybeRun(
        CronJobType.USAGE_CREDITS_TO_REDIS,
        this::runUsageCreditsJob);
  }

  private Single<CronRedisSyncJobAcceptedRestResponse> enqueueAndMaybeRun(
      String jobType,
      JobRunner runner) {
    Instant staleBefore = Instant.now().minus(IN_PROGRESS_STALE_AFTER);
    return cronJobHistoryDao
        .enqueueOrDeduplicate(jobType, staleBefore, STALE_IN_PROGRESS_RECLAIMED)
        .map(outcome -> {
          if (!outcome.isDeduplicated()) {
            runner.run(outcome.getJobId());
          }
          return CronRedisSyncJobAcceptedRestResponse.builder()
              .jobId(outcome.getJobId())
              .deduplicated(outcome.isDeduplicated())
              .jobType(jobType)
              .build();
        });
  }

  private void runApiKeysJob(long jobId) {
    kongApiKeyRedisSyncService
        .syncValidApiKeysToRedis()
        .flatMapCompletable(n -> cronJobHistoryDao.markCompleted(jobId))
        .subscribe(
            () -> log.info("cron_jobs_history job {} ({}) completed", jobId, CronJobType.API_KEYS_TO_REDIS),
            err -> {
              log.error("cron_jobs_history job {} ({}) failed", jobId, CronJobType.API_KEYS_TO_REDIS, err);
              cronJobHistoryDao
                  .markFailed(jobId, err.getMessage() != null ? err.getMessage() : err.getClass().getName())
                  .subscribe(
                      () -> { },
                      markErr -> log.error("Failed to mark job {} FAILED after error", jobId, markErr));
            });
  }

  private void runUsageCreditsJob(long jobId) {
    kongUsageCreditsRedisSyncService
        .syncUsageCreditsToRedis()
        .flatMapCompletable(n -> cronJobHistoryDao.markCompleted(jobId))
        .subscribe(
            () -> log.info("cron_jobs_history job {} ({}) completed", jobId, CronJobType.USAGE_CREDITS_TO_REDIS),
            err -> {
              log.error("cron_jobs_history job {} ({}) failed", jobId, CronJobType.USAGE_CREDITS_TO_REDIS, err);
              cronJobHistoryDao
                  .markFailed(jobId, err.getMessage() != null ? err.getMessage() : err.getClass().getName())
                  .subscribe(
                      () -> { },
                      markErr -> log.error("Failed to mark job {} FAILED after error", jobId, markErr));
            });
  }

  @FunctionalInterface
  private interface JobRunner {
    void run(long jobId);
  }
}
