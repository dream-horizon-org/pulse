package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.client.athena.AthenaClient;
import org.dreamhorizon.pulseserver.client.athena.AthenaQueryClientAdapter;
import org.dreamhorizon.pulseserver.client.athena.AthenaResultConverter;
import org.dreamhorizon.pulseserver.client.query.QueryClient;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import org.dreamhorizon.pulseserver.dao.athena.AthenaJobDao;
import org.dreamhorizon.pulseserver.dao.athena.AthenaQueryJobDaoAdapter;
import org.dreamhorizon.pulseserver.dao.query.QueryJobDao;
import org.dreamhorizon.pulseserver.service.query.QueryService;
import org.dreamhorizon.pulseserver.service.query.QueryServiceImpl;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaAsyncClient;

public class AthenaModule extends AbstractModule {

  public AthenaModule() {
  }

  @Override
  protected void configure() {
    bind(AthenaAsyncClient.class).toProvider(AthenaAsyncClientProvider.class).in(Singleton.class);
    bind(AthenaClient.class).in(Singleton.class);
    bind(AthenaJobDao.class).in(Singleton.class);
    bind(AthenaResultConverter.class).in(Singleton.class);
    
    bind(QueryClient.class).to(AthenaQueryClientAdapter.class).in(Singleton.class);
    bind(QueryJobDao.class).to(AthenaQueryJobDaoAdapter.class).in(Singleton.class);
    bind(QueryService.class).to(QueryServiceImpl.class).in(Singleton.class);
  }

  @Singleton
  private static class AthenaAsyncClientProvider implements Provider<AthenaAsyncClient> {
    private final Provider<AthenaConfig> athenaConfigProvider;

    @Inject
    public AthenaAsyncClientProvider(Provider<AthenaConfig> athenaConfigProvider) {
      this.athenaConfigProvider = athenaConfigProvider;
    }

    @Override
    public AthenaAsyncClient get() {
      AthenaConfig config = athenaConfigProvider.get();
      String region = config != null && config.getAthenaRegion() != null && !config.getAthenaRegion().isEmpty()
          ? config.getAthenaRegion()
          : System.getenv("CONFIG_SERVICE_APPLICATION_ATHENA_REGION");

      if (region == null || region.isEmpty()) {
        throw new IllegalStateException(
            "Athena region must be configured via CONFIG_SERVICE_APPLICATION_ATHENA_REGION environment variable or config file");
      }

      return AthenaAsyncClient.builder()
          .httpClientBuilder(NettyNioAsyncHttpClient.builder())
          .region(Region.of(region))
          .build();
    }
  }
}


