import { Text } from "@mantine/core";
import { IconDeviceDesktop } from "@tabler/icons-react";
import { SkeletonLoader } from "../../../../components/SkeletonLoader";
import classes from "./ScreenCardSkeleton.module.css";

interface ScreenCardSkeletonProps {
  screenName: string;
}

export function ScreenCardSkeleton({ screenName }: ScreenCardSkeletonProps) {
  return (
    <div className={classes.screenCard}>
      {/* Screen Mockup */}
      <div className={classes.screenMockup}>
        <div className={classes.screenContent}>
          <div className={classes.screenIcon}>
            <IconDeviceDesktop size={32} stroke={1.8} />
          </div>
          <Text className={classes.screenName}>{screenName}</Text>
        </div>
      </div>
      <div className={classes.healthIndicator} />

      {/* Health Metrics Skeleton */}
      <div className={classes.metricsRow}>
        <div className={classes.metricItem}>
          <SkeletonLoader height="12px" width="80px" radius="sm" />
          <SkeletonLoader height="16px" width="50px" radius="sm" />
        </div>
        <div className={classes.metricItem}>
          <SkeletonLoader height="12px" width="80px" radius="sm" />
          <SkeletonLoader height="16px" width="50px" radius="sm" />
        </div>
        <div className={classes.metricItem}>
          <SkeletonLoader height="12px" width="80px" radius="sm" />
          <SkeletonLoader height="16px" width="50px" radius="sm" />
        </div>
        <div className={classes.metricItem}>
          <SkeletonLoader height="12px" width="80px" radius="sm" />
          <SkeletonLoader height="16px" width="50px" radius="sm" />
        </div>
      </div>
    </div>
  );
}
