package org.dreamhorizon.pulseserver.resources.tiers;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.tiers.models.CreateTierRestRequest;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierListRestResponse;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierPublicListRestResponse;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierPublicRestResponse;
import org.dreamhorizon.pulseserver.resources.tiers.models.TierRestResponse;
import org.dreamhorizon.pulseserver.resources.tiers.models.UpdateTierRestRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.tier.TierService;

/**
 * Controller for tier management.
 * 
 * Public endpoints (for dashboard):
 * - GET /v1/tiers - Get all active tiers (simplified info)
 * - GET /v1/tiers/{tierId} - Get tier details (simplified info)
 * 
 * Internal endpoints:
 * - POST /internal/v1/tiers - Create a new tier
 * - PUT /internal/v1/tiers/{tierId} - Update an existing tier
 * - PUT /internal/v1/tiers/{tierId}/deactivate - Deactivate a tier
 * - PUT /internal/v1/tiers/{tierId}/activate - Reactivate a tier
 * - GET /internal/v1/tiers - Get all tiers including inactive (full info)
 * - GET /internal/v1/tiers/{tierId} - Get tier details (full info)
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("")
public class TiersController {

  private static final TierMapper mapper = TierMapper.INSTANCE;

  private final TierService tierService;

  // ==================== PUBLIC ENDPOINTS ====================

  /**
   * Get all active tiers (public, simplified info for dashboard).
   */
  @GET
  @Path("/v1/tiers")
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
  @Path("/v1/tiers/{tierId}")
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

  // ==================== INTERNAL ENDPOINTS ====================

  /**
   * Create a new tier (internal only).
   */
  @POST
  @Path("/internal/v1/tiers")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TierRestResponse>> createTier(
      @NotNull @Valid CreateTierRestRequest request
  ) {
    return tierService.createTier(mapper.toCreateTierRequest(request))
        .map(mapper::toTierRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Update an existing tier (internal only).
   */
  @PUT
  @Path("/internal/v1/tiers/{tierId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TierRestResponse>> updateTier(
      @NotNull @PathParam("tierId") Integer tierId,
      @NotNull @Valid UpdateTierRestRequest request
  ) {
    return tierService.updateTier(mapper.toUpdateTierRequest(tierId, request))
        .map(mapper::toTierRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Deactivate a tier (internal only).
   * New tenants cannot be assigned to this tier, but existing tenants are not affected.
   */
  @PUT
  @Path("/internal/v1/tiers/{tierId}/deactivate")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TierRestResponse>> deactivateTier(
      @NotNull @PathParam("tierId") Integer tierId
  ) {
    return tierService.deactivateTier(tierId)
        .map(mapper::toTierRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Reactivate a previously deactivated tier (internal only).
   */
  @PUT
  @Path("/internal/v1/tiers/{tierId}/activate")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TierRestResponse>> activateTier(
      @NotNull @PathParam("tierId") Integer tierId
  ) {
    return tierService.activateTier(tierId)
        .map(mapper::toTierRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Get all tiers including inactive (internal  only, full info).
   */
  @GET
  @Path("/internal/v1/tiers")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TierListRestResponse>> getAllTiers(
      @QueryParam("activeOnly") @DefaultValue("false") Boolean activeOnly
  ) {
    var flowable = activeOnly
        ? tierService.getAllTiers().filter(tier -> tier.getIsActive())
        : tierService.getAllTiers();

    return flowable
        .toList()
        .map(mapper::toTierListRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Get tier details by ID (internal  only, full info).
   */
  @GET
  @Path("/internal/v1/tiers/{tierId}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TierRestResponse>> getTier(
      @NotNull @PathParam("tierId") Integer tierId
  ) {
    return tierService.getTierById(tierId)
        .map(mapper::toTierRestResponse)
        .switchIfEmpty(io.reactivex.rxjava3.core.Single.error(
            new RuntimeException("Tier not found: " + tierId)))
        .to(RestResponse.jaxrsRestHandler());
  }
}

