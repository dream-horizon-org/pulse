package org.dreamhorizon.pulseserver.resources.v1.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.ai.models.GetRcaJobResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.rca.RcaReportJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetRcaJobStatusTest {

  @Mock
  private RcaReportJobService rcaReportJobService;

  private GetRcaJobStatus controller;

  @BeforeEach
  void setUp() {
    controller = new GetRcaJobStatus(rcaReportJobService);
  }

  private Response<GetRcaJobResponse> await(CompletionStage<Response<GetRcaJobResponse>> stage)
      throws InterruptedException, ExecutionException, TimeoutException {
    return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Nested
  class GetJobStatusEndpoint {

    @Test
    void shouldReturnJobResponseFromService() throws Exception {
      GetRcaJobResponse jobResponse =
          GetRcaJobResponse.builder()
              .jobId("j1")
              .status("PROCESSING")
              .createdAt(Instant.parse("2025-06-01T10:00:00Z"))
              .pollUrl("/v1/ai-rca/job/j1")
              .build();
      when(rcaReportJobService.getJobStatus("j1", "proj-1")).thenReturn(Single.just(jobResponse));

      Response<GetRcaJobResponse> response = await(controller.getJobStatus("j1", "proj-1"));

      assertThat(response.getData().getJobId()).isEqualTo("j1");
      assertThat(response.getData().getStatus()).isEqualTo("PROCESSING");
      verify(rcaReportJobService).getJobStatus("j1", "proj-1");
    }

    @Test
    void shouldPropagateServiceNotFoundAsException() {
      when(rcaReportJobService.getJobStatus("missing", "proj-1"))
          .thenReturn(Single.error(ServiceError.NOT_FOUND.getException()));

      CompletionStage<Response<GetRcaJobResponse>> stage =
          controller.getJobStatus("missing", "proj-1");

      assertThatThrownBy(() -> stage.toCompletableFuture().get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class);
    }
  }

  @Nested
  class PeekRcaStatusEndpoint {

    @Test
    void shouldReturn404WhenInteractionNameIsNull() {
      CompletionStage<Response<GetRcaJobResponse>> stage =
          controller.peekRcaStatus(null, null, null, "2025-06-01", "proj-1");

      assertThatThrownBy(() -> stage.toCompletableFuture().get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class);
    }

    @Test
    void shouldReturn404WhenInteractionNameIsBlank() {
      CompletionStage<Response<GetRcaJobResponse>> stage =
          controller.peekRcaStatus(null, null, "   ", "2025-06-01", "proj-1");

      assertThatThrownBy(() -> stage.toCompletableFuture().get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class);
    }

    @Test
    void shouldReturn404WhenProjectIdIsNull() {
      CompletionStage<Response<GetRcaJobResponse>> stage =
          controller.peekRcaStatus(null, null, "checkout", "2025-06-01", null);

      assertThatThrownBy(() -> stage.toCompletableFuture().get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class);
    }

    @Test
    void shouldReturn404WhenProjectIdIsBlank() {
      CompletionStage<Response<GetRcaJobResponse>> stage =
          controller.peekRcaStatus(null, null, "checkout", "2025-06-01", "  ");

      assertThatThrownBy(() -> stage.toCompletableFuture().get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class);
    }

    @Test
    void shouldReturnResponseWhenServiceFindsResult() throws Exception {
      GetRcaJobResponse jobResponse =
          GetRcaJobResponse.builder().status("COMPLETED").cached(true).build();
      when(rcaReportJobService.peekStatus(
              eq("proj-1"), eq(RcaType.INTERACTION), eq("checkout"), eq(LocalDate.of(2025, 6, 1))))
          .thenReturn(Maybe.just(jobResponse));

      Response<GetRcaJobResponse> response =
          await(controller.peekRcaStatus(null, null, "checkout", "2025-06-01", "proj-1"));

      assertThat(response.getData().getStatus()).isEqualTo("COMPLETED");
      assertThat(response.getData().getCached()).isTrue();
    }

    @Test
    void shouldReturn404WhenServiceReturnsEmpty() {
      when(rcaReportJobService.peekStatus(any(), any(), any(), any())).thenReturn(Maybe.empty());

      CompletionStage<Response<GetRcaJobResponse>> stage =
          controller.peekRcaStatus(null, null, "checkout", "2025-06-01", "proj-1");

      assertThatThrownBy(() -> stage.toCompletableFuture().get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class);
    }

    @Test
    void shouldFallbackToTodayWhenDateParamIsNull() throws Exception {
      GetRcaJobResponse jobResponse = GetRcaJobResponse.builder().status("PENDING").build();
      when(rcaReportJobService.peekStatus(eq("proj-1"), eq(RcaType.INTERACTION), eq("checkout"), any(LocalDate.class)))
          .thenReturn(Maybe.just(jobResponse));

      Response<GetRcaJobResponse> response =
          await(controller.peekRcaStatus(null, null, "checkout", null, "proj-1"));

      assertThat(response.getData().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldFallbackToTodayWhenDateParamIsInvalid() throws Exception {
      GetRcaJobResponse jobResponse = GetRcaJobResponse.builder().status("PENDING").build();
      when(rcaReportJobService.peekStatus(eq("proj-1"), eq(RcaType.INTERACTION), eq("checkout"), any(LocalDate.class)))
          .thenReturn(Maybe.just(jobResponse));

      Response<GetRcaJobResponse> response =
          await(controller.peekRcaStatus(null, null, "checkout", "not-a-date", "proj-1"));

      assertThat(response.getData().getStatus()).isEqualTo("PENDING");
    }
  }
}
