package in.horizonos.pulseserver.dao;

import com.google.inject.Inject;
import in.horizonos.pulseserver.client.mysql.MysqlClient;
import in.horizonos.pulseserver.util.CompletableUtils;
import in.horizonos.pulseserver.util.JsonUtils;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;


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