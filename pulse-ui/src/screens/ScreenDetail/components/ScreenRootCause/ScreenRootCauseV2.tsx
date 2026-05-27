import {
  ActionIcon,
  Alert,
  Badge,
  Box,
  Button,
  Card,
  Group,
  Modal,
  Skeleton,
  Stack,
  Text,
  Tooltip,
} from "@mantine/core";
import { IconAlertCircle, IconInfoCircle, IconRefresh, IconSparkles } from "@tabler/icons-react";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { LoaderWithMessage } from "../../../../components/LoaderWithMessage";
import { filtersToQueryString } from "../../../../helpers/filtersToQueryString";
import { getJobIdFromScreenV2PostResponse } from "../../../../hooks/useRegenerateScreenRcaV2Narrative/useRegenerateScreenRcaV2Narrative";
import { useGetScreenRcaV2Narrative } from "../../../../hooks/useGetScreenRcaV2Narrative";
import { useRegenerateScreenRcaV2Narrative } from "../../../../hooks/useRegenerateScreenRcaV2Narrative";

import { resolveHeatmapEvidenceUtcRange } from "../../../CriticalInteractionDetails/components/RootCause/buildRcaHeatmapEvidenceHref";
import { RcaRelatedHeatmapCard } from "../../../CriticalInteractionDetails/components/RootCause/RcaRelatedHeatmapCard";
import { RcaSessionReplayEvidenceCard } from "../../../CriticalInteractionDetails/components/RootCause/RcaSessionReplayEvidenceCard";
import { ROOT_CAUSE_MESSAGES } from "../../../CriticalInteractionDetails/components/RootCause/RootCause.constants";
import interactionRcaClasses from "../../../CriticalInteractionDetails/components/RootCause/RootCause.module.css";
import rcaClasses from "../../../CriticalInteractionDetails/components/RootCause/RcaReportView.module.css";
import classes from "./ScreenRootCause.module.css";
import {
  buildScreenRcaHeatmapFilters,
  DEFAULT_ROOT_CAUSE_LOOKBACK_DAYS,
  rcaWindowFromAnchorAndAsOf,
} from "./buildScreenRcaHeatmapEvidence";
import type { ScreenRcaProblemV2, ScreenRcaSpecificIssueV2, ScreenRcaIssueSessionEvidenceV2 } from "../../../../hooks/useGetScreenRcaV2Narrative";

dayjs.extend(utc);

const UNKNOWN = "Unknown";

function isNullishDisplayValue(value: unknown): boolean {
  if (value == null) return true;
  const str = String(value).trim();
  return str === "" || str.toLowerCase() === "null";
}

function displayText(value: string | null | undefined, fallback = UNKNOWN): string {
  if (isNullishDisplayValue(value)) return fallback;
  return String(value).trim();
}

function displayNumber(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return UNKNOWN;
  return value.toLocaleString();
}

function displayRate(rate: string | null | undefined): string {
  if (isNullishDisplayValue(rate)) return UNKNOWN;
  return String(rate).trim();
}

const REGENERATE_DEBOUNCE_MS = 500;
const NARRATIVE_NOTICE_MODAL_DELAY_MS = 2000;

const RCA_HTTP_STATUS = {
  OK: 200,
  ACCEPTED: 202,
} as const;

const PROBLEM_TYPE_LABELS: Record<string, string> = {
  crashes: "Crashes",
  anr: "ANR",
  frozen_frames: "Frozen Frames",
  frozen_frame: "Frozen Frames",
  slow_rendering: "Slow Rendering",
  slow_render: "Slow Rendering",
  slow_render_rate: "Slow Rendering",
  network_failures: "Network Failures",
  network_failure: "Network Failures",
  network_latency: "Network Latency",
  screen_load_time: "Screen Load Time",
  screen_load: "Screen Load Time",
  screen_interactive: "Screen Interactive",
  screen_interactive_time: "Screen Interactive",
  bad_clicks: "Bad Clicks",
  bad_click: "Bad Clicks",
};

const PROBLEM_TYPE_COLORS: Record<string, string> = {
  crashes: "red",
  anr: "orange",
  frozen_frames: "yellow",
  frozen_frame: "yellow",
  slow_rendering: "yellow",
  slow_render: "yellow",
  slow_render_rate: "yellow",
  network_failures: "red",
  network_failure: "red",
  network_latency: "orange",
  screen_load_time: "blue",
  screen_load: "blue",
  screen_interactive: "blue",
  screen_interactive_time: "blue",
  bad_clicks: "grape",
  bad_click: "grape",
};

