package org.dreamhorizon.pulseserver.resources;


import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.dreamhorizon.pulseserver.dao.HealthCheckDao;
import org.dreamhorizon.pulseserver.healthcheck.HealthCheckResponse;
import org.dreamhorizon.pulseserver.healthcheck.HealthCheckUtil;
import org.dreamhorizon.pulseserver.util.CompletableFutureUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/healthcheck")
public class HealthCheck {

  final HealthCheckDao healthCheckDao;

  @GET
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<HealthCheckResponse> healthcheck() {
    val map = ImmutableMap.<String, Single<JsonObject>>builder()
        .put("mysql", healthCheckDao.mysqlHealthCheck())
        .build();
    return HealthCheckUtil.handler(map)
        .to(CompletableFutureUtils::fromSingle);
  }
}