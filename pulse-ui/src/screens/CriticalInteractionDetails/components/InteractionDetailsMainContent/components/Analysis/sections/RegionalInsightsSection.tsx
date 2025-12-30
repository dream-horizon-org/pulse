import { Box, Flex, Text } from "@mantine/core";
import GeographicHeatmap from "../components/GeographicHeatmap";
import { ErrorAndEmptyStateWithNotification } from "../../ErrorAndEmptyStateWithNotification";
import { AnalysisSectionSkeleton } from "../../../../../../../components/Skeletons";
import {
  ANALYSIS_ERROR_MESSAGES,
} from "../Analysis.constants";
import { AnalysisSectionProps } from "../Analysis.interface";
import { useGetRegionalInsights } from "../hooks/useGetRegionalInsights";

export const RegionalInsightsSection: React.FC<AnalysisSectionProps> = ({
  dashboardFilters,
  startTimeMs,
  endTimeMs,
  shouldFetch,
  interactionName,
}) => {
  const {
    regionalData,
    isLoading,
    isError,
    error,
  } = useGetRegionalInsights({
    interactionName,
    startTime: startTimeMs,
    endTime: endTimeMs,
    enabled: shouldFetch,
    dashboardFilters,
  });

  const { errorRateByRegion, poorUsersPercentageByRegion } = regionalData;

  if (isError) {
    const errorMessage = error?.message || "Unknown error";

    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.REGIONAL_INSIGHTS.ERROR}
        errorDetails={errorMessage}
      />
    );
  }

  if (isLoading) {
    return <AnalysisSectionSkeleton chartCount={2} layout="horizontal" chartHeight={300} />;
  }

  const hasData =
    errorRateByRegion.length > 0 || poorUsersPercentageByRegion.length > 0;

  if (!hasData) {
    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.REGIONAL_INSIGHTS.EMPTY}
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
          Regional Insights
        </Text>
        <Text size="xs" c="dimmed" style={{ fontSize: "12px" }}>
          Where are the biggest problems located?
        </Text>
      </Box>
      <Flex gap="sm" justify="space-between" align="stretch">
        <Box flex="1">
          <GeographicHeatmap
            data={errorRateByRegion}
            title="Error Rate by Region"
            description="User perceived error rate by location • Darker = Higher Errors"
            metricLabel="error rate"
            metricSuffix="%"
          />
        </Box>
        <Box flex="1">
          <GeographicHeatmap
            data={poorUsersPercentageByRegion}
            title="Poor Users Percentage by Region"
            description="Percentage of users with poor experience • Darker = Higher %"
            metricLabel="poor users"
            metricSuffix="%"
          />
        </Box>
      </Flex>
    </Box>
  );
};
