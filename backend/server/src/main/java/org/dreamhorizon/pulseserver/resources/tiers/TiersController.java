package org.dreamhorizon.pulseserver.resources.tiers;

import com.google.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierPublicListRestResponse;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierPublicRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.tier.TierService;

/**
 * Controller for tier management - public endpoints.
 * 
 * Public endpoints (for dashboard):
 * - GET /v1/tiers - Get all active tiers (simplified info)
 * - GET /v1/tiers/{tierId} - Get tier details (simplified info)
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/tiers")
public class TiersController {

  private static final TierMapper mapper = TierMapper.INSTANCE;

  private final TierService tierService;

  /**
   * Get all active tiers (public, simplified info for dashboard).
   */
  @GET
  @Path("")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TierPublicListRestResponse>> getPublicTiers() {
    return tierService.getAllActiveTiersPublic()
        .toList()
        .map(mapper::toTierPublicListRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Get tier details by ID (public, simplified info for dashboard).
   */
  @GET
  @Path("/{tierId}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TierPublicRestResponse>> getPublicTier(
      @NotNull @PathParam("tierId") Integer tierId
  ) {
    return tierService.getTierPublicById(tierId)
        .map(mapper::toTierPublicRestResponse)
        .switchIfEmpty(io.reactivex.rxjava3.core.Single.error(
            new RuntimeException("Tier not found: " + tierId)))
        .to(RestResponse.jaxrsRestHandler());
  }
}
