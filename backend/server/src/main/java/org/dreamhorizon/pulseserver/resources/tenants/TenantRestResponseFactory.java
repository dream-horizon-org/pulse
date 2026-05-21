package org.dreamhorizon.pulseserver.resources.tenants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantRestResponse;
import org.dreamhorizon.pulseserver.service.tier.TierService;

/**
 * Builds {@link TenantRestResponse} including tier name resolved from {@link Tenant#getTierId()}.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantRestResponseFactory {

  private static final TenantMapper MAPPER = TenantMapper.INSTANCE;

  private final TierService tierService;

  /**
   * Maps a tenant row to REST response and sets {@code tier} from the tiers table (defaults to "free").
   */
  public Single<TenantRestResponse> toResponseWithTier(Tenant tenant) {
    TenantRestResponse response = MAPPER.toTenantRestResponse(tenant);
    return tierService
        .getTierNameById(tenant.getTierId())
        .switchIfEmpty(Maybe.just("free"))
        .map(
            tierName -> {
              response.setTier(tierName);
              return response;
            })
        .toSingle();
  }
}
