package org.dreamhorizon.pulseserver.service.configs;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;

public interface ConfigService {
  Single<Config> getConfig(long version);

  Single<Config> getActiveConfig();

  Single<Config> createConfig(ConfigData createConfig);

  Single<AllConfigdetails> getAllConfigDetails();
}
