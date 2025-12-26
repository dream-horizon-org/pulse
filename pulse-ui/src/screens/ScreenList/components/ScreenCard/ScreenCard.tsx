import { Text } from "@mantine/core";
import { IconDeviceDesktop, TablerIcon } from "@tabler/icons-react";
import classes from "./ScreenCard.module.css";

interface ScreenCardProps {
  screenName: string;
  onClick?: () => void;
  icon?: TablerIcon;
  // Static values from useGetScreenDetails
  staticAvgTimeSpent?: number;
  staticCrashRate?: number;
  staticLoadTime?: number;
  staticUsers?: number;
}

export function ScreenCard({
  screenName,
  onClick,
  icon: IconComponent = IconDeviceDesktop,
  staticAvgTimeSpent,
  staticCrashRate,
  staticLoadTime,
  staticUsers,
}: ScreenCardProps) {
  // Format load time (nanoseconds) to readable string (ms or s)
  const formatLoadTime = (nanoseconds: number): string => {
    const milliseconds = nanoseconds / 1_000_000;
    if (milliseconds >= 1000) {
      return (milliseconds / 1000).toFixed(2) + "s";
    }
    return milliseconds.toFixed(0) + "ms";
  };

  // Format time spent (nanoseconds) to readable string (s, or Xm Ys if > 60s)
  const formatTimeSpent = (nanoseconds: number): string => {
    const seconds = nanoseconds / 1_000_000_000;

    if (seconds < 1) {
      // Less than 1 second, show in milliseconds
      const ms = nanoseconds / 1_000_000;
      return ms.toFixed(0) + "ms";
    }

    if (seconds < 60) {
      // Less than 60 seconds, show in seconds
      return seconds.toFixed(1) + "s";
    }

    // 60 seconds or more, show in minutes and seconds
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = Math.round(seconds % 60);

    if (remainingSeconds === 0) {
      return `${minutes}m`;
    }
    return `${minutes}m ${remainingSeconds}s`;
  };

  // Format user count to human readable format (K, M, B)
  const formatUserCount = (count: number): string => {
    if (count < 1000) {
      return count.toString();
    }
    if (count < 1_000_000) {
      const value = count / 1000;
      return value >= 100 ? `${Math.round(value)}K` : `${value.toFixed(1)}K`;
    }
    if (count < 1_000_000_000) {
      const value = count / 1_000_000;
      return value >= 100 ? `${Math.round(value)}M` : `${value.toFixed(1)}M`;
    }
    const value = count / 1_000_000_000;
    return value >= 100 ? `${Math.round(value)}B` : `${value.toFixed(1)}B`;
  };


  return (
    <div className={classes.screenCard} onClick={onClick}>
      {/* Screen Mockup */}
      <div className={classes.screenMockup}>
        <div className={classes.screenHeader}></div>
        <div className={classes.screenContent}>
          <div className={classes.screenIcon}>
            <IconComponent size={32} stroke={1.8} />
          </div>
          <Text className={classes.screenName}>{screenName}</Text>
        </div>
      </div>
      <div className={classes.healthIndicator} />

      {/* Health Metrics */}
      <div className={classes.metricsRow}>
        <div className={classes.metricItem}>
          <Text className={classes.metricLabel}>Avg Time Spent</Text>
          <Text className={classes.metricValue}>
            {staticAvgTimeSpent ? formatTimeSpent(staticAvgTimeSpent) : "N/A"}
          </Text>
        </div>
        <div className={classes.metricItem}>
          <Text className={classes.metricLabel}>Error Rate</Text>
          <Text className={classes.metricValue}>
            {staticCrashRate !== undefined ? `${staticCrashRate.toFixed(1)}%` : "N/A"}
          </Text>
        </div>
        <div className={classes.metricItem}>
          <Text className={classes.metricLabel}>Avg Load Time</Text>
          <Text className={classes.metricValue}>
            {staticLoadTime ? formatLoadTime(staticLoadTime) : "N/A"}
          </Text>
        </div>
        <div className={classes.metricItem}>
          <Text className={classes.metricLabel}>Users</Text>
          <Text className={classes.metricValue}>
            {staticUsers ? formatUserCount(staticUsers) : "N/A"}
          </Text>
        </div>
      </div>
    </div>
  );
}
