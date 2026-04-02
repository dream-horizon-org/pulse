package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.*;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelService;

import java.util.concurrent.CompletionStage;

@Slf4j
@Path("/v1/funnels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelsController {

  private final FunnelService funnelService;

  @GET
  public CompletionStage<Response<FunnelDefinitionListResponse>> listFunnels(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @BeanParam FunnelListQueryParams query) {
    return funnelService
      .list(projectId, query)
      .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{id}")
  public CompletionStage<Response<FunnelDefinitionResponse>> getFunnel(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id) {
    return funnelService.get(projectId, id).to(RestResponse.jaxrsRestHandler());
  }

  @POST
  public CompletionStage<Response<Long>> createFunnel(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @HeaderParam("user-email") String userEmail,
    @NotNull @Valid CreateFunnelDefinitionRequest request) {
    return funnelService
      .create(projectId, request, userEmail)
      .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{id}")
  public CompletionStage<Response<String>> updateFunnel(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id,
    @NotNull @Valid UpdateFunnelDefinitionRequest request) {
    return funnelService.update(projectId, id, request)
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }

  @DELETE
  @Path("/{id}")
  public CompletionStage<Response<String>> deleteFunnel(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id) {
    return funnelService
      .delete(projectId, id)
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }
}
