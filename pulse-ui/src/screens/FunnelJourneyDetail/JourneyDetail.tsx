import { ActionIcon, Box, Group, Loader, Text } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import { useNavigate, useParams } from "react-router-dom";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { useGetJourneyDetail } from "../../hooks/useGetJourneyDetail";
import { FunnelJourneyDetailChrome } from "./FunnelJourneyDetailChrome";
import {
  BACK_NAV_LABEL,
  JOURNEY_DETAIL_WRONG_KIND_MESSAGE,
  NOT_FOUND_DESCRIPTION,
  NOT_FOUND_TITLE,
} from "./FunnelJourneyDetail.constants";
import classes from "./FunnelJourneyDetail.module.css";
import React, { useEffect, useMemo, useState } from "react";
import {
  useGetAllFilterValues,
  useGetFunnelEvents,
  useGetFunnelFilters,
  useGetJourneyData,
} from "../../hooks";
import { getDateRangeFromPreset } from "../FunnelJourneyCreate/FunnelJourneyCreate.util";
import { useUpdateJourney } from "../../hooks/useUpdateJourney";
import { GlobalFilterBar } from "../FunnelJourneyCreate/components/GlobalFilterBar";
import funnelClasses from "../FunnelJourneyCreate/FunnelCreate.module.css";
import { JourneyExplorer } from "../FunnelJourneyCreate/components/JourneyExplorer";
import ReactECharts from "echarts-for-react";
import { buildJourneySankeyOption } from "../FunnelJourneyCreate/utils/buildJourneySankeyOption";
import { mapDetailFilters } from "./FunnelJourneyDetails.util";

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
    (detail.filters || []).flatMap((f: any) => {
      const vals: string[] = Array.isArray(f.value) ? f.value : [f.value];
      return vals.map((v) => ({ property: f.field, value: String(v) }));
    }),
  );

  const { data: eventsData } = useGetFunnelEvents();
  const availableEvents = eventsData?.data?.events ?? [];

  const { data: filtersData } = useGetFunnelFilters();
  const filterKeys = useMemo(() => filtersData?.data?.filters ?? [], [filtersData?.data?.filters]);
  const filterValuesResults = useGetAllFilterValues(filterKeys, filterKeys.length > 0);
  const filterOptions = useMemo(() => {
    const result: Record<string, string[]> = {};
    filterKeys.forEach((key, index) => {
      result[key] = filterValuesResults[index]?.data?.data?.values ?? [];
    });
    return result;
  }, [filterKeys, filterValuesResults]);

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

  const apiFilters = useMemo(() => {
    const grouped: Record<string, string[]> = {};
    for (const f of filters) {
      (grouped[f.property] ??= []).push(f.value);
    }
    return Object.entries(grouped).map(([field, values]) => ({
      field,
      operator: "EQ" as const,
      value: values,
    }));
  }, [filters]);

  const [anchorEvent, setAnchorEvent] = useState(detail.anchorEvent || "");
  const [direction, setDirection] = useState<"forward" | "reverse">(
    detail.direction || "forward",
  );
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [depth, setDepth] = useState(detail.depth || 5);

  const [initialDataFetched, setInitialDataFetched] = useState(false);

  const stableJourneyRequestBody = useMemo(
    () => ({
      direction: detail.direction || "forward",
      anchorEvent: detail.anchorEvent || "",
      depth: detail.depth ?? 5,
      timeRange: detail.timeRange ?? getDateRangeFromPreset("7d"),
      filters: mapDetailFilters(detail),
    }),
    // Intentionally tied to persisted fields only, not the whole detail object
    // eslint-disable-next-line react-hooks/exhaustive-deps -- stable snapshot for journey explore API
    [
      detail.id,
      detail.direction,
      detail.anchorEvent,
      detail.depth,
      detail.timeRange,
      detail.filters,
    ],
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
    if (anchorEvent !== (detail.anchorEvent ?? "")) return true;
    if (direction !== (detail.direction || "forward")) return true;
    if (depth !== (detail.depth || 5)) return true;

    const toGrouped = (pairs: Array<{ field: string; value: string }>) => {
      const m: Record<string, string[]> = {};
      for (const p of pairs) (m[p.field] ??= []).push(p.value);
      return Object.entries(m)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([field, values]) => ({ field, values: [...values].sort() }));
    };
    const currentFiltersFlat = filters.map((f) => ({ field: f.property, value: f.value }));
    const originalFiltersFlat = (detail.filters || []).flatMap((f: any) => {
      const vals: string[] = Array.isArray(f.value) ? f.value : [f.value];
      return vals.map((v) => ({ field: f.field, value: String(v) }));
    });
    if (JSON.stringify(toGrouped(currentFiltersFlat)) !== JSON.stringify(toGrouped(originalFiltersFlat)))
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

  const { mutate: updateJourney, isPending: isUpdating } = useUpdateJourney();

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

  const journeyVizStatuses = ["ACTIVE", "COMPLETED", "STOPPED"];

  const { data, isLoading } = useGetJourneyData({
    requestBody: stableJourneyRequestBody,
    enabled:
      !initialDataFetched &&
      !!detail.anchorEvent &&
      journeyVizStatuses.includes(detail.status),
  });

  useEffect(() => {
    setInitialDataFetched(false);
  }, [detail.id]);

  useEffect(() => {
    if (data?.data != null && !initialDataFetched) {
      setInitialDataFetched(true);
    }
  }, [data, initialDataFetched]);

  useEffect(() => {
    if (
      (detail.rollingType || "RECURRING") === "ONCE" &&
      detail.timeRange?.start &&
      detail.timeRange?.end
    ) {
      setCustomStartDate(new Date(detail.timeRange.start));
      setCustomEndDate(new Date(detail.timeRange.end));
    }
  }, [detail.id, detail.rollingType, detail.timeRange]);

  const journeyData = data?.data;

  return (
    <>
      <GlobalFilterBar
        filters={filters}
        onFiltersChange={setFilters}
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
            anchorEvent={anchorEvent}
            onAnchorEventChange={setAnchorEvent}
            direction={direction}
            onDirectionChange={setDirection}
            depth={depth}
            onDepthChange={setDepth}
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
                {(detail.direction || "forward") === "forward"
                  ? "Forward"
                  : "Reverse"}{" "}
                journey from{" "}
                <Text span c="teal" fw={700}>
                  {detail.anchorEvent || "—"}
                </Text>{" "}
                (saved · depth {detail.depth ?? 5})
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

export function JourneyDetail() {
  const navigate = useNavigate();
  const { journeyId } = useParams<{ projectId: string; journeyId: string }>();

  const journeyQuery = useGetJourneyDetail(journeyId);
  const apiResponse = journeyQuery.data;
  const isLoading = journeyQuery.isLoading;
  const error = journeyQuery.error;
  const detail = apiResponse?.data ?? null;
  const isNotFound = apiResponse?.status === 404;
  const failMessage =
    apiResponse?.error?.message ||
    (error instanceof Error ? error.message : NOT_FOUND_TITLE);

  const goBack = () => {
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
            {BACK_NAV_LABEL}
          </Text>
        </Group>
        <ErrorAndEmptyState
          message={failMessage}
          description={isNotFound ? NOT_FOUND_DESCRIPTION : undefined}
        />
      </Box>
    );
  }

  if (detail.kind !== "JOURNEY") {
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
            {BACK_NAV_LABEL}
          </Text>
        </Group>
        <ErrorAndEmptyState message={JOURNEY_DETAIL_WRONG_KIND_MESSAGE} />
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
      <FunnelJourneyDetailChrome detail={detail} onBack={goBack} />
      <JourneyDetailView detail={detail} />
    </Box>
  );
}
