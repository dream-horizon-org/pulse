package org.dreamhorizon.pulseserver.dao;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.util.CompletableUtils;
import org.dreamhorizon.pulseserver.util.JsonUtils;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class HealthCheckDao {

  final MysqlClient d11MysqlClient;

  public Single<JsonObject> mysqlHealthCheck() {
    val masterMysqlClient = d11MysqlClient.getReaderPool();
    return masterMysqlClient.query("SELECT 1;").rxExecute()
        .map(rowSet -> JsonUtils.jsonFrom("response", "1"))
        .compose(CompletableUtils.applyDebugLogsSingle(log));
  }

}