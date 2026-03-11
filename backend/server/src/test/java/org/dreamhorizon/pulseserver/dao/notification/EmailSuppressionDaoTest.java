package org.dreamhorizon.pulseserver.dao.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.service.notification.models.EmailSuppression;
import org.dreamhorizon.pulseserver.service.notification.models.SuppressionReason;
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
@SuppressWarnings("unchecked")
class EmailSuppressionDaoTest {

  @Mock MysqlClient mysqlClient;
  @Mock MySQLPool writerPool;
  @Mock MySQLPool readerPool;
  @Mock PreparedQuery<RowSet<Row>> preparedQuery;
  @Mock RowSet<Row> rowSet;
  @Mock Row row;

  EmailSuppressionDao emailSuppressionDao;

  @BeforeEach
  void setup() {
    emailSuppressionDao = new EmailSuppressionDao(mysqlClient);
  }

  private void setupWriterPool() {
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
  }

  private void setupReaderPool() {
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
  }

  private void setupWriterPreparedQuery() {
    setupWriterPool();
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupReaderPreparedQuery() {
    setupReaderPool();
    when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private RowIterator<Row> createMockRowIterator(List<Row> rows) {
    RowIterator<Row> iterator = mock(RowIterator.class);
    if (rows.isEmpty()) {
      when(iterator.hasNext()).thenReturn(false);
    } else {
      final int[] index = {0};
      when(iterator.hasNext()).thenAnswer(invocation -> index[0] < rows.size());
      when(iterator.next()).thenAnswer(invocation -> {
        if (index[0] < rows.size()) {
          return rows.get(index[0]++);
        }
        throw new java.util.NoSuchElementException();
      });
    }
    return iterator;
  }

  private void setupRowSetForEach(RowSet<Row> rs, List<Row> rows) {
    doAnswer(invocation -> {
      java.util.function.Consumer<Row> consumer = invocation.getArgument(0);
      rows.forEach(consumer);
      return null;
    }).when(rs).forEach(any());
  }

  private Row createMockSuppressionRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("id")).thenReturn(1L);
    when(mockRow.getString("project_id")).thenReturn("proj-1");
    when(mockRow.getString("email")).thenReturn("bounced@example.com");
    when(mockRow.getString("reason")).thenReturn("BOUNCE");
    when(mockRow.getString("bounce_type")).thenReturn("Permanent");
    when(mockRow.getString("source_message_id")).thenReturn("msg-123");
    when(mockRow.getLocalDateTime("suppressed_at")).thenReturn(now);
    return mockRow;
  }

  @Nested
  class GetSuppressionByEmail {

    @Test
    void shouldGetSuppressionSuccessfully() {
      setupReaderPreparedQuery();
      Row suppressionRow = createMockSuppressionRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(suppressionRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      EmailSuppression result =
          emailSuppressionDao.getSuppressionByEmail("proj-1", "bounced@example.com").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getEmail()).isEqualTo("bounced@example.com");
      assertThat(result.getReason()).isEqualTo(SuppressionReason.BOUNCE);
    }

    @Test
    void shouldReturnEmptyWhenSuppressionNotFound() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      EmailSuppression result =
          emailSuppressionDao.getSuppressionByEmail("proj-1", "normal@example.com").blockingGet();

      assertThat(result).isNull();
    }

    @Test
    void shouldLowercaseEmailWhenQuerying() {
      setupReaderPreparedQuery();
      Row suppressionRow = createMockSuppressionRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(suppressionRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      EmailSuppression result =
          emailSuppressionDao.getSuppressionByEmail("proj-1", "BOUNCED@EXAMPLE.COM").blockingGet();

      assertThat(result).isNotNull();
    }
  }

  @Nested
  class IsEmailSuppressed {

    @Test
    void shouldReturnTrueWhenEmailIsSuppressed() {
      setupReaderPreparedQuery();
      Row suppressionRow = createMockSuppressionRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(suppressionRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result =
          emailSuppressionDao.isEmailSuppressed("proj-1", "bounced@example.com").blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenEmailIsNotSuppressed() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result =
          emailSuppressionDao.isEmailSuppressed("proj-1", "normal@example.com").blockingGet();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class AddToSuppressionList {

    @Test
    void shouldAddSuppressionSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(15L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Long result =
          emailSuppressionDao
              .addToSuppressionList(
                  "proj-1",
                  "bounced@example.com",
                  SuppressionReason.BOUNCE,
                  "Permanent",
                  "msg-123")
              .blockingGet();

      assertThat(result).isEqualTo(15L);
    }
  }

  @Nested
  class AddToSuppressionListAllProjects {

    @Test
    void shouldReturnTrueWhenInserted() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(5);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result =
          emailSuppressionDao
              .addToSuppressionListAllProjects(
                  "bounced@example.com", SuppressionReason.BOUNCE, "Permanent", "msg-123")
              .blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNoRowsInserted() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result =
          emailSuppressionDao
              .addToSuppressionListAllProjects(
                  "normal@example.com", SuppressionReason.MANUAL, null, null)
              .blockingGet();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class RemoveFromSuppressionList {

    @Test
    void shouldRemoveSuppressionSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result =
          emailSuppressionDao
              .removeFromSuppressionList("proj-1", "bounced@example.com")
              .blockingGet();

      assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroWhenNoRowsDeleted() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result =
          emailSuppressionDao
              .removeFromSuppressionList("proj-1", "not-suppressed@example.com")
              .blockingGet();

      assertThat(result).isEqualTo(0);
    }
  }

  @Nested
  class GetSuppressionsByProject {

    @Test
    void shouldGetSuppressionsSuccessfully() {
      setupReaderPreparedQuery();
      Row suppressionRow = createMockSuppressionRow();
      List<Row> rows = Collections.singletonList(suppressionRow);
      setupRowSetForEach(rowSet, rows);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<EmailSuppression> result =
          emailSuppressionDao.getSuppressionsByProject("proj-1", 10, 0).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getProjectId()).isEqualTo("proj-1");
      assertThat(result.get(0).getReason()).isEqualTo(SuppressionReason.BOUNCE);
    }

    @Test
    void shouldReturnEmptyListWhenNoSuppressions() {
      setupReaderPreparedQuery();
      setupRowSetForEach(rowSet, new ArrayList<>());
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<EmailSuppression> result =
          emailSuppressionDao.getSuppressionsByProject("proj-empty", 10, 0).blockingGet();

      assertThat(result).isEmpty();
    }
  }
}
