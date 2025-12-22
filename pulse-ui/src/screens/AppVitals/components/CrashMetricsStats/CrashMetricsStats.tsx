import { Box, Text } from "@mantine/core";
import { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks";
import { useQueryError } from "../../../../hooks/useQueryError";
import { StatsSkeleton } from "../../../../components/StatsSkeleton";
import type { DataQueryResponse } from "../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import classes from "./CrashMetricsStats.module.css";
import { COLUMN_NAME } from "../../../../constants/PulseOtelSemcov";  

interface CrashMetricsStatsProps {
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  screenName?: string;
  /** External total users count (from screen engagement data) - used when users exist but no crashes */
  externalTotalUsers?: number;
  /** External total sessions count (from screen engagement data) - used when sessions exist but no crashes */
  externalTotalSessions?: number;
}

export function CrashMetricsStats({
  startTime,
  endTime,
  appVersion = "all",
  osVersion = "all",
  device = "all",
  screenName,
  externalTotalUsers,
  externalTotalSessions,
}: CrashMetricsStatsProps) {
  // Build filters array for API request
  const filters = useMemo(() => {
    const filterArray = [];

    // Add screen name filter if provided
    if (screenName) {
      filterArray.push({
        field: "ScreenName",
        operator: "EQ" as const,
        value: [screenName],
      });
    }

    if (appVersion && appVersion !== "all") {
      filterArray.push({
        field: COLUMN_NAME.APP_VERSION,
        operator: "EQ" as const,
        value: [appVersion],
      });
    }

    if (osVersion && osVersion !== "all") {
      filterArray.push({
        field: "OsVersion",
        operator: "EQ" as const,
        value: [osVersion],
      });
    }

    if (device && device !== "all") {
      filterArray.push({
        field: "DeviceModel",
        operator: "EQ" as const,
        value: [device],
      });
    }

    return filterArray.length > 0 ? filterArray : undefined;
  }, [appVersion, osVersion, device, screenName]);

  // Query only crash users/sessions from EXCEPTIONS table
  // Total users/sessions come from external source (TRACES via useGetAppStats)
  const queryResult = useGetDataQuery({
    requestBody: {
      dataType: "EXCEPTIONS",
      timeRange: {
        start: startTime,
        end: endTime,
      },
      filters,
      select: [
        {
          function: "CUSTOM",
          param: {
            expression: "uniqCombinedIf(UserId, EventName = 'device.crash')",
          },
          alias: "crash_users",
        },
        {
          function: "CUSTOM",
          param: {
            expression: "uniqCombinedIf(SessionId, EventName = 'device.crash')",
          },
          alias: "crash_sessions",
        },
      ],
    },
    enabled: !!startTime && !!endTime,
  }) as ReturnType<typeof useGetDataQuery>;

  const { data } = queryResult;
  const queryState = useQueryError<DataQueryResponse>({ queryResult });

  const metrics = useMemo(() => {
    const responseData = data?.data;

    // Get crash users/sessions from EXCEPTIONS table
    let crashUsers = 0;
    let crashSessions = 0;

    if (responseData && responseData.rows && responseData.rows.length > 0) {
      const fields = responseData.fields;
      const crashUsersIndex = fields.indexOf("crash_users");
      const crashSessionsIndex = fields.indexOf("crash_sessions");

      const row = responseData.rows[0];
      crashUsers = parseFloat(row[crashUsersIndex]) || 0;
      crashSessions = parseFloat(row[crashSessionsIndex]) || 0;
    }

    // Total users/sessions from TRACES table (passed as props)
    const totalUsers = externalTotalUsers ?? 0;
    const totalSessions = externalTotalSessions ?? 0;

    // If there are no users/sessions, we have no data to calculate from
    if (totalUsers === 0 && totalSessions === 0) {
      return {
        crashFreeUsers: null,
        crashFreeSessions: null,
        hasData: false,
      };
    }

    // Calculate crash-free percentage: (total - crash) / total * 100
    const crashFreeUsers =
      totalUsers > 0 ? ((totalUsers - crashUsers) / totalUsers) * 100 : null;
    const crashFreeSessions =
      totalSessions > 0 ? ((totalSessions - crashSessions) / totalSessions) * 100 : null;

    return {
      crashFreeUsers: crashFreeUsers !== null ? parseFloat(crashFreeUsers.toFixed(2)) : null,
      crashFreeSessions: crashFreeSessions !== null ? parseFloat(crashFreeSessions.toFixed(2)) : null,
      hasData: true,
    };
  }, [data, externalTotalUsers, externalTotalSessions]);

  if (queryState.isLoading) {
    return <StatsSkeleton title="Crash Metrics" itemCount={2} />;
  }

  if (queryState.isError) {
    return (
      <Box className={classes.statSection}>
        <Text className={classes.sectionTitle}>Crash Metrics</Text>
        <Text size="sm" c="red" mt="xs">
          {queryState.errorMessage || "Failed to load crash metrics"}
        </Text>
      </Box>
    );
  }

  const formatMetricValue = (value: number | null) => {
    if (value === null) return "N/A";
    return `${value}%`;
  };

  return (
    <Box className={`${classes.statSection} ${classes.fadeIn}`}>
      <Text className={classes.sectionTitle}>Crash Metrics</Text>
      <Box className={classes.metricsGrid}>
        <Box className={classes.statItem}>
          <Text className={classes.statLabel}>Crash-Free Users</Text>
          <Text className={classes.statValue} c={metrics.crashFreeUsers !== null ? "red" : "dimmed"}>
            {formatMetricValue(metrics.crashFreeUsers)}
          </Text>
        </Box>
        <Box className={classes.statItem}>
          <Text className={classes.statLabel}>Crash-Free Sessions</Text>
          <Text className={classes.statValue} c={metrics.crashFreeSessions !== null ? "red" : "dimmed"}>
            {formatMetricValue(metrics.crashFreeSessions)}
          </Text>
        </Box>
      </Box>
    </Box>
  );
}
