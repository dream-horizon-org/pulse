import { useState, useMemo, useEffect, useRef } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import { Box, Button, Paper, Group, Text, Badge, Loader } from "@mantine/core";
import { IconArrowLeft, IconClock, IconDeviceMobile, IconHash } from "@tabler/icons-react";
import { FlameChart } from "./components/FlameChart";
import { DetailsSidebar } from "./components/DetailsSidebar";
import { FlameChartNode, transformToFlameChart } from "./utils/flameChartTransform";
import { useGetSessionData } from "../../hooks";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import relativeTime from "dayjs/plugin/relativeTime";
import { SkeletonLoader, MetricsGridSkeleton } from "../../components/Skeletons";
import { ErrorAndEmptyStateWithNotification } from "../CriticalInteractionDetails/components/InteractionDetailsMainContent/components/ErrorAndEmptyStateWithNotification";
import classes from "./SessionTimeline.module.css";

dayjs.extend(utc);
dayjs.extend(relativeTime);

/**
 * Format duration for display
 */
function formatDuration(ms: number): string {
  if (ms < 1000) {
    return `${ms.toFixed(0)}ms`;
  }
  if (ms < 60000) {
    return `${(ms / 1000).toFixed(2)}s`;
  }
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
}

/**
 * SessionTimeline - Displays a flame chart visualization of all traces and logs for a session
 * 
 * URL params:
 * - id: Session ID (required)
 * - traceId: Trace ID to highlight (optional)
 * - startTime: Start time for data query (optional, defaults to 30 days ago)
 * - endTime: End time for data query (optional, defaults to now)
 */
