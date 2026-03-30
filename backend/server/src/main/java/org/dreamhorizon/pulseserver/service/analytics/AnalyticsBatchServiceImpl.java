package org.dreamhorizon.pulseserver.service.analytics;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.spark.SparkJobStatus;
import org.dreamhorizon.pulseserver.dao.spark.SparkJobType;
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
    return triggerDailyBatchJob(
        SparkJobType.FUNNELS_DAILY,
        sparkConfig.getFunnelsMainClass()
    );
  }

  @Override
  public Single<Boolean> triggerJourneysBatch() {
    return triggerDailyBatchJob(
        SparkJobType.JOURNEYS_DAILY,
        sparkConfig.getJourneysMainClass()
    );
  }

  @Override
  public Single<Boolean> triggerEventsBatch() {
    return triggerDailyBatchJob(
        SparkJobType.EVENTS_INCREMENTAL,
        sparkConfig.getEventsMainClass()
    );
  }

  private Single<Boolean> triggerDailyBatchJob(
      final SparkJobType jobType,
      final String mainClass) {
    return sparkJobDao.getLatestJobByType(jobType)
        .flatMapSingle(latestJob -> {
          java.time.LocalDate today = java.time.LocalDate.now(
              java.time.ZoneOffset.UTC);
          java.time.LocalDate latestJobDate = latestJob.getCreatedAt()
              .toLocalDate();
          if (latestJobDate.isEqual(today)) {
            log.info(
                "Batch job {} has already been triggered today. Skipping.",
                jobType);
            return Single.just(false);
          }
          return submitSparkJob(
              jobType, jobType.getJobNamePrefix(), sparkConfig.getJobJarPath(),
              mainClass, null, List.of());
        })
        .switchIfEmpty(submitSparkJob(
            jobType, jobType.getJobNamePrefix(), sparkConfig.getJobJarPath(),
            mainClass, null, List.of()));
  }

  @Override
  public Single<Boolean> triggerFunnelOnSaveJob(final Long funnelId) {
    return submitSparkJob(
        SparkJobType.FUNNEL,
        SparkJobType.FUNNEL.getJobNamePrefix() + funnelId,
        sparkConfig.getJobJarPath(),
        sparkConfig.getFunnelsMainClass(),
        funnelId,
        List.of("--funnelId", String.valueOf(funnelId))
    );
  }

  @Override
  public Single<Boolean> triggerJourneyOnSaveJob(final Long journeyId) {
    return submitSparkJob(
        SparkJobType.JOURNEY,
        SparkJobType.JOURNEY.getJobNamePrefix() + journeyId,
        sparkConfig.getJobJarPath(),
        sparkConfig.getJourneysMainClass(),
        journeyId,
        List.of("--journeyId", String.valueOf(journeyId))
    );
  }

  private Single<Boolean> submitSparkJob(
      final SparkJobType jobType,
      final String jobName,
      final String entryPoint,
      final String mainClass,
      final Long referenceId,
      final List<String> arguments) {

    log.info("Submitting Spark job: {}", jobName);

    return sparkJobDao.insertJob(
            jobType, referenceId, null, SparkJobStatus.PENDING)
        .flatMap(dbId -> {
          // Append jobType and jobId to arguments
          java.util.ArrayList<String> fullArgs = new java.util.ArrayList<>(
              arguments);
          fullArgs.add("--jobType");
          fullArgs.add(jobType.name());
          fullArgs.add("--jobId");
          fullArgs.add(String.valueOf(dbId));

          SparkJobRequest request = SparkJobRequest.builder()
              .jobName(jobName)
              .mainClass(mainClass)
              .entryPoint(entryPoint)
              .arguments(fullArgs)
              .timeoutMinutes(DEFAULT_TIMEOUT_MINUTES)
              .build();

          return sparkJobService.submitJob(request)
              .flatMap(response -> {
                log.info("Successfully submitted job: {}. JobRunId: {}",
                    jobName, response.getJobRunId());
                return sparkJobDao.updateJobIdAndStatus(
                        dbId, response.getJobRunId(), SparkJobStatus.SUBMITTED)
                    .map(updated -> true);
              })
              .onErrorResumeNext(error -> {
                log.error("Failed to submit job: {}", jobName, error);
                return sparkJobDao.updateJobStatus(
                        dbId, SparkJobStatus.FAILED, error.getMessage(), null,
                        null)
                    .map(updated -> false);
              });
        });
  }
}
