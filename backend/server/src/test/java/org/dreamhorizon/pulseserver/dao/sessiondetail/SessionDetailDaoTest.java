package org.dreamhorizon.pulseserver.dao.sessiondetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionCoreRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionDetailDaoTest {

  private static final String SESSION_ID = "sess-dao-123";
  private static final String PROJECT_ID = "proj-1";
  private static final String SESSION_START = "2024-01-01T00:00:00Z";
  private static final String SESSION_END = "2024-01-01T01:00:00Z";

  @Mock
  ClickhouseQueryService clickhouseQueryService;

  SessionDetailDao sessionDetailDao;

  @BeforeEach
  void setUp() {
    sessionDetailDao = new SessionDetailDao(clickhouseQueryService);
    TenantContext.setTenantId(PROJECT_ID);
    ProjectContext.setProjectId(PROJECT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    ProjectContext.clear();
  }

  @Nested
  class GetSessionCore {

    @Test
    void shouldSubstituteSessionIdAndCallClickHouse() {
      SessionCoreRow row = SessionCoreRow.builder()
          .sessionId(SESSION_ID)
          .userId("u1")
          .qualityScore(0.9)
          .build();
      QueryResultResponse<SessionCoreRow> response = QueryResultResponse.<SessionCoreRow>builder()
          .rows(java.util.List.of(row))
          .build();

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(SessionCoreRow.class)))
          .thenReturn(Single.just(response));

      QueryResultResponse<SessionCoreRow> result = sessionDetailDao
          .getSessionCore(SESSION_ID, SESSION_START, SESSION_END)
          .blockingGet();

      assertThat(result.getRows()).hasSize(1);
      assertThat(result.getRows().get(0).getSessionId()).isEqualTo(SESSION_ID);

      ArgumentCaptor<QueryConfiguration> configCaptor = ArgumentCaptor.forClass(QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture(), eq(SessionCoreRow.class));
      assertThat(configCaptor.getValue().getQuery()).contains(SESSION_ID);
      assertThat(configCaptor.getValue().getProjectId()).isEqualTo(PROJECT_ID);
      assertThat(configCaptor.getValue().getTimeoutMs()).isEqualTo(15000);
    }
  }
}
