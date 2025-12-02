package in.horizonos.pulseserver.resources;


import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import in.horizonos.pulseserver.dao.HealthCheckDao;
import in.horizonos.pulseserver.healthcheck.HealthCheckResponse;
import in.horizonos.pulseserver.healthcheck.HealthCheckUtil;
import in.horizonos.pulseserver.util.CompletableFutureUtils;
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