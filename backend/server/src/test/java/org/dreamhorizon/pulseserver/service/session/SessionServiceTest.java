package org.dreamhorizon.pulseserver.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.resources.session.models.GetSessionRequest;
import org.dreamhorizon.pulseserver.resources.session.models.GetSessionResponse;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String PROJECT_ID = "project-1";

  @Mock
  ClickhouseQueryService clickhouseQueryService;

  SessionService sessionService;

  @BeforeEach
  void setUp() {
    sessionService = new SessionService(clickhouseQueryService);
    TenantContext.setTenantId(TENANT_ID);
    ProjectContext.setProjectId(PROJECT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    ProjectContext.clear();
  }

  private GetSessionRequest createRequest() {
    return GetSessionRequest.builder()
        .startTime("2024-01-01T00:00:00Z")
        .endTime("2024-01-01T23:59:59Z")
        .spanName("test-span")
        .filters(GetSessionRequest.Filters.builder()
            .appVersionFilters(null)
            .platformFilters(null)
            .osVersionFilters(null)
            .networkProviderFilters(null)
            .stateFilters(null)
            .build())
        .build();
  }

  @Nested
  class GetSessions {

    @Test
    void shouldGetSessionsSuccessfully() {
      GetSessionRequest request = createRequest();
      GetSessionResponse.Session session = GetSessionResponse.Session.builder()
          .sessionId("sess-1")
          .device("iPhone")
          .userId("user-1")
          .duration(5000L)
          .hasAnr(false)
          .hasCrash(false)
          .hasNetwork(true)
          .hasFrozen(false)
          .timestamp("2024-01-01T12:00:00Z")
          .build();

      QueryResultResponse<GetSessionResponse.Session> queryResult =
          QueryResultResponse.<GetSessionResponse.Session>builder()
              .rows(Collections.singletonList(session))
              .jobComplete(true)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(), eq(GetSessionResponse.Session.class)))
          .thenReturn(Single.just(queryResult));

      GetSessionResponse result = sessionService.getSessions(request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getSessions()).hasSize(1);
      assertThat(result.getSessions().get(0).getSessionId()).isEqualTo("sess-1");
      assertThat(result.getSessions().get(0).getDevice()).isEqualTo("iPhone");
      verify(clickhouseQueryService).executeQueryOrCreateJob(any(), eq(GetSessionResponse.Session.class));
    }

    @Test
    void shouldApplyFiltersToQuery() {
      GetSessionRequest request = GetSessionRequest.builder()
          .startTime("2024-01-01T00:00:00Z")
          .endTime("2024-01-01T23:59:59Z")
          .spanName("test-span")
          .filters(GetSessionRequest.Filters.builder()
              .appVersionFilters(List.of("1.0.0"))
              .platformFilters(List.of("iOS"))
              .osVersionFilters(null)
              .networkProviderFilters(null)
              .stateFilters(null)
              .build())
          .build();

      QueryResultResponse<GetSessionResponse.Session> emptyResult =
          QueryResultResponse.<GetSessionResponse.Session>builder()
              .rows(Collections.emptyList())
              .jobComplete(true)
              .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(), eq(GetSessionResponse.Session.class)))
          .thenReturn(Single.just(emptyResult));

      sessionService.getSessions(request).blockingGet();

      verify(clickhouseQueryService).executeQueryOrCreateJob(any(), eq(GetSessionResponse.Session.class));
    }

    @Test
    void shouldPropagateErrorOnQueryFailure() {
      GetSessionRequest request = createRequest();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(), eq(GetSessionResponse.Session.class)))
          .thenReturn(Single.error(new RuntimeException("ClickHouse error")));

      assertThatThrownBy(() -> sessionService.getSessions(request).blockingGet())
          .hasCauseInstanceOf(Exception.class)
          .hasMessageContaining("Failed to fetch sessions");
    }
  }
}
