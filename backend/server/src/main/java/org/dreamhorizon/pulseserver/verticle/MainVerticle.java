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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClientImpl;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.dreamhorizon.pulseserver.config.ConfigUtils;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;
import org.dreamhorizon.pulseserver.service.notification.queue.NotificationWorker;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

@Slf4j
public class MainVerticle extends AbstractVerticle {

  private WebClient webClient;
  private MysqlClient mysqlClient;

  @Override
  public Completable rxStart() {
    Completable completable =
        ConfigUtils.getConfigRetriever(vertx)
            .rxGetConfig()
            .map(
                config -> {
                  JsonObject appConfig = config.getJsonObject("app", new JsonObject());

                  JsonObject mysqlConfig = config.getJsonObject("mysql", new JsonObject());
                  JsonObject webClientConfig = config.getJsonObject("webclient", new JsonObject());


          this.mysqlClient = new MysqlClientImpl(this.vertx, mysqlConfig);
          this.webClient = WebClient.create(vertx, getWebClientOptions(webClientConfig));
          SharedDataUtils.put(vertx.getDelegate(), appConfig.mapTo(ApplicationConfig.class));
          JsonObject chConfig = config.getJsonObject("clickhouse", new JsonObject());
          SharedDataUtils.put(vertx.getDelegate(), chConfig.mapTo(ClickhouseConfig.class));
          JsonObject athenaConfig = config.getJsonObject("athena", new JsonObject());
          SharedDataUtils.put(vertx.getDelegate(), athenaConfig.mapTo(AthenaConfig.class));
                    JsonObject notificationConfig =
                            config.getJsonObject("notification", new JsonObject());
                    SharedDataUtils.put(
                            vertx.getDelegate(), notificationConfig.mapTo(NotificationConfig.class));

          // Initialize OpenFGA configuration
          JsonObject openfgaJson = config.getJsonObject("openfga", new JsonObject());
          String apiUrl = openfgaJson.getString("apiUrl", "http://localhost:8080");
          String storeId = openfgaJson.getString("storeId", "");
          String modelId = openfgaJson.getString("authorizationModelId", "");
          // Handle enabled as string or boolean (env vars are strings)
          boolean enabled = false;
          Object enabledValue = openfgaJson.getValue("enabled");
          if (enabledValue instanceof Boolean) {
            enabled = (Boolean) enabledValue;
          } else if (enabledValue instanceof String) {
            enabled = Boolean.parseBoolean((String) enabledValue);
          }

          // If enabled but missing IDs, try to fetch from OpenFGA
          if (enabled && (storeId == null || storeId.isEmpty())) {
            log.info("OpenFGA enabled but no storeId configured, attempting to fetch from {}...", apiUrl);
            try {
              storeId = fetchOpenFgaStoreId(apiUrl, "pulse-authorization");
              if (storeId != null && !storeId.isEmpty()) {
                modelId = fetchLatestModelId(apiUrl, storeId);
                log.info("Fetched OpenFGA config - storeId: {}, modelId: {}", storeId, modelId);
              }
            } catch (Exception e) {
              log.warn("Failed to fetch OpenFGA config: {}. Authorization will be disabled.", e.getMessage());
              enabled = false;
            }
          }

          OpenFgaConfig openfgaConfig = OpenFgaConfig.builder()
              .apiUrl(apiUrl)
              .storeId(storeId != null ? storeId : "")
              .authorizationModelId(modelId != null ? modelId : "")
              .enabled(enabled && storeId != null && !storeId.isEmpty())
              .build();
          SharedDataUtils.put(vertx.getDelegate(), openfgaConfig);
          log.info("OpenFGA config initialized - enabled: {}, apiUrl: {}, storeId: {}",
              openfgaConfig.isEnabled(), openfgaConfig.getApiUrl(), openfgaConfig.getStoreId());

          SharedDataUtils.put(vertx.getDelegate(), mysqlClient);
          SharedDataUtils.put(vertx.getDelegate(), webClient);
          return config;
        })
        .ignoreElement()
        .andThen(
            vertx.rxDeployVerticle(
                () ->
                    new RestVerticle(
                        new HttpServerOptions().setPort(8080)),
                new DeploymentOptions().setInstances(getNumOfCores()))
        ).ignoreElement()
        .doOnComplete(this::startNotificationWorker);

    if (Objects.equals(System.getenv("KAFKA_ENABLED"), "true")) {
      return completable
          .andThen(
              (vertx.rxDeployVerticle(
                  AnrCrashLogConsumerVerticle::new,
                  new DeploymentOptions().setInstances(getNumOfCores()))))
          .ignoreElement();
    }
    return completable;
  }

