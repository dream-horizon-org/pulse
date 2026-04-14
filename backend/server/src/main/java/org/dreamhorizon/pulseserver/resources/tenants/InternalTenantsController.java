package org.dreamhorizon.pulseserver.resources.tenants;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.tenants.models.StatusRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.UpdateTenantTierRestRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;

/**
 * Controller for tenant management - internal endpoints.
 *
 * Internal endpoints:
 * - PUT /internal/v1/tenants/{tenantId}/tier - Update the tier of a tenant
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/internal/v1/tenants")
public class InternalTenantsController {

  private final TenantService tenantService;

  /**
   * Update the tier of a tenant (internal only).
   * The target tier must exist and be active.
   */
  @PUT
  @Path("/{tenantId}/tier")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<StatusRestResponse>> updateTenantTier(
      @NotNull @PathParam("tenantId") String tenantId,
      @NotNull @Valid UpdateTenantTierRestRequest request
  ) {
    return tenantService.updateTenantTier(tenantId, request.getTierId())
        .map(tenant -> StatusRestResponse.builder()
            .success(true)
            .message("Tenant tier updated successfully")
            .build())
        .to(RestResponse.jaxrsRestHandler());
  }
}
