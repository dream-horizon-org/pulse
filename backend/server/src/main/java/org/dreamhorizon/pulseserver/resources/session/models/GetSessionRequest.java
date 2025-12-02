package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GetSessionRequest {
  @NonNull
  @JsonProperty("startTime")
  private String startTime;

  @NonNull
  @JsonProperty("endTime")
  private String endTime;

  @NonNull
  @JsonProperty("spanName")
  private String spanName;


  @JsonProperty("filters")
  private Filters filters;


  @Getter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Filters {

    @JsonProperty("platform")
    private List<String> platformFilters;

    @JsonProperty("osVersion")
    private List<String> osVersionFilters;

    @JsonProperty("appVersion")
    private List<String> appVersionFilters;

    @JsonProperty("networkProvider")
    private List<String> networkProviderFilters;

    @JsonProperty("state")
    private List<String> stateFilters;
  }


}