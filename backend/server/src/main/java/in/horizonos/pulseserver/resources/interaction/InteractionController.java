package in.horizonos.pulseserver.resources.interaction;

import com.google.inject.Inject;
import in.horizonos.pulseserver.resources.interaction.models.DeleteInteractionRestResponse;
import in.horizonos.pulseserver.resources.interaction.models.GetInteractionsRestRequest;
import in.horizonos.pulseserver.resources.interaction.models.GetInteractionsRestResponse;
import in.horizonos.pulseserver.resources.interaction.models.InteractionFilterOptionsResponse;
import in.horizonos.pulseserver.resources.interaction.models.RestInteractionDetail;
import in.horizonos.pulseserver.resources.interaction.models.TelemetryFilterOptionsResponse;
import in.horizonos.pulseserver.resources.interaction.models.UpdateInteractionRestResponse;
import in.horizonos.pulseserver.resources.interaction.validators.CreateInteractionValidations;
import in.horizonos.pulseserver.resources.interaction.validators.UpdateInteractionValidations;
import in.horizonos.pulseserver.rest.io.Response;
import in.horizonos.pulseserver.rest.io.RestResponse;
import in.horizonos.pulseserver.service.interaction.InteractionService;
import in.horizonos.pulseserver.service.interaction.models.CreateInteractionRequest;
import in.horizonos.pulseserver.service.interaction.models.DeleteInteractionRequest;
import in.horizonos.pulseserver.service.interaction.models.InteractionDetails;
import in.horizonos.pulseserver.service.interaction.models.UpdateInteractionRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/interactions")
public class InteractionController {
  private static final String TYPE_EXPERIENCE = "experience";
  private static final String OPERATION_UPDATE = "update";
  private static final RestInteractionMapper mapper = RestInteractionMapper.INSTANCE;

  private final InteractionService interactionService;
  private final Validator validator;

  private static WebApplicationException getWebApplicationException(Set<ConstraintViolation<RestInteractionDetail>> violations) {
    return new WebApplicationException(
        jakarta.ws.rs.core.Response.status(400)
            .entity(getErrorMessageFromViolations(violations))
            .type(MediaType.APPLICATION_JSON)
            .build()
    );
  }

  private static Map<String, Object> getErrorMessageFromViolations(Set<ConstraintViolation<RestInteractionDetail>> violations) {
    List<String> messages = violations.stream()
        .map(v -> String.format("%s %s", v.getPropertyPath(), v.getMessage()))
        .collect(Collectors.toList());

    return Map.of("errors", messages);
  }

  @GET
  @Path("/all-active-interactions")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<InteractionDetails>>> getAllInteractions() {

    return interactionService.getAllActiveAndRunningInteractions()
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<GetInteractionsRestResponse>> getInteractions(
      @BeanParam GetInteractionsRestRequest request
  ) {

    return interactionService.getInteractions(mapper.toServiceRequest(request))
        .map(mapper::toRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<RestInteractionDetail>> createInteraction(
      @NotNull @HeaderParam("user-email") String userEmail,
      @NotNull RestInteractionDetail restRequest
  ) {

    Set<ConstraintViolation<RestInteractionDetail>> violations = validator.validate(restRequest, CreateInteractionValidations.class);
    if (!violations.isEmpty()) {
      WebApplicationException badReq = getWebApplicationException(violations);
      return CompletableFuture.failedFuture(badReq);
    }

    CreateInteractionRequest serviceRequest = mapper.toServiceCreateInteractionRequest(restRequest, userEmail);

    return interactionService.createInteraction(serviceRequest)
        .map(resp -> RestInteractionDetail.builder()
            .id(resp.getId())
            .build())
        .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{name}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<DeleteInteractionRestResponse>> deleteInteraction(
      @NotNull @HeaderParam("user-email") String userEmail,
      @NotNull @PathParam("name") String name
  ) {

    DeleteInteractionRequest serviceRequest = DeleteInteractionRequest.builder()
        .name(name)
        .userEmail(userEmail)
        .build();

    return interactionService.deleteInteraction(serviceRequest)
        .map(res -> DeleteInteractionRestResponse.builder()
            .status(200)
            .build())
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{name}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<UpdateInteractionRestResponse>> updateInteraction(
      @NotNull @HeaderParam("user-email") String userEmail,
      @NotNull @PathParam("name") String name,
      @NotNull RestInteractionDetail restRequest
  ) {

    Set<ConstraintViolation<RestInteractionDetail>> violations = validator.validate(restRequest, UpdateInteractionValidations.class);
    if (!violations.isEmpty()) {
      WebApplicationException badReq = getWebApplicationException(violations);
      return CompletableFuture.failedFuture(badReq);
    }

    UpdateInteractionRequest serviceRequest = mapper.toServiceUpdateInteractionRequest(restRequest, name, userEmail);

    return interactionService.updateInteraction(serviceRequest)
        .map(res -> UpdateInteractionRestResponse.builder().status(200).build())
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{name}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<RestInteractionDetail>> getInteractionDetails(
      @PathParam("name") String name
  ) {

    return interactionService.getInteractionDetails(name)
        .map(mapper::toRestInteractionDetail)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/filter-options")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<InteractionFilterOptionsResponse>> getInteractionFilterOptions() {
    return interactionService.getInteractionFilterOptions()
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/telemetry-filters")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<TelemetryFilterOptionsResponse>> getTelemetryFilterOptions() {
    return interactionService.getTelemetryFilterOptions()
        .to(RestResponse.jaxrsRestHandler());
  }
}