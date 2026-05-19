package org.dreamhorizon.pulseserver.resources.services;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.services.models.CreateServiceRequest;
import org.dreamhorizon.pulseserver.resources.services.models.ServiceResponseDto;
import org.dreamhorizon.pulseserver.resources.services.models.UpdateServiceRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.serviceowner.ServiceOwnerService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/services")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ServiceController {

  private final ServiceOwnerService serviceOwnerService;

  @GET
  public CompletionStage<Response<List<ServiceResponseDto>>> listServices() {
    log.info("Received list services request");
    return serviceOwnerService.listServices()
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{serviceName}")
  public CompletionStage<Response<ServiceResponseDto>> getService(
      @PathParam("serviceName") String serviceName) {
    log.info("Received get service request: serviceName={}", serviceName);
    return serviceOwnerService.getByServiceName(serviceName)
        .switchIfEmpty(io.reactivex.rxjava3.core.Single.error(
            new RuntimeException("Service not found: " + serviceName)))
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  public CompletionStage<Response<ServiceResponseDto>> createService(
      @NotNull @Valid CreateServiceRequest request) {
    log.info("Received create service request: serviceName={}", request.getServiceName());
    return serviceOwnerService.createService(request)
        .to(RestResponse.jaxrsRestHandler(201));
  }

  @PUT
  @Path("/{serviceName}")
  public CompletionStage<Response<ServiceResponseDto>> updateService(
      @PathParam("serviceName") String serviceName,
      @NotNull @Valid UpdateServiceRequest request) {
    log.info("Received update service request: serviceName={}", serviceName);
    return serviceOwnerService.updateService(serviceName, request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{serviceName}")
  public CompletionStage<Response<Void>> deleteService(
      @PathParam("serviceName") String serviceName) {
    log.info("Received delete service request: serviceName={}", serviceName);
    return serviceOwnerService.deleteService(serviceName)
        .toSingleDefault((Void) null)
        .to(RestResponse.jaxrsRestHandler());
  }
}
