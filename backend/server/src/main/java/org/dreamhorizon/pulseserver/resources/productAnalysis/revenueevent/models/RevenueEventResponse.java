package org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RevenueEventResponse {

  private String id;
  private String eventName;
  private String valueAttribute;
  private String currency;
  private String currencyAttribute;
  private Integer conversionWindowHours;
  private String configuredBy;
  private Instant configuredAt;
}
