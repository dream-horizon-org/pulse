package org.dreamhorizon.pulseserver.resources.performance.models;

import lombok.Getter;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;

@Getter
public enum Functions {
  APDEX("apdex", ClickhouseConstants.CH_APDEX_SELECT_CLAUSE),
  CRASH("crash", ClickhouseConstants.CH_CRASH_SELECT_CLAUSE),
  ANR("anr", ClickhouseConstants.CH_ANR_SELECT_CLAUSE),
  FROZEN_FRAME("frozen_frame", ClickhouseConstants.CH_FROZEN_FRAME_SELECT_CLAUSE),
  ANALYSED_FRAME("analysed_frame", ClickhouseConstants.CH_ANALYSED_FRAME_SELECT_CLAUSE),
  UNANALYSED_FRAME("unanalysed_frame", ClickhouseConstants.CH_UNANALYSED_FRAME_SELECT_CLAUSE),
  DURATION_P99("duration_p99", ClickhouseConstants.CH_DURATION_P99_SELECT_CLAUSE),
  DURATION_P50("duration_p50", ClickhouseConstants.CH_DURATION_P50_SELECT_CLAUSE),
  DURATION_P95("duration_p95", ClickhouseConstants.CH_DURATION_P95_SELECT_CLAUSE),
  COL("col", "%s"),
  CUSTOM("custom", "%s"),
  TIME_BUCKET("time_bucket", ClickhouseConstants.CH_TIME_BUCKET_SELECT_CLAUSE),
  INTERACTION_SUCCESS_COUNT("successInteractionCount", ClickhouseConstants.SUC_IN_CNT),
  INTERACTION_ERROR_COUNT("errorInteractionCount", ClickhouseConstants.ERR_IN_CNT),
  INTERACTION_ERROR_DISTINCT_USERS("distinctUsers", ClickhouseConstants.ERR_DIST_USERS),

  USER_CATEGORY_EXCELLENT("lowUptimeUser", ClickhouseConstants.EXCELLENT_CAT),
  USER_CATEGORY_GOOD("midUptimeUser1", ClickhouseConstants.GOOD_CAT),
  USER_CATEGORY_AVERAGE("midUptimeUser2", ClickhouseConstants.AVERAGE_CAT),
  USER_CATEGORY_POOR("highUptimeUser", ClickhouseConstants.POOR_CAT),
  NET_0("connectionerror", ClickhouseConstants.NET_0),
  NET_2XX("net2XX", ClickhouseConstants.NET_2XX),
  NET_3XX("net3XX", ClickhouseConstants.NET_3XX),
  NET_4XX("net4XX", ClickhouseConstants.NET_4XX),
  NET_5XX("net5XX", ClickhouseConstants.NET_5XX),
  NET_COUNT("netCount", ClickhouseConstants.NET_COUNT),
  CRASH_RATE("crashRate", ClickhouseConstants.CRASH_RATE),
  ANR_RATE("anrRate", ClickhouseConstants.ANR_RATE),
  CRASH_FREE_USERS_PERCENTAGE("crashFreeUsersPercentage", ClickhouseConstants.CRASH_FREE_USERS_PERCENTAGE),
  CRASH_FREE_SESSIONS_PERCENTAGE("crashFreeSessionsPercentage", ClickhouseConstants.CRASH_FREE_SESSIONS_PERCENTAGE),
  CRASH_USERS("crashUsers", ClickhouseConstants.CRASH_USERS),
  CRASH_SESSIONS("crashSessions", ClickhouseConstants.CRASH_SESSIONS),
  ALL_USERS("allUsers", ClickhouseConstants.ALL_USERS),
  ALL_SESSIONS("allSessions", ClickhouseConstants.ALL_SESSIONS),
  ANR_FREE_USERS_PERCENTAGE("anrFreeUsersPercentage", ClickhouseConstants.ANR_FREE_USERS_PERCENTAGE),
  ANR_FREE_SESSIONS_PERCENTAGE("anrFreeSessionsPercentage", ClickhouseConstants.ANR_FREE_SESSIONS_PERCENTAGE),
  ANR_USERS("anrUsers", ClickhouseConstants.ANR_USERS),
  ANR_SESSIONS("anrSessions", ClickhouseConstants.ANR_SESSIONS),
  NON_FATAL_FREE_USERS_PERCENTAGE("nonFatalFreeUsersPercentage", ClickhouseConstants.NON_FATAL_FREE_USERS_PERCENTAGE),
  NON_FATAL_FREE_SESSIONS_PERCENTAGE("nonFatalFreeSessionsPercentage", ClickhouseConstants.NON_FATAL_FREE_SESSIONS_PERCENTAGE),
  NON_FATAL_USERS("nonFatalUsers", ClickhouseConstants.NON_FATAL_USERS),
  NON_FATAL_SESSIONS("nonFatalSessions", ClickhouseConstants.NON_FATAL_SESSIONS),
  FROZEN_FRAME_RATE("frozenFrameRate", ClickhouseConstants.FROZEN_FRAME_RATE),
  ERROR_RATE("errorRate", ClickhouseConstants.ERROR_RATE),
  POOR_USER_RATE("poorUserRate", ClickhouseConstants.POOR_USER_RATE),
  AVERAGE_USER_RATE("averageUserRate", ClickhouseConstants.AVERAGE_USER_RATE),
  GOOD_USER_RATE("goodUserRate", ClickhouseConstants.GOOD_USER_RATE),
  EXCELLENT_USER_RATE("excellentUserRate", ClickhouseConstants.EXCELLENT_USER_RATE),
  LOAD_TIME("loadTime", ClickhouseConstants.LOAD_TIME),
  SCREEN_TIME("screenTime", ClickhouseConstants.SCREEN_TIME),
  SCREEN_DAILY_USERS("screenDailyUsers", ClickhouseConstants.SCREEN_DAILY_USERS),
  NET_4XX_RATE("net4xxRate", ClickhouseConstants.NET_4XX_RATE),
  NET_5XX_RATE("net5xxRate", ClickhouseConstants.NET_5XX_RATE),
  // Network metrics for alerts (uses PulseType instead of Events.Name)
  NET_0_BY_PULSE_TYPE("connectionErrorByPulseType", ClickhouseConstants.NET_0_BY_PULSE_TYPE),
  NET_2XX_BY_PULSE_TYPE("net2xxByPulseType", ClickhouseConstants.NET_2XX_BY_PULSE_TYPE),
  NET_3XX_BY_PULSE_TYPE("net3xxByPulseType", ClickhouseConstants.NET_3XX_BY_PULSE_TYPE),
  NET_4XX_BY_PULSE_TYPE("net4xxByPulseType", ClickhouseConstants.NET_4XX_BY_PULSE_TYPE),
  NET_5XX_BY_PULSE_TYPE("net5xxByPulseType", ClickhouseConstants.NET_5XX_BY_PULSE_TYPE),
  NET_COUNT_BY_PULSE_TYPE("netCountByPulseType", ClickhouseConstants.NET_COUNT_BY_PULSE_TYPE),
  ARR_TO_STR("arrToString", ClickhouseConstants.ARR_TO_STR);

  private final String displayName;
  private final String chSelectClause;

  Functions(String displayName, String chSelectClause) {
    this.displayName = displayName;
    this.chSelectClause = chSelectClause;
  }

}
