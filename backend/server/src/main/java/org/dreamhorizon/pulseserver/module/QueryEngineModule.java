package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.athena.AthenaClient;
import org.dreamhorizon.pulseserver.client.athena.AthenaQueryClientAdapter;
import org.dreamhorizon.pulseserver.client.athena.AthenaResultConverter;
import org.dreamhorizon.pulseserver.client.query.QueryClient;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import org.dreamhorizon.pulseserver.dao.athena.AthenaJobDao;
import org.dreamhorizon.pulseserver.dao.athena.AthenaQueryJobDaoAdapter;
import org.dreamhorizon.pulseserver.dao.query.QueryJobDao;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.service.query.QueryService;
import org.dreamhorizon.pulseserver.service.query.QueryServiceImpl;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaAsyncClient;

@Slf4j
public class QueryEngineModule extends AbstractModule {

  public QueryEngineModule() {
  }

  @Override
  protected void configure() {
    String queryEngine = getQueryEngine();
    log.info("Configuring query engine: {}", queryEngine);

    if (Constants.ATHENA_ENGINE.equalsIgnoreCase(queryEngine)) {
      configureAthena();
    } else if (Constants.GCP_ENGINE.equalsIgnoreCase(queryEngine)) {
      configureGcp();
    } else {
      throw new IllegalStateException(
          String.format("Unsupported query engine: %s. Supported values: %s, %s", 
              queryEngine, Constants.ATHENA_ENGINE, Constants.GCP_ENGINE));
    }

    // QueryService is common for all engines
    bind(QueryService.class).to(QueryServiceImpl.class).in(Singleton.class);
  }

  private void configureAthena() {
    log.info("Binding Athena query engine implementations");
    bind(AthenaAsyncClient.class).toProvider(AthenaAsyncClientProvider.class).in(Singleton.class);
    bind(AthenaClient.class).in(Singleton.class);
    bind(AthenaJobDao.class).in(Singleton.class);
    bind(AthenaResultConverter.class).in(Singleton.class);
    
    bind(QueryClient.class).to(AthenaQueryClientAdapter.class).in(Singleton.class);
    bind(QueryJobDao.class).to(AthenaQueryJobDaoAdapter.class).in(Singleton.class);
  }

  private void configureGcp() {
    log.info("Binding GCP query engine implementations");
    // TODO: Implement GCP bindings when GCP client is available
    // bind(GcpClient.class).in(Singleton.class);
    // bind(QueryClient.class).to(GcpQueryClientAdapter.class).in(Singleton.class);
    // bind(QueryJobDao.class).to(GcpQueryJobDaoAdapter.class).in(Singleton.class);
    throw new UnsupportedOperationException("GCP query engine is not yet implemented");
  }

  private String getQueryEngine() {
    String queryEngine = System.getenv(Constants.QUERY_ENGINE_ENV_VAR);
    if (queryEngine == null || queryEngine.isEmpty()) {
      queryEngine = Constants.DEFAULT_QUERY_ENGINE;
      log.info("QUERY_ENGINE environment variable not set, using default: {}", queryEngine);
    }
    return queryEngine.toLowerCase().trim();
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

