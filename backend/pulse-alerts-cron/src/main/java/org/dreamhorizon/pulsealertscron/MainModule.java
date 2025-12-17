package org.dreamhorizon.pulsealertscron;

import org.dreamhorizon.pulsealertscron.module.VertxAbstractModule;
import org.dreamhorizon.pulsealertscron.services.CronManager;
import org.dreamhorizon.pulsealertscron.util.SharedDataUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;

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
    bind(CronManager.class).in(Singleton.class);
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
}
