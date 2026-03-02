import { useEffect, useState } from "react";
import {
  Box,
  Group,
  Text,
  Select,
  Button,
  Loader,
  NumberInput,
} from "@mantine/core";
import { IconChartFunnel, IconPlayerPlay } from "@tabler/icons-react";
import { useSearchParams } from "react-router-dom";
import classes from "./FunnelAnalysis.module.css";
import { FunnelStepBuilder } from "./components/FunnelStepBuilder";
import { FunnelChart } from "./components/FunnelChart";
import { FunnelTable } from "./components/FunnelTable";
import { FunnelSessionDrawer } from "./components/FunnelSessionDrawer";
import {
  useGetFunnelData,
  useGetFunnelHealth,
  FunnelStep,
} from "../../hooks/useGetFunnelData";
import DateTimeRangePicker from "../CriticalInteractionDetails/components/DateTimeRangePicker/DateTimeRangePicker";
import { StartEndDateTimeType } from "../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import { useFilterStore } from "../../stores/useFilterStore";
import { getStartAndEndDateTimeString } from "../../utils/DateUtil";
import {
  DEFAULT_QUICK_TIME_FILTER,
  DEFAULT_QUICK_TIME_FILTER_INDEX,
} from "../../constants";

const MODE_OPTIONS = [
  { value: "UNIQUE_USERS", label: "Unique Users" },
  { value: "SESSIONS", label: "Sessions" },
];

const INITIAL_STEPS: FunnelStep[] = [
  { eventName: "", dataType: "TRACES" },
  { eventName: "", dataType: "TRACES" },
];

