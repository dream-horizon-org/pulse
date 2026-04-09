import { ActionIcon, Box, Button, Group, Loader, Text, Tooltip } from "@mantine/core";
import { IconArrowLeft, IconMinus, IconPencil, IconPlus, IconRefresh } from "@tabler/icons-react";
import { generatePath, useNavigate, useParams } from "react-router-dom";
import { ROUTES } from "../../constants";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { useGetJourneyDetail } from "../../hooks/useGetJourneyDetail";
import { FunnelJourneyDetailChrome } from "./FunnelJourneyDetailChrome";
import {
  BACK_NAV_LABEL,
  NOT_FOUND_DESCRIPTION,
  NOT_FOUND_TITLE,
} from "./FunnelJourneyDetail.constants";
import classes from "./FunnelJourneyDetail.module.css";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  useGetAllFilterValues,
  useGetFunnelEvents,
  useGetFunnelFilters,
} from "../../hooks";
import { FunnelType, type CreateJourneyRequestBody } from "../../services/funnels.service";
import { useUpdateJourney } from "../../hooks/useUpdateJourney";
import { GlobalFilterBar } from "../FunnelJourneyCreate/components/GlobalFilterBar";
import funnelClasses from "../FunnelJourneyCreate/FunnelCreate.module.css";
import { JourneyExplorer } from "../FunnelJourneyCreate/components/JourneyExplorer";
import ReactECharts from "echarts-for-react";
import { buildJourneySankeyOption } from "../FunnelJourneyCreate/utils/buildJourneySankeyOption";

