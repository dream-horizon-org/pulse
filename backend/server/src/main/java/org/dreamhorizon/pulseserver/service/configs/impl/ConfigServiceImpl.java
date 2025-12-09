package org.dreamhorizon.pulseserver.service.configs.impl;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.configs.ConfigsDao;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.UploadConfigDetailService;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ConfigServiceImpl implements ConfigService {

  private final ConfigsDao configsDao;
  private final UploadConfigDetailService uploadConfigDetailService;

  @Override
  public Single<Config> getConfig(long version) {
    return configsDao.getConfig(version);
  }

  @Override
  public Single<Config> getActiveConfig() {
    return configsDao.getConfig();
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

  @Override
  public Single<AllConfigdetails> getAllConfigDetails() {
    return configsDao.getAllConfigDetails();
  }
}
