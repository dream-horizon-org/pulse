import { Center, LoadingOverlay } from "@mantine/core";
import classes from "./GraphLoadingAndErrorHandler.module.css";

export function GraphLoadingAndErrorHandler({
  isLoading,
  error,
  hasNoData,
}: {
  isLoading: boolean;
  error?: string;
  hasNoData?: boolean;
}) {
  return (
    <Center>
      <LoadingOverlay
        visible={isLoading}
        loaderProps={{
          color: "blue",
          size: "sm",
          speed: "xl",
        }}
        style={{
          zIndex: 100,
        }}
      />
      {isLoading ? null : error ? (
        <div className={classes.errorOverlay}>{error}</div>
      ) : hasNoData ? (
        <div className={classes.noDataOverlay}>No data available</div>
      ) : null}
    </Center>
  );
}
