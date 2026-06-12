import { Box, Paper, Text } from "@mantine/core";
import { SkeletonLoader } from "../SkeletonLoader";
import classes from "./GraphSkeleton.module.css";

interface GraphSkeletonProps {
  title: string;
  height?: number;
}

export function GraphSkeleton({ title, height = 225 }: GraphSkeletonProps) {
  return (
    <Paper withBorder p="md" mb="lg" className={classes.graphCard}>
      <Box className={classes.topAccent} />
      <Text className={classes.graphTitle}>{title}</Text>
      <Box style={{ height }} className={classes.graphContainer}>
        <SkeletonLoader height="100%" width="100%" radius="md" />
      </Box>
    </Paper>
  );
}