// Metric info: descriptions and thresholds for tooltips
const METRIC_INFO: Record<string, { label: string; description: string; threshold?: string }> = {
  crash_rate: {
    label: "Crash Rate",
    description: "Percentage of sessions that experienced a crash",
  },
  anr_rate: {
    label: "ANR Rate",
    description: "Percentage of sessions that experienced Application Not Responding (ANR)",
  },
  frozen_frame_rate: {
    label: "Frozen Frame Rate",
    description: "Percentage of sessions which experienced frozen frames",
  },
  slow_frame_rate: {
    label: "Slow Rendering Rate",
    description: "Percentage of sessions which faced slow rendering",
  },
  network_error_rate: {
    label: "Network Error Rate",
    description: "Percentage of sessions which experienced network failures",
  },
  bad_network_latency_rate: {
    label: "Bad Network Latency",
    description: "Percentage of sessions which exceeded the 1000ms network latency threshold",
    threshold: "1000ms",
  },
  bad_screen_load_rate: {
    label: "Bad Screen Load",
    description: "Percentage of sessions which exceeded the 500ms screen load time threshold",
    threshold: "500ms",
  },
  bad_screen_interactive_rate: {
    label: "Bad Screen Interactive",
    description: "Percentage of sessions which exceeded the 7300ms time-to-interactive threshold",
    threshold: "7300ms",
  },
  bad_clicks_rate: {
    label: "Bad Clicks Rate",
    description: "Percentage of sessions which had dead or rage clicks",
  },
};

const RATE_ROW_LABELS: Record<string, string> = {
  crashes: "Crash rate",
  anr: "ANR rate",
  frozen_frames: "Frozen frame rate",
  frozen_frame: "Frozen frame rate",
  slow_rendering: "Slow rendering rate",
  slow_render: "Slow rendering rate",
  slow_render_rate: "Slow rendering rate",
  network_failures: "Network error rate",
  network_failure: "Network error rate",
  network_latency: "Bad network latency rate",
  screen_load_time: "Bad screen load rate",
  screen_load: "Bad screen load rate",
  screen_interactive: "Bad screen interactive rate",
  screen_interactive_time: "Bad screen interactive rate",
  bad_clicks: "Bad click rate",
  bad_click: "Bad click rate",
};

function getRateRowLabel(problemType: string): string {
  return RATE_ROW_LABELS[problemType] ?? "Rate";
}

function formatMetricTooltip(info: { description: string; threshold?: string }): string {
  if (info.threshold) {
    return `${info.description}\n\nThreshold: sessions exceeding ${info.threshold} are counted as affected.`;
  }
  return info.description;
}

function MetricLabelWithTooltip({ label, infoKey }: { label: string; infoKey?: string }) {
  const info = infoKey ? METRIC_INFO[infoKey] : undefined;
  return (
    <Group gap={4} align="center" wrap="nowrap">
      <span>{label}</span>
      {info ? (
        <Tooltip
          label={formatMetricTooltip(info)}
          position="top"
          withArrow
          multiline
          w={280}
          styles={{ tooltip: { whiteSpace: "pre-line" } }}
        >
          <ActionIcon
            variant="transparent"
            color="gray"
            size="xs"
            aria-label={`Info about ${label}`}
            className={classes.metricInfoIcon}
          >
            <IconInfoCircle size={14} />
          </ActionIcon>
        </Tooltip>
      ) : null}
    </Group>
  );
}

export interface ScreenRootCauseV2Props {
  screenName: string;
  projectId: string | null | undefined;
  windowEndIso: string;
}

function formatMs(ms: number | null | undefined): string {
  if (ms == null || !Number.isFinite(ms)) return UNKNOWN;
  return `${ms.toLocaleString()}ms`;
}

function parseRate(rateStr: string | null | undefined): number | null {
  if (isNullishDisplayValue(rateStr)) return null;
  const match = rateStr!.match(/([\d.]+)/);
  return match ? parseFloat(match[1]) : null;
}

function calcDelta(value: number | null | undefined, baseline: number | null | undefined): number | null {
  if (baseline == null || baseline === 0 || value == null) return null;
  return ((value - baseline) / baseline) * 100;
}

function formatSegmentMetadata(segment: string | null | undefined): string | null {
  if (!segment?.trim() || segment.trim().toLowerCase() === "overall") return null;
  return segment
    .split(" + ")
    .map((part) => {
      const colonIdx = part.indexOf(":");
      return colonIdx >= 0 ? part.slice(colonIdx + 1).trim() : part.trim();
    })
    .filter(Boolean)
    .join(" · ");
}

function issueTypeBadgeLabel(problemType: string): string {
  if (problemType === "crashes") return "CRASH";
  if (problemType === "anr") return "ANR";
  return (PROBLEM_TYPE_LABELS[problemType] ?? problemType).toUpperCase();
}

