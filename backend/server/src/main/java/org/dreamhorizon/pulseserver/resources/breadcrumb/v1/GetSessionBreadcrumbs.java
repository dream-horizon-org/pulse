package org.dreamhorizon.pulseserver.resources.breadcrumb.v1;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.breadcrumb.models.BreadcrumbRequestDto;
import org.dreamhorizon.pulseserver.resources.query.models.SubmitQueryResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.breadcrumb.BreadcrumbService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/breadcrumbs")
public class GetSessionBreadcrumbs {
  private final BreadcrumbService breadcrumbService;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SubmitQueryResponseDto>> getSessionBreadcrumbs(
      @HeaderParam("user-email") String userEmail,
      @Valid BreadcrumbRequestDto request) {
    return breadcrumbService.getSessionBreadcrumbs(
            request.getSessionId(), request.getErrorTimestamp(), userEmail)
        .to(RestResponse.jaxrsRestHandler());
  }
}
