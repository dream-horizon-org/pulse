import { Box, Skeleton, Stack } from "@mantine/core";
import classes from "./AnalysisShimmerLoader.module.css";

interface AnalysisShimmerLoaderProps {
  type?: "chart" | "heatmap" | "donut" | "table";
  height?: number | string;
}

export const AnalysisShimmerLoader: React.FC<AnalysisShimmerLoaderProps> = ({
  type = "chart",
  height = 300,
}) => {
  if (type === "heatmap") {
    return (
      <Box className={classes.shimmerContainer} style={{ height }}>
        <Stack gap="xs">
          <Skeleton height={30} width="60%" radius="sm" />
          <Skeleton height={20} width="80%" radius="sm" />
          <Box mt="md">
            <Skeleton height={height} radius="md" />
          </Box>
        </Stack>
      </Box>
    );
  }

  if (type === "donut") {
    return (
      <Box className={classes.shimmerContainer} style={{ height }}>
        <Stack gap="xs" align="center">
          <Skeleton height={30} width="60%" radius="sm" />
          <Skeleton height={20} width="70%" radius="sm" />
          <Box mt="md">
            <Skeleton height={200} width={200} circle />
          </Box>
        </Stack>
      </Box>
    );
  }

  if (type === "table") {
    return (
      <Box className={classes.shimmerContainer} style={{ height }}>
        <Stack gap="xs">
          <Skeleton height={30} width="50%" radius="sm" />
          <Skeleton height={20} width="70%" radius="sm" />
          <Box mt="md">
            <Stack gap="xs">
              <Skeleton height={40} radius="sm" />
              <Skeleton height={35} radius="sm" />
              <Skeleton height={35} radius="sm" />
              <Skeleton height={35} radius="sm" />
              <Skeleton height={35} radius="sm" />
            </Stack>
          </Box>
        </Stack>
      </Box>
    );
  }

  // Default: chart type
  return (
    <Box className={classes.shimmerContainer} style={{ height }}>
      <Stack gap="xs">
        <Skeleton height={30} width="50%" radius="sm" />
        <Skeleton height={20} width="70%" radius="sm" />
        <Box mt="md">
          <Skeleton height={height} radius="md" />
        </Box>
      </Stack>
    </Box>
  );
};
