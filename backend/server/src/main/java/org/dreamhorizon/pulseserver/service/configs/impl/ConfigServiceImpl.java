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
import org.dreamhorizon.pulseserver.dao.configs.SdkConfigsDao;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.GetScopeAndSdksResponse;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.resources.configs.models.RulesAndFeaturesResponse;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.UploadConfigDetailService;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.service.configs.models.Scope;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;
import org.dreamhorizon.pulseserver.service.configs.models.rules;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

@Slf4j
public class ConfigServiceImpl implements ConfigService {

  private static final int MAX_TENANTS_IN_CACHE = 100;

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
        .maximumSize(MAX_TENANTS_IN_CACHE)
        .executor(cmd -> ctx.runOnContext(v -> cmd.run()))
        .expireAfterWrite(Duration.ofHours(1))
        .recordStats()
        .buildAsync((String tenantId, java.util.concurrent.Executor executor) -> {
          log.info("Loading config into cache for tenant: {}", tenantId);
          return sdkConfigsDao.getConfig()
              .toCompletionStage()
              .toCompletableFuture();
        });
  }

  @Override
  public Single<PulseConfig> getSdkConfig(long version) {
    return sdkConfigsDao.getConfig(version);
  }

  @Override
  public Single<PulseConfig> getActiveSdkConfig(String tenantId) {
    CompletableFuture<PulseConfig> fut = latestConfigCache.get(tenantId);
    return Single.create(emitter -> {
      fut.whenComplete((result, throwable) -> {
        if (throwable != null) {
          log.error("Error fetching config from cache for tenant: {}", tenantId, throwable);
          emitter.onError(throwable);
        } else {
          log.debug("Returning config from cache for tenant: {}", tenantId);
          emitter.onSuccess(result);
        }
      });
    });
  }

  @Override
  public Single<PulseConfig> createSdkConfig(ConfigData createConfigRequest) {
    String tenantId = TenantContext.requireTenantId();
    return sdkConfigsDao.createConfig(createConfigRequest)
        .doOnSuccess(resp -> {
          latestConfigCache.synchronous().invalidate(tenantId);
          log.info("Invalidated config cache for tenant: {}", tenantId);
          uploadConfigDetailService
              .pushInteractionDetailsToObjectStore(tenantId)
              .subscribe();
        })
        .doOnError(err -> log.error("error while creating config for tenant: {}", tenantId, err));
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
