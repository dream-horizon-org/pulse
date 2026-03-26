package org.dreamhorizon.pulseserver.service.analytics;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.emr.EmrServerlessJobClient;
import software.amazon.awssdk.services.emrserverless.model.JobDriver;
import software.amazon.awssdk.services.emrserverless.model.SparkSubmit;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunRequest;

/**
 * Implementation of {@link AnalyticsBatchService}.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public final class AnalyticsBatchServiceImpl implements AnalyticsBatchService {

  /**
   * Client for submitting EMR Serverless jobs.
   */
  private final EmrServerlessJobClient emrClient;

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
    return Single.fromCallable(() -> {
      if (!emrClient.isEnabled()) {
        log.warn("EMR Serverless is disabled. Skipping job: {}", jobName);
        return false;
      }

      log.info("Submitting EMR Serverless job: {}", jobName);

      SparkSubmit sparkSubmit = SparkSubmit.builder()
          .entryPoint(entryPoint)
          .sparkSubmitParameters("--class " + mainClass)
          .build();

      JobDriver jobDriver = JobDriver.builder()
          .sparkSubmit(sparkSubmit)
          .build();

      StartJobRunRequest request = emrClient.startJobRunRequestBuilder()
          .name(jobName)
          .jobDriver(jobDriver)
          .build();

      var response = emrClient.startJobRun(request);
      log.info("Successfully submitted job: {}. JobRunId: {}",
          jobName, response.jobRunId());
      return true;
    }).subscribeOn(Schedulers.io());
  }
}