function buildIssueDescription(
  problemType: string,
  title: string,
  count: number | null | undefined,
): string {
  const occurrenceLabel =
    count != null
      ? `[${count.toLocaleString()} occurrence${count === 1 ? "" : "s"}]`
      : "";
  if (problemType === "anr") {
    const thread = title.trim();
    return thread
      ? `ANR: ${thread} ${occurrenceLabel}`.trim()
      : `Application Not Responding ${occurrenceLabel}`.trim();
  }
  return `${title} ${occurrenceLabel}`.trim();
}

const TOP_ISSUE_TYPE_LABEL_CLASS: Record<string, string> = {
  red: classes.topIssueTypeLabel_red,
  orange: classes.topIssueTypeLabel_orange,
  yellow: classes.topIssueTypeLabel_yellow,
  blue: classes.topIssueTypeLabel_blue,
  grape: classes.topIssueTypeLabel_grape,
  gray: classes.topIssueTypeLabel_gray,
};

function isAppVitalsIssueProblemType(problemType: string): boolean {
  return problemType === "crashes" || problemType === "anr";
}

function buildAppVitalsIssueHref(
  projectId: string,
  groupId: string,
  windowStartIso: string,
  windowEndIso: string,
): string {
  const heatmapFilters = buildScreenRcaHeatmapFilters(undefined, windowStartIso, windowEndIso);
  const { start, end } = resolveHeatmapEvidenceUtcRange(heatmapFilters);
  const params: Record<string, string> = { quickDateFilter: "-1" };
  if (start && end) {
    params.startDate = start;
    params.endDate = end;
  }
  const search = filtersToQueryString(params);
  const base = `/projects/${encodeURIComponent(projectId)}/app-vitals/${encodeURIComponent(groupId)}`;
  return search ? `${base}?${search}` : base;
}

function TopIssueCard({
  issue,
  projectId,
  problemType,
  segmentMetadata,
  windowStartIso,
  windowEndIso,
}: {
  issue: ScreenRcaSpecificIssueV2;
  projectId: string;
  problemType: string;
  segmentMetadata?: string | null;
  windowStartIso: string;
  windowEndIso: string;
}) {
  const title = displayText(
    issue.issue ?? issue.thread_name ?? (issue as { threadName?: string | null }).threadName,
    "Unknown issue",
  );
  const badgeLabel = issueTypeBadgeLabel(problemType);
  const badgeColor = PROBLEM_TYPE_COLORS[problemType] ?? "gray";
  const metadataLine = formatSegmentMetadata(segmentMetadata);
  const countMeta =
    issue.count != null
      ? `${issue.count.toLocaleString()} occurrence${issue.count === 1 ? "" : "s"}`
      : null;
  const description = buildIssueDescription(problemType, title, issue.count);
  const groupId = issue.group_id ?? (issue as { groupId?: string | null }).groupId;
  const cardClassName = groupId
    ? `${classes.topIssueCard} ${classes.topIssueCardInteractive}`
    : `${classes.topIssueCard} ${classes.topIssueCardStatic}`;

  const cardContent = (
    <>
      <Group
        justify="space-between"
        align="flex-start"
        wrap="nowrap"
        gap="xs"
        className={classes.topIssueHeader}
      >
        <Text
          className={`${classes.topIssueTypeLabel} ${TOP_ISSUE_TYPE_LABEL_CLASS[badgeColor] ?? classes.topIssueTypeLabel_gray}`}
          tt="uppercase"
          size="xs"
          fw={700}
        >
          {badgeLabel}
        </Text>
        {countMeta ? (
          <Text size="xs" c="dimmed" ta="right" className={classes.topIssueCountMeta}>
            {countMeta}
          </Text>
        ) : null}
      </Group>

      <Text className={classes.topIssueSubtitle} fw={700} size="sm" lineClamp={2}>
        {title}
      </Text>

      {metadataLine ? (
        <Text size="xs" c="dimmed" lineClamp={1} className={classes.topIssueMetadata}>
          {metadataLine}
        </Text>
      ) : null}

      <Text size="xs" c="gray.6" lh={1.45} lineClamp={2} className={classes.topIssueDescription}>
        {description}
      </Text>
    </>
  );

  if (groupId) {
    const href =
      isAppVitalsIssueProblemType(problemType) && windowStartIso && windowEndIso
        ? buildAppVitalsIssueHref(projectId, groupId, windowStartIso, windowEndIso)
        : `/projects/${encodeURIComponent(projectId)}/app-vitals/${encodeURIComponent(groupId)}`;
    return (
      <Link to={href} className={cardClassName}>
        {cardContent}
      </Link>
    );
  }

  return <div className={cardClassName}>{cardContent}</div>;
}

