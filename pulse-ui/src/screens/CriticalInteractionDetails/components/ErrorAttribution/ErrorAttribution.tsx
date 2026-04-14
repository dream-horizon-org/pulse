import {
  Badge,
  Box,
  Button,
  Group,
  Skeleton,
  Stack,
  Table,
  Text,
  Title,
} from "@mantine/core";
import { IconRefresh } from "@tabler/icons-react";
import dayjs from "dayjs";
import { useMemo } from "react";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import {
  getErrorAttributionWindowIso,
  useGetErrorAttribution,
  useRefreshErrorAttribution,
} from "../../../../hooks/useGetErrorAttribution";
import type { ErrorAttributionRiskRatioEntry } from "../../../../hooks/useGetErrorAttribution";
import {
  EN_DASH,
  ERROR_ATTRIBUTION_MESSAGES,
} from "./ErrorAttribution.constants";
import type { ErrorAttributionProps } from "./ErrorAttribution.interface";
import classes from "./ErrorAttribution.module.css";
import rootCauseClasses from "../RootCause/RootCause.module.css";

const SIGNAL_LABEL: Record<string, string> = {
  crash: "Crash",
  anr: "ANR",
  non_fatal: "Non-fatal",
  api: "API failure",
};

function formatProbability(value: number): string {
  return value.toFixed(4);
}

function UndefinedDash() {
  return (
    <Text component="span" className={classes.undefinedValue}>
      {EN_DASH}
    </Text>
  );
}

function formatP(value: number | null | undefined) {
  if (value == null || Number.isNaN(value)) return <UndefinedDash />;
  return formatProbability(value);
}

function formatRiskRatioCell(row: ErrorAttributionRiskRatioEntry) {
  if (row.rrUndefinedReason === "INFINITE_RR") {
    return "∞";
  }
  if (
    row.rrUndefinedReason === "EMPTY_TREATED_ARM" ||
    row.rrUndefinedReason === "EMPTY_CONTROL_ARM" ||
    row.rrUndefinedReason === "ZERO_POOR"
  ) {
    return <UndefinedDash />;
  }
  if (typeof row.rr === "number" && Number.isFinite(row.rr)) {
    return formatProbability(row.rr);
  }
  return <UndefinedDash />;
}

function formatCachedAt(iso: string | null | undefined): string | null {
  if (iso == null || String(iso).trim() === "") return null;
  const parsed = dayjs(iso);
  return parsed.isValid() ? parsed.format("MMM D, YYYY [at] h:mm A") : null;
}

