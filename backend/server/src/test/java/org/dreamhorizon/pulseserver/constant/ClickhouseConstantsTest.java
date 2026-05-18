package org.dreamhorizon.pulseserver.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClickhouseConstantsTest {

  @Nested
  class MaterializedColumnContracts {

    @Test
    void apdexSelectClauseUsesMaterializedColumn() {
      assertThat(ClickhouseConstants.CH_APDEX_SELECT_CLAUSE)
          .isEqualTo("avgIf(nullIf(ApdexScore, 0), StatusCode != 'Error')");
    }

    @Test
    void crashAndAnrSelectClausesUseMaterializedFlagColumns() {
      assertThat(ClickhouseConstants.CH_CRASH_SELECT_CLAUSE).isEqualTo("sum(HasCrashEvent)");
      assertThat(ClickhouseConstants.CH_ANR_SELECT_CLAUSE).isEqualTo("sum(HasAnrEvent)");
    }

    @Test
    void frameSelectClausesUseMaterializedColumns() {
      assertThat(ClickhouseConstants.CH_FROZEN_FRAME_SELECT_CLAUSE).isEqualTo("sum(FrozenFrameCount)");
      assertThat(ClickhouseConstants.CH_SLOW_FRAME_SELECT_CLAUSE).isEqualTo("sum(SlowFrameCount)");
      assertThat(ClickhouseConstants.CH_ANALYSED_FRAME_SELECT_CLAUSE).isEqualTo("sum(AnalysedFrameCount)");
      assertThat(ClickhouseConstants.CH_UNANALYSED_FRAME_SELECT_CLAUSE).isEqualTo("sum(UnanalysedFrameCount)");
    }

    @Test
    void networkBucketSelectClausesUseMaterializedColumns() {
      assertThat(ClickhouseConstants.NET_0).isEqualTo("sum(Net0Count)");
      assertThat(ClickhouseConstants.NET_2XX).isEqualTo("sum(Net2xxCount)");
      assertThat(ClickhouseConstants.NET_3XX).isEqualTo("sum(Net3xxCount)");
      assertThat(ClickhouseConstants.NET_4XX).isEqualTo("sum(Net4xxCount)");
      assertThat(ClickhouseConstants.NET_5XX).isEqualTo("sum(Net5xxCount)");
    }

    @Test
    void userCategoryRawIsMaterializedColumnReference() {
      assertThat(ClickhouseConstants.CH_SPAN_USER_CATEGORY_RAW).isEqualTo("UserCategory");
      assertThat(ClickhouseConstants.CH_SPAN_USER_CATEGORY_IS_POOR).isEqualTo("UserCategory = 'Poor'");
    }
  }

  @Nested
  class RateExpressionContracts {

    @Test
    void crashRateUsesHasCrashEventColumn() {
      assertThat(ClickhouseConstants.CRASH_RATE)
          .isEqualTo("if(count() = 0, NULL, (sum(HasCrashEvent)/count()) * 100)");
    }

    @Test
    void anrRateUsesHasAnrEventColumn() {
      assertThat(ClickhouseConstants.ANR_RATE)
          .isEqualTo("if(count() = 0, NULL, (sum(HasAnrEvent)/count()) * 100)");
    }

    @Test
    void frozenFrameRateUsesMaterializedFrameColumns() {
      assertThat(ClickhouseConstants.FROZEN_FRAME_RATE)
          .contains("sum(FrozenFrameCount)")
          .contains("sum(AnalysedFrameCount)")
          .contains("sum(UnanalysedFrameCount)")
          .doesNotContain("toFloat64OrZero");
    }

    @Test
    void slowFrameRateUsesMaterializedFrameColumns() {
      assertThat(ClickhouseConstants.SLOW_FRAME_RATE)
          .contains("sum(SlowFrameCount)")
          .contains("sum(AnalysedFrameCount)")
          .contains("sum(UnanalysedFrameCount)")
          .doesNotContain("toFloat64OrZero");
    }

    @Test
    void categoryRatesUseMaterializedUserCategoryColumn() {
      assertThat(ClickhouseConstants.POOR_USER_RATE)
          .contains("countIf(UserCategory = 'Poor')")
          .contains("countIf(UserCategory != '')")
          .doesNotContain("ifNull(SpanAttributes['pulse.interaction.user_category']");
      assertThat(ClickhouseConstants.AVERAGE_USER_RATE).contains("countIf(UserCategory = 'Average')");
      assertThat(ClickhouseConstants.GOOD_USER_RATE).contains("countIf(UserCategory = 'Good')");
      assertThat(ClickhouseConstants.EXCELLENT_USER_RATE).contains("countIf(UserCategory = 'Excellent')");
    }
  }

  @Nested
  class RegressionGuards {

    @Test
    void noConstantStillReadsApdexFromSpanAttributes() {
      assertThat(ClickhouseConstants.CH_APDEX_SELECT_CLAUSE).doesNotContain("SpanAttributes['pulse.interaction.apdex_score']");
    }

    @Test
    void noNetworkBucketStillUsesArrayCount() {
      assertThat(ClickhouseConstants.NET_0).doesNotContain("arrayCount");
      assertThat(ClickhouseConstants.NET_2XX).doesNotContain("arrayCount");
      assertThat(ClickhouseConstants.NET_3XX).doesNotContain("arrayCount");
      assertThat(ClickhouseConstants.NET_4XX).doesNotContain("arrayCount");
      assertThat(ClickhouseConstants.NET_5XX).doesNotContain("arrayCount");
    }
  }
}
