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
import software.amazon.awssdk.services.emrserverless.model.JobRun;
import software.amazon.awssdk.services.emrserverless.model.JobRunState;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SparkJobServiceImplTest {

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
    void submitJob_shouldReturnSparkJobResponse() {
        // Given
        SparkJobRequest request = SparkJobRequest.builder()
                .jobName("Test Job")
                .mainClass("com.example.TestJob")
                .arguments(List.of("--input", "/data/input"))
                .sparkConfig("--conf spark.sql.adaptive.enabled=true")
                .timeoutMinutes(60)
                .tags(Map.of("env", "test"))
                .mode("BATCH")
                .build();

        StartJobRunResponse emrResponse = StartJobRunResponse.builder()
                .applicationId("app-123")
                .jobRunId("job-456")
                .arn("arn:aws:emr-serverless:us-east-1:123456789012:applications/app-123/jobruns/job-456")
                .build();

        when(emrClient.startJobRunRequestBuilder()).thenReturn(requestBuilder);
        when(requestBuilder.clientToken(any(String.class))).thenReturn(requestBuilder);
        when(requestBuilder.name(any(String.class))).thenReturn(requestBuilder);
        when(requestBuilder.executionTimeoutMinutes(any(Integer.class))).thenReturn(requestBuilder);
        when(requestBuilder.jobDriver(any())).thenReturn(requestBuilder);
        when(requestBuilder.tags(any())).thenReturn(requestBuilder);
        when(requestBuilder.mode(any(String.class))).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(any(StartJobRunRequest.class));
        when(emrClient.startJobRun(any(StartJobRunRequest.class))).thenReturn(emrResponse);

        // When
        TestObserver<SparkJobResponse> testObserver = sparkJobService.submitJob(request).test();

        // Then
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        SparkJobResponse response = testObserver.values().get(0);
        assertEquals("app-123", response.getApplicationId());
        assertEquals("job-456", response.getJobRunId());
        assertEquals("arn:aws:emr-serverless:us-east-1:123456789012:applications/app-123/jobruns/job-456", response.getArn());
        assertEquals("Test Job", response.getJobName());
        assertEquals("com.example.TestJob", response.getMainClass());
        assertNotNull(response.getSubmittedAt());

        // Verify EMR client was called
        verify(emrClient).startJobRunRequestBuilder();
        verify(emrClient).startJobRun(any(StartJobRunRequest.class));
    }

    @Test
    void submitJob_shouldGenerateClientToken() {
        // Given
        SparkJobRequest request = SparkJobRequest.builder()
                .jobName("Test Job")
                .mainClass("com.example.TestJob")
                .build();

        StartJobRunResponse emrResponse = StartJobRunResponse.builder()
                .applicationId("app-123")
                .jobRunId("job-456")
                .arn("arn:aws:emr-serverless:us-east-1:123456789012:applications/app-123/jobruns/job-456")
                .build();

        when(emrClient.startJobRunRequestBuilder()).thenReturn(requestBuilder);
        when(requestBuilder.clientToken(any(String.class))).thenReturn(requestBuilder);
        when(requestBuilder.name(any(String.class))).thenReturn(requestBuilder);
        when(requestBuilder.executionTimeoutMinutes(any())).thenReturn(requestBuilder);
        when(requestBuilder.jobDriver(any())).thenReturn(requestBuilder);
        when(requestBuilder.tags(any())).thenReturn(requestBuilder);
        when(requestBuilder.mode(any())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(any(StartJobRunRequest.class));
        when(emrClient.startJobRun(any(StartJobRunRequest.class))).thenReturn(emrResponse);

        // When
        TestObserver<SparkJobResponse> testObserver = sparkJobService.submitJob(request).test();

        // Then
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();

        // Verify clientToken was generated (UUID format)
        ArgumentCaptor<String> clientTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBuilder).clientToken(clientTokenCaptor.capture());
        
        String clientToken = clientTokenCaptor.getValue();
        assertNotNull(clientToken);
        // UUID format check (36 characters with dashes)
        assertEquals(36, clientToken.length());
    }

    @Test
    void getJobStatus_shouldReturnEmrResponse() {
        // Given
        String jobRunId = "job-456";
        String applicationId = "app-123";
        
        GetJobRunResponse emrResponse = GetJobRunResponse.builder()
                .jobRun(JobRun.builder()
                        .jobRunId(jobRunId)
                        .applicationId(applicationId)
                        .state(JobRunState.RUNNING)
                        .build())
                .build();

        when(emrClient.getApplicationId()).thenReturn(applicationId);
        when(emrClient.getJobRun(any(GetJobRunRequest.class))).thenReturn(emrResponse);

        // When
        TestObserver<GetJobRunResponse> testObserver = sparkJobService.getJobStatus(jobRunId).test();

        // Then
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        GetJobRunResponse response = testObserver.values().get(0);
        assertEquals(jobRunId, response.jobRun().jobRunId());
        assertEquals(applicationId, response.jobRun().applicationId());
        assertEquals(JobRunState.RUNNING, response.jobRun().state());

        // Verify EMR client was called with correct parameters
        ArgumentCaptor<GetJobRunRequest> requestCaptor = ArgumentCaptor.forClass(GetJobRunRequest.class);
        verify(emrClient).getJobRun(requestCaptor.capture());
        
        GetJobRunRequest capturedRequest = requestCaptor.getValue();
        assertEquals(applicationId, capturedRequest.applicationId());
        assertEquals(jobRunId, capturedRequest.jobRunId());
    }
}