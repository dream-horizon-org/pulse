package org.dreamhorizon.pulseserver.service.spark;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobRequest;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobResponse;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunResponse;

/**
 * Service for submitting and monitoring Spark jobs.
 */
public interface SparkJobService {

  /**
   * Submits a new Spark job.
   *
   * @param request the job request
   * @return the job response
   */
  Single<SparkJobResponse> submitJob(SparkJobRequest request);

  /**
   * Gets the status of a Spark job.
   *
   * @param jobRunId the EMR job run ID
   * @return the job status response
   */
  Single<GetJobRunResponse> getJobStatus(String jobRunId);
}
