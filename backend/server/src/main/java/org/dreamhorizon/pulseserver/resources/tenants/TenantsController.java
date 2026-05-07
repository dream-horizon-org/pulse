package org.dreamhorizon.pulseserver.resources.tenants;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.jsonwebtoken.Claims;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.tenants.models.CreateInternalTenantRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.StatusRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantListRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.UpdateTenantRestRequest;
import org.dreamhorizon.pulseserver.rest.exception.ForbiddenOperationException;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/tenants")
public class TenantsController {

  private static final TenantMapper mapper = TenantMapper.INSTANCE;

  private final TenantService tenantService;
  private final JwtService jwtService;
  private final Provider<OpenFgaService> openFgaServiceProvider;

  private String extractUserIdFromAuthorization(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return null;
    }
    String token = authorization.substring("Bearer ".length()).trim();
    if (token.isEmpty() || !jwtService.isAccessToken(token)) {
      return null;
    }
    try {
      Claims claims = jwtService.verifyToken(token);
      String userId = claims.getSubject();
      return userId == null || userId.isBlank() ? null : userId;
    } catch (Exception e) {
      log.debug("Unable to parse caller token for tenant role enrichment: {}", e.getMessage());
      return null;
    }
  }

  private Single<List<TenantRestResponse>> enrichTenantRoles(List<TenantRestResponse> tenants, String userId) {
    if (userId == null || userId.isBlank()) {
      return Single.just(tenants);
    }

    OpenFgaService openFgaService = openFgaServiceProvider.get();
    if (openFgaService == null || !openFgaService.isEnabled()) {
      return Single.just(tenants);
    }

    return io.reactivex.rxjava3.core.Flowable.fromIterable(tenants)
        .concatMapSingle(
            tenant ->
                openFgaService
                    .getUserTenantRole(userId, tenant.getTenantId())
                    .map(
                        role -> {
                          tenant.setTenantRole(role.orElse(null));
                          return tenant;
                        })
                    .onErrorReturnItem(tenant))
        .toList();
  }


  @GET
  @Path("/{tenantId}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TenantRestResponse>> getTenant(
      @NotNull @PathParam("tenantId") String tenantId
  ) {
    return tenantService.getTenant(tenantId)
        .map(mapper::toTenantRestResponse)
        .switchIfEmpty(io.reactivex.rxjava3.core.Single.error(
            new RuntimeException("Tenant not found: " + tenantId)))
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TenantRestResponse>> createTenant(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @NotNull @Valid CreateInternalTenantRestRequest request
  ) {
    return Single.defer(() -> {
      String userId = extractUserIdFromAuthorization(authorization);
      if (userId == null || userId.isBlank()) {
        return Single.error(ServiceError.UNAUTHORISED.getCustomException("Missing or invalid Authorization header"));
      }

      OpenFgaService openFgaService = openFgaServiceProvider.get();
      if (openFgaService == null || !openFgaService.isEnabled()) {
        return Single.error(
            ServiceError.INTERNAL_SERVER_ERROR.getCustomException(
                "OpenFGA is not available",
                "OpenFGA is disabled or not initialized",
                503));
      }

      return Single.zip(
              openFgaService.isSuperAdmin(userId),
              openFgaService.isInternalViewer(userId),
              (isSuperadmin, isInternalViewer) -> Boolean.TRUE.equals(isSuperadmin) || Boolean.TRUE.equals(isInternalViewer))
          .flatMap(isSystemRole -> {
            if (!Boolean.TRUE.equals(isSystemRole)) {
              return Single.error(new ForbiddenOperationException("Only superadmin or internal_viewer can create tenants"));
            }

            String tenantId = "tenant-" + UUID.randomUUID().toString().replace("-", "");
            CreateTenantRequest createTenantRequest = CreateTenantRequest.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .gcpTenantId(null)
                .domainName(null)
                .build();
            return tenantService.createTenant(createTenantRequest);
          })
          .map(mapper::toTenantRestResponse);
    }).to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TenantListRestResponse>> getAllTenants(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @QueryParam("activeOnly") @DefaultValue("true") Boolean activeOnly
  ) {
    String callerUserId = extractUserIdFromAuthorization(authorization);
    var flowable = activeOnly
        ? tenantService.getAllActiveTenants()
        : tenantService.getAllTenants();

    return flowable
        .toList()
        .map(mapper::toTenantRestResponseList)
        .flatMap(tenants -> enrichTenantRoles(tenants, callerUserId))
        .map(
            tenants ->
                TenantListRestResponse.builder()
                    .tenants(tenants)
                    .totalCount(tenants.size())
                    .build())
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{tenantId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TenantRestResponse>> updateTenant(
      @NotNull @PathParam("tenantId") String tenantId,
      @NotNull @Valid UpdateTenantRestRequest request
  ) {
    return tenantService.updateTenant(mapper.toUpdateTenantRequest(tenantId, request))
        .map(mapper::toTenantRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }


  @PUT
  @Path("/{tenantId}/deactivate")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<StatusRestResponse>> deactivateTenant(
      @NotNull @PathParam("tenantId") String tenantId
  ) {
    return tenantService.deactivateTenant(tenantId)
        .andThen(io.reactivex.rxjava3.core.Single.just(StatusRestResponse.builder()
            .success(true)
            .message("Tenant deactivated successfully")
            .build()))
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{tenantId}/activate")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<StatusRestResponse>> activateTenant(
      @NotNull @PathParam("tenantId") String tenantId
  ) {
    return tenantService.activateTenant(tenantId)
        .andThen(io.reactivex.rxjava3.core.Single.just(StatusRestResponse.builder()
            .success(true)
            .message("Tenant activated successfully")
            .build()))
        .to(RestResponse.jaxrsRestHandler());
  }
}
