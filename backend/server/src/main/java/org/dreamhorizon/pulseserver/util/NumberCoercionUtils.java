package org.dreamhorizon.pulseserver.util;

import lombok.experimental.UtilityClass;

/**
 * Coerces loosely-typed values (e.g. JDBC / query row cells) to primitives with safe defaults.
 */
@UtilityClass
public class NumberCoercionUtils {

  public static long toLong(Object value) {
    if (value == null) {
      return 0L;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(value.toString());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  public static double toDouble(Object value) {
    if (value == null) {
      return 0d;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(value.toString());
    } catch (NumberFormatException e) {
      return 0d;
    }
  }
}
