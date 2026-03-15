import { useMemo, useState } from "react";
import { Box, Loader, SegmentedControl, Select, Text } from "@mantine/core";
import { IconChartFunnel, IconRoute } from "@tabler/icons-react";
import classes from "./FunnelAnalysis.module.css";
import {
  GlobalFilterBar,
  ActiveFilter,
} from "./components/GlobalFilterBar";
import { FunnelBuilder, BuilderStep } from "./components/FunnelBuilder";
import { FunnelVisualization } from "./components/FunnelVisualization";
import { FunnelDataTable } from "./components/FunnelDataTable";
import { JourneyExplorer } from "./components/JourneyExplorer";
import { DATE_RANGE_OPTIONS, getDateRangeFromPreset } from "./mockData";
import {
  useGetFunnelData,
  useGetFunnelTrend,
  useGetFunnelEvents,
  useGetFunnelFilters,
  FunnelStep,
} from "../../hooks/useGetFunnelData";

const EMPTY_STEPS: BuilderStep[] = [
  { id: "s-1", eventName: "" },
  { id: "s-2", eventName: "" },
];

function toApiSteps(steps: BuilderStep[]): FunnelStep[] {
  return steps
    .filter((s) => s.eventName)
    .map((s) => ({ eventName: s.eventName, dataType: "LOGS" as const }));
}

export function FunnelAnalysis() {
  const [activeModule, setActiveModule] = useState<"funnels" | "journeys">("funnels");
  const [dateRange, setDateRange] = useState("7d");
  const [filters, setFilters] = useState<ActiveFilter[]>([]);

  const [steps, setSteps] = useState<BuilderStep[]>(EMPTY_STEPS);
  const [funnelMode, setFunnelMode] = useState<"ordered" | "unordered">("ordered");
  const [conversionWindow, setConversionWindow] = useState("86400");
  const [shouldFetch, setShouldFetch] = useState(false);

  const { data: eventsData } = useGetFunnelEvents();
  const { data: filtersData } = useGetFunnelFilters();

  const availableEvents = eventsData?.data?.events ?? [];
  const filterOptions = filtersData?.data?.filters ?? {};

  const timeRange = useMemo(() => getDateRangeFromPreset(dateRange), [dateRange]);

  const apiSteps = useMemo(() => toApiSteps(steps), [steps]);

  const requestBody = useMemo(
    () => ({
      steps: apiSteps,
      timeRange,
      mode: "UNIQUE_USERS" as const,
      windowSeconds: parseInt(conversionWindow, 10),
    }),
    [apiSteps, timeRange, conversionWindow],
  );

  const {
    data: funnelData,
    isLoading: funnelLoading,
  } = useGetFunnelData({ requestBody, enabled: shouldFetch });

  const {
    data: trendData,
    isLoading: trendLoading,
  } = useGetFunnelTrend({ requestBody, enabled: shouldFetch });

  const funnelResult = funnelData?.data;
  const trendResult = trendData?.data;
  const isLoading = funnelLoading || trendLoading;

  const handleAnalyze = () => {
    setShouldFetch(true);
  };

  return (
    <Box className={classes.shell}>
      <Box className={classes.topBar}>
        <Box className={classes.topBarLeft}>
          <SegmentedControl
            value={activeModule}
            onChange={(val) => setActiveModule(val as "funnels" | "journeys")}
            data={[
              {
                label: (
                  <Box style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <IconChartFunnel size={15} />
                    <span>Funnels</span>
                  </Box>
                ),
                value: "funnels",
              },
              {
                label: (
                  <Box style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <IconRoute size={15} />
                    <span>Journeys</span>
                  </Box>
                ),
                value: "journeys",
              },
            ]}
            size="sm"
            color="teal"
          />
          <Text className={classes.moduleTitle}>
            {activeModule === "funnels" ? "Funnel Analysis" : "Journey Explorer"}
          </Text>
        </Box>

        <Box className={classes.topBarRight}>
          <Select
            data={DATE_RANGE_OPTIONS}
            value={dateRange}
            onChange={(val) => {
              setDateRange(val || "7d");
              setShouldFetch(false);
            }}
            size="xs"
            style={{ width: 160 }}
            allowDeselect={false}
          />
        </Box>
      </Box>

      <GlobalFilterBar
        filters={filters}
        onFiltersChange={setFilters}
        filterOptions={filterOptions}
      />

      {activeModule === "funnels" ? (
        <Box className={classes.funnelLayout}>
          <Box className={classes.sidebar}>
            <FunnelBuilder
              steps={steps}
              onStepsChange={(s) => { setSteps(s); setShouldFetch(false); }}
              funnelMode={funnelMode}
              onFunnelModeChange={setFunnelMode}
              conversionWindow={conversionWindow}
              onConversionWindowChange={(v) => { setConversionWindow(v); setShouldFetch(false); }}
              onAnalyze={handleAnalyze}
              availableEvents={availableEvents}
            />
          </Box>

          <Box className={classes.mainCanvas}>
            {isLoading && shouldFetch && (
              <Box className={classes.emptyState}>
                <Loader color="teal" size="lg" />
                <Text size="sm" c="dimmed" mt="md">Analyzing funnel...</Text>
              </Box>
            )}

            {!isLoading && funnelResult && funnelResult.steps && (
              <>
                <FunnelVisualization
                  steps={funnelResult.steps}
                  totalConversionRate={trendResult?.totalConversionRate ?? funnelResult.overallConversionRate}
                  conversionTrend={trendResult?.conversionTrend ?? 0}
                  medianTimes={trendResult?.medianTimes ?? []}
                />
                <FunnelDataTable
                  steps={funnelResult.steps}
                  timeRange={timeRange}
                  apiSteps={apiSteps}
                />
              </>
            )}

            {!isLoading && !funnelResult && (
              <Box className={classes.emptyState}>
                <Box className={classes.emptyStateIcon}>
                  <IconChartFunnel size={28} color="#0ba09a" />
                </Box>
                <Text size="lg" fw={700} c="dark.6" mt="xs">
                  Build Your Funnel
                </Text>
                <Text size="sm" c="dimmed" mt={4} maw={380}>
                  Select events for each step, set your conversion window, and
                  click "Analyze Funnel" to see results.
                </Text>
              </Box>
            )}
          </Box>
        </Box>
      ) : (
        <JourneyExplorer dateRange={dateRange} availableEvents={availableEvents} />
      )}
    </Box>
  );
}
