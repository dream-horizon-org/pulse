package org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pre-computed journey path graph from ClickHouse — matches the journey explorer payload
 * ({@code nodes} + {@code links}) from POST {@code /v1/journey/explore}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JourneyResultsResponse {

  private List<JourneySankeyNode> nodes;

  private List<JourneySankeyLink> links;
}
