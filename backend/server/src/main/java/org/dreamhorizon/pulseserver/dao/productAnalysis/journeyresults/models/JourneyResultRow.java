package org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row from {@code otel.journey_results} (latest-run query).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JourneyResultRow {

  @JsonProperty("direction")
  private String direction;

  @JsonProperty("posFrom")
  private Integer posFrom;

  @JsonProperty("eventFrom")
  private String eventFrom;

  @JsonProperty("posTo")
  private Integer posTo;

  @JsonProperty("eventTo")
  private String eventTo;

  @JsonProperty("userCount")
  private Long userCount;

  @JsonProperty("runTime")
  private Instant runTime;
}
