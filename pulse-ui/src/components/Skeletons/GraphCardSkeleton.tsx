import { Box } from "@mantine/core";
import { SkeletonLoader } from "./SkeletonLoader";
import classes from "./Skeletons.module.css";

export interface GraphCardSkeletonProps {
  /** Optional title to show above skeleton */
  title?: string;
  /** Height of the chart area */
  chartHeight?: number;
  /** Number of metric cards to show */
  metricsCount?: number;
  /** Show title skeleton placeholder */
  showTitleSkeleton?: boolean;
  /** Additional CSS class */
  className?: string;
}

/**
 * Skeleton for graph cards with metrics and chart area.
 * Matches the layout of UserEngagementGraph, ActiveSessionsGraph, etc.
 */
export function GraphCardSkeleton({
  title,
  chartHeight = 260,
  metricsCount = 3,
  showTitleSkeleton = true,
  className,
}: GraphCardSkeletonProps) {
  return (
    <Box className={`${classes.graphCard} ${className || ""}`}>
      {/* Title */}
      {title ? (
        <div className={classes.graphTitle}>{title}</div>
      ) : showTitleSkeleton ? (
        <SkeletonLoader height={14} width="40%" radius="sm" className={classes.titleSkeleton} />
      ) : null}

      {/* Metrics grid */}
      {metricsCount > 0 && (
        <div className={classes.metricsGrid}>
          {Array.from({ length: metricsCount }).map((_, index) => (
            <div key={index} className={classes.metricCard}>
              <SkeletonLoader height={10} width="60%" radius="sm" />
              <SkeletonLoader height={30} width="50%" radius="sm" className={classes.metricValue} />
            </div>
          ))}
        </div>
      )}

      {/* Chart area */}
      <div className={classes.chartContainer}>
        <SkeletonLoader height={chartHeight} width="100%" radius="md" />
      </div>
    </Box>
  );
}

