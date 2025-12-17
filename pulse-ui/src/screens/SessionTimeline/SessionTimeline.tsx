import { useState, useMemo } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import { Box, Button, Paper, Group } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import { SessionHeader } from "./components/SessionHeader";
import { ResourceAttributesPanel } from "./components/ResourceAttributesPanel";
import { SpanFilters } from "./components/SpanFilters";
import { TimelineView } from "./components/TimelineView";
import { AttributesPanel } from "./components/AttributesPanel";
import { SessionTimelineEvent } from "./SessionTimeline.interface";
import { OtelEventType } from "./constants/otelConstants";
import { filterSpans } from "./utils/filters";
import { calculateMaxTimeFromSpans } from "./utils/formatters";
import { transformApiResponse } from "./utils/transformApiResponse";
import { DataQueryRequestBody, useGetDataQuery } from "../../hooks";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { SkeletonLoader, MetricsGridSkeleton, TableSkeleton } from "../../components/Skeletons";
import { ErrorAndEmptyStateWithNotification } from "../CriticalInteractionDetails/components/InteractionDetailsMainContent/components/ErrorAndEmptyStateWithNotification";
import classes from "./SessionTimeline.module.css";

dayjs.extend(utc);

export function SessionTimeline() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const traceId = searchParams.get("traceId");

  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState("");
  const [activeFilters, setActiveFilters] = useState<Set<OtelEventType>>(
    new Set(),
  );
  const [expandedSpans, setExpandedSpans] = useState<Set<string>>(new Set());
  const [selectedSpan, setSelectedSpan] = useState<SessionTimelineEvent | null>(
    null,
  );

  // Get time range from URL params, or fallback to last 30 days
  const timeRange = useMemo(() => {
    const startTimeParam = searchParams.get("startTime");
    const endTimeParam = searchParams.get("endTime");

    if (startTimeParam && endTimeParam) {
      return {
        start: startTimeParam,
        end: endTimeParam,
      };
    }

    // Fallback to last 30 days if not in URL
    const end = dayjs().utc();
    const start = end.subtract(30, "days");
    return {
      start: start.toISOString(),
      end: end.toISOString(),
    };
  }, [searchParams]);
  // Build request body
  const requestBody = useMemo(
    (): DataQueryRequestBody => ({
      dataType: "TRACES",
      timeRange,
      select: [
        {
          function: "COL",
          param: { field: "TraceId" },
          alias: "traceid",
        },
        {
          function: "COL",
          param: { field: "SpanId" },
          alias: "spanid",
        },
        {
          function: "COL",
          param: { field: "SpanName" },
          alias: "spanname",
        },
        {
          function: "COL",
          param: { field: "Timestamp" },
          alias: "timestamp",
        },
        {
          function: "COL",
          param: { field: "Duration" },
          alias: "duration",
        },
        {
          function: "COL",
          param: { field: "DeviceModel" },
          alias: "device",
        },
        {
          function: "COL",
          param: { field: "OsVersion" },
          alias: "os_version",
        },
        {
          function: "COL",
          param: { field: "Platform" },
          alias: "os_name",
        },
        {
          function: "COL",
          param: { field: "GeoState" },
          alias: "state",
        },
        { function: "CUSTOM", 
          param: {
            expression:
              "toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count'])",
          },
          alias: "frozen_frame" },
        { function: "CUSTOM",
          param: {
            expression:
              "toFloat64OrZero(SpanAttributes['app.interaction.analysed_frame_count'])",
          },
          alias: "analysed_frame" },
        { function: "CUSTOM",
          param: {
            expression:
              "toFloat64OrZero(SpanAttributes['app.interaction.unanalysed_frame_count'])",
          },
          alias: "unanalysed_frame" },
        {
          function: "CUSTOM",
          param: {
            expression:
              "(arrayCount(x -> x LIKE '%device.anr%', Events.Name))",
          },
          alias: "anr",
        },
        {
          function: "CUSTOM",
          param: {
            expression:
              "(arrayCount(x -> x LIKE '%device.crash%', Events.Name))",
          },
          alias: "crash",
        },
      ],
      filters: [
        {
          field: "TraceId" as const,
          operator: "EQ" as const,
          value: [traceId || ""],
        },
      ],
    }),
    [timeRange, traceId],
  );

  // Fetch session data
  const { data, isLoading, error } = useGetDataQuery({
    requestBody,
    enabled: !!id,
  });

  // Transform API response
  const { summary, events } = useMemo(() => {
    if (!data?.data) {
      return {
        summary: {
          sessionId: id || "unknown",
          platform: "unknown",
          status: "completed" as const,
          duration: 0,
          crashes: 0,
          anrs: 0,
          frozenFrames: 0,
          totalEvents: 0,
        },
        events: [],
      };
    }

    return transformApiResponse(data.data, id || "unknown");
  }, [data, id]);

  // Build request body for interaction spans query (using spanName from summary)
  const interactionSpansRequestBody = useMemo((): DataQueryRequestBody => {
    const filters: DataQueryRequestBody["filters"] = [
      {
        field: "SessionId",
        operator: "EQ",
        value: [id || ""],
      },
    ];

    // Add has() filter if spanName is available in summary
    if (summary.spanName) {
      filters.push({
        field: "SpanAttributes['pulse.interaction.active.names']",
        operator: "LIKE",
        value: [`%${summary.spanName}%`],
      });
    }

    return {
      dataType: "TRACES",
      timeRange,
      select: [
        {
          function: "COL",
          param: { field: "TraceId" },
          alias: "traceid",
        },
        {
          function: "COL",
          param: { field: "SpanId" },
          alias: "spanid",
        },
        {
          function: "COL",
          param: { field: "SpanName" },
          alias: "spanname",
        },
        {
          function: "COL",
          param: { field: "Timestamp" },
          alias: "timestamp",
        },
        {
          function: "COL",
          param: { field: "Duration" },
          alias: "duration",
        },
      ],
      filters,
    };
  }, [timeRange, id, summary.spanName]);

  console.log("interactionSpansRequestBody", interactionSpansRequestBody);

  // TODO: Uncomment this when we have the interaction spans data
  // Fetch interaction spans data
  // const {
  //   data: interactionSpansData,
  //   isLoading: isLoadingInteractionSpans,
  //   error: interactionSpansError,
  // } = useGetDataQuery({
  //   requestBody: interactionSpansRequestBody,
  //   enabled: !!id && !!summary.spanName,
  // });

  const toggleFilter = (type: OtelEventType) => {
    const newFilters = new Set(activeFilters);
    if (newFilters.has(type)) {
      newFilters.delete(type);
    } else {
      newFilters.add(type);
    }
    setActiveFilters(newFilters);
  };

  const toggleSpanExpansion = (spanId: string) => {
    const newExpanded = new Set(expandedSpans);
    if (newExpanded.has(spanId)) {
      newExpanded.delete(spanId);
    } else {
      newExpanded.add(spanId);
    }
    setExpandedSpans(newExpanded);
  };

  const filteredSpans = useMemo(
    () => filterSpans(events, activeFilters, searchQuery),
    [events, activeFilters, searchQuery],
  );

  const maxTime = useMemo(
    () => calculateMaxTimeFromSpans(filteredSpans),
    [filteredSpans],
  );

  if (isLoading) {
    return (
      <Box className={classes.container}>
        <Button
          variant="subtle"
          color="teal"
          leftSection={<IconArrowLeft size={16} />}
          onClick={() => navigate(-1)}
          className={classes.backButton}
          mb="md"
        >
          Back
        </Button>

        {/* Session Header Skeleton */}
        <Paper className={classes.sessionHeaderSkeleton} mb="md">
          <Group gap="sm" mb="md">
            <SkeletonLoader height={20} width={20} radius="sm" />
            <SkeletonLoader height={18} width={80} radius="sm" />
            <SkeletonLoader height={16} width={200} radius="sm" />
          </Group>
          <MetricsGridSkeleton count={7} />
        </Paper>

        {/* Resource Attributes Panel Skeleton */}
        <Paper className={classes.resourcePanelSkeleton} mb="md">
          <Group gap="sm" mb="md">
            <SkeletonLoader height={18} width={180} radius="sm" />
          </Group>
          <Group gap="md">
            {Array.from({ length: 4 }).map((_, i) => (
              <Box key={i} style={{ flex: 1 }}>
                <SkeletonLoader height={12} width="60%" radius="xs" />
                <SkeletonLoader height={16} width="80%" radius="sm" />
              </Box>
            ))}
          </Group>
        </Paper>

        {/* Filters Skeleton */}
        <Box mb="md">
          <SkeletonLoader height={36} width="100%" radius="md" />
        </Box>

        {/* Timeline Skeleton */}
        <TableSkeleton columns={4} rows={8} />
      </Box>
    );
  }

  if (error) {
    return (
      <Box className={classes.container}>
        <Button
          variant="subtle"
          color="teal"
          leftSection={<IconArrowLeft size={16} />}
          onClick={() => navigate(-1)}
          className={classes.backButton}
          mb="md"
        >
          Back
        </Button>
        <ErrorAndEmptyStateWithNotification
          message="Failed to load session timeline"
          errorDetails={
            error instanceof Error ? error.message : "Unknown error"
          }
        />
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      <Button
        variant="subtle"
        color="teal"
        leftSection={<IconArrowLeft size={16} />}
        onClick={() => navigate(-1)}
        className={classes.backButton}
        mb="md"
      >
        Back
      </Button>

      <SessionHeader summary={summary} />

      <ResourceAttributesPanel events={events} />

      <SpanFilters searchQuery={searchQuery} onSearchChange={setSearchQuery} />

      <TimelineView
        spans={filteredSpans}
        maxTime={maxTime}
        expandedSpans={expandedSpans}
        onToggleExpand={toggleSpanExpansion}
        activeFilters={activeFilters}
        onFilterToggle={toggleFilter}
        onSpanClick={setSelectedSpan}
      />

      <AttributesPanel
        selectedSpan={selectedSpan}
        onClose={() => setSelectedSpan(null)}
      />
    </Box>
  );
}
