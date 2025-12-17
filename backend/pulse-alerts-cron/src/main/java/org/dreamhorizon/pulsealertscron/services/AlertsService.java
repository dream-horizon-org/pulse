package org.dreamhorizon.pulsealertscron.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.dto.response.AlertsResponseDto;
import org.dreamhorizon.pulsealertscron.models.Alert;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AlertsService {
  final WebClient webClient;
  final ApplicationConfig applicationConfig;
  final ObjectMapper objectMapper;
  private static final String ALERT_EVALUATION_PATH = "/v1/alert/evaluateAndTriggerAlert?alertId=";
  private static final String HEADER_APPLICATION_JSON = "application/json";

  public Single<List<Alert>> getAlerts() {
    return webClient
        .getAbs(applicationConfig.getPulseServerUrl() + "/alerts")
        .putHeader(HttpHeaders.CONTENT_TYPE, HEADER_APPLICATION_JSON)
        .putHeader(HttpHeaders.ACCEPT, HEADER_APPLICATION_JSON)
        .rxSend()
        .map(HttpResponse::bodyAsString)
        .flatMap(response -> {

          AlertsResponseDto alertsResponse = objectMapper.readValue(response, AlertsResponseDto.class);
          List<Alert> alerts = new ArrayList<>();

          if (alertsResponse.getData() != null && alertsResponse.getData().getAlerts() != null) {
            alertsResponse.getData().getAlerts().forEach(alert -> {
              String url = applicationConfig.getPulseServerUrl() + ALERT_EVALUATION_PATH + alert.getAlertId();
              alert.setUrl(url);
              alerts.add(alert);
            });
          }

          return Single.just(alerts);
        })
        .onErrorResumeNext(err -> {
          log.error("error in fetching alerts " + err);
          return Single.error(new Exception("Failed to fetch alerts : " + err));
        });
  }
}

