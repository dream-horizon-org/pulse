package org.dreamhorizon.pulseserver.service.tenant;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateTenantRequest;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantService {

  private final TenantDao tenantDao;
  private final ClickhouseProjectConnectionPoolManager poolManager;
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
  
}
