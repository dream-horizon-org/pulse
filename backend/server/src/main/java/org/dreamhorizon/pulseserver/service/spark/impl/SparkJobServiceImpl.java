package org.dreamhorizon.pulseserver.service.spark.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.emr.EmrServerlessJobClient;
import org.dreamhorizon.pulseserver.service.spark.SparkJobService;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobRequest;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobResponse;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunResponse;
import software.amazon.awssdk.services.emrserverless.model.JobDriver;
import software.amazon.awssdk.services.emrserverless.model.SparkSubmit;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunResponse;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Singleton
public class SparkJobServiceImpl implements SparkJobService {
    
    private final EmrServerlessJobClient emrClient;
    
    @Inject
    public SparkJobServiceImpl(EmrServerlessJobClient emrClient) {
        this.emrClient = emrClient;
    }
    
    @Override
    public Single<SparkJobResponse> submitJob(SparkJobRequest request) {
        return Single.fromCallable(() -> {
            log.info("[submitJob] Submitting Spark job: {} with main class: {}", 
                    request.getJobName(), request.getMainClass());
            
            // 1. Build EMR request with auto-generated clientToken
            StartJobRunRequest emrRequest = emrClient.startJobRunRequestBuilder()
                .clientToken(UUID.randomUUID().toString())  // Auto-generated idempotency token
                .name(request.getJobName())
                .executionTimeoutMinutes(request.getTimeoutMinutes())
                .jobDriver(JobDriver.builder()
                    .sparkSubmit(SparkSubmit.builder()
                        .entryPoint(request.getMainClass())
                        .entryPointArguments(request.getArguments())
                        .sparkSubmitParameters(request.getSparkConfig())
                        .build())
                    .build())
                .tags(request.getTags())
                .mode(request.getMode())
                .build();
                
            // 2. Submit to EMR
            StartJobRunResponse emrResponse = emrClient.startJobRun(emrRequest);
            
            log.info("[submitJob] Successfully submitted job: {} with EMR job run ID: {}", 
                    request.getJobName(), emrResponse.jobRunId());
            
            // 3. Return EMR response with additional metadata
            return SparkJobResponse.builder()
                .applicationId(emrResponse.applicationId())
                .jobRunId(emrResponse.jobRunId())
                .arn(emrResponse.arn())
                .jobName(request.getJobName())
                .mainClass(request.getMainClass())
                .submittedAt(LocalDateTime.now())
                .build();
        });
    }
    
    @Override
    public Single<GetJobRunResponse> getJobStatus(String jobRunId) {
        return Single.fromCallable(() -> {
            log.info("[getJobStatus] Getting status for job run ID: {}", jobRunId);
            
            GetJobRunRequest request = GetJobRunRequest.builder()
                .applicationId(emrClient.getApplicationId())
                .jobRunId(jobRunId)
                .build();
                
            GetJobRunResponse response = emrClient.getJobRun(request);
            
            log.info("[getJobStatus] Job run ID: {} has status: {}", 
                    jobRunId, response.jobRun().state());
            
            return response;
        });
    }
}