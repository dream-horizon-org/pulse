package org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sankey edge — same shape as POST {@code /v1/journey/explore} {@code links} entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JourneySankeyLink {

  private String source;

  private String target;

  private long value;
}
