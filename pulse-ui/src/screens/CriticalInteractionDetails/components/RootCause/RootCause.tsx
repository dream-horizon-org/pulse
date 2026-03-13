import { Box, Card, Table, Text } from "@mantine/core";
import { useGetRootCause } from "../../../../hooks/useGetRootCause";
import type {
  RootCauseMetricKey,
  RootCauseMetrics,
  RootCauseSegment,
} from "../../../../hooks/useGetRootCause";
import { ErrorAndEmptyStateWithNotification } from "../InteractionDetailsMainContent/components/ErrorAndEmptyStateWithNotification";
import { TableSkeleton } from "../../../../components/Skeletons";
import {
  ROOT_CAUSE_METRIC_COLUMNS,
  ROOT_CAUSE_MESSAGES,
} from "./RootCause.constants";
import type { RootCauseProps } from "./RootCause.interface";
import classes from "./RootCause.module.css";

function formatMetricValue(
  value: number | undefined | null,
  key: RootCauseMetricKey,
  format?: "number" | "percent" | "ms",
): string {
  if (value == null || Number.isNaN(value)) return "—";
  if (format === "percent") return `${value.toFixed(2)}%`;
  if (format === "ms") return `${Math.round(value)}`;
  if (key === "volume") return String(Math.round(value));
  if (key === "apdex") return value.toFixed(2);
  return String(value);
}

function formatDelta(delta: number | undefined | null): string {
  if (delta == null || Number.isNaN(delta)) return "—";
  const sign = delta >= 0 ? "+" : "";
  return `${sign}${delta.toFixed(1)}%`;
}

function SegmentTable({
  segment,
  baseline,
}: {
  segment: RootCauseSegment;
  baseline: RootCauseMetrics;
}) {
  return (
    <div className={classes.tableWrapper}>
      <Table highlightOnHover verticalSpacing="sm">
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Metric</Table.Th>
            <Table.Th>Value</Table.Th>
            <Table.Th>Baseline</Table.Th>
            <Table.Th>Delta</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {ROOT_CAUSE_METRIC_COLUMNS.map(({ key, label, format }) => {
            const value = segment.metrics[key];
            const baseVal = baseline[key];
            const delta = segment.deltas?.[key];
            const deltaNum =
              delta != null && !Number.isNaN(delta) ? delta : null;
            const deltaClass =
              deltaNum == null
                ? classes.deltaNeutral
                : deltaNum > 0
                  ? classes.deltaPositive
                  : classes.deltaNegative;
            return (
              <Table.Tr key={key}>
                <Table.Td>{label}</Table.Td>
                <Table.Td>{formatMetricValue(value, key, format)}</Table.Td>
                <Table.Td>{formatMetricValue(baseVal, key, format)}</Table.Td>
                <Table.Td className={deltaClass}>{formatDelta(delta)}</Table.Td>
              </Table.Tr>
            );
          })}
        </Table.Tbody>
      </Table>
    </div>
  );
}

export function RootCause({ interactionName, date }: RootCauseProps) {
  const { data, isLoading, isError, error } = useGetRootCause({
    interactionName,
    date: date ?? undefined,
    enabled: !!interactionName,
  });

  if (isLoading) {
    return (
      <Box className={classes.container}>
        <Card withBorder p="lg">
          <TableSkeleton columns={4} rows={12} />
        </Card>
      </Box>
    );
  }

  if (isError) {
    return (
      <ErrorAndEmptyStateWithNotification
        message={ROOT_CAUSE_MESSAGES.ERROR}
        errorDetails={error instanceof Error ? error.message : "Unknown error"}
      />
    );
  }

  if (!data) {
    return (
      <ErrorAndEmptyStateWithNotification
        message={ROOT_CAUSE_MESSAGES.NO_DATA}
        isError={false}
        showNotification={false}
      />
    );
  }

  const { baseline, segments, cachedAt, mode, everythingGood, message } = data;

  if (everythingGood || (segments.length === 0 && message)) {
    return (
      <Box className={classes.container}>
        <ErrorAndEmptyStateWithNotification
          message={message || ROOT_CAUSE_MESSAGES.EVERYTHING_GOOD}
          isError={false}
          showNotification={false}
        />
      </Box>
    );
  }

  if (!segments || segments.length === 0) {
    return (
      <Box className={classes.container}>
        <ErrorAndEmptyStateWithNotification
          message={ROOT_CAUSE_MESSAGES.NO_DATA}
          isError={false}
          showNotification={false}
        />
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      {(cachedAt || mode) && (
        <div className={classes.metaRow}>
          {cachedAt && (
            <Text size="sm" c="dimmed">
              Data as of {new Date(cachedAt).toLocaleString()}
            </Text>
          )}
          {mode && (
            <Text size="sm" c="dimmed">
              Mode: {mode === "hierarchical" ? "Hierarchical" : "Flat"}
            </Text>
          )}
        </div>
      )}

      {segments.map((segment, index) => (
        <Card
          key={`${segment.label}-${index}`}
          className={classes.segmentCard}
          withBorder
        >
          <div className={classes.segmentLabel}>{segment.label}</div>
          <Box p="md">
            <SegmentTable segment={segment} baseline={baseline ?? {}} />
          </Box>
        </Card>
      ))}
    </Box>
  );
}
