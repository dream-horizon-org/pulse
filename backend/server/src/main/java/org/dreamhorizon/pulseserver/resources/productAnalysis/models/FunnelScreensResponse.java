package org.dreamhorizon.pulseserver.resources.productAnalysis.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET {@code /v1/funnels/screens} — screen names for funnel/journey path pickers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelScreensResponse {

  /** Distinct {@code ScreenName} from {@code otel_traces} where {@code PulseType = screen_load}. */
  private List<String> screens;
}
