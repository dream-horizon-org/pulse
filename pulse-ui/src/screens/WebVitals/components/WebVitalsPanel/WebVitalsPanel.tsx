import { Stack, SimpleGrid, Box } from "@mantine/core";
import { useMemo, useState } from "react";
import { WebVitalsPanelProps } from "./WebVitalsPanel.interface";
import {
  useWebVitalsSummary,
  useWebVitalsTrend,
  useWebVitalsByScreen,
} from "../../hooks";
import { useQueryError } from "../../../../hooks/useQueryError";
import type {
  WebVitalsSummaryResponse,
  WebVitalsTrendResponse,
  WebVitalsByScreenResponse,
} from "../../WebVitals.interface";
import { VitalCard } from "../VitalCard";
import { VitalTrendChart } from "../VitalTrendChart";
import { VitalsByScreenTable } from "../VitalsByScreenTable";
import { CardSkeleton } from "../../../../components/Skeletons/CardSkeleton";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState/ErrorAndEmptyState";

export function WebVitalsPanel({
  screenName,
  startTime,
  endTime,
}: WebVitalsPanelProps) {
  const [selectedVital, setSelectedVital] = useState<string>("LCP");

  const startMs = useMemo(() => Date.parse(startTime), [startTime]);
  const endMs = useMemo(() => Date.parse(endTime), [endTime]);

  // Fetch summary data (hooks expect unix ms; parents pass ISO strings)
  const summaryQuery = useWebVitalsSummary({
    startTime: startMs,
    endTime: endMs,
    screenName,
  });

  const trendQuery = useWebVitalsTrend({
    startTime: startMs,
    endTime: endMs,
    vitalName: selectedVital,
    screenName,
  });

  const byScreenQuery = useWebVitalsByScreen({
    startTime: startMs,
    endTime: endMs,
    vitalName: selectedVital,
  });

  const summaryState = useQueryError<WebVitalsSummaryResponse>({
    queryResult: summaryQuery,
  });
  const trendState = useQueryError<WebVitalsTrendResponse>({
    queryResult: trendQuery,
  });
  const byScreenState = useQueryError<WebVitalsByScreenResponse>({
    queryResult: byScreenQuery,
  });

  const vitals = summaryQuery.data?.data?.vitals;

  const isLoading = summaryState.isLoading;

  if (summaryState.isError) {
    return (
      <ErrorAndEmptyState
        message="Error loading Web Vitals"
        description={
          summaryState.errorMessage || "Failed to load Web Vitals summary"
        }
      />
    );
  }

  return (
    <Stack gap="lg">
      {/* Vital Cards Grid */}
      <Box>
        {isLoading ? (
          <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }} spacing="md">
            {Array.from({ length: 6 }).map((_, i) => (
              <CardSkeleton
                key={i}
                height={200}
                showHeader={false}
                contentRows={2}
              />
            ))}
          </SimpleGrid>
        ) : vitals ? (
          <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }} spacing="md">
            {vitals.map((vital) => (
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
          data={trendQuery.data?.data?.points}
          isLoading={trendState.isLoading}
          error={
            trendState.isError
              ? new Error(trendState.errorMessage || "Trend request failed")
              : null
          }
        />
      </Box>

      {/* By-Screen Table (only when screenName is not provided) */}
      {!screenName && (
        <Box>
          <VitalsByScreenTable
            data={byScreenQuery.data?.data?.screens}
            isLoading={byScreenState.isLoading}
            error={
              byScreenState.isError
                ? new Error(
                    byScreenState.errorMessage || "By-screen request failed",
                  )
                : null
            }
          />
        </Box>
      )}
    </Stack>
  );
}
