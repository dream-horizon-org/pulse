package org.dreamhorizon.pulseserver.client.emr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.dreamhorizon.pulseserver.config.EmrServerlessConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.emrserverless.EmrServerlessClient;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunResponse;
import software.amazon.awssdk.services.emrserverless.model.JobRun;
import software.amazon.awssdk.services.emrserverless.model.JobRunState;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunResponse;

/**
 * Unit tests for {@link EmrServerlessJobClient}.
 * <p>
 * The production class constructs an {@link EmrServerlessClient} internally when enabled, which
 * makes it impossible to inject a mock via the public constructor. These tests therefore focus on:
 * <ul>
 *   <li>the disabled-path behavior (constructor + {@code ensureEnabled} guard)</li>
 *   <li>reflectively swapping in a mock AWS client to exercise the enabled methods</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EmrServerlessJobClientTest {

  private EmrServerlessConfig disabledConfig;

  @BeforeEach
  void setUp() {
    disabledConfig = EmrServerlessConfig.builder()
        .enabled(false)
        .build();
  }

  @Nested
  class WhenDisabled {

    @Test
    void shouldReportDisabled() {
      try (EmrServerlessJobClient jobClient = new EmrServerlessJobClient(disabledConfig)) {
        assertThat(jobClient.isEnabled()).isFalse();
      }
    }

    @Test
    void shouldThrowWhenStartingJobRun() {
      try (EmrServerlessJobClient jobClient = new EmrServerlessJobClient(disabledConfig)) {
        StartJobRunRequest request = StartJobRunRequest.builder().applicationId("app").build();
        assertThatThrownBy(() -> jobClient.startJobRun(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EMR Serverless is disabled");
      }
    }

    @Test
    void shouldThrowWhenGettingJobRun() {
      try (EmrServerlessJobClient jobClient = new EmrServerlessJobClient(disabledConfig)) {
        GetJobRunRequest request = GetJobRunRequest.builder().applicationId("app").jobRunId("j").build();
        assertThatThrownBy(() -> jobClient.getJobRun(request))
            .isInstanceOf(IllegalStateException.class);
      }
    }

    @Test
    void shouldThrowWhenBuildingStartJobRunRequest() {
      try (EmrServerlessJobClient jobClient = new EmrServerlessJobClient(disabledConfig)) {
        assertThatThrownBy(jobClient::startJobRunRequestBuilder)
            .isInstanceOf(IllegalStateException.class);
      }
    }

    @Test
    void shouldReturnApplicationIdFromConfig() {
      EmrServerlessConfig cfg = EmrServerlessConfig.builder()
          .enabled(false)
          .applicationId("app-xyz")
          .build();
      try (EmrServerlessJobClient jobClient = new EmrServerlessJobClient(cfg)) {
        assertThat(jobClient.getApplicationId()).isEqualTo("app-xyz");
      }
    }

    @Test
    void closeShouldBeNoopWhenClientIsNull() {
      EmrServerlessJobClient jobClient = new EmrServerlessJobClient(disabledConfig);
      // Should not throw even though internal client is null.
      jobClient.close();
      jobClient.close();
    }
  }

  @Nested
  class WhenEnabled {

    private EmrServerlessConfig enabledConfig;
    private EmrServerlessClient underlyingClient;
    private EmrServerlessJobClient jobClient;

    @BeforeEach
    void setUp() throws Exception {
      enabledConfig = EmrServerlessConfig.builder()
          .enabled(false) // keep false so constructor does NOT build a real AWS client
          .applicationId("app-id")
          .executionRoleArn("arn:aws:iam::123:role/exec")
          .region("us-east-1")
          .build();

      jobClient = new EmrServerlessJobClient(enabledConfig);

      // Now flip the flag + inject a mock AWS client using reflection to simulate an enabled
      // client without the constructor actually contacting AWS.
      enabledConfig.setEnabled(true);
      underlyingClient = mock(EmrServerlessClient.class);
      java.lang.reflect.Field clientField = EmrServerlessJobClient.class.getDeclaredField("client");
      clientField.setAccessible(true);
      clientField.set(jobClient, underlyingClient);
    }

    @Test
    void shouldReportEnabled() {
      assertThat(jobClient.isEnabled()).isTrue();
    }

    @Test
    void shouldReturnApplicationId() {
      assertThat(jobClient.getApplicationId()).isEqualTo("app-id");
    }

    @Test
    void shouldDelegateStartJobRun() {
      StartJobRunRequest request = StartJobRunRequest.builder().applicationId("app-id").build();
      StartJobRunResponse response = StartJobRunResponse.builder()
          .applicationId("app-id")
          .jobRunId("job-1")
          .build();
      when(underlyingClient.startJobRun(any(StartJobRunRequest.class))).thenReturn(response);

      StartJobRunResponse actual = jobClient.startJobRun(request);

      assertThat(actual).isSameAs(response);
      verify(underlyingClient, times(1)).startJobRun(request);
    }

    @Test
    void shouldDelegateGetJobRun() {
      GetJobRunRequest request = GetJobRunRequest.builder().applicationId("app-id").jobRunId("job-1").build();
      GetJobRunResponse response = GetJobRunResponse.builder()
          .jobRun(JobRun.builder().jobRunId("job-1").state(JobRunState.RUNNING).build())
          .build();
      when(underlyingClient.getJobRun(any(GetJobRunRequest.class))).thenReturn(response);

      GetJobRunResponse actual = jobClient.getJobRun(request);

      assertThat(actual).isSameAs(response);
      verify(underlyingClient).getJobRun(request);
    }

    @Test
    void shouldBuildStartJobRunRequestWithApplicationIdAndRoleArn() {
      StartJobRunRequest.Builder builder = jobClient.startJobRunRequestBuilder();
      StartJobRunRequest built = builder.name("job-x").build();

      assertThat(built.applicationId()).isEqualTo("app-id");
      assertThat(built.executionRoleArn()).isEqualTo("arn:aws:iam::123:role/exec");
      assertThat(built.name()).isEqualTo("job-x");
    }

    @Test
    void shouldBuildStartJobRunRequestWithoutRoleArnWhenBlank() {
      enabledConfig.setExecutionRoleArn("   ");

      StartJobRunRequest built = jobClient.startJobRunRequestBuilder().name("job-y").build();

      assertThat(built.applicationId()).isEqualTo("app-id");
      assertThat(built.executionRoleArn()).isNull();
    }

    @Test
    void shouldBuildStartJobRunRequestWithoutRoleArnWhenNull() {
      enabledConfig.setExecutionRoleArn(null);

      StartJobRunRequest built = jobClient.startJobRunRequestBuilder().name("job-z").build();

      assertThat(built.executionRoleArn()).isNull();
    }

    @Test
    void closeShouldCloseUnderlyingClient() {
      jobClient.close();
      verify(underlyingClient, times(1)).close();
    }

    @Test
    void closeShouldNotInvokeWhenClientIsNullAfterReflection() throws Exception {
      java.lang.reflect.Field clientField = EmrServerlessJobClient.class.getDeclaredField("client");
      clientField.setAccessible(true);
      clientField.set(jobClient, null);

      jobClient.close();

      verify(underlyingClient, never()).close();
    }
  }

  @Nested
  class EffectiveRegion {

    @Test
    void shouldUseDefaultRegionWhenBlank() {
      EmrServerlessConfig cfg = EmrServerlessConfig.builder().enabled(false).region("  ").build();
      try (EmrServerlessJobClient jobClient = new EmrServerlessJobClient(cfg)) {
        assertThat(jobClient.isEnabled()).isFalse();
      }
      assertThat(cfg.getEffectiveRegion()).isEqualTo("ap-south-1");
    }
  }
}
