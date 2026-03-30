import { useMemo, useState } from "react";
import { ActionIcon, Badge, Box, Group, Loader, Text } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import { generatePath, useNavigate, useParams } from "react-router-dom";
import ReactECharts from "echarts-for-react";
import { ROUTES } from "../../constants";
import { useGetFunnelJourneyDetail } from "../../hooks/useGetFunnelJourneyDetail";
import { useUpdateFunnelJourney } from "../../hooks/useUpdateFunnelJourney";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import type { FunnelStep } from "../../hooks/useGetFunnelData";
import {
  useGetFunnelData,
  useGetFunnelEvents,
  useGetFunnelFilters,
  useGetFunnelTrend,
  useGetJourneyData,
} from "../../hooks/useGetFunnelData";
import { getDateRangeFromPreset } from "../FunnelAnalysis/mockData";
import { FunnelVisualization } from "../FunnelAnalysis/components/FunnelVisualization";
import { FunnelDataTable } from "../FunnelAnalysis/components/FunnelDataTable";
import { buildJourneySankeyOption } from "../FunnelAnalysis/utils/buildJourneySankeyOption";
import { FunnelBuilder } from "../FunnelAnalysis/components/FunnelBuilder";
import { JourneyExplorer } from "../FunnelAnalysis/components/JourneyExplorer";
import { GlobalFilterBar } from "../FunnelAnalysis/components/GlobalFilterBar";
import funnelClasses from "../FunnelAnalysis/FunnelAnalysis.module.css";
import {
  BACK_TO_LIST,
  NOT_FOUND_DESCRIPTION,
  NOT_FOUND_TITLE,
} from "./FunnelJourneyDetail.constants";
import classes from "./FunnelJourneyDetail.module.css";

const MOCK_JOURNEY_ANCHOR_EVENT = "Screen_View: Home";

