import { Box, Paper, Group, Text } from "@mantine/core";
import { SkeletonLoader } from "./SkeletonLoader";
import classes from "./Skeletons.module.css";

interface InfoCardSkeletonProps {
  title: string;
  itemCount?: number;
  columns?: number;
}

/**
 * Skeleton for info cards like Device Information and User Information
 * Displays a grid of skeleton items with labels and values
 */
export function InfoCardSkeleton({
  title,
  itemCount = 8,
  columns = 4,
}: InfoCardSkeletonProps) {
  return (
    <Paper className={classes.infoCardSkeleton}>
      <Box className={classes.topAccent} />
      
      {/* Header */}
      <Group gap="sm" mb="md">
        <SkeletonLoader height={20} width={20} radius="sm" />
        <Text className={classes.infoCardTitle}>{title}</Text>
      </Group>

      {/* Grid of info items */}
      <Box
        className={classes.infoGrid}
        style={{
          gridTemplateColumns: `repeat(${columns}, 1fr)`,
        }}
      >
        {Array.from({ length: itemCount }).map((_, index) => (
          <Box key={index} className={classes.infoItemSkeleton}>
            <SkeletonLoader height={10} width="50%" radius="xs" />
            <SkeletonLoader height={16} width="80%" radius="sm" />
          </Box>
        ))}
      </Box>
    </Paper>
  );
}

