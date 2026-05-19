package org.dreamhorizon.pulseserver.resources.v1.insight.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TriggerInsightRequest {
  private String insightType;
  private String entityKey;
  private String startDate;
  private String endDate;
  private Boolean regenerate;
}
