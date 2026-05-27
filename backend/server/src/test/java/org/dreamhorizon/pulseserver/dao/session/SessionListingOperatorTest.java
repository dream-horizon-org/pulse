package org.dreamhorizon.pulseserver.dao.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SessionListingOperatorTest {

  @Nested
  class EqualsOperator {

    @Test
    void shouldGenerateSqlForNumericValue() {
      String sql = SessionListingOperator.EQ.toSql("duration", 1000);

      assertThat(sql).isEqualTo("duration = 1000");
    }

    @Test
    void shouldGenerateSqlForStringValue() {
      String sql = SessionListingOperator.EQ.toSql("platform", "ios");

      assertThat(sql).isEqualTo("platform = 'ios'");
    }

    @Test
    void shouldLowercaseStringValue() {
      String sql = SessionListingOperator.EQ.toSql("platform", "iOS");

      assertThat(sql).isEqualTo("platform = 'ios'");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.EQ.getDisplayName()).isEqualTo("equals");
    }

    @Test
    void shouldHaveSingleValueType() {
      assertThat(SessionListingOperator.EQ.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.SINGLE);
    }
  }

  @Nested
  class NotEqualsOperator {

    @Test
    void shouldGenerateSqlForNumericValue() {
      String sql = SessionListingOperator.NEQ.toSql("crashes", 0);

      assertThat(sql).isEqualTo("crashes != 0");
    }

    @Test
    void shouldGenerateSqlForStringValue() {
      String sql = SessionListingOperator.NEQ.toSql("platform", "web");

      assertThat(sql).isEqualTo("platform != 'web'");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.NEQ.getDisplayName()).isEqualTo("does not equal");
    }

    @Test
    void shouldHaveSingleValueType() {
      assertThat(SessionListingOperator.NEQ.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.SINGLE);
    }
  }

  @Nested
  class InOperator {

    @Test
    void shouldGenerateSqlForSingleItem() {
      List<String> values = List.of("ios");
      String sql = SessionListingOperator.IN.toSql("platform", values);

      assertThat(sql).isEqualTo("platform IN ('ios')");
    }

    @Test
    void shouldGenerateSqlForMultipleItems() {
      List<String> values = Arrays.asList("ios", "android", "web");
      String sql = SessionListingOperator.IN.toSql("platform", values);

      assertThat(sql).isEqualTo("platform IN ('ios', 'android', 'web')");
    }

    @Test
    void shouldLowercaseStringValuesInList() {
      List<String> values = Arrays.asList("iOS", "Android");
      String sql = SessionListingOperator.IN.toSql("platform", values);

      assertThat(sql).isEqualTo("platform IN ('ios', 'android')");
    }

    @Test
    void shouldHandleNumericValuesInList() {
      List<Integer> values = Arrays.asList(1, 2, 3);
      String sql = SessionListingOperator.IN.toSql("version_code", values);

      assertThat(sql).isEqualTo("version_code IN (1, 2, 3)");
    }

    @Test
    void shouldThrowForNonCollectionValue() {
      assertThatThrownBy(() -> SessionListingOperator.IN.toSql("platform", "ios"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("IN operator requires a Collection value");
    }

    @Test
    void shouldThrowForNullCollection() {
      assertThatThrownBy(() -> SessionListingOperator.IN.toSql("platform", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("IN operator requires a Collection value");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.IN.getDisplayName()).isEqualTo("is one of");
    }

    @Test
    void shouldHaveArrayValueType() {
      assertThat(SessionListingOperator.IN.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.ARRAY);
    }
  }

  @Nested
  class GreaterThanOperator {

    @Test
    void shouldGenerateSqlForNumericValue() {
      String sql = SessionListingOperator.GT.toSql("duration", 5000);

      assertThat(sql).isEqualTo("duration > 5000");
    }

    @Test
    void shouldGenerateSqlForDoubleValue() {
      String sql = SessionListingOperator.GT.toSql("quality_score", 0.8);

      assertThat(sql).isEqualTo("quality_score > 0.8");
    }

    @Test
    void shouldThrowForNonNumericValue() {
      assertThatThrownBy(() -> SessionListingOperator.GT.toSql("duration", "not-a-number"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expected numeric value");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.GT.getDisplayName()).isEqualTo("greater than");
    }

    @Test
    void shouldHaveSingleValueType() {
      assertThat(SessionListingOperator.GT.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.SINGLE);
    }
  }

  @Nested
  class LessThanOperator {

    @Test
    void shouldGenerateSqlForNumericValue() {
      String sql = SessionListingOperator.LT.toSql("duration", 1000);

      assertThat(sql).isEqualTo("duration < 1000");
    }

    @Test
    void shouldGenerateSqlForFloatValue() {
      String sql = SessionListingOperator.LT.toSql("quality_score", 0.5);

      assertThat(sql).isEqualTo("quality_score < 0.5");
    }

    @Test
    void shouldThrowForNonNumericValue() {
      assertThatThrownBy(() -> SessionListingOperator.LT.toSql("duration", "abc"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expected numeric value");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.LT.getDisplayName()).isEqualTo("less than");
    }

    @Test
    void shouldHaveSingleValueType() {
      assertThat(SessionListingOperator.LT.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.SINGLE);
    }
  }

  @Nested
  class GreaterThanOrEqualOperator {

    @Test
    void shouldGenerateSqlForNumericValue() {
      String sql = SessionListingOperator.GTE.toSql("crashes", 0);

      assertThat(sql).isEqualTo("crashes >= 0");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.GTE.getDisplayName()).isEqualTo("greater than or equal to");
    }

    @Test
    void shouldHaveSingleValueType() {
      assertThat(SessionListingOperator.GTE.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.SINGLE);
    }
  }

  @Nested
  class LessThanOrEqualOperator {

    @Test
    void shouldGenerateSqlForNumericValue() {
      String sql = SessionListingOperator.LTE.toSql("errors", 10);

      assertThat(sql).isEqualTo("errors <= 10");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.LTE.getDisplayName()).isEqualTo("less than or equal to");
    }

    @Test
    void shouldHaveSingleValueType() {
      assertThat(SessionListingOperator.LTE.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.SINGLE);
    }
  }

  @Nested
  class BetweenOperator {

    @Test
    void shouldGenerateSqlForValidRange() {
      List<Integer> range = Arrays.asList(1000, 5000);
      String sql = SessionListingOperator.BETWEEN.toSql("duration", range);

      assertThat(sql).isEqualTo("duration BETWEEN 1000 AND 5000");
    }

    @Test
    void shouldGenerateSqlForFloatRange() {
      List<Double> range = Arrays.asList(0.0, 1.0);
      String sql = SessionListingOperator.BETWEEN.toSql("quality_score", range);

      assertThat(sql).isEqualTo("quality_score BETWEEN 0.0 AND 1.0");
    }

    @Test
    void shouldThrowForNonCollectionValue() {
      assertThatThrownBy(() -> SessionListingOperator.BETWEEN.toSql("duration", 1000))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("BETWEEN operator requires a Collection of exactly 2 values");
    }

    @Test
    void shouldThrowForSingleValueInCollection() {
      List<Integer> range = List.of(1000);
      assertThatThrownBy(() -> SessionListingOperator.BETWEEN.toSql("duration", range))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("BETWEEN operator requires a Collection of exactly 2 values");
    }

    @Test
    void shouldThrowForMoreThanTwoValues() {
      List<Integer> range = Arrays.asList(1000, 5000, 10000);
      assertThatThrownBy(() -> SessionListingOperator.BETWEEN.toSql("duration", range))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("BETWEEN operator requires a Collection of exactly 2 values");
    }

    @Test
    void shouldThrowForNonNumericBounds() {
      List<String> range = Arrays.asList("min", "max");
      assertThatThrownBy(() -> SessionListingOperator.BETWEEN.toSql("duration", range))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expected numeric value");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.BETWEEN.getDisplayName()).isEqualTo("between");
    }

    @Test
    void shouldHaveRangeValueType() {
      assertThat(SessionListingOperator.BETWEEN.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.RANGE);
    }
  }

  @Nested
  class EmptyOperator {

    @Test
    void shouldGenerateSqlIgnoringValue() {
      String sql = SessionListingOperator.EMPTY.toSql("user_id", null);

      assertThat(sql).isEqualTo("user_id = ''");
    }

    @Test
    void shouldGenerateSqlRegardlessOfProvidedValue() {
      String sql = SessionListingOperator.EMPTY.toSql("user_id", "some_value");

      assertThat(sql).isEqualTo("user_id = ''");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.EMPTY.getDisplayName()).isEqualTo("is empty");
    }

    @Test
    void shouldHaveNoneValueType() {
      assertThat(SessionListingOperator.EMPTY.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.NONE);
    }
  }

  @Nested
  class NotEmptyOperator {

    @Test
    void shouldGenerateSqlIgnoringValue() {
      String sql = SessionListingOperator.NOT_EMPTY.toSql("user_id", null);

      assertThat(sql).isEqualTo("user_id != ''");
    }

    @Test
    void shouldGenerateSqlRegardlessOfProvidedValue() {
      String sql = SessionListingOperator.NOT_EMPTY.toSql("user_id", "irrelevant");

      assertThat(sql).isEqualTo("user_id != ''");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.NOT_EMPTY.getDisplayName()).isEqualTo("is not empty");
    }

    @Test
    void shouldHaveNoneValueType() {
      assertThat(SessionListingOperator.NOT_EMPTY.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.NONE);
    }
  }

  @Nested
  class IsNullOperator {

    @Test
    void shouldGenerateSqlIgnoringValue() {
      String sql = SessionListingOperator.IS_NULL.toSql("optional_field", null);

      assertThat(sql).isEqualTo("optional_field IS NULL");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.IS_NULL.getDisplayName()).isEqualTo("is null");
    }

    @Test
    void shouldHaveNoneValueType() {
      assertThat(SessionListingOperator.IS_NULL.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.NONE);
    }
  }

  @Nested
  class IsNotNullOperator {

    @Test
    void shouldGenerateSqlIgnoringValue() {
      String sql = SessionListingOperator.IS_NOT_NULL.toSql("optional_field", "anything");

      assertThat(sql).isEqualTo("optional_field IS NOT NULL");
    }

    @Test
    void shouldHaveCorrectDisplayName() {
      assertThat(SessionListingOperator.IS_NOT_NULL.getDisplayName()).isEqualTo("is not null");
    }

    @Test
    void shouldHaveNoneValueType() {
      assertThat(SessionListingOperator.IS_NOT_NULL.getValueType())
          .isEqualTo(SessionListingOperator.ValueType.NONE);
    }
  }

  @Nested
  class QuoteValueHelper {

    @Test
    void shouldNotQuoteNumericValue() {
      String result = SessionListingOperator.EQ.toSql("field", 42);

      assertThat(result).contains("42");
    }

    @Test
    void shouldQuoteStringValue() {
      String result = SessionListingOperator.EQ.toSql("field", "text");

      assertThat(result).contains("'");
    }

    @Test
    void shouldEscapeSingleQuotesInString() {
      String result = SessionListingOperator.EQ.toSql("field", "can't");

      assertThat(result).contains("\\'");
    }

    @Test
    void shouldEscapeBackslashesInString() {
      String result = SessionListingOperator.EQ.toSql("field", "path\\to\\file");

      assertThat(result).contains("\\\\");
    }

    @Test
    void shouldHandleComplexEscaping() {
      String result = SessionListingOperator.EQ.toSql("field", "it's\\mine");

      assertThat(result).contains("\\'");
      assertThat(result).contains("\\\\");
    }
  }

  @Nested
  class NumericValueHelper {

    @Test
    void shouldReturnNumericValueAsString() {
      String result = SessionListingOperator.GT.toSql("field", 100);

      assertThat(result).contains("100");
    }

    @Test
    void shouldHandleDoubleValues() {
      String result = SessionListingOperator.GT.toSql("field", 99.99);

      assertThat(result).contains("99.99");
    }

    @Test
    void shouldParseStringNumbersToDouble() {
      String result = SessionListingOperator.GT.toSql("field", "123.45");

      assertThat(result).contains("123.45");
    }

    @Test
    void shouldThrowForInvalidNumericString() {
      assertThatThrownBy(() -> SessionListingOperator.GT.toSql("field", "not_a_number"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expected numeric value");
    }
  }

  @Nested
  class OperatorEnumProperties {

    @Test
    void shouldHaveAllOperators() {
      SessionListingOperator[] operators = SessionListingOperator.values();

      assertThat(operators).isNotEmpty();
      assertThat(operators.length).isGreaterThanOrEqualTo(10);
    }

    @Test
    void shouldReturnOperatorByName() {
      SessionListingOperator op = SessionListingOperator.valueOf("EQ");

      assertThat(op).isEqualTo(SessionListingOperator.EQ);
    }

    @Test
    void shouldHaveNonEmptyDisplayNames() {
      for (SessionListingOperator op : SessionListingOperator.values()) {
        assertThat(op.getDisplayName()).isNotBlank();
      }
    }

    @Test
    void shouldHaveValidValueTypes() {
      for (SessionListingOperator op : SessionListingOperator.values()) {
        assertThat(op.getValueType()).isNotNull();
      }
    }
  }

  @Nested
  class ValueTypeEnum {

    @Test
    void shouldHaveSingleValueType() {
      assertThat(SessionListingOperator.ValueType.SINGLE).isNotNull();
    }

    @Test
    void shouldHaveArrayValueType() {
      assertThat(SessionListingOperator.ValueType.ARRAY).isNotNull();
    }

    @Test
    void shouldHaveRangeValueType() {
      assertThat(SessionListingOperator.ValueType.RANGE).isNotNull();
    }

    @Test
    void shouldHaveNoneValueType() {
      assertThat(SessionListingOperator.ValueType.NONE).isNotNull();
    }

    @Test
    void shouldReturnAllValueTypes() {
      SessionListingOperator.ValueType[] types = SessionListingOperator.ValueType.values();

      assertThat(types).hasSize(4);
      assertThat(types).contains(
          SessionListingOperator.ValueType.SINGLE,
          SessionListingOperator.ValueType.ARRAY,
          SessionListingOperator.ValueType.RANGE,
          SessionListingOperator.ValueType.NONE
      );
    }
  }

  @Nested
  class SqlGeneration {

    @Test
    void shouldHandleMultipleFilters() {
      String sql1 = SessionListingOperator.GT.toSql("duration", 1000);
      String sql2 = SessionListingOperator.LT.toSql("crashes", 5);

      assertThat(sql1).isEqualTo("duration > 1000");
      assertThat(sql2).isEqualTo("crashes < 5");
    }

    @Test
    void shouldProduceValidClickHouseSyntax() {
      String sql = SessionListingOperator.IN.toSql("platform", Arrays.asList("ios", "android"));

      assertThat(sql).matches("platform IN \\('ios', 'android'\\)");
    }

    @Test
    void shouldHandleSpecialCharactersInFields() {
      String sql = SessionListingOperator.EQ.toSql("ResourceAttributes['os.version']", "14");

      assertThat(sql).contains("ResourceAttributes['os.version']");
      assertThat(sql).contains("'14'");
    }
  }
}
