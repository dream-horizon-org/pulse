package org.dreamhorizon.pulseserver.verticle;

import static org.dreamhorizon.pulseserver.constant.Constants.HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE;
import static org.dreamhorizon.pulseserver.constant.Constants.HTTP_CLIENT_IDLE_TIMEOUT;
import static org.dreamhorizon.pulseserver.constant.Constants.HTTP_CLIENT_KEEP_ALIVE;
import static org.dreamhorizon.pulseserver.constant.Constants.HTTP_CLIENT_KEEP_ALIVE_TIMEOUT;
import static org.dreamhorizon.pulseserver.constant.Constants.HTTP_CONNECT_TIMEOUT;
import static org.dreamhorizon.pulseserver.constant.Constants.HTTP_READ_TIMEOUT;
import static org.dreamhorizon.pulseserver.constant.Constants.HTTP_WRITE_TIMEOUT;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClientImpl;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.dreamhorizon.pulseserver.config.ConfigUtils;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

@Slf4j
public class MainVerticle extends AbstractVerticle {

  private WebClient webClient;
  private MysqlClient mysqlClient;

  @Override
  public Completable rxStart() {
    return ConfigUtils.getConfigRetriever(vertx)
        .rxGetConfig()
        .map(config -> {
          JsonObject appConfig = config.getJsonObject("app", new JsonObject());

          JsonObject mysqlConfig = config.getJsonObject("mysql", new JsonObject());
          JsonObject webClientConfig = config.getJsonObject("webclient", new JsonObject());


          this.mysqlClient = new MysqlClientImpl(this.vertx, mysqlConfig);
          this.webClient = WebClient.create(vertx, getWebClientOptions(webClientConfig));
          SharedDataUtils.put(vertx.getDelegate(), appConfig.mapTo(ApplicationConfig.class));
          JsonObject chConfig = config.getJsonObject("clickhouse", new JsonObject());
          SharedDataUtils.put(vertx.getDelegate(), chConfig.mapTo(ClickhouseConfig.class));
          SharedDataUtils.put(vertx.getDelegate(), mysqlClient);
          SharedDataUtils.put(vertx.getDelegate(), webClient);
          return config;
        }).ignoreElement()
        .andThen(
            vertx.rxDeployVerticle(
                () ->
                    new RestVerticle(
                        new HttpServerOptions().setPort(8080)),
                new DeploymentOptions().setInstances(getNumOfCores()))
        ).ignoreElement();
  }

  private Integer getNumOfCores() {
    return CpuCoreSensor.availableProcessors();
  }

  private WebClientOptions getWebClientOptions(JsonObject config) {
    return new WebClientOptions()
        .setConnectTimeout(Integer.parseInt(config.getString(HTTP_CONNECT_TIMEOUT)))
        .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
        .setKeepAlive(Boolean.parseBoolean(config.getString(HTTP_CLIENT_KEEP_ALIVE)))
        .setKeepAliveTimeout(
            Integer.parseInt(config.getString(HTTP_CLIENT_KEEP_ALIVE_TIMEOUT)) / 1000)
        .setIdleTimeout(Integer.parseInt(config.getString(HTTP_CLIENT_IDLE_TIMEOUT)))
        .setMaxPoolSize(
            Integer.parseInt(config.getString(HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE)))
        .setReadIdleTimeout(Integer.parseInt(config.getString(HTTP_READ_TIMEOUT)))
        .setWriteIdleTimeout(Integer.parseInt(config.getString(HTTP_WRITE_TIMEOUT)));
  }

  @Override
  public Completable rxStop() {
    this.webClient.close();
    return mysqlClient.rxClose();
  }
}
