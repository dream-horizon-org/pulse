package org.dreamhorizon.pulseserver.service.tenant;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseTenantConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.ClickhouseCredentialsDao;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseTenantCredentialAudit;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateCredentialsRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.TenantInfo;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateCredentialsRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateTenantRequest;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantService {

  private final TenantDao tenantDao;
  private final ClickhouseCredentialsDao credentialsDao;
  private final ClickhouseTenantConnectionPoolManager poolManager;
  private final org.dreamhorizon.pulseserver.service.OpenFgaService openFgaService;

  public Single<Tenant> createTenant(CreateTenantRequest request) {
    log.info("Creating tenant: {}", request.getTenantId());

    Tenant tenant = Tenant.builder()
        .tenantId(request.getTenantId())
        .name(request.getName())
        .description(request.getDescription())
        .gcpTenantId(request.getGcpTenantId())
        .domainName(request.getDomainName())
        .isActive(true)
        .build();

    return tenantDao.createTenant(tenant)
        .doOnSuccess(t -> log.info("Tenant created: {}", t.getTenantId()))
        .doOnError(error -> log.error("Failed to create tenant: {}", request.getTenantId(), error));
  }

  public Maybe<Tenant> getTenant(String tenantId) {
    return tenantDao.getTenantById(tenantId)
        .doOnError(error -> log.error("Failed to get tenant: {}", tenantId, error));
  }

  public Flowable<Tenant> getAllActiveTenants() {
    return tenantDao.getAllActiveTenants()
        .doOnError(error -> log.error("Failed to get all active tenants", error));
  }

  public Flowable<Tenant> getAllTenants() {
    return tenantDao.getAllTenants()
        .doOnError(error -> log.error("Failed to get all tenants", error));
  }

  public Single<Tenant> updateTenant(UpdateTenantRequest request) {
    log.info("Updating tenant: {}", request.getTenantId());

    Tenant tenant = Tenant.builder()
        .tenantId(request.getTenantId())
        .name(request.getName())
        .description(request.getDescription())
        .build();

    return tenantDao.updateTenant(tenant)
        .doOnSuccess(t -> log.info("Tenant updated: {}", t.getTenantId()))
        .doOnError(error -> log.error("Failed to update tenant: {}", request.getTenantId(), error));
  }

  public Completable deactivateTenant(String tenantId) {
    log.info("Deactivating tenant: {}", tenantId);

    return tenantDao.deactivateTenant(tenantId)
        .doOnComplete(() -> log.info("Tenant deactivated: {}", tenantId))
        .doOnError(error -> log.error("Failed to deactivate tenant: {}", tenantId, error));
  }

  public Completable activateTenant(String tenantId) {
    log.info("Activating tenant: {}", tenantId);

    return tenantDao.activateTenant(tenantId)
        .doOnComplete(() -> log.info("Tenant activated: {}", tenantId))
        .doOnError(error -> log.error("Failed to activate tenant: {}", tenantId, error));
  }

  public Completable deleteTenant(String tenantId) {
    log.info("Deleting tenant: {}", tenantId);

    return tenantDao.deleteTenant(tenantId)
        .doOnComplete(() -> log.info("Tenant deleted: {}", tenantId))
        .doOnError(error -> log.error("Failed to delete tenant: {}", tenantId, error));
  }

  public Single<Boolean> tenantExists(String tenantId) {
    return tenantDao.tenantExists(tenantId);
  }

  public Single<TenantInfo> createClickhouseCredentials(CreateCredentialsRequest request, String performedBy) {
    String tenantId = request.getTenantId();
    log.info("Creating ClickHouse credentials for tenant: {} by user: {}", tenantId, performedBy);

    String plainPassword = request.getClickhousePassword();

    return credentialsDao.saveTenantCredentials(tenantId, plainPassword)
        .flatMap(credentials -> {
          // Initialize connection pool
          try {
            poolManager.getPoolForTenant(tenantId, credentials.getClickhouseUsername(), plainPassword);
            log.info("Connection pool created for tenant: {}", tenantId);
          } catch (Exception e) {
            log.warn("Failed to create connection pool for tenant: {}, will be created on first query", tenantId, e);
          }

          // Audit log
          JsonObject auditDetails = new JsonObject()
              .put("clickhouseUsername", credentials.getClickhouseUsername())
              .put("action", "ClickHouse credentials created");

          return credentialsDao.insertAuditLog(tenantId, TenantAuditAction.CREDENTIALS_CREATED, performedBy, auditDetails)
              .andThen(Single.just(TenantInfo.builder()
                  .tenantId(tenantId)
                  .clickhouseUsername(credentials.getClickhouseUsername())
                  .isActive(true)
                  .message("ClickHouse credentials created. Please create ClickHouse user and row policies.")
                  .build()));
        })
        .doOnSuccess(info -> log.info("ClickHouse credentials created for tenant: {}", tenantId))
        .doOnError(error -> log.error("Failed to create ClickHouse credentials for tenant: {}", tenantId, error));
  }

  public Single<ClickhouseCredentials> getClickhouseCredentials(String tenantId) {
    return credentialsDao.getCredentialsByTenantId(tenantId)
        .doOnError(error -> log.error("Failed to get ClickHouse credentials: {}", tenantId, error));
  }

  public Flowable<ClickhouseCredentials> getAllActiveClickhouseCredentials() {
    return credentialsDao.getAllActiveTenantCredentials()
        .doOnError(error -> log.error("Failed to get all active ClickHouse credentials", error));
  }

  public Single<TenantInfo> updateClickhouseCredentials(UpdateCredentialsRequest request, String performedBy) {
    String tenantId = request.getTenantId();
    log.info("Updating ClickHouse credentials for tenant: {} by user: {}", tenantId, performedBy);

    String newPassword = request.getNewPassword();

    return credentialsDao.updateTenantCredentials(tenantId, newPassword)
        .flatMap(credentials -> {
          // Recreate connection pool with new credentials
          try {
            poolManager.closePoolForTenant(tenantId);
            poolManager.getPoolForTenant(tenantId, credentials.getClickhouseUsername(), newPassword);
            log.info("Connection pool recreated for tenant: {}", tenantId);
          } catch (Exception e) {
            log.warn("Failed to recreate connection pool for tenant: {}", tenantId, e);
          }

          // Audit log
          JsonObject auditDetails = new JsonObject()
              .put("action", "ClickHouse credentials updated")
              .put("reason", request.getReason() != null ? request.getReason() : "Manual update");

          return credentialsDao.insertAuditLog(tenantId, TenantAuditAction.CREDENTIALS_UPDATED, performedBy, auditDetails)
              .andThen(Single.just(TenantInfo.builder()
                  .tenantId(tenantId)
                  .clickhouseUsername(credentials.getClickhouseUsername())
                  .isActive(credentials.getIsActive())
                  .message("ClickHouse credentials updated. Update ClickHouse user password to match.")
                  .build()));
        })
        .doOnSuccess(info -> log.info("ClickHouse credentials updated for tenant: {}", tenantId))
        .doOnError(error -> log.error("Failed to update ClickHouse credentials for tenant: {}", tenantId, error));
  }

  public Completable deactivateClickhouseCredentials(String tenantId, String performedBy) {
    log.info("Deactivating ClickHouse credentials for tenant: {} by user: {}", tenantId, performedBy);

    JsonObject auditDetails = new JsonObject()
        .put("action", "ClickHouse credentials deactivated");

    return credentialsDao.deactivateTenantCredentials(tenantId)
        .andThen(Completable.fromAction(() -> {
          try {
            poolManager.closePoolForTenant(tenantId);
            log.info("Connection pool closed for tenant: {}", tenantId);
          } catch (Exception e) {
            log.warn("Failed to close pool during deactivation: {}", tenantId, e);
          }
        }))
        .andThen(credentialsDao.insertAuditLog(tenantId, TenantAuditAction.CREDENTIALS_DEACTIVATED, performedBy, auditDetails))
        .doOnComplete(() -> log.info("ClickHouse credentials deactivated for tenant: {}", tenantId))
        .doOnError(error -> log.error("Failed to deactivate ClickHouse credentials for tenant: {}", tenantId, error));
  }

  public Single<TenantInfo> reactivateClickhouseCredentials(String tenantId, String performedBy) {
    log.info("Reactivating ClickHouse credentials for tenant: {} by user: {}", tenantId, performedBy);

    return credentialsDao.reactivateTenantCredentials(tenantId)
        .andThen(credentialsDao.getCredentialsByTenantId(tenantId))
        .flatMap(credentials -> {
          // Re-initialize connection pool
          try {
            poolManager.getPoolForTenant(tenantId, credentials.getClickhouseUsername(), credentials.getClickhousePassword());
            log.info("Connection pool created for reactivated tenant: {}", tenantId);
          } catch (Exception e) {
            log.warn("Failed to create pool during reactivation: {}", tenantId, e);
          }

          // Audit log
          JsonObject auditDetails = new JsonObject()
              .put("action", "ClickHouse credentials reactivated");

          return credentialsDao.insertAuditLog(tenantId, TenantAuditAction.CREDENTIALS_REACTIVATED, performedBy, auditDetails)
              .andThen(Single.just(TenantInfo.builder()
                  .tenantId(tenantId)
                  .clickhouseUsername(credentials.getClickhouseUsername())
                  .isActive(true)
                  .message("ClickHouse credentials reactivated.")
                  .build()));
        })
        .doOnSuccess(info -> log.info("ClickHouse credentials reactivated for tenant: {}", tenantId))
        .doOnError(error -> log.error("Failed to reactivate ClickHouse credentials for tenant: {}", tenantId, error));
  }

  /**
   * Get audit history for ClickHouse credentials.
   * Note: Despite the method accepting projectId, the audit table tracks project-level credentials.
   * 
   * @param projectId Project ID to get audit history for
   * @return Flowable of audit log entries
   */
  public Flowable<ClickhouseTenantCredentialAudit> getCredentialsAuditHistory(String projectId) {
    return credentialsDao.getAuditLogsByProjectId(projectId)
        .doOnError(error -> log.error("Failed to get audit history for project: {}", projectId, error));
  }

  public Flowable<ClickhouseTenantCredentialAudit> getRecentCredentialsAuditLogs(int limit) {
    return credentialsDao.getRecentAuditLogs(limit)
        .doOnError(error -> log.error("Failed to get recent audit logs", error));
  }

  public Completable refreshConnectionPool(String tenantId) {
    log.info("Refreshing connection pool for tenant: {}", tenantId);

    return credentialsDao.getCredentialsByTenantId(tenantId)
        .flatMapCompletable(credentials -> {
          try {
            poolManager.closePoolForTenant(tenantId);
            poolManager.getPoolForTenant(tenantId, credentials.getClickhouseUsername(), credentials.getClickhousePassword());
            log.info("Connection pool refreshed for tenant: {}", tenantId);
            return Completable.complete();
          } catch (Exception e) {
            log.error("Failed to refresh pool for tenant: {}", tenantId, e);
            return Completable.error(e);
          }
        })
        .doOnError(error -> log.error("Failed to refresh pool for tenant: {}", tenantId, error));
  }

  public ClickhouseTenantConnectionPoolManager.PoolStatistics getPoolStatistics(String tenantId) {
    return poolManager.getPoolStatistics(tenantId);
  }
  
  /**
   * Create tenant for a specific user during onboarding flow.
   * This method:
   * 1. Creates the tenant record in MySQL
   * 2. Assigns the user as admin in OpenFGA
   * 
   * @param name Tenant name
   * @param description Tenant description
   * @param userId User ID (who will be the admin)
   * @return Single with created Tenant
   */
  public Single<Tenant> createTenantForUser(String name, String description, String userId) {
    String tenantId = "tenant-" + java.util.UUID.randomUUID();
    
    log.info("Creating tenant: tenantId={}, name={}, userId={}", tenantId, name, userId);
    
    Tenant tenant = Tenant.builder()
        .tenantId(tenantId)
        .name(name)
        .description(description)
        .isActive(true)
        .build();
    
    return tenantDao.createTenant(tenant)
        .flatMap(created -> 
            openFgaService.assignTenantRole(userId, tenantId, "admin")
                .andThen(Single.just(created))
        )
        .doOnSuccess(t -> log.info("Tenant created and user assigned as admin: tenantId={}, userId={}", tenantId, userId))
        .doOnError(error -> log.error("Failed to create tenant for user: tenantId={}, userId={}", tenantId, userId, error));
  }
}
