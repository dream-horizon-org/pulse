package org.dreamhorizon.pulseserver.resources.v1.insight.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
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
public class GetInsightJobResponse {
  private String jobId;
  private String insightType;
  private String executionMode;
  private String status;
  private String pollUrl;
  private Instant createdAt;
  private Instant startedAt;
  private Instant completedAt;
  private JsonNode report;
  private String errorMessage;
  private Boolean cached;
  private Instant cachedAt;
}
