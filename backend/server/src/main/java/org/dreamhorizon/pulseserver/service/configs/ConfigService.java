package org.dreamhorizon.pulseserver.service.configs;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.GetScopeAndSdksResponse;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.resources.configs.models.RulesAndFeaturesResponse;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;

public interface ConfigService {
  Single<PulseConfig> getSdkConfig(long version);

  Single<PulseConfig> getActiveSdkConfig();

  Single<PulseConfig> createSdkConfig(ConfigData createConfig);

  Single<AllConfigdetails> getAllSdkConfigDetails();

  Single<RulesAndFeaturesResponse> getRulesandFeatures();

  Single<GetScopeAndSdksResponse> getScopeAndSdks();
}
