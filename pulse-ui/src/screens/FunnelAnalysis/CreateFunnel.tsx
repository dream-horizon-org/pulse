import { useMemo, useState } from "react";
import { ActionIcon, Box, Loader, Select, Text, Group } from "@mantine/core";
import { IconArrowLeft, IconChartFunnel } from "@tabler/icons-react";
import { useNavigate, useParams, generatePath } from "react-router-dom";
import { ROUTES } from "../../constants";
import classes from "./FunnelAnalysis.module.css";
import {
  GlobalFilterBar,
  ActiveFilter,
} from "./components/GlobalFilterBar";
import { FunnelBuilder, BuilderStep } from "./components/FunnelBuilder";
import { FunnelVisualization } from "./components/FunnelVisualization";
import { FunnelDataTable } from "./components/FunnelDataTable";
import { DATE_RANGE_OPTIONS, getDateRangeFromPreset } from "./mockData";
import {
  useGetFunnelData,
  useGetFunnelTrend,
  useGetFunnelEvents,
  useGetFunnelFilters,
  FunnelStep,
} from "../../hooks/useGetFunnelData";
import { useMutation } from "@tanstack/react-query";
import { createFunnelJourney } from "../../services/funnels.service";

const EMPTY_STEPS: BuilderStep[] = [
  { id: "s-1", eventName: "" },
  { id: "s-2", eventName: "" },
];

function toApiSteps(steps: BuilderStep[]): FunnelStep[] {
  return steps
    .filter((s) => s.eventName)
    .map((s) => ({ eventName: s.eventName, dataType: "LOGS" as const }));
}

export function CreateFunnel() {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
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

  const { mutate: createFunnel, isPending: isCreating } = useMutation({
    mutationFn: createFunnelJourney,
    onSuccess: (res) => {
      if (projectId && res.data) {
        navigate(
          generatePath(ROUTES.FUNNEL_JOURNEY_DETAIL.path, {
            projectId,
            id: res.data.id,
          })
        );
      }
    },
  });

  const handleAnalyze = () => {
    createFunnel({
      name,
      description,
      kind: "FUNNEL",
      funnelType: funnelMode.toUpperCase(),
      steps: apiSteps,
      timeRange,
      windowSeconds: parseInt(conversionWindow, 10),
    });
  };

  const goBack = () => {
    if (projectId) {
      navigate(generatePath(ROUTES.FUNNEL_ANALYSIS.path, { projectId }));
      return;
    }
    navigate(-1);
  };

  return (
    <Box className={classes.shell}>
      <Box className={classes.topBar}>
        <Box className={classes.topBarLeft}>
          <Group gap="sm">
            <ActionIcon variant="subtle" color="gray" onClick={goBack} size="lg">
              <IconArrowLeft size={20} />
            </ActionIcon>
            <Text className={classes.moduleTitle}>Create Funnel</Text>
          </Group>
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

      <Box className={classes.funnelLayout}>
        <Box className={classes.sidebar}>
          <FunnelBuilder
            name={name}
            onNameChange={setName}
            description={description}
            onDescriptionChange={setDescription}
            steps={steps}
            onStepsChange={(s) => { setSteps(s); setShouldFetch(false); }}
            funnelMode={funnelMode}
            onFunnelModeChange={setFunnelMode}
            conversionWindow={conversionWindow}
            onConversionWindowChange={(v) => { setConversionWindow(v); setShouldFetch(false); }}
            onAnalyze={handleAnalyze}
            isCreating={isCreating}
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
    </Box>
  );
}
