package org.dreamhorizon.pulseserver.service;

import org.dreamhorizon.pulseserver.dto.v1.request.incident.CreateIncidentRequestDto;
import com.google.inject.Inject;
import jakarta.validation.constraints.NotNull;

import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class IncidentService {
  final WebClient d11WebClient;

  public void createIncident(@NotNull CreateIncidentRequestDto createIncidentRequestDto) {
    log.info("Creating incident");
    d11WebClient
        .postAbs(createIncidentRequestDto.getWebhook_url())
        .putHeader("Content-Type", "application/json")
        .putHeader("org", "d11")
        .rxSendJson(createIncidentRequestDto)
        .retry(2)
        .doOnError(throwable -> log.error("Failed to create incident", throwable))
        .subscribe(response -> {
          if (response.statusCode() == 200) {
            log.info("Incident created successfully");
          } else {
            log.error("Failed to create incident");
          }
        }, error -> log.error("Failed to create incident", error));
  }
}
