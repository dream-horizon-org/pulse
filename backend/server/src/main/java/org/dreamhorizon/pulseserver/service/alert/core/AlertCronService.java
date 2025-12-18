package org.dreamhorizon.pulseserver.service.alert.core;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.ext.web.client.WebClient;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.resources.alert.models.AddAlertToCronManager;
import org.dreamhorizon.pulseserver.resources.alert.models.DeleteAlertFromCronManager;
import org.dreamhorizon.pulseserver.resources.alert.models.UpdateAlertInCronManager;

@Slf4j
@Data
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AlertCronService {
  private final WebClient d11WebClient;
  private final ApplicationConfig applicationConfig;

  public Single<Boolean> createAlertCron(@NotNull AddAlertToCronManager cron) {
    log.info("Creating alert cron: {}", cron);
    log.info("Cron manager base url: {}", applicationConfig.getCronManagerBaseUrl());
    return d11WebClient
        .postAbs(applicationConfig.getCronManagerBaseUrl())
        .rxSendJson(cron)
        .retry(2)
        .map(response -> response.statusCode() == 200);
  }

  public Single<Boolean> deleteAlertCron(@NotNull DeleteAlertFromCronManager cron) {
    log.info("Deleting alert cron: {}", cron.getId());
    log.info("Cron manager base url: {}", applicationConfig.getCronManagerBaseUrl());
    return d11WebClient
        .deleteAbs(applicationConfig.getCronManagerBaseUrl())
        .rxSendJson(cron)
        .retry(2)
        .map(response -> response.statusCode() == 200);
  }

  public Single<Boolean> updateAlertCron(@NotNull UpdateAlertInCronManager cron) {
    log.info("Updating alert cron: {}", cron);
    log.info("Cron manager base url: {}", applicationConfig.getCronManagerBaseUrl());
    return d11WebClient
        .putAbs(applicationConfig.getCronManagerBaseUrl())
        .rxSendJson(cron)
        .retry(2)
        .map(response -> response.statusCode() == 200);
  }
}
