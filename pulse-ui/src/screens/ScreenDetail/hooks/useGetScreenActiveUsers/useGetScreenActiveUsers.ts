import { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { PulseType } from "../../../../constants/PulseOtelSemcov";

dayjs.extend(utc);

interface UseGetScreenActiveUsersProps {
  screenName: string;
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
}

interface ActiveUsersData {
  dau: number;
  wau: number;
  mau: number;
  trendData: Array<{
    timestamp: number;
    dau: number;
    wau: number;
    mau: number;
  }>;
}

export function useGetScreenActiveUsers({
  screenName,
  startTime,
  endTime,
  appVersion,
  osVersion,
  device,
}: UseGetScreenActiveUsersProps): {
  data: ActiveUsersData | null;
  isLoading: boolean;
  error: Error | null;
} {
  // Calculate date ranges
  const {
    dailyStartDate,
    dailyEndDate,
    weeklyStartDate,
    weeklyEndDate,
    monthlyStartDate,
    monthlyEndDate,
  } = useMemo(() => {
    const end = dayjs(endTime).utc().startOf("day");
    const dailyStart = end.subtract(1, "day");
    const weeklyStart = end.subtract(7, "days");
    const monthlyStart = end.subtract(30, "days");

    return {
      dailyStartDate: dailyStart.toISOString(),
      dailyEndDate: end.toISOString(),
      weeklyStartDate: weeklyStart.toISOString(),
      weeklyEndDate: end.toISOString(),
      monthlyStartDate: monthlyStart.toISOString(),
      monthlyEndDate: end.toISOString(),
    };
  }, [endTime]);

  // Build base filters - using TRACES with screen_session for screen-specific user counts
  const buildFilters = useMemo(() => {
    const filterArray: Array<{
      field: string;
      operator: "IN" | "EQ";
      value: string[];
    }> = [
      {
        field: `SpanAttributes['${PulseType.SCREEN_NAME}']`,
        operator: "IN",
        value: [screenName],
      },
      {
        field: "PulseType",
        operator: "IN",
        value: [PulseType.SCREEN_SESSION, PulseType.SCREEN_LOAD],
      },
    ];

    if (appVersion && appVersion !== "all") {
      filterArray.push({
        field: "ResourceAttributes['app.version']",
        operator: "EQ",
        value: [appVersion],
      });
    }

    if (osVersion && osVersion !== "all") {
      filterArray.push({
        field: "ResourceAttributes['os.version']",
        operator: "EQ",
        value: [osVersion],
      });
    }

    if (device && device !== "all") {
      filterArray.push({
        field: "ResourceAttributes['device.model']",
        operator: "EQ",
        value: [device],
      });
    }

    return filterArray;
  }, [screenName, appVersion, osVersion, device]);

  // DAU: single aggregate over last 1 day (no bucketing)
  const {
    data: dauData,
    isLoading: isLoadingDau,
    error: dauError,
  } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES" as const,
      timeRange: {
        start: dailyStartDate,
        end: dailyEndDate,
      },
      select: [
        {
          function: "CUSTOM" as const,
          param: { expression: "uniqCombined64(nullIf(UserId, ''))" },
          alias: "user_count",
        },
      ],
      filters: buildFilters,
    },
    enabled: !!screenName && !!dailyStartDate && !!dailyEndDate,
  });

  // WAU: single aggregate over last 7 days (no bucketing)
  const {
    data: wauData,
    isLoading: isLoadingWau,
    error: wauError,
  } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES" as const,
      timeRange: {
        start: weeklyStartDate,
        end: weeklyEndDate,
      },
      select: [
        {
          function: "CUSTOM" as const,
          param: { expression: "uniqCombined64(nullIf(UserId, ''))" },
          alias: "user_count",
        },
      ],
      filters: buildFilters,
    },
    enabled: !!screenName && !!weeklyStartDate && !!weeklyEndDate,
  });

  // MAU: single aggregate over last 30 days (no bucketing)
  const {
    data: mauData,
    isLoading: isLoadingMau,
    error: mauError,
  } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES" as const,
      timeRange: {
        start: monthlyStartDate,
        end: monthlyEndDate,
      },
      select: [
        {
          function: "CUSTOM" as const,
          param: { expression: "uniqCombined64(nullIf(UserId, ''))" },
          alias: "user_count",
        },
      ],
      filters: buildFilters,
    },
    enabled: !!screenName && !!monthlyStartDate && !!monthlyEndDate,
  });

  // Daily trend for graph (last 7 days, bucketed by day)
  const {
    data: dailyTrendData,
    isLoading: isLoadingTrend,
    error: trendError,
  } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES" as const,
      timeRange: {
        start: weeklyStartDate,
        end: weeklyEndDate,
      },
      select: [
        {
          function: "TIME_BUCKET" as const,
          param: { bucket: "1d", field: "Timestamp" },
          alias: "t1",
        },
        {
          function: "CUSTOM" as const,
          param: { expression: "uniqCombined64(nullIf(UserId, ''))" },
          alias: "user_count",
        },
      ],
      filters: buildFilters,
      groupBy: ["t1"],
      orderBy: [{ field: "t1", direction: "ASC" as const }],
    },
    enabled: !!screenName && !!weeklyStartDate && !!weeklyEndDate,
  });

  // Transform data: read single aggregate values for DAU/WAU/MAU
  const transformedData = useMemo<ActiveUsersData | null>(() => {
    const readSingleValue = (response: typeof dauData): number => {
      const responseData = response?.data;
      if (!responseData?.rows || responseData.rows.length === 0) return 0;
      const idx = responseData.fields.indexOf("user_count");
      return Math.round(parseFloat(responseData.rows[0][idx]) || 0);
    };

    const dau = readSingleValue(dauData);
    const wau = readSingleValue(wauData);
    const mau = readSingleValue(mauData);

    // Build trend data from daily bucketed query
    const trendResponse = dailyTrendData?.data;
    const trendData: Array<{
      timestamp: number;
      dau: number;
      wau: number;
      mau: number;
    }> = [];

    if (trendResponse?.rows && trendResponse.rows.length > 0) {
      const t1Index = trendResponse.fields.indexOf("t1");
      const userCountIndex = trendResponse.fields.indexOf("user_count");

      trendResponse.rows.forEach((row) => {
        const timestamp = dayjs(row[t1Index]).valueOf();
        const userCount = Math.round(parseFloat(row[userCountIndex]) || 0);

        trendData.push({
          timestamp,
          dau: userCount,
          wau,
          mau,
        });
      });
    }

    return {
      dau,
      wau,
      mau,
      trendData,
    };
  }, [dauData, wauData, mauData, dailyTrendData]);

  const isLoading =
    isLoadingDau || isLoadingWau || isLoadingMau || isLoadingTrend;
  const error = dauError || wauError || mauError || trendError;

  return {
    data: transformedData,
    isLoading,
    error: error as Error | null,
  };
}
