package org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.models.ClickhouseProjectCredentialAudit;
import org.dreamhorizon.pulseserver.model.ClickhouseProjectCredentials;
import org.dreamhorizon.pulseserver.service.ProjectAuditAction;
import org.dreamhorizon.pulseserver.util.encryption.ClickhousePasswordEncryptionUtil;
import org.dreamhorizon.pulseserver.util.encryption.EncryptedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class ClickhouseProjectCredentialsDaoTest {

  @Mock
  MysqlClient mysqlClient;

  @Mock
  MySQLPool writerPool;

  @Mock
  MySQLPool readerPool;

  @Mock
  PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  RowSet<Row> rowSet;

  @Mock
  Row row;

  @Mock
  ClickhousePasswordEncryptionUtil encryptionUtil;

  @Mock
  SqlConnection sqlConnection;

  @Mock
  PreparedQuery<RowSet<Row>> connPreparedQuery;

  ClickhouseProjectCredentialsDao dao;

  private static final EncryptedData MOCK_ENCRYPTED = EncryptedData.builder()
      .encryptedValue("enc-value")
      .salt("salt")
      .digest("digest")
      .build();

  @BeforeEach
  void setup() {
    dao = new ClickhouseProjectCredentialsDao(mysqlClient, encryptionUtil);
    when(encryptionUtil.encrypt(anyString())).thenReturn(MOCK_ENCRYPTED);
    when(encryptionUtil.decrypt(anyString())).thenAnswer(inv -> "decrypted-" + inv.getArgument(0));
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
  }

  private void setupWriterPreparedQuery() {
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupReaderPreparedQuery() {
    when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private RowIterator<Row> createMockRowIterator(List<Row> rows) {
    RowIterator<Row> iterator = mock(RowIterator.class);
    if (rows.isEmpty()) {
      when(iterator.hasNext()).thenReturn(false);
    } else {
      final int[] index = {0};
      when(iterator.hasNext()).thenAnswer(inv -> index[0] < rows.size());
      when(iterator.next()).thenAnswer(inv -> {
        if (index[0] < rows.size()) {
          return rows.get(index[0]++);
        }
        throw new java.util.NoSuchElementException();
      });
    }
    return iterator;
  }

  private Row createMockCredentialsRow(String projectId, String username) {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("id")).thenReturn(1L);
    when(mockRow.getString("project_id")).thenReturn(projectId);
    when(mockRow.getString("clickhouse_username")).thenReturn(username);
    when(mockRow.getString("clickhouse_password_encrypted")).thenReturn("encrypted-pwd");
    when(mockRow.getString("encryption_salt")).thenReturn("salt");
    when(mockRow.getString("password_digest")).thenReturn("digest");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("updated_at")).thenReturn(now);
    return mockRow;
  }

  private Row createMockAuditRow(long id, String projectId, String action, String performedBy, String details) {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("id")).thenReturn(id);
    when(mockRow.getString("project_id")).thenReturn(projectId);
    when(mockRow.getString("action")).thenReturn(action);
    when(mockRow.getString("performed_by")).thenReturn(performedBy);
    when(mockRow.getString("details")).thenReturn(details);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    return mockRow;
  }

  @Nested
  class SaveCredentials {

    @Test
    void shouldSaveCredentialsWithPool() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ClickhouseProjectCredentials result = dao.saveCredentials("proj-1", "project_proj1", "plainPwd")
          .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("proj-1");
      assertThat(result.getClickhouseUsername()).isEqualTo("project_proj1");
      assertThat(result.getClickhousePasswordEncrypted()).isEqualTo("enc-value");
      assertThat(result.getEncryptionSalt()).isEqualTo("salt");
      assertThat(result.getPasswordDigest()).isEqualTo("digest");
      assertThat(result.getIsActive()).isTrue();

      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      org.mockito.Mockito.verify(preparedQuery).rxExecute(tupleCaptor.capture());
      Tuple t = tupleCaptor.getValue();
      assertThat(t.getString(0)).isEqualTo("proj-1");
      assertThat(t.getString(1)).isEqualTo("project_proj1");
      assertThat(t.getString(2)).isEqualTo("enc-value");
      assertThat(t.getString(3)).isEqualTo("salt");
      assertThat(t.getString(4)).isEqualTo("digest");
      assertThat(t.getBoolean(5)).isTrue();
    }

    @Test
    void shouldSaveCredentialsWithSqlConnection() {
      when(sqlConnection.preparedQuery(anyString())).thenReturn(connPreparedQuery);
      when(connPreparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ClickhouseProjectCredentials result = dao.saveCredentials(
          sqlConnection, "proj-2", "project_proj2", "secret")
          .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("proj-2");
      assertThat(result.getClickhouseUsername()).isEqualTo("project_proj2");
    }
  }

  @Nested
  class GetCredentialsByProjectId {

    @Test
    void shouldReturnCredentialsWhenFound() {
      setupReaderPreparedQuery();
      Row credRow = createMockCredentialsRow("proj-1", "project_proj1");
      RowIterator<Row> iterator = createMockRowIterator(List.of(credRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ClickhouseProjectCredentials result = dao.getCredentialsByProjectId("proj-1").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("proj-1");
      assertThat(result.getClickhouseUsername()).isEqualTo("project_proj1");
      assertThat(result.getClickhousePasswordEncrypted()).isEqualTo("decrypted-encrypted-pwd");
    }

    @Test
    void shouldReturnEmptyWhenNoCredentials() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ClickhouseProjectCredentials result = dao.getCredentialsByProjectId("proj-none").blockingGet();

      assertThat(result).isNull();
    }
  }

  @Nested
  class DeactivateCredentials {

    @Test
    void shouldDeactivateSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao.deactivateCredentials("proj-1").blockingAwait();

      org.mockito.Mockito.verify(preparedQuery).rxExecute(any(Tuple.class));
    }

    @Test
    void shouldCompleteWhenNoRowsAffected() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao.deactivateCredentials("proj-none").blockingAwait();
    }
  }

  @Nested
  class InsertAuditLog {

    @Test
    void shouldInsertAuditLog() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      JsonObject details = new JsonObject().put("key", "value");
      dao.insertAuditLog("proj-1", ProjectAuditAction.CREDENTIALS_SETUP, "user-1", details)
          .blockingAwait();

      ArgumentCaptor<Tuple> captor = ArgumentCaptor.forClass(Tuple.class);
      org.mockito.Mockito.verify(preparedQuery).rxExecute(captor.capture());
      Tuple t = captor.getValue();
      assertThat(t.getString(0)).isEqualTo("proj-1");
      assertThat(t.getString(1)).isEqualTo("CREDENTIALS_SETUP");
      assertThat(t.getString(2)).isEqualTo("user-1");
    }

    @Test
    void shouldInsertAuditLogWithNullDetails() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao.insertAuditLog("proj-1", ProjectAuditAction.CREDENTIALS_REMOVED, "user-1", null)
          .blockingAwait();
    }
  }

  @Nested
  class GetAuditLogsByProjectId {

    @Test
    void shouldReturnAuditLogs() {
      setupReaderPreparedQuery();
      Row auditRow = createMockAuditRow(1L, "proj-1", "CREDENTIALS_SETUP", "user-1", "{\"action\":\"setup\"}");
      RowIterator<Row> iterator = createMockRowIterator(List.of(auditRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ClickhouseProjectCredentialAudit> audits =
          dao.getAuditLogsByProjectId("proj-1").toList().blockingGet();

      assertThat(audits).hasSize(1);
      assertThat(audits.get(0).getProjectId()).isEqualTo("proj-1");
      assertThat(audits.get(0).getAction()).isEqualTo("CREDENTIALS_SETUP");
      assertThat(audits.get(0).getPerformedBy()).isEqualTo("user-1");
      assertThat(audits.get(0).getDetails()).isEqualTo("{\"action\":\"setup\"}");
    }

    @Test
    void shouldReturnEmptyWhenNoAudits() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(Collections.emptyList());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ClickhouseProjectCredentialAudit> audits =
          dao.getAuditLogsByProjectId("proj-1").toList().blockingGet();

      assertThat(audits).isEmpty();
    }
  }

  @Nested
  class GetRecentAuditLogs {

    @Test
    void shouldReturnRecentAudits() {
      setupReaderPreparedQuery();
      Row auditRow = createMockAuditRow(1L, "proj-1", "CREDENTIALS_UPDATED", "user-2", null);
      RowIterator<Row> iterator = createMockRowIterator(List.of(auditRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ClickhouseProjectCredentialAudit> audits =
          dao.getRecentAuditLogs(10).toList().blockingGet();

      assertThat(audits).hasSize(1);
      assertThat(audits.get(0).getAction()).isEqualTo("CREDENTIALS_UPDATED");

      ArgumentCaptor<Tuple> captor = ArgumentCaptor.forClass(Tuple.class);
      org.mockito.Mockito.verify(preparedQuery).rxExecute(captor.capture());
      assertThat(captor.getValue().getInteger(0)).isEqualTo(10);
    }
  }
}
