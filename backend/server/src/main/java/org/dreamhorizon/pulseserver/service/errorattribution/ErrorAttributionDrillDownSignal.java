package org.dreamhorizon.pulseserver.service.errorattribution;

/** Per-signal discriminator for error-attribution drill-down (Track B). */
public enum ErrorAttributionDrillDownSignal {
  crash,
  anr,
  non_fatal,
  api;
}
