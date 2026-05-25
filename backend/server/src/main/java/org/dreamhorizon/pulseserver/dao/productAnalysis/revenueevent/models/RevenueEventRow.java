package org.dreamhorizon.pulseserver.dao.productAnalysis.revenueevent.models;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueEventRow {

  private String id;
  private String projectId;
  private String eventName;
  private String valueAttribute;
  private String currency;
  private String currencyAttribute;
  private Integer conversionWindowHours;
  private String configuredBy;
  private Instant configuredAt;
  private Instant updatedAt;
}