export function SessionTimeline() {
  const { id: sessionId } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const highlightTraceId = searchParams.get("traceId");

  const navigate = useNavigate();
  const [selectedItem, setSelectedItem] = useState<FlameChartNode | null>(null);
  const flameChartContainerRef = useRef<HTMLDivElement>(null);
  const hasScrolledToTrace = useRef(false);

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

  // Fetch session data (traces and logs)
  const { data: sessionData, isLoading, error } = useGetSessionData({
    sessionId: sessionId || "",
      timeRange,
    enabled: !!sessionId,
  });

  // Transform data to flame chart format
  const {
    flameChartData,
    sessionDuration,
    sessionStartTime,
    orphanItems,
    totalDepth,
  } = useMemo(() => {
    if (!sessionData?.traces && !sessionData?.logs && !sessionData?.exceptions) {
      return {
        flameChartData: [],
        sessionDuration: 0,
        sessionStartTime: Date.now(),
        orphanItems: [],
        itemsMap: new Map(),
        totalDepth: 0,
      };
    }

    return transformToFlameChart(sessionData.traces, sessionData.logs, sessionData.exceptions);
  }, [sessionData]);

  // Session summary stats
  const sessionSummary = useMemo(() => {
    const totalSpans = sessionData?.traces?.rows?.length || 0;
    const totalLogs = sessionData?.logs?.rows?.length || 0;
    const totalExceptions = sessionData?.exceptions?.rows?.length || 0;
    const orphanCount = orphanItems.length;

    // Get service name from first trace if available
    let serviceName = "";
    if (sessionData?.traces?.rows?.[0]) {
      const fields = sessionData.traces.fields;
      const row = sessionData.traces.rows[0];
      
      const serviceNameIdx = fields.findIndex((f) => f.toLowerCase() === "servicename");
      if (serviceNameIdx >= 0) {
        serviceName = String(row[serviceNameIdx] || "");
      }
    }

    return {
      totalSpans,
      totalLogs,
      totalExceptions,
      totalItems: totalSpans + totalLogs + totalExceptions,
      orphanCount,
      duration: sessionDuration,
      startTime: sessionStartTime,
      serviceName,
    };
  }, [sessionData, sessionDuration, sessionStartTime, orphanItems]);

  // Handle item click - open details sidebar
  const handleItemClick = (item: FlameChartNode) => {
    setSelectedItem(item);
  };

  // Close details sidebar
  const handleCloseSidebar = () => {
    setSelectedItem(null);
  };

  // Scroll to highlighted trace on initial load
  useEffect(() => {
    if (
      highlightTraceId &&
      flameChartData.length > 0 &&
      !hasScrolledToTrace.current &&
      flameChartContainerRef.current
    ) {
      // The FlameChart component handles scrolling internally
      hasScrolledToTrace.current = true;
    }
  }, [highlightTraceId, flameChartData]);

  // Loading state
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
          <MetricsGridSkeleton count={5} />
        </Paper>

        {/* Timeline Skeleton */}
        <Paper p="md" className={classes.timelinePaper}>
          <Box style={{ display: "flex", alignItems: "center", justifyContent: "center", height: 400 }}>
            <Loader color="teal" size="lg" />
              </Box>
        </Paper>
      </Box>
    );
  }

  // Error state
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
      {/* Back Button */}
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

      {/* Session Header */}
      <Paper className={classes.sessionHeader} mb="md">
        <Group justify="space-between" align="flex-start" wrap="nowrap">
          <Box>
            <Group gap="sm" align="center" mb="xs">
              <IconHash size={20} color="#0ec9c2" />
              <Text className={classes.sessionTitle}>Session Timeline</Text>
              {highlightTraceId && (
                <Badge color="yellow" size="sm" variant="light">
                  Highlighting Trace
                </Badge>
              )}
            </Group>
            <Text className={classes.sessionId} c="dimmed">
              {sessionId}
            </Text>
          </Box>

          <Group gap="md">
            {sessionSummary.serviceName && (
              <Box className={classes.statItem}>
                <IconDeviceMobile size={14} className={classes.statIcon} />
                <Text size="xs" c="dimmed">App</Text>
                <Text size="sm" fw={500}>{sessionSummary.serviceName}</Text>
              </Box>
            )}
          </Group>
        </Group>

        {/* Stats Row */}
        <Group gap="xl" mt="md" className={classes.statsRow}>
          <Box className={classes.statCard}>
            <Text className={classes.statValue}>{sessionSummary.totalSpans}</Text>
            <Text className={classes.statLabel}>Spans</Text>
          </Box>
          <Box className={classes.statCard}>
            <Text className={classes.statValue}>{sessionSummary.totalLogs}</Text>
            <Text className={classes.statLabel}>Logs</Text>
          </Box>
          {sessionSummary.totalExceptions > 0 && (
            <Box className={classes.statCardError}>
              <Text className={classes.statValue}>{sessionSummary.totalExceptions}</Text>
              <Text className={classes.statLabel}>Exceptions</Text>
            </Box>
          )}
          <Box className={classes.statCard}>
            <Text className={classes.statValue}>{formatDuration(sessionSummary.duration)}</Text>
            <Text className={classes.statLabel}>Duration</Text>
          </Box>
          {sessionSummary.orphanCount > 0 && (
            <Box className={classes.statCardWarning}>
              <Text className={classes.statValue}>{sessionSummary.orphanCount}</Text>
              <Text className={classes.statLabel}>Orphan Items</Text>
            </Box>
          )}
          <Box className={classes.statCard}>
            <Text className={classes.statValue}>
              <IconClock size={14} style={{ marginRight: 4 }} />
              {sessionSummary.startTime ? dayjs(sessionSummary.startTime).format("MMM D, HH:mm:ss") : "—"}
            </Text>
            <Text className={classes.statLabel}>Start Time</Text>
          </Box>
        </Group>
      </Paper>

      {/* Highlighted Trace Info */}
      {highlightTraceId && (
        <Paper className={classes.highlightInfo} mb="md">
          <Group gap="sm">
            <Badge color="yellow" size="lg" variant="light">
              🎯 Trace Highlighted
            </Badge>
            <Text size="sm" c="dimmed">
              Trace ID: <Text component="span" ff="monospace" size="sm">{highlightTraceId}</Text>
            </Text>
          </Group>
        </Paper>
      )}

      {/* Flame Chart */}
      <Paper className={classes.timelinePaper} ref={flameChartContainerRef}>
        <FlameChart
          data={flameChartData}
          sessionDuration={sessionDuration}
          sessionStartTime={sessionStartTime}
          totalDepth={totalDepth}
          highlightTraceId={highlightTraceId}
          onItemClick={handleItemClick}
          isLoading={isLoading}
        />
      </Paper>

      {/* Details Sidebar */}
      <DetailsSidebar
        item={selectedItem}
        onClose={handleCloseSidebar}
      />
    </Box>
  );
}
