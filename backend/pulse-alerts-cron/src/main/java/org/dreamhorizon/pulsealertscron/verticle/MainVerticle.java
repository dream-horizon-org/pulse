package org.dreamhorizon.pulsealertscron.verticle;

import org.dreamhorizon.pulsealertscron.client.mysql.MysqlClient;
import org.dreamhorizon.pulsealertscron.client.mysql.MysqlClientImpl;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.constant.Constants;
import org.dreamhorizon.pulsealertscron.guice.GuiceInjector;
import org.dreamhorizon.pulsealertscron.services.AlertsService;
import org.dreamhorizon.pulsealertscron.services.CronManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
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
    public void start(Promise<Void> startPromise) {
        try {
            // Get config from Guice
            this.applicationConfig = GuiceInjector.getInstance(ApplicationConfig.class);
            
            // Initialize clients
            this.mysqlClient = new MysqlClientImpl(vertx);
            this.webClient = WebClient.create(io.vertx.rxjava3.core.Vertx.newInstance(vertx));

            // Connect to MySQL
            mysqlClient.rxConnect()
                    .doOnComplete(() -> {
                        log.info("MySQL client connected successfully");
                        initCrons();
                    })
                    .subscribe(
                            () -> startPromise.complete(),
                            error -> {
                                log.error("Failed to start MainVerticle", error);
                                startPromise.fail(error);
                            }
                    );
        } catch (Exception e) {
            log.error("Error starting MainVerticle", e);
            startPromise.fail(e);
        }
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
        ObjectMapper objectMapper = GuiceInjector.getInstance(ObjectMapper.class);

        CronManager cronManager = new CronManager(io.vertx.rxjava3.core.Vertx.newInstance(vertx), webClient);
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
    public void stop(Promise<Void> stopPromise) {
        try {
            if (mysqlClient != null) {
                mysqlClient.close();
            }
            if (webClient != null) {
                webClient.close();
            }
            log.info("MainVerticle stopped successfully");
            stopPromise.complete();
        } catch (Exception e) {
            log.error("Error stopping MainVerticle", e);
            stopPromise.fail(e);
        }
    }
}

