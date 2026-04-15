package org.dreamhorizon.pulseserver.service.interaction;

/**
 * OTEL / ClickHouse values for interaction telemetry ({@code otel.otel_traces}).
 */
public final class InteractionTelemetryConstants {

  private InteractionTelemetryConstants() {}

  /** {@code PulseType} column value for UI interaction spans. */
  public static final String INTERACTION_PULSE_TYPE = "interaction";
}
