package org.dreamhorizon.pulsealertscron.dao;

import org.dreamhorizon.pulsealertscron.util.JsonUtils;
import org.dreamhorizon.pulsealertscron.util.MaintenanceUtil;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class HealthCheckDao {

  public Single<JsonObject> maintenanceHealthCheck() {
    var isUnderMaintenance = MaintenanceUtil.isUnderMaintenance(Vertx.currentContext().owner().getDelegate());
    var response = JsonUtils.jsonFrom("isUnderMaintenance", String.valueOf(isUnderMaintenance));
    return isUnderMaintenance ? Single.error(new RuntimeException(response.toString())) : Single.just(response);
  }
}
