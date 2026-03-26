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
   * Service for submitting Spark jobs.
   */
  private final SparkJobService sparkJobService;

  @Override
  public Single<Boolean> triggerFunnelsBatch() {
    return submitSparkJob(
        "funnels-daily-batch",
        "local:///usr/lib/spark/examples/jars/spark-examples.jar",
        "org.dreamhorizon.spark.FunnelsDailyBatch"
    );
  }

  @Override
  public Single<Boolean> triggerJourneysBatch() {
    return submitSparkJob(
        "journeys-daily-batch",
        "local:///usr/lib/spark/examples/jars/spark-examples.jar",
        "org.dreamhorizon.spark.JourneysDailyBatch"
    );
  }

  @Override
  public Single<Boolean> triggerEventsBatch() {
    return submitSparkJob(
        "events-incremental-batch",
        "local:///usr/lib/spark/examples/jars/spark-examples.jar",
        "org.dreamhorizon.spark.EventsIncrementalBatch"
    );
  }

  private Single<Boolean> submitSparkJob(
      final String jobName,
      final String entryPoint,
      final String mainClass) {

    log.info("Submitting Spark job: {}", jobName);

    SparkJobRequest request = SparkJobRequest.builder()
        .jobName(jobName)
        .mainClass(mainClass)
        .entryPoint(entryPoint)
        .arguments(List.of())
        .timeoutMinutes(DEFAULT_TIMEOUT_MINUTES)
        .build();

    return sparkJobService.submitJob(request)
        .map(response -> {
          log.info("Successfully submitted job: {}. JobRunId: {}",
              jobName, response.getJobRunId());
          return true;
        })
        .onErrorReturn(error -> {
          log.error("Failed to submit job: {}", jobName, error);
          return false;
        })
        .subscribeOn(Schedulers.io());
  }
}
