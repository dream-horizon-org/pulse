package org.dreamhorizon.pulseserver.dao.configs;

import static org.dreamhorizon.pulseserver.dao.configs.Queries.DEACTIVATE_ACTIVE_CONFIG;
import static org.dreamhorizon.pulseserver.dao.configs.Queries.GET_ALL_CONFIG_DETAILS;
import static org.dreamhorizon.pulseserver.dao.configs.Queries.GET_CONFIG_BY_VERSION;
import static org.dreamhorizon.pulseserver.dao.configs.Queries.GET_LATEST_VERSION;
import static org.dreamhorizon.pulseserver.dao.configs.Queries.INSERT_CONFIG;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.configs.models.ConfigDataDao;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.util.ObjectMapperUtil;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ConfigsDao {

  private final MysqlClient d11MysqlClient;
  private final ObjectMapperUtil objectMapper;

  public Single<PulseConfig> getConfig(long version) {
    return d11MysqlClient.getReaderPool()
        .preparedQuery(GET_CONFIG_BY_VERSION)
        .rxExecute(Tuple.of(version))
        .map(rows -> {
          if (rows.size() > 0) {
            Row row = rows.iterator().next();
            PulseConfig pulseConfig = objectMapper.readValue(row.getValue("config_json").toString(), PulseConfig.class);
            String description = row.getValue("description") != null ? row.getValue("description").toString() : null;
            pulseConfig.setDescription(description);
            pulseConfig.setVersion(Long.parseLong(row.getValue("version").toString()));
            return pulseConfig;
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

  public Single<PulseConfig> getConfig() {
    return d11MysqlClient.getReaderPool()
        .preparedQuery(GET_LATEST_VERSION)
        .rxExecute()
        .flatMap(rows -> {
          Row row = rows.iterator().next();
          return getConfig(Long.parseLong(row.getValue("version").toString()));
        })
        .onErrorResumeNext(error -> {
          log.error("Error while fetching latest version from db: {}", error.getMessage());
          return Single.error(error);
        });
  }

  private static Single<Long> getLastInsertedId(RowSet<Row> rowSet) {
    if (rowSet.rowCount() == 0) {
      return Single.error(new RuntimeException("Failed to insert config"));
    }

    return Single.just(Long.parseLong(rowSet.property(MySQLClient.LAST_INSERTED_ID).toString()));
  }

  public Single<PulseConfig> createConfig(ConfigData createConfig) {
    ConfigDataDao configDataDao = ConfigDataDao.builder()
        .features(createConfig.getFeatures())
        .interaction(createConfig.getInteraction())
        .sampling(createConfig.getSampling())
        .signals(createConfig.getSignals())
        .build();

    String configDetailRowStr = objectMapper.writeValueAsString(configDataDao);

    Tuple tuple = Tuple.tuple()
        .addString(configDetailRowStr)
        .addBoolean(true)
        .addString(createConfig.getUser())
        .addString(createConfig.getDescription());

    return d11MysqlClient
        .getWriterPool()
        .rxGetConnection()
        .flatMap(conn -> conn.begin()
            .flatMap(tx -> conn.preparedQuery(DEACTIVATE_ACTIVE_CONFIG)
                .rxExecute()
                .flatMap(deactivateResult -> conn.preparedQuery(INSERT_CONFIG).rxExecute(tuple))
                .flatMap(ConfigsDao::getLastInsertedId)
                .map(configId -> {
                  PulseConfig pulseConfig = PulseConfig.builder()
                      .version(configId)
                      .description(createConfig.getDescription())
                      .sampling(objectMapper.convertValue(createConfig.getSampling(), PulseConfig.SamplingConfig.class))
                      .signals(objectMapper.convertValue(createConfig.getSignals(), PulseConfig.SignalsConfig.class))
                      .interaction(objectMapper.convertValue(createConfig.getInteraction(), PulseConfig.InteractionConfig.class))
                      .features(objectMapper.convertValue(createConfig.getFeatures(),
                          objectMapper.constructCollectionType(List.class, PulseConfig.FeatureConfig.class)))
                      .build();
                  return pulseConfig;
                })
                .flatMap(config -> tx.rxCommit().toSingleDefault(config))
                .onErrorResumeNext(err -> {
                  log.error("Error while creating config in DB ", err);
                  return tx
                      .rxRollback()
                      .toSingleDefault(PulseConfig.builder().build())
                      .flatMap(msg -> Single.error(err));
                })
                .doFinally(conn::close)));
  }

  public Single<AllConfigdetails> getAllConfigDetails() {
    return d11MysqlClient.getReaderPool()
        .preparedQuery(GET_ALL_CONFIG_DETAILS)
        .rxExecute()
        .map(rows -> {
          List<AllConfigdetails.Configdetails> configDetails = new ArrayList<>();
          for (Row row : rows) {
            configDetails.add(AllConfigdetails.Configdetails.builder()
                .version(Long.parseLong(row.getValue("version").toString()))
                .description(row.getValue("description").toString())
                .createdBy(row.getValue("created_by").toString())
                .createdAt(row.getValue("created_at").toString())
                .isactive(row.getInteger("is_active") != 0)
                .build());
          }
          return AllConfigdetails.builder()
              .configDetails(configDetails)
              .build();
        });
  }
}
