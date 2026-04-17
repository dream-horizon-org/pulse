package org.dreamhorizon.pulseserver.client.chclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

/**
 * Guice {@link Provider} for {@link ClickhouseWriteClient} using shared
 * Jackson configuration.
 */
@Singleton
public final class ClickhouseWriteClientProvider
    implements Provider<ClickhouseWriteClient> {

  /** Vert.x for resolving ClickHouse config from shared data. */
  private final Vertx vertx;
  /**
   * Base mapper; {@link ClickhouseWriteClient} applies NDJSON-specific
   * overrides on a copy.
   */
  private final ObjectMapper objectMapper;

  @Inject
  ClickhouseWriteClientProvider(final Vertx vx, final ObjectMapper om) {
    this.vertx = vx;
    this.objectMapper = om;
  }

  @Override
  public ClickhouseWriteClient get() {
    ClickhouseConfig cfg = SharedDataUtils.get(vertx, ClickhouseConfig.class);
    return new ClickhouseWriteClient(cfg, objectMapper);
  }
}
