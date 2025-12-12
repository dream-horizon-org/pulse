package org.dreamhorizon.pulsealertscron.rest;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.dto.request.AddCronDto;
import org.dreamhorizon.pulsealertscron.dto.request.DeleteCronDto;
import org.dreamhorizon.pulsealertscron.dto.request.UpdateCronDto;
import org.dreamhorizon.pulsealertscron.dto.response.CronManagerDto;
import org.dreamhorizon.pulsealertscron.rest.io.Response;
import org.dreamhorizon.pulsealertscron.rest.io.RestResponse;
import org.dreamhorizon.pulsealertscron.services.CronManager;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/cron")
public class CronController {
  final CronManager cronManager;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CronManagerDto>> addCron(AddCronDto cronDto) {
    return cronManager.addCronTask(cronDto.getId(), cronDto.getUrl(), cronDto.getInterval())
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CronManagerDto>> modifyCron(UpdateCronDto cronDto) {
    cronManager.modifyCronTask(cronDto.getId(), cronDto.getUrl(), cronDto.getNewInterval(), cronDto.getOldInterval());
    return Single.just(CronManagerDto.builder().status("success").build())
        .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CronManagerDto>> removeCron(DeleteCronDto cronDto) {
    cronManager.removeCronTask(cronDto.getId(), cronDto.getInterval());
    return Single.just(CronManagerDto.builder().status("success").build())
        .to(RestResponse.jaxrsRestHandler());
  }
}
