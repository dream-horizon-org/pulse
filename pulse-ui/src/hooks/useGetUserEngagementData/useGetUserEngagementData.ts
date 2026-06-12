import { useMemo } from "react";
import { useGetDataQuery, getDataQueryStatus } from "../useGetDataQuery";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { COLUMN_NAME, PulseType } from "../../constants/PulseOtelSemcov";
import {
  UseGetUserEngagementDataProps,
  UserEngagementData,
  UserEngagementLoadingState,
  UserEngagementFailedState,
} from "./useGetUserEngagementData.interface";

dayjs.extend(utc);

export function useGetUserEngagementData({
  screenName,
  appVersion,
  osVersion,
  device,
  dailyStartDate,
  dailyEndDate,
  weekStartDate,
  weekEndDate,
  monthStartDate,
  monthEndDate,
}: UseGetUserEngagementDataProps): {
  data: UserEngagementData;
  isLoading: boolean;
  loading: UserEngagementLoadingState;
  failed: UserEngagementFailedState;
  error: Error | null;
} {
  // Determine data source based on whether screenName is provided
  const useTracesTable = !!screenName;
  const dataType = "TRACES";

  // Build filters array
  const buildFilters = useMemo(() => {
    const filterArray: Array<{
      field: string;
      operator: "IN" | "EQ";
      value: string[];
    }> = [];

    if (useTracesTable) {
      // Screen Detail page: TRACES with screen_session/screen_load
      filterArray.push({
        field: COLUMN_NAME.PULSE_TYPE,
        operator: "IN",
        value: [PulseType.SCREEN_SESSION, PulseType.SCREEN_LOAD],
      });
      filterArray.push({
        field: COLUMN_NAME.SCREEN_NAME,
        operator: "IN",
        value: [screenName!],
      });
    } else {
      // User Engagement page: LOGS with session.start
      filterArray.push({
        field: COLUMN_NAME.PULSE_TYPE,
        operator: "EQ",
        value: [PulseType.APP_START],
      });
    }

    if (appVersion && appVersion !== "all") {
      filterArray.push({
        field: COLUMN_NAME.APP_VERSION_CODE,
        operator: "EQ",
        value: [appVersion],
      });
    }

    if (osVersion && osVersion !== "all") {
      filterArray.push({
        field: COLUMN_NAME.OS_VERSION,
        operator: "EQ",
        value: [osVersion],
      });
    }

    if (device && device !== "all") {
      filterArray.push({
        field: COLUMN_NAME.DEVICE_MODEL,
        operator: "EQ",
        value: [device],
      });
    }

    return filterArray;
  }, [screenName, appVersion, osVersion, device, useTracesTable]);

  // Fetch daily unique users (bucketed by day) for trend chart
  const dailyQuery = useGetDataQuery({
    requestBody: {
      dataType,
      timeRange: {
        start: dailyStartDate,
        end: dailyEndDate,
      },
      select: [
        {
          function: "TIME_BUCKET",
          param: { bucket: "1d", field: COLUMN_NAME.TIMESTAMP },
          alias: "t1",
        },
        {
          function: "CUSTOM",
          param: {
            expression: `uniq(nullIf(${COLUMN_NAME.INSTALLATION_ID}, ''))`,
          },
          alias: "user_count",
        },
      ],
      filters: buildFilters,
      groupBy: ["t1"],
      orderBy: [{ field: "t1", direction: "ASC" }],
    },
    enabled: !!dailyStartDate && !!dailyEndDate,
  });

  // WAU: single aggregate over the entire week window (no time bucketing)
  const weeklyQuery = useGetDataQuery({
    requestBody: {
      dataType,
      timeRange: {
        start: weekStartDate,
        end: weekEndDate,
      },
      select: [
        {
          function: "CUSTOM",
          param: {
            expression: `uniq(nullIf(${COLUMN_NAME.INSTALLATION_ID}, ''))`,
          },
          alias: "user_count",
        },
      ],
      filters: buildFilters,
    },
    enabled: !!weekStartDate && !!weekEndDate,
  });

  // MAU: single aggregate over the entire month window (no time bucketing)
  const monthlyQuery = useGetDataQuery({
    requestBody: {
      dataType,
      timeRange: {
        start: monthStartDate,
        end: monthEndDate,
      },
      select: [
        {
          function: "CUSTOM",
          param: {
            expression: `uniq(nullIf(${COLUMN_NAME.INSTALLATION_ID}, ''))`,
          },
          alias: "user_count",
        },
      ],
      filters: buildFilters,
    },
    enabled: !!monthStartDate && !!monthEndDate,
  });

  const dailyData = dailyQuery.data;
  const weeklyData = weeklyQuery.data;
  const monthlyData = monthlyQuery.data;

  const dailyStatus = getDataQueryStatus(dailyQuery);
  const weeklyStatus = getDataQueryStatus(weeklyQuery);
  const monthlyStatus = getDataQueryStatus(monthlyQuery);

  // Transform daily data: use the most recent day's value as the DAU headline
  const { dailyUsers, trendData, hasDailyData } = useMemo(() => {
    const responseData = dailyData?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return {
        dailyUsers: null,
        trendData: [],
        hasDailyData: false,
      };
    }

    const t1Index = responseData.fields.indexOf("t1");
    const userCountIndex = responseData.fields.indexOf("user_count");

    const trend = responseData.rows.map((row) => ({
      timestamp: dayjs(row[t1Index]).valueOf(),
      dau: parseFloat(row[userCountIndex]) || 0,
    }));

    const latestDau =
      trend.length > 0 ? Math.round(trend[trend.length - 1].dau) : null;

    return {
      dailyUsers: latestDau,
      trendData: trend,
      hasDailyData: true,
    };
  }, [dailyData]);

  // WAU: single aggregate value from non-bucketed query
  const { weeklyUsers, hasWeeklyData } = useMemo(() => {
    const responseData = weeklyData?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return { weeklyUsers: null, hasWeeklyData: false };
    }

    const userCountIndex = responseData.fields.indexOf("user_count");
    const value = parseFloat(responseData.rows[0][userCountIndex]) || 0;

    return { weeklyUsers: Math.round(value), hasWeeklyData: true };
  }, [weeklyData]);

  // MAU: single aggregate value from non-bucketed query
  const { monthlyUsers, hasMonthlyData } = useMemo(() => {
    const responseData = monthlyData?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return { monthlyUsers: null, hasMonthlyData: false };
    }

    const userCountIndex = responseData.fields.indexOf("user_count");
    const value = parseFloat(responseData.rows[0][userCountIndex]) || 0;

    return { monthlyUsers: Math.round(value), hasMonthlyData: true };
  }, [monthlyData]);

  const hasData = hasDailyData || hasWeeklyData || hasMonthlyData;

  const loading: UserEngagementLoadingState = {
    daily: dailyStatus.loading,
    weekly: weeklyStatus.loading,
    monthly: monthlyStatus.loading,
  };

  const failed: UserEngagementFailedState = {
    daily: dailyStatus.failed,
    weekly: weeklyStatus.failed,
    monthly: monthlyStatus.failed,
  };

  const isLoading = loading.daily || loading.weekly || loading.monthly;
  const error = null;

  return {
    data: {
      dailyUsers,
      weeklyUsers,
      monthlyUsers,
      trendData,
      hasData,
    },
    isLoading,
    loading,
    failed,
    error,
  };
}
