package org.dreamhorizon.pulseserver.service.spark;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobRequest;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobResponse;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunResponse;

public interface SparkJobService {
    
    Single<SparkJobResponse> submitJob(SparkJobRequest request);
    
    Single<GetJobRunResponse> getJobStatus(String jobRunId);
}