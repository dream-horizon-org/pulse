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
  ErrorAttributionDrillDownPayload,
  ErrorAttributionSignal,
} from "../../../../hooks/useGetErrorAttribution";
import { encodeNetworkId } from "../../../NetworkList/utils/networkIdUtils";
import {
  DEFAULT_MIN_POOR_SESSIONS_ERROR_ATTRIBUTION,
  EN_DASH,
  ERROR_ATTRIBUTION_MESSAGES,
  insufficientPoorSessionsMessage,
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
): string {
  if (rrUndefined === true || rr == null || Number.isNaN(rr)) return EN_DASH;
  return rr.toFixed(2);
}

function formatCachedAt(iso: string | null | undefined): string | null {
  if (iso == null || String(iso).trim() === "") return null;
  const parsed = dayjs(iso);
  return parsed.isValid() ? parsed.format("MMM D, YYYY [at] h:mm A") : null;
}

function SignalIssueList({
  signal,
  payload,
  projectId,
  linkSuffix,
}: {
  signal: ErrorAttributionSignal;
  payload: ErrorAttributionDrillDownPayload | undefined;
  projectId: string;
  linkSuffix: string;
}) {
  if (signal === "api") {
    const endpoints = payload?.networkEndpoints ?? [];
    if (endpoints.length === 0) {
      return (
        <Text size="sm" c="dimmed">
          {ERROR_ATTRIBUTION_MESSAGES.DRILL_DOWN_EMPTY}
        </Text>
      );
    }
    return (
      <Stack gap="xs">
        {endpoints.map((ep, idx) => {
          const apiId = encodeNetworkId(
            ep.url,
            ep.graphqlOperationName ?? undefined,
            ep.graphqlOperationType ?? undefined,
          );
          const to = `/projects/${encodeURIComponent(projectId)}/network-apis/${encodeURIComponent(apiId)}${linkSuffix}`;
          return (
            <Stack key={`${ep.url}-${idx}`} gap={4}>
              <Group justify="space-between" wrap="nowrap" gap="md">
                <Text
                  component={Link}
                  to={to}
                  size="sm"
                  className={classes.drillDownLink}
                  lineClamp={2}
                >
                  {ep.url || "(no URL)"}
                </Text>
                <Text size="sm" c="dimmed" style={{ flexShrink: 0 }}>
                  {ep.occurrences.toLocaleString()}{" "}
                  {ERROR_ATTRIBUTION_MESSAGES.DRILL_DOWN_SESSIONS}
                </Text>
              </Group>
              <Text size="xs" c="dimmed">
                Poor rate (with endpoint): {formatPoorRate(ep.p1)} · Poor rate
                (without): {formatPoorRate(ep.p2)} · RR:{" "}
                {formatRiskRatio(ep.rr, ep.rrUndefined ?? null)}
              </Text>
            </Stack>
          );
        })}
      </Stack>
    );
  }

  const issues = payload?.issues ?? [];
  if (issues.length === 0) {
    return (
      <Text size="sm" c="dimmed">
        {ERROR_ATTRIBUTION_MESSAGES.DRILL_DOWN_EMPTY}
      </Text>
    );
  }

  return (
    <Stack gap="xs">
      {issues.map((issue) => {
        const to = `/projects/${encodeURIComponent(projectId)}/app-vitals/${encodeURIComponent(issue.groupId)}${linkSuffix}`;
        const label =
          issue.title && issue.title.trim() !== ""
            ? issue.title
            : issue.groupId || "(issue)";
        const typeSuffix =
          signal === "non_fatal" && issue.exceptionType
            ? ` (${issue.exceptionType})`
            : "";
        return (
          <Stack key={`${issue.groupId}-${issue.exceptionType ?? ""}`} gap={4}>
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
                {issue.occurrences.toLocaleString()}{" "}
                {ERROR_ATTRIBUTION_MESSAGES.DRILL_DOWN_SESSIONS}
              </Text>
            </Group>
            <Text size="xs" c="dimmed">
              Poor rate (with issue): {formatPoorRate(issue.p1)} · Poor rate
              (without): {formatPoorRate(issue.p2)} · RR:{" "}
              {formatRiskRatio(issue.rr, issue.rrUndefined ?? null)}
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
                : insufficientPoorSessionsMessage(
                    body.minPoorSessionsForErrorAttribution ??
                      DEFAULT_MIN_POOR_SESSIONS_ERROR_ATTRIBUTION,
                  )
            }
            classes={[rootCauseClasses.stateMessage]}
          />
          {disclaimerBlock}
        </Stack>
      </Box>
    );
  }

  const drill = body.drillDown ?? {};

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

      <Stack gap="lg" mt="md">
        {ALL_DRILL_SIGNALS.map((signal) => (
          <Box key={signal} className={classes.drillDownRow} p="md">
            <Text fw={600} size="sm" mb="sm">
              {SIGNAL_LABEL[signal] ?? signal}
            </Text>
            <SignalIssueList
              signal={signal}
              payload={drill[signal]}
              projectId={trimmedProjectId}
              linkSuffix={linkSuffix}
            />
          </Box>
        ))}
      </Stack>

      {disclaimerBlock}
    </Box>
  );
}
