package org.dreamhorizon.pulseserver.service.usagelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UsageLimitValidatorTest {

  // ==================== VALIDATE LIMIT VALUES TESTS ====================

  @Nested
  class TestValidateLimitValues {

    @Test
    void shouldPassValidationForValidLimits() {
      Map<String, UsageLimitValue> validLimits = new HashMap<>();
      validLimits.put("max_events", UsageLimitValue.builder()
          .displayName("Max Events")
          .value(1000L)
          .overage(10)
          .build());
      validLimits.put("max_projects", UsageLimitValue.builder()
          .displayName("Max Projects")
          .value(5L)
          .overage(0)
          .build());

      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(validLimits));
    }

    @Test
    void shouldPassValidationForNullMap() {
      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(null));
    }

    @Test
    void shouldPassValidationForEmptyMap() {
      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(new HashMap<>()));
    }

    @Test
    void shouldPassValidationForNullValue() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_events", null);

      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(limits));
    }

    @Test
    void shouldPassValidationForZeroValue() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_events", UsageLimitValue.builder()
          .displayName("Max Events")
          .value(0L)
          .overage(0)
          .build());

      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(limits));
    }

    @Test
    void shouldPassValidationForNullValueField() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_events", UsageLimitValue.builder()
          .displayName("Max Events")
          .value(null)
          .overage(10)
          .build());

      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(limits));
    }

    @Test
    void shouldPassValidationForNullOverageField() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_events", UsageLimitValue.builder()
          .displayName("Max Events")
          .value(1000L)
          .overage(null)
          .build());

      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(limits));
    }

    @Test
    void shouldPassValidationForMaxOverage100() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_events", UsageLimitValue.builder()
          .displayName("Max Events")
          .value(1000L)
          .overage(100)
          .build());

      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(limits));
    }

    @Test
    void shouldFailValidationForNegativeValue() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_events", UsageLimitValue.builder()
          .displayName("Max Events")
          .value(-100L)
          .overage(10)
          .build());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateLimitValues(limits));
      assertTrue(ex.getMessage().contains("max_events"));
      assertTrue(ex.getMessage().contains("non-negative"));
    }

    @Test
    void shouldFailValidationForNegativeOverage() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_projects", UsageLimitValue.builder()
          .displayName("Max Projects")
          .value(10L)
          .overage(-5)
          .build());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateLimitValues(limits));
      assertTrue(ex.getMessage().contains("max_projects"));
      assertTrue(ex.getMessage().contains("between 0 and 100"));
    }

    @Test
    void shouldFailValidationForOverageGreaterThan100() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_users", UsageLimitValue.builder()
          .displayName("Max Users")
          .value(50L)
          .overage(150)
          .build());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateLimitValues(limits));
      assertTrue(ex.getMessage().contains("max_users"));
      assertTrue(ex.getMessage().contains("between 0 and 100"));
    }

    @Test
    void shouldFailValidationForOverageOf101() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_storage", UsageLimitValue.builder()
          .displayName("Max Storage")
          .value(1000L)
          .overage(101)
          .build());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateLimitValues(limits));
      assertTrue(ex.getMessage().contains("between 0 and 100"));
    }

    @Test
    void shouldReportFirstInvalidKeyInMessage() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("specific_key_name", UsageLimitValue.builder()
          .displayName("Test")
          .value(-1L)
          .build());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateLimitValues(limits));
      assertTrue(ex.getMessage().contains("specific_key_name"));
    }

    @Test
    void shouldValidateMultipleLimits() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("max_events", UsageLimitValue.builder().value(1000L).overage(10).build());
      limits.put("max_projects", UsageLimitValue.builder().value(5L).overage(0).build());
      limits.put("max_users", UsageLimitValue.builder().value(100L).overage(20).build());
      limits.put("max_storage", UsageLimitValue.builder().value(10000L).overage(50).build());

      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(limits));
    }
  }

  // ==================== VALIDATE NON-NEGATIVE TESTS ====================

  @Nested
  class TestValidateNonNegative {

    @Test
    void shouldPassForPositiveValue() {
      assertDoesNotThrow(() -> UsageLimitValidator.validateNonNegative(100L, "testField"));
    }

    @Test
    void shouldPassForZeroValue() {
      assertDoesNotThrow(() -> UsageLimitValidator.validateNonNegative(0L, "testField"));
    }

    @Test
    void shouldPassForNullValue() {
      assertDoesNotThrow(() -> UsageLimitValidator.validateNonNegative(null, "testField"));
    }

    @Test
    void shouldPassForLargePositiveValue() {
      assertDoesNotThrow(() -> UsageLimitValidator.validateNonNegative(Long.MAX_VALUE, "testField"));
    }

    @Test
    void shouldFailForNegativeValue() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateNonNegative(-1L, "myField"));
      assertEquals("myField must be non-negative", ex.getMessage());
    }

    @Test
    void shouldFailForLargeNegativeValue() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateNonNegative(Long.MIN_VALUE, "testField"));
      assertTrue(ex.getMessage().contains("non-negative"));
    }

    @Test
    void shouldIncludeFieldNameInErrorMessage() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateNonNegative(-50L, "customFieldName"));
      assertTrue(ex.getMessage().contains("customFieldName"));
    }
  }

  // ==================== VALIDATE OVERAGE TESTS ====================

  @Nested
  class TestValidateOverage {

    @Test
    void shouldPassForValidOverageOf0() {
      assertDoesNotThrow(() -> UsageLimitValidator.validateOverage(0, "testField"));
    }

    @Test
    void shouldPassForValidOverageOf50() {
      assertDoesNotThrow(() -> UsageLimitValidator.validateOverage(50, "testField"));
    }

    @Test
    void shouldPassForValidOverageOf100() {
      assertDoesNotThrow(() -> UsageLimitValidator.validateOverage(100, "testField"));
    }

    @Test
    void shouldPassForNullOverage() {
      assertDoesNotThrow(() -> UsageLimitValidator.validateOverage(null, "testField"));
    }

    @Test
    void shouldFailForNegativeOverage() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateOverage(-1, "myField"));
      assertEquals("myField must be between 0 and 100", ex.getMessage());
    }

    @Test
    void shouldFailForOverageGreaterThan100() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateOverage(101, "myField"));
      assertTrue(ex.getMessage().contains("between 0 and 100"));
    }

    @Test
    void shouldFailForLargeOverage() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateOverage(1000, "testField"));
      assertTrue(ex.getMessage().contains("between 0 and 100"));
    }

    @Test
    void shouldIncludeFieldNameInErrorMessage() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
          () -> UsageLimitValidator.validateOverage(200, "customOverageField"));
      assertTrue(ex.getMessage().contains("customOverageField"));
    }
  }

  // ==================== BOUNDARY VALUE TESTS ====================

  @Nested
  class TestBoundaryValues {

    @Test
    void shouldPassForValueOfOne() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("test", UsageLimitValue.builder().value(1L).build());
      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(limits));
    }

    @Test
    void shouldFailForValueOfNegativeOne() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("test", UsageLimitValue.builder().value(-1L).build());
      assertThrows(IllegalArgumentException.class, () -> UsageLimitValidator.validateLimitValues(limits));
    }

    @Test
    void shouldPassForOverageOfOne() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("test", UsageLimitValue.builder().value(100L).overage(1).build());
      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(limits));
    }

    @Test
    void shouldPassForOverageOf99() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("test", UsageLimitValue.builder().value(100L).overage(99).build());
      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(limits));
    }

    @Test
    void shouldPassForMaxLongValue() {
      Map<String, UsageLimitValue> limits = new HashMap<>();
      limits.put("test", UsageLimitValue.builder().value(Long.MAX_VALUE).overage(0).build());
      assertDoesNotThrow(() -> UsageLimitValidator.validateLimitValues(limits));
    }
  }
}
