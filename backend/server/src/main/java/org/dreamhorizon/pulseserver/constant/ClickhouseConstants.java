package org.dreamhorizon.pulseserver.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ClickhouseConstants {

  public final String CH_APDEX_SELECT_CLAUSE =
      "avgIf(toFloat64OrNull(SpanAttributes['pulse.interaction.apdex_score']), StatusCode != 'Error')";
  public final String CH_ANR_SELECT_CLAUSE = "countIf(has(Events.Name, 'device.anr'))";
  public final String CH_CRASH_SELECT_CLAUSE = "countIf(has(Events.Name, 'device.crash'))";
  public final String CH_FROZEN_FRAME_SELECT_CLAUSE = "sum(toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count']))";
  public final String CH_ANALYSED_FRAME_SELECT_CLAUSE = "sum(toFloat64OrZero(SpanAttributes['app.interaction.analysed_frame_count']))";
  public final String CH_UNANALYSED_FRAME_SELECT_CLAUSE = "sum(toFloat64OrZero(SpanAttributes['app.interaction.unanalysed_frame_count ']))";
  public final String CH_DURATION_P99_SELECT_CLAUSE = "quantileTDigestIf(0.99)(Duration / 1e6, StatusCode != 'Error')";
  public final String CH_DURATION_P95_SELECT_CLAUSE = "quantileTDigestIf(0.95)(Duration / 1e6, StatusCode != 'Error')";
  public final String CH_DURATION_P50_SELECT_CLAUSE = "quantileTDigestIf(0.50)(Duration / 1e6, StatusCode != 'Error')";
  public final String CH_TIME_BUCKET_SELECT_CLAUSE = "toDateTime(intDiv(toUnixTimestamp(%s, 'UTC'), %s) * %s,'UTC')";

  public final String SUC_IN_CNT = "countIf(StatusCode != 'Error')";
  public final String ERR_IN_CNT = "countIf(StatusCode = 'Error')";
  public final String ERR_DIST_USERS = "uniqExactIf(nullIf(UserId, ''), StatusCode = 'Error')";
  public final String EXCELLENT_CAT = "countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Excellent')";
  public final String GOOD_CAT = "countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Good')";
  public final String AVERAGE_CAT = "countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Average')";
  public final String POOR_CAT = "countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor')";

  // Network metrics for interactions flow (uses Events.Name)
  public final String NET_0 = "sum(arrayCount(x -> x = 'network.0', Events.Name))";
  public final String NET_2XX = "sum(arrayCount(x -> x LIKE 'network.2%', Events.Name))";
  public final String NET_3XX = "sum(arrayCount(x -> x LIKE 'network.3%', Events.Name))";
  public final String NET_4XX = "sum(arrayCount(x -> x LIKE 'network.4%', Events.Name))";
  public final String NET_5XX = "sum(arrayCount(x -> x LIKE 'network.5%', Events.Name))";
  public final String NET_COUNT = "count()";

  // Network metrics for alerts flow (uses PulseType - network traces have status in PulseType)
  public final String NET_0_BY_PULSE_TYPE = "countIf(PulseType = 'network.0')";
  public final String NET_2XX_BY_PULSE_TYPE = "countIf(PulseType LIKE 'network.2%')";
  public final String NET_3XX_BY_PULSE_TYPE = "countIf(PulseType LIKE 'network.3%')";
  public final String NET_4XX_BY_PULSE_TYPE = "countIf(PulseType LIKE 'network.4%')";
  public final String NET_5XX_BY_PULSE_TYPE = "countIf(PulseType LIKE 'network.5%')";
  public final String NET_COUNT_BY_PULSE_TYPE = "countIf(PulseType LIKE 'network.%')";

  public final String CRASH_RATE = "if(count() = 0, NULL, (countIf(has(Events.Name, 'device.crash'))/count()) * 100)";
  public final String ANR_RATE = "if(count() = 0, NULL, (countIf(has(Events.Name, 'device.anr'))/count()) * 100)";
  public final String FROZEN_FRAME_RATE =
      "if((sum(toFloat64OrZero(SpanAttributes['app.interaction.analysed_frame_count'])) + sum(toFloat64OrZero(SpanAttributes['app.interaction.unanalysed_frame_count']))) = 0, NULL, (sum(toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count']))/(sum(toFloat64OrZero(SpanAttributes['app.interaction.analysed_frame_count'])) + sum(toFloat64OrZero(SpanAttributes['app.interaction.unanalysed_frame_count'])))) * 100)";
  public final String ERROR_RATE = "if(count() = 0, NULL, (countIf(StatusCode = 'Error')/count()) * 100)";
  public final String POOR_USER_RATE =
      "if(countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') != '') = 0, NULL, (countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor')/countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') != '')) * 100)";
  public final String AVERAGE_USER_RATE =
      "if(countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') != '') = 0, NULL, (countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Average')/countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') != '')) * 100)";
  public final String GOOD_USER_RATE =
      "if(countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') != '') = 0, NULL, (countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Good')/countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') != '')) * 100)";
  public final String EXCELLENT_USER_RATE =
      "if(countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') != '') = 0, NULL, (countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Excellent')/countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') != '')) * 100)";
  public final String LOAD_TIME =
      "if(countIf(PulseType = 'screen_load') = 0, NULL, sumIf(Duration / 1e6, PulseType = 'screen_load')/countIf(PulseType = 'screen_load'))";
  public final String SCREEN_TIME =
      "if(countIf(PulseType = 'screen_session') = 0, NULL, sumIf(Duration / 1e9, PulseType = 'screen_session')/countIf(PulseType = 'screen_session'))";
  public final String SCREEN_DAILY_USERS =
      "uniqCombined(UserId)";
  public final String NET_4XX_RATE =
      "if(countIf(PulseType LIKE 'network.%') = 0, NULL, (countIf(PulseType LIKE 'network.4%')/countIf(PulseType LIKE 'network.%')) * 100)";
  public final String NET_5XX_RATE =
      "if(countIf(PulseType LIKE 'network.%') = 0, NULL, (countIf(PulseType LIKE 'network.5%')/countIf(PulseType LIKE 'network.%')) * 100)";
  public final String ARR_TO_STR = "arrayStringConcat(arrayMap(x -> toString(x), %s), ',')";

  public final String CRASH_USERS = "uniqCombinedIf(UserId, PulseType = 'device.crash')";
  public final String CRASH_SESSIONS = "uniqCombinedIf(SessionId, PulseType = 'device.crash')";
  public final String ALL_USERS = "uniqCombined(UserId)";
  public final String ALL_SESSIONS = "uniqCombined(SessionId)";

  public final String CRASH_FREE_USERS_PERCENTAGE =
      "if(uniqCombined(UserId) = 0, NULL, ((uniqCombined(UserId) - uniqCombinedIf(UserId, PulseType = 'device.crash')) / uniqCombined(UserId)) * 100)";
  public final String CRASH_FREE_SESSIONS_PERCENTAGE =
      "if(uniqCombined(SessionId) = 0, NULL, ((uniqCombined(SessionId) - uniqCombinedIf(SessionId, PulseType = 'device.crash')) / uniqCombined(SessionId)) * 100)";

  public final String ANR_USERS = "uniqCombinedIf(UserId, PulseType = 'device.anr')";
  public final String ANR_SESSIONS = "uniqCombinedIf(SessionId, PulseType = 'device.anr')";

  public final String ANR_FREE_USERS_PERCENTAGE =
      "if(uniqCombined(UserId) = 0, NULL, ((uniqCombined(UserId) - uniqCombinedIf(UserId, PulseType = 'device.anr')) / uniqCombined(UserId)) * 100)";
  public final String ANR_FREE_SESSIONS_PERCENTAGE =
      "if(uniqCombined(SessionId) = 0, NULL, ((uniqCombined(SessionId) - uniqCombinedIf(SessionId, PulseType = 'device.anr')) / uniqCombined(SessionId)) * 100)";

  public final String NON_FATAL_USERS = "uniqCombinedIf(UserId, PulseType = 'non_fatal')";
  public final String NON_FATAL_SESSIONS = "uniqCombinedIf(SessionId, PulseType = 'non_fatal')";

  public final String NON_FATAL_FREE_USERS_PERCENTAGE =
      "if(uniqCombined(UserId) = 0, NULL, ((uniqCombined(UserId) - uniqCombinedIf(UserId, PulseType = 'non_fatal')) / uniqCombined(UserId)) * 100)";
  public final String NON_FATAL_FREE_SESSIONS_PERCENTAGE =
      "if(uniqCombined(SessionId) = 0, NULL, ((uniqCombined(SessionId) - uniqCombinedIf(SessionId, PulseType = 'non_fatal')) / uniqCombined(SessionId)) * 100)";
}