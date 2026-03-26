package org.dreamhorizon.pulseserver.dao.tnc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
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
import org.dreamhorizon.pulseserver.dao.tnc.models.TncAcceptance;
import org.dreamhorizon.pulseserver.dao.tnc.models.TncVersion;
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
class TncDaoTest {

  @Mock MysqlClient mysqlClient;
  @Mock MySQLPool writerPool;
  @Mock MySQLPool readerPool;
  @Mock PreparedQuery<RowSet<Row>> preparedQuery;
  @Mock RowSet<Row> rowSet;

  TncDao tncDao;

  @BeforeEach
  void setup() {
    tncDao = new TncDao(mysqlClient);
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

  private Row createMockTncVersionRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("id")).thenReturn(1L);
    when(mockRow.getString("version")).thenReturn("2024-01");
    when(mockRow.getString("tos_s3_url")).thenReturn("s3://bucket/tnc/2024-01/tos.html");
    when(mockRow.getString("aup_s3_url")).thenReturn("s3://bucket/tnc/2024-01/aup.html");
    when(mockRow.getString("privacy_policy_s3_url"))
        .thenReturn("s3://bucket/tnc/2024-01/privacy-policy.html");
    when(mockRow.getString("summary")).thenReturn("Initial terms");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("published_at")).thenReturn(now);
    when(mockRow.getString("created_by")).thenReturn("admin@example.com");
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    return mockRow;
  }

  private Row createMockTncAcceptanceRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("id")).thenReturn(10L);
    when(mockRow.getString("tenant_id")).thenReturn("tenant-1");
    when(mockRow.getLong("tnc_version_id")).thenReturn(1L);
    when(mockRow.getString("accepted_by_email")).thenReturn("user@example.com");
    when(mockRow.getLocalDateTime("accepted_at")).thenReturn(now);
    when(mockRow.getString("user_agent")).thenReturn("Mozilla/5.0");
    return mockRow;
  }

  @Nested
  class GetActiveVersion {

    @Test
    void shouldGetActiveVersionSuccessfully() {
      setupReaderPreparedQuery();
      Row versionRow = createMockTncVersionRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(versionRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      TncVersion result = tncDao.getActiveVersion().blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(1L);
      assertThat(result.getVersion()).isEqualTo("2024-01");
      assertThat(result.getTosS3Url()).isEqualTo("s3://bucket/tnc/2024-01/tos.html");
      assertThat(result.isActive()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenNoActiveVersion() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      TncVersion result = tncDao.getActiveVersion().blockingGet();

      assertThat(result).isNull();
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute()).thenReturn(Single.error(new RuntimeException("DB Error")));

      assertThatThrownBy(() -> tncDao.getActiveVersion().blockingGet())
          .hasMessageContaining("DB Error");
    }

    @Test
    void shouldFormatNullPublishedAt() {
      Row versionRow = createMockTncVersionRow();
      when(versionRow.getLocalDateTime("published_at")).thenReturn(null);
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(versionRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      TncVersion result = tncDao.getActiveVersion().blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getPublishedAt()).isNull();
    }
  }

  @Nested
  class GetAcceptance {

    @Test
    void shouldGetAcceptanceSuccessfully() {
      setupReaderPreparedQuery();
      Row acceptanceRow = createMockTncAcceptanceRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(acceptanceRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      TncAcceptance result = tncDao.getAcceptance("tenant-1", 1L).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(10L);
      assertThat(result.getTenantId()).isEqualTo("tenant-1");
      assertThat(result.getTncVersionId()).isEqualTo(1L);
      assertThat(result.getAcceptedByEmail()).isEqualTo("user@example.com");
    }

    @Test
    void shouldReturnEmptyWhenAcceptanceNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      TncAcceptance result = tncDao.getAcceptance("tenant-1", 1L).blockingGet();

      assertThat(result).isNull();
    }
  }

  @Nested
  class InsertAcceptance {

    @Test
    void shouldInsertAcceptanceAndReturnIt() {
      setupWriterPreparedQuery();
      setupReaderPreparedQuery();
      Row acceptanceRow = createMockTncAcceptanceRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(acceptanceRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet))
          .thenReturn(Single.just(rowSet));

      TncAcceptance result = tncDao.insertAcceptance("tenant-1", 1L, "user@example.com", "Mozilla/5.0")
          .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTenantId()).isEqualTo("tenant-1");
      assertThat(result.getAcceptedByEmail()).isEqualTo("user@example.com");
    }
  }

  @Nested
  class GetAcceptanceHistory {

    @Test
    void shouldGetAcceptanceHistorySuccessfully() {
      setupReaderPreparedQuery();
      Row acceptanceRow1 = createMockTncAcceptanceRow();
      Row acceptanceRow2 = createMockTncAcceptanceRow();
      when(acceptanceRow2.getLong("id")).thenReturn(11L);
      when(acceptanceRow2.getString("accepted_by_email")).thenReturn("other@example.com");
      List<Row> rows = List.of(acceptanceRow1, acceptanceRow2);
      RowIterator<Row> iterator = createMockRowIterator(rows);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<TncAcceptance> result = tncDao.getAcceptanceHistory("tenant-1").toList().blockingGet();

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getTenantId()).isEqualTo("tenant-1");
      assertThat(result.get(1).getAcceptedByEmail()).isEqualTo("other@example.com");
    }

    @Test
    void shouldReturnEmptyListWhenNoHistory() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<TncAcceptance> result = tncDao.getAcceptanceHistory("tenant-1").toList().blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class PublishVersion {

    @Test
    void shouldPublishVersionSuccessfully() {
      setupWriterPreparedQuery();
      setupReaderPreparedQuery();
      Row versionRow = createMockTncVersionRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(versionRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet))
          .thenReturn(Single.just(rowSet));
      when(preparedQuery.rxExecute())
          .thenReturn(Single.just(rowSet));

      TncVersion result = tncDao.publishVersion(
          "2024-02",
          "s3://bucket/tnc/2024-02/tos.html",
          "s3://bucket/tnc/2024-02/aup.html",
          "s3://bucket/tnc/2024-02/privacy-policy.html",
          "Updated terms",
          "admin@example.com"
      ).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo("2024-01");
      assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void shouldThrowOnInsertError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("Insert failed")));

      assertThatThrownBy(() -> tncDao.publishVersion("2024-02", "url1", "url2", "url3", "sum", "admin")
          .blockingGet())
          .hasMessageContaining("Insert failed");
    }
  }
}
