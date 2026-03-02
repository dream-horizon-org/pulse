package org.dreamhorizon.pulseserver.resources.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelSessionsRequest {

  @NotNull
  @NotEmpty
  private List<FunnelStep> steps;

  @NotNull
  private QueryRequest.TimeRange timeRange;

  private List<QueryRequest.Filter> filters;

  @Builder.Default
  private FunnelRequest.FunnelMode mode = FunnelRequest.FunnelMode.UNIQUE_USERS;

  @Builder.Default
  private Long windowSeconds = 86400L;

  @Min(1)
  private int stepLevel;

  @Builder.Default
  private String issueType = "ALL";

  @Builder.Default
  private Integer limit = 100;

  private String tenantId;
}
