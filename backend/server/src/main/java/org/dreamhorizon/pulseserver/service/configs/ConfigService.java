package org.dreamhorizon.pulseserver.service.configs;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;

public interface ConfigService {
    Single<Config> getConfig(Integer version);
    Single<Config> getConfig();
}
