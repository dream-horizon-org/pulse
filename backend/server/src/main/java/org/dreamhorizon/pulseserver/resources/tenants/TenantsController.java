package org.dreamhorizon.pulseserver.resources.tenants;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
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
import org.dreamhorizon.pulseserver.resources.tenants.models.AuditListRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.CreateCredentialsRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.CreateTenantRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.CredentialsRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.StatusRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantListRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.UpdateCredentialsRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.UpdateTenantRestRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/tenants")
public class TenantsController {

  private static final TenantMapper mapper = TenantMapper.INSTANCE;

  private final TenantService tenantService;


  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TenantRestResponse>> createTenant(
      @NotNull @Valid CreateTenantRestRequest request
  ) {
    return tenantService.createTenant(mapper.toCreateTenantRequest(request))
        .map(mapper::toTenantRestResponse)
        .to(RestResponse.jaxrsRestHandler());
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

  @GET
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TenantListRestResponse>> getAllTenants(
      @QueryParam("activeOnly") @DefaultValue("true") Boolean activeOnly
  ) {
    var flowable = activeOnly
        ? tenantService.getAllActiveTenants()
        : tenantService.getAllTenants();

    return flowable
        .toList()
        .map(mapper::toTenantListRestResponse)
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

  @DELETE
  @Path("/{tenantId}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<StatusRestResponse>> deleteTenant(
      @NotNull @PathParam("tenantId") String tenantId
  ) {
    return tenantService.deleteTenant(tenantId)
        .andThen(io.reactivex.rxjava3.core.Single.just(StatusRestResponse.builder()
            .success(true)
            .message("Tenant deleted successfully")
            .build()))
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


  @POST
  @Path("/{tenantId}/credentials")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CredentialsRestResponse>> createCredentials(
      @NotNull @PathParam("tenantId") String tenantId,
      @NotNull @HeaderParam("user-email") String userEmail,
      @NotNull @Valid CreateCredentialsRestRequest request
  ) {
    return tenantService.createClickhouseCredentials(
            mapper.toCreateCredentialsRequest(tenantId, request), userEmail)
        .map(mapper::toCredentialsRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{tenantId}/credentials")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CredentialsRestResponse>> getCredentials(
      @NotNull @PathParam("tenantId") String tenantId
  ) {
    return tenantService.getClickhouseCredentials(tenantId)
        .map(mapper::toCredentialsRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{tenantId}/credentials")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CredentialsRestResponse>> updateCredentials(
      @NotNull @PathParam("tenantId") String tenantId,
      @NotNull @HeaderParam("user-email") String userEmail,
      @NotNull @Valid UpdateCredentialsRestRequest request
  ) {
    return tenantService.updateClickhouseCredentials(
            mapper.toUpdateCredentialsRequest(tenantId, request), userEmail)
        .map(mapper::toCredentialsRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{tenantId}/credentials/deactivate")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<StatusRestResponse>> deactivateCredentials(
      @NotNull @PathParam("tenantId") String tenantId,
      @NotNull @HeaderParam("user-email") String userEmail
  ) {
    return tenantService.deactivateClickhouseCredentials(tenantId, userEmail)
        .andThen(io.reactivex.rxjava3.core.Single.just(StatusRestResponse.builder()
            .success(true)
            .message("ClickHouse credentials deactivated successfully")
            .build()))
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{tenantId}/credentials/reactivate")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CredentialsRestResponse>> reactivateCredentials(
      @NotNull @PathParam("tenantId") String tenantId,
      @NotNull @HeaderParam("user-email") String userEmail
  ) {
    return tenantService.reactivateClickhouseCredentials(tenantId, userEmail)
        .map(mapper::toCredentialsRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }


  @GET
  @Path("/{tenantId}/credentials/audit")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AuditListRestResponse>> getCredentialsAuditHistory(
      @NotNull @PathParam("tenantId") String tenantId
  ) {
    return tenantService.getCredentialsAuditHistory(tenantId)
        .toList()
        .map(mapper::toAuditListRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/credentials/audit/recent")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AuditListRestResponse>> getRecentAuditLogs(
      @QueryParam("limit") @DefaultValue("50") Integer limit
  ) {
    return tenantService.getRecentCredentialsAuditLogs(limit)
        .toList()
        .map(mapper::toAuditListRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }


  @POST
  @Path("/{tenantId}/credentials/pool/refresh")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<StatusRestResponse>> refreshConnectionPool(
      @NotNull @PathParam("tenantId") String tenantId
  ) {
    return tenantService.refreshConnectionPool(tenantId)
        .andThen(io.reactivex.rxjava3.core.Single.just(StatusRestResponse.builder()
            .success(true)
            .message("Connection pool refreshed successfully")
            .build()))
        .to(RestResponse.jaxrsRestHandler());
  }
}
