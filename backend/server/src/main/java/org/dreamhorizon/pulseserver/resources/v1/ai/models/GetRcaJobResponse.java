package org.dreamhorizon.pulseserver.resources.v1.ai.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Polling response for async RCA report generation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetRcaJobResponse {

  private String jobId;
  private String status;
  private Instant createdAt;
  private Instant startedAt;
  private Instant completedAt;
  private JsonNode report;
  private String errorMessage;
  private String pollUrl;
  /** True when {@link #report} was loaded from MySQL cache. */
  private Boolean cached;
  /** Present when status is COMPLETED and cache row exists. */
  private Instant cachedAt;
}
