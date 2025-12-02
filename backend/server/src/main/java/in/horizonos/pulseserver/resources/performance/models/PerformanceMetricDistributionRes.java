package in.horizonos.pulseserver.resources.performance.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PerformanceMetricDistributionRes {
  private List<String> fields;
  private List<List<String>> rows;
}
