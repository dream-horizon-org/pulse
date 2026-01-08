package org.dreamhorizon.pulseserver.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QueryTimestampEnricherTest {

  @Nested
  class TestEnrichQueryWithTimestamp {

    @Test
    void shouldEnrichQueryWithTimestampString() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
      assertThat(result).contains("month = 12");
      assertThat(result).contains("day = 23");
      assertThat(result).contains("hour = 11");
      assertThat(result).contains("WHERE");
    }

    @Test
    void shouldExtractTimestampFromWhereClause() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, null);

      assertThat(result).contains("(year, month, day, hour) >= (2025, 12, 23, 11)");
    }

    @Test
    void shouldAppendPartitionFilterToExistingWhereClause() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("(year, month, day, hour) >= (2025, 12, 23, 11)");
      assertThat(result).contains("timestamp");
    }

    @Test
    void shouldHandleWhereClauseStartingWithAnd() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE AND \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("(year, month, day, hour) >= (2025, 12, 23, 11)");
      assertThat(result).contains("AND \"timestamp\"");
    }

    @Test
    void shouldSkipEnrichmentIfPartitionFiltersAlreadyExist() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).isEqualTo(query);
    }

    @Test
    void shouldAddWhereClauseBeforeGroupBy() {
      String query = "SELECT * FROM pulse_athena_db.otel_data GROUP BY column1";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      assertThat(result).contains("GROUP BY");
      int whereIndex = result.indexOf("WHERE");
      int groupByIndex = result.indexOf("GROUP BY");
      assertThat(whereIndex).isLessThan(groupByIndex);
    }

    @Test
    void shouldAddWhereClauseBeforeOrderBy() {
      String query = "SELECT * FROM pulse_athena_db.otel_data ORDER BY column1";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      assertThat(result).contains("ORDER BY");
      int whereIndex = result.indexOf("WHERE");
      int orderByIndex = result.indexOf("ORDER BY");
      assertThat(whereIndex).isLessThan(orderByIndex);
    }

    @Test
    void shouldAddWhereClauseBeforeLimit() {
      String query = "SELECT * FROM pulse_athena_db.otel_data LIMIT 10";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      assertThat(result).contains("LIMIT");
      int whereIndex = result.indexOf("WHERE");
      int limitIndex = result.indexOf("LIMIT");
      assertThat(whereIndex).isLessThan(limitIndex);
    }

    @Test
    void shouldReturnOriginalQueryIfNoTimestampProvided() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, null);

      assertThat(result).isEqualTo(query);
    }

    @Test
    void shouldReturnOriginalQueryIfEmptyTimestampProvided() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, "");

      assertThat(result).isEqualTo(query);
    }

    @Test
    void shouldHandleTimeOnlyFormat() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      String timestamp = "11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("hour = 11");
    }

    @Test
    void shouldHandleTimeOnlyFormatWithoutPadding() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      String timestamp = "1:2:33";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("hour = 1");
    }

    @Test
    void shouldHandleMultipleTimestampLiterals() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00' AND \"timestamp\" <= TIMESTAMP '2025-12-23 11:59:59'";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, null);

      assertThat(result).contains("year = 2025");
      assertThat(result).contains("month = 12");
      assertThat(result).contains("day = 23");
      assertThat(result).contains("hour = 11");
    }

    @Test
    void shouldPreferWhereClauseTimestampOverTimestampString() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 10:00:00'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("(year, month, day, hour) >= (2025, 12, 23, 10)");
    }

    @Test
    void shouldRejectNullQuery() {
      org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
        QueryTimestampEnricher.enrichQueryWithTimestamp(null, "2025-12-23 11:29:35");
      });
    }

    @Test
    void shouldHandleQueryWithControlCharacters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      String timestamp = "2025-12-23 11:29:35";
      String queryWithControl = query + "\u0000\u0001";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(queryWithControl, timestamp);

      assertThat(result).contains("year = 2025");
      assertThat(result).doesNotContain("\u0000");
    }

    @Test
    void shouldRejectTimestampWithControlCharacters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      String timestamp = "2025-12-23\u000011:29:35";

      org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
        QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);
      });
    }

    @Test
    void shouldRejectTimestampWithInvalidCharacters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      String timestamp = "2025-12-23 11:29:35abc";

      org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
        QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);
      });
    }

    @Test
    void shouldHandleWhitespaceOnlyTimestamp() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, "   ");

      assertThat(result).isEqualTo(query);
    }

    @Test
    void shouldHandleUnicodeNormalizationInQuery() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String timestamp = "2025-12-23 11:29:35";
      String queryWithUnicode = query.replace("SELECT", "SELECT\u200B");

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(queryWithUnicode, timestamp);

      assertThat(result).contains("year = 2025");
    }

    @Test
    void shouldRejectTimestampWithInvalidUnicodeCharacters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String timestamp = "2025-12-23 11:29:35";
      String timestampWithUnicode = timestamp.replace("-", "-\u200B");

      org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
        QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestampWithUnicode);
      });
    }

    @Test
    void shouldRejectTimestampWithInvalidUTF8Encoding() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      byte[] invalidBytes = {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD};
      String invalidTimestamp = "2025-12-23 " + new String(invalidBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

      org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
        QueryTimestampEnricher.enrichQueryWithTimestamp(query, invalidTimestamp);
      });
    }

    @Test
    void shouldHandleQueryWithUnicodeCharactersInStringLiterals() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11 AND name = 'José'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
      assertThat(result).contains("José");
    }

    @Test
    void shouldHandleTimestampWithOnlySpaces() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, "    ");

      assertThat(result).isEqualTo(query);
    }

    @Test
    void shouldHandleNullTimestampAfterNormalization() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, null);

      assertThat(result).isEqualTo(query);
    }

    @Test
    void shouldHandleInvalidTimestampFormat() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String invalidTimestamp = "2025-13-45 25:99:99";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, invalidTimestamp);

      assertThat(result).isEqualTo(query);
    }


    @Test
    void shouldHandleAddWhereClauseWithNoAfterClause() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      assertThat(result).contains("year = 2025");
      assertThat(result).endsWith("hour = 11");
    }

    @Test
    void shouldHandleMultipleClausesInQuery() {
      String query = "SELECT * FROM pulse_athena_db.otel_data GROUP BY col1 ORDER BY col2 LIMIT 10";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      int whereIndex = result.indexOf("WHERE");
      int groupByIndex = result.indexOf("GROUP BY");
      assertThat(whereIndex).isLessThan(groupByIndex);
    }

    @Test
    void shouldHandleAppendPartitionFilterWhenWhereStartsWithAnd() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE AND column1 = 'value'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
      assertThat(result).contains("AND column1");
    }

    @Test
    void shouldHandleAppendPartitionFilterWhenWhereDoesNotStartWithAnd() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE column1 = 'value'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
      assertThat(result).contains("AND column1");
    }

    @Test
    void shouldHandleWhereClauseWithMultipleWhitespaces() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE    column1 = 'value'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
      assertThat(result).contains("column1");
    }

    @Test
    void shouldHandleExtractTimestampWithInvalidFormatInWhereClause() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP 'invalid-format'";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, null);

      assertThat(result).isEqualTo(query);
    }

    @Test
    void shouldHandleExtractTimestampWithMultipleInvalidFormats() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP 'invalid1' AND \"timestamp\" <= TIMESTAMP 'invalid2'";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, null);

      assertThat(result).isEqualTo(query);
    }

    @Test
    void shouldHandleTimestampWithDoubleQuotes() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP \"2025-12-23 11:00:00\"";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, null);

      assertThat(result).contains("(year, month, day, hour) >= (2025, 12, 23, 11)");
    }

    @Test
    void shouldHandleTimestampWithSingleQuotes() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, null);

      assertThat(result).contains("(year, month, day, hour) >= (2025, 12, 23, 11)");
    }

    @Test
    void shouldHandleQueryWhereTimestampExtractionFailsButTimestampStringProvided() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP 'invalid'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("(year, month, day, hour) >= (2025, 12, 23, 11)");
    }

    @Test
    void shouldHandleTimeFormatWithoutPadding() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      String timestamp = "1:2:3";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("hour = 1");
    }

    @Test
    void shouldHandleTimeFormatWithPadding() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      String timestamp = "01:02:03";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("hour = 1");
    }

    @Test
    void shouldHandleInvalidTimeFormat() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String timestamp = "25:99:99";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).isEqualTo(query);
    }

    @Test
    void shouldHandleQueryWithAllThreeClauses() {
      String query = "SELECT * FROM pulse_athena_db.otel_data GROUP BY col1 ORDER BY col2 LIMIT 10";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      int whereIndex = result.indexOf("WHERE");
      int groupByIndex = result.indexOf("GROUP BY");
      assertThat(whereIndex).isLessThan(groupByIndex);
    }

    @Test
    void shouldHandleQueryWithUTF8Recovery() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      String timestamp = "2025-12-23 11:29:35";
      String queryWithUTF8 = query + "\uFFFD";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(queryWithUTF8, timestamp);

      assertThat(result).contains("year = 2025");
    }

    @Test
    void shouldHandleAppendPartitionFilterWithWhitespaceAfterWhere() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE    column1 = 'value'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
      assertThat(result).contains("column1 = 'value'");
    }

    @Test
    void shouldHandleAppendPartitionFilterWhenAfterIsEmpty() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
    }

    @Test
    void shouldHandleExtractTimestampWithMultipleMatches() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00' AND \"timestamp\" <= TIMESTAMP '2025-12-23 11:59:59'";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, null);

      assertThat(result).contains("year = 2025");
      assertThat(result).contains("hour = 11");
    }

    @Test
    void shouldHandleExtractTimestampWhenFirstMatchFails() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP 'invalid' AND \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, null);

      assertThat(result).contains("(year, month, day, hour) >= (2025, 12, 23, 11)");
    }

    @Test
    void shouldHandleAppendPartitionFilterWhenAfterStartsWithLowercaseAnd() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE and column1 = 'value'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
      assertThat(result).contains("column1 = 'value'");
    }

    @Test
    void shouldHandleWhereAtEndOfQuery() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
      assertThat(result).contains("month = 12");
      assertThat(result).contains("day = 23");
      assertThat(result).contains("hour = 11");
      assertThat(result).contains("WHERE");
    }

    @Test
    void shouldHandleQueryWithOnlyGroupBy() {
      String query = "SELECT * FROM pulse_athena_db.otel_data GROUP BY col1";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      assertThat(result).contains("year = 2025");
      int whereIndex = result.indexOf("WHERE");
      int groupByIndex = result.indexOf("GROUP BY");
      assertThat(whereIndex).isLessThan(groupByIndex);
    }

    @Test
    void shouldHandleQueryWithOnlyOrderBy() {
      String query = "SELECT * FROM pulse_athena_db.otel_data ORDER BY col1";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      assertThat(result).contains("year = 2025");
      int whereIndex = result.indexOf("WHERE");
      int orderByIndex = result.indexOf("ORDER BY");
      assertThat(whereIndex).isLessThan(orderByIndex);
    }

    @Test
    void shouldHandleQueryWithOnlyLimit() {
      String query = "SELECT * FROM pulse_athena_db.otel_data LIMIT 10";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      assertThat(result).contains("year = 2025");
      int whereIndex = result.indexOf("WHERE");
      int limitIndex = result.indexOf("LIMIT");
      assertThat(whereIndex).isLessThan(limitIndex);
    }

    @Test
    void shouldHandleQueryWithMultipleWhereKeywords() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE column1 = 'value' AND WHERE column2 = 'value2'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
    }

    @Test
    void shouldHandleQueryWithWhereInSubquery() {
      String query = "SELECT * FROM (SELECT * FROM pulse_athena_db.otel_data WHERE column1 = 'value') WHERE column2 = 'value2'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
    }

    @Test
    void shouldHandleQueryWithCaseInsensitiveWhere() {
      String query = "SELECT * FROM pulse_athena_db.otel_data where column1 = 'value'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
    }

    @Test
    void shouldHandleQueryWithCaseInsensitiveGroupBy() {
      String query = "SELECT * FROM pulse_athena_db.otel_data group by col1";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      assertThat(result).contains("year = 2025");
    }

    @Test
    void shouldHandleQueryWithCaseInsensitiveOrderBy() {
      String query = "SELECT * FROM pulse_athena_db.otel_data order by col1";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      assertThat(result).contains("year = 2025");
    }

    @Test
    void shouldHandleQueryWithCaseInsensitiveLimit() {
      String query = "SELECT * FROM pulse_athena_db.otel_data limit 10";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("WHERE");
      assertThat(result).contains("year = 2025");
    }

    @Test
    void shouldHandleQueryWithAllClausesCaseInsensitive() {
      String query = "SELECT * FROM pulse_athena_db.otel_data where col1 = 'val' group by col2 order by col3 limit 10";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
      assertThat(result.toLowerCase()).contains("group by");
      assertThat(result.toLowerCase()).contains("order by");
      assertThat(result.toLowerCase()).contains("limit");
    }
  }
}


