package org.dreamhorizon.pulseserver.service.alert.core.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GetAllAlertsResponse {
  private List<Alert> alerts;
}