function ProblemCard({
  problem,
  projectId,
  windowStartIso,
  windowEndIso,
}: {
  problem: ScreenRcaProblemV2;
  projectId: string;
  windowStartIso: string;
  windowEndIso: string;
}) {
  const typeLabel = isNullishDisplayValue(problem.problem_type)
    ? UNKNOWN
    : (PROBLEM_TYPE_LABELS[problem.problem_type] ??
      problem.problem_type.replace(/_/g, " ").replace(/\b\w/g, (l: string) => l.toUpperCase()));
  const color = PROBLEM_TYPE_COLORS[problem.problem_type] ?? "gray";
  const metrics = problem.metrics;
  const segmentMetrics = problem.segment_metrics;
  const issues = problem.specific_issues ?? [];

  // Determine which metric rows to show based on problem type
  const isBadClicks = problem.problem_type === "bad_clicks" || problem.problem_type === "bad_click";
  const showAffectedUsers = true; // all 9 problem types expose affected_volume
  const showRate = true; // bad_clicks rate = (rage + dead) / click_volume
  const showLatency = ["network_latency", "screen_load_time", "screen_interactive", "screen_interactive_time"].includes(problem.problem_type);

  return (
    <Card padding="lg" radius="md" withBorder>
      <Stack gap="xs">
        <Group justify="space-between" align="flex-start" wrap="wrap">
          <Group gap="xs" align="center">
            <Badge size="sm" variant="filled" color="gray" circle>{problem.rank}</Badge>
            <Badge size="sm" variant="light" color={color}>{typeLabel}</Badge>
          </Group>
          {!isNullishDisplayValue(problem.most_affected_segment) ? (
            <Text size="xs" c="dimmed">
              Most affected:{" "}
              <Text span fw={500} c="dark">
                {displayText(problem.most_affected_segment)}
              </Text>
            </Text>
          ) : null}
        </Group>

        {metrics ? (
          <Box>
            <table style={{
              width: '100%',
              fontSize: '14px',
              borderCollapse: 'collapse',
              marginTop: '8px',
            }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--mantine-color-gray-2)' }}>
                  <th style={{ textAlign: 'left', padding: '8px', fontWeight: 600, fontSize: '12px', color: 'var(--mantine-color-gray-6)' }}>Metric</th>
                  <th style={{ textAlign: 'right', padding: '8px', fontWeight: 600, fontSize: '12px', color: 'var(--mantine-color-gray-6)' }}>Current segment</th>
                  <th style={{ textAlign: 'right', padding: '8px', fontWeight: 600, fontSize: '12px', color: 'var(--mantine-color-gray-6)' }}>Last 7 days</th>
                  <th style={{ textAlign: 'right', padding: '8px', fontWeight: 600, fontSize: '12px', color: 'var(--mantine-color-gray-6)' }}>
                    <Tooltip
                      label={
                        <Box>
                          <Text size="xs" fw={600} mb={4}>Delta = (segment − baseline) / baseline × 100%</Text>
                          <Text size="xs">How much the segment differs from the 7-day baseline, as a relative %.</Text>
                        </Box>
                      }
                      multiline
                      withArrow
                      position="top-end"
                      w={260}
                    >
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, cursor: 'default' }}>
                        Delta <IconInfoCircle size={12} style={{ opacity: 0.6 }} />
                      </span>
                    </Tooltip>
                  </th>
                </tr>
              </thead>
              <tbody>
                {showAffectedUsers && (() => {
                  const delta = calcDelta(segmentMetrics?.affected_volume, metrics.affected_volume);
                  const deltaColor = delta === null
                    ? 'var(--mantine-color-gray-6)'
                    : delta < 0
                      ? 'var(--mantine-color-red-6)'
                      : 'var(--mantine-color-green-6)';
                  return (
                    <tr style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}>
                      <td style={{ padding: '8px', fontSize: '13px' }}>Affected users</td>
                      <td style={{ textAlign: 'right', padding: '8px', fontWeight: 500 }}>
                        {displayNumber(segmentMetrics?.affected_volume)}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: 'var(--mantine-color-gray-6)', fontSize: '13px' }}>
                        {displayNumber(metrics.affected_volume)}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: deltaColor }}>
                        {delta == null ? UNKNOWN : `${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`}
                      </td>
                    </tr>
                  );
                })()}
                {showRate && (() => {
                  const delta = calcDelta(parseRate(segmentMetrics?.rate), parseRate(metrics.rate));
                  const deltaColor = delta === null
                    ? 'var(--mantine-color-gray-6)'
                    : delta < 0
                      ? 'var(--mantine-color-red-6)'
                      : 'var(--mantine-color-green-6)';
                  return (
                    <tr style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}>
                      <td style={{ padding: '8px', fontSize: '13px' }}>
                        <MetricLabelWithTooltip
                          label={getRateRowLabel(problem.problem_type)}
                          infoKey={problem.metric_id ?? undefined}
                        />
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', fontWeight: 500 }}>
                        {displayRate(segmentMetrics?.rate)}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: 'var(--mantine-color-gray-6)', fontSize: '13px' }}>
                        {displayRate(metrics.rate)}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: deltaColor }}>
                        {delta == null ? UNKNOWN : `${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`}
                      </td>
                    </tr>
                  );
                })()}
                {showLatency && metrics.p50_ms != null && (() => {
                  const delta = calcDelta(segmentMetrics?.p50_ms, metrics.p50_ms);
                  const deltaColor = delta === null
                    ? 'var(--mantine-color-gray-6)'
                    : delta < 0
                      ? 'var(--mantine-color-red-6)'
                      : 'var(--mantine-color-green-6)';
                  return (
                    <tr style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}>
                      <td style={{ padding: '8px', fontSize: '13px' }}>P50</td>
                      <td style={{ textAlign: 'right', padding: '8px', fontWeight: 500 }}>
                        {formatMs(segmentMetrics?.p50_ms)}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: 'var(--mantine-color-gray-6)', fontSize: '13px' }}>
                        {formatMs(metrics.p50_ms)}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: deltaColor }}>
                        {delta == null ? UNKNOWN : `${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`}
                      </td>
                    </tr>
                  );
                })()}
                {showLatency && metrics.p95_ms != null && (() => {
                  const delta = calcDelta(segmentMetrics?.p95_ms, metrics.p95_ms);
                  const deltaColor = delta === null
                    ? 'var(--mantine-color-gray-6)'
                    : delta < 0
                      ? 'var(--mantine-color-red-6)'
                      : 'var(--mantine-color-green-6)';
                  return (
                    <tr>
                      <td style={{ padding: '8px', fontSize: '13px' }}>P95</td>
                      <td style={{ textAlign: 'right', padding: '8px', fontWeight: 500 }}>
                        {formatMs(segmentMetrics?.p95_ms)}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: 'var(--mantine-color-gray-6)', fontSize: '13px' }}>
                        {formatMs(metrics.p95_ms)}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: deltaColor }}>
                        {delta == null ? UNKNOWN : `${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`}
                      </td>
                    </tr>
                  );
                })()}
                {isBadClicks && ([
                  { key: "click_volume", label: "Click volume" },
                  { key: "rage_count",   label: "Rage tap count" },
                  { key: "dead_count",   label: "Dead click count" },
                ] as const).map(({ key, label }, idx, arr) => {
                  const baseVal = typeof (metrics as Record<string, any>)[key] === 'number' ? (metrics as Record<string, any>)[key] : null;
                  const segVal  = typeof (segmentMetrics as Record<string, any>)?.[key] === 'number' ? (segmentMetrics as Record<string, any>)[key] : null;
                  const delta   = calcDelta(segVal, baseVal);
                  const isLast  = idx === arr.length - 1;
                  const deltaColor = delta === null
                    ? 'var(--mantine-color-gray-6)'
                    : delta < 0 ? 'var(--mantine-color-red-6)' : 'var(--mantine-color-green-6)';
                  return (
                    <tr key={key} style={isLast ? undefined : { borderBottom: '1px solid var(--mantine-color-gray-1)' }}>
                      <td style={{ padding: '8px', fontSize: '13px' }}>{label}</td>
                      <td style={{ textAlign: 'right', padding: '8px', fontWeight: 500 }}>
                        {displayNumber(segVal)}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: 'var(--mantine-color-gray-6)', fontSize: '13px' }}>
                        {displayNumber(baseVal)}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: deltaColor }}>
                        {delta == null ? UNKNOWN : `${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </Box>
        ) : null}

        {issues.length > 0 && (
          <Box>
            <Text size="xs" c="dimmed" tt="uppercase" fw={600} mb={4}>Top issues</Text>
            <Box className={classes.topIssuesRow}>
              {issues.map((issue: ScreenRcaSpecificIssueV2, i: number) => (
                <TopIssueCard
                  key={issue.group_id ?? `issue-${i}`}
                  issue={issue}
                  projectId={projectId}
                  problemType={problem.problem_type}
                  segmentMetadata={problem.most_affected_segment}
                  windowStartIso={windowStartIso}
                  windowEndIso={windowEndIso}
                />
              ))}
            </Box>
          </Box>
        )}
      </Stack>
    </Card>
  );
}

export function ScreenRootCauseV2({ screenName, projectId, windowEndIso }: ScreenRootCauseV2Props) {
  const trimmedProjectId = projectId != null ? String(projectId).trim() : "";
  const regenerateDebounceTimerRef = useRef<number | null>(null);
  const [rcaRequestSession, setRcaRequestSession] = useState(0);

  const { windowStartIso } = useMemo(() => {
    const endMs = Date.parse(windowEndIso);
    if (Number.isNaN(endMs)) return { windowStartIso: "" };
    const anchor = dayjs.utc(endMs).format("YYYY-MM-DD");
    return rcaWindowFromAnchorAndAsOf(
      anchor,
      windowEndIso,
      DEFAULT_ROOT_CAUSE_LOOKBACK_DAYS,
    );
  }, [windowEndIso]);

  const narrativeEnabled =
    trimmedProjectId !== "" &&
    Boolean(screenName?.trim()) &&
    windowStartIso !== "";

  const {
    data: reportResponse,
    structured,
    isFetching: reportFetching,
    isError: reportError,
    error: reportErrorDetail,
    isRcaQueuePending,
    isProcessing: isRcaProcessing,
    isUnknown: isRcaJobUnknown,
    isFailed: isRcaFailed,
    errorMessage: rcaErrorMessage,
    isJoiningExistingJob,
    retry: retryRcaJob,
    isRetrying,
    beginFollowingJob,
    staleRegenerationDetected,
    stalePollAsyncJobDetected,
    isAsyncBootstrapping,
    isAwaitingPollPayload,
    hasDisplayableCompletedReport,
  } = useGetScreenRcaV2Narrative({
    screenName,
    windowEndIso,
    windowStartIso,
    projectId,
    enabled: narrativeEnabled,
    requestSession: rcaRequestSession,
  });

  const problems = structured?.problems ?? [];
  const evidences = structured?.evidences ?? null;

  const regenerate = useRegenerateScreenRcaV2Narrative();
  const isRegenerateMutating = regenerate.isPending;
  const showReport = hasDisplayableCompletedReport;

  const showAsyncGenerationUi =
    !isRcaFailed &&
    !showReport &&
    (isAsyncBootstrapping ||
      isAwaitingPollPayload ||
      isRcaQueuePending ||
      isRcaProcessing ||
      isRegenerateMutating ||
      (reportFetching && reportResponse === undefined));

  const narrativeBusy = showAsyncGenerationUi;

  const executiveSummaryText = structured?.executive_summary?.trim() ?? "";
  const recommendationLines = (structured?.recommendations ?? []).filter(
    (l: string) => String(l).trim() !== "",
  );
  const hasExecutiveSummary = executiveSummaryText !== "";
  const hasRecommendations = recommendationLines.length > 0;

  const regenerateErrMsg =
    regenerate.error instanceof Error ? regenerate.error.message : null;

  const showNarrativeGenerationWait = narrativeEnabled && narrativeBusy && !reportError && !isRcaFailed;

  const [userDismissedNotice, setUserDismissedNotice] = useState(false);
  const [noticeDelayElapsed, setNoticeDelayElapsed] = useState(false);

  useEffect(() => {
    if (!showNarrativeGenerationWait) setUserDismissedNotice(false);
  }, [showNarrativeGenerationWait]);

  useEffect(() => {
    if (!showNarrativeGenerationWait) {
      setNoticeDelayElapsed(false);
      return;
    }
    const id = window.setTimeout(() => setNoticeDelayElapsed(true), NARRATIVE_NOTICE_MODAL_DELAY_MS);
    return () => window.clearTimeout(id);
  }, [showNarrativeGenerationWait]);

  const isNoticeModalOpen = showNarrativeGenerationWait && !userDismissedNotice && noticeDelayElapsed;

  const handleRegenerate = useCallback(() => {
    if (!screenName?.trim()) return;
    if (regenerate.isPending) return;
    if (regenerateDebounceTimerRef.current !== null) {
      window.clearTimeout(regenerateDebounceTimerRef.current);
    }
    regenerateDebounceTimerRef.current = window.setTimeout(() => {
      regenerate.mutate(
        {
          screenName: String(screenName).trim(),
          windowEndIso,
          windowStartIso,
          projectId: trimmedProjectId,
        },
        {
          onSuccess: (res) => {
            if (res.status === RCA_HTTP_STATUS.ACCEPTED) {
              const jobId = getJobIdFromScreenV2PostResponse(res);
              if (jobId) {
                beginFollowingJob(jobId);
              }
              return;
            }
            if (res.status === RCA_HTTP_STATUS.OK) {
              setRcaRequestSession((s) => s + 1);
            }
          },
        },
      );
      regenerateDebounceTimerRef.current = null;
    }, REGENERATE_DEBOUNCE_MS);
  }, [
    screenName,
    windowEndIso,
    windowStartIso,
    trimmedProjectId,
    regenerate,
    beginFollowingJob,
  ]);

  useEffect(() => {
    return () => {
      if (regenerateDebounceTimerRef.current !== null) {
        window.clearTimeout(regenerateDebounceTimerRef.current);
      }
    };
  }, []);

  if (isRcaJobUnknown) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Alert color="red" icon={<IconAlertCircle size={16} />} title="Unexpected response">
          <Text size="sm" mb="sm">
            {ROOT_CAUSE_MESSAGES.RCA_UNKNOWN_JOB_STATUS}
          </Text>
          <Button
            leftSection={<IconRefresh size={14} />}
            variant="subtle"
            color="red"
            size="xs"
            pl={0}
            onClick={() => {
              void retryRcaJob();
            }}
          >
            {ROOT_CAUSE_MESSAGES.RCA_STALE_REFRESH}
          </Button>
        </Alert>
      </Box>
    );
  }

  if (isRcaFailed) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Alert color="red" icon={<IconAlertCircle size={16} />} title="Report generation failed">
          <Text size="sm" mb="sm">
            {rcaErrorMessage?.trim()
              ? rcaErrorMessage
              : ROOT_CAUSE_MESSAGES.GENERIC_ERROR}
          </Text>
          <Button
            leftSection={<IconRefresh size={14} />}
            variant="subtle"
            color="red"
            size="xs"
            pl={0}
            loading={isRetrying}
            onClick={() => {
              void retryRcaJob();
            }}
          >
            Retry
          </Button>
        </Alert>
      </Box>
    );
  }

  if (showAsyncGenerationUi) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Stack align="center" gap="md" className={interactionRcaClasses.stateMessage}>
          {isJoiningExistingJob ? (
            <Alert color="blue" variant="light" maw={520} w="100%">
              {ROOT_CAUSE_MESSAGES.RCA_JOINING_JOB}
            </Alert>
          ) : null}
          <LoaderWithMessage loadingMessage={ROOT_CAUSE_MESSAGES.RCA_WAITING_IN_QUEUE} />
        </Stack>
      </Box>
    );
  }

  if (reportError) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Alert color="red" icon={<IconAlertCircle size={16} />} title="Failed to load">
          {reportErrorDetail instanceof Error
            ? reportErrorDetail.message
            : "An error occurred loading Screen RCA."}
        </Alert>
      </Box>
    );
  }

  if (showReport && problems.length === 0) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Text className={interactionRcaClasses.stateMessage}>
          No issues detected for this screen in the selected period.
        </Text>
      </Box>
    );
  }

  const issueSessions = (evidences?.issue_sessions ?? []).filter(
    (s: ScreenRcaIssueSessionEvidenceV2) => s.session_id?.trim(),
  );
  const heatmapAvailable = evidences?.heatmap_available === true;
  const heatmapDate =
    evidences?.heatmap_date?.trim() ||
    (() => {
      const endMs = Date.parse(windowEndIso);
      return Number.isNaN(endMs) ? "" : dayjs.utc(endMs).format("YYYY-MM-DD");
    })();
  const heatmapWindowStartIso = heatmapDate
    ? dayjs.utc(heatmapDate, "YYYY-MM-DD").startOf("day").toISOString()
    : windowStartIso;
  const heatmapWindowEndIso = heatmapDate
    ? dayjs.utc(heatmapDate, "YYYY-MM-DD").add(1, "day").startOf("day").toISOString()
    : windowEndIso;
  const rank1SegmentFilters = issueSessions.find((s) => s.rank === 1)?.segment_filters ?? null;
  const evidenceCount = issueSessions.length + (heatmapAvailable ? 1 : 0);
  const showEvidenceStrip = evidenceCount > 0;

  return (
    <>
      <Modal
        opened={isNoticeModalOpen}
        onClose={() => setUserDismissedNotice(true)}
        title="Generating AI insights"
        centered
      >
        <Stack gap="md">
          <Text size="sm">{ROOT_CAUSE_MESSAGES.RCA_WAITING_IN_QUEUE}</Text>
          <Button variant="light" onClick={() => setUserDismissedNotice(true)}>OK</Button>
        </Stack>
      </Modal>

      <Box className={interactionRcaClasses.container}>
        <Stack gap="lg">
          <Group justify="flex-end" gap="xs">
            <Button
              variant="light"
              size="xs"
              leftSection={<IconRefresh size={14} />}
              disabled={narrativeBusy}
              onClick={handleRegenerate}
            >
              {ROOT_CAUSE_MESSAGES.REGENERATE_REPORT}
            </Button>
          </Group>

          {staleRegenerationDetected ? (
            <Alert color="yellow" variant="light" title={ROOT_CAUSE_MESSAGES.RCA_STALE_REPORT_BANNER}>
              <Stack gap="sm" align="flex-start">
                <Text size="sm">{ROOT_CAUSE_MESSAGES.RCA_STALE_REPORT_BANNER}</Text>
                <Button
                  size="xs"
                  variant="light"
                  onClick={() => {
                    void retryRcaJob();
                  }}
                >
                  {ROOT_CAUSE_MESSAGES.RCA_STALE_REFRESH}
                </Button>
              </Stack>
            </Alert>
          ) : null}
          {stalePollAsyncJobDetected ? (
            <Alert color="blue" variant="light">
              <Stack gap="sm" align="flex-start">
                <Text size="sm">{ROOT_CAUSE_MESSAGES.RCA_STALE_ASYNC_ACTIVITY}</Text>
                <Button
                  size="xs"
                  variant="light"
                  onClick={() => {
                    void retryRcaJob();
                  }}
                >
                  {ROOT_CAUSE_MESSAGES.RCA_STALE_REFRESH}
                </Button>
              </Stack>
            </Alert>
          ) : null}

          {/* Narrative loading skeleton */}
          {narrativeBusy && (
            <Stack gap="sm">
              <Skeleton height={100} radius="md" />
              <Skeleton height={120} radius="md" />
            </Stack>
          )}

          {/* Narrative error */}
          {!narrativeBusy && regenerate.isError && (
            <Alert color="orange" title="AI insights unavailable">
              {regenerateErrMsg ?? ROOT_CAUSE_MESSAGES.GENERIC_ERROR}
            </Alert>
          )}

          {/* Executive summary */}
          {!narrativeBusy && !regenerate.isError && hasExecutiveSummary && (
            <Card padding="lg" radius="md" withBorder className={rcaClasses.executiveSummaryCard}>
              <div className={rcaClasses.executiveSummaryTitleRow}>
                <IconSparkles size={18} color="var(--mantine-color-violet-6)" />
                <Text fw={700} size="sm" c="violet.7">Executive summary</Text>
              </div>
              <Text className={rcaClasses.executiveSummaryBody} size="sm" lh={1.65}>
                {executiveSummaryText}
              </Text>
            </Card>
          )}

          {/* Ranked problems */}
          <Stack gap="md">
            <Group gap="xs" align="center">
              <Text fw={700} size="md" tt="uppercase" c="gray.7">Issues</Text>
              <Badge size="sm" variant="light" color="gray">{problems.length}</Badge>
            </Group>
            {problems.map((p: ScreenRcaProblemV2) => (
              <ProblemCard
                key={p.problem_type}
                problem={p}
                projectId={trimmedProjectId}
                windowStartIso={windowStartIso}
                windowEndIso={windowEndIso}
              />
            ))}
          </Stack>

          {/* Evidence cards — same pattern as interaction RCA */}
          {showEvidenceStrip ? (
            <Box className={rcaClasses.evidenceSection}>
              <div className={rcaClasses.evidenceSectionTitleRow}>
                <Text
                  className={rcaClasses.evidenceTitle}
                  fw={700}
                  size="sm"
                  tt="uppercase"
                >
                  Evidence
                </Text>
                <Badge
                  size="sm"
                  variant="light"
                  color="teal"
                  circle
                  className={rcaClasses.evidenceCountBadge}
                >
                  {evidenceCount}
                </Badge>
              </div>
              <Box className={rcaClasses.evidenceCardRow}>
                {issueSessions.map((ev: ScreenRcaIssueSessionEvidenceV2, sessionIdx: number) => {
                  const segmentLabel =
                    formatSegmentMetadata(ev.segment) ?? displayText(ev.segment, screenName);
                  return (
                  <Box
                    key={`${ev.session_id}-${sessionIdx}`}
                    className={rcaClasses.evidenceCardSlot}
                  >
                    <RcaSessionReplayEvidenceCard
                      sessionId={ev.session_id!}
                      segmentTitle={segmentLabel}
                      projectId={trimmedProjectId}
                      evidenceOrdinal={sessionIdx + 1}
                      evidenceSessionCount={issueSessions.length}
                    />
                  </Box>
                  );
                })}
                {heatmapAvailable && heatmapWindowStartIso ? (
                  <RcaRelatedHeatmapCard
                    projectId={trimmedProjectId}
                    screenName={screenName}
                    segmentTitle={`${screenName} — ${heatmapDate}`}
                    heatmapFilters={buildScreenRcaHeatmapFilters(
                      rank1SegmentFilters ?? undefined,
                      heatmapWindowStartIso,
                      heatmapWindowEndIso,
                    )}
                  />
                ) : null}
              </Box>
            </Box>
          ) : null}

          {/* Recommendations */}
          {!narrativeBusy && !regenerate.isError && hasRecommendations && (
            <Card padding="lg" radius="md" withBorder className={rcaClasses.recommendationsCard}>
              <Text className={rcaClasses.recommendationsTitle} fw={700} size="sm" c="teal.8">
                Recommendations
              </Text>
              <ul className={rcaClasses.recommendationsList}>
                {recommendationLines.map((item: string, i: number) => (
                  <li key={`rec-${i}`}>
                    <Text size="sm" lh={1.65}>{item}</Text>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </Stack>
      </Box>
    </>
  );
}
