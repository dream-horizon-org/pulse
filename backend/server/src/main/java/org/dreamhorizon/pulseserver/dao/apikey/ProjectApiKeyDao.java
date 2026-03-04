package org.dreamhorizon.pulseserver.dao.apikey;

import static org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyQueries.CHECK_ACTIVE_API_KEY_EXISTS;
import static org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyQueries.DEACTIVATE_API_KEY;
import static org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyQueries.GET_ACTIVE_API_KEYS_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyQueries.GET_ALL_API_KEYS_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyQueries.GET_ALL_VALID_API_KEYS;
import static org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyQueries.GET_API_KEY_BY_DIGEST;
import static org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyQueries.GET_API_KEY_BY_ID;
import static org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyQueries.INSERT_API_KEY;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.apikey.models.ProjectApiKey;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectApiKeyDao {
  private final MysqlClient mysqlClient;

  public Single<ProjectApiKey> createApiKey(
      String projectId,
      String displayName,
      String apiKeyEncrypted,
      String encryptionSalt,
      String apiKeyDigest,
      LocalDateTime expiresAt,
      String createdBy) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(INSERT_API_KEY)
        .rxExecute(buildApiKeyTuple(projectId, displayName, apiKeyEncrypted, encryptionSalt, apiKeyDigest, expiresAt, createdBy))
        .map(result -> mapToCreatedApiKey(result, projectId, displayName, apiKeyEncrypted, encryptionSalt, apiKeyDigest, expiresAt, createdBy))
        .doOnError(error -> log.error("Failed to create API key for project: {}", projectId, error));
  }

  public Single<ProjectApiKey> createApiKey(
      SqlConnection conn,
      String projectId,
      String displayName,
      String apiKeyEncrypted,
      String encryptionSalt,
      String apiKeyDigest,
      LocalDateTime expiresAt,
      String createdBy) {
    return conn.preparedQuery(INSERT_API_KEY)
        .rxExecute(buildApiKeyTuple(projectId, displayName, apiKeyEncrypted, encryptionSalt, apiKeyDigest, expiresAt, createdBy))
        .map(result -> mapToCreatedApiKey(result, projectId, displayName, apiKeyEncrypted, encryptionSalt, apiKeyDigest, expiresAt, createdBy))
        .doOnError(error -> log.error("Failed to create API key for project: {}", projectId, error));
  }

  private Tuple buildApiKeyTuple(
      String projectId,
      String displayName,
      String apiKeyEncrypted,
      String encryptionSalt,
      String apiKeyDigest,
      LocalDateTime expiresAt,
      String createdBy) {
    return Tuple.tuple()
        .addString(projectId)
        .addString(displayName)
        .addString(apiKeyEncrypted)
        .addString(encryptionSalt)
        .addString(apiKeyDigest)
        .addLocalDateTime(expiresAt)
        .addString(createdBy);
  }

  private ProjectApiKey mapToCreatedApiKey(
      io.vertx.rxjava3.sqlclient.RowSet<Row> result,
      String projectId,
      String displayName,
      String apiKeyEncrypted,
      String encryptionSalt,
      String apiKeyDigest,
      LocalDateTime expiresAt,
      String createdBy) {
    long generatedId = Long.parseLong(result.property(io.vertx.rxjava3.mysqlclient.MySQLClient.LAST_INSERTED_ID).toString());
    log.info("Created API key {} for project: {}", generatedId, projectId);
    return ProjectApiKey.builder()
        .projectApiKeyId(generatedId)
        .projectId(projectId)
        .displayName(displayName)
        .apiKeyEncrypted(apiKeyEncrypted)
        .encryptionSalt(encryptionSalt)
        .apiKeyDigest(apiKeyDigest)
        .isActive(true)
        .expiresAt(expiresAt != null ? expiresAt.toString() : null)
        .createdBy(createdBy)
        .build();
  }

  public Maybe<ProjectApiKey> getApiKeyById(long apiKeyId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_API_KEY_BY_ID)
        .rxExecute(Tuple.of(apiKeyId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToApiKey(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch API key by id: {}", apiKeyId, error));
  }

  public Maybe<ProjectApiKey> getApiKeyByDigest(String apiKeyDigest) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_API_KEY_BY_DIGEST)
        .rxExecute(Tuple.of(apiKeyDigest))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToApiKey(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch API key by digest", error));
  }

  public Flowable<ProjectApiKey> getActiveApiKeysByProjectId(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_ACTIVE_API_KEYS_BY_PROJECT_ID)
        .rxExecute(Tuple.of(projectId))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToApiKey((Row) row)))
        .doOnError(error -> log.error("Failed to fetch active API keys for project: {}", projectId, error));
  }

  public Flowable<ProjectApiKey> getAllApiKeysByProjectId(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_ALL_API_KEYS_BY_PROJECT_ID)
        .rxExecute(Tuple.of(projectId))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToApiKey((Row) row)))
        .doOnError(error -> log.error("Failed to fetch all API keys for project: {}", projectId, error));
  }

  public Completable deactivateApiKey(
      long apiKeyId,
      String projectId,
      String deactivatedBy,
      String deactivationReason,
      LocalDateTime gracePeriodEndsAt) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DEACTIVATE_API_KEY)
        .rxExecute(Tuple.of(deactivatedBy, deactivationReason, gracePeriodEndsAt, apiKeyId, projectId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            return Completable.error(new RuntimeException("API key not found or does not belong to project: " + apiKeyId));
          }
          log.info("Deactivated API key: {} for project: {} reason: {}", apiKeyId, projectId, deactivationReason);
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to deactivate API key: {}", apiKeyId, error));
  }

  public Single<Boolean> hasActiveApiKey(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(CHECK_ACTIVE_API_KEY_EXISTS)
        .rxExecute(Tuple.of(projectId))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          return row.getLong("count") > 0;
        })
        .doOnError(error -> log.error("Failed to check active API key existence for project: {}", projectId, error));
  }

  /**
   * Returns all valid API keys across all projects.
   * Valid means: active OR (inactive but in grace period), AND not expired.
   */
  public Flowable<ProjectApiKey> getAllValidApiKeys() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.query(GET_ALL_VALID_API_KEYS)
        .rxExecute()
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToApiKey((Row) row)))
        .doOnError(error -> log.error("Failed to fetch all valid API keys", error));
  }

  private ProjectApiKey mapRowToApiKey(Row row) {
    return ProjectApiKey.builder()
        .projectApiKeyId(row.getLong("project_api_key_id"))
        .projectId(row.getString("project_id"))
        .displayName(row.getString("display_name"))
        .apiKeyEncrypted(row.getString("api_key_encrypted"))
        .encryptionSalt(row.getString("encryption_salt"))
        .apiKeyDigest(row.getString("api_key_digest"))
        .isActive(row.getBoolean("is_active"))
        .expiresAt(row.getLocalDateTime("expires_at") != null
            ? row.getLocalDateTime("expires_at").toString() : null)
        .gracePeriodEndsAt(row.getLocalDateTime("grace_period_ends_at") != null
            ? row.getLocalDateTime("grace_period_ends_at").toString() : null)
        .createdBy(row.getString("created_by"))
        .createdAt(row.getLocalDateTime("created_at") != null
            ? row.getLocalDateTime("created_at").toString() : null)
        .deactivatedAt(row.getLocalDateTime("deactivated_at") != null
            ? row.getLocalDateTime("deactivated_at").toString() : null)
        .deactivatedBy(row.getString("deactivated_by"))
        .deactivationReason(row.getString("deactivation_reason"))
        .build();
  }
}

