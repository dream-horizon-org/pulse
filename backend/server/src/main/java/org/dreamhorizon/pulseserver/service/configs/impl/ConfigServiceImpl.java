package org.dreamhorizon.pulseserver.service.configs.impl;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.configs.SdkConfigsDao;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.GetScopeAndSdksResponse;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.resources.configs.models.RulesAndFeaturesResponse;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.DefaultSdkConfigTemplate;
import org.dreamhorizon.pulseserver.service.configs.UploadConfigDetailService;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.service.configs.models.Scope;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;
import org.dreamhorizon.pulseserver.service.configs.models.rules;

@Slf4j
public class ConfigServiceImpl implements ConfigService {

  private static final int MAX_PROJECTS_IN_CACHE = 100;

  private final SdkConfigsDao sdkConfigsDao;
  private final UploadConfigDetailService uploadConfigDetailService;
  private final AsyncLoadingCache<String, PulseConfig> latestConfigCache;

  @Inject
  public ConfigServiceImpl(Vertx vertx, SdkConfigsDao sdkConfigsDao,
                           UploadConfigDetailService uploadConfigDetailService) {
    this.sdkConfigsDao = sdkConfigsDao;
    this.uploadConfigDetailService = uploadConfigDetailService;

    Context ctx = vertx.getOrCreateContext();
    Objects.requireNonNull(ctx, "ConfigServiceImpl must be created on a Vert.x context thread");

    this.latestConfigCache = Caffeine.newBuilder()
        .maximumSize(MAX_PROJECTS_IN_CACHE)
        .executor(cmd -> ctx.runOnContext(v -> cmd.run()))
        .expireAfterWrite(Duration.ofHours(1))
        .recordStats()
        .buildAsync((String projectId, java.util.concurrent.Executor executor) -> {
          log.info("Loading config into cache for project: {}", projectId);
          return sdkConfigsDao.getConfig(projectId)
              .toCompletionStage()
              .toCompletableFuture();
        });
  }

  @Override
  public Single<PulseConfig> getSdkConfig(String projectId, long version) {
    return sdkConfigsDao.getConfig(projectId, version);
  }

  @Override
  public Single<PulseConfig> getActiveSdkConfig(String projectId) {
    CompletableFuture<PulseConfig> fut = latestConfigCache.get(projectId);
    return Single.create(emitter -> {
      fut.whenComplete((result, throwable) -> {
        if (throwable != null) {
          log.error("Error fetching config from cache for project: {}", projectId, throwable);
          emitter.onError(throwable);
        } else {
          log.debug("Returning config from cache for project: {}", projectId);
          emitter.onSuccess(result);
        }
      });
    });
  }

  @Override
  public Single<PulseConfig> createSdkConfig(String projectId, ConfigData createConfigRequest) {
    return sdkConfigsDao.createConfig(projectId, createConfigRequest)
        .doOnSuccess(resp -> {
          latestConfigCache.synchronous().invalidate(projectId);
          log.info("Invalidated config cache for project: {}", projectId);
          uploadConfigDetailService
              .pushInteractionDetailsToObjectStore(projectId)
              .subscribe();
        })
        .doOnError(err -> log.error("error while creating config for project: {}", projectId, err));
  }

  @Override
  public Single<PulseConfig> createInitialConfig(SqlConnection conn, String projectId, String createdBy) {
    log.debug("Creating initial SDK config for project: {} within transaction", projectId);
    ConfigData defaultConfig = DefaultSdkConfigTemplate.createDefaultConfig(createdBy);
    return sdkConfigsDao.createInitialConfig(conn, projectId, defaultConfig)
        .doOnSuccess(config -> {
          latestConfigCache.put(projectId, CompletableFuture.completedFuture(config));
          log.debug("Created and cached initial SDK config for project: {}, version: {}", projectId, config.getVersion());
        })
        .doOnError(err -> log.error("Failed to create initial SDK config for project: {}", projectId, err));
  }

  @Override
  public Single<AllConfigdetails> getAllSdkConfigDetails() {
    return sdkConfigsDao.getAllConfigDetails();
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
