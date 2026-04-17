package org.dreamhorizon.pulseserver.service.errorattribution;

/** Per-signal discriminator for error-attribution drill-down (Track B). */
public enum ErrorAttributionDrillDownSignal {
  crash,
  anr,
  non_fatal,
  api;

  public static ErrorAttributionDrillDownSignal fromParam(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("signal is required");
    }
    try {
      return ErrorAttributionDrillDownSignal.valueOf(raw.trim().toLowerCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "signal must be one of: crash, anr, non_fatal, api", e);
    }
  }
}
