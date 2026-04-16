import {
  Box,
  Button,
  Group,
  Skeleton,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { IconRefresh } from "@tabler/icons-react";
import dayjs from "dayjs";
import { useMemo } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import {
  getErrorAttributionWindowIso,
  useGetErrorAttribution,
  useRefreshErrorAttribution,
} from "../../../../hooks/useGetErrorAttribution";
import type {
  ErrorAttributionSignal,
  RelatedAttributionEntry,
} from "../../../../hooks/useGetErrorAttribution";
import { encodeNetworkId } from "../../../NetworkList/utils/networkIdUtils";
import {
  EN_DASH,
  ERROR_ATTRIBUTION_MESSAGES,
  relatedAttributionsEmptyMessage,
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

const ALL_DRILL_SIGNALS: ErrorAttributionSignal[] = [
  "crash",
  "anr",
  "non_fatal",
  "api",
];

function formatPoorRate(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return EN_DASH;
  return `${(value * 100).toFixed(1)}%`;
}

function formatRiskRatio(
  rr: number | null | undefined,
  rrUndefined: boolean | null | undefined,
  rrUndefinedReason?: string | null,
): string {
  if (rrUndefinedReason === "INFINITE_RR") return "∞";
  if (rrUndefined === true || rr == null || Number.isNaN(rr)) return EN_DASH;
  return rr.toFixed(2);
}

function formatCachedAt(iso: string | null | undefined): string | null {
  if (iso == null || String(iso).trim() === "") return null;
  const parsed = dayjs(iso);
  return parsed.isValid() ? parsed.format("MMM D, YYYY [at] h:mm A") : null;
}

function UnifiedRelatedAttributionsList({
  rows,
  projectId,
  linkSuffix,
}: {
  rows: RelatedAttributionEntry[];
  projectId: string;
  linkSuffix: string;
}) {
  return (
    <Stack gap="xs">
      {rows.map((row, idx) => {
        const signalLabel = SIGNAL_LABEL[row.sourceSignal] ?? row.sourceSignal;
        if (row.rowKind === "api") {
          const apiId = encodeNetworkId(
            row.url ?? "",
            row.graphqlOperationName ?? undefined,
            row.graphqlOperationType ?? undefined,
          );
          const to = `/projects/${encodeURIComponent(projectId)}/network-apis/${encodeURIComponent(apiId)}${linkSuffix}`;
          return (
            <Stack key={`api-${row.url}-${idx}`} gap={4}>
              <Group justify="space-between" wrap="nowrap" gap="md">
                <Text
                  component={Link}
                  to={to}
                  size="sm"
                  className={classes.drillDownLink}
                  lineClamp={2}
                >
                  {row.url || "(no URL)"}
                </Text>
                <Text size="sm" c="dimmed" style={{ flexShrink: 0 }}>
                  {row.occurrences.toLocaleString()}{" "}
                  {ERROR_ATTRIBUTION_MESSAGES.DRILL_DOWN_SESSIONS}
                </Text>
              </Group>
              <Text size="xs" c="dimmed">
                {signalLabel} · Poor rate (with endpoint):{" "}
                {formatPoorRate(row.p1)} · Poor rate (without):{" "}
                {formatPoorRate(row.p2)} · RR:{" "}
                {formatRiskRatio(
                  row.rr,
                  row.rrUndefined ?? null,
                  row.rrUndefinedReason,
                )}
              </Text>
            </Stack>
          );
        }

        const to = `/projects/${encodeURIComponent(projectId)}/app-vitals/${encodeURIComponent(row.groupId ?? "")}${linkSuffix}`;
        const label =
          row.title && row.title.trim() !== ""
            ? row.title
            : row.groupId || "(issue)";
        const typeSuffix =
          row.sourceSignal === "non_fatal" && row.exceptionType
            ? ` (${row.exceptionType})`
            : "";
        return (
          <Stack
            key={`issue-${row.groupId}-${row.exceptionType ?? ""}-${idx}`}
            gap={4}
          >
            <Group justify="space-between" wrap="nowrap" gap="md">
              <Text
                component={Link}
                to={to}
                size="sm"
                className={classes.drillDownLink}
                lineClamp={2}
              >
                {label}
                {typeSuffix}
              </Text>
              <Text size="sm" c="dimmed" style={{ flexShrink: 0 }}>
                {row.occurrences.toLocaleString()}{" "}
                {ERROR_ATTRIBUTION_MESSAGES.DRILL_DOWN_SESSIONS}
              </Text>
            </Group>
            <Text size="xs" c="dimmed">
              {signalLabel} · Poor rate (with issue): {formatPoorRate(row.p1)} ·
              Poor rate (without): {formatPoorRate(row.p2)} · RR:{" "}
              {formatRiskRatio(
                row.rr,
                row.rrUndefined ?? null,
                row.rrUndefinedReason,
              )}
            </Text>
          </Stack>
        );
      })}
    </Stack>
  );
}

export function ErrorAttribution({
  interactionName,
  date,
  projectId,
}: ErrorAttributionProps) {
  const trimmedProjectId = projectId != null ? String(projectId).trim() : "";
  const [searchParams] = useSearchParams();
  const linkSuffix = useMemo(() => {
    const qs = searchParams.toString();
    return qs ? `?${qs}` : "";
  }, [searchParams]);

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
    drillDownSignals: ALL_DRILL_SIGNALS,
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
          drillDownSignals: ALL_DRILL_SIGNALS,
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

  const related = body.relatedAttributions ?? [];

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

      <Stack gap="md" mt="md" className={classes.drillDownRow} p="md">
        {related.length === 0 ? (
          <Text size="sm" c="dimmed">
            {relatedAttributionsEmptyMessage(
              body.minRiskRatioForIssueAttribution,
            )}
          </Text>
        ) : (
          <UnifiedRelatedAttributionsList
            rows={related}
            projectId={trimmedProjectId}
            linkSuffix={linkSuffix}
          />
        )}
      </Stack>

      {disclaimerBlock}
    </Box>
  );
}