export function FunnelAnalysis() {
  const [searchParams] = useSearchParams();
  const [steps, setSteps] = useState<FunnelStep[]>(INITIAL_STEPS);
  const [mode, setMode] = useState<"UNIQUE_USERS" | "SESSIONS">("UNIQUE_USERS");
  const [windowSeconds, setWindowSeconds] = useState<number>(86400);
  const [shouldFetch, setShouldFetch] = useState(false);

  // Drawer state for session drill-down
  const [drawerOpened, setDrawerOpened] = useState(false);
  const [drawerStepLevel, setDrawerStepLevel] = useState(1);
  const [drawerIssueType, setDrawerIssueType] = useState("ALL");

  const {
    startTime: storeStartTime,
    endTime: storeEndTime,
    handleTimeFilterChange: storeHandleTimeFilterChange,
    initializeFromUrlParams,
    quickTimeRangeString,
    quickTimeRangeFilterIndex,
    selectedTimeFilter,
  } = useFilterStore();

  const getDefaultTimeRange = () => {
    return getStartAndEndDateTimeString(DEFAULT_QUICK_TIME_FILTER, 2);
  };

  useEffect(() => {
    initializeFromUrlParams(searchParams);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const startTime = storeStartTime || getDefaultTimeRange().startDate;
  const endTime = storeEndTime || getDefaultTimeRange().endDate;

  const handleTimeFilterChange = (value: StartEndDateTimeType) => {
    storeHandleTimeFilterChange(value);
  };

  const hasValidSteps =
    steps.length >= 2 && steps.every((s) => s.eventName.trim() !== "");

  const requestBody = {
    steps,
    timeRange: { start: startTime, end: endTime },
    mode,
    windowSeconds,
  };

  // Funnel analysis query
  const { data, isLoading, isError, error } = useGetFunnelData({
    requestBody,
    enabled: shouldFetch && hasValidSteps,
  });

  // Funnel health query (crash/ANR/non-fatal per step)
  const { data: healthData, isLoading: isHealthLoading } = useGetFunnelHealth({
    requestBody,
    enabled: shouldFetch && hasValidSteps,
  });

  const funnelResult = data?.data;
  const healthResult = healthData?.data;

  const handleAnalyze = () => {
    if (hasValidSteps) {
      setShouldFetch(true);
    }
  };

  useEffect(() => {
    setShouldFetch(false);
  }, [steps, mode, windowSeconds, startTime, endTime]);

  const handleStepIssueClick = (stepLevel: number, issueType: string) => {
    setDrawerStepLevel(stepLevel);
    setDrawerIssueType(issueType);
    setDrawerOpened(true);
  };

  return (
    <Box className={classes.pageContainer}>
      <Box className={classes.pageHeader}>
        <Box className={classes.titleSection}>
          <Text className={classes.pageTitle}>Funnel Analysis</Text>
        </Box>
      </Box>

      <Box className={classes.controlsSection}>
        <Group justify="space-between" align="flex-end" wrap="wrap" gap="sm">
          <Group gap="md" align="flex-end" wrap="wrap">
            <DateTimeRangePicker
              handleTimefilterChange={handleTimeFilterChange}
              selectedQuickTimeFilterIndex={
                quickTimeRangeFilterIndex !== null
                  ? quickTimeRangeFilterIndex
                  : DEFAULT_QUICK_TIME_FILTER_INDEX
              }
              defaultQuickTimeFilterIndex={DEFAULT_QUICK_TIME_FILTER_INDEX}
              defaultQuickTimeFilterString={
                quickTimeRangeString || DEFAULT_QUICK_TIME_FILTER
              }
              defaultEndTime={selectedTimeFilter?.endDate || endTime}
              defaultStartTime={selectedTimeFilter?.startDate || startTime}
            />

            <Select
              label="Count by"
              data={MODE_OPTIONS}
              value={mode}
              onChange={(value) =>
                setMode(
                  (value as "UNIQUE_USERS" | "SESSIONS") || "UNIQUE_USERS"
                )
              }
              size="sm"
              style={{ width: 160 }}
              allowDeselect={false}
            />

            <NumberInput
              label="Window (seconds)"
              value={windowSeconds}
              onChange={(value) =>
                setWindowSeconds(typeof value === "number" ? value : 86400)
              }
              min={60}
              max={604800}
              step={3600}
              size="sm"
              style={{ width: 160 }}
            />
          </Group>

          <Button
            variant="filled"
            color="teal"
            size="sm"
            leftSection={<IconPlayerPlay size={16} />}
            onClick={handleAnalyze}
            disabled={!hasValidSteps}
          >
            Analyze Funnel
          </Button>
        </Group>
      </Box>

      <FunnelStepBuilder steps={steps} onStepsChange={setSteps} />

      {(isLoading || isHealthLoading) && shouldFetch && (
        <Box className={classes.loader}>
          <Loader color="teal" size="lg" />
          <Text size="sm" c="dimmed" mt="md">
            Analyzing funnel...
          </Text>
        </Box>
      )}

      {isError && (
        <Box className={classes.emptyState}>
          <Text size="sm" c="red" fw={500}>
            Failed to load funnel data
          </Text>
          <Text size="xs" c="dimmed" mt="xs">
            {error instanceof Error ? error.message : "Unknown error"}
          </Text>
        </Box>
      )}

      {!isLoading && !isError && funnelResult && funnelResult.steps && (
        <>
          <Box className={classes.summaryCards}>
            <Box className={classes.summaryCard}>
              <Text className={classes.summaryValue}>
                {funnelResult.totalEnteredUsers.toLocaleString()}
              </Text>
              <Text className={classes.summaryLabel}>
                Total {mode === "UNIQUE_USERS" ? "Users" : "Sessions"} Entered
              </Text>
            </Box>
            <Box className={classes.summaryCard}>
              <Text className={classes.summaryValue}>
                {funnelResult.overallConversionRate}%
              </Text>
              <Text className={classes.summaryLabel}>Overall Conversion</Text>
            </Box>
            <Box className={classes.summaryCard}>
              <Text className={classes.summaryValue}>
                {funnelResult.steps.length > 0
                  ? funnelResult.steps[
                      funnelResult.steps.length - 1
                    ].count.toLocaleString()
                  : 0}
              </Text>
              <Text className={classes.summaryLabel}>Completed Funnel</Text>
            </Box>

            {/* Health summary cards */}
            {healthResult && (
              <>
                <Box className={classes.summaryCard}>
                  <Text className={classes.summaryValue} c="red">
                    {healthResult.totalCrashUsers.toLocaleString()}
                  </Text>
                  <Text className={classes.summaryLabel}>Crash Users</Text>
                </Box>
                <Box className={classes.summaryCard}>
                  <Text className={classes.summaryValue} c="orange">
                    {healthResult.totalAnrUsers.toLocaleString()}
                  </Text>
                  <Text className={classes.summaryLabel}>ANR Users</Text>
                </Box>
                <Box className={classes.summaryCard}>
                  <Text className={classes.summaryValue} c="yellow.8">
                    {healthResult.totalNonFatalUsers.toLocaleString()}
                  </Text>
                  <Text className={classes.summaryLabel}>Non-Fatal Users</Text>
                </Box>
              </>
            )}
          </Box>

          <Box className={classes.resultsSection}>
            <Box className={classes.chartContainer}>
              <Text size="sm" fw={600} mb="md" c="dark.6">
                Funnel Visualization
              </Text>
              <FunnelChart steps={funnelResult.steps} />
            </Box>

            <Box className={classes.tableContainer}>
              <Text size="sm" fw={600} mb="md" c="dark.6">
                Step Breakdown
                {healthResult && (
                  <Text span size="xs" c="dimmed" ml="xs">
                    (click issue badges to view affected sessions)
                  </Text>
                )}
              </Text>
              <FunnelTable
                steps={funnelResult.steps}
                healthData={healthResult?.steps}
                onStepClick={handleStepIssueClick}
              />
            </Box>
          </Box>
        </>
      )}

      {!isLoading && !isError && !funnelResult && !shouldFetch && (
        <Box className={classes.emptyState}>
          <IconChartFunnel size={48} color="#94a3b8" />
          <Text size="lg" fw={600} c="dark.4" mt="md">
            Build Your Funnel
          </Text>
          <Text size="sm" c="dimmed" mt="xs" maw={400}>
            Add event names for each step, select a time range, and click
            "Analyze Funnel" to see conversion metrics with crash/ANR
            correlation.
          </Text>
        </Box>
      )}

      {/* Session drill-down drawer */}
      <FunnelSessionDrawer
        opened={drawerOpened}
        onClose={() => setDrawerOpened(false)}
        stepLevel={drawerStepLevel}
        issueType={drawerIssueType}
        steps={steps}
        timeRange={{ start: startTime, end: endTime }}
        mode={mode}
        windowSeconds={windowSeconds}
      />
    </Box>
  );
}
