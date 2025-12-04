package org.dreamhorizon.pulseserver.dao.configs;

import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ConfigsDao {

    private final MysqlClient d11MysqlClient;
    private final ObjectMapper objectMapper;
  
    public Single<Config> getConfig(Integer version) {
      return d11MysqlClient.getReaderPool()
          .preparedQuery("SELECT config_json, version FROM pulse_sdk_configs WHERE version = ?")
          .rxExecute(Tuple.of(version))
          .map(rows -> {
            if (rows.size() > 0) {
                Row row = rows.iterator().next();
                return Config.builder()
                    .version(row.getValue("version").toString())
                    .configData(objectMapper.readValue(row.getValue("config_json").toString(), Config.ConfigData.class))
                    .build();
            } else {
                log.error("No config found for version: {}", version);
                throw new RuntimeException("No config found for version: " + version);
            }

          })
          .onErrorResumeNext(error -> {
            log.error("Error while fetching config from db: {}", error.getMessage());
            return Single.error(error);
          });
    }
  
    public Single<Config> getConfig() {
      return d11MysqlClient.getReaderPool()
          .preparedQuery("SELECT version FROM pulse_sdk_configs WHERE is_active = 1 LIMIT 1")
          .rxExecute()
          .flatMap(rows -> {
            Row row = rows.iterator().next();
            return getConfig(Integer.parseInt(row.getValue("version").toString()));
          })
          .onErrorResumeNext(error -> {
            log.error("Error while fetching latest version from db: {}", error.getMessage());
            return Single.error(error);
          });
    }
}
