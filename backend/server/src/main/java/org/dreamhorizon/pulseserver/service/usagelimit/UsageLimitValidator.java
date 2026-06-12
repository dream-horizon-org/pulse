package org.dreamhorizon.pulseserver.service.usagelimit;

import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;

import java.util.Map;

/**
 * Utility class for validating usage limit values.
 * Contains pure validation methods with no DB calls.
 * Shared between TierService and UsageLimitService.
 */
public final class UsageLimitValidator {

  private UsageLimitValidator() {
    // Utility class - prevent instantiation
  }

  /**
   * Validates usage limit values.
   * Checks that:
   * - value is non-negative (if provided)
   * - overage is between 0 and 100 (if provided)
   *
   * @param limits Map of limit key to UsageLimitValue
   * @throws IllegalArgumentException if validation fails
   */
  public static void validateLimitValues(Map<String, UsageLimitValue> limits) {
    if (limits == null || limits.isEmpty()) {
      return;
    }

    for (Map.Entry<String, UsageLimitValue> entry : limits.entrySet()) {
      String key = entry.getKey();
      UsageLimitValue value = entry.getValue();

      if (value == null) {
        continue;
      }

      if (value.getValue() != null && value.getValue() < 0) {
        throw new IllegalArgumentException(
            "Value for '" + key + "' must be non-negative");
      }

      if (value.getOverage() != null && (value.getOverage() < 0 || value.getOverage() > 100)) {
        throw new IllegalArgumentException(
            "Overage for '" + key + "' must be between 0 and 100");
      }
    }
  }

  /**
   * Validates that a value is non-negative.
   *
   * @param value The value to validate
   * @param fieldName The name of the field for error messages
   * @throws IllegalArgumentException if value is negative
   */
  public static void validateNonNegative(Long value, String fieldName) {
    if (value != null && value < 0) {
      throw new IllegalArgumentException(fieldName + " must be non-negative");
    }
  }

  /**
   * Validates that overage is between 0 and 100.
   *
   * @param overage The overage percentage to validate
   * @param fieldName The name of the field for error messages
   * @throws IllegalArgumentException if overage is out of range
   */
  public static void validateOverage(Integer overage, String fieldName) {
    if (overage != null && (overage < 0 || overage > 100)) {
      throw new IllegalArgumentException(fieldName + " must be between 0 and 100");
    }
  }
}

