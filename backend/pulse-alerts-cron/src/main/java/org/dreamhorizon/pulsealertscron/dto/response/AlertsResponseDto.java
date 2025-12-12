package org.dreamhorizon.pulsealertscron.dto.response;

import org.dreamhorizon.pulsealertscron.models.Alert;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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