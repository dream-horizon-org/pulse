package org.dreamhorizon.pulseserver.dao.suggestedinteraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.WebApplicationException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.List;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateSuggestedInteractionsRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateSuggestedInteractionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.Event;
import org.dreamhorizon.pulseserver.service.interaction.models.SuggestedInteractionCreateItem;
import org.dreamhorizon.pulseserver.service.interaction.models.SuggestedInteractionEdge;
import java.time.LocalDateTime;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.GetSuggestedInteractionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.SuggestedInteractionDetails;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperFactory;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperUtil;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
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
class SuggestedInteractionDaoTest {

  private static final String PROJECT_ID = "test-project";

  @Mock
  MysqlClient mysqlClient;
  @Mock
  MySQLPool readerPool;
  @Mock
  MySQLPool writerPool;

  @Mock
  SqlConnection sqlConnection;

  ObjectMapperUtil objectMapperUtil;
  SuggestedInteractionDao dao;

  @BeforeEach
  void setUp() {
    ProjectContext.setProjectId(PROJECT_ID);
    objectMapperUtil = new ObjectMapperUtil(ObjectMapperFactory.get());
    dao = new SuggestedInteractionDao(mysqlClient, objectMapperUtil);
  }

  @AfterEach
  void tearDown() {
    ProjectContext.clear();
  }

  private Row mockDataRow(boolean includeEdges) {
    Row row = mock(Row.class);
    when(row.getValue("events_json")).thenReturn(
        "[{\"name\":\"StepOne\",\"props\":[],\"isBlacklisted\":false},"
            + "{\"name\":\"StepTwo\",\"props\":[],\"isBlacklisted\":false}]");
    when(row.getValue("edges_json")).thenReturn(
        includeEdges
            ? "[{\"from\":\"StepOne\",\"to\":\"StepTwo\",\"mean_gap_s\":0.5,\"median_gap_s\":0.4,"
                + "\"cv\":0.1,\"p5_s\":0.1,\"p95_s\":1.2}]"
            : null);
    when(row.getLong("id")).thenReturn(42L);
    when(row.getString("project_id")).thenReturn(PROJECT_ID);
    when(row.getInteger("total_occurrences")).thenReturn(100);
    when(row.getInteger("unique_sessions")).thenReturn(80);
    when(row.getDouble("session_pct")).thenReturn(12.5);
    when(row.getDouble("mean_span_s")).thenReturn(1.1);
    when(row.getDouble("median_span_s")).thenReturn(0.9);
    when(row.getDouble("p95_span_s")).thenReturn(3.2);
    when(row.getDouble("cv")).thenReturn(0.15);
    when(row.getString("status")).thenReturn("PENDING");
    when(row.getLocalDateTime("created_at")).thenReturn(LocalDateTime.of(2025, 1, 15, 10, 30));
    return row;
  }

