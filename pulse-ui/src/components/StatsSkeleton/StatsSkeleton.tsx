import { Box, Text } from "@mantine/core";
import { SkeletonLoader } from "../SkeletonLoader";
import classes from "./StatsSkeleton.module.css";

interface StatsSkeletonProps {
  title: string;
  itemCount?: number;
}

export function StatsSkeleton({ title, itemCount = 2 }: StatsSkeletonProps) {
  return (
    <Box className={classes.statSection}>
      <Text className={classes.sectionTitle}>{title}</Text>
      <Box className={classes.metricsGrid}>
        {Array.from({ length: itemCount }).map((_, index) => (
          <Box key={index} className={classes.statItem}>
            <SkeletonLoader height="11px" width="80%" radius="sm" />
            <SkeletonLoader
              height="30px"
              width="60%"
              radius="md"
              className={classes.valueSkeleton}
            />
          </Box>
        ))}
      </Box>
    </Box>
  );
}
