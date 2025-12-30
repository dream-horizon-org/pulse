import { useState, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Card,
  Text,
  Table,
  Group,
  Tooltip,
  Flex,
  Badge,
  Pagination,
  Button,
} from "@mantine/core";
import {
  IconAlertCircle,
  IconSnowflake,
  IconDeviceMobile,
  IconClock,
  IconArrowNarrowRight,
  IconActivity,
  IconX,
  IconCheck,
} from "@tabler/icons-react";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import utc from "dayjs/plugin/utc";

import type {
  FiltersState,
  ProblematicInteractionsProps,
} from "./ProblematicInteractions.interface";
import { AbsoluteNumbersForGraphs } from "../AbsoluteNumbersForGraphs/AbsoluteNumbersForGraphs";
import { ErrorAndEmptyStateWithNotification } from "../ErrorAndEmptyStateWithNotification";
import { MetricsGridSkeleton, TableSkeleton } from "../../../../../../components/Skeletons";
import {
  useGetProblematicInteractionsStats,
  useGetProblematicInteractions,
} from "../../../../../../hooks";
import type { ProblematicInteractionData } from "../../../../../../hooks";
import {
  PROBLEMATIC_INTERACTIONS_ERROR_MESSAGES,
  DEFAULT_PAGE_SIZE,
} from "./ProblematicInteractions.constants";
import classes from "./ProblematicInteractions.module.css";

dayjs.extend(utc);

dayjs.extend(relativeTime);

