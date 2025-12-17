package org.dreamhorizon.pulseserver.service.configs.impl;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.configs.ConfigsDao;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.resources.configs.models.RulesAndFeaturesResponse;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.service.configs.models.rules;
import org.dreamhorizon.pulseserver.resources.configs.models.GetScopeAndSdksResponse;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.Scope;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;

@Slf4j
public class ConfigServiceImpl implements ConfigService {

  private static final String LATEST_CONFIG_KEY = "latest-config";

  private final ConfigsDao configsDao;
  private final AsyncLoadingCache<String, PulseConfig> latestConfigCache;

  @Inject
  public ConfigServiceImpl(Vertx vertx, ConfigsDao configsDao) {
    this.configsDao = configsDao;

    Context ctx = vertx.getOrCreateContext();
    Objects.requireNonNull(ctx, "ConfigServiceImpl must be created on a Vert.x context thread");

    this.latestConfigCache = Caffeine.newBuilder()
        .maximumSize(1)
        .executor(cmd -> ctx.runOnContext(v -> cmd.run()))
        .expireAfterWrite(Duration.ofHours(1))
        .recordStats()
        .buildAsync((String key, java.util.concurrent.Executor executor) -> {
          log.info("Loading config into cache for key: {}", key);
          return configsDao.getConfig()
              .toCompletionStage()
              .toCompletableFuture();
        });
  }

  @Override
  public Single<PulseConfig> getConfig(long version) {
    return configsDao.getConfig(version);
  }

  @Override
  public Single<PulseConfig> getActiveConfig() {
    CompletableFuture<PulseConfig> fut = latestConfigCache.get(LATEST_CONFIG_KEY);
    return Single.create(emitter -> {
      fut.whenComplete((result, throwable) -> {
        if (throwable != null) {
          log.error("Error fetching config from cache", throwable);
          emitter.onError(throwable);
        } else {
          log.debug("Returning config from cache");
          emitter.onSuccess(result);
        }
      });
    });
  }

  @Override
  public Single<PulseConfig> createConfig(ConfigData createConfigRequest) {
    return configsDao.createConfig(createConfigRequest)
        .doOnSuccess(resp -> {
          latestConfigCache.synchronous().invalidate(LATEST_CONFIG_KEY);
        })
        .doOnError(err -> log.error("error while creating interaction", err));
  }

  @Override
  public Single<AllConfigdetails> getAllConfigDetails() {
    return configsDao.getAllConfigDetails();
  }

  @Override
  public Single<RulesAndFeaturesResponse> getRulesandFeatures() {
    return Single.just(RulesAndFeaturesResponse.builder()
        .rules(rules.getRules())
        .features(Features.getFeatures())
        .build());
  }

  @Override
  public Single<GetScopeAndSdksResponse> getScopeAndSdks() {
    return Single.just(GetScopeAndSdksResponse.builder()
        .scope(Arrays.stream(Scope.values()).map(Enum::name).collect(Collectors.toList()))
        .sdks(Arrays.stream(Sdk.values()).map(Enum::name).collect(Collectors.toList()))
        .build());
  }
}
