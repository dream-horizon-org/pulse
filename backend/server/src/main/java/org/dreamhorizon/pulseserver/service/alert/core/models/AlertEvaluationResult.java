package org.dreamhorizon.pulseserver.service.alert.core.models;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AlertEvaluationResult {
  @NotNull
  LocalTime startTime;

  @NotNull
  LocalDateTime evaluationWindowStart;

  @NotNull
  LocalDateTime evaluationWindowEnd;

  @NotNull
  Alert alertDetails;

  List<Reading> metricReadings;

  @Getter
  @Builder
  public static class Reading {
    @NotNull
    Float reading;

    @NotNull
    String useCaseId;

    @NotNull
    Integer successInteractionCount;

    @NotNull
    Integer errorInteractionCount;

    @NotNull
    Integer totalInteractionCount;

    @NotNull
    LocalDateTime timestamp;
  }
}
