import { Box, Text } from "@mantine/core";
import { useMemo } from "react";
import { useGetAlertList } from "../../../../hooks";
import { StatsSkeleton } from "../../../../components/StatsSkeleton";
import classes from "./AlertStatusStats.module.css";
import type { AlertStatusStatsProps } from "./AlertStatusStats.interface";

// Alert scope for App Vitals related alerts
const APP_VITALS_SCOPE = "APP_VITALS";

export function AlertStatusStats(_props: AlertStatusStatsProps) {
  // Fetch only App Vitals alerts from the API
  const { data, isLoading, isError } = useGetAlertList({
    queryParams: {
      offset: null,
      limit: null,
      created_by: null,
      updated_by: null,
      scope: APP_VITALS_SCOPE,
      name: null,
      status: null, // Fetch all statuses
    },
  });

  // Calculate metrics from the alerts data
  const metrics = useMemo(() => {
    const alerts = data?.data?.alerts;
    
    if (!alerts || alerts.length === 0) {
      return {
        firingAlerts: 0,
        activeAlerts: 0,
        hasData: false,
      };
    }

    // Count firing alerts (status === "FIRING")
    const firingAlerts = alerts.filter(
      (alert) => alert.status === "FIRING"
    ).length;

    // Count active alerts (is_active === true)
    const activeAlerts = alerts.filter((alert) => alert.is_active).length;

    return {
      firingAlerts,
      activeAlerts,
      hasData: alerts.length > 0,
    };
  }, [data]);

  if (isLoading) {
    return <StatsSkeleton title="Alert Status" itemCount={2} />;
  }

  if (isError) {
    return (
      <Box className={classes.statSection}>
        <Text className={classes.sectionTitle}>Alert Status</Text>
        <Text size="sm" c="dimmed" mt="xs">
          Failed to load alert status
        </Text>
      </Box>
    );
  }

  return (
    <Box className={classes.statSection}>
      <Text className={classes.sectionTitle}>Alert Status</Text>
      <Box className={classes.metricsGrid}>
        <Box className={classes.statItem}>
          <Text className={classes.statLabel}>Firing Alerts</Text>
          <Text
            className={classes.statValue}
            c={metrics.firingAlerts > 0 ? "red" : "blue"}
          >
            {metrics.firingAlerts}
          </Text>
        </Box>
        <Box className={classes.statItem}>
          <Text className={classes.statLabel}>Active Alerts</Text>
          <Text className={classes.statValue} c="blue">
            {metrics.activeAlerts}
          </Text>
        </Box>
      </Box>
    </Box>
  );
}
