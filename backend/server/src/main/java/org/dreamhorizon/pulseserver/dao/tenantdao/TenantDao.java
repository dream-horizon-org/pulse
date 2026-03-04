package org.dreamhorizon.pulseserver.dao.tenantdao;

import static org.dreamhorizon.pulseserver.dao.tenantdao.TenantQueries.ACTIVATE_TENANT;
import static org.dreamhorizon.pulseserver.dao.tenantdao.TenantQueries.CHECK_TENANT_EXISTS;
import static org.dreamhorizon.pulseserver.dao.tenantdao.TenantQueries.DEACTIVATE_TENANT;
import static org.dreamhorizon.pulseserver.dao.tenantdao.TenantQueries.DELETE_TENANT;
import static org.dreamhorizon.pulseserver.dao.tenantdao.TenantQueries.GET_ALL_ACTIVE_TENANTS;
import static org.dreamhorizon.pulseserver.dao.tenantdao.TenantQueries.GET_ALL_TENANTS;
import static org.dreamhorizon.pulseserver.dao.tenantdao.TenantQueries.GET_TENANT_BY_DOMAIN_NAME;
import static org.dreamhorizon.pulseserver.dao.tenantdao.TenantQueries.GET_TENANT_BY_GCP_TENANT_ID;
import static org.dreamhorizon.pulseserver.dao.tenantdao.TenantQueries.GET_TENANT_BY_ID;
import static org.dreamhorizon.pulseserver.dao.tenantdao.TenantQueries.INSERT_TENANT;
import static org.dreamhorizon.pulseserver.dao.tenantdao.TenantQueries.UPDATE_TENANT;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.tenantdao.models.Tenant;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantDao {
  private final MysqlClient mysqlClient;

  public Single<Tenant> createTenant(Tenant tenant) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(INSERT_TENANT)
        .rxExecute(
            Tuple.of(
                tenant.getTenantId(),
                tenant.getName(),
                tenant.getDescription(),
                tenant.getGcpTenantId(),
                tenant.getDomainName(),
                true))
        .map(result -> {
          log.info("Created tenant: {}", tenant.getTenantId());
          return Tenant.builder()
              .tenantId(tenant.getTenantId())
              .name(tenant.getName())
              .description(tenant.getDescription())
              .gcpTenantId(tenant.getGcpTenantId())
              .domainName(tenant.getDomainName())
              .isActive(true)
              .build();
        })
        .doOnError(error -> log.error("Failed to create tenant: {}", tenant.getTenantId(), error));
  }

  public Maybe<Tenant> getTenantById(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_TENANT_BY_ID)
        .rxExecute(Tuple.of(tenantId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToTenant(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch tenant: {}", tenantId, error));
  }

  public Flowable<Tenant> getAllActiveTenants() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.query(GET_ALL_ACTIVE_TENANTS)
        .rxExecute()
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToTenant((Row) row)))
        .doOnError(error -> log.error("Failed to fetch all active tenants", error));
  }

  public Flowable<Tenant> getAllTenants() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.query(GET_ALL_TENANTS)
        .rxExecute()
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToTenant((Row) row)))
        .doOnError(error -> log.error("Failed to fetch all tenants", error));
  }

  public Single<Tenant> updateTenant(String tenantId, String name, String description) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(UPDATE_TENANT)
        .rxExecute(Tuple.of(name, description, tenantId))
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException("Tenant not found: " + tenantId));
          }
          log.info("Updated tenant: {}", tenantId);
          return getTenantById(tenantId)
              .switchIfEmpty(Single.error(new RuntimeException("Tenant not found after update: " + tenantId)));
        })
        .doOnError(error -> log.error("Failed to update tenant: {}", tenantId, error));
  }

  public Completable deactivateTenant(String tenantId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DEACTIVATE_TENANT)
        .rxExecute(Tuple.of(tenantId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.warn("No tenant found to deactivate: {}", tenantId);
          } else {
            log.info("Deactivated tenant: {}", tenantId);
          }
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to deactivate tenant: {}", tenantId, error));
  }

  public Completable activateTenant(String tenantId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(ACTIVATE_TENANT)
        .rxExecute(Tuple.of(tenantId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            return Completable.error(new RuntimeException("Tenant not found: " + tenantId));
          }
          log.info("Activated tenant: {}", tenantId);
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to activate tenant: {}", tenantId, error));
  }

  public Completable deleteTenant(String tenantId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DELETE_TENANT)
        .rxExecute(Tuple.of(tenantId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.warn("No tenant found to delete: {}", tenantId);
          } else {
            log.info("Deleted tenant: {}", tenantId);
          }
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to delete tenant: {}", tenantId, error));
  }

  public Single<Boolean> tenantExists(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(CHECK_TENANT_EXISTS)
        .rxExecute(Tuple.of(tenantId))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          return row.getLong("count") > 0;
        })
        .doOnError(error -> log.error("Failed to check tenant existence: {}", tenantId, error));
  }

  public Maybe<Tenant> getTenantByGcpTenantId(String gcpTenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_TENANT_BY_GCP_TENANT_ID)
        .rxExecute(Tuple.of(gcpTenantId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToTenant(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch tenant by gcpTenantId: {}", gcpTenantId, error));
  }

  public Maybe<Tenant> getTenantByDomainName(String domainName) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_TENANT_BY_DOMAIN_NAME)
        .rxExecute(Tuple.of(domainName))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToTenant(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch tenant by domainName: {}", domainName, error));
  }

  private Tenant mapRowToTenant(Row row) {
    return Tenant.builder()
        .tenantId(row.getString("tenant_id"))
        .name(row.getString("name"))
        .description(row.getString("description"))
        .isActive(row.getBoolean("is_active"))
        .createdAt(row.getLocalDateTime("created_at") != null ? row.getLocalDateTime("created_at").toString() : null)
        .updatedAt(row.getLocalDateTime("updated_at") != null ? row.getLocalDateTime("updated_at").toString() : null)
        .gcpTenantId(row.getString("gcp_tenant_id"))
        .domainName(row.getString("domain_name"))
        .build();
  }
}