  private void attachRowIterator(RowSet<Row> rowSet, Row... rows) {
    RowIterator<Row> iterator = mock(RowIterator.class);
    when(rowSet.iterator()).thenReturn(iterator);
    if (rows.length == 0) {
      when(iterator.hasNext()).thenReturn(false);
    } else if (rows.length == 1) {
      when(iterator.hasNext()).thenReturn(true, false);
      when(iterator.next()).thenReturn(rows[0]);
    } else {
      when(iterator.hasNext()).thenReturn(true, true, false);
      when(iterator.next()).thenReturn(rows[0], rows[1]);
    }
    doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      java.util.function.Consumer<Row> consumer = invocation.getArgument(0);
      for (Row r : rows) {
        consumer.accept(r);
      }
      return null;
    }).when(rowSet).forEach(any());
  }

  @SuppressWarnings("unchecked")
  private void stubReaderQuery(RowSet<Row> rowSet) {
    PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    when(readerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
    when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
  }

  @SuppressWarnings("unchecked")
  private void stubWriterQuery() {
    RowSet<Row> rowSet = mock(RowSet.class);
    when(rowSet.rowCount()).thenReturn(1);
    PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
    when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
  }

  @Nested
  class GetSuggestedInteractions {

    @Test
    void shouldReturnEmptyWhenNoRows() {
      RowSet<Row> rowSet = mock(RowSet.class);
      attachRowIterator(rowSet);
      stubReaderQuery(rowSet);

      GetSuggestedInteractionsResponse result = dao.getSuggestionsByStatus(null).blockingGet();

      assertThat(result.getSuggestions()).isEmpty();
      assertThat(result.getTotalSuggestions()).isZero();
    }

    @Test
    void shouldMapRowWithoutEdges() {
      Row row = mockDataRow(false);
      RowSet<Row> rowSet = mock(RowSet.class);
      attachRowIterator(rowSet, row);
      stubReaderQuery(rowSet);

      GetSuggestedInteractionsResponse result = dao.getSuggestionsByStatus(null).blockingGet();

      assertThat(result.getTotalSuggestions()).isEqualTo(1);
      SuggestedInteractionDetails d = result.getSuggestions().get(0);
      assertThat(d.getId()).isEqualTo(42L);
      assertThat(d.getPattern()).containsExactly("StepOne", "StepTwo");
      assertThat(d.getEdges()).isEmpty();
      assertThat(d.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldMapRowWithEdges() {
      Row row = mockDataRow(true);
      RowSet<Row> rowSet = mock(RowSet.class);
      attachRowIterator(rowSet, row);
      stubReaderQuery(rowSet);

      GetSuggestedInteractionsResponse result = dao.getSuggestionsByStatus(null).blockingGet();

      assertThat(result.getSuggestions().get(0).getEdges()).hasSize(1);
      assertThat(result.getSuggestions().get(0).getEdges().get(0).getFrom()).isEqualTo("StepOne");
      assertThat(result.getSuggestions().get(0).getEdges().get(0).getTo()).isEqualTo("StepTwo");
    }
  }

  @Nested
  class GetSuggestionById {

    @Test
    void shouldReturnWhenRowExists() {
      Row row = mockDataRow(false);
      RowSet<Row> rowSet = mock(RowSet.class);
      attachRowIterator(rowSet, row);
      stubReaderQuery(rowSet);

      SuggestedInteractionDetails result = dao.getSuggestionById(42L).blockingGet();

      assertThat(result.getId()).isEqualTo(42L);
    }

    @Test
    void shouldThrowWhenNotFound() {
      RowSet<Row> rowSet = mock(RowSet.class);
      attachRowIterator(rowSet);
      stubReaderQuery(rowSet);

      assertThatThrownBy(() -> dao.getSuggestionById(99L).blockingGet())
          .isInstanceOf(WebApplicationException.class)
          .hasMessageContaining("99");
    }
  }

  @Nested
  class UpdateStatus {

    @Test
    void shouldReturnEmptyResponseOnSuccess() {
      stubWriterQuery();

      EmptyResponse response = dao.updateStatus(5L, "DISMISSED", "u@x.com").blockingGet();

      assertThat(response).isNotNull();
      verify(writerPool).preparedQuery(eq(Queries.UPDATE_STATUS));
    }

    @Test
    void shouldPropagateErrorOnFailure() {
      stubWriterQueryError(new RuntimeException("Write failed"));

      TestObserver<EmptyResponse> observer = dao.updateStatus(5L, "DISMISSED", "u@x.com").test();
      observer.assertError(RuntimeException.class);
    }

    @Test
    void shouldReturn404WhenNoRowsUpdated() {
      RowSet<Row> rowSet = mock(RowSet.class);
      when(rowSet.rowCount()).thenReturn(0);
      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
      when(mysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(eq(Queries.UPDATE_STATUS))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      TestObserver<EmptyResponse> observer = dao.updateStatus(999L, "DISMISSED", "u@x.com").test();
      observer.assertError(throwable -> {
        assertThat(throwable).isInstanceOf(WebApplicationException.class);
        WebApplicationException wae = (WebApplicationException) throwable;
        assertThat(wae.getResponse().getStatus()).isEqualTo(404);
        return true;
      });
    }

    @Test
    void shouldPassCorrectTupleArguments() {
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      RowSet<Row> rowSet = mock(RowSet.class);
      when(rowSet.rowCount()).thenReturn(1);
      @SuppressWarnings("unchecked")
      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
      when(mysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(eq(Queries.UPDATE_STATUS))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(tupleCaptor.capture())).thenReturn(Single.just(rowSet));

      dao.updateStatus(7L, "ACTIVATED", "admin@test.com").blockingGet();

      Tuple captured = tupleCaptor.getValue();
      assertThat(captured.getString(0)).isEqualTo("ACTIVATED");
      assertThat(captured.getString(1)).isEqualTo("admin@test.com");
      assertThat(captured.getLong(2)).isEqualTo(7L);
      assertThat(captured.getString(3)).isEqualTo(PROJECT_ID);
    }
  }

  @Nested
  class GetSuggestedInteractionsByStatus {

    @Test
    void shouldUseCatalogQueryWhenStatusIsAll() {
      Row row = mockDataRow(false);
      RowSet<Row> rowSet = mock(RowSet.class);
      attachRowIterator(rowSet, row);

      ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
      @SuppressWarnings("unchecked")
      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
      when(mysqlClient.getReaderPool()).thenReturn(readerPool);
      when(readerPool.preparedQuery(queryCaptor.capture())).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao.getSuggestionsByStatus("ALL").blockingGet();

      assertThat(queryCaptor.getValue()).isEqualTo(Queries.GET_SUGGESTIONS_CATALOG_BY_PROJECT);
    }

    @Test
    void shouldUseStatusFilterQueryWhenStatusIsDismissed() {
      Row row = mockDataRow(false);
      when(row.getString("status")).thenReturn("DISMISSED");
      RowSet<Row> rowSet = mock(RowSet.class);
      attachRowIterator(rowSet, row);

      ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      @SuppressWarnings("unchecked")
      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
      when(mysqlClient.getReaderPool()).thenReturn(readerPool);
      when(readerPool.preparedQuery(queryCaptor.capture())).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(tupleCaptor.capture())).thenReturn(Single.just(rowSet));

      dao.getSuggestionsByStatus("dismissed").blockingGet();

      assertThat(queryCaptor.getValue()).isEqualTo(Queries.GET_SUGGESTIONS_CATALOG_BY_PROJECT_AND_STATUS);
      assertThat(tupleCaptor.getValue().getString(1)).isEqualTo("DISMISSED");
    }
  }

  @Nested
  class CreateSuggestions {

    private SuggestedInteractionCreateItem buildCreateItem() {
      return SuggestedInteractionCreateItem.builder()
          .events(List.of(
              Event.builder().name("StepOne").props(List.of()).isBlacklisted(false).build()))
          .totalOccurrences(100)
          .uniqueSessions(80)
          .sessionPct(12.5)
          .meanSpanS(1.1)
          .medianSpanS(0.9)
          .p95SpanS(3.2)
          .cv(0.15)
          .edges(List.of(
              SuggestedInteractionEdge.builder().from("StepOne").to("StepTwo").build()))
          .build();
    }

    private void stubCreateConnection() {
      when(mysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.rxClose()).thenReturn(Completable.complete());
    }

    @Test
    void shouldReturnZeroCountsWhenSuggestionsEmpty() {
      CreateSuggestedInteractionsRequest request = CreateSuggestedInteractionsRequest.builder()
          .replacePending(true)
          .suggestions(List.of())
          .build();

      CreateSuggestedInteractionsResponse result = dao.createSuggestions(request).blockingGet();

      assertThat(result.getCreatedCount()).isZero();
      assertThat(result.getReplacedPendingCount()).isZero();
      verifyNoInteractions(writerPool);
    }

    @Test
    void shouldInsertWithoutReplacingPending() {
      stubCreateConnection();
      PreparedQuery<RowSet<Row>> insertPq = mock(PreparedQuery.class);
      RowSet<Row> insertRs = mock(RowSet.class);
      when(sqlConnection.preparedQuery(Queries.INSERT_SUGGESTION)).thenReturn(insertPq);
      when(insertPq.rxExecute(any(Tuple.class))).thenReturn(Single.just(insertRs));

      CreateSuggestedInteractionsRequest request = CreateSuggestedInteractionsRequest.builder()
          .replacePending(false)
          .suggestions(List.of(buildCreateItem()))
          .build();

      CreateSuggestedInteractionsResponse result = dao.createSuggestions(request).blockingGet();

      assertThat(result.getCreatedCount()).isEqualTo(1);
      assertThat(result.getReplacedPendingCount()).isZero();
      verify(sqlConnection).preparedQuery(Queries.INSERT_SUGGESTION);
      verify(sqlConnection, org.mockito.Mockito.never()).preparedQuery(Queries.DELETE_PENDING_BY_PROJECT);
    }

    @Test
    void shouldReplacePendingThenInsert() {
      stubCreateConnection();
      PreparedQuery<RowSet<Row>> deletePq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insertPq = mock(PreparedQuery.class);
      RowSet<Row> deleteRs = mock(RowSet.class);
      RowSet<Row> insertRs = mock(RowSet.class);
      when(deleteRs.rowCount()).thenReturn(2);
      when(sqlConnection.preparedQuery(Queries.DELETE_PENDING_BY_PROJECT)).thenReturn(deletePq);
      when(sqlConnection.preparedQuery(Queries.INSERT_SUGGESTION)).thenReturn(insertPq);
      when(deletePq.rxExecute(any(Tuple.class))).thenReturn(Single.just(deleteRs));
      when(insertPq.rxExecute(any(Tuple.class))).thenReturn(Single.just(insertRs));

      CreateSuggestedInteractionsRequest request = CreateSuggestedInteractionsRequest.builder()
          .replacePending(true)
          .suggestions(List.of(buildCreateItem(), buildCreateItem()))
          .build();

      CreateSuggestedInteractionsResponse result = dao.createSuggestions(request).blockingGet();

      assertThat(result.getCreatedCount()).isEqualTo(2);
      assertThat(result.getReplacedPendingCount()).isEqualTo(2);
    }

    @Test
    void shouldPropagateErrorOnConnectionFailure() {
      when(mysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.error(new RuntimeException("No connection")));

      CreateSuggestedInteractionsRequest request = CreateSuggestedInteractionsRequest.builder()
          .suggestions(List.of(buildCreateItem()))
          .build();

      TestObserver<CreateSuggestedInteractionsResponse> observer = dao.createSuggestions(request).test();
      observer.assertError(RuntimeException.class);
    }
  }

  @Nested
  class GetSuggestedInteractionsErrors {

    @Test
    void shouldPropagateErrorOnDatabaseFailure() {
      stubReaderQueryError(new RuntimeException("Connection lost"));

      TestObserver<GetSuggestedInteractionsResponse> observer = dao.getSuggestionsByStatus(null).test();
      observer.assertError(RuntimeException.class);
    }
  }

  @Nested
  class GetSuggestionByIdErrors {

    @Test
    void shouldPropagateErrorOnDatabaseFailure() {
      stubReaderQueryError(new RuntimeException("Timeout"));

      TestObserver<SuggestedInteractionDetails> observer = dao.getSuggestionById(1L).test();
      observer.assertError(RuntimeException.class);
    }
  }

  @Nested
  class GetSuggestedInteractionsMultipleRows {

    @Test
    void shouldReturnMultipleRows() {
      Row row1 = mockDataRow(false);
      Row row2 = mockDataRow(true);
      when(row2.getLong("id")).thenReturn(43L);

      RowSet<Row> rowSet = mock(RowSet.class);
      attachRowIterator(rowSet, row1, row2);
      stubReaderQuery(rowSet);

      GetSuggestedInteractionsResponse result = dao.getSuggestionsByStatus(null).blockingGet();

      assertThat(result.getSuggestions()).hasSize(2);
      assertThat(result.getTotalSuggestions()).isEqualTo(2);
      assertThat(result.getSuggestions().get(0).getId()).isEqualTo(42L);
      assertThat(result.getSuggestions().get(1).getId()).isEqualTo(43L);
    }
  }

  @Nested
  class GetSuggestionByIdQueryArgs {

    @Test
    void shouldPassCorrectQueryArguments() {
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      Row row = mockDataRow(false);
      RowSet<Row> rowSet = mock(RowSet.class);
      attachRowIterator(rowSet, row);

      @SuppressWarnings("unchecked")
      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
      when(mysqlClient.getReaderPool()).thenReturn(readerPool);
      when(readerPool.preparedQuery(eq(Queries.GET_SUGGESTION_BY_ID))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(tupleCaptor.capture())).thenReturn(Single.just(rowSet));

      dao.getSuggestionById(42L).blockingGet();

      Tuple captured = tupleCaptor.getValue();
      assertThat(captured.getLong(0)).isEqualTo(42L);
      assertThat(captured.getString(1)).isEqualTo(PROJECT_ID);
    }
  }

  @SuppressWarnings("unchecked")
  private void stubReaderQueryError(Throwable error) {
    PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    when(readerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
    when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.error(error));
  }

  @SuppressWarnings("unchecked")
  private void stubWriterQueryError(Throwable error) {
    PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
    when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.error(error));
  }
}
