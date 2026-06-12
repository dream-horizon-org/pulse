import { Box, Text, Alert } from "@mantine/core";
import { QueryStateProps } from "./QueryState.interface";
import { GraphCardSkeleton } from "../Skeletons";
import classes from "./QueryState.module.css";

export function QueryState({
  isLoading,
  isError,
  errorMessage,
  errorType,
  children,
  loadingComponent,
  emptyComponent,
  emptyMessage = "No data available",
  skeletonTitle,
  skeletonHeight,
}: QueryStateProps) {
  // Show loading state
  if (isLoading) {
    if (loadingComponent) {
      return <>{loadingComponent}</>;
    }
    // Use GraphCardSkeleton for trend graphs if title is provided
    if (skeletonTitle) {
      return (
        <GraphCardSkeleton
          title={skeletonTitle}
          chartHeight={skeletonHeight}
          metricsCount={0}
        />
      );
    }
    return (
      <Box className={classes.loadingContainer}>
        <Text size="sm" c="dimmed" ta="center">
          Loading...
        </Text>
      </Box>
    );
  }

  // Show error state
  if (isError) {
    const alertColor =
      errorType === "NETWORK"
        ? "red"
        : errorType === "CLIENT"
          ? "yellow"
          : errorType === "SERVER"
            ? "red"
            : "gray";

    return (
      <Alert
        color={alertColor}
        title={
          errorType === "NETWORK"
            ? "Network Error"
            : errorType === "CLIENT"
              ? "Request Error"
              : errorType === "SERVER"
                ? "Server Error"
                : "Error"
        }
        className={classes.errorAlert}
      >
        {errorMessage || "An error occurred while fetching data"}
      </Alert>
    );
  }

  // Show empty state if no children and emptyComponent provided
  if (emptyComponent && !children) {
    return <>{emptyComponent}</>;
  }

  // Show default empty message if no children
  if (!children) {
    return (
      <Box className={classes.emptyContainer}>
        <Text size="sm" c="dimmed" ta="center">
          {emptyMessage}
        </Text>
      </Box>
    );
  }

  // Render children (success state) with fade-in animation
  return <Box className={classes.fadeIn}>{children}</Box>;
}
