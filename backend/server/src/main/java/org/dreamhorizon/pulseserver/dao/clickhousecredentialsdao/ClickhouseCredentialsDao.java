package org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao;

import static org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.Queries.DEACTIVATE_CREDENTIALS;
import static org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.Queries.GET_ALL_ACTIVE_CREDENTIALS;
import static org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.Queries.GET_AUDIT_BY_TENANT;
import static org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.Queries.GET_CREDENTIALS_BY_TENANT;
import static org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.Queries.GET_CREDENTIALS_BY_TENANT_INCLUDING_INACTIVE;
import static org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.Queries.GET_RECENT_AUDITS;
import static org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.Queries.INSERT_AUDIT;
import static org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.Queries.INSERT_CREDENTIALS;
import static org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.Queries.REACTIVATE_CREDENTIALS;
import static org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.Queries.UPDATE_CREDENTIALS;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models.ClickhouseTenantCredentialAudit;
import org.dreamhorizon.pulseserver.service.tenant.TenantAuditAction;
import org.dreamhorizon.pulseserver.util.PasswordEncryptionUtil;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickhouseCredentialsDao {
  private final MysqlClient mysqlClient;
  private final PasswordEncryptionUtil encryptionUtil;


  public Single<ClickhouseCredentials> saveTenantCredentials(String tenantId, String plainPassword) {
    try {
      String clickhouseUsername = "tenant_" + tenantId;
      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(plainPassword);

      MySQLPool pool = mysqlClient.getWriterPool();
      return pool.preparedQuery(INSERT_CREDENTIALS)
          .rxExecute(
              Tuple.of(
                  tenantId,
                  clickhouseUsername,
                  encrypted.getEncryptedPassword(),
                  encrypted.getSalt(),
                  encrypted.getDigest(),
                  true))
          .map(
              result -> {
                log.info("Saved ClickHouse credentials for tenant: {}", tenantId);
                return ClickhouseCredentials.builder()
                    .tenantId(tenantId)
                    .clickhouseUsername(clickhouseUsername)
                    .isActive(true)
                    .build();
              })
          .doOnError(error -> log.error("Failed to save credentials for tenant: {}", tenantId, error));
    } catch (Exception e) {
      log.error("Error preparing credentials for tenant: {}", tenantId, e);
      return Single.error(e);
    }
  }

  public Single<ClickhouseCredentials> getCredentialsByTenantId(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_CREDENTIALS_BY_TENANT)
        .rxExecute(Tuple.of(tenantId))
        .map(
            rowSet -> {
              if (rowSet.size() == 0) {
                throw new RuntimeException("No credentials found for tenant: " + tenantId);
              }
              var row = rowSet.iterator().next();
              return mapRowToTenantCredentials(row);
            })
        .doOnError(error -> log.error("Failed to fetch credentials for tenant: {}", tenantId, error));
  }

  public Maybe<ClickhouseCredentials> getCredentialsByTenantIdIncludingInactive(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_CREDENTIALS_BY_TENANT_INCLUDING_INACTIVE)
        .rxExecute(Tuple.of(tenantId))
        .flatMapMaybe(
            rowSet -> {
              if (rowSet.size() == 0) {
                return Maybe.empty();
              }
              var row = rowSet.iterator().next();
              return Maybe.just(mapRowToTenantCredentials(row));
            })
        .doOnError(error -> log.error("Failed to fetch credentials for tenant: {}", tenantId, error));
  }

  public Flowable<ClickhouseCredentials> getAllActiveTenantCredentials() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.query(GET_ALL_ACTIVE_CREDENTIALS)
        .rxExecute()
        .toFlowable()
        .flatMap(
            rowSet -> {
              return Flowable.fromIterable(rowSet)
                  .map(row -> mapRowToTenantCredentials((Row) row));
            })
        .doOnError(error -> log.error("Failed to fetch all tenant credentials", error));
  }


  public Single<ClickhouseCredentials> updateTenantCredentials(String tenantId, String newPlainPassword) {
    try {
      PasswordEncryptionUtil.EncryptedPassword encrypted = encryptionUtil.encryptPassword(newPlainPassword);

      MySQLPool pool = mysqlClient.getWriterPool();
      return pool.preparedQuery(UPDATE_CREDENTIALS)
          .rxExecute(
              Tuple.of(
                  encrypted.getEncryptedPassword(),
                  encrypted.getSalt(),
                  encrypted.getDigest(),
                  tenantId))
          .flatMap(result -> {
            if (result.rowCount() == 0) {
              return Single.error(new RuntimeException("No credentials found for tenant: " + tenantId));
            }
            log.info("Updated ClickHouse credentials for tenant: {}", tenantId);
            return getCredentialsByTenantId(tenantId);
          })
          .doOnError(error -> log.error("Failed to update credentials for tenant: {}", tenantId, error));
    } catch (Exception e) {
      log.error("Error preparing credential update for tenant: {}", tenantId, e);
      return Single.error(e);
    }
  }

  public Completable deactivateTenantCredentials(String tenantId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DEACTIVATE_CREDENTIALS)
        .rxExecute(Tuple.of(tenantId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.warn("No credentials found to deactivate for tenant: {}", tenantId);
          } else {
            log.info("Deactivated credentials for tenant: {}", tenantId);
          }
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to deactivate credentials for tenant: {}", tenantId, error));
  }

  public Completable reactivateTenantCredentials(String tenantId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(REACTIVATE_CREDENTIALS)
        .rxExecute(Tuple.of(tenantId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            return Completable.error(new RuntimeException("No credentials found for tenant: " + tenantId));
          }
          log.info("Reactivated credentials for tenant: {}", tenantId);
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to reactivate credentials for tenant: {}", tenantId, error));
  }

  public Completable insertAuditLog(String tenantId, TenantAuditAction action, String performedBy, JsonObject details) {
    MySQLPool pool = mysqlClient.getWriterPool();
    String detailsJson = details != null ? details.encode() : null;

    return pool.preparedQuery(INSERT_AUDIT)
        .rxExecute(Tuple.of(tenantId, action.getValue(), performedBy, detailsJson))
        .flatMapCompletable(result -> {
          log.debug("Inserted audit log for tenant: {}, action: {}", tenantId, action.getValue());
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to insert audit log for tenant: {}", tenantId, error));
  }

  public Flowable<ClickhouseTenantCredentialAudit> getAuditLogsByTenantId(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_AUDIT_BY_TENANT)
        .rxExecute(Tuple.of(tenantId))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToAudit((Row) row)))
        .doOnError(error -> log.error("Failed to fetch audit logs for tenant: {}", tenantId, error));
  }

  public Flowable<ClickhouseTenantCredentialAudit> getRecentAuditLogs(int limit) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_RECENT_AUDITS)
        .rxExecute(Tuple.of(limit))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToAudit((Row) row)))
        .doOnError(error -> log.error("Failed to fetch recent audit logs", error));
  }


  private ClickhouseCredentials mapRowToTenantCredentials(Row row) {
    String encryptedPassword = row.getString("clickhouse_password_encrypted");
    String decryptedPassword = encryptionUtil.decryptPassword(encryptedPassword);

    return ClickhouseCredentials.builder()
        .id(row.getLong("id"))
        .tenantId(row.getString("tenant_id"))
        .clickhouseUsername(row.getString("clickhouse_username"))
        .clickhousePassword(decryptedPassword)
        .encryptionSalt(row.getString("encryption_salt"))
        .passwordDigest(row.getString("password_digest"))
        .isActive(row.getBoolean("is_active"))
        .createdAt(row.getLocalDateTime("created_at").toString())
        .updatedAt(row.getLocalDateTime("updated_at").toString())
        .build();
  }

  private ClickhouseTenantCredentialAudit mapRowToAudit(Row row) {
    return ClickhouseTenantCredentialAudit.builder()
        .id(row.getLong("id"))
        .tenantId(row.getString("tenant_id"))
        .action(row.getString("action"))
        .performedBy(row.getString("performed_by"))
        .details(row.getString("details"))
        .createdAt(row.getLocalDateTime("created_at").toString())
        .build();
  }
}
