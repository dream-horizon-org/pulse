package org.dreamhorizon.pulseserver.service.configs.impl;

import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.dao.configs.ConfigsDao;
import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.google.inject.Inject;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ConfigServiceImpl implements ConfigService {

    private final ConfigsDao configsDao;
    
    @Override
    public Single<Config> getConfig(Integer version) {
      return configsDao.getConfig(version);
    }

    @Override
    public Single<Config> getConfig() {
        return configsDao.getConfig();
    }
}
