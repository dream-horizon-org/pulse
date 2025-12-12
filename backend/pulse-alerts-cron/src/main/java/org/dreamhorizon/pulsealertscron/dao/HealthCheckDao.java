package org.dreamhorizon.pulsealertscron.dao;

import org.dreamhorizon.pulsealertscron.client.mysql.MysqlClient;
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

  final MysqlClient mysqlClient;

  public Single<JsonObject> mysqlHealthCheck() {
    var masterMysqlClient = mysqlClient.getMasterMysqlClient();
    return masterMysqlClient.query("SELECT 1;").rxExecute()
        .map(rowSet -> JsonUtils.jsonFrom("response", "1"))
        .doOnSuccess(result -> log.debug("MySQL health check passed"))
        .doOnError(error -> log.error("MySQL health check failed", error));
  }

  public Single<JsonObject> maintenanceHealthCheck() {
    var isUnderMaintenance = MaintenanceUtil.isUnderMaintenance(Vertx.currentContext().owner().getDelegate());
    var response = JsonUtils.jsonFrom("isUnderMaintenance", String.valueOf(isUnderMaintenance));
    return isUnderMaintenance ? Single.error(new RuntimeException(response.toString())) : Single.just(response);
  }
}

