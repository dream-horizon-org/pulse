import { useMemo } from "react";
import { useGetDataQuery } from "../useGetDataQuery";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { COLUMN_NAME, SpanType } from "../../constants/PulseOtelSemcov";
import {
  UseGetUserEngagementDataProps,
  UserEngagementData,
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
  spanType = SpanType.APP_START,
}: UseGetUserEngagementDataProps): {
  data: UserEngagementData;
  isLoading: boolean;
  error: Error | null;
} {

  // Build filters array
  const buildFilters = useMemo(() => {
    const filterArray: Array<{
      field: string;
      operator: "IN" | "EQ";
      value: string[];
    }> = [
      {
        field: COLUMN_NAME.SPAN_TYPE,
        operator: "EQ",
        value: [spanType],
      },
    ];

    if (screenName) {
      filterArray.push({
        field: `SpanAttributes['${SpanType.SCREEN_NAME}']`,
        operator: "IN",
        value: [screenName],
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
  }, [screenName, appVersion, osVersion, device, spanType]);

  // Fetch daily unique users for the last 7 days (for graph)
  const { data: dailyData, isLoading: isLoadingDaily } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
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
          param: { expression: `uniqCombined(${COLUMN_NAME.USER_ID})` },
          alias: "user_count",
        },
      ],
      filters: buildFilters,
      groupBy: ["t1"],
      orderBy: [{ field: "t1", direction: "ASC" }],
    },
    enabled: !!dailyStartDate && !!dailyEndDate,
  });

  // Fetch weekly unique users for the last 1 month
  const { data: weeklyData, isLoading: isLoadingWeekly } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: {
        start: weekStartDate,
        end: weekEndDate,
      },
      select: [
        {
          function: "TIME_BUCKET",
          param: { bucket: "1w", field: COLUMN_NAME.TIMESTAMP },
          alias: "t1",
        },
        {
          function: "CUSTOM",
          param: { expression: `uniqCombined(${COLUMN_NAME.USER_ID})` },
          alias: "user_count",
        },
      ],
      filters: buildFilters,
      groupBy: ["t1"],
      orderBy: [{ field: "t1", direction: "ASC" }],
    },
    enabled: !!weekStartDate && !!weekEndDate,
  });

  // Fetch monthly unique users for the last 1 month
  const { data: monthlyData, isLoading: isLoadingMonthly } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: {
        start: monthStartDate,
        end: monthEndDate,
      },
      select: [
        {
          function: "TIME_BUCKET",
          param: { bucket: "1M", field: COLUMN_NAME.TIMESTAMP },
          alias: "t1",
        },
        {
          function: "CUSTOM",
          param: { expression: `uniqCombined(${COLUMN_NAME.USER_ID})` },
          alias: "user_count",
        },
      ],
      filters: buildFilters,
      groupBy: ["t1"],
      orderBy: [{ field: "t1", direction: "ASC" }],
    },
    enabled: !!monthStartDate && !!monthEndDate,
  });

  // Transform daily data and calculate average
  const { dailyUsers, trendData } = useMemo(() => {
    const responseData = dailyData?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return {
        dailyUsers: 0,
        trendData: [],
      };
    }

    const t1Index = responseData.fields.indexOf("t1");
    const userCountIndex = responseData.fields.indexOf("user_count");

    const trend = responseData.rows.map((row) => ({
      timestamp: dayjs(row[t1Index]).valueOf(),
      dau: parseFloat(row[userCountIndex]) || 0,
    }));

    // Calculate average daily users
    const avgDailyUsers =
      trend.length > 0
        ? Math.round(trend.reduce((sum, d) => sum + d.dau, 0) / trend.length)
        : 0;

    return {
      dailyUsers: avgDailyUsers,
      trendData: trend,
    };
  }, [dailyData]);

  // Calculate weekly active users
  const weeklyUsers = useMemo(() => {
    const responseData = weeklyData?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return 0;
    }

    const userCountIndex = responseData.fields.indexOf("user_count");
    const total = responseData.rows.reduce(
      (sum, row) => sum + (parseFloat(row[userCountIndex]) || 0),
      0,
    );

    return total;
  }, [weeklyData]);

  // Calculate monthly active users
  const monthlyUsers = useMemo(() => {
    const responseData = monthlyData?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return 0;
    }

    const userCountIndex = responseData.fields.indexOf("user_count");
    const total = responseData.rows.reduce(
      (sum, row) => sum + (parseFloat(row[userCountIndex]) || 0),
      0,
    );

    return total;
  }, [monthlyData]);

  const isLoading = isLoadingDaily || isLoadingWeekly || isLoadingMonthly;
  const error = null; // You can enhance this to capture errors from queries if needed

  return {
    data: {
      dailyUsers,
      weeklyUsers,
      monthlyUsers,
      trendData,
    },
    isLoading,
    error,
  };
}

