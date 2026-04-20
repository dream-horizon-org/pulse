package org.dreamhorizon.pulseserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import io.vertx.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.client.CloudFrontClient;
import org.dreamhorizon.pulseserver.client.S3BucketClient;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClientImpl;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.dreamhorizon.pulseserver.config.SessionReplayS3Config;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import org.dreamhorizon.pulseserver.dao.notification.*;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.user.UserDao;
import org.dreamhorizon.pulseserver.errorgrouping.IosLlvmSymbolicator;
import org.dreamhorizon.pulseserver.errorgrouping.Symbolicator;
import org.dreamhorizon.pulseserver.errorgrouping.service.DsymCache;
import org.dreamhorizon.pulseserver.errorgrouping.service.ErrorGroupingService;
import org.dreamhorizon.pulseserver.errorgrouping.service.MysqlSymbolFileService;
import org.dreamhorizon.pulseserver.errorgrouping.service.S3SymbolFileService;
import org.dreamhorizon.pulseserver.errorgrouping.service.SourceMapCache;
import org.dreamhorizon.pulseserver.errorgrouping.service.SymbolFileService;
import org.dreamhorizon.pulseserver.module.VertxAbstractModule;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.configs.ICloudFrontClient;
import org.dreamhorizon.pulseserver.service.configs.IS3BucketClient;
import org.dreamhorizon.pulseserver.service.incident.IncidentService;
import org.dreamhorizon.pulseserver.service.incident.IncidentServiceImpl;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.notification.NotificationServiceImpl;
import org.dreamhorizon.pulseserver.service.notification.TemplateService;
import org.dreamhorizon.pulseserver.service.notification.oauth.SlackOAuthService;
import org.dreamhorizon.pulseserver.service.notification.provider.*;
import org.dreamhorizon.pulseserver.service.notification.queue.DlqHandler;
import org.dreamhorizon.pulseserver.service.notification.queue.NotificationRetryPolicy;
import org.dreamhorizon.pulseserver.service.notification.queue.NotificationWorker;
import org.dreamhorizon.pulseserver.service.notification.queue.SqsNotificationQueue;
import org.dreamhorizon.pulseserver.service.kong.KongApiKeyRedisSyncService;
import org.dreamhorizon.pulseserver.service.notification.webhook.SesWebhookHandler;
import org.dreamhorizon.pulseserver.service.session.SessionBlockFetcher;
import org.dreamhorizon.pulseserver.service.session.SessionReplayService;
import org.dreamhorizon.pulseserver.util.ApiKeyGenerator;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperFactory;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperNames;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperUtil;
import org.dreamhorizon.pulseserver.util.RxObjectMapper;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontAsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.net.URI;

@Slf4j
public class MainModule extends VertxAbstractModule {

  private final Vertx vertx;

  public MainModule(Vertx vertx) {
    super(vertx);
    this.vertx = vertx;
  }

  @Override
  protected void bindConfiguration() {
    bind(Vertx.class).toInstance(this.vertx);
    bind(io.vertx.rxjava3.core.Vertx.class)
        .toInstance(io.vertx.rxjava3.core.Vertx.newInstance(vertx));
    bind(ObjectMapper.class).toInstance(ObjectMapperFactory.getIgnoringUnknownProperties());
    bind(ObjectMapper.class)
        .annotatedWith(Names.named(ObjectMapperNames.NORMAL))
        .toInstance(ObjectMapperFactory.getNormal());
    bind(ObjectMapper.class)
        .annotatedWith(Names.named(ObjectMapperNames.IGNORE_UNKNOWN_PROPERTIES))
        .toInstance(ObjectMapperFactory.getIgnoringUnknownProperties());
    bind(ObjectMapperUtil.class).in(Singleton.class);
    bind(RxObjectMapper.class).in(Singleton.class);
    bind(WebClient.class).toProvider(() -> SharedDataUtils.get(vertx, WebClient.class));
    bind(WebClient.class)
        .annotatedWith(Names.named(Constants.WEB_CLIENT_AI_PROXY))
        .toProvider(
            () -> SharedDataUtils.get(vertx, WebClient.class, Constants.WEB_CLIENT_AI_PROXY))
        .in(Singleton.class);
    bind(MysqlClient.class).toProvider(() -> SharedDataUtils.get(vertx, MysqlClientImpl.class));

    // === NEW: Multi-tenancy & RBAC Services ===
    // === NEW: Multi-tenancy & RBAC DAOs ===
    bind(UserDao.class).in(Singleton.class);
    bind(ProjectDao.class).in(Singleton.class);
    bind(ClickhouseProjectCredentialsDao.class).in(Singleton.class);

    // === NEW: Utilities ===
    bind(ApiKeyGenerator.class).in(Singleton.class);

    // === NEW: ClickHouse Project Connection Pool Manager ===
    bind(ClickhouseProjectConnectionPoolManager.class).toProvider(() -> {
      ClickhouseConfig config = SharedDataUtils.get(vertx, ClickhouseConfig.class);
      return new ClickhouseProjectConnectionPoolManager(config);
    }).in(Singleton.class);

    bind(S3SymbolFileService.class).in(Singleton.class);
    bind(SymbolFileService.class).to(MysqlSymbolFileService.class).in(Singleton.class);
    bind(SourceMapCache.class).in(Singleton.class);
    bind(DsymCache.class).in(Singleton.class);
    bind(IosLlvmSymbolicator.class).in(Singleton.class);
    bind(ErrorGroupingService.class).in(Singleton.class);
    bind(Symbolicator.class).in(Singleton.class);
    bind(S3AsyncClient.class).toProvider(this::loadS3Client).in(Singleton.class);
    bind(S3Presigner.class).toProvider(this::loadS3Presigner).in(Singleton.class);
    bind(CloudFrontAsyncClient.class).toProvider(this::loadCloudFrontClient).in(Singleton.class);
    bind(ICloudFrontClient.class).to(CloudFrontClient.class).in(Singleton.class);
    bind(IS3BucketClient.class).to(S3BucketClient.class).in(Singleton.class);
    bind(SessionBlockFetcher.class).in(Singleton.class);
    bind(SessionReplayService.class).in(Singleton.class);
    bind(KongApiKeyRedisSyncService.class).in(Singleton.class);

    // OpenFGA Authorization
    bind(OpenFgaConfig.class).toProvider(() -> {
      OpenFgaConfig config = SharedDataUtils.get(vertx, OpenFgaConfig.class);
      if (config == null) {
        config = OpenFgaConfig.builder()
            .enabled(false)
            .build();
      }
      return config;
    }).in(Singleton.class);

    bind(RootCauseConfig.class).toProvider(() -> {
      RootCauseConfig config = SharedDataUtils.get(vertx, RootCauseConfig.class);
      return RootCauseConfig.withDefaults(config);
    }).in(Singleton.class);

    bind(OpenFgaService.class).toProvider(() -> {
        OpenFgaConfig config = SharedDataUtils.get(vertx, OpenFgaConfig.class);
        if (config != null && config.isEnabled()) {
            try {
                return new OpenFgaService(config);
            } catch (Exception e) {
                log.error("Failed to initialize OpenFgaService: {}", e.getMessage());
                return null;
            }
        }
          return null;
    }).in(Singleton.class);

    bind(IncidentService.class).to(IncidentServiceImpl.class).in(Singleton.class);

    bindNotificationFeature();
  }

