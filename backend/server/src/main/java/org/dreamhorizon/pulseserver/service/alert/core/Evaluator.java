package org.dreamhorizon.pulseserver.service.alert.core;

import org.dreamhorizon.pulseserver.resources.alert.models.EvaluateAndTriggerAlertResponseDto;
import org.dreamhorizon.pulseserver.service.alert.core.models.Alert;
import org.dreamhorizon.pulseserver.service.alert.core.models.Metric;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.constraints.NotNull;

public interface Evaluator {
  boolean canEvaluate(Metric metric);

  Single<EvaluateAndTriggerAlertResponseDto> evaluate(@NotNull Alert alertDetails);
}
