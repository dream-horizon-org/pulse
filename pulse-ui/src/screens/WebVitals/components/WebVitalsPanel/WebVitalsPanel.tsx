import { Stack, SimpleGrid, Box } from "@mantine/core";
import { useState } from "react";
import { WebVitalsPanelProps } from "./WebVitalsPanel.interface";
import {
  useWebVitalsSummary,
  useWebVitalsTrend,
  useWebVitalsByScreen,
} from "../../hooks";
import { VitalCard } from "../VitalCard";
import { VitalTrendChart } from "../VitalTrendChart";
import { VitalsByScreenTable } from "../VitalsByScreenTable";
import { CardSkeleton } from "../../../../components/Skeletons/CardSkeleton";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState/ErrorAndEmptyState";
import { VITAL_NAMES } from "../../../../constants";

export function WebVitalsPanel({
  screenName,
  startTime,
  endTime,
}: WebVitalsPanelProps) {
  const [selectedVital, setSelectedVital] = useState<string>("LCP");

  // Fetch summary data
  const summaryQuery = useWebVitalsSummary({
    startTime,
    endTime,
    screenName,
  });

  // Fetch trend data for selected vital
  const trendQuery = useWebVitalsTrend({
    startTime,
    endTime,
    vitalName: selectedVital,
    screenName,
  });

  // Fetch by-screen data (only when screenName is not provided)
  const byScreenQuery = useWebVitalsByScreen({
    startTime,
    endTime,
    vitalName: selectedVital,
  });

  const isLoading = summaryQuery.isLoading;
  const error = summaryQuery.error;

  if (error) {
    return <ErrorAndEmptyState message="Error loading Web Vitals" description={error.message} />;
  }

  return (
    <Stack gap="lg">
      {/* Vital Cards Grid */}
      <Box>
        {isLoading ? (
          <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }} spacing="md">
            {Array.from({ length: 6 }).map((_, i) => (
              <CardSkeleton key={i} height={200} showHeader={false} contentRows={2} />
            ))}
          </SimpleGrid>
        ) : summaryQuery.data ? (
          <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }} spacing="md">
            {summaryQuery.data.vitals.map((vital) => (
              <VitalCard
                key={vital.name}
                name={vital.name}
                p75={vital.p75}
                goodPct={vital.goodPct}
                needsImprovementPct={vital.needsImprovementPct}
                poorPct={vital.poorPct}
                isSelected={selectedVital === vital.name}
                onSelect={() => setSelectedVital(vital.name)}
              />
            ))}
          </SimpleGrid>
        ) : null}
      </Box>

      {/* Trend Chart */}
      <Box>
        <VitalTrendChart
          vitalName={selectedVital}
          data={trendQuery.data?.points}
          isLoading={trendQuery.isLoading}
          error={trendQuery.error}
        />
      </Box>

      {/* By-Screen Table (only when screenName is not provided) */}
      {!screenName && (
        <Box>
          <VitalsByScreenTable
            data={byScreenQuery.data?.screens}
            isLoading={byScreenQuery.isLoading}
            error={byScreenQuery.error}
          />
        </Box>
      )}
    </Stack>
  );
}
