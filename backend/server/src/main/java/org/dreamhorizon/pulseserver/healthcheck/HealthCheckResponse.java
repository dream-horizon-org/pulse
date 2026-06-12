package org.dreamhorizon.pulseserver.healthcheck;

import com.google.common.collect.ImmutableList;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.dreamhorizon.pulseserver.util.CollectionUtils;
import org.dreamhorizon.pulseserver.util.JsonUtils;
import org.dreamhorizon.pulseserver.util.MapUtils;

@RequiredArgsConstructor
@Slf4j
public class HealthCheckResponse extends VertxEntity {

  @Getter
  private final List<HealthCheckResponse.Check> checks;

  @Override
  public JsonObject toJson() {
    val json =
        JsonUtils.jsonMerge(
            ImmutableList.of(
                JsonUtils.jsonFrom("status", getStatus().getName()),
                JsonUtils.jsonFrom(
                    "checks", JsonUtils.jsonFrom(MapUtils.map(HealthCheckResponse.Check::toJson, getChecksAsMap())))));
    // Adding this extra explict data key until we have a workaround for error case
    return JsonUtils.jsonFrom("data", json);
  }

  public HealthCheckResponse.Status getStatus() {
    val allChecksUp = CollectionUtils.all(kv -> HealthCheckResponse.Status.UP.equals(kv.getStatus()), this.checks);
    return Boolean.TRUE.equals(allChecksUp) ? HealthCheckResponse.Status.UP : HealthCheckResponse.Status.DOWN;
  }

  private Map<String, HealthCheckResponse.Check> getChecksAsMap() {
    return CollectionUtils.indexBy(HealthCheckResponse.Check::getType, checks);
  }

  @Getter
  @ToString
  @RequiredArgsConstructor()
  public enum Status {
    UP("UP"),
    DOWN("DOWN");

    private final String name;
  }

  @Value
  public static class Check extends VertxEntity {
    String error;
    JsonObject response;
    HealthCheckResponse.Status status;
    String type;

    public Check(String type, Throwable error) {
      this.type = type;
      this.response = null;
      this.error = error.toString();
      this.status = HealthCheckResponse.Status.DOWN;
    }

    public Check(String type, JsonObject response) {
      this.type = type;
      this.response = response;
      this.error = null;
      this.status = HealthCheckResponse.Status.UP;
    }

    @Override
    public JsonObject toJson() {
      return super.toJson(true);
    }
  }
}

