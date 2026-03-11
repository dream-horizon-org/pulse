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
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationLog;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;
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
class NotificationLogDaoTest {

  @Mock MysqlClient mysqlClient;
  @Mock MySQLPool writerPool;
  @Mock MySQLPool readerPool;
  @Mock PreparedQuery<RowSet<Row>> preparedQuery;
  @Mock RowSet<Row> rowSet;
  @Mock Row row;

  NotificationLogDao notificationLogDao;

  @BeforeEach
  void setup() {
    notificationLogDao = new NotificationLogDao(mysqlClient);
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

  private Row createMockNotificationLogRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("id")).thenReturn(1L);
    when(mockRow.getString("project_id")).thenReturn("proj-1");
    when(mockRow.getString("idempotency_key")).thenReturn("key-1");
    when(mockRow.getString("channel_type")).thenReturn("EMAIL");
    when(mockRow.getLong("channel_id")).thenReturn(10L);
    when(mockRow.getLong("template_id")).thenReturn(20L);
    when(mockRow.getString("recipient")).thenReturn("user@example.com");
    when(mockRow.getString("subject")).thenReturn("Test");
    when(mockRow.getString("status")).thenReturn("SENT");
    when(mockRow.getInteger("attempt_count")).thenReturn(1);
    when(mockRow.getLocalDateTime("last_attempt_at")).thenReturn(now);
    when(mockRow.getString("error_message")).thenReturn(null);
    when(mockRow.getString("error_code")).thenReturn(null);
    when(mockRow.getString("external_id")).thenReturn("ext-1");
    when(mockRow.getString("provider_response")).thenReturn("OK");
    when(mockRow.getInteger("latency_ms")).thenReturn(100);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("sent_at")).thenReturn(now);
    return mockRow;
  }

  @Nested
  class GetLogsByProject {

    @Test
    void shouldGetLogsSuccessfully() {
      setupReaderPreparedQuery();
      Row logRow = createMockNotificationLogRow();
      List<Row> rows = Collections.singletonList(logRow);
      setupRowSetForEach(rowSet, rows);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<NotificationLog> result =
          notificationLogDao.getLogsByProject("proj-1", 10, 0).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getProjectId()).isEqualTo("proj-1");
      assertThat(result.get(0).getChannelType()).isEqualTo(ChannelType.EMAIL);
      assertThat(result.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void shouldReturnEmptyListWhenNoLogs() {
      setupReaderPreparedQuery();
      setupRowSetForEach(rowSet, new ArrayList<>());
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<NotificationLog> result =
          notificationLogDao.getLogsByProject("proj-1", 10, 0).blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GetLogsByIdempotencyKey {

    @Test
    void shouldGetLogsSuccessfully() {
      setupReaderPreparedQuery();
      Row logRow = createMockNotificationLogRow();
      List<Row> rows = Collections.singletonList(logRow);
      setupRowSetForEach(rowSet, rows);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<NotificationLog> result =
          notificationLogDao.getLogsByIdempotencyKey("proj-1", "key-1").blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getIdempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void shouldReturnEmptyListWhenNoLogs() {
      setupReaderPreparedQuery();
      setupRowSetForEach(rowSet, new ArrayList<>());
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<NotificationLog> result =
          notificationLogDao.getLogsByIdempotencyKey("proj-1", "key-empty").blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GetLogByIdempotency {

    @Test
    void shouldGetLogSuccessfully() {
      setupReaderPreparedQuery();
      Row logRow = createMockNotificationLogRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(logRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationLog result =
          notificationLogDao
              .getLogByIdempotency("proj-1", "key-1", ChannelType.EMAIL, "user@example.com")
              .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getIdempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void shouldReturnEmptyWhenLogNotFound() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      NotificationLog result =
          notificationLogDao
              .getLogByIdempotency("proj-1", "nonexistent", ChannelType.EMAIL, "x@y.com")
              .blockingGet();

      assertThat(result).isNull();
    }
  }

  @Nested
  class InsertLog {

    @Test
    void shouldInsertLogSuccessfully() {
      NotificationLog log =
          NotificationLog.builder()
              .projectId("proj-1")
              .idempotencyKey("key-1")
              .channelType(ChannelType.EMAIL)
              .channelId(10L)
              .templateId(20L)
              .recipient("user@example.com")
              .subject("Test")
              .status(NotificationStatus.QUEUED)
              .attemptCount(0)
              .build();

      setupWriterPreparedQuery();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(42L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Long result = notificationLogDao.insertLog(log).blockingGet();

      assertThat(result).isEqualTo(42L);
    }
  }

  @Nested
  class InsertLogIfNotExists {

    @Test
    void shouldReturnTrueWhenInserted() {
      NotificationLog log =
          NotificationLog.builder()
              .projectId("proj-1")
              .idempotencyKey("key-1")
              .channelType(ChannelType.EMAIL)
              .channelId(10L)
              .templateId(20L)
              .recipient("user@example.com")
              .subject("Test")
              .status(NotificationStatus.QUEUED)
              .attemptCount(0)
              .build();

      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = notificationLogDao.insertLogIfNotExists(log).blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNotInserted() {
      NotificationLog log =
          NotificationLog.builder()
              .projectId("proj-1")
              .idempotencyKey("key-1")
              .channelType(ChannelType.EMAIL)
              .channelId(10L)
              .templateId(20L)
              .recipient("user@example.com")
              .subject("Test")
              .status(NotificationStatus.QUEUED)
              .attemptCount(0)
              .build();

      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = notificationLogDao.insertLogIfNotExists(log).blockingGet();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class UpdateLogStatus {

    @Test
    void shouldUpdateLogStatusSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result =
          notificationLogDao
              .updateLogStatus(
                  1L,
                  NotificationStatus.SENT,
                  1,
                  null,
                  null,
                  "ext-1",
                  "OK",
                  100)
              .blockingGet();

      assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroWhenNoRowsUpdated() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result =
          notificationLogDao
              .updateLogStatus(999L, NotificationStatus.SENT, 1, null, null, null, null, null)
              .blockingGet();

      assertThat(result).isEqualTo(0);
    }
  }

  @Nested
  class UpdateLogStatusByExternalId {

    @Test
    void shouldUpdateLogStatusSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result =
          notificationLogDao
              .updateLogStatusByExternalId("ext-1", NotificationStatus.DELIVERED, "Success")
              .blockingGet();

      assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroWhenNoRowsUpdated() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result =
          notificationLogDao
              .updateLogStatusByExternalId("nonexistent", NotificationStatus.SENT, "msg")
              .blockingGet();

      assertThat(result).isEqualTo(0);
    }
  }
}