const ProblematicInteractions: React.FC<ProblematicInteractionsProps> = ({
  interactionName,
  startTime,
  endTime,
  dashboardFilters,
}) => {
  const navigate = useNavigate();
  const [filters, setFilters] = useState<FiltersState>({
    eventTypes: [],
    device: "all",
    timeRange: "all",
  });
  const [currentPage, setCurrentPage] = useState(0);

  // Use custom hooks for data fetching
  const {
    interactions: transformedInteractions,
    isLoading: isLoadingInteractions,
    error,
  } = useGetProblematicInteractions({
    interactionName,
    startTime,
    endTime,
    eventTypeFilters: filters.eventTypes,
    enabled: !!interactionName && !!startTime && !!endTime,
    dashboardFilters,
  });

  const { stats, isLoading: isLoadingStats } =
    useGetProblematicInteractionsStats({
      interactionName,
      startTime,
      endTime,
      enabled: !!interactionName && !!startTime && !!endTime,
      dashboardFilters,
    });

  const eventTypeConfig = {
    crash: { label: "Crash", color: "red", icon: IconAlertCircle },
    anr: { label: "ANR", color: "orange", icon: IconActivity },
    frozenFrame: {
      label: "Frozen Frames",
      color: "yellow",
      icon: IconSnowflake,
    },
    nonFatal: { label: "Non-Fatal", color: "pink", icon: IconAlertCircle },
    error: { label: "Error", color: "red", icon: IconX },
    completed: { label: "Completed", color: "green", icon: IconCheck },
  };

  const toggleEventType = (type: keyof typeof eventTypeConfig) => {
    setFilters((prev) => ({
      ...prev,
      eventTypes: prev.eventTypes.includes(
        type as FiltersState["eventTypes"][number],
      )
        ? prev.eventTypes.filter(
            (t) => t !== (type as FiltersState["eventTypes"][number]),
          )
        : ([...prev.eventTypes, type] as FiltersState["eventTypes"]),
    }));
    setCurrentPage(0);
  };

  const clearAllFilters = () => {
    setFilters((prev) => ({
      ...prev,
      eventTypes: [],
    }));
    setCurrentPage(0);
  };

  const getEventTypeConfig = (eventType: ProblematicInteractionData["event_type"]) => {
    return eventTypeConfig[eventType] || eventTypeConfig.completed;
  };

  const paginatedInteractions = useMemo(() => {
    const startIndex = currentPage * DEFAULT_PAGE_SIZE;
    const endIndex = startIndex + DEFAULT_PAGE_SIZE;
    return transformedInteractions.slice(startIndex, endIndex);
  }, [transformedInteractions, currentPage]);

  const totalPages = Math.ceil(transformedInteractions.length / DEFAULT_PAGE_SIZE);

  const isLoading = isLoadingInteractions || isLoadingStats;

  if (isLoading) {
    return (
      <Box>
        {/* Stats skeleton */}
        <Flex mt="lg" mb="lg" wrap="wrap" gap="md">
          <MetricsGridSkeleton count={5} />
        </Flex>
        
        {/* Filters skeleton */}
        <Card p="md" mb="md" withBorder>
          <Box mb="xs">
            <Text size="sm" fw={600}>Interaction Filters</Text>
          </Box>
          <Flex gap="xs">
            {Array.from({ length: 6 }).map((_, i) => (
              <Box key={i} style={{ height: 28, width: 100, background: 'rgba(14, 201, 194, 0.08)', borderRadius: 4 }} />
            ))}
          </Flex>
        </Card>
        
        {/* Table skeleton */}
        <Card withBorder>
          <TableSkeleton columns={7} rows={8} />
        </Card>
      </Box>
    );
  }

  if (error) {
    return (
      <ErrorAndEmptyStateWithNotification
        message={PROBLEMATIC_INTERACTIONS_ERROR_MESSAGES.ERROR}
        errorDetails={error instanceof Error ? error.message : "Unknown error"}
      />
    );
  }


  const formattedDuration = (duration: number) => {
    // duration is in nanoseconds, convert to milliseconds
    const durationMs = duration / 1000000;  
    
    if (durationMs > 1000) {
      return `${(durationMs / 1000).toFixed(2)}s`;
    }
    return `${durationMs.toFixed(2)}ms`;
  };

  return (
    <Box>
      <Flex mt="lg" mb="lg" wrap="wrap" gap="md">
        {[
          { label: "Total Interactions", value: stats.total, color: "black" },
          { label: "Completed", value: stats.completed, color: "green-9" },
          { label: "Errored (%)", value: stats.errored, color: "red-7" },
          { label: "Crashed (%)", value: stats.crashed, color: "red-7" },
          {
            label: "Latency (P95)",
            value: `${Math.round(stats.latency)} ms`,
            color: "black",
          },
        ].map((stat, i) => (
          <Box key={stat.label} w={200}>
            <AbsoluteNumbersForGraphs
              data={stat.value.toString()}
              title={stat.label}
              color={stat.color}
            />
          </Box>
        ))}
      </Flex>

      <Card p="md" mb="md" withBorder>
        <Group gap="xs" mb="xs" justify="space-between">
          <Text size="sm" fw={600}>
            Interaction Filters
          </Text>
        </Group>
        <Group gap="xs">
          {(
            Object.keys(eventTypeConfig) as Array<keyof typeof eventTypeConfig>
          ).map((type) => {
            const config = eventTypeConfig[type];
            const Icon = config.icon;
            const isSelected = filters.eventTypes.includes(
              type as FiltersState["eventTypes"][number],
            );
            return (
              <Button
                key={type}
                size="xs"
                variant={isSelected ? "filled" : "outline"}
                color={config.color}
                leftSection={<Icon size={16} />}
                onClick={() => toggleEventType(type)}
              >
                {config.label}
              </Button>
            );
          })}
          <Button
            size="xs"
            variant={filters.eventTypes.length === 0 ? "subtle" : "light"}
            color={filters.eventTypes.length === 0 ? "gray" : "red"}
            onClick={clearAllFilters}
            disabled={filters.eventTypes.length === 0}
            rightSection={<IconX size={16} />}
            style={{
              opacity: filters.eventTypes.length === 0 ? 0.5 : 1,
              cursor:
                filters.eventTypes.length === 0 ? "not-allowed" : "pointer",
            }}
          >
            Clear All
          </Button>
        </Group>
      </Card>

      <Card className={classes.tableCard} withBorder>
        <div className={classes.tableWrapper}>
          <Table highlightOnHover verticalSpacing="sm">
            <thead>
              <tr>
                <th align="left">Trace ID</th>
                <th>User ID</th>
                <th>Device</th>
                <th>Duration</th>
                <th>Status</th>
                <th>Date</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {paginatedInteractions.length === 0 ? (
                <tr className={classes.tableCardRow}>
                  <td colSpan={7} style={{ textAlign: "center", padding: 24 }}>
                    <Text c="dimmed">
                      No interactions found matching your filters
                    </Text>
                  </td>
                </tr>
              ) : (
                paginatedInteractions.map((interaction: ProblematicInteractionData, index: number) => (
                  <tr
                    key={`${interaction.trace_id}-${interaction.sessionId}-${index}`}
                    style={{
                      cursor: "pointer",
                      borderBottom: "1px solid #dee2e6",
                    }}
                    className={classes.tableCardRow}
                    onClick={() => {
                      const interactionTime = dayjs(interaction.start_time).utc();
                      const startTime = interactionTime
                        .subtract(30, "minutes")
                        .toISOString();
                      const endTime = interactionTime
                        .add(30, "minutes")
                        .toISOString();
                      navigate(
                        `/session/${interaction.sessionId}?traceId=${interaction.trace_id}&startTime=${encodeURIComponent(startTime)}&endTime=${encodeURIComponent(endTime)}`,
                      );
                    }}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        const interactionTime = dayjs(interaction.start_time).utc();
                        const startTime = interactionTime
                          .subtract(30, "minutes")
                          .toISOString();
                        const endTime = interactionTime
                          .add(30, "minutes")
                          .toISOString();
                        navigate(
                          `/session/${interaction.sessionId}?traceId=${interaction.trace_id}&startTime=${encodeURIComponent(startTime)}&endTime=${encodeURIComponent(endTime)}`,
                        );
                      }
                    }}
                    tabIndex={0}
                  >
                    <td>
                      <Text fw={500}>{interaction.trace_id}</Text>
                    </td>
                    <td>
                      <Text>{interaction.user_id}</Text>
                    </td>
                    <td>
                      <Group gap={6}>
                        <IconDeviceMobile size={16} />
                        <Box>
                          <Text>{interaction.device}</Text>
                          <Text size="xs" c="dimmed">
                            {interaction.os_version}
                          </Text>
                        </Box>
                      </Group>
                    </td>
                    <td>
                      <Group gap={4}>
                        <IconClock size={16} />
                        <Text>{formattedDuration(interaction.duration_ms)}</Text>
                      </Group>
                    </td>
                    <td>
                      {(() => {
                        const config = getEventTypeConfig(interaction.event_type);
                        const Icon = config.icon;
                        return (
                          <Badge color={config.color} size="md">
                            <Flex align="center" gap="xs">
                              <Icon size={16} />
                              {config.label}
                            </Flex>
                          </Badge>
                        );
                      })()}
                    </td>
                    <td>
                      <Text>
                        {dayjs(interaction.start_time).format("MMM D, YYYY")}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {dayjs(interaction.start_time).format("HH:mm:ss")}
                      </Text>
                    </td>
                    <td>
                      <Box className={classes.actionButtonContainer}>
                        <Tooltip label="View Session Timeline">
                          <IconArrowNarrowRight
                            className={classes.actionButton}
                            size={18}
                          />
                        </Tooltip>
                      </Box>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </Table>
        </div>

        {totalPages > 1 && (
          <Box mt="md" style={{ display: "flex", justifyContent: "right" }}>
            <Pagination
              total={totalPages}
              value={currentPage + 1}
              onChange={(page) => setCurrentPage(page - 1)}
              size="sm"
              withEdges
            />
          </Box>
        )}
      </Card>
    </Box>
  );
};

export default ProblematicInteractions;
