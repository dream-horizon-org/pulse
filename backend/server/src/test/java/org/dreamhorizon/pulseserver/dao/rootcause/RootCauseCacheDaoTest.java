package org.dreamhorizon.pulseserver.dao.rootcause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RootCauseCacheDaoTest {

  private static final String PROJECT = "proj-a";
  private static final String INTERACTION = "pay";
  private static final LocalDate DATE = LocalDate.of(2025, 4, 1);

  @Mock
  private ClickhouseQueryService clickhouseQueryService;

  private RootCauseCacheDao dao;

  @BeforeEach
  void setUp() {
    dao = new RootCauseCacheDao(clickhouseQueryService);
  }

  @Nested
  class FindByKey {

    @Test
    void shouldReturnEmptyWhenNoRows() {
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(RootCauseCacheRow.class)))
          .thenReturn(
              Single.just(
                  QueryResultResponse.<RootCauseCacheRow>builder()
                      .jobComplete(true)
                      .rows(List.of())
                      .build()));

      Optional<RootCauseCacheRow> result =
          dao.findByKey(PROJECT, INTERACTION, DATE).blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnFirstRowWhenPresent() {
      RootCauseCacheRow row =
          RootCauseCacheRow.builder()
              .projectId(PROJECT)
              .interactionName(INTERACTION)
              .date(DATE)
              .mode("flat")
              .baseline("{}")
              .segments("[]")
              .cachedAt(LocalDateTime.now())
              .build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(RootCauseCacheRow.class)))
          .thenReturn(
              Single.just(
                  QueryResultResponse.<RootCauseCacheRow>builder()
                      .jobComplete(true)
                      .rows(List.of(row))
                      .build()));

      Optional<RootCauseCacheRow> result =
          dao.findByKey(PROJECT, INTERACTION, DATE).blockingGet();

      assertThat(result).contains(row);
    }

    @Test
    void shouldScopeQueryToProjectInteractionAndDate() {
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(RootCauseCacheRow.class)))
          .thenReturn(
              Single.just(
                  QueryResultResponse.<RootCauseCacheRow>builder()
                      .jobComplete(true)
                      .rows(List.of())
                      .build()));

      dao.findByKey(PROJECT, INTERACTION, DATE).blockingGet();

      ArgumentCaptor<QueryConfiguration> captor = ArgumentCaptor.forClass(QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(captor.capture(), eq(RootCauseCacheRow.class));
      String q = captor.getValue().getQuery();
      assertThat(q).contains("proj-a");
      assertThat(q).contains("pay");
      assertThat(q).contains("2025-04-01");
      assertThat(captor.getValue().getProjectId()).isEqualTo(PROJECT);
    }
  }

  @Nested
  class Upsert {

    @Test
    void shouldExecuteInsertStatement() {
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> ok =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .jobComplete(true)
              .build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenReturn(Single.just(ok));

      dao.upsert(PROJECT, INTERACTION, DATE, "flat", "{}", "[]", LocalDateTime.of(2025, 4, 1, 12, 0))
          .blockingAwait();

      ArgumentCaptor<QueryConfiguration> captor = ArgumentCaptor.forClass(QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(captor.capture());
      assertThat(captor.getValue().getQuery()).contains("INSERT INTO otel.root_cause_cache");
      assertThat(captor.getValue().getQuery()).contains("'proj-a'");
    }

    @Test
    void shouldPropagateErrorsFromClickhouse() {
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenReturn(Single.error(new RuntimeException("clickhouse down")));

      io.reactivex.rxjava3.observers.TestObserver<Void> observer = new io.reactivex.rxjava3.observers.TestObserver<>();
      dao.upsert(PROJECT, INTERACTION, DATE, "m", "{}", "[]", LocalDateTime.now())
          .subscribe(observer);
      observer.assertError(RuntimeException.class);
    }
  }
}
