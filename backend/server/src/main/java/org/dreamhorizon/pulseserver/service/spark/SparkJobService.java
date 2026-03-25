package org.dreamhorizon.pulseserver.service.spark;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobRequest;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobResponse;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunResponse;

/**
 * Service for submitting and managing Spark jobs via EMR Serverless.
 * Provides a Spark-focused abstraction that hides EMR configuration details from callers.
 */
public interface SparkJobService {
    
    /**
     * Submit a Spark job to EMR Serverless.
     * 
     * @param request Spark job configuration
     * @return EMR job submission response with additional metadata
     */
    Single<SparkJobResponse> submitJob(SparkJobRequest request);
    
    /**
     * Get the status of a running job.
     * 
     * @param jobRunId EMR job run ID
     * @return EMR job run details directly from AWS SDK
     */
    Single<GetJobRunResponse> getJobStatus(String jobRunId);
}