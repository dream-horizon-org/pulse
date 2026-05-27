package org.dreamhorizon.pulseserver.dao.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingFilterCategory.DEVICE;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingFilterCategory.GEOGRAPHY;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingFilterCategory.SESSION;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingFilterCategory.STABILITY;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingFilterCategory.UI_INTERACTIONS;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingFilterCategory.USER;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingFilterField.ClauseType.HAVING;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingFilterField.ClauseType.WHERE;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingOperator.BETWEEN;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingOperator.EQ;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingOperator.EMPTY;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingOperator.GT;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingOperator.GTE;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingOperator.IN;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingOperator.LT;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingOperator.LTE;
import static org.dreamhorizon.pulseserver.dao.session.SessionListingOperator.NOT_EMPTY;

import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SessionListingFilterFieldTest {

  @Nested
  class SessionProperties {

    @Test
    void shouldHaveDurationField() {
      SessionListingFilterField field = SessionListingFilterField.DURATION;

      assertThat(field.getExpression()).contains("dateDiff('millisecond'");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Duration (ms)");
      assertThat(field.getDataType()).isEqualTo("integer");
      assertThat(field.getCategory()).isEqualTo(SESSION);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(GT, LT, GTE, LTE, BETWEEN);
    }

    @Test
    void shouldHaveQualityScoreField() {
      SessionListingFilterField field = SessionListingFilterField.QUALITY_SCORE;

      assertThat(field.getExpression()).contains("sum(apdexCount)");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Quality Score");
      assertThat(field.getDataType()).isEqualTo("float");
      assertThat(field.getCategory()).isEqualTo(SESSION);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(GT, LT, GTE, LTE, BETWEEN);
    }

    @Test
    void shouldHaveSpanCountField() {
      SessionListingFilterField field = SessionListingFilterField.SPAN_COUNT;

      assertThat(field.getExpression()).isEqualTo("sum(spanCount)");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Span Count");
      assertThat(field.getDataType()).isEqualTo("integer");
      assertThat(field.getCategory()).isEqualTo(SESSION);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(GT, LT, EQ);
    }

    @Test
    void shouldHaveHasUserIdField() {
      SessionListingFilterField field = SessionListingFilterField.HAS_USER_ID;

      assertThat(field.getExpression()).contains("anyIf(userId");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Has User ID");
      assertThat(field.getDataType()).isEqualTo("string");
      assertThat(field.getCategory()).isEqualTo(SESSION);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(EMPTY, NOT_EMPTY);
    }

    @Test
    void shouldHaveScreenNameField() {
      SessionListingFilterField field = SessionListingFilterField.SCREEN_NAME;

      assertThat(field.getExpression()).contains("ScreenName");
      assertThat(field.getClauseType()).isEqualTo(WHERE);
      assertThat(field.isInMV()).isFalse();
      assertThat(field.getDisplayName()).isEqualTo("Screen Name");
      assertThat(field.getDataType()).isEqualTo("string");
      assertThat(field.getCategory()).isEqualTo(SESSION);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(EQ, IN);
    }
  }

  @Nested
  class UserProperties {

    @Test
    void shouldHaveUserIdField() {
      SessionListingFilterField field = SessionListingFilterField.USER_ID;

      assertThat(field.getExpression()).contains("lower(anyIf(userId");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("User ID");
      assertThat(field.getDataType()).isEqualTo("string");
      assertThat(field.getCategory()).isEqualTo(USER);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(EQ);
    }
  }

  @Nested
  class DeviceProperties {

    @Test
    void shouldHavePlatformField() {
      SessionListingFilterField field = SessionListingFilterField.PLATFORM;

      assertThat(field.getExpression()).contains("lower(anyIf(platform");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Platform");
      assertThat(field.getDataType()).isEqualTo("string");
      assertThat(field.getCategory()).isEqualTo(DEVICE);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(EQ, IN);
    }

    @Test
    void shouldHaveAppVersionField() {
      SessionListingFilterField field = SessionListingFilterField.APP_VERSION;

      assertThat(field.getExpression()).contains("lower(anyIf(appVersion");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("App Version");
      assertThat(field.getDataType()).isEqualTo("string");
      assertThat(field.getCategory()).isEqualTo(DEVICE);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(EQ, IN);
    }

    @Test
    void shouldHaveOsVersionField() {
      SessionListingFilterField field = SessionListingFilterField.OS_VERSION;

      assertThat(field.getExpression()).contains("lower(anyIf(osVersion");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("OS Version");
      assertThat(field.getDataType()).isEqualTo("string");
      assertThat(field.getCategory()).isEqualTo(DEVICE);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(EQ, IN);
    }

    @Test
    void shouldHaveDeviceModelField() {
      SessionListingFilterField field = SessionListingFilterField.DEVICE_MODEL;

      assertThat(field.getExpression()).contains("lower(anyIf(deviceModel");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Device Model");
      assertThat(field.getDataType()).isEqualTo("string");
      assertThat(field.getCategory()).isEqualTo(DEVICE);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(EQ, IN);
    }

    @Test
    void shouldHaveNetworkProviderField() {
      SessionListingFilterField field = SessionListingFilterField.NETWORK_PROVIDER;

      assertThat(field.getExpression()).contains("lower(anyIf(networkProvider");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Network Provider");
      assertThat(field.getDataType()).isEqualTo("string");
      assertThat(field.getCategory()).isEqualTo(DEVICE);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(EQ, IN);
    }
  }

  @Nested
  class UIInteractionProperties {

    @Test
    void shouldHaveFailedInteractionsField() {
      SessionListingFilterField field = SessionListingFilterField.FAILED_INTERACTIONS;

      assertThat(field.getExpression()).isEqualTo("sum(interactionErrors)");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Failed Interactions");
      assertThat(field.getDataType()).isEqualTo("integer");
      assertThat(field.getCategory()).isEqualTo(UI_INTERACTIONS);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(GT, EQ);
    }

    @Test
    void shouldHaveSlowInteractionsField() {
      SessionListingFilterField field = SessionListingFilterField.SLOW_INTERACTIONS;

      assertThat(field.getExpression()).isEqualTo("sum(slowInteractionCount)");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Slow Interactions");
      assertThat(field.getDataType()).isEqualTo("integer");
      assertThat(field.getCategory()).isEqualTo(UI_INTERACTIONS);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(GT, EQ);
    }

    @Test
    void shouldHaveFrozenFramesField() {
      SessionListingFilterField field = SessionListingFilterField.FROZEN_FRAMES;

      assertThat(field.getExpression()).isEqualTo("sum(frozenFrameCount)");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Frozen Frames");
      assertThat(field.getDataType()).isEqualTo("float");
      assertThat(field.getCategory()).isEqualTo(UI_INTERACTIONS);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(GT, EQ);
    }

    @Test
    void shouldHaveInteractionNameField() {
      SessionListingFilterField field = SessionListingFilterField.INTERACTION_NAME;

      assertThat(field.getExpression()).contains("SpanAttributes['pulse.interaction.name']");
      assertThat(field.getClauseType()).isEqualTo(WHERE);
      assertThat(field.isInMV()).isFalse();
      assertThat(field.getDisplayName()).isEqualTo("Interaction Name");
      assertThat(field.getDataType()).isEqualTo("string");
      assertThat(field.getCategory()).isEqualTo(UI_INTERACTIONS);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(EQ, IN);
    }
  }

  @Nested
  class StabilityProperties {

    @Test
    void shouldHaveCrashesField() {
      SessionListingFilterField field = SessionListingFilterField.CRASHES;

      assertThat(field.getExpression()).isEqualTo("sum(crashCount)");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Crashes");
      assertThat(field.getDataType()).isEqualTo("integer");
      assertThat(field.getCategory()).isEqualTo(STABILITY);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(GT, EQ);
    }

    @Test
    void shouldHaveANRsField() {
      SessionListingFilterField field = SessionListingFilterField.ANRS;

      assertThat(field.getExpression()).isEqualTo("sum(anrCount)");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("ANRs");
      assertThat(field.getDataType()).isEqualTo("integer");
      assertThat(field.getCategory()).isEqualTo(STABILITY);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(GT, EQ);
    }

    @Test
    void shouldHaveNonFatalsField() {
      SessionListingFilterField field = SessionListingFilterField.NON_FATALS;

      assertThat(field.getExpression()).isEqualTo("sum(nonFatal)");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Non-Fatals");
      assertThat(field.getDataType()).isEqualTo("integer");
      assertThat(field.getCategory()).isEqualTo(STABILITY);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(GT, EQ);
    }

    @Test
    void shouldHaveNetworkErrorsField() {
      SessionListingFilterField field = SessionListingFilterField.NETWORK_ERRORS;

      assertThat(field.getExpression()).isEqualTo("sum(networkErrors)");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Network Errors");
      assertThat(field.getDataType()).isEqualTo("integer");
      assertThat(field.getCategory()).isEqualTo(STABILITY);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(GT, EQ);
    }
  }

  @Nested
  class GeographyProperties {

    @Test
    void shouldHaveCountryField() {
      SessionListingFilterField field = SessionListingFilterField.COUNTRY;

      assertThat(field.getExpression()).contains("lower(anyIf(geoCountry");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Country");
      assertThat(field.getDataType()).isEqualTo("string");
      assertThat(field.getCategory()).isEqualTo(GEOGRAPHY);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(EQ, IN);
    }

    @Test
    void shouldHaveRegionField() {
      SessionListingFilterField field = SessionListingFilterField.REGION;

      assertThat(field.getExpression()).contains("lower(anyIf(geoRegion");
      assertThat(field.getClauseType()).isEqualTo(HAVING);
      assertThat(field.isInMV()).isTrue();
      assertThat(field.getDisplayName()).isEqualTo("Region / State");
      assertThat(field.getDataType()).isEqualTo("string");
      assertThat(field.getCategory()).isEqualTo(GEOGRAPHY);
      assertThat(field.getAllowedOperators()).containsExactlyInAnyOrder(EQ, IN);
    }
  }

  @Nested
  class FieldGetters {

    @Test
    void shouldReturnAllEnumValues() {
      SessionListingFilterField[] fields = SessionListingFilterField.values();

      assertThat(fields).isNotEmpty();
      assertThat(fields.length).isGreaterThanOrEqualTo(20);
    }

    @Test
    void shouldReturnFieldByName() {
      SessionListingFilterField field = SessionListingFilterField.valueOf("DURATION");

      assertThat(field).isEqualTo(SessionListingFilterField.DURATION);
    }

    @Test
    void shouldHaveNonEmptyDisplayNames() {
      for (SessionListingFilterField field : SessionListingFilterField.values()) {
        assertThat(field.getDisplayName()).isNotBlank();
      }
    }

    @Test
    void shouldHaveNonEmptyExpressions() {
      for (SessionListingFilterField field : SessionListingFilterField.values()) {
        assertThat(field.getExpression()).isNotBlank();
      }
    }

    @Test
    void shouldHaveNonEmptyDataTypes() {
      for (SessionListingFilterField field : SessionListingFilterField.values()) {
        assertThat(field.getDataType()).isNotBlank();
      }
    }

    @Test
    void shouldHaveValidCategories() {
      for (SessionListingFilterField field : SessionListingFilterField.values()) {
        assertThat(field.getCategory()).isNotNull();
      }
    }

    @Test
    void shouldHaveNonEmptyAllowedOperators() {
      for (SessionListingFilterField field : SessionListingFilterField.values()) {
        Set<SessionListingOperator> operators = field.getAllowedOperators();
        assertThat(operators).isNotEmpty();
      }
    }

    @Test
    void shouldHaveValidClauseTypes() {
      for (SessionListingFilterField field : SessionListingFilterField.values()) {
        assertThat(field.getClauseType()).isIn(WHERE, HAVING);
      }
    }
  }

  @Nested
  class ClauseTypeEnum {

    @Test
    void shouldHaveWhereClauseType() {
      assertThat(SessionListingFilterField.ClauseType.WHERE).isNotNull();
    }

    @Test
    void shouldHaveHavingClauseType() {
      assertThat(SessionListingFilterField.ClauseType.HAVING).isNotNull();
    }

    @Test
    void shouldReturnAllClauseTypes() {
      SessionListingFilterField.ClauseType[] types = SessionListingFilterField.ClauseType.values();

      assertThat(types).hasSize(2);
      assertThat(types).contains(WHERE, HAVING);
    }
  }
}
