import { ActionIcon, Box, Button, Group, Loader, Text } from "@mantine/core";
import { IconArrowLeft, IconPencil } from "@tabler/icons-react";
import { generatePath, useNavigate, useParams } from "react-router-dom";
import { ROUTES } from "../../constants";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { useGetFunnelDetail } from "../../hooks/useGetFunnelDetail";
import { FunnelJourneyDetailChrome } from "./FunnelJourneyDetailChrome";
import {
  BACK_NAV_LABEL,
  NOT_FOUND_DESCRIPTION,
  NOT_FOUND_TITLE,
} from "./FunnelJourneyDetail.constants";
import classes from "./FunnelJourneyDetail.module.css";
import React, { useEffect, useMemo, useState } from "react";
import {
  type FunnelStep,
  useGetAllFilterValues,
  useGetFunnelEvents,
  useGetFunnelFilters,
} from "../../hooks";
import { getDateRangeFromPreset } from "../FunnelJourneyCreate/FunnelJourneyCreate.util";
import { useUpdateFunnel } from "../../hooks/useUpdateFunnel";
import { useStopFunnel } from "../../hooks/useStopFunnel";
import { useDeleteFunnel } from "../../hooks/useDeleteFunnel";
import { GlobalFilterBar } from "../FunnelJourneyCreate/components/GlobalFilterBar";
import funnelClasses from "../FunnelJourneyCreate/FunnelCreate.module.css";
import { FunnelBuilder } from "../FunnelJourneyCreate/components/FunnelBuilder";
import { FunnelVisualization } from "../FunnelJourneyCreate/components/FunnelVisualization";
import { FunnelDataTable } from "../FunnelJourneyCreate/components/FunnelDataTable";
import {
  FunnelMode,
  FunnelType,
  StepOrderType,
  type UpdateFunnelRequestBody,
} from "../../services/funnels.service";

/** Extracts an integer day-count from a preset string like "7d" → 7. */
function extractDateRangeDays(preset: string): number {
  const match = preset.match(/^(\d+)d$/);
  if (match) return parseInt(match[1], 10);
  return 1;
}

