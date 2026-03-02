package org.dreamhorizon.pulseserver.resources.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
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
public class FunnelRequest {

  @NotNull
  @NotEmpty
  private List<FunnelStep> steps;

  @NotNull
  private QueryRequest.TimeRange timeRange;

  private List<QueryRequest.Filter> filters;

  private String groupBy;

  @Builder.Default
  private FunnelMode mode = FunnelMode.UNIQUE_USERS;

  @Builder.Default
  private Long windowSeconds = 86400L;

  private String tenantId;

  public enum FunnelMode {
    UNIQUE_USERS,
    SESSIONS
  }
}
