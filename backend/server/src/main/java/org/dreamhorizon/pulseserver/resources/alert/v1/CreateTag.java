package org.dreamhorizon.pulseserver.resources.alert.v1;

import org.dreamhorizon.pulseserver.resources.alert.models.CreateTagRequestDto;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/tag")
public class CreateTag {
    final AlertService alertsService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response<Boolean>> createTag(@NotNull CreateTagRequestDto tag) {
        return alertsService
                .createTag(tag.getTag())
                .to(RestResponse.jaxrsRestHandler());
    }
}
