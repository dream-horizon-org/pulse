package org.dreamhorizon.pulseserver.service.alert.core.operatror;

public class LessThanEqualToMetricOperatorProcessor implements MetricOperatorProcessor {
  @Override
  public boolean isFiring(Float threshold, Float actualValue) {
    return actualValue <= threshold;
  }
}
