package org.dreamhorizon.pulseserver.dao.configs;

import static org.dreamhorizon.pulseserver.dao.configs.Queries.INSERT_CONFIG;

import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.util.ObjectMapperUtil;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ConfigsDao {

    private final MysqlClient d11MysqlClient;
    private final ObjectMapperUtil objectMapper;
  
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

    public Single<Config> createConfig(ConfigData createConfig){
      String configDetailRowStr = objectMapper.writeValueAsString(createConfig);

      Tuple tuple = Tuple.tuple()
          .addString(configDetailRowStr)
          .addBoolean(true)
          .addString(createConfig.getUser());

      return d11MysqlClient
          .getWriterPool()
          .rxGetConnection()
          .flatMap(conn -> conn.begin()
              .flatMap(tx -> conn.preparedQuery(INSERT_CONFIG)
                  .rxExecute(tuple)
                  .flatMap(rows -> {
                    Row row = rows.iterator().next();
                    long insertedId = row.getLong("LAST_INSERT_ID()");
                    return Single.just(insertedId);
                  })
                  .map(configId -> Config.builder()
                      .version(String.valueOf(configId))
                      .configData(objectMapper.convertValue(createConfig, Config.ConfigData.class))
                      .build())
                  .flatMap(config -> tx.rxCommit().toSingleDefault(config))
                  .onErrorResumeNext(err -> {
                    log.error("Error while creating config in DB ", err);
                    return tx
                        .rxRollback()
                        .toSingleDefault(Config.builder().build())
                        .flatMap(msg -> Single.error(err));
                  })
                  .doFinally(conn::close)));
    }

}