function JourneyDetailView({ detail, isEditing, onEdit }: { detail: any; isEditing: boolean; onEdit: () => void }) {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string; journeyId: string }>();
  const [name, setName] = useState(detail.name || "");
  const [description, setDescription] = useState(detail.description || "");
  const [tags, setTags] = useState<string[]>(detail.tags || []);
  const [rollingType, setRollingType] = useState<FunnelType>(
    detail.journeyType || FunnelType.AUTO,
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
  const [expiryDate, setExpiryDate] = useState<Date | null>(
    detail.expiry ? new Date(detail.expiry) : null,
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

  const [zoomLevel, setZoomLevel] = useState(1);
  const handleZoomIn = () => setZoomLevel((z) => Math.min(z + 0.25, 3));
  const handleZoomOut = () => setZoomLevel((z) => Math.max(z - 0.25, 0.5));
  const handleZoomReset = () => setZoomLevel(1);

  // ── Expansion state for progressive depth reveal ──
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set());
  const [globalExpanded, setGlobalExpanded] = useState(false);

  const handleGlobalExpand = useCallback(() => {
    setGlobalExpanded(true);
    setExpandedNodes(new Set());
  }, []);
  const handleGlobalCollapse = useCallback(() => {
    setGlobalExpanded(false);
    setExpandedNodes(new Set());
  }, []);
  const handleChartClick = useCallback((params: any) => {
    if (params.data?.expandable || params.data?.expanded) {
      const nodeName = params.name as string;
      setExpandedNodes((prev) => {
        const next = new Set(prev);
        if (next.has(nodeName)) {
          next.delete(nodeName);
        } else {
          next.add(nodeName);
        }
        return next;
      });
    }
  }, []);

  const [anchorEvent, setAnchorEvent] = useState(detail.anchorEvent || "");
  const [direction, setDirection] = useState<"START" | "END">(
    detail.direction || "START",
  );
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [depth, setDepth] = useState(detail.depth || 5);

  const isChanged = useMemo(() => {
    if (name !== detail.name) return true;
    if (description !== detail.description) return true;
    if (JSON.stringify(tags) !== JSON.stringify(detail.tags || [])) return true;
    if (rollingType !== (detail.journeyType || FunnelType.AUTO)) return true;
    if (
      expiryDate?.toISOString() !==
      (detail.expiry ? new Date(detail.expiry).toISOString() : undefined)
    )
      return true;
    if (anchorEvent !== (detail.anchorEvent ?? "")) return true;
    if (direction !== (detail.direction || "START")) return true;
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
    customStartDate,
    customEndDate,
    dateRange,
    detail,
  ]);

  const { mutate: updateJourney, isPending: isUpdating } = useUpdateJourney();

  const handleUpdate = () => {
    const body: CreateJourneyRequestBody = {
      name,
      description,
      tags,
      journeyType: rollingType,
      direction,
      anchorEvent,
      depth,
      filters: apiFilters,
      dateRangeDays: parseInt(dateRange, 10) || 7,
    };

    if (rollingType === FunnelType.ONCE) {
      if (customStartDate) body.startTime = customStartDate.toISOString();
      if (customEndDate) body.endTime = customEndDate.toISOString();
    } else {
      if (expiryDate) body.expiry = expiryDate.toISOString();
    }

    updateJourney(
      { id: detail.id, payload: body },
      {
        onSuccess: () => {
          if (projectId) {
            navigate(generatePath(ROUTES.JOURNEYS_LIST.path, { projectId }));
          }
        },
      },
    );
  };

  const journeyData = detail.journeyResults as
    | { nodes: any[]; links: any[] }
    | undefined;

  const graphResult = useMemo(() => {
    if (!journeyData?.nodes?.length) return null;
    return buildJourneySankeyOption(journeyData, { expandedNodes, globalExpanded });
  }, [journeyData, expandedNodes, globalExpanded]);

  useEffect(() => {
    if (
      (detail.journeyType || FunnelType.AUTO) === FunnelType.ONCE &&
      detail.startTime &&
      detail.endTime
    ) {
      setCustomStartDate(new Date(detail.startTime));
      setCustomEndDate(new Date(detail.endTime));
    }
  }, [detail.id, detail.journeyType, detail.startTime, detail.endTime]);

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
              Edit Journey
            </Button>
          )}
          <div
            style={{
              cursor: isEditing ? undefined : "not-allowed",
            }}
          >
            <div style={{ pointerEvents: isEditing ? undefined : "none" }}>
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
            </div>
          </div>
        </Box>
        <Box
          className={funnelClasses.mainCanvas}
          style={{
            padding: 0,
            overflow: "hidden",
            flex: 1,
            display: "flex",
            flexDirection: "column",
            minHeight: 0,
          }}
        >
          <Box
            style={{
              display: "flex",
              flexDirection: "column",
              flex: 1,
              minHeight: 0,
              padding: 16,
            }}
          >
            {/* ── Header row: title + zoom controls ── */}
            <Group justify="space-between" align="center" mb="sm" style={{ flexShrink: 0 }}>
              <Text size="sm" fw={600} c="dark.7">
                {(detail.direction || "START") === "START"
                  ? "Start Point"
                  : "End Point"}{" "}
                journey from{" "}
                <Text span c="teal" fw={700}>
                  {detail.anchorEvent || "—"}
                </Text>{" "}
                (saved · depth {detail.depth ?? 5})
              </Text>

              {graphResult ? (
                <Group gap={8}>
                  {/* Expand / Collapse all depths */}
                  {graphResult.hasHiddenPaths && !globalExpanded && (
                    <Button
                      size="compact-xs"
                      variant="light"
                      color="blue"
                      onClick={handleGlobalExpand}
                    >
                      Expand All
                    </Button>
                  )}
                  {(globalExpanded || expandedNodes.size > 0) && (
                    <Button
                      size="compact-xs"
                      variant="light"
                      color="gray"
                      onClick={handleGlobalCollapse}
                    >
                      Collapse All
                    </Button>
                  )}

                  {/* Zoom controls */}
                  <Tooltip label="Zoom in" position="top" withArrow>
                    <ActionIcon variant="light" color="gray" size="sm" onClick={handleZoomIn}>
                      <IconPlus size={14} />
                    </ActionIcon>
                  </Tooltip>
                  <Tooltip label="Zoom out" position="top" withArrow>
                    <ActionIcon variant="light" color="gray" size="sm" onClick={handleZoomOut}>
                      <IconMinus size={14} />
                    </ActionIcon>
                  </Tooltip>
                  <Tooltip label="Reset zoom" position="top" withArrow>
                    <ActionIcon variant="light" color="gray" size="sm" onClick={handleZoomReset}>
                      <IconRefresh size={14} />
                    </ActionIcon>
                  </Tooltip>
                  <Text size="xs" c="dimmed" ml={4}>
                    {Math.round(zoomLevel * 100)}%
                  </Text>
                </Group>
              ) : null}
            </Group>

            {/* ── Chart area — fills remaining height ── */}
            {detail.status === "IN_PROGRESS" ? (
              <Box className={funnelClasses.emptyState} py={60}>
                <Loader color="blue" size="lg" />
                <Text size="lg" fw={700} c="dark.6" mt="md">
                  Computing Journey Data
                </Text>
                <Text size="sm" c="dimmed" mt={4} maw={400} ta="center">
                  Your journey is currently being computed on the server. This
                  might take a few moments. Please check back later.
                </Text>
              </Box>
            ) : graphResult ? (
              <Box
                style={{
                  flex: 1,
                  minHeight: 0,
                  overflow: "auto",
                  border: "1px solid #e9ecef",
                  borderRadius: 8,
                  background: "#fff",
                }}
              >
                <ReactECharts
                  option={graphResult.option}
                  style={{
                    width: `${Math.round(graphResult.graphWidth * zoomLevel)}px`,
                    height: `${Math.round(graphResult.graphHeight * zoomLevel)}px`,
                    minWidth: "100%",
                    minHeight: "100%",
                  }}
                  onEvents={{ click: handleChartClick }}
                  notMerge
                />
              </Box>
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
    </>
  );
}

export function JourneyDetail() {
  const navigate = useNavigate();
  const { journeyId } = useParams<{ projectId: string; journeyId: string }>();
  const [isEditing, setIsEditing] = useState(false);

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

  return (
    <Box
      className={classes.shell}
      style={{
        display: "flex",
        flexDirection: "column",
        height: "calc(100vh - 60px)",
      }}
    >
      <FunnelJourneyDetailChrome detail={detail} kind="JOURNEY" onBack={goBack} />
      <JourneyDetailView detail={detail} isEditing={isEditing} onEdit={() => setIsEditing(true)} />
    </Box>
  );
}
