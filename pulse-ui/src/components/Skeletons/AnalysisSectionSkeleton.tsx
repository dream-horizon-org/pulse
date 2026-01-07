import { Box, Flex } from "@mantine/core";
import { SkeletonLoader } from "./SkeletonLoader";
import classes from "./Skeletons.module.css";

export interface AnalysisSectionSkeletonProps {
  /** Number of chart placeholders to show */
  chartCount?: number;
  /** Show section header */
  showHeader?: boolean;
  /** Layout direction for charts */
  layout?: "horizontal" | "vertical" | "grid";
  /** Chart height */
  chartHeight?: number;
}

/**
 * Skeleton for Analysis section loading states.
 * Use for Release Performance, Regional Insights, etc.
 */
export function AnalysisSectionSkeleton({
  chartCount = 2,
  showHeader = true,
  layout = "horizontal",
  chartHeight = 300,
}: AnalysisSectionSkeletonProps) {
  return (
    <Box mb="lg">
      {showHeader && (
        <Box mb="md">
          <SkeletonLoader height={18} width="30%" radius="sm" />
          <Box mt={8}>
            <SkeletonLoader height={12} width="50%" radius="sm" />
          </Box>
        </Box>
      )}
      
      {layout === "horizontal" && (
        <Flex gap="md" justify="space-between">
          {Array.from({ length: chartCount }).map((_, index) => (
            <Box key={index} flex="1" className={classes.analysisChartSkeleton}>
              <SkeletonLoader height={14} width="60%" radius="sm" />
              <SkeletonLoader height={chartHeight} width="100%" radius="md" />
            </Box>
          ))}
        </Flex>
      )}
      
      {layout === "vertical" && (
        <Flex direction="column" gap="md">
          {Array.from({ length: chartCount }).map((_, index) => (
            <Box key={index} className={classes.analysisChartSkeleton}>
              <SkeletonLoader height={14} width="40%" radius="sm" />
              <SkeletonLoader height={chartHeight} width="100%" radius="md" />
            </Box>
          ))}
        </Flex>
      )}
      
      {layout === "grid" && (
        <Box style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 'var(--mantine-spacing-md)' }}>
          {Array.from({ length: chartCount }).map((_, index) => (
            <Box key={index} className={classes.analysisChartSkeleton}>
              <SkeletonLoader height={14} width="60%" radius="sm" />
              <SkeletonLoader height={chartHeight} width="100%" radius="md" />
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
}

