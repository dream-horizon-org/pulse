package org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials;

import static org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsQueries.DEACTIVATE_CREDENTIALS;
import static org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsQueries.GET_AUDIT_BY_PROJECT;
import static org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsQueries.GET_CREDENTIALS_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsQueries.GET_RECENT_AUDITS;
import static org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsQueries.INSERT_AUDIT;
import static org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsQueries.INSERT_CREDENTIALS;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.models.ClickhouseProjectCredentialAudit;
import org.dreamhorizon.pulseserver.model.ClickhouseProjectCredentials;
import org.dreamhorizon.pulseserver.service.ProjectAuditAction;
import org.dreamhorizon.pulseserver.util.encryption.ClickhousePasswordEncryptionUtil;
import org.dreamhorizon.pulseserver.util.encryption.EncryptedData;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickhouseProjectCredentialsDao {

  private final MysqlClient mysqlClient;
  private final ClickhousePasswordEncryptionUtil encryptionUtil;

  public Single<ClickhouseProjectCredentials> saveCredentials(
      SqlConnection conn,
      String projectId,
      String clickhouseUsername,
      String plainPassword) {

    EncryptedData encrypted = encryptionUtil.encrypt(plainPassword);

    return conn.preparedQuery(INSERT_CREDENTIALS)
        .rxExecute(buildCredentialsTuple(projectId, clickhouseUsername, encrypted))
        .map(result -> mapToSavedCredentials(projectId, clickhouseUsername, encrypted))
        .doOnError(error ->
            log.error("Failed to save credentials: projectId={}", projectId, error)
        );
  }

  public Single<ClickhouseProjectCredentials> saveCredentials(
      String projectId,
      String clickhouseUsername,
      String plainPassword) {

    EncryptedData encrypted = encryptionUtil.encrypt(plainPassword);

    return mysqlClient.getWriterPool()
        .preparedQuery(INSERT_CREDENTIALS)
        .rxExecute(buildCredentialsTuple(projectId, clickhouseUsername, encrypted))
        .map(result -> mapToSavedCredentials(projectId, clickhouseUsername, encrypted))
        .doOnError(error ->
            log.error("Failed to save credentials: projectId={}", projectId, error)
        );
  }

  private Tuple buildCredentialsTuple(String projectId, String clickhouseUsername, EncryptedData encrypted) {
    return Tuple.of(
        projectId,
        clickhouseUsername,
        encrypted.getEncryptedValue(),
        encrypted.getSalt(),
        encrypted.getDigest(),
        true
    );
  }

  private ClickhouseProjectCredentials mapToSavedCredentials(
      String projectId,
      String clickhouseUsername,
      EncryptedData encrypted) {
    log.info("Saved ClickHouse credentials: projectId={}, username={}", projectId, clickhouseUsername);
    return ClickhouseProjectCredentials.builder()
        .projectId(projectId)
        .clickhouseUsername(clickhouseUsername)
        .clickhousePasswordEncrypted(encrypted.getEncryptedValue())
        .encryptionSalt(encrypted.getSalt())
        .passwordDigest(encrypted.getDigest())
        .isActive(true)
        .build();
  }

  public Maybe<ClickhouseProjectCredentials> getCredentialsByProjectId(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();

    return pool.preparedQuery(GET_CREDENTIALS_BY_PROJECT_ID)
        .rxExecute(Tuple.of(projectId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            log.debug("No credentials found for project: {}", projectId);
            return Maybe.empty();
          }

          Row row = rowSet.iterator().next();
          String encryptedPassword = row.getString("clickhouse_password_encrypted");

          return Maybe.fromCallable(() -> {
            String decryptedPassword = encryptionUtil.decrypt(encryptedPassword);

            return ClickhouseProjectCredentials.builder()
                .id(row.getLong("id"))
                .projectId(row.getString("project_id"))
                .clickhouseUsername(row.getString("clickhouse_username"))
                .clickhousePasswordEncrypted(decryptedPassword)  // Return decrypted
                .encryptionSalt(row.getString("encryption_salt"))
                .passwordDigest(row.getString("password_digest"))
                .isActive(row.getBoolean("is_active"))
                .createdAt(row.getLocalDateTime("created_at") != null ?
                    row.getLocalDateTime("created_at").toString() : null)
                .updatedAt(row.getLocalDateTime("updated_at") != null ?
                    row.getLocalDateTime("updated_at").toString() : null)
                .build();
          });
        })
        .doOnError(error ->
            log.error("Failed to get credentials: projectId={}", projectId, error)
        );
  }

  public Completable deactivateCredentials(String projectId) {
    MySQLPool pool = mysqlClient.getWriterPool();

    return pool.preparedQuery(DEACTIVATE_CREDENTIALS)
        .rxExecute(Tuple.of(projectId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.warn("No credentials found to deactivate: projectId={}", projectId);
          }
          log.info("Deactivated credentials: projectId={}", projectId);
          return Completable.complete();
        })
        .doOnError(error ->
            log.error("Failed to deactivate credentials: projectId={}", projectId, error)
        );
  }

  /**
   * Insert audit log for credential operations.
   */
  public Completable insertAuditLog(String projectId, ProjectAuditAction action, String performedBy, JsonObject details) {
    MySQLPool pool = mysqlClient.getWriterPool();
    String detailsJson = details != null ? details.encode() : null;

    return pool.preparedQuery(INSERT_AUDIT)
        .rxExecute(Tuple.of(projectId, action.getValue(), performedBy, detailsJson))
        .flatMapCompletable(result -> {
          log.debug("Inserted audit log for project: {}, action: {}", projectId, action.getValue());
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to insert audit log for project: {}", projectId, error));
  }

  /**
   * Get audit logs for a specific project.
   */
  public Flowable<ClickhouseProjectCredentialAudit> getAuditLogsByProjectId(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_AUDIT_BY_PROJECT)
        .rxExecute(Tuple.of(projectId))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToAudit((Row) row)))
        .doOnError(error -> log.error("Failed to fetch audit logs for project: {}", projectId, error));
  }

  /**
   * Get recent audit logs across all projects.
   */
  public Flowable<ClickhouseProjectCredentialAudit> getRecentAuditLogs(int limit) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_RECENT_AUDITS)
        .rxExecute(Tuple.of(limit))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToAudit((Row) row)))
        .doOnError(error -> log.error("Failed to fetch recent audit logs", error));
  }

  private ClickhouseProjectCredentialAudit mapRowToAudit(Row row) {
    return ClickhouseProjectCredentialAudit.builder()
        .id(row.getLong("id"))
        .projectId(row.getString("project_id"))
        .action(row.getString("action"))
        .performedBy(row.getString("performed_by"))
        .details(row.getString("details"))
        .createdAt(row.getLocalDateTime("created_at").toString())
        .build();
  }
}
