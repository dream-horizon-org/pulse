package org.dreamhorizon.pulseserver.dao.configs;

import static org.dreamhorizon.pulseserver.dao.configs.Queries.DEACTIVATE_ACTIVE_CONFIG;
import static org.dreamhorizon.pulseserver.dao.configs.Queries.GET_ALL_CONFIG_DETAILS;
import static org.dreamhorizon.pulseserver.dao.configs.Queries.GET_CONFIG_BY_VERSION;
import static org.dreamhorizon.pulseserver.dao.configs.Queries.GET_LATEST_VERSION;
import static org.dreamhorizon.pulseserver.dao.configs.Queries.GET_NEXT_VERSION;
import static org.dreamhorizon.pulseserver.dao.configs.Queries.INSERT_CONFIG;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.configs.models.SdkConfigData;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.util.ObjectMapperUtil;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SdkConfigsDao {

  private final MysqlClient d11MysqlClient;
  private final ObjectMapperUtil objectMapper;

  /**
   * Gets the current project ID from the ProjectContext.
   */
  private String getProjectId() {
    return ProjectContext.getProjectId();
  }

  /**
   * Gets the next available version number for a project.
   * Returns 1 for the first config, or MAX(version) + 1 for subsequent configs.
   * 
   * @param conn Database connection (for use within transactions)
   * @param projectId Project ID
   * @return Single with the next version number
   */
  private Single<Long> getNextVersion(SqlConnection conn, String projectId) {
    return conn.preparedQuery(GET_NEXT_VERSION)
        .rxExecute(Tuple.of(projectId))
        .map(rows -> {
          if (rows.size() > 0) {
            Row row = rows.iterator().next();
            Long nextVersion = row.getLong("next_version");
            log.debug("Next version for project {}: {}", projectId, nextVersion);
            return nextVersion;
          } else {
            log.warn("No result from GET_NEXT_VERSION query for project: {}, defaulting to 1", projectId);
            return 1L;
          }
        })
        .doOnError(error -> log.error("Failed to get next version for project: {}", projectId, error));
  }

  public Single<PulseConfig> getConfig(String projectId, long version) {
    return d11MysqlClient.getWriterPool()
        .preparedQuery(GET_CONFIG_BY_VERSION)
        .rxExecute(Tuple.of(projectId, version))
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

  public Single<PulseConfig> getConfig(String projectId) {
    return d11MysqlClient.getWriterPool()
        .preparedQuery(GET_LATEST_VERSION)
        .rxExecute(Tuple.of(projectId))
        .flatMap(rows -> {
          if (rows.size() == 0) {
            log.warn("No active configuration found in database");
            return Single.error(new RuntimeException("No active configuration found. Please create a configuration first."));
          }
          Row row = rows.iterator().next();
          return getConfig(projectId, Long.parseLong(row.getValue("version").toString()));
        })
        .onErrorResumeNext(error -> {
          log.error("Error while fetching latest version from db: {}", error.getMessage());
          return Single.error(error);
        });
  }

  public Single<PulseConfig> createConfig(String projectId, org.dreamhorizon.pulseserver.service.configs.models.ConfigData createConfig) {
    SdkConfigData sdkConfigData = SdkConfigData.builder()
        .features(createConfig.getFeatures())
        .interaction(createConfig.getInteraction())
        .sampling(createConfig.getSampling())
        .signals(createConfig.getSignals())
        .build();

    String configDetailRowStr = objectMapper.writeValueAsString(sdkConfigData);

    return d11MysqlClient
        .getWriterPool()
        .rxGetConnection()
        .flatMap(conn -> conn.begin()
            .flatMap(tx -> 
                // Get the next version number for this project
                getNextVersion(conn, projectId)
                    .flatMap(nextVersion -> {
                      // Build tuple with the calculated version
                      Tuple tuple = Tuple.tuple()
                          .addString(projectId)
                          .addLong(nextVersion)
                          .addString(configDetailRowStr)
                          .addBoolean(true)
                          .addString(createConfig.getUser())
                          .addString(createConfig.getDescription());
                      
                      // Deactivate existing configs and insert new one
                      return conn.preparedQuery(DEACTIVATE_ACTIVE_CONFIG)
                          .rxExecute(Tuple.of(projectId))
                          .flatMap(deactivateResult -> conn.preparedQuery(INSERT_CONFIG).rxExecute(tuple))
                          .map(insertResult -> {
                            // Build PulseConfig with the calculated version
                            PulseConfig pulseConfig = PulseConfig.builder()
                                .version(nextVersion)
                                .description(createConfig.getDescription())
                                .sampling(objectMapper.convertValue(createConfig.getSampling(), PulseConfig.SamplingConfig.class))
                                .signals(objectMapper.convertValue(createConfig.getSignals(), PulseConfig.SignalsConfig.class))
                                .interaction(objectMapper.convertValue(createConfig.getInteraction(), PulseConfig.InteractionConfig.class))
                                .features(objectMapper.convertValue(createConfig.getFeatures(),
                                    objectMapper.constructCollectionType(List.class, PulseConfig.FeatureConfig.class)))
                                .build();
                            log.info("Created new SDK config for project: {}, version: {}", projectId, nextVersion);
                            return pulseConfig;
                          });
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
    return d11MysqlClient.getWriterPool()
        .preparedQuery(GET_ALL_CONFIG_DETAILS)
        .rxExecute(Tuple.of(getProjectId()))
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

  /**
   * Creates the initial SDK config for a new project.
   * Used during project creation to include config insertion in the main transaction.
   * Does NOT deactivate existing configs (assumes none exist for new projects).
   * Always creates version 1 for new projects.
   */
  public Single<PulseConfig> createInitialConfig(
      SqlConnection conn,
      String projectId,
      org.dreamhorizon.pulseserver.service.configs.models.ConfigData configData) {

    SdkConfigData sdkConfigData = SdkConfigData.builder()
        .features(configData.getFeatures())
        .interaction(configData.getInteraction())
        .sampling(configData.getSampling())
        .signals(configData.getSignals())
        .build();

    String configJson = objectMapper.writeValueAsString(sdkConfigData);
    
    // For initial config, version is always 1
    long initialVersion = 1L;
    Tuple tuple = buildConfigTuple(projectId, initialVersion, configJson, configData.getUser(), configData.getDescription());

    return conn.preparedQuery(INSERT_CONFIG)
        .rxExecute(tuple)
        .map(insertResult -> mapToPulseConfig(initialVersion, configData))
        .doOnSuccess(config -> log.info("Created initial SDK config for project: {}, version: {}", projectId, config.getVersion()))
        .doOnError(error -> log.error("Failed to create initial SDK config for project: {}", projectId, error));
  }

  private Tuple buildConfigTuple(String projectId, long version, String configJson, String createdBy, String description) {
    return Tuple.tuple()
        .addString(projectId)
        .addLong(version)
        .addString(configJson)
        .addBoolean(true)
        .addString(createdBy)
        .addString(description);
  }

  private PulseConfig mapToPulseConfig(Long version, org.dreamhorizon.pulseserver.service.configs.models.ConfigData configData) {
    return PulseConfig.builder()
        .version(version)
        .description(configData.getDescription())
        .sampling(objectMapper.convertValue(configData.getSampling(), PulseConfig.SamplingConfig.class))
        .signals(objectMapper.convertValue(configData.getSignals(), PulseConfig.SignalsConfig.class))
        .interaction(objectMapper.convertValue(configData.getInteraction(), PulseConfig.InteractionConfig.class))
        .features(objectMapper.convertValue(configData.getFeatures(),
            objectMapper.constructCollectionType(List.class, PulseConfig.FeatureConfig.class)))
        .build();
  }
}
