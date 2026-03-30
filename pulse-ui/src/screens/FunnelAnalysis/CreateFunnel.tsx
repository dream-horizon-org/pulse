import { useMemo, useState } from "react";
import { ActionIcon, Box, Group, Loader, Text } from "@mantine/core";
import { IconArrowLeft, IconChartFunnel } from "@tabler/icons-react";
import { generatePath, useNavigate, useParams } from "react-router-dom";
import { ROUTES } from "../../constants";
import classes from "./FunnelAnalysis.module.css";
import { ActiveFilter, GlobalFilterBar } from "./components/GlobalFilterBar";
import { BuilderStep, FunnelBuilder } from "./components/FunnelBuilder";
import { FunnelVisualization } from "./components/FunnelVisualization";
import { FunnelDataTable } from "./components/FunnelDataTable";
import { getDateRangeFromPreset } from "./mockData";
import {
  FunnelStep,
  useGetFunnelData,
  useGetFunnelEvents,
  useGetFunnelFilters,
  useGetFunnelTrend
} from "../../hooks/useGetFunnelData";
import { useCreateFunnelJourney } from "../../hooks/useCreateFunnelJourney";

const EMPTY_STEPS: BuilderStep[] = [
  { id: "s-1", eventName: "" },
  { id: "s-2", eventName: "" },
];

function toApiSteps(steps: BuilderStep[]): FunnelStep[] {
  return steps
    .filter((s) => s.eventName)
    .map((s) => {
      const apiStep: FunnelStep = {
        eventName: s.eventName,
        dataType: "LOGS" as const,
      };

      return apiStep;
    });
}

export function CreateFunnel() {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [tags, setTags] = useState<string[]>([]);
  const [rollingType, setRollingType] = useState<"RECURRING" | "ONCE">(
    "RECURRING",
  );
  const [dateRange, setDateRange] = useState("7d");
  const [customStartDate, setCustomStartDate] = useState<Date | null>(null);
  const [customEndDate, setCustomEndDate] = useState<Date | null>(null);
  const [expiryDate, setExpiryDate] = useState<Date | null>(null);
  const [filters, setFilters] = useState<ActiveFilter[]>([]);

  const [steps, setSteps] = useState<BuilderStep[]>(EMPTY_STEPS);
  const [funnelMode, setFunnelMode] = useState<"ordered" | "unordered">(
    "ordered",
  );
  const [conversionWindow, setConversionWindow] = useState("86400");
  const [shouldFetch, setShouldFetch] = useState(false);

  const { data: eventsData } = useGetFunnelEvents();
  const { data: filtersData } = useGetFunnelFilters();

  const availableEvents = eventsData?.data?.events ?? [];

  const EXPECTED_FILTER_KEYS = ["OS Name", "OS Version", "App Version"];
  const filterOptions = EXPECTED_FILTER_KEYS.reduce(
    (acc, key) => {
      acc[key] = filtersData?.data?.filters?.[key] ?? [];
      return acc;
    },
    {} as Record<string, string[]>,
  );

  const timeRange = useMemo(() => {
    if (rollingType === "ONCE") {
      return {
        start: customStartDate
          ? customStartDate.toISOString()
          : new Date().toISOString(),
        end: customEndDate
          ? customEndDate.toISOString()
          : new Date().toISOString(),
      };
    }
    return getDateRangeFromPreset(dateRange);
  }, [rollingType, dateRange, customStartDate, customEndDate]);

  const apiSteps = useMemo(() => toApiSteps(steps), [steps]);

  const apiFilters = useMemo(
    () =>
      filters.map((f) => ({
        field: f.property,
        operator: "EQ" as const,
        value: f.value,
      })),
    [filters],
  );

  const requestBody = useMemo(
    () => ({
      steps: apiSteps,
      timeRange,
      mode: "UNIQUE_USERS" as const,
      windowSeconds: parseInt(conversionWindow, 10),
      filters: apiFilters,
    }),
    [apiSteps, timeRange, conversionWindow, apiFilters],
  );

  const { data: funnelData, isLoading: funnelLoading } = useGetFunnelData({
    requestBody,
    enabled: shouldFetch,
  });

  const { data: trendData, isLoading: trendLoading } = useGetFunnelTrend({
    requestBody,
    enabled: shouldFetch,
  });

  const funnelResult = funnelData?.data;
  const trendResult = trendData?.data;
  const isLoading = funnelLoading || trendLoading;

  const { mutate: createFunnel, isPending: isCreating } =
    useCreateFunnelJourney();

  const handleAnalyze = () => {
    createFunnel(
      {
        name,
        description,
        tags,
        rollingType,
        kind: "FUNNEL",
        funnelType: funnelMode.toUpperCase(),
        steps: apiSteps,
        timeRange,
        windowSeconds: parseInt(conversionWindow, 10),
        filters: apiFilters,
        expiryDate:
          rollingType === "RECURRING" && expiryDate
            ? expiryDate.toISOString()
            : undefined,
      },
      {
        onSuccess: (res) => {
          if (projectId && res.data) {
            navigate(
              generatePath(ROUTES.FUNNEL_JOURNEY_DETAIL.path, {
                projectId,
                id: res.data.id,
              }),
            );
          }
        },
      },
    );
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
          <Group gap="sm" align="center">
            <ActionIcon
              variant="subtle"
              color="gray"
              onClick={goBack}
              size="lg"
            >
              <IconArrowLeft size={20} />
            </ActionIcon>
            <Box>
              <Text className={classes.moduleTitle}>Create Funnel</Text>
            </Box>
          </Group>
        </Box>

        <Box
          className={classes.topBarRight}
          style={{ display: "flex", gap: 12, alignItems: "center" }}
        ></Box>
      </Box>

      <GlobalFilterBar
        filters={filters}
        onFiltersChange={(newFilters) => {
          setFilters(newFilters);
          setShouldFetch(false);
        }}
        filterOptions={filterOptions}
      />

      <Box className={classes.funnelLayout}>
        <Box className={classes.sidebar}>
          <FunnelBuilder
            name={name}
            onNameChange={setName}
            description={description}
            onDescriptionChange={setDescription}
            tags={tags}
            onTagsChange={setTags}
            rollingType={rollingType}
            onRollingTypeChange={setRollingType}
            dateRange={dateRange}
            onDateRangeChange={setDateRange}
            customStartDate={customStartDate}
            onCustomStartDateChange={setCustomStartDate}
            customEndDate={customEndDate}
            onCustomEndDateChange={setCustomEndDate}
            expiryDate={expiryDate}
            onExpiryDateChange={setExpiryDate}
            steps={steps}
            onStepsChange={(s) => {
              setSteps(s);
              setShouldFetch(false);
            }}
            funnelMode={funnelMode}
            onFunnelModeChange={setFunnelMode}
            conversionWindow={conversionWindow}
            onConversionWindowChange={(v) => {
              setConversionWindow(v);
              setShouldFetch(false);
            }}
            onAnalyze={handleAnalyze}
            isCreating={isCreating}
            availableEvents={availableEvents}
          />
        </Box>

        <Box className={classes.mainCanvas}>
          {isLoading && shouldFetch && (
            <Box className={classes.emptyState}>
              <Loader color="teal" size="lg" />
              <Text size="sm" c="dimmed" mt="md">
                Analyzing funnel...
              </Text>
            </Box>
          )}

          {!isLoading && funnelResult && funnelResult.steps && (
            <>
              <FunnelVisualization
                steps={funnelResult.steps}
                totalConversionRate={
                  trendResult?.totalConversionRate ??
                  funnelResult.overallConversionRate
                }
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
