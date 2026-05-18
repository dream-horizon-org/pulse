package org.dreamhorizon.pulses3archiver.config;

import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.config.ConfigRetriever;
import io.vertx.rxjava3.core.Vertx;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ConfigUtils {

  private ConfigUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  private static ConfigStoreOptions hoconFile(String path, boolean optional) {
    return new ConfigStoreOptions()
        .setType("file")
        .setFormat("hocon")
        .setOptional(optional)
        .setConfig(new JsonObject().put("path", path));
  }

  public static ConfigRetriever getConfigRetriever(Vertx vertx) {
    return ConfigRetriever.create(
        vertx,
        new ConfigRetrieverOptions()
            .addStore(hoconFile("conf/application-default.conf", false))
            .addStore(hoconFile("conf/kafka-default.conf", false))
            .addStore(hoconFile("conf/s3-default.conf", false))
            .addStore(hoconFile("conf/writer-default.conf", false))
            .setScanPeriod(5000));
  }
}
