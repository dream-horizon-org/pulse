import { Box, Flex, Text } from "@mantine/core";
import PlatformDonutChart from "../components/PlatformDonutChart";
import { ErrorAndEmptyStateWithNotification } from "../../ErrorAndEmptyStateWithNotification";
import { AnalysisSectionSkeleton } from "../../../../../../../components/Skeletons";
import {
  ANALYSIS_ERROR_MESSAGES,
} from "../Analysis.constants";
import { AnalysisSectionProps } from "../Analysis.interface";
import { useGetPlatformInsights } from "../hooks/useGetPlatformInsights";

export const PlatformInsightsSection: React.FC<AnalysisSectionProps> = ({
  dashboardFilters,
  startTimeMs,
  endTimeMs,
  shouldFetch,
  interactionName,
}) => {
  const {
    platformData,
    isLoading,
    isError,
    error,
  } = useGetPlatformInsights({
    interactionName,
    startTime: startTimeMs,
    endTime: endTimeMs,
    enabled: shouldFetch,
    dashboardFilters,
  });

  const { poorUsersByPlatform, errorsByPlatform } = platformData;

  if (isError) {
    const errorMessage = error?.message || "Unknown error";

    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.PLATFORM_INSIGHTS.ERROR}
        errorDetails={errorMessage}
      />
    );
  }

  if (isLoading) {
    return <AnalysisSectionSkeleton chartCount={2} layout="horizontal" chartHeight={250} />;
  }

  const hasData = poorUsersByPlatform.length > 0 || errorsByPlatform.length > 0;

  if (!hasData) {
    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.PLATFORM_INSIGHTS.EMPTY}
        isError={false}
        showNotification={false}
      />
    );
  }

  return (
    <Box mb="lg">
      <Box mb="md">
        <Text
          size="sm"
          fw={700}
          c="#0ba09a"
          mb={4}
          style={{ fontSize: "16px", letterSpacing: "-0.3px" }}
        >
          Platform Insights
        </Text>
        <Text size="xs" c="dimmed" style={{ fontSize: "12px" }}>
          Which platform contributes more to negative experiences?
        </Text>
      </Box>
      <Flex gap="sm" justify="space-between" align="stretch">
        <Box flex="1">
          <PlatformDonutChart
            data={poorUsersByPlatform}
            title="Poor Users Distribution by Platform"
            description="Total number of users with poor experience"
            metricName="Poor Users"
          />
        </Box>
        <Box flex="1">
          <PlatformDonutChart
            data={errorsByPlatform}
            title="Error Rate Distribution by Platform"
            description="Error rate percentage by platform"
            metricName="Error Rate"
          />
        </Box>
      </Flex>
    </Box>
  );
};
