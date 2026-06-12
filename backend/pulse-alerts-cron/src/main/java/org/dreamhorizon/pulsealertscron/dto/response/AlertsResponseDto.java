package org.dreamhorizon.pulsealertscron.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulsealertscron.models.Alert;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertsResponseDto {
  private AlertsData data;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class AlertsData {
    private List<Alert> alerts;
  }
} 