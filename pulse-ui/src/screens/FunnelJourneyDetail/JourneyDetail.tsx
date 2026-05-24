import { ActionIcon, Box, Button, Group, Loader, Text } from "@mantine/core";
import { IconArrowLeft, IconPencil } from "@tabler/icons-react";
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
import { FunnelType, type AnalysisBasis, type CreateJourneyRequestBody } from "../../services/funnels.service";
import { useUpdateJourney } from "../../hooks/useUpdateJourney";
import { useStopJourney } from "../../hooks/useStopJourney";
import { useDeleteJourney } from "../../hooks/useDeleteJourney";
import { GlobalFilterBar } from "../FunnelJourneyCreate/components/GlobalFilterBar";
import funnelClasses from "../FunnelJourneyCreate/FunnelCreate.module.css";
import { JourneyExplorer } from "../FunnelJourneyCreate/components/JourneyExplorer";
import { ReactFlowProvider } from "@xyflow/react";
import {
  JourneyFlowGraph,
  buildJourneyFlowData,
} from "../FunnelJourneyCreate/components/JourneyGraph";

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
  const handleToggleExpand = useCallback((nodeName: string) => {
    setExpandedNodes((prev) => {
      const next = new Set(prev);
      if (next.has(nodeName)) {
        next.delete(nodeName);
      } else {
        next.add(nodeName);
      }
      return next;
    });
  }, []);

  const [anchorEvent, setAnchorEvent] = useState(detail.anchorEvent || "");
  const [direction, setDirection] = useState<"START" | "END">(
    detail.direction || "START",
  );
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [depth, setDepth] = useState(detail.depth || 5);
  const [analysisBasis, setAnalysisBasis] = useState<AnalysisBasis>(
    detail.analysisBasis ?? "EVENT",
  );

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
    if (analysisBasis !== (detail.analysisBasis ?? "EVENT")) return true;

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
    analysisBasis,
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
      analysisBasis,
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

  const flowResult = useMemo(() => {
    if (!journeyData?.nodes?.length) return null;
    return buildJourneyFlowData(
      journeyData,
      { expandedNodes, globalExpanded },
      handleToggleExpand,
      detail.depth,
    );
  }, [journeyData, expandedNodes, globalExpanded, handleToggleExpand, detail.depth]);

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
              analysisBasis={analysisBasis}
              onAnalysisBasisChange={setAnalysisBasis}
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
            {/* ── Header row: title + expand/collapse controls ── */}
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

              {flowResult ? (
                <Group gap={8}>
                  {flowResult.hasHiddenPaths && !globalExpanded && (
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
            ) : flowResult ? (
              <Box
                style={{
                  flex: 1,
                  minHeight: 0,
                  border: "1px solid #e9ecef",
                  borderRadius: 8,
                  background: "#fff",
                }}
              >
                <ReactFlowProvider>
                  <JourneyFlowGraph
                    nodes={flowResult.nodes}
                    edges={flowResult.edges}
                  />
                </ReactFlowProvider>
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
  const { journeyId, projectId } = useParams<{
    projectId: string;
    journeyId: string;
  }>();
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

  const { mutate: stopJourneyMutation, isPending: isStopping } = useStopJourney();
  const { mutate: deleteJourneyMutation, isPending: isDeleting } = useDeleteJourney();

  const handleDeleteJourney = () => {
    if (!detail) return;
    deleteJourneyMutation(detail.id, {
      onSuccess: () => {
        if (projectId) {
          navigate(generatePath(ROUTES.JOURNEYS_LIST.path, { projectId }));
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
        kind="JOURNEY"
        analysisBasis={detail.analysisBasis ?? "EVENT"}
        onBack={goBack}
        onStop={() => stopJourneyMutation(detail.id)}
        isStopping={isStopping}
        onDelete={handleDeleteJourney}
        isDeleting={isDeleting}
      />
      <JourneyDetailView detail={detail} isEditing={isEditing} onEdit={() => setIsEditing(true)} />
    </Box>
  );
}
