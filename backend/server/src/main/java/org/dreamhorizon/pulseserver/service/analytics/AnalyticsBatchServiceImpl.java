package org.dreamhorizon.pulseserver.service.analytics;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.spark.SparkJobService;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobRequest;

/**
 * Implementation of {@link AnalyticsBatchService}.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public final class AnalyticsBatchServiceImpl implements AnalyticsBatchService {

  /**
   * Default timeout for batch jobs.
   */
  private static final long DEFAULT_TIMEOUT_MINUTES = 60L;

  /**
   * Spark configuration.
   */
  private final org.dreamhorizon.pulseserver.config.SparkConfig sparkConfig;

  /**
   * Service for submitting Spark jobs.
   */
  private final SparkJobService sparkJobService;

  /**
   * DAO for Spark job records.
   */
  private final org.dreamhorizon.pulseserver.dao.spark.SparkJobDao sparkJobDao;

  @Override
  public Single<Boolean> triggerFunnelsBatch() {
    return submitSparkJob(
        "FUNNELS_DAILY",
        "funnels-daily-batch",
        sparkConfig.getJobJarPath(),
        sparkConfig.getFunnelsMainClass()
    );
  }

  @Override
  public Single<Boolean> triggerJourneysBatch() {
    return submitSparkJob(
        "JOURNEYS_DAILY",
        "journeys-daily-batch",
        sparkConfig.getJobJarPath(),
        sparkConfig.getJourneysMainClass()
    );
  }

  @Override
  public Single<Boolean> triggerEventsBatch() {
    return submitSparkJob(
        "EVENTS_INCREMENTAL",
        "events-incremental-batch",
        sparkConfig.getJobJarPath(),
        sparkConfig.getEventsMainClass(),
        null,
        List.of()
    );
  }

  @Override
  public Single<Boolean> triggerFunnelOnSaveJob(final Long funnelId) {
    return submitSparkJob(
        "FUNNEL",
        "funnel-onsave-" + funnelId,
        sparkConfig.getJobJarPath(),
        sparkConfig.getFunnelsMainClass(),
        funnelId,
        List.of("--funnelId", String.valueOf(funnelId))
    );
  }

  @Override
  public Single<Boolean> triggerJourneyOnSaveJob(final Long journeyId) {
    return submitSparkJob(
        "JOURNEY",
        "journey-onsave-" + journeyId,
        sparkConfig.getJobJarPath(),
        sparkConfig.getJourneysMainClass(),
        journeyId,
        List.of("--journeyId", String.valueOf(journeyId))
    );
  }

  private Single<Boolean> submitSparkJob(
      final String jobType,
      final String jobName,
      final String entryPoint,
      final String mainClass) {
    return submitSparkJob(jobType, jobName, entryPoint, mainClass, null, List.of());
  }

  private Single<Boolean> submitSparkJob(
      final String jobType,
      final String jobName,
      final String entryPoint,
      final String mainClass,
      final Long referenceId,
      final List<String> arguments) {

    log.info("Submitting Spark job: {}", jobName);

    SparkJobRequest request = SparkJobRequest.builder()
        .jobName(jobName)
        .mainClass(mainClass)
        .entryPoint(entryPoint)
        .arguments(arguments)
        .timeoutMinutes(DEFAULT_TIMEOUT_MINUTES)
        .build();

    return sparkJobDao.insertJob(jobType, referenceId, null, "PENDING")
        .flatMap(dbId -> sparkJobService.submitJob(request)
            .flatMap(response -> {
              log.info("Successfully submitted job: {}. JobRunId: {}",
                  jobName, response.getJobRunId());
              return sparkJobDao.updateJobIdAndStatus(
                      dbId, response.getJobRunId(), "SUBMITTED")
                  .map(updated -> true);
            })
            .onErrorResumeNext(error -> {
              log.error("Failed to submit job: {}", jobName, error);
              return sparkJobDao.updateJobStatus(
                      dbId, "FAILED", error.getMessage(), null, null)
                  .map(updated -> false);
            }))
        .subscribeOn(Schedulers.io());
  }
}
