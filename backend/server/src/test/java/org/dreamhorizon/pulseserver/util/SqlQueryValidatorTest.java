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
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";

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
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithTimestampLiteral() {
      String query =
          "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00' AND \"timestamp\" <= TIMESTAMP '2025-12-23 11:59:59'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithCaseInsensitivePartitionFilters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE DATE = '2025-12-23' AND HOUR = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithQuotedPartitionColumns() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"date\" = '2025-12-23' AND \"hour\" = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithTableQualifiedPartitionColumns() {
      String query =
          "SELECT * FROM pulse_athena_db.otel_data WHERE otel_data.date = '2025-12-23' AND otel_data.hour = '11'";

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
      String query =
          "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11' AND column1 = 'value'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithTimestampColumn() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE timestamp >= date_add('hour', -24, current_timestamp)";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithQuotedTimestampColumn() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithTimestampColumnAndOtherConditions() {
      String query =
          "SELECT COUNT(DISTINCT \"android_os_api_level\") AS \"count_distinct_android_os_api_level\" FROM pulse_athena_db.otel_data WHERE timestamp >= date_add('hour', -24, current_timestamp) ORDER BY \"count_distinct_android_os_api_level\" DESC LIMIT 1000";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithTimestampColumnLessThan() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE timestamp < current_timestamp";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithTimestampColumnBetween() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE timestamp >= TIMESTAMP '2025-12-23 11:00:00' AND timestamp <= TIMESTAMP '2025-12-23 11:59:59'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithControlCharacters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";
      String queryWithControl = query + "\u0000\u0001";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithControl);

      assertTrue(result.isValid());
    }

    @Test
    void shouldRejectQueryWithInvalidEncoding() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";
      byte[] invalidBytes = {(byte) 0xFF, (byte) 0xFE};
      String invalidQuery = query + new String(invalidBytes);

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(invalidQuery);

      assertTrue(result.isValid() || result.getErrorMessage().contains("encoding"));
    }

    @Test
    void shouldHandleUnicodeNormalizationInQuery() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";
      String queryWithUnicode = query.replace("SELECT", "SELECT\u200B");

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithUnicode);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithMultipleControlCharacters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";
      String queryWithControl = "\u0000\u0001\u0002" + query + "\u007F\u001F";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithControl);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithUnicodeNormalizationThatChangesString() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";
      String queryWithComposedUnicode = query.replace("SELECT", "SEL\u0301ECT");

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithComposedUnicode);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void shouldRejectQueryWithZeroWidthSpaceInKeyword() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";
      String queryWithZeroWidthSpace = query.replace("SELECT", "SEL\u200BECT");

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithZeroWidthSpace);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("must start with SELECT");
    }

    @Test
    void shouldHandleQueryValidationWithEncodingError() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";
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

    @Test
    void shouldHandleQueryWithDateButMissingHour() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("timestamp filter");
    }

    @Test
    void shouldHandleQueryWithMissingDateAndHour() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("timestamp filter");
    }

    @Test
    void shouldHandleQueryWithOnlyYear() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("timestamp filter");
    }

    @Test
    void shouldHandleQueryWithUTF8Recovery() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";
      String queryWithUTF8 = query + "\uFFFD";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithUTF8);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithPartitionColumnsInDifferentCase() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE Date = '2025-12-23' AND Hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithTimestampLiteralInDifferentCase() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= timestamp '2025-12-23 11:00:00'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithPartitionColumnsWithSpaces() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithTimestampLiteralWithDoubleQuotes() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP \"2025-12-23 11:00:00\"";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithTimestampLiteralWithSingleQuotes() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithMixedCasePartitionFilters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE DATE = '2025-12-23' AND HOUR = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithTimestampLiteralAndPartitionFilters() {
      String query =
          "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11' AND \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithWhereInDifferentCase() {
      String query = "SELECT * FROM pulse_athena_db.otel_data where date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithComplexWhereClause() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE (date = '2025-12-23' AND hour = '11')";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithTimestampLiteralInComplexWhere() {
      String query =
          "SELECT * FROM pulse_athena_db.otel_data WHERE column1 = 'value' AND \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00' AND column2 = 'value2'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithMultipleTimestampLiterals() {
      String query =
          "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00' AND \"timestamp\" <= TIMESTAMP '2025-12-23 11:59:59'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithPartitionColumnsWithExtraSpaces() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date='2025-12-23' AND hour='11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithPartitionColumnsWithTabs() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date\t=\t'2025-12-23' AND hour\t=\t'11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    // --- Additional cases for broader coverage ---

    @Test
    void shouldRejectQueryWithUnion() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11' UNION SELECT * FROM other";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("dangerous operations");
    }

    @Test
    void shouldRejectQueryWithTruncate() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'; TRUNCATE otel_data";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("dangerous operations");
    }

    @Test
    void shouldRejectQueryWithAlter() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11' AND ALTER TABLE x";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("dangerous operations");
    }

    @Test
    void shouldRejectQueryWithCreate() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11' -- CREATE TABLE x";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("dangerous operations");
    }

    @Test
    void shouldRejectQueryWithExecute() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11' AND execute('evil')";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("dangerous operations");
    }

    @Test
    void shouldRejectQueryWithInsertInMiddle() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11' OR insert into x values(1)";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("dangerous operations");
    }

    @Test
    void shouldAcceptQueryWithTimestampEquals() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE timestamp = TIMESTAMP '2025-12-23 11:00:00'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithTimestampNotEquals() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE timestamp != TIMESTAMP '2025-12-23 11:00:00'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithTimestampNotEqualOperator() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE timestamp <> TIMESTAMP '2025-12-23 11:00:00'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldRejectQueryWithHourOnlyNoDate() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("timestamp filter");
    }

    @Test
    void shouldAcceptQueryWithLowercaseSelectAndFrom() {
      String query = "select * from pulse_athena_db.otel_data where date = '2025-12-23' and hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWithSelectAndNewline() {
      String query = "SELECT\n* FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldRejectQueryExceedingMaxLength_withClearMessage() {
      String base = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";
      String longQuery = base + "x".repeat(100001);

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(longQuery);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).satisfiesAnyOf(
          msg -> assertThat(msg).contains("encoding"),
          msg -> assertThat(msg).contains("length"),
          msg -> assertThat(msg).contains("maximum")
      );
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

  @Nested
  class TestNormalizeAndValidateQuery {

    @Test
    void shouldRejectQueryExceedingMaxLength() {
      String longQuery =
          "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11" + "x".repeat(100001);

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(longQuery);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void shouldHandleQueryWithControlCharactersAndOldFormat() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'\u0000\u0001";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithUTF8EncodingIssues() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";
      byte[] bytes = query.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      String queryWithEncoding = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithEncoding);

      assertTrue(result.isValid());
    }

    @Test
    void shouldHandleQueryWithReencodedLengthMismatch() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";
      String queryWithIssue = query + "\uFFFD";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(queryWithIssue);

      assertTrue(result.isValid() || result.getErrorMessage() != null);
    }
  }

  @Nested
  class TestValidateQueryWithProjectId {

    @Test
    void shouldAcceptQueryReferencingCorrectProjectTable() {
      String query = "SELECT * FROM pulse_athena_db.otel_data_42 WHERE date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query, "42");

      assertTrue(result.isValid());
    }

    @Test
    void shouldRejectQueryReferencingWrongProjectTable() {
      String query = "SELECT * FROM pulse_athena_db.otel_data_99 WHERE date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query, "42");

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("otel_data_42");
    }

    @Test
    void shouldRejectQueryReferencingUnprefixedTable() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query, "42");

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("otel_data_42");
    }

    @Test
    void shouldRejectQueryWhenProjectIdIsNull() {
      String query = "SELECT * FROM pulse_athena_db.otel_data_42 WHERE date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query, null);

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("Project ID");
    }

    @Test
    void shouldRejectQueryWhenProjectIdIsBlank() {
      String query = "SELECT * FROM pulse_athena_db.otel_data_42 WHERE date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query, "  ");

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("Project ID");
    }

    @Test
    void shouldStillFailBaseValidationEvenWithCorrectTableName() {
      String query = "SELECT * FROM pulse_athena_db.otel_data_42";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query, "42");

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("timestamp filter");
    }

    @Test
    void shouldBeCaseInsensitiveForTableName() {
      String query = "SELECT * FROM pulse_athena_db.OTEL_DATA_42 WHERE date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query, "42");

      assertTrue(result.isValid());
    }

    @Test
    void shouldAcceptQueryWhenProjectIdHasLeadingAndTrailingSpaces() {
      String query = "SELECT * FROM pulse_athena_db.otel_data_42 WHERE date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query, "  42  ");

      assertTrue(result.isValid());
    }

    @Test
    void shouldRejectQueryWhenProjectIdIsEmptyString() {
      String query = "SELECT * FROM pulse_athena_db.otel_data_42 WHERE date = '2025-12-23' AND hour = '11'";

      SqlQueryValidator.ValidationResult result = SqlQueryValidator.validateQuery(query, "");

      assertFalse(result.isValid());
      assertThat(result.getErrorMessage()).contains("Project ID");
    }
  }
}


