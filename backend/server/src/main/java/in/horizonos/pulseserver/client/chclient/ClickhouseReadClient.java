package in.horizonos.pulseserver.client.chclient;

import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

import in.horizonos.pulseserver.config.ClickhouseConfig;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.time.Duration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Getter
public class ClickhouseReadClient {
  private final ConnectionPool pool;

  public ClickhouseReadClient(ClickhouseConfig clickhouseConfig) {
    String r2dbcUrl = clickhouseConfig.getR2dbcUrl();
    String username = clickhouseConfig.getUsername();
    String password = clickhouseConfig.getPassword();
    ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
        .from(ConnectionFactoryOptions.parse(r2dbcUrl))
        .option(USER, username != null ? username : "default")
        .option(PASSWORD, password != null ? password : "")
        .build();
    ConnectionFactory connectionFactory = ConnectionFactories.get(options);
    ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder(connectionFactory)
        .initialSize(clickhouseConfig.getInitsize())
        .maxSize(clickhouseConfig.getMaxsize())
        .maxIdleTime(Duration.ofMinutes(5))
        .build();
    this.pool = new ConnectionPool(poolConfiguration);
  }
}