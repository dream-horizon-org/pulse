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
  ARR_TO_STR("arrToString", ClickhouseConstants.ARR_TO_STR),
  ;

  private final String displayName;
  private final String chSelectClause;
  Functions(String displayName, String chSelectClause) {
    this.displayName = displayName;
    this.chSelectClause = chSelectClause;
  }

}
