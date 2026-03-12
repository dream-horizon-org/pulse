package org.dreamhorizon.pulseserver.service.usagelimit.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single usage limit value with its configuration.
 * Used in tier defaults and project usage limits.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageLimitValue {
  @JsonProperty("displayName")
  @JsonAlias("display_name")
  private String displayName;

  @JsonProperty("windowType")
  @JsonAlias("window_type")
  private String windowType;

  @JsonProperty("dataType")
  @JsonAlias("data_type")
  private String dataType;

  private Long value;
  private Integer overage;
  private Long finalThreshold;
}

