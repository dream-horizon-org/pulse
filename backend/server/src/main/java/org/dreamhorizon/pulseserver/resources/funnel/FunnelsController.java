package org.dreamhorizon.pulseserver.resources.funnel;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.funnel.models.CreateFunnelDefinitionRequest;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelDefinitionListResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelDefinitionResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelListQueryParams;
import org.dreamhorizon.pulseserver.resources.funnel.models.UpdateFunnelDefinitionRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.funnel.FunnelDefinitionService;

@Slf4j
@Path("/v1/funnels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelsController {

  private final FunnelDefinitionService funnelDefinitionService;

  @GET
  public CompletionStage<Response<FunnelDefinitionListResponse>> listFunnels(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
      @BeanParam FunnelListQueryParams query) {
    return funnelDefinitionService
        .list(projectId, query)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{id}")
  public CompletionStage<Response<FunnelDefinitionResponse>> getFunnel(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
      @PathParam("id") long id) {
    return funnelDefinitionService.get(projectId, id).to(RestResponse.jaxrsRestHandler());
  }

  @POST
  public CompletionStage<Response<Long>> createFunnel(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
      @HeaderParam("user-email") String userEmail,
      @NotNull @Valid CreateFunnelDefinitionRequest request) {
    return funnelDefinitionService
        .create(projectId, request, userEmail)
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{id}")
  public CompletionStage<Response<String>> updateFunnel(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
      @PathParam("id") long id,
      @NotNull @Valid UpdateFunnelDefinitionRequest request) {
    return funnelDefinitionService.update(projectId, id, request)
            .toSingleDefault(Response.successfulResponse("Success"))
            .toCompletionStage();
  }

  @DELETE
  @Path("/{id}")
  public CompletionStage<Response<String>> deleteFunnel(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
      @PathParam("id") long id) {
    return funnelDefinitionService
        .delete(projectId, id)
            .toSingleDefault(Response.successfulResponse("Success"))
            .toCompletionStage();
  }
}
