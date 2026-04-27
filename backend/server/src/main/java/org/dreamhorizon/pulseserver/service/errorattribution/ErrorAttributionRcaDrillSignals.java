package org.dreamhorizon.pulseserver.service.errorattribution;

import java.util.List;

/**
 * Canonical drill signals for RCA enrichment, post-AI merge, and pulse_ai prompt contract. Crash
 * is intentionally excluded; add {@link ErrorAttributionDrillDownSignal#crash} here and in merger
 * together when product should include it.
 */
public final class ErrorAttributionRcaDrillSignals {

  public static final List<ErrorAttributionDrillDownSignal> CANONICAL_FOR_RCA =
      List.of(
          ErrorAttributionDrillDownSignal.anr,
          ErrorAttributionDrillDownSignal.non_fatal,
          ErrorAttributionDrillDownSignal.api);

  private ErrorAttributionRcaDrillSignals() {}
}
