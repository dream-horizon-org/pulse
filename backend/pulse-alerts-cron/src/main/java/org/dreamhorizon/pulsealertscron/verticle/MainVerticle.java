package org.dreamhorizon.pulsealertscron.verticle;

import org.dreamhorizon.pulsealertscron.client.mysql.MysqlClient;
import org.dreamhorizon.pulsealertscron.client.mysql.MysqlClientImpl;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.constant.Constants;
import org.dreamhorizon.pulsealertscron.guice.GuiceInjector;
import org.dreamhorizon.pulsealertscron.services.AlertsService;
import org.dreamhorizon.pulsealertscron.services.CronManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class MainVerticle extends AbstractVerticle {

  private MysqlClient mysqlClient;
  private WebClient webClient;
  private ApplicationConfig applicationConfig;

  private static final AtomicBoolean alertCronMethodCalled = new AtomicBoolean(false);
  private static final AtomicBoolean populateMetricInitiated = new AtomicBoolean(false);
  private static final AtomicBoolean populateAndIngestAnomalyInitiated = new AtomicBoolean(false);

  @Override
  public Completable rxStart() {
    // Get config from Guice
    this.applicationConfig = GuiceInjector.getGuiceInjector().getInstance(ApplicationConfig.class);

    // Initialize clients
    this.mysqlClient = new MysqlClientImpl(vertx.getDelegate());
    this.webClient = GuiceInjector.getGuiceInjector().getInstance(WebClient.class);

    // Connect to MySQL and deploy REST server
    return mysqlClient.rxConnect()
        .doOnComplete(() -> {
          log.info("MySQL client connected successfully");
          initCrons();
        })
        .andThen(
            vertx.rxDeployVerticle(
                () -> new RestVerticle(new HttpServerOptions().setPort(4000)),
                new DeploymentOptions().setInstances(1)
            )
        )
        .doOnSuccess(deploymentId -> log.info("REST server started on port 4000"))
        .ignoreElement();
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
    if (mysqlClient != null) {
      mysqlClient.close();
    }
    return Completable.complete()
        .doOnComplete(() -> log.info("MainVerticle stopped successfully"));
  }
}

