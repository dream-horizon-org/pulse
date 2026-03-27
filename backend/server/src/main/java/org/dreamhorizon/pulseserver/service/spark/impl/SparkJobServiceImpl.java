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

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class SparkJobServiceImpl implements SparkJobService {

    /**
     * Detects the {@code --class} spark-submit flag without matching {@code --classpath}.
     */
    static final Pattern SPARK_SUBMIT_CLASS_FLAG =
        Pattern.compile("(^|\\s)--class(\\s|$|=)", Pattern.CASE_INSENSITIVE);

    private final EmrServerlessJobClient emrClient;

    @Inject
    public SparkJobServiceImpl(EmrServerlessJobClient emrClient) {
        this.emrClient = emrClient;
    }

    @Override
    public Single<SparkJobResponse> submitJob(SparkJobRequest request) {
        return Single.fromCallable(() -> {
            if (request.getEntryPoint() == null || request.getEntryPoint().isBlank()) {
                throw new IllegalArgumentException("entryPoint is required (e.g. S3 URI of the main JAR or script)");
            }
            if (request.getSparkSubmitParameters() != null
                && SPARK_SUBMIT_CLASS_FLAG.matcher(request.getSparkSubmitParameters()).find()) {
                throw new IllegalArgumentException(
                    "sparkSubmitParameters must not contain --class; use the mainClass field instead");
            }

            String mergedSparkSubmitParams =
                mergeSparkSubmitParameters(request.getMainClass(), request.getSparkSubmitParameters());

            log.info(
                "[submitJob] Submitting Spark job: {} entryPoint={} mainClass={}",
                request.getJobName(),
                request.getEntryPoint(),
                request.getMainClass());

            SparkSubmit.Builder sparkBuilder =
                SparkSubmit.builder()
                    .entryPoint(request.getEntryPoint().trim())
                    .entryPointArguments(request.getArguments());
            if (mergedSparkSubmitParams != null) {
                sparkBuilder.sparkSubmitParameters(mergedSparkSubmitParams);
            }

            StartJobRunRequest emrRequest =
                emrClient
                    .startJobRunRequestBuilder()
                    .clientToken(UUID.randomUUID().toString())
                    .name(request.getJobName())
                    .executionTimeoutMinutes(request.getTimeoutMinutes())
                    .jobDriver(JobDriver.builder().sparkSubmit(sparkBuilder.build()).build())
                    .tags(request.getTags())
                    .build();

            StartJobRunResponse emrResponse = emrClient.startJobRun(emrRequest);

            log.info(
                "[submitJob] Successfully submitted job: {} with EMR job run ID: {}",
                request.getJobName(),
                emrResponse.jobRunId());

            return SparkJobResponse.builder()
                .applicationId(emrResponse.applicationId())
                .jobRunId(emrResponse.jobRunId())
                .arn(emrResponse.arn())
                .jobName(request.getJobName())
                .entryPoint(request.getEntryPoint().trim())
                .mainClass(request.getMainClass())
                .submittedAt(Instant.now().toString())
                .build();
        });
    }

    static String mergeSparkSubmitParameters(String mainClass, String sparkSubmitParameters) {
        StringBuilder sb = new StringBuilder();
        if (mainClass != null && !mainClass.isBlank()) {
            sb.append("--class ").append(mainClass.trim());
        }
        if (sparkSubmitParameters != null && !sparkSubmitParameters.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(sparkSubmitParameters.trim());
        }
        String merged = sb.toString();
        return merged.isEmpty() ? null : merged;
    }

    @Override
    public Single<GetJobRunResponse> getJobStatus(String jobRunId) {
        return Single.fromCallable(() -> {
            log.info("[getJobStatus] Getting status for job run ID: {}", jobRunId);

            GetJobRunRequest request =
                GetJobRunRequest.builder()
                    .applicationId(emrClient.getApplicationId())
                    .jobRunId(jobRunId)
                    .build();

            GetJobRunResponse response = emrClient.getJobRun(request);

            log.info(
                "[getJobStatus] Job run ID: {} has status: {}",
                jobRunId,
                response.jobRun().state());

            return response;
        });
    }
}
