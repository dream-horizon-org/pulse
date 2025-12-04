package org.dreamhorizon.pulseserver.service.alert.core.operatror;


import static org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator.GREATER_THAN;
import static org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator.GREATER_THAN_EQUAL;
import static org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator.LESS_THAN;
import static org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator.LESS_THAN_EQUAL;

import org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator;
import com.google.inject.Inject;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class MetricOperatorFactory {

  private final Map<MetricOperator, MetricOperatorProcessor> metricOperatorMap;

  @Inject
  public MetricOperatorFactory(
      GreaterThanMetricOperatorProcessor greaterThanOperatorProcessor,
      LessThanMetricOperatorProcessor lessThanOperatorProcessor,
      GreaterThanEqualToMetricOperatorProcessor greaterThanEqualToOperatorProcessor,
      LessThanEqualToMetricOperatorProcessor lessThanEqualToOperatorProcessor) {

    this.metricOperatorMap = Map.of(
        GREATER_THAN, greaterThanOperatorProcessor,
        GREATER_THAN_EQUAL, greaterThanEqualToOperatorProcessor,
        LESS_THAN, lessThanOperatorProcessor,
        LESS_THAN_EQUAL, lessThanEqualToOperatorProcessor
    );
  }

  public MetricOperatorProcessor getProcessor(MetricOperator metricOperator) {
    MetricOperatorProcessor processor = metricOperatorMap.get(metricOperator);
    if (processor == null) {
      throw new IllegalArgumentException("Unsupported MetricOperator: " + metricOperator);
    }
    return processor;
  }

}
