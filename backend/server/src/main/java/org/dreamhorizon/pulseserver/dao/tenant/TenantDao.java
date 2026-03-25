package org.dreamhorizon.pulseserver.dao.tenant;

import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.ACTIVATE_TENANT;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.CHECK_TENANT_EXISTS;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.DEACTIVATE_TENANT;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.DELETE_TENANT;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.GET_ALL_ACTIVE_TENANTS;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.GET_ALL_TENANTS;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.GET_TENANT_BY_DOMAIN_NAME;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.GET_TENANT_BY_GCP_TENANT_ID;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.GET_TENANT_BY_ID;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.GET_TENANTS_BY_TIER_ID;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.INSERT_TENANT;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.UPDATE_TENANT;
import static org.dreamhorizon.pulseserver.dao.tenant.TenantQueries.UPDATE_TENANT_TIER;

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
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantDao {
  private final MysqlClient mysqlClient;

  private static final int DEFAULT_TIER_ID = 1; // Free tier

  public Single<Tenant> createTenant(Tenant tenant) {
    return createTenant(tenant, DEFAULT_TIER_ID);
  }

  public Single<Tenant> createTenant(Tenant tenant, int tierId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    Tuple tuple = Tuple.tuple()
        .addString(tenant.getTenantId())
        .addString(tenant.getName())
        .addString(tenant.getDescription())
        .addString(tenant.getGcpTenantId())
        .addString(tenant.getDomainName())
        .addInteger(tierId)
        .addBoolean(true);
    return pool.preparedQuery(INSERT_TENANT)
        .rxExecute(tuple)
        .map(result -> {
          log.info("Created tenant: {} with tier: {}", tenant.getTenantId(), tierId);
          return Tenant.builder()
              .tenantId(tenant.getTenantId())
              .name(tenant.getName())
              .description(tenant.getDescription())
              .gcpTenantId(tenant.getGcpTenantId())
              .domainName(tenant.getDomainName())
              .tierId(tierId)
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

  public Single<Tenant> updateTenant(Tenant tenant) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(UPDATE_TENANT)
        .rxExecute(Tuple.of(tenant.getName(), tenant.getDescription(), tenant.getTenantId()))
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException("Tenant not found: " + tenant.getTenantId()));
          }
          log.info("Updated tenant: {}", tenant.getTenantId());
          // Return the updated tenant (we know the values since we just set them)
          return Single.just(tenant);
        })
        .doOnError(error -> log.error("Failed to update tenant: {}", tenant.getTenantId(), error));
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

  public Flowable<Tenant> getTenantsByTierId(int tierId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_TENANTS_BY_TIER_ID)
        .rxExecute(Tuple.of(tierId))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToTenant((Row) row)))
        .doOnError(error -> log.error("Failed to fetch tenants by tierId: {}", tierId, error));
  }

  public Single<Tenant> updateTenantTier(Tenant tenant, int tierId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(UPDATE_TENANT_TIER)
        .rxExecute(Tuple.of(tierId, tenant.getTenantId()))
        .flatMap(result -> {
          if (result.rowCount() == 0) {
            return Single.error(new RuntimeException("Tenant not found: " + tenant.getTenantId()));
          }
          log.info("Updated tenant {} tier to {}", tenant.getTenantId(), tierId);
          // Return the updated tenant with new tier
          return Single.just(Tenant.builder()
              .tenantId(tenant.getTenantId())
              .name(tenant.getName())
              .description(tenant.getDescription())
              .gcpTenantId(tenant.getGcpTenantId())
              .domainName(tenant.getDomainName())
              .tierId(tierId)
              .isActive(tenant.getIsActive())
              .createdAt(tenant.getCreatedAt())
              .build());
        })
        .doOnError(error -> log.error("Failed to update tier for tenant: {}", tenant.getTenantId(), error));
  }

  private Tenant mapRowToTenant(Row row) {
    return Tenant.builder()
        .tenantId(row.getString("tenant_id"))
        .name(row.getString("name"))
        .description(row.getString("description"))
        .tierId(row.getInteger("tier_id"))
        .isActive(row.getBoolean("is_active"))
        .createdAt(row.getLocalDateTime("created_at") != null ? row.getLocalDateTime("created_at").toString() : null)
        .updatedAt(row.getLocalDateTime("updated_at") != null ? row.getLocalDateTime("updated_at").toString() : null)
        .gcpTenantId(row.getString("gcp_tenant_id"))
        .domainName(row.getString("domain_name"))
        .build();
  }
}
