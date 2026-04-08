package org.dreamhorizon.pulsespark;

import java.util.Locale;

/**
 * Normalizes filter {@code operator} strings from REST ({@code EQ}, {@code NOT_IN}) and legacy forms
 * ({@code =}, {@code NOT IN}) to a single uppercase token (spaces → underscores).
 */
public final class FunnelFilterOperators {

  private FunnelFilterOperators() {}

  /**
   * Aligns REST enum operators with legacy symbol operators for use in {@link FunnelComputeJob#applyFilters}.
   */
  public static String normalize(String operator) {
    if (operator == null || operator.isBlank()) {
      return "EQ";
    }
    return operator.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
  }
}
