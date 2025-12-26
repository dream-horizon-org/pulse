import { useMemo } from "react";
import { useGetDataQuery } from "../useGetDataQuery";
import { PulseType, COLUMN_NAME } from "../../constants/PulseOtelSemcov";
import type { UseGetAppStatsProps, AppStatsData } from "./useGetAppStats.interface";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

/**
 * Hook to get total users and sessions from app_start spans
 * Used to calculate crash-free/ANR-free percentages on App Vitals page
 * 
 * Note: Uses MATERIALIZED columns from otel_traces table directly:
 * - AppVersion, OsVersion, DeviceModel, UserId, SessionId, PulseType
 * See: backend/ingestion/clickhouse-otel-schema.sql
 */
export function useGetAppStats({
  startTime,
  endTime,
  appVersion,
  osVersion,
  device,
}: UseGetAppStatsProps): {
  data: AppStatsData | null;
  isLoading: boolean;
  error: Error | null;
} {
  // Build filters array using MATERIALIZED columns from otel_traces
  const filters = useMemo(() => {
    const filterArray: Array<{
      field: string;
      operator: "IN" | "EQ";
      value: string[];
    }> = [
      {
        field: COLUMN_NAME.PULSE_TYPE,
        operator: "EQ",
        value: [PulseType.APP_START],
      },
    ];

    if (appVersion && appVersion !== "all") {
      filterArray.push({
        field: COLUMN_NAME.APP_VERSION,
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
  }, [appVersion, osVersion, device]);

  // Format time strings to ISO if needed
  const formattedStartTime = useMemo(() => {
    if (!startTime) return "";
    if (startTime.includes("T") || startTime.includes("Z")) {
      return startTime;
    }
    return dayjs.utc(startTime, "YYYY-MM-DD HH:mm:ss").toISOString();
  }, [startTime]);

  const formattedEndTime = useMemo(() => {
    if (!endTime) return "";
    if (endTime.includes("T") || endTime.includes("Z")) {
      return endTime;
    }
    return dayjs.utc(endTime, "YYYY-MM-DD HH:mm:ss").toISOString();
  }, [endTime]);

  // Build request body
  // Note: TRACES table has direct UserId and SessionId columns (not nested in ResourceAttributes)
  const requestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: {
        start: formattedStartTime,
        end: formattedEndTime,
      },
      select: [
        {
          function: "CUSTOM" as const,
          param: {
            expression: `uniqCombined(${COLUMN_NAME.USER_ID})`,
          },
          alias: "unique_users",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `uniqCombined(${COLUMN_NAME.SESSION_ID})`,
          },
          alias: "unique_sessions",
        },
      ],
      filters,
    }),
    [formattedStartTime, formattedEndTime, filters]
  );

  const {
    data,
    isLoading,
    error: queryError,
  } = useGetDataQuery({
    requestBody,
    enabled: !!formattedStartTime && !!formattedEndTime,
  });

  // Transform data
  const transformedData = useMemo<AppStatsData | null>(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return null;
    }

    const uniqueUsersIndex = responseData.fields.indexOf("unique_users");
    const uniqueSessionsIndex = responseData.fields.indexOf("unique_sessions");

    const row = responseData.rows[0];
    const totalUsers = parseFloat(row[uniqueUsersIndex]) || 0;
    const totalSessions = parseFloat(row[uniqueSessionsIndex]) || 0;

    return {
      totalUsers: Math.round(totalUsers),
      totalSessions: Math.round(totalSessions),
      hasData: totalUsers > 0 || totalSessions > 0,
    };
  }, [data]);

  return {
    data: transformedData,
    isLoading,
    error: queryError as Error | null,
  };
}

