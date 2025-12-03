package org.dreamhorizon.pulseserver.service.alert.core.operatror;

public class GreaterThanMetricOperatorProcessor implements MetricOperatorProcessor {
  @Override
  public boolean isFiring(Float threshold, Float actualValue) {
    return actualValue > threshold;
  }
}
