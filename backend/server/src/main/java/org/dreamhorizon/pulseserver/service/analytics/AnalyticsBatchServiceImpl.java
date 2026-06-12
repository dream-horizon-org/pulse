package org.dreamhorizon.pulseserver.service.analytics;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobDao;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobStatus;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType;
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
   * DAO for analytics job records ({@code analytics_jobs}).
   */
  private final AnalyticsJobDao analyticsJobDao;

  @Override
  public Single<Boolean> triggerFunnelsBatch() {
    return triggerDailyBatchJob(
      AnalyticsJobType.FUNNELS_DAILY,
      sparkConfig.getFunnelsMainClass()
    );
  }

  @Override
  public Single<Boolean> triggerJourneysBatch() {
    return triggerDailyBatchJob(
      AnalyticsJobType.JOURNEYS_DAILY,
      sparkConfig.getJourneysMainClass()
    );
  }

  @Override
  public Single<Boolean> triggerEventsBatch() {
    return triggerDailyBatchJob(
      AnalyticsJobType.EVENTS_INCREMENTAL,
      sparkConfig.getEventsMainClass()
    );
  }

  private Single<Boolean> triggerDailyBatchJob(
    final AnalyticsJobType jobType,
    final String mainClass) {
    return analyticsJobDao.getLatestJobByType(jobType)
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
      .switchIfEmpty(Single.defer(() -> submitSparkJob(
        jobType, jobType.getJobNamePrefix(), sparkConfig.getJobJarPath(),
        mainClass, null, List.of())));
  }

  @Override
  public Single<Boolean> triggerFunnelOnSaveJob(final Long funnelId) {
    return submitSparkJob(
      AnalyticsJobType.FUNNEL,
      AnalyticsJobType.FUNNEL.getJobNamePrefix() + funnelId,
      sparkConfig.getJobJarPath(),
      sparkConfig.getFunnelsMainClass(),
      funnelId,
      List.of("--reference_id", String.valueOf(funnelId))
    );
  }

  @Override
  public Single<Boolean> triggerJourneyOnSaveJob(final Long journeyId) {
    return submitSparkJob(
      AnalyticsJobType.JOURNEY,
      AnalyticsJobType.JOURNEY.getJobNamePrefix() + journeyId,
      sparkConfig.getJobJarPath(),
      sparkConfig.getJourneysMainClass(),
      journeyId,
      List.of("--reference_id", String.valueOf(journeyId))
    );
  }

  private Single<Boolean> submitSparkJob(
    final AnalyticsJobType jobType,
    final String jobName,
    final String entryPoint,
    final String mainClass,
    final Long referenceId,
    final List<String> arguments) {

    log.info("Submitting Spark job: {}", jobName);

    return analyticsJobDao.insertJob(
        jobType, referenceId, null, AnalyticsJobStatus.PENDING)
      .flatMap(dbId -> {
        // Append jobType and jobId to arguments
        java.util.ArrayList<String> fullArgs = new java.util.ArrayList<>(
          arguments);
        fullArgs.add("--job_type");
        fullArgs.add(jobType.name());
        fullArgs.add("--spark_job_id");
        fullArgs.add(String.valueOf(dbId));

        fullArgs.add("--s3_bucket_prefix");
        fullArgs.add("pulse-otel-ingestion");

        fullArgs.add("--aws_region");
        fullArgs.add("ap-south-1");

        fullArgs.add("--secrets_name");
        fullArgs.add("prod/pulseserver/appenv");


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
            return analyticsJobDao.updateJobIdAndStatus(
                dbId, response.getJobRunId(), AnalyticsJobStatus.SUBMITTED)
              .map(updated -> true);
          })
          .onErrorResumeNext(error -> {
            log.error("Failed to submit job: {}", jobName, error);
            return analyticsJobDao.updateJobStatus(
                dbId, AnalyticsJobStatus.FAILED, error.getMessage(), null,
                null)
              .map(updated -> false);
          });
      });
  }
}
