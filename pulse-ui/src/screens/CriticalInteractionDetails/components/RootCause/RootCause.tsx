import { Box, Button, Card, Skeleton, Stack, Table, Text } from "@mantine/core";
import { IconRefresh } from "@tabler/icons-react";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { useGetRootCause } from "../../../../hooks/useGetRootCause";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import {
  ROOT_CAUSE_METRIC_LABELS,
  ROOT_CAUSE_METRIC_ORDER,
  ROOT_CAUSE_MESSAGES,
} from "./RootCause.constants";
import type { RootCauseProps } from "./RootCause.interface";
import type { RootCauseSegment } from "../../../../hooks/useGetRootCause";
import classes from "./RootCause.module.css";

dayjs.extend(utc);

function formatMetricValue(value: number | string): string {
  if (typeof value === "number") {
    if (Number.isInteger(value)) return String(value);
    return value.toFixed(2);
  }
  return String(value);
}

function orderedMetricKeys(metrics: Record<string, number | string>): string[] {
  const keys = Object.keys(metrics);
  const ordered: string[] = [];
  for (const k of ROOT_CAUSE_METRIC_ORDER) {
    if (keys.includes(k)) {
      ordered.push(k);
    }
  }
  const rest = keys.filter((k) => !ROOT_CAUSE_METRIC_ORDER.includes(k));
  return [...ordered, ...rest];
}

function SegmentTable({
  segment,
  baseline,
}: {
  segment: RootCauseSegment;
  baseline: Record<string, number | string>;
}) {
  const metricKeys = orderedMetricKeys(segment.metrics);
  if (metricKeys.length === 0) return null;

  return (
    <Table className={classes.metricsTable} withTableBorder>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Metric</Table.Th>
          <Table.Th>Value</Table.Th>
          <Table.Th>Baseline</Table.Th>
          <Table.Th>Delta</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {metricKeys.map((key) => {
          const value = segment.metrics[key];
          const base = baseline[key];
          const delta = segment.deltas[key];
          const deltaNum =
            typeof delta === "number" ? delta : parseFloat(String(delta));
          const isDeltaPositive =
            typeof delta === "number"
              ? delta > 0
              : typeof delta === "string" &&
                  !Number.isNaN(deltaNum) &&
                  String(delta).trim() !== ""
                ? deltaNum > 0
                : false;
          return (
            <Table.Tr key={key}>
              <Table.Td>{ROOT_CAUSE_METRIC_LABELS[key] ?? key}</Table.Td>
              <Table.Td>{formatMetricValue(value)}</Table.Td>
              <Table.Td>
                {base != null ? formatMetricValue(base) : "—"}
              </Table.Td>
              <Table.Td>
                {delta != null && delta !== "" ? (
                  <span
                    className={
                      isDeltaPositive
                        ? classes.deltaPositive
                        : classes.deltaNegative
                    }
                  >
                    {formatMetricValue(delta)}
                  </span>
                ) : (
                  "—"
                )}
              </Table.Td>
            </Table.Tr>
          );
        })}
      </Table.Tbody>
    </Table>
  );
}

export function RootCause({ interactionName, date }: RootCauseProps) {
  const {
    data: response,
    isLoading,
    isError,
    refetch,
    error,
  } = useGetRootCause({
    interactionName,
    date: date ?? null,
    enabled: !!interactionName,
  });

  const status = response?.status;
  const payload = response?.data ?? null;
  const is404 = status === 404;

  if (isLoading) {
    return (
      <Box className={classes.container}>
        <div className={classes.skeletonWrapper}>
          <Skeleton height={24} width={200} mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} />
        </div>
      </Box>
    );
  }

  if (isError || is404) {
    const message = is404
      ? ROOT_CAUSE_MESSAGES.FEATURE_OR_NO_DATA
      : (error?.message ?? response?.error?.message ?? "Something went wrong.");
    const isTimeout =
      message.toLowerCase().includes("timeout") ||
      message === "Request Timeout";
    const displayMessage = isTimeout
      ? ROOT_CAUSE_MESSAGES.REQUEST_TIMEOUT
      : message;
    return (
      <Box className={classes.container}>
        <Stack align="center" gap="md" className={classes.stateMessage}>
          <ErrorAndEmptyState
            message={displayMessage}
            classes={[classes.errorState]}
          />
          <Button
            className={classes.retryButton}
            leftSection={<IconRefresh size={16} />}
            variant="light"
            onClick={() => refetch()}
          >
            Retry
          </Button>
        </Stack>
      </Box>
    );
  }

  if (
    payload?.noDataAvailable ||
    (payload && !payload.segments?.length && payload.message)
  ) {
    return (
      <Box className={classes.container}>
        <Text className={classes.stateMessage}>
          {payload?.message ?? ROOT_CAUSE_MESSAGES.NO_DATA}
        </Text>
      </Box>
    );
  }

  if (
    payload?.everythingGood &&
    (!payload.segments || payload.segments.length === 0)
  ) {
    return (
      <Box className={classes.container}>
        <Text className={classes.stateMessage}>
          {payload?.message ?? ROOT_CAUSE_MESSAGES.EVERYTHING_GOOD}
        </Text>
      </Box>
    );
  }

  if (!payload?.segments?.length) {
    return (
      <Box className={classes.container}>
        <Text className={classes.stateMessage}>
          {ROOT_CAUSE_MESSAGES.NO_DATA}
        </Text>
      </Box>
    );
  }

  const cachedAtFormatted = payload.cachedAt
    ? dayjs(payload.cachedAt).format("MMM D, YYYY [at] h:mm A")
    : "";

  return (
    <Box className={classes.container}>
      {cachedAtFormatted && (
        <Text className={classes.cachedAt} size="sm" c="dimmed">
          Data as of {cachedAtFormatted}
        </Text>
      )}
      <Stack gap="lg">
        {payload.segments.map((segment, index) => (
          <Card
            key={`${segment.label}-${index}`}
            className={classes.segmentCard}
            withBorder
            padding="md"
          >
            <Text className={classes.segmentLabel}>{segment.label}</Text>
            <SegmentTable segment={segment} baseline={payload.baseline ?? {}} />
          </Card>
        ))}
      </Stack>
    </Box>
  );
}
