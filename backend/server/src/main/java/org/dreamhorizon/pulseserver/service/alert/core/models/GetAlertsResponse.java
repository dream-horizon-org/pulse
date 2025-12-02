package org.dreamhorizon.pulseserver.service.alert.core.models;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GetAlertsResponse {
  @NotNull
  Integer totalAlerts;

  @NotNull
  List<Alert> alerts;

  @NotNull
  Integer page;

  @NotNull
  Integer limit;
}