  private Integer getNumOfCores() {
    return CpuCoreSensor.availableProcessors();
  }

  private void startNotificationWorker() {
    try {
      NotificationWorker worker = GuiceInjector.getGuiceInjector().getInstance(NotificationWorker.class);
      worker.start();
      log.info("Notification worker started successfully");
    } catch (Exception e) {
      log.warn("Failed to start notification worker: {}", e.getMessage());
    }
  }

  private void stopNotificationWorker() {
    try {
      NotificationWorker worker = GuiceInjector.getGuiceInjector().getInstance(NotificationWorker.class);
      worker.stop();
      log.info("Notification worker stopped");
    } catch (Exception e) {
      log.warn("Error stopping notification worker: {}", e.getMessage());
    }
  }

  /**
   * Fetch the store ID from OpenFGA by store name.
   */
  private String fetchOpenFgaStoreId(String apiUrl, String storeName) {
    try {
      java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
      java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
          .uri(java.net.URI.create(apiUrl + "/stores"))
          .GET()
          .build();

      java.net.http.HttpResponse<String> response = client.send(request,
          java.net.http.HttpResponse.BodyHandlers.ofString());

      String body = response.body();
      // Parse JSON to find store with matching name
      // Looking for pattern: "id": "xxx", "name": "pulse-authorization" (handles spaces)
      String pattern = "\"id\":\\s*\"([^\"]+)\"\\s*,\\s*\"name\":\\s*\"" + storeName + "\"";
      java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
      java.util.regex.Matcher m = p.matcher(body);
      if (m.find()) {
        return m.group(1);
      }
      log.warn("Store '{}' not found in OpenFGA. Response: {}", storeName, body.substring(0, Math.min(200, body.length())));
      return null;
    } catch (Exception e) {
      log.error("Failed to fetch OpenFGA store ID: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Fetch the latest authorization model ID from OpenFGA.
   */
  private String fetchLatestModelId(String apiUrl, String storeId) {
    try {
      java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
      java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
          .uri(java.net.URI.create(apiUrl + "/stores/" + storeId + "/authorization-models"))
          .GET()
          .build();

      java.net.http.HttpResponse<String> response = client.send(request,
          java.net.http.HttpResponse.BodyHandlers.ofString());

      String body = response.body();
      // Parse JSON to find first model ID (handles spaces in JSON)
      // Looking for pattern: "id": "xxx"
      java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"id\":\\s*\"([^\"]+)\"");
      java.util.regex.Matcher m = p.matcher(body);
      if (m.find()) {
        return m.group(1);
      }
      log.warn("No authorization models found in store {}. Response: {}", storeId, body.substring(0, Math.min(200, body.length())));
      return null;
    } catch (Exception e) {
      log.error("Failed to fetch OpenFGA model ID: {}", e.getMessage());
      return null;
    }
  }

  private WebClientOptions getWebClientOptions(JsonObject config) {
    return new WebClientOptions()
        .setConnectTimeout(Integer.parseInt(config.getString(HTTP_CONNECT_TIMEOUT)))
        .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
        .setKeepAlive(Boolean.parseBoolean(config.getString(HTTP_CLIENT_KEEP_ALIVE)))
        .setKeepAliveTimeout(
            Integer.parseInt(config.getString(HTTP_CLIENT_KEEP_ALIVE_TIMEOUT)) / 1000)
        .setIdleTimeout(Integer.parseInt(config.getString(HTTP_CLIENT_IDLE_TIMEOUT)))
        .setMaxPoolSize(Integer.parseInt(config.getString(HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE)))
        .setReadIdleTimeout(Integer.parseInt(config.getString(HTTP_READ_TIMEOUT)))
        .setWriteIdleTimeout(Integer.parseInt(config.getString(HTTP_WRITE_TIMEOUT)));
  }

  @Override
  public Completable rxStop() {
    stopNotificationWorker();
    
    try {
      ClickhouseProjectConnectionPoolManager poolManager =
          SharedDataUtils.get(vertx.getDelegate(), ClickhouseProjectConnectionPoolManager.class);
      if (poolManager != null) {
        poolManager.closeAllPools();
        log.info("Closed all project connection pools");
      }
    } catch (Exception e) {
      log.warn("Error closing project pools", e);
    }

    this.webClient.close();
    return mysqlClient.rxClose();
  }
}
