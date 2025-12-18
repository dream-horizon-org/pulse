package org.dreamhorizon.pulseserver;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.dreamhorizon.pulseserver.client.CloudFrontClient;
import org.dreamhorizon.pulseserver.client.S3BucketClient;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClientImpl;
import org.dreamhorizon.pulseserver.errorgrouping.Symbolicator;
import org.dreamhorizon.pulseserver.errorgrouping.service.ErrorGroupingService;
import org.dreamhorizon.pulseserver.errorgrouping.service.MysqlSymbolFileService;
import org.dreamhorizon.pulseserver.errorgrouping.service.SourceMapCache;
import org.dreamhorizon.pulseserver.errorgrouping.service.SymbolFileService;
import org.dreamhorizon.pulseserver.module.VertxAbstractModule;
import org.dreamhorizon.pulseserver.service.configs.ICloudFrontClient;
import org.dreamhorizon.pulseserver.service.configs.IS3BucketClient;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontAsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;

public class MainModule extends VertxAbstractModule {

  private final Vertx vertx;
  private ObjectMapper objectMapper;
  public MainModule(Vertx vertx) {
    super(vertx);
    this.vertx = vertx;
  }
  @Override
  protected void bindConfiguration() {
    bind(Vertx.class).toInstance(this.vertx);
    bind(io.vertx.rxjava3.core.Vertx.class)
        .toInstance(io.vertx.rxjava3.core.Vertx.newInstance(vertx));
    bind(ObjectMapper.class).toInstance(getObjectMapper());
    bind(WebClient.class).toProvider(() -> SharedDataUtils.get(vertx, WebClient.class));
    bind(MysqlClient.class).toProvider(() -> SharedDataUtils.get(vertx, MysqlClientImpl.class));
    bind(SymbolFileService.class).to(MysqlSymbolFileService.class).in(Singleton.class);
    bind(SourceMapCache.class).in(Singleton.class);
    bind(ErrorGroupingService.class).in(Singleton.class);
    bind(Symbolicator.class).in(Singleton.class);
    bind(S3AsyncClient.class).toProvider(this::loadS3Client).in(Singleton.class);
    bind(CloudFrontAsyncClient.class).toProvider(this::loadCloudFrontClient).in(Singleton.class);
    bind(ICloudFrontClient.class).to(CloudFrontClient.class).in(Singleton.class);
    bind(IS3BucketClient.class).to(S3BucketClient.class).in(Singleton.class);
  }

  protected ObjectMapper getObjectMapper() {
    if (objectMapper == null) {
      objectMapper = new ObjectMapper();
      objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
      objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
      objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    return objectMapper;
  }

  private S3AsyncClient loadS3Client() {
    return S3AsyncClient.builder()
        .httpClientBuilder(NettyNioAsyncHttpClient.builder())
        .region(Region.US_EAST_1)
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  private CloudFrontAsyncClient loadCloudFrontClient() {
    return CloudFrontAsyncClient
        .builder()
        .httpClientBuilder(NettyNioAsyncHttpClient.builder())
        .region(Region.US_EAST_1)
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
