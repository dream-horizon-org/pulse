package org.dreamhorizon.pulsealertscron.verticle;

import static org.dreamhorizon.pulsealertscron.constant.Constants.HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE;
import static org.dreamhorizon.pulsealertscron.constant.Constants.HTTP_CLIENT_IDLE_TIMEOUT;
import static org.dreamhorizon.pulsealertscron.constant.Constants.HTTP_CLIENT_KEEP_ALIVE;
import static org.dreamhorizon.pulsealertscron.constant.Constants.HTTP_CLIENT_KEEP_ALIVE_TIMEOUT;
import static org.dreamhorizon.pulsealertscron.constant.Constants.HTTP_CONNECT_TIMEOUT;
import static org.dreamhorizon.pulsealertscron.constant.Constants.HTTP_READ_TIMEOUT;
import static org.dreamhorizon.pulsealertscron.constant.Constants.HTTP_WRITE_TIMEOUT;

import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.constant.Constants;
import org.dreamhorizon.pulsealertscron.guice.GuiceInjector;
import org.dreamhorizon.pulsealertscron.services.AlertsService;
import org.dreamhorizon.pulsealertscron.services.CronManager;
import org.dreamhorizon.pulsealertscron.config.ConfigUtils;
import org.dreamhorizon.pulsealertscron.util.SharedDataUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class MainVerticle extends AbstractVerticle {

  private WebClient webClient;
  private ApplicationConfig applicationConfig;

  private static final AtomicBoolean alertCronMethodCalled = new AtomicBoolean(false);
  private static final AtomicBoolean populateMetricInitiated = new AtomicBoolean(false);
  private static final AtomicBoolean populateAndIngestAnomalyInitiated = new AtomicBoolean(false);

  @Override
  public Completable rxStart() {
    return ConfigUtils.getConfigRetriever(vertx)
        .rxGetConfig()
        .map(config -> {
          JsonObject appConfig = config.getJsonObject("app", new JsonObject());
          JsonObject webClientConfig = config.getJsonObject("webclient", new JsonObject());

          this.applicationConfig = appConfig.mapTo(ApplicationConfig.class);
          this.webClient = WebClient.create(vertx, getWebClientOptions(webClientConfig));

          SharedDataUtils.put(vertx.getDelegate(), this.applicationConfig);
          SharedDataUtils.put(vertx.getDelegate(), this.webClient);

          log.info("Loaded ApplicationConfig: pulseServerUrl={}", this.applicationConfig.getPulseServerUrl());
          return config;
        })
        .doOnSuccess(config -> {
          // Initialize crons
          initCrons();
        })
        .ignoreElement()
        .andThen(
            vertx.rxDeployVerticle(
                () -> new RestVerticle(new HttpServerOptions().setPort(4000)),
                new DeploymentOptions().setInstances(1)
            )
        )
        .doOnSuccess(deploymentId -> log.info("REST server started on port 4000"))
        .ignoreElement();
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

  private void initCrons() {
    this.initAlertsFromDbOnce();
  }

  private void initAlertsFromDbOnce() {
    if (alertCronMethodCalled.compareAndSet(false, true)) {
      initAlertsFromDb();
    }
  }

  private void initAlertsFromDb() {
    ObjectMapper objectMapper = GuiceInjector.getGuiceInjector().getInstance(ObjectMapper.class);

    CronManager cronManager = new CronManager(vertx, webClient);
    AlertsService alertsService = new AlertsService(webClient, applicationConfig, objectMapper);

    alertsService
        .getAlerts()
        .map(alerts -> {
          alerts.forEach(alert -> cronManager.addCronTask(alert.getAlertId(), alert.getUrl(), alert.getEvaluationInterval()));
          return alerts;
        })
        .onErrorResumeNext(e -> {
          log.error(Constants.ERROR_INIT_MESSAGE + e.getMessage());
          return Single.error(e);
        }).subscribe((alerts, throwable) -> {
          if (throwable != null) {
            log.error(Constants.ERROR_INIT_MESSAGE + "{}", throwable.getMessage());
          } else {
            log.info(Constants.ALERTS_FETCHED_MESSAGE + "{}", alerts.size());
          }
        });
  }


  @Override
  public Completable rxStop() {
    if (webClient != null) {
      webClient.close();
    }
    return Completable.complete()
        .doOnComplete(() -> log.info("MainVerticle stopped successfully"));
  }
}