  private void bindNotificationFeature() {
    bind(NotificationChannelDao.class).in(Singleton.class);
    bind(NotificationTemplateDao.class).in(Singleton.class);
    bind(NotificationLogDao.class).in(Singleton.class);
    bind(EmailSuppressionDao.class).in(Singleton.class);
    bind(ChannelEventMappingDao.class).in(Singleton.class);

    bind(TemplateService.class).in(Singleton.class);
    bind(NotificationService.class).to(NotificationServiceImpl.class).in(Singleton.class);

    bind(SqsNotificationQueue.class).in(Singleton.class);
    bind(NotificationRetryPolicy.class).in(Singleton.class);
    bind(NotificationWorker.class).in(Singleton.class);
    bind(DlqHandler.class).in(Singleton.class);

    bind(NotificationProviderFactory.class).in(Singleton.class);
    Multibinder<NotificationProvider> providerBinder =
        Multibinder.newSetBinder(binder(), NotificationProvider.class);
    providerBinder.addBinding().to(EmailNotificationProvider.class).in(Singleton.class);
    providerBinder.addBinding().to(SlackNotificationProvider.class).in(Singleton.class);
    providerBinder.addBinding().to(SlackWebhookNotificationProvider.class).in(Singleton.class);
    providerBinder.addBinding().to(TeamsNotificationProvider.class).in(Singleton.class);

    bind(SesWebhookHandler.class).in(Singleton.class);

    bind(SlackOAuthService.class).in(Singleton.class);
  }

  private S3AsyncClient loadS3Client() {
    ApplicationConfig config = SharedDataUtils.get(vertx, ApplicationConfig.class);
    // When session replay S3 endpoint is set (e.g. MinIO in dev), use it for the default S3 client
    // so both config uploads and session replay block reads use the same client and env config.
    SessionReplayS3Config sr = config != null ? config.getSessionReplayS3() : null;
    if (sr != null && StringUtils.isNotBlank(sr.getEndpoint())) {
      String region = StringUtils.defaultIfBlank(sr.getRegion(), "ap-south-1");
      String accessKey = StringUtils.defaultString(sr.getAccessKeyId());
      String secretKey = StringUtils.defaultString(sr.getSecretAccessKey());
      return S3AsyncClient.builder()
          .httpClientBuilder(NettyNioAsyncHttpClient.builder())
          .region(Region.of(region))
          .endpointOverride(URI.create(sr.getEndpoint()))
          .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
          .forcePathStyle(true)
          .build();
    }
    return S3AsyncClient.builder()
        .httpClientBuilder(NettyNioAsyncHttpClient.builder())
        .region(Region.AP_SOUTH_1)
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  private S3Presigner loadS3Presigner() {
    return S3Presigner.builder()
        .region(Region.AP_SOUTH_1)
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  private CloudFrontAsyncClient loadCloudFrontClient() {
    return CloudFrontAsyncClient.builder()
        .httpClientBuilder(NettyNioAsyncHttpClient.builder())
        .region(Region.US_EAST_1) // CloudFront API is always in us-east-1
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
