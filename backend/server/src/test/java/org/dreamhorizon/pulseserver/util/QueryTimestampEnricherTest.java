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

      assertThat(result).contains("year = 2025");
      assertThat(result).contains("month = 12");
      assertThat(result).contains("day = 23");
      assertThat(result).contains("hour = 11");
    }

    @Test
    void shouldAppendPartitionFilterToExistingWhereClause() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025 AND month = 12 AND day = 23 AND hour = 11");
      assertThat(result).contains("timestamp");
    }

    @Test
    void shouldHandleWhereClauseStartingWithAnd() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE AND \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";
      String timestamp = "2025-12-23 11:29:35";

      String result = QueryTimestampEnricher.enrichQueryWithTimestamp(query, timestamp);

      assertThat(result).contains("year = 2025");
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

      assertThat(result).contains("hour = 10");
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
  }
}


