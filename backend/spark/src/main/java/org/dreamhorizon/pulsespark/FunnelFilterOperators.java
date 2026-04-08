package org.dreamhorizon.pulsespark;

import java.util.Locale;

public final class FunnelFilterOperators {

  private FunnelFilterOperators() {}

  public static String normalize(String operator) {
    if (operator == null || operator.isBlank()) {
      return "EQ";
    }
    return operator.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
  }
}
