import { Box } from "@mantine/core";
import { SkeletonLoader } from "./SkeletonLoader";
import classes from "./Skeletons.module.css";

export interface MetricsGridSkeletonProps {
  /** Number of metric items */
  count?: number;
  /** Layout direction */
  direction?: "row" | "column";
  /** Additional CSS class */
  className?: string;
}

/**
 * Skeleton for metrics/stats grid.
 * Use for metric card rows or stat summaries.
 */
export function MetricsGridSkeleton({
  count = 3,
  direction = "row",
  className,
}: MetricsGridSkeletonProps) {
  return (
    <Box 
      className={`${classes.metricsGridWrapper} ${className || ""}`}
      style={{ flexDirection: direction }}
    >
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className={classes.metricItem}>
          <SkeletonLoader height={10} width="50%" radius="sm" />
          <SkeletonLoader height={24} width="40%" radius="sm" className={classes.metricValue} />
        </div>
      ))}
    </Box>
  );
}

