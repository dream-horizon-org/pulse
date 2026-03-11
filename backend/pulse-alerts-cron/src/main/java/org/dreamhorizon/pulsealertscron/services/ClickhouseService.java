package org.dreamhorizon.pulsealertscron.services;

import com.google.inject.Inject;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.dto.response.UsageStats;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
public class ClickhouseService {
  private final ConnectionPool connectionPool;

  @Inject
  public ClickhouseService(ApplicationConfig config) {
    log.info("Initializing ClickHouse connection pool with host: {}:{}/{}", 
        config.getClickhouseHost(),
        config.getClickhousePort(),
        config.getClickhouseDatabase());

    ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
        .option(ConnectionFactoryOptions.DRIVER, "clickhouse")
        .option(ConnectionFactoryOptions.PROTOCOL, "http")
        .option(ConnectionFactoryOptions.HOST, config.getClickhouseHost())
        .option(ConnectionFactoryOptions.PORT, config.getClickhousePort())
        .option(ConnectionFactoryOptions.DATABASE, config.getClickhouseDatabase())
        .option(ConnectionFactoryOptions.USER, config.getClickhouseUsername())
        .option(ConnectionFactoryOptions.PASSWORD, config.getClickhousePassword())
        .build();

    ConnectionFactory connectionFactory = ConnectionFactories.get(options);
    
    ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration.builder(connectionFactory)
        .maxIdleTime(Duration.ofMinutes(5))
        .maxSize(10)
        .initialSize(0)
        .maxAcquireTime(Duration.ofSeconds(30))
        .validationQuery("SELECT 1")
        .build();

    this.connectionPool = new ConnectionPool(poolConfig);
  }

  public Single<List<UsageStats>> getCurrentMonthUsage() {
    String query = """
        SELECT 
            tenant,
            sum(event_count) as events_used,
            uniqCombined64Merge(session_count) as sessions_used
        FROM otel.tenant_monthly_usage
        WHERE month = toStartOfMonth(now())
        GROUP BY tenant
        """;

    log.info("Executing ClickHouse query to fetch current month usage");

    return Single.defer(() -> {
      Mono<List<UsageStats>> mono = Mono.from(connectionPool.create())
          .flatMap(connection -> {
              Flux<UsageStats> statsFlux = Flux.from(connection.createStatement(query).execute())
                  .flatMap(result -> 
                      result.map((row, metadata) -> {
                          String tenant = row.get("tenant", String.class);
                          Long eventsUsed = row.get("events_used", Long.class);
                          Long sessionsUsed = row.get("sessions_used", Long.class);
                          
                          log.debug("Row: tenant={}, events={}, sessions={}", 
                              tenant, eventsUsed, sessionsUsed);
                          
                          return UsageStats.builder()
                              .tenant(tenant)
                              .eventsUsed(eventsUsed != null ? eventsUsed : 0L)
                              .sessionsUsed(sessionsUsed != null ? sessionsUsed : 0L)
                              .build();
                      })
                  );
              
              return statsFlux
                  .collectList()
                  .doFinally(signalType -> Mono.from(connection.close()).subscribe());
          });

      return RxJava3Adapter.monoToSingle(mono);
    }).doOnSuccess(stats -> {
        log.info("✅ Successfully fetched usage stats for {} tenants", stats.size());
        stats.forEach(stat -> 
            log.info("  → {}", stat)
        );
    }).doOnError(error -> 
        log.error("❌ Error fetching usage stats from ClickHouse", error)
    );
  }

  public void close() {
    if (connectionPool != null) {
      connectionPool.dispose();
      log.info("ClickHouse connection pool closed");
    }
  }
}
