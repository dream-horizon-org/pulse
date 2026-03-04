package org.dreamhorizon.pulseserver.service.configs;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.GetScopeAndSdksResponse;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.resources.configs.models.RulesAndFeaturesResponse;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;

public interface ConfigService {
  Single<PulseConfig> getSdkConfig(String projectId, long version);

  Single<PulseConfig> getActiveSdkConfig(String projectId);

  Single<PulseConfig> createSdkConfig(String projectId, ConfigData createConfig);

  /**
   * Creates the initial SDK config for a new project within a transaction.
   * Uses default config template internally.
   *
   * @param conn      The SQL connection for the transaction
   * @param projectId The project ID
   * @param createdBy The user creating the project
   * @return Single containing the created config
   */
  Single<PulseConfig> createInitialConfig(SqlConnection conn, String projectId, String createdBy);

  Single<AllConfigdetails> getAllSdkConfigDetails();

  Single<RulesAndFeaturesResponse> getRulesandFeatures();

  Single<GetScopeAndSdksResponse> getScopeAndSdks();
}
