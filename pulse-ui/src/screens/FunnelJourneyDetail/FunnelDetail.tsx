import { ActionIcon, Box, Group, Loader, Text } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import { useNavigate, useParams } from "react-router-dom";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { useGetFunnelDetail } from "../../hooks/useGetFunnelDetail";
import { FunnelJourneyDetailChrome } from "./FunnelJourneyDetailChrome";
import {
  BACK_NAV_LABEL,
  FUNNEL_DETAIL_WRONG_KIND_MESSAGE,
  NOT_FOUND_DESCRIPTION,
  NOT_FOUND_TITLE,
} from "./FunnelJourneyDetail.constants";
import classes from "./FunnelJourneyDetail.module.css";
import React, { useEffect, useMemo, useState } from "react";
import {
  type FunnelStep,
  useGetAllFilterValues,
  useGetFunnelData,
  useGetFunnelEvents,
  useGetFunnelFilters,
  useGetFunnelTrend,
} from "../../hooks";
import { getDateRangeFromPreset } from "../FunnelJourneyCreate/FunnelJourneyCreate.util";
import { useUpdateFunnel } from "../../hooks/useUpdateFunnel";
import { GlobalFilterBar } from "../FunnelJourneyCreate/components/GlobalFilterBar";
import funnelClasses from "../FunnelJourneyCreate/FunnelCreate.module.css";
import { FunnelBuilder } from "../FunnelJourneyCreate/components/FunnelBuilder";
import { FunnelVisualization } from "../FunnelJourneyCreate/components/FunnelVisualization";
import { FunnelDataTable } from "../FunnelJourneyCreate/components/FunnelDataTable";
import { mapDetailFilters } from "./FunnelJourneyDetails.util";
import { FunnelType, StepOrderType } from "../../services/funnels.service";

function FunnelDetailView({ detail }: { detail: any }) {
  const [name, setName] = useState(detail.name || "");
  const [description, setDescription] = useState(detail.description || "");
  const [tags, setTags] = useState<string[]>(detail.tags || []);
  const [rollingType, setRollingType] = useState<FunnelType>(
    detail.funnelType || FunnelType.AUTO,
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

  const [funnelMode, setFunnelMode] = useState<StepOrderType>(
    detail.stepOrderType || StepOrderType.ORDERED,
  );
  const [conversionWindow, setConversionWindow] = useState(
    detail.windowSeconds ? String(detail.windowSeconds) : "86400",
  );
  const [, setShouldFetch] = useState(false);
  const [initialFunnelDataFetched, setInitialFunnelDataFetched] =
    useState(false);

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
    if (rollingType === FunnelType.ONCE) {
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

  // const requestBody = useMemo(
  //   () => ({
  //     steps: apiSteps,
  //     timeRange,
  //     mode: "UNIQUE_USERS" as const,
  //     windowSeconds: parseInt(conversionWindow, 10),
  //     filters: apiFilters,
  //   }),
  //   [apiSteps, timeRange, conversionWindow, apiFilters],
  // );

  /** Server-saved snapshot only — keeps chart stable when the form (e.g. rolling type) changes */
  const stableFunnelRequestBody = useMemo(
    () => ({
      steps: (detail.steps || []).map((s: any) => ({
        eventName: s.eventName,
        dataType: "LOGS" as const,
      })),
      mode: "UNIQUE_USERS" as const,
      timeRange: detail.timeRange ?? getDateRangeFromPreset("7d"),
      windowSeconds: detail.windowSeconds ?? 86400,
      filters: mapDetailFilters(detail),
    }),
    // Intentionally tied to persisted fields only, not the whole detail object
    // eslint-disable-next-line react-hooks/exhaustive-deps -- stable snapshot for analyze/trend APIs
    [
      detail.id,
      detail.steps,
      detail.timeRange,
      detail.windowSeconds,
      detail.filters,
    ],
  );

  const visualizationTimeRange = useMemo(
    () => detail.timeRange ?? getDateRangeFromPreset("7d"),
    [detail.timeRange],
  );

  useEffect(() => {
    setInitialFunnelDataFetched(false);
  }, [detail.id]);

  useEffect(() => {
    if (
      (detail.funnelType || FunnelType.AUTO) === FunnelType.ONCE &&
      detail.timeRange?.start &&
      detail.timeRange?.end
    ) {
      setCustomStartDate(new Date(detail.timeRange.start));
      setCustomEndDate(new Date(detail.timeRange.end));
    }
  }, [detail.id, detail.funnelType, detail.timeRange]);

  const isChanged = useMemo(() => {
    if (name !== detail.name) return true;
    if (description !== detail.description) return true;
    if (JSON.stringify(tags) !== JSON.stringify(detail.tags || [])) return true;
    if (rollingType !== (detail.funnelType || FunnelType.AUTO)) return true;
    if (funnelMode !== (detail.stepOrderType || StepOrderType.ORDERED))
      return true;
    if (conversionWindow !== String(detail.windowSeconds || 86400)) return true;
    if (
      expiryDate?.toISOString() !==
      (detail.expiryDate
        ? new Date(detail.expiryDate).toISOString()
        : undefined)
    )
      return true;

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

  const { mutate: updateFunnel, isPending: isUpdating } = useUpdateFunnel();

  const handleUpdate = () => {
    updateFunnel({
      id: detail.id,
      payload: {
        name,
        description,
        tags,
        funnelType: rollingType,
        stepOrderType: funnelMode,
        steps: apiSteps,
        timeRange,
        windowSeconds: parseInt(conversionWindow, 10),
        filters: apiFilters,
        expiryDate:
          rollingType === FunnelType.AUTO && expiryDate
            ? expiryDate.toISOString()
            : undefined,
      },
    });
  };

  const vizStatuses = ["ACTIVE", "COMPLETED", "STOPPED"];

  const { data: funnelRes, isLoading: funnelLoading } = useGetFunnelData({
    requestBody: stableFunnelRequestBody,
    enabled:
      !initialFunnelDataFetched &&
      (detail.steps?.length ?? 0) >= 2 &&
      vizStatuses.includes(detail.status),
  });

  const { data: trendRes, isLoading: trendLoading } = useGetFunnelTrend({
    requestBody: stableFunnelRequestBody,
    enabled:
      !initialFunnelDataFetched &&
      (detail.steps?.length ?? 0) >= 2 &&
      vizStatuses.includes(detail.status),
  });

  // Set initial funnel data fetched flag when data is loaded
  useEffect(() => {
    if ((funnelRes || trendRes) && !initialFunnelDataFetched) {
      setInitialFunnelDataFetched(true);
    }
  }, [funnelRes, trendRes, initialFunnelDataFetched]);

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
                timeRange={visualizationTimeRange}
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

export function FunnelDetail() {
  const navigate = useNavigate();
  const { funnelId } = useParams<{ projectId: string; funnelId: string }>();

  const funnelQuery = useGetFunnelDetail(funnelId);
  const apiResponse = funnelQuery.data;
  const isLoading = funnelQuery.isLoading;
  const error = funnelQuery.error;
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

  if (detail.kind !== "FUNNEL") {
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
        <ErrorAndEmptyState message={FUNNEL_DETAIL_WRONG_KIND_MESSAGE} />
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
      <FunnelDetailView detail={detail} />
    </Box>
  );
}