function FunnelDetailView({ detail }: { detail: any }) {
  const [name, setName] = useState(detail.name || "");
  const [description, setDescription] = useState(detail.description || "");
  const [tags, setTags] = useState<string[]>(detail.tags || []);
  const [rollingType, setRollingType] = useState<"RECURRING" | "ONCE">(
    detail.rollingType || "RECURRING",
  );

  const [dateRange, setDateRange] = useState("7d");
  const [customStartDate, setCustomStartDate] = useState<Date | null>(null);
  const [customEndDate, setCustomEndDate] = useState<Date | null>(null);
  const [expiryDate, setExpiryDate] = useState<Date | null>(
    detail.expiryDate ? new Date(detail.expiryDate) : null,
  );

  const [filters, setFilters] = useState<any[]>(
    (detail.filters || []).map((f: any) => ({
      property: f.field,
      value: f.value,
    })),
  );

  const [steps, setSteps] = useState<any[]>(
    detail.steps && detail.steps.length > 0
      ? detail.steps.map((s: any, i: number) => ({
          id: `s-${i}`,
          eventName: s.eventName,
        }))
      : [
          { id: "s-1", eventName: "" },
          { id: "s-2", eventName: "" },
        ],
  );

  const [funnelMode, setFunnelMode] = useState<"ordered" | "unordered">(
    detail.funnelType === "UNORDERED" ? "unordered" : "ordered",
  );
  const [conversionWindow, setConversionWindow] = useState(
    detail.windowSeconds ? String(detail.windowSeconds) : "86400",
  );
  const [shouldFetch, setShouldFetch] = useState(true);

  const { data: eventsData } = useGetFunnelEvents();
  const availableEvents = eventsData?.data?.events ?? [];

  const { data: filtersData } = useGetFunnelFilters();
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

  const apiSteps: FunnelStep[] = useMemo(
    () =>
      steps
        .filter((s) => s.eventName)
        .map((s) => ({
          eventName: s.eventName,
          dataType: "LOGS" as const,
        })),
    [steps],
  );

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

  const isChanged = useMemo(() => {
    if (name !== detail.name) return true;
    if (description !== detail.description) return true;
    if (JSON.stringify(tags) !== JSON.stringify(detail.tags || [])) return true;
    if (rollingType !== (detail.rollingType || "RECURRING")) return true;
    if (
      funnelMode !==
      (detail.funnelType === "UNORDERED" ? "unordered" : "ordered")
    )
      return true;
    if (conversionWindow !== String(detail.windowSeconds || 86400)) return true;
    if (
      expiryDate?.toISOString() !==
      (detail.expiryDate
        ? new Date(detail.expiryDate).toISOString()
        : undefined)
    )
      return true;

    const currentFilters = filters.map((f) => ({
      field: f.property,
      value: f.value,
    }));
    const originalFilters = (detail.filters || []).map((f: any) => ({
      field: f.field,
      value: f.value,
    }));
    if (JSON.stringify(currentFilters) !== JSON.stringify(originalFilters))
      return true;

    const currentSteps = steps.map((s) => s.eventName).filter(Boolean);
    const originalSteps = (detail.steps || []).map((s: any) => s.eventName);
    if (JSON.stringify(currentSteps) !== JSON.stringify(originalSteps))
      return true;

    return false;
  }, [
    name,
    description,
    tags,
    rollingType,
    funnelMode,
    conversionWindow,
    expiryDate,
    filters,
    steps,
    detail,
  ]);

  const { mutate: updateFunnel, isPending: isUpdating } =
    useUpdateFunnelJourney();

  const handleUpdate = () => {
    updateFunnel({
      id: detail.id,
      payload: {
        name,
        description,
        tags,
        rollingType,
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
    });
  };

  const { data: funnelRes, isLoading: funnelLoading } = useGetFunnelData({
    requestBody,
    enabled: shouldFetch && apiSteps.length >= 2,
  });

  const { data: trendRes, isLoading: trendLoading } = useGetFunnelTrend({
    requestBody,
    enabled: shouldFetch && apiSteps.length >= 2,
  });

  const funnelResult = funnelRes?.data;
  const trendResult = trendRes?.data;
  const isLoading = funnelLoading || trendLoading;

  return (
    <>
      <GlobalFilterBar
        filters={filters}
        onFiltersChange={(newFilters) => {
          setFilters(newFilters);
          setShouldFetch(false);
        }}
        filterOptions={filterOptions}
      />
      <Box
        className={funnelClasses.funnelLayout}
        style={{ flex: 1, minHeight: 0, display: "flex", overflow: "hidden" }}
      >
        <Box
          className={funnelClasses.sidebar}
          style={{ overflowY: "auto", height: "100%", flexShrink: 0 }}
        >
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
            onAnalyze={handleUpdate}
            isCreating={isUpdating}
            availableEvents={availableEvents}
            isUpdateMode={true}
            isValid={isChanged}
          />
        </Box>
        <Box
          className={funnelClasses.mainCanvas}
          style={{
            minHeight: 560,
            padding: 0,
            overflowY: "auto",
            height: "100%",
            flex: 1,
          }}
        >
          {detail.status === "CREATING" || detail.status === "UPDATING" ? (
            <Box className={funnelClasses.emptyState} py={60}>
              <Loader color="blue" size="lg" />
              <Text size="lg" fw={700} c="dark.6" mt="md">
                {detail.status === "CREATING" ? "Computing" : "Updating"} Funnel
                Data
              </Text>
              <Text size="sm" c="dimmed" mt={4} maw={400} ta="center">
                Your funnel is currently being{" "}
                {detail.status === "CREATING" ? "computed" : "updated"} on the
                server. This might take a few moments. Please check back later.
              </Text>
            </Box>
          ) : isLoading ? (
            <Box className={funnelClasses.emptyState}>
              <Loader color="teal" size="lg" />
              <Text size="sm" c="dimmed" mt="md">
                Loading funnel visualization…
              </Text>
            </Box>
          ) : funnelResult?.steps?.length ? (
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
          ) : (
            <Box className={funnelClasses.emptyState}>
              <Text size="sm" c="dimmed">
                Funnel data could not be loaded.
              </Text>
            </Box>
          )}
        </Box>
      </Box>
    </>
  );
}

function JourneyDetailView({ detail }: { detail: any }) {
  const [name, setName] = useState(detail.name || "");
  const [description, setDescription] = useState(detail.description || "");
  const [tags, setTags] = useState<string[]>(detail.tags || []);
  const [rollingType, setRollingType] = useState<"RECURRING" | "ONCE">(
    detail.rollingType || "RECURRING",
  );

  const [dateRange, setDateRange] = useState("7d");
  const [customStartDate, setCustomStartDate] = useState<Date | null>(null);
  const [customEndDate, setCustomEndDate] = useState<Date | null>(null);
  const [expiryDate, setExpiryDate] = useState<Date | null>(
    detail.expiryDate ? new Date(detail.expiryDate) : null,
  );

  const [filters, setFilters] = useState<any[]>(
    (detail.filters || []).map((f: any) => ({
      property: f.field,
      value: f.value,
    })),
  );

  const [shouldFetch, setShouldFetch] = useState(true);

  const { data: eventsData } = useGetFunnelEvents();
  const availableEvents = eventsData?.data?.events ?? [];

  const { data: filtersData } = useGetFunnelFilters();
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

  const apiFilters = useMemo(
    () =>
      filters.map((f) => ({
        field: f.property,
        operator: "EQ" as const,
        value: f.value,
      })),
    [filters],
  );

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [anchorEvent, setAnchorEvent] = useState(
    detail.anchorEvent || MOCK_JOURNEY_ANCHOR_EVENT,
  );
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [direction, setDirection] = useState<"forward" | "reverse">(
    detail.direction || "forward",
  );
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [depth, setDepth] = useState(detail.depth || 5);

  const requestBody = useMemo(
    () => ({
      direction,
      anchorEvent,
      depth,
      timeRange,
      filters: apiFilters,
    }),
    [direction, anchorEvent, depth, timeRange, apiFilters],
  );

  const isChanged = useMemo(() => {
    if (name !== detail.name) return true;
    if (description !== detail.description) return true;
    if (JSON.stringify(tags) !== JSON.stringify(detail.tags || [])) return true;
    if (rollingType !== (detail.rollingType || "RECURRING")) return true;
    if (
      expiryDate?.toISOString() !==
      (detail.expiryDate
        ? new Date(detail.expiryDate).toISOString()
        : undefined)
    )
      return true;
    if (anchorEvent !== (detail.anchorEvent || MOCK_JOURNEY_ANCHOR_EVENT))
      return true;
    if (direction !== (detail.direction || "forward")) return true;
    if (depth !== (detail.depth || 5)) return true;

    const currentFilters = filters.map((f) => ({
      field: f.property,
      value: f.value,
    }));
    const originalFilters = (detail.filters || []).map((f: any) => ({
      field: f.field,
      value: f.value,
    }));
    if (JSON.stringify(currentFilters) !== JSON.stringify(originalFilters))
      return true;

    return false;
  }, [
    name,
    description,
    tags,
    rollingType,
    expiryDate,
    anchorEvent,
    direction,
    depth,
    filters,
    detail,
  ]);

  const { mutate: updateJourney, isPending: isUpdating } =
    useUpdateFunnelJourney();

  const handleUpdate = (config: any) => {
    updateJourney({
      id: detail.id,
      payload: {
        name,
        description,
        tags,
        rollingType,
        timeRange,
        filters: apiFilters,
        expiryDate:
          rollingType === "RECURRING" && expiryDate
            ? expiryDate.toISOString()
            : undefined,
        ...config,
      },
    });
  };

  const { data, isLoading } = useGetJourneyData({
    requestBody,
    enabled: shouldFetch && !!anchorEvent,
  });

  const journeyData = data?.data;

  return (
    <>
      <GlobalFilterBar
        filters={filters}
        onFiltersChange={(newFilters) => {
          setFilters(newFilters);
          setShouldFetch(false);
        }}
        filterOptions={filterOptions}
      />
      <Box
        className={funnelClasses.funnelLayout}
        style={{ flex: 1, minHeight: 0, display: "flex", overflow: "hidden" }}
      >
        <Box
          className={funnelClasses.sidebar}
          style={{ overflowY: "auto", height: "100%", flexShrink: 0 }}
        >
          <JourneyExplorer
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
            availableEvents={availableEvents}
            onCreate={handleUpdate}
            isCreating={isUpdating}
            filters={filters}
            isUpdateMode={true}
            isValid={isChanged}
          />
        </Box>
        <Box
          className={funnelClasses.mainCanvas}
          style={{
            minHeight: 560,
            padding: 0,
            overflowY: "auto",
            height: "100%",
            flex: 1,
          }}
        >
          <Box className={funnelClasses.journeyCanvas} style={{ padding: 0 }}>
            <Box className={funnelClasses.sankeyContainer}>
              <Text size="sm" fw={600} c="dark.7" mb="md">
                {direction === "forward" ? "Forward" : "Reverse"} journey from{" "}
                <Text span c="teal" fw={700}>
                  {anchorEvent}
                </Text>{" "}
                (preview · depth {depth})
              </Text>

              {detail.status === "CREATING" || detail.status === "UPDATING" ? (
                <Box className={funnelClasses.emptyState} py={60}>
                  <Loader color="blue" size="lg" />
                  <Text size="lg" fw={700} c="dark.6" mt="md">
                    {detail.status === "CREATING" ? "Computing" : "Updating"}{" "}
                    Journey Data
                  </Text>
                  <Text size="sm" c="dimmed" mt={4} maw={400} ta="center">
                    Your journey is currently being{" "}
                    {detail.status === "CREATING" ? "computed" : "updated"} on
                    the server. This might take a few moments. Please check back
                    later.
                  </Text>
                </Box>
              ) : isLoading ? (
                <Box
                  style={{
                    display: "flex",
                    justifyContent: "center",
                    padding: 80,
                  }}
                >
                  <Loader color="teal" size="lg" />
                </Box>
              ) : journeyData ? (
                <ReactECharts
                  option={buildJourneySankeyOption(journeyData)}
                  style={{ height: "520px", width: "100%" }}
                  notMerge
                />
              ) : (
                <Box className={funnelClasses.emptyState}>
                  <Text size="sm" c="dimmed">
                    Journey data could not be loaded.
                  </Text>
                </Box>
              )}
            </Box>
          </Box>
        </Box>
      </Box>
    </>
  );
}

export function FunnelJourneyDetail() {
  const navigate = useNavigate();
  const { projectId, id } = useParams<{ projectId: string; id: string }>();
  const { data: apiResponse, isLoading, error } = useGetFunnelJourneyDetail(id);
  const detail = apiResponse?.data ?? null;
  const isNotFound = apiResponse?.status === 404;
  const failMessage =
    apiResponse?.error?.message ||
    (error instanceof Error ? error.message : NOT_FOUND_TITLE);

  const goBack = () => {
    if (projectId) {
      navigate(generatePath(ROUTES.FUNNEL_ANALYSIS.path, { projectId }));
      return;
    }
    navigate(-1);
  };

  if (isLoading) {
    return (
      <Box
        className={classes.shell}
        style={{
          display: "flex",
          flexDirection: "column",
          height: "calc(100vh - 60px)",
        }}
      >
        <Group justify="center" py={80}>
          <Loader color="teal" />
        </Group>
      </Box>
    );
  }

  if (!detail) {
    return (
      <Box
        className={classes.shell}
        style={{
          display: "flex",
          flexDirection: "column",
          height: "calc(100vh - 60px)",
        }}
      >
        <Group mb="md">
          <ActionIcon variant="subtle" color="gray" onClick={goBack} size="lg">
            <IconArrowLeft size={20} />
          </ActionIcon>
          <Text size="sm" c="dimmed">
            {BACK_TO_LIST}
          </Text>
        </Group>
        <ErrorAndEmptyState
          message={failMessage}
          description={isNotFound ? NOT_FOUND_DESCRIPTION : undefined}
        />
      </Box>
    );
  }


  return (
    <Box
      className={classes.shell}
      style={{
        display: "flex",
        flexDirection: "column",
        height: "calc(100vh - 60px)",
      }}
    >
      <Box className={funnelClasses.topBar}>
        <Box className={funnelClasses.topBarLeft}>
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
              <Text className={funnelClasses.moduleTitle}>{detail.name}</Text>
              <Group gap="xs" mt={4}>
                <Badge
                  color={
                    detail.status === "ACTIVE"
                      ? "teal"
                      : detail.status === "CREATING"
                        ? "blue"
                        : detail.status === "UPDATING"
                          ? "orange"
                          : "gray"
                  }
                  variant="light"
                  size="sm"
                >
                  {detail.status === "ACTIVE"
                    ? "Active"
                    : detail.status === "CREATING"
                      ? "Creating"
                      : detail.status === "UPDATING"
                        ? "Updating"
                        : "Stopped"}
                </Badge>
                <Text size="xs" c="dimmed">
                  {detail.kind === "FUNNEL" ? "Funnel" : "Journey"}
                </Text>
              </Group>
            </Box>
          </Group>
        </Box>
      </Box>

      {detail.kind === "FUNNEL" ? (
        <FunnelDetailView detail={detail} />
      ) : (
        <JourneyDetailView detail={detail} />
      )}
    </Box>
  );
}
