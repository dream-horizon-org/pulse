package org.dreamhorizon.pulseserver.service.configs;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.CreateConfigResponse;

public interface ConfigService {
  Single<Config> getConfig(Integer version);

  Single<Config> getConfig();

  Single<Config> getActiveConfig();

  Single<Config> createConfig(ConfigData createConfig);
}
