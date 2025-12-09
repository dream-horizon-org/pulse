package org.dreamhorizon.pulseserver.dao.configs;

import static org.dreamhorizon.pulseserver.dao.configs.Queries.GET_CONFIG_BY_VERSION;
import static org.dreamhorizon.pulseserver.dao.configs.Queries.GET_LATEST_VERSION;
import static org.dreamhorizon.pulseserver.dao.configs.Queries.INSERT_CONFIG;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.FilterMode;
import org.dreamhorizon.pulseserver.util.ObjectMapperUtil;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ConfigsDao {

  private final MysqlClient d11MysqlClient;
  private final ObjectMapperUtil objectMapper;

  public Single<Config> getConfig(long version) {
    return d11MysqlClient.getReaderPool()
        .preparedQuery(GET_CONFIG_BY_VERSION)
        .rxExecute(Tuple.of(version))
        .map(rows -> {
          if (rows.size() > 0) {
            Row row = rows.iterator().next();
            return Config.builder()
                .version(Long.parseLong(row.getValue("version").toString()))
                .configData(objectMapper.readValue(row.getValue("config_json").toString(), PulseConfig.class))
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
        .preparedQuery(GET_LATEST_VERSION)
        .rxExecute()
        .flatMap(rows -> {
          Row row = rows.iterator().next();
          return getConfig(Long.parseLong(row.getValue("version").toString()))
              .map(config -> {
                if (config.getConfigData().getFilters() != null) {
                  FilterMode mode = config.getConfigData().getFilters().getMode();
                  if (mode != null && mode.equals(FilterMode.BLACKLIST)) {
                    config.getConfigData().getFilters().setWhitelist(List.of());
                  } else {
                    config.getConfigData().getFilters().setBlacklist(List.of());
                  }
                }
                return config;
              });
        })
        .onErrorResumeNext(error -> {
          log.error("Error while fetching latest version from db: {}", error.getMessage());
          return Single.error(error);
        });
  }

  public Single<Config> createConfig(ConfigData createConfig) {
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
                  Long insertedId = rows.property(MySQLClient.LAST_INSERTED_ID);
                  return Single.just(insertedId);
                })
                .map(configId -> Config.builder()
                    .version(configId)
                    .configData(objectMapper.convertValue(createConfig, PulseConfig.class))
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
