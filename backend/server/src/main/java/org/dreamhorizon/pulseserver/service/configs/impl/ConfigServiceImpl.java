package org.dreamhorizon.pulseserver.service.configs.impl;

import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.dao.configs.ConfigsDao;
import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.google.inject.Inject;
import org.dreamhorizon.pulseserver.service.configs.UploadConfigDetailService;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ConfigServiceImpl implements ConfigService {

  private final ConfigsDao configsDao;
  private final UploadConfigDetailService uploadConfigDetailService;

  @Override
  public Single<Config> getConfig(Integer version) {
    return configsDao.getConfig(version);
  }

  @Override
  public Single<Config> getConfig() {
    return configsDao.getConfig();
  }

  @Override
  public Single<Config> getActiveConfig(){
    return null;
  }

  @Override
  public Single<Config> createConfig(ConfigData createConfigRequest) {
    return configsDao.createConfig(createConfigRequest)
        .doOnSuccess(resp ->
            uploadConfigDetailService
                .pushInteractionDetailsToObjectStore()
                .subscribe())
        .flatMap(Single::just)
        .doOnError(err -> log.error("error while creating interaction", err));
  }
}
