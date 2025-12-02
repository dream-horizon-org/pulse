package org.dreamhorizon.pulseserver.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ClickhouseConstants {

  public static final String CH_APDEX_SELECT_CLAUSE =
      "avgIf(toFloat64OrNull(SpanAttributes['pulse.interaction.apdex_score']), StatusCode != 'Error')";
  public static final String CH_ANR_SELECT_CLAUSE = "countIf(has(Events.Name, 'device.anr'))";
  public static final String CH_CRASH_SELECT_CLAUSE = "countIf(has(Events.Name, 'device.crash'))";
  public static final String CH_FROZEN_FRAME_SELECT_CLAUSE = "sum(toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count']))";
  public static final String CH_ANALYSED_FRAME_SELECT_CLAUSE =
      "sum(toFloat64OrZero(SpanAttributes['app.interaction.analysed_frame_count']))";
  public static final String CH_UNANALYSED_FRAME_SELECT_CLAUSE =
      "sum(toFloat64OrZero(SpanAttributes['app.interaction.unanalysed_frame_count ']))";
  public static final String CH_DURATION_P99_SELECT_CLAUSE = "quantileTDigestIf(0.99)(Duration / 1e6, StatusCode != 'Error')";
  public static final String CH_DURATION_P95_SELECT_CLAUSE = "quantileTDigestIf(0.95)(Duration / 1e6, StatusCode != 'Error')";
  public static final String CH_DURATION_P50_SELECT_CLAUSE = "quantileTDigestIf(0.50)(Duration / 1e6, StatusCode != 'Error')";
  public static final String CH_TIME_BUCKET_SELECT_CLAUSE = "toDateTime(intDiv(toUnixTimestamp(%s, 'UTC'), %s) * %s,'UTC')";

  public static final String SUC_IN_CNT = "countIf(StatusCode != 'Error')";
  public static final String ERR_IN_CNT = "countIf(StatusCode = 'Error')";
  public static final String ERR_DIST_USERS = "uniqExactIf(nullIf(UserId, ''), StatusCode = 'Error')";
  public static final String EXCELLENT_CAT = "countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Excellent')";
  public static final String GOOD_CAT = "countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Good')";
  public static final String AVERAGE_CAT = "countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Average')";
  public static final String POOR_CAT = "countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor')";

  public static final String NET_0 = "sum(arrayCount(x -> x = 'network.0', Events.Name))";
  public static final String NET_2XX = "sum(arrayCount(x -> x LIKE 'network.2%', Events.Name))";
  public static final String NET_3XX = "sum(arrayCount(x -> x LIKE 'network.3%', Events.Name))";
  public static final String NET_4XX = "sum(arrayCount(x -> x LIKE 'network.4%', Events.Name))";
  public static final String NET_5XX = "sum(arrayCount(x -> x LIKE 'network.5%', Events.Name))";

  public static final String ARR_TO_STR = "arrayStringConcat(arrayMap(x -> toString(x), %s), ',')";
}