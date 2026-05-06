package org.dreamhorizon.pulseserver.service.spark.impl;

import io.reactivex.rxjava3.observers.TestObserver;
import org.dreamhorizon.pulseserver.client.emr.EmrServerlessJobClient;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobRequest;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunResponse;
import software.amazon.awssdk.services.emrserverless.model.JobDriver;
import software.amazon.awssdk.services.emrserverless.model.JobRun;
import software.amazon.awssdk.services.emrserverless.model.JobRunState;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SparkJobServiceImplTest {

    private static final String ENTRY_JAR = "s3://artifacts-bucket/jobs/app.jar";

    @Mock
    private EmrServerlessJobClient emrClient;

    @Mock
    private StartJobRunRequest.Builder requestBuilder;

    private SparkJobServiceImpl sparkJobService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sparkJobService = new SparkJobServiceImpl(emrClient);
    }

    @Test
    void mergeSparkSubmitParameters_classAndExtra() {
        assertEquals(
            "--class com.example.Main --conf spark.sql.adaptive.enabled=true",
            SparkJobServiceImpl.mergeSparkSubmitParameters(
                "com.example.Main", "--conf spark.sql.adaptive.enabled=true"));
    }

    @Test
    void mergeSparkSubmitParameters_classOnly() {
        assertEquals("--class com.example.Main", SparkJobServiceImpl.mergeSparkSubmitParameters("com.example.Main", null));
    }

    @Test
    void mergeSparkSubmitParameters_extraOnly() {
        assertEquals(
            "--jars s3://b/extra.jar",
            SparkJobServiceImpl.mergeSparkSubmitParameters(null, "  --jars s3://b/extra.jar  "));
    }

    @Test
    void mergeSparkSubmitParameters_bothBlank() {
        assertNull(SparkJobServiceImpl.mergeSparkSubmitParameters(null, null));
        assertNull(SparkJobServiceImpl.mergeSparkSubmitParameters("  ", ""));
    }

    @Test
    void submitJob_rejectsBlankEntryPoint() {
        SparkJobRequest request =
            SparkJobRequest.builder()
                .jobName("x")
                .entryPoint("  ")
                .mainClass("com.example.Main")
                .build();
        TestObserver<SparkJobResponse> obs = sparkJobService.submitJob(request).test();
        obs.assertError(IllegalArgumentException.class);
    }

    @Test
    void classFlagPattern_matchesStandaloneClassFlag() {
        assertTrue(SparkJobServiceImpl.SPARK_SUBMIT_CLASS_FLAG.matcher("--class com.foo").find());
        assertTrue(SparkJobServiceImpl.SPARK_SUBMIT_CLASS_FLAG.matcher(" --class com.foo").find());
        assertTrue(SparkJobServiceImpl.SPARK_SUBMIT_CLASS_FLAG.matcher("--class=com.foo").find());
        assertTrue(SparkJobServiceImpl.SPARK_SUBMIT_CLASS_FLAG.matcher("--CLASS com.foo").find());
    }

    @Test
    void classFlagPattern_doesNotMatchClasspath() {
        assertFalse(
            SparkJobServiceImpl.SPARK_SUBMIT_CLASS_FLAG.matcher("--classpath /opt/lib/*").find());
    }

    @Test
    void submitJob_rejectsClassInSparkSubmitParameters() {
        SparkJobRequest request =
            SparkJobRequest.builder()
                .jobName("x")
                .entryPoint(ENTRY_JAR)
                .mainClass("com.example.Main")
                .sparkSubmitParameters("--class com.other.Main --conf k=v")
                .build();
        TestObserver<SparkJobResponse> obs = sparkJobService.submitJob(request).test();
        obs.assertError(IllegalArgumentException.class);
        obs.assertError(
            t ->
                t.getMessage() != null
                    && t.getMessage().contains("sparkSubmitParameters")
                    && t.getMessage().contains("--class"));
    }

    @Test
    void submitJob_shouldReturnSparkJobResponse() throws InterruptedException {
        SparkJobRequest request =
            SparkJobRequest.builder()
                .jobName("Test Job")
                .entryPoint(ENTRY_JAR)
                .mainClass("com.example.TestJob")
                .arguments(List.of("--input", "/data/input"))
                .sparkSubmitParameters("--conf spark.sql.adaptive.enabled=true")
                .timeoutMinutes(60L)
                .tags(Map.of("env", "test"))
                .build();

        StartJobRunResponse emrResponse =
            StartJobRunResponse.builder()
                .applicationId("app-123")
                .jobRunId("job-456")
                .arn(
                    "arn:aws:emr-serverless:us-east-1:123456789012:applications/app-123/jobruns/job-456")
                .build();

        StartJobRunRequest mockRequest = StartJobRunRequest.builder().build();

        when(emrClient.startJobRunRequestBuilder()).thenReturn(requestBuilder);
        when(requestBuilder.clientToken(any(String.class))).thenReturn(requestBuilder);
        when(requestBuilder.name(any(String.class))).thenReturn(requestBuilder);
        when(requestBuilder.executionTimeoutMinutes(any(Long.class))).thenReturn(requestBuilder);
        when(requestBuilder.jobDriver(any(JobDriver.class))).thenReturn(requestBuilder);
        when(requestBuilder.tags(any())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(mockRequest);
        when(emrClient.startJobRun(any(StartJobRunRequest.class))).thenReturn(emrResponse);

        TestObserver<SparkJobResponse> testObserver = sparkJobService.submitJob(request).test();

        testObserver.await();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        SparkJobResponse response = testObserver.values().get(0);
        assertEquals("app-123", response.getApplicationId());
        assertEquals("job-456", response.getJobRunId());
        assertEquals(
            "arn:aws:emr-serverless:us-east-1:123456789012:applications/app-123/jobruns/job-456",
            response.getArn());
        assertEquals("Test Job", response.getJobName());
        assertEquals(ENTRY_JAR, response.getEntryPoint());
        assertEquals("com.example.TestJob", response.getMainClass());
        assertNotNull(response.getSubmittedAt());

        ArgumentCaptor<JobDriver> jobDriverCaptor = ArgumentCaptor.forClass(JobDriver.class);
        verify(requestBuilder).jobDriver(jobDriverCaptor.capture());
        assertEquals(ENTRY_JAR, jobDriverCaptor.getValue().sparkSubmit().entryPoint());
        assertEquals(
            List.of("--input", "/data/input"),
            jobDriverCaptor.getValue().sparkSubmit().entryPointArguments());
        assertTrue(
            jobDriverCaptor.getValue().sparkSubmit().sparkSubmitParameters().contains("--class com.example.TestJob"));
        assertTrue(
            jobDriverCaptor
                .getValue()
                .sparkSubmit()
                .sparkSubmitParameters()
                .contains("spark.sql.adaptive.enabled=true"));

        verify(emrClient).startJobRunRequestBuilder();
        verify(emrClient).startJobRun(any(StartJobRunRequest.class));
    }

    @Test
    void submitJob_shouldGenerateClientToken() throws InterruptedException {
        SparkJobRequest request =
            SparkJobRequest.builder()
                .jobName("Test Job")
                .entryPoint(ENTRY_JAR)
                .mainClass("com.example.TestJob")
                .build();

        StartJobRunResponse emrResponse =
            StartJobRunResponse.builder()
                .applicationId("app-123")
                .jobRunId("job-456")
                .arn(
                    "arn:aws:emr-serverless:us-east-1:123456789012:applications/app-123/jobruns/job-456")
                .build();

        StartJobRunRequest mockRequest = StartJobRunRequest.builder().build();

        when(emrClient.startJobRunRequestBuilder()).thenReturn(requestBuilder);
        when(requestBuilder.clientToken(any(String.class))).thenReturn(requestBuilder);
        when(requestBuilder.name(any(String.class))).thenReturn(requestBuilder);
        when(requestBuilder.executionTimeoutMinutes(any())).thenReturn(requestBuilder);
        when(requestBuilder.jobDriver(any(JobDriver.class))).thenReturn(requestBuilder);
        when(requestBuilder.tags(any())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(mockRequest);
        when(emrClient.startJobRun(any(StartJobRunRequest.class))).thenReturn(emrResponse);

        TestObserver<SparkJobResponse> testObserver = sparkJobService.submitJob(request).test();

        testObserver.await();
        testObserver.assertComplete();

        ArgumentCaptor<String> clientTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBuilder).clientToken(clientTokenCaptor.capture());

        String clientToken = clientTokenCaptor.getValue();
        assertNotNull(clientToken);
        assertEquals(36, clientToken.length());
    }

    @Test
    void getJobStatus_shouldReturnEmrResponse() throws InterruptedException {
        String jobRunId = "job-456";
        String applicationId = "app-123";

        GetJobRunResponse emrResponse =
            GetJobRunResponse.builder()
                .jobRun(
                    JobRun.builder()
                        .jobRunId(jobRunId)
                        .applicationId(applicationId)
                        .state(JobRunState.RUNNING)
                        .build())
                .build();

        when(emrClient.getApplicationId()).thenReturn(applicationId);
        when(emrClient.getJobRun(any(GetJobRunRequest.class))).thenReturn(emrResponse);

        TestObserver<GetJobRunResponse> testObserver = sparkJobService.getJobStatus(jobRunId).test();

        testObserver.await();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        GetJobRunResponse response = testObserver.values().get(0);
        assertEquals(jobRunId, response.jobRun().jobRunId());
        assertEquals(applicationId, response.jobRun().applicationId());
        assertEquals(JobRunState.RUNNING, response.jobRun().state());

        ArgumentCaptor<GetJobRunRequest> requestCaptor = ArgumentCaptor.forClass(GetJobRunRequest.class);
        verify(emrClient).getJobRun(requestCaptor.capture());

        GetJobRunRequest capturedRequest = requestCaptor.getValue();
        assertEquals(applicationId, capturedRequest.applicationId());
        assertEquals(jobRunId, capturedRequest.jobRunId());
    }
}
