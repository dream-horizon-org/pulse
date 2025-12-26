import { Box } from "@mantine/core";
import { SkeletonLoader } from "./SkeletonLoader";
import classes from "./Skeletons.module.css";

export interface ChartSkeletonProps {
  /** Height of the chart area */
  height?: number;
  /** Optional title to show */
  title?: string;
  /** Show legend skeleton */
  showLegend?: boolean;
  /** Additional CSS class */
  className?: string;
}

/**
 * Skeleton for standalone chart/graph areas.
 * Use when you just need a chart placeholder without metrics.
 */
export function ChartSkeleton({
  height = 320,
  title,
  showLegend = false,
  className,
}: ChartSkeletonProps) {
  return (
    <Box className={`${classes.chartWrapper} ${className || ""}`}>
      {title && <div className={classes.chartTitle}>{title}</div>}
      
      {showLegend && (
        <div className={classes.legendRow}>
          <SkeletonLoader height={12} width={60} radius="sm" />
          <SkeletonLoader height={12} width={60} radius="sm" />
          <SkeletonLoader height={12} width={60} radius="sm" />
        </div>
      )}
      
      <SkeletonLoader height={height} width="100%" radius="md" />
    </Box>
  );
}