function FunnelDetailView({ detail, isEditing, onEdit }: { detail: any; isEditing: boolean; onEdit: () => void }) {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string; funnelId: string }>();
  const [name, setName] = useState(detail.name || "");
  const [description, setDescription] = useState(detail.description || "");
  const [tags, setTags] = useState<string[]>(detail.tags || []);
  const [rollingType, setRollingType] = useState<FunnelType>(
    detail.funnelType || FunnelType.AUTO,
  );

  const [dateRange, setDateRange] = useState(
    detail.dateRangeDays ? `${detail.dateRangeDays}d` : "7d",
  );
  const [customStartDate, setCustomStartDate] = useState<Date | null>(
    detail.startTime ? new Date(detail.startTime) : null,
  );
  const [customEndDate, setCustomEndDate] = useState<Date | null>(
    detail.endTime ? new Date(detail.endTime) : null,
  );
  // Backend response names this field `expiry` (see FunnelDefinitionResponse).
  // Legacy `expiryDate` is kept as a fallback for older cached payloads.
  const initialExpiry = detail.expiry ?? detail.expiryDate;
  const [expiryDate, setExpiryDate] = useState<Date | null>(
    initialExpiry ? new Date(initialExpiry) : null,
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
  const [analysisMode, setAnalysisMode] = useState<FunnelMode>(
    (detail.mode as FunnelMode) || FunnelMode.UNIQUE_USERS,
  );
  const [conversionWindow, setConversionWindow] = useState(
    detail.windowSeconds ? String(detail.windowSeconds) : "86400",
  );
  const [, setShouldFetch] = useState(false);

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

  const visualizationTimeRange = useMemo(
    () => detail.timeRange ?? getDateRangeFromPreset("7d"),
    [detail.timeRange],
  );

  useEffect(() => {
    if ((detail.funnelType || FunnelType.AUTO) === FunnelType.ONCE) {
      if (detail.startTime) setCustomStartDate(new Date(detail.startTime));
      if (detail.endTime) setCustomEndDate(new Date(detail.endTime));
    }
  }, [detail.id, detail.funnelType, detail.startTime, detail.endTime]);

  const isChanged = useMemo(() => {
    if (name !== detail.name) return true;
    if (description !== detail.description) return true;
    if (JSON.stringify(tags) !== JSON.stringify(detail.tags || [])) return true;
    if (rollingType !== (detail.funnelType || FunnelType.AUTO)) return true;
    if (funnelMode !== (detail.stepOrderType || StepOrderType.ORDERED))
      return true;
    if (analysisMode !== (detail.mode || FunnelMode.UNIQUE_USERS)) return true;
    if (conversionWindow !== String(detail.windowSeconds || 86400)) return true;
    const detailExpiry = detail.expiry ?? detail.expiryDate;
    if (
      expiryDate?.toISOString() !==
      (detailExpiry ? new Date(detailExpiry).toISOString() : undefined)
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

    const origStartIso = detail.startTime
      ? new Date(detail.startTime).toISOString()
      : undefined;
    if (customStartDate?.toISOString() !== origStartIso) return true;

    const origEndIso = detail.endTime
      ? new Date(detail.endTime).toISOString()
      : undefined;
    if (customEndDate?.toISOString() !== origEndIso) return true;

    const origDateRange = detail.dateRangeDays
      ? `${detail.dateRangeDays}d`
      : "7d";
    if (dateRange !== origDateRange) return true;

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
    analysisMode,
    conversionWindow,
    expiryDate,
    customStartDate,
    customEndDate,
    dateRange,
    filters,
    steps,
    detail,
  ]);

  const { mutate: updateFunnel, isPending: isUpdating } = useUpdateFunnel();

  const handleUpdate = () => {
    const body: UpdateFunnelRequestBody = {
      name,
      description,
      tags,
      funnelType: rollingType,
      stepOrderType: funnelMode,
      steps: apiSteps,
      windowSeconds: parseInt(conversionWindow, 10),
      filters: apiFilters,
      mode: analysisMode,
      dateRangeDays: extractDateRangeDays(dateRange),
    };

    if (rollingType === FunnelType.ONCE) {
      if (customStartDate) body.startTime = customStartDate.toISOString();
      if (customEndDate) body.endTime = customEndDate.toISOString();
    } else {
      if (expiryDate) body.expiry = expiryDate.toISOString();
    }

    updateFunnel(
      { id: detail.id, payload: body },
      {
        onSuccess: () => {
          if (projectId) {
            navigate(generatePath(ROUTES.FUNNELS_LIST.path, { projectId }));
          }
        },
      },
    );
  };

  const funnelResult = detail.funnelResults as
    | {
        steps: any[];
        overallConversionRate: number;
        totalRevenue?: number | null;
        totalOrderCount?: number | null;
        overallAvgOrderValue?: number | null;
        currency?: string | null;
      }
    | undefined;
  const revenueCurrency = detail.currency ?? funnelResult?.currency ?? null;

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
          {!isEditing && (
            <Button
              fullWidth
              variant="light"
              color="teal"
              size="sm"
              leftSection={<IconPencil size={14} />}
              onClick={onEdit}
              mb="md"
            >
              Edit Funnel
            </Button>
          )}
          <div
            style={{
              cursor: isEditing ? undefined : "not-allowed",
            }}
          >
            <div style={{ pointerEvents: isEditing ? undefined : "none" }}>
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
              analysisMode={analysisMode}
              onAnalysisModeChange={setAnalysisMode}
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
            </div>
          </div>
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
          {detail.status === "IN_PROGRESS" ? (
            <Box className={funnelClasses.emptyState} py={60}>
              <Loader color="blue" size="lg" />
              <Text size="lg" fw={700} c="dark.6" mt="md">
                Computing Funnel Data
              </Text>
              <Text size="sm" c="dimmed" mt={4} maw={400} ta="center">
                Your funnel is currently being computed on the server. This
                might take a few moments. Please check back later.
              </Text>
            </Box>
          ) : funnelResult?.steps?.length ? (
            <>
              <FunnelVisualization
                steps={funnelResult.steps}
                totalConversionRate={
                  detail.overallConversionRate ??
                  funnelResult.overallConversionRate ??
                  0
                }
                // Backend's detail response carries `conversionTrend` for the same
                // funnel that the listing shows; surface that instead of hardcoding 0.
                conversionTrend={detail.conversionTrend ?? 0}
                medianTimes={funnelResult.steps.map((s: any) => s.medianStepSeconds ?? null)}
                mode={analysisMode}
              />
              <FunnelDataTable
                steps={funnelResult.steps}
                timeRange={visualizationTimeRange}
                apiSteps={apiSteps}
                mode={analysisMode}
                currency={revenueCurrency}
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
  const { funnelId, projectId } = useParams<{
    projectId: string;
    funnelId: string;
  }>();
  const [isEditing, setIsEditing] = useState(false);

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

  const { mutate: stopFunnelMutation, isPending: isStopping } = useStopFunnel();
  const { mutate: deleteFunnelMutation, isPending: isDeleting } = useDeleteFunnel();

  const handleDeleteFunnel = () => {
    if (!detail) return;
    deleteFunnelMutation(detail.id, {
      onSuccess: () => {
        if (projectId) {
          navigate(generatePath(ROUTES.FUNNELS_LIST.path, { projectId }));
        } else {
          navigate(-1);
        }
      },
    });
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

  return (
    <Box
      className={classes.shell}
      style={{
        display: "flex",
        flexDirection: "column",
        height: "calc(100vh - 60px)",
      }}
    >
      <FunnelJourneyDetailChrome
        detail={detail}
        kind="FUNNEL"
        onBack={goBack}
        onStop={() => stopFunnelMutation(detail.id)}
        isStopping={isStopping}
        onDelete={handleDeleteFunnel}
        isDeleting={isDeleting}
      />
      <FunnelDetailView detail={detail} isEditing={isEditing} onEdit={() => setIsEditing(true)} />
    </Box>
  );
}
