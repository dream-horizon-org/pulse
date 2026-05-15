package org.dreamhorizon.pulseserver.service.tenant;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.dto.request.ReqUserInfo;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.ProjectService;
import org.dreamhorizon.pulseserver.service.tenant.dto.TenantWithProjectResult;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateTenantRequest;
import org.dreamhorizon.pulseserver.service.tier.TierService;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantService {

  private final TenantDao tenantDao;
  private final ClickhouseProjectConnectionPoolManager poolManager;
  private final org.dreamhorizon.pulseserver.service.OpenFgaService openFgaService;
  private final TierService tierService;
  private final ProjectService projectService;

  /**
   * Atomically provisions a tenant + first project in one RxJava chain.
   * Called by both onboarding and the admin dashboard — neither flow can diverge.
   *
   * @param ownerInfo user info for the caller who becomes tenant admin and project admin
   * @param tenantName display name for the new tenant
   * @param projectName display name for the first project
   * @param tenantDescription optional tenant description (may be null)
   * @param projectDescription optional project description (may be null)
   * @return result containing tenantId, projectId, and raw API key
   */
  public Single<TenantWithProjectResult> createTenantWithProject(
      ReqUserInfo ownerInfo,
      String tenantName,
      String projectName,
      String tenantDescription,
      String projectDescription) {

    String tenantId = "tenant-" + UUID.randomUUID().toString();
    log.info("Creating tenant with project: ownerId={}, tenantId={}, tenantName={}, projectName={}",
        ownerInfo.getUserId(), tenantId, tenantName, projectName);

    CreateTenantRequest tenantRequest = CreateTenantRequest.builder()
        .tenantId(tenantId)
        .name(tenantName)
        .description(tenantDescription)
        .gcpTenantId(null)
        .domainName(null)
        .build();

    return createTenant(tenantRequest)
        .flatMap(tenant ->
            projectService.createProject(tenantId, projectName, projectDescription, ownerInfo)
                .flatMap(creationResult -> {
                  if (openFgaService != null && openFgaService.isEnabled()) {
                    return openFgaService.assignTenantRole(ownerInfo.getUserId(), tenantId, "admin")
                        .andThen(Single.just(creationResult));
                  }
                  return Single.just(creationResult);
                })
                .map(creationResult -> {
                  TenantWithProjectResult result = new TenantWithProjectResult();
                  result.setTenantId(tenantId);
                  result.setProjectId(creationResult.getProject().getProjectId());
                  result.setRawApiKey(creationResult.getRawApiKey());
                  return result;
                })
        )
        .doOnSuccess(result ->
            log.info("Tenant with project created: tenantId={}, projectId={}",
                result.getTenantId(), result.getProjectId())
        )
        .doOnError(error ->
            log.error("Failed to create tenant with project: ownerId={}, tenantName={}",
                ownerInfo.getUserId(), tenantName, error)
        );
  }

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
        .flatMap(t -> {
          if (openFgaService != null && openFgaService.isEnabled()) {
            return openFgaService.linkTenantToSystem(t.getTenantId()).toSingleDefault(t);
          }
          return Single.just(t);
        })
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

  public Single<Tenant> updateTenantTier(String tenantId, int tierId) {
    log.info("Updating tier for tenant {} to {}", tenantId, tierId);

    return tenantDao.getTenantById(tenantId)
        .switchIfEmpty(Single.error(ServiceError.NOT_FOUND.getCustomException("Tenant not found: " + tenantId)))
        .flatMap(tenant ->
            tierService.getTierById(tierId)
                .switchIfEmpty(Single.error(ServiceError.NOT_FOUND.getCustomException("Tier not found: " + tierId)))
                .flatMap(tier -> {
                  if (!tier.getIsActive()) {
                    return Single.error(ServiceError.INVALID_REQUEST_PARAM.getCustomException("Tier is not active: " + tierId));
                  }
                  return tenantDao.updateTenantTier(tenant, tierId);
                })
        )
        .doOnSuccess(t -> log.info("Updated tier for tenant {} to {}", tenantId, tierId))
        .doOnError(error -> log.error("Failed to update tier for tenant: {}", tenantId, error));
  }

}
