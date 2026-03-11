package org.dreamhorizon.pulseserver.dao.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.context.ProjectContext;
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
class BaseInteractionDaoTest {

  @Mock
  MysqlClient mysqlClient;

  @Mock
  MySQLPool readerPool;

  @Mock
  SqlConnection sqlConnection;

  BaseInteractionDao baseInteractionDao;

  @BeforeEach
  void setUp() {
    baseInteractionDao = new BaseInteractionDao(mysqlClient);
    ProjectContext.setProjectId("test-project");
  }

  @Nested
  class TestIsInteractionPresent {

    @Test
    void shouldReturnTrueWhenInteractionExists() {
      Row countRow = mock(Row.class);
      when(countRow.getInteger("count")).thenReturn(1);

      RowSet<Row> rowSet = mock(RowSet.class);
      RowIterator<Row> iterator = mock(RowIterator.class);
      when(rowSet.iterator()).thenReturn(iterator);
      when(iterator.next()).thenReturn(countRow);

      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
      when(mysqlClient.getReaderPool()).thenReturn(readerPool);
      when(readerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = baseInteractionDao.isInteractionPresent("existing-interaction").blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenInteractionDoesNotExist() {
      Row countRow = mock(Row.class);
      when(countRow.getInteger("count")).thenReturn(0);

      RowSet<Row> rowSet = mock(RowSet.class);
      RowIterator<Row> iterator = mock(RowIterator.class);
      when(rowSet.iterator()).thenReturn(iterator);
      when(iterator.next()).thenReturn(countRow);

      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
      when(mysqlClient.getReaderPool()).thenReturn(readerPool);
      when(readerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = baseInteractionDao.isInteractionPresent("non-existent").blockingGet();

      assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseOnDatabaseError() {
      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
      when(mysqlClient.getReaderPool()).thenReturn(readerPool);
      when(readerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("DB error")));

      Boolean result = baseInteractionDao.isInteractionPresent("any-interaction").blockingGet();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class TestArchiveJob {

    @Test
    void shouldArchiveJobSuccessfully() {
      RowSet<Row> rowSet = mock(RowSet.class);
      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = baseInteractionDao.archiveJob(
          sqlConnection, "interaction-to-archive", "user@test.com").blockingGet();

      assertThat(result).isTrue();
      verify(preparedQuery).rxExecute(any(Tuple.class));
    }
  }
}
