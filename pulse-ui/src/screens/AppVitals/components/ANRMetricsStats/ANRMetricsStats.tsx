import { Box, Text } from "@mantine/core";
import { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks";
import { useQueryError } from "../../../../hooks/useQueryError";
import { StatsSkeleton } from "../../../../components/StatsSkeleton";
import type { DataQueryResponse } from "../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import classes from "./ANRMetricsStats.module.css";
import { COLUMN_NAME } from "../../../../constants/PulseOtelSemcov";

interface ANRMetricsStatsProps {
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  screenName?: string;
  /** External total users count (from screen engagement data) - used when users exist but no ANRs */
  externalTotalUsers?: number;
  /** External total sessions count (from screen engagement data) - used when sessions exist but no ANRs */
  externalTotalSessions?: number;
}

export function ANRMetricsStats({
  startTime,
  endTime,
  appVersion = "all",
  osVersion = "all",
  device = "all",
  screenName,
  externalTotalUsers,
  externalTotalSessions,
}: ANRMetricsStatsProps) {
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

  // Query only ANR users/sessions from EXCEPTIONS table
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
            expression: "uniqCombinedIf(UserId, PulseType = 'device.anr')",
          },
          alias: "anr_users",
        },
        {
          function: "CUSTOM",
          param: {
            expression: "uniqCombinedIf(SessionId, PulseType = 'device.anr')",
          },
          alias: "anr_sessions",
        },
      ],
    },
    enabled: !!startTime && !!endTime,
  });

  const { data } = queryResult;
  const queryState = useQueryError<DataQueryResponse>({ queryResult });

  const metrics = useMemo(() => {
    const responseData = data?.data;

    // Get ANR users/sessions from EXCEPTIONS table
    let anrUsers = 0;
    let anrSessions = 0;

    if (responseData && responseData.rows && responseData.rows.length > 0) {
      const fields = responseData.fields;
      const anrUsersIndex = fields.indexOf("anr_users");
      const anrSessionsIndex = fields.indexOf("anr_sessions");

      const row = responseData.rows[0];
      anrUsers = parseFloat(row[anrUsersIndex]) || 0;
      anrSessions = parseFloat(row[anrSessionsIndex]) || 0;
    }

    // Total users/sessions from TRACES table (passed as props)
    const totalUsers = externalTotalUsers ?? 0;
    const totalSessions = externalTotalSessions ?? 0;

    // If there are no users/sessions, we have no data to calculate from
    if (totalUsers === 0 && totalSessions === 0) {
      return {
        anrFreeUsers: null,
        anrFreeSessions: null,
        hasData: false,
      };
    }

    // Calculate ANR-free percentage: (total - anr) / total * 100
    const anrFreeUsers =
      totalUsers > 0 ? ((totalUsers - anrUsers) / totalUsers) * 100 : null;
    const anrFreeSessions =
      totalSessions > 0 ? ((totalSessions - anrSessions) / totalSessions) * 100 : null;

    return {
      anrFreeUsers: anrFreeUsers !== null ? parseFloat(anrFreeUsers.toFixed(2)) : null,
      anrFreeSessions: anrFreeSessions !== null ? parseFloat(anrFreeSessions.toFixed(2)) : null,
      hasData: true,
    };
  }, [data, externalTotalUsers, externalTotalSessions]);

  if (queryState.isLoading) {
    return <StatsSkeleton title="ANR Metrics" itemCount={2} />;
  }

  if (queryState.isError) {
    return (
      <Box className={classes.statSection}>
        <Text className={classes.sectionTitle}>ANR Metrics</Text>
        <Text size="sm" c="red" mt="xs">
          {queryState.errorMessage || "Failed to load ANR metrics"}
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
      <Text className={classes.sectionTitle}>ANR Metrics</Text>
      <Box className={classes.metricsGrid}>
        <Box className={classes.statItem}>
          <Text className={classes.statLabel}>ANR-Free Users</Text>
          <Text className={classes.statValue} c={metrics.anrFreeUsers !== null ? "orange" : "dimmed"}>
            {formatMetricValue(metrics.anrFreeUsers)}
          </Text>
        </Box>
        <Box className={classes.statItem}>
          <Text className={classes.statLabel}>ANR-Free Sessions</Text>
          <Text className={classes.statValue} c={metrics.anrFreeSessions !== null ? "orange" : "dimmed"}>
            {formatMetricValue(metrics.anrFreeSessions)}
          </Text>
        </Box>
      </Box>
    </Box>
  );
}