export function ErrorAttribution({
  interactionName,
  date,
  projectId,
}: ErrorAttributionProps) {
  const trimmedProjectId = projectId != null ? String(projectId).trim() : "";

  const { start, end } = useMemo(
    () => getErrorAttributionWindowIso(date ?? null),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reset window `end` when RCA scope (project / interaction / date) changes
    [date, interactionName, trimmedProjectId],
  );

  const {
    data: apiResponse,
    isLoading,
    isFetching,
    isError,
  } = useGetErrorAttribution({
    interactionName,
    start,
    end,
    projectId: trimmedProjectId || null,
    enabled: trimmedProjectId !== "" && !!interactionName,
  });

  const refreshAttribution = useRefreshErrorAttribution();

  const httpOk = apiResponse?.status === 200 && apiResponse.data != null;
  const body = httpOk ? apiResponse.data : null;

  const showLoading = (isLoading || isFetching) && !httpOk;

  const disclaimerBlock =
    body?.disclaimer != null && String(body.disclaimer).trim() !== "" ? (
      <Text className={classes.disclaimer} size="sm">
        {body.disclaimer}
      </Text>
    ) : null;

  const cachedAtLabel = formatCachedAt(body?.cachedAt);

  const refreshButton = (
    <Button
      variant="light"
      size="xs"
      leftSection={<IconRefresh size={14} />}
      loading={refreshAttribution.isPending}
      onClick={() => {
        refreshAttribution.mutate({
          interactionName,
          start,
          end,
          projectId: trimmedProjectId,
        });
      }}
    >
      {ERROR_ATTRIBUTION_MESSAGES.REFRESH}
    </Button>
  );

  if (showLoading) {
    return (
      <Box className={classes.section}>
        <Group
          justify="space-between"
          align="center"
          wrap="wrap"
          gap="sm"
          className={classes.headerRow}
        >
          <Title order={4}>{ERROR_ATTRIBUTION_MESSAGES.SECTION_TITLE}</Title>
          {refreshButton}
        </Group>
        <div className={classes.skeletonBlock}>
          <Skeleton height={24} width="60%" mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} />
        </div>
      </Box>
    );
  }

  if (isError || (apiResponse != null && !httpOk)) {
    return (
      <Box className={classes.section}>
        <Group
          justify="space-between"
          align="center"
          wrap="wrap"
          gap="sm"
          className={classes.headerRow}
        >
          <Title order={4}>{ERROR_ATTRIBUTION_MESSAGES.SECTION_TITLE}</Title>
          {refreshButton}
        </Group>
        <Stack
          align="center"
          gap="md"
          className={rootCauseClasses.stateMessage}
        >
          <ErrorAndEmptyState
            message={ERROR_ATTRIBUTION_MESSAGES.GENERIC_ERROR}
            classes={[rootCauseClasses.errorState]}
          />
        </Stack>
      </Box>
    );
  }

  if (!body) {
    return null;
  }

  const winners = new Set(body.jointWinners ?? []);

  const insufficient = body.trackBInsufficientData === true;
  const emptyUniverse = insufficient && body.nU === 0;

  if (insufficient) {
    return (
      <Box className={classes.section}>
        <Group
          justify="space-between"
          align="flex-start"
          wrap="wrap"
          gap="sm"
          className={classes.headerRow}
        >
          <div>
            <Title order={4}>{ERROR_ATTRIBUTION_MESSAGES.SECTION_TITLE}</Title>
            {cachedAtLabel ? (
              <Text className={classes.cachedAt} size="xs">
                Cached {cachedAtLabel}
              </Text>
            ) : null}
          </div>
          {refreshButton}
        </Group>
        <Stack gap="md" className={classes.emptyState}>
          <ErrorAndEmptyState
            message={
              emptyUniverse
                ? ERROR_ATTRIBUTION_MESSAGES.NO_DATA_IN_WINDOW
                : ERROR_ATTRIBUTION_MESSAGES.INSUFFICIENT_POOR
            }
            classes={[rootCauseClasses.stateMessage]}
          />
          {disclaimerBlock}
        </Stack>
      </Box>
    );
  }

  return (
    <Box className={classes.section}>
      <Group
        justify="space-between"
        align="flex-start"
        wrap="wrap"
        gap="sm"
        className={classes.headerRow}
      >
        <div>
          <Title order={4}>{ERROR_ATTRIBUTION_MESSAGES.SECTION_TITLE}</Title>
          {cachedAtLabel ? (
            <Text className={classes.cachedAt} size="xs">
              Cached {cachedAtLabel}
            </Text>
          ) : null}
        </div>
        {refreshButton}
      </Group>

      <Table striped highlightOnHover withTableBorder withColumnBorders>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Signal</Table.Th>
            <Table.Th style={{ textAlign: "right" }}>Exposed p1</Table.Th>
            <Table.Th style={{ textAlign: "right" }}>Unexposed p2</Table.Th>
            <Table.Th style={{ textAlign: "right" }}>Risk ratio</Table.Th>
            <Table.Th style={{ textAlign: "center" }}>Winner</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {(body.riskRatios ?? []).map((row) => (
            <Table.Tr key={row.signal}>
              <Table.Td>
                <Text size="sm">{SIGNAL_LABEL[row.signal] ?? row.signal}</Text>
              </Table.Td>
              <Table.Td style={{ textAlign: "right" }}>
                <Text size="sm">{formatP(row.p1)}</Text>
              </Table.Td>
              <Table.Td style={{ textAlign: "right" }}>
                <Text size="sm">{formatP(row.p2)}</Text>
              </Table.Td>
              <Table.Td style={{ textAlign: "right" }}>
                <Text size="sm" fw={500}>
                  {formatRiskRatioCell(row)}
                </Text>
              </Table.Td>
              <Table.Td style={{ textAlign: "center" }}>
                {winners.has(row.signal) ? (
                  <Badge size="sm" variant="filled">
                    Winner
                  </Badge>
                ) : null}
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>

      {disclaimerBlock}
    </Box>
  );
}
