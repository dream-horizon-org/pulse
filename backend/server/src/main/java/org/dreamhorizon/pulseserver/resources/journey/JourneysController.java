package org.dreamhorizon.pulseserver.resources.journey;

import com.google.inject.Inject;
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
import org.dreamhorizon.pulseserver.resources.journey.models.CreateJourneyRequest;
import org.dreamhorizon.pulseserver.resources.journey.models.JourneyListQueryParams;
import org.dreamhorizon.pulseserver.resources.journey.models.JourneyListResponse;
import org.dreamhorizon.pulseserver.resources.journey.models.JourneyResponse;
import org.dreamhorizon.pulseserver.resources.journey.models.UpdateJourneyRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.journey.JourneyService;

@Slf4j
@Path("/v1/journeys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class JourneysController {

  private final JourneyService journeyService;

  @GET
  public CompletionStage<Response<JourneyListResponse>> listJourneys(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
      @BeanParam JourneyListQueryParams query) {
    return journeyService.list(projectId, query).to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{id}")
  public CompletionStage<Response<JourneyResponse>> getJourney(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
      @PathParam("id") long id) {
    return journeyService.get(projectId, id).to(RestResponse.jaxrsRestHandler());
  }

  @POST
  public CompletionStage<Response<Long>> createJourney(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
      @HeaderParam("user-email") String userEmail,
      @NotNull @Valid CreateJourneyRequest request) {
    return journeyService.create(projectId, request, userEmail).to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{id}")
  public CompletionStage<Response<String>> updateJourney(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
      @PathParam("id") long id,
      @NotNull @Valid UpdateJourneyRequest request) {
    return journeyService
        .update(projectId, id, request)
        .toSingleDefault(Response.successfulResponse("Success"))
        .toCompletionStage();
  }

  @DELETE
  @Path("/{id}")
  public CompletionStage<Response<String>> deleteJourney(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
      @PathParam("id") long id) {
    return journeyService
        .delete(projectId, id)
        .toSingleDefault(Response.successfulResponse("Success"))
        .toCompletionStage();
  }
}
