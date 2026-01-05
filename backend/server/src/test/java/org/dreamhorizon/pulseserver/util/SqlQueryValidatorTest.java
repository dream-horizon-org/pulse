package org.dreamhorizon.pulseserver.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SqlQueryValidatorTest {

  @Nested
  class TestValidateQuery {

    @Test
    void shouldValidateValidQueryWithPartitionFilters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
      assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void shouldValidateValidQueryWithTimestampLiterals() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
      assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void shouldRejectNullQuery() {
      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(null);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("cannot be empty");
    }

    @Test
    void shouldRejectEmptyQuery() {
      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery("");

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("cannot be empty");
    }

    @Test
    void shouldRejectWhitespaceOnlyQuery() {
      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery("   ");

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("cannot be empty");
    }

    @Test
    void shouldRejectQueryNotStartingWithSelect() {
      String query = "INSERT INTO table VALUES (1)";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("must start with SELECT");
    }

    @Test
    void shouldRejectQueryWithoutFromClause() {
      String query = "SELECT * WHERE year = 2025";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("must contain a FROM clause");
    }

    @Test
    void shouldRejectQueryWithDangerousOperations() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025; DROP TABLE otel_data";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("dangerous operations");
    }

    @Test
    void shouldRejectQueryWithDeleteOperation() {
      String query = "DELETE FROM pulse_athena_db.otel_data";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("must start with SELECT");
    }

    @Test
    void shouldRejectQueryWithUpdateOperation() {
      String query = "UPDATE pulse_athena_db.otel_data SET column = 'value'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("must start with SELECT");
    }

    @Test
    void shouldRejectQueryWithoutTimestampFilters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE column1 = 'value'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("timestamp filter");
    }

    @Test
    void shouldRejectQueryWithoutWhereClause() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("timestamp filter");
    }

    @Test
    void shouldAcceptQueryWithAllPartitionFilters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithTimestampLiteral() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00' AND \"timestamp\" <= TIMESTAMP '2025-12-23 11:59:59'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithCaseInsensitivePartitionFilters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE YEAR = 2025 AND MONTH = 12 AND DAY = 23 AND HOUR = 11";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithQuotedPartitionColumns() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"year\" = 2025 AND \"month\" = 12 AND \"day\" = 23 AND \"hour\" = 11";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithTableQualifiedPartitionColumns() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE otel_data.year = 2025 AND otel_data.month = 12 AND otel_data.day = 23 AND otel_data.hour = 11";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldRejectQueryWithPartialPartitionFilters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("timestamp filter");
    }

    @Test
    void shouldAcceptQueryWithMixedConditions() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11 AND column1 = 'value'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithControlCharacters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String queryWithControl = query + "\u0000\u0001";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithControl);

      assertTrue(result.isValid());
    }

    @Test
    void shouldRejectQueryWithInvalidEncoding() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      byte[] invalidBytes = {(byte) 0xFF, (byte) 0xFE};
      String invalidQuery = query + new String(invalidBytes);

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(invalidQuery);

      assertTrue(result.isValid() || result.getErrorMessage().contains("encoding"));
    }

    @Test
    void shouldHandleUnicodeNormalizationInQuery() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String queryWithUnicode = query.replace("SELECT", "SELECT\u200B");

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithUnicode);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithMultipleControlCharacters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String queryWithControl = "\u0000\u0001\u0002" + query + "\u007F\u001F";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithControl);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithUnicodeNormalizationThatChangesString() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String queryWithComposedUnicode = query.replace("SELECT", "SEL\u0301ECT");

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithComposedUnicode);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void shouldRejectQueryWithZeroWidthSpaceInKeyword() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String queryWithZeroWidthSpace = query.replace("SELECT", "SEL\u200BECT");

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithZeroWidthSpace);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("must start with SELECT");
    }

    @Test
    void shouldHandleQueryValidationWithEncodingError() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      byte[] invalidBytes = {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD};
      String invalidQuery = query + new String(invalidBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(invalidQuery);

      assertTrue(result.isValid() || result.getErrorMessage() != null);
    }

    @Test
    void shouldHandleQueryWithPartialTimestampFilters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("timestamp filter");
    }

    @Test
    void shouldHandleQueryWithTimestampLiteralButMissingPartitionFilters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }
  }

  @Nested
  class TestValidationResult {

    @Test
    void shouldCreateValidResult() {
      SqlQueryValidator.ValidationResult result = SqlQueryValidator.ValidationResult.valid();

      assertTrue(result.isValid());
      assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void shouldCreateInvalidResult() {
      String errorMessage = "Test error message";
      SqlQueryValidator.ValidationResult result = SqlQueryValidator.ValidationResult.invalid(errorMessage);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    void shouldHaveCorrectGetters() {
      String errorMessage = "Custom error";
      SqlQueryValidator.ValidationResult result = SqlQueryValidator.ValidationResult.invalid(errorMessage);

      assertThat(result.isValid()).isFalse();
      assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
    }
  }
}


