import {
  Alert,
  Badge,
  Box,
  Button,
  Card,
  Divider,
  Group,
  Modal,
  Popover,
  Skeleton,
  Stack,
  Text,
  ThemeIcon,
  Tooltip,
} from "@mantine/core";
import { IconAlertCircle, IconInfoCircle, IconRefresh, IconSparkles } from "@tabler/icons-react";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useGetScreenRcaV2Narrative } from "../../../../hooks/useGetScreenRcaV2Narrative";
import { useRegenerateScreenRcaV2Narrative } from "../../../../hooks/useRegenerateScreenRcaV2Narrative";

import { RcaRelatedHeatmapCard } from "../../../CriticalInteractionDetails/components/RootCause/RcaRelatedHeatmapCard";
import { RcaSessionReplayEvidenceCard } from "../../../CriticalInteractionDetails/components/RootCause/RcaSessionReplayEvidenceCard";
import { ROOT_CAUSE_MESSAGES } from "../../../CriticalInteractionDetails/components/RootCause/RootCause.constants";
import interactionRcaClasses from "../../../CriticalInteractionDetails/components/RootCause/RootCause.module.css";
import rcaClasses from "../../../CriticalInteractionDetails/components/RootCause/RcaReportView.module.css";
import classes from "./ScreenRootCause.module.css";
import {
  buildScreenRcaHeatmapFilters,
  DEFAULT_ROOT_CAUSE_LOOKBACK_DAYS,
  fromDateFromStartInclusiveUtc,
  rcaWindowFromAnchorAndAsOf,
} from "./buildScreenRcaHeatmapEvidence";
import type { ScreenRcaProblemV2, ScreenRcaSpecificIssueV2 } from "../../../../hooks/useGetScreenRcaV2Narrative";

dayjs.extend(utc);

const REGENERATE_DEBOUNCE_MS = 500;
const NARRATIVE_NOTICE_MODAL_DELAY_MS = 2000;

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
    description: "Percentage of sessions with frozen frames detected",
  },
  slow_frame_rate: {
    label: "Slow Rendering Rate",
    description: "Percentage of sessions with slow render rate",
  },
  network_error_rate: {
    label: "Network Error Rate",
    description: "Percentage of sessions with network failures",
  },
  bad_network_latency_rate: {
    label: "Bad Network Latency",
    description: "Percentage of sessions exceeding 1000ms network latency threshold",
    threshold: "1000ms",
  },
  bad_screen_load_rate: {
    label: "Bad Screen Load",
    description: "Percentage of sessions exceeding 500ms screen load time threshold",
    threshold: "500ms",
  },
  bad_screen_interactive_rate: {
    label: "Bad Screen Interactive",
    description: "Percentage of sessions exceeding 7300ms time-to-interactive threshold",
    threshold: "7300ms",
  },
  bad_clicks_rate: {
    label: "Bad Clicks Rate",
    description: "Percentage of sessions with dead or rage clicks",
  },
};

export interface ScreenRootCauseV2Props {
  screenName: string;
  projectId: string | null | undefined;
  windowEndIso: string;
}

function formatMs(ms: number | null | undefined): string {
  if (ms == null) return "—";
  return `${ms.toLocaleString()}ms`;
}

function parseRate(rateStr: string | null | undefined): number | null {
  if (!rateStr) return null;
  const match = rateStr.match(/([\d.]+)/);
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

function TopIssueCard({
  issue,
  projectId,
  problemType,
  segmentMetadata,
}: {
  issue: ScreenRcaSpecificIssueV2;
  projectId: string;
  problemType: string;
  segmentMetadata?: string | null;
}) {
  const title = issue.issue ?? issue.thread_name ?? "Unknown issue";
  const badgeLabel = issueTypeBadgeLabel(problemType);
  const badgeColor = PROBLEM_TYPE_COLORS[problemType] ?? "gray";
  const metadataLine = formatSegmentMetadata(segmentMetadata);
  const countMeta =
    issue.count != null
      ? `${issue.count.toLocaleString()} occurrence${issue.count === 1 ? "" : "s"}`
      : null;
  const description = buildIssueDescription(problemType, title, issue.count);
  const cardClassName = issue.group_id
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

  if (issue.group_id) {
    return (
      <Link
        to={`/projects/${encodeURIComponent(projectId)}/app-vitals/${encodeURIComponent(issue.group_id)}`}
        className={cardClassName}
      >
        {cardContent}
      </Link>
    );
  }

  return <div className={cardClassName}>{cardContent}</div>;
}

function MetricInfoPopover({ problems }: { problems: ScreenRcaProblemV2[] }) {
  const [opened, setOpened] = useState(false);

  // Only show entries for problems actually present in this report
  const entries = problems
    .filter((p) => p.metric_id && METRIC_INFO[p.metric_id])
    .map((p) => ({
      rank: p.rank,
      typeLabel: PROBLEM_TYPE_LABELS[p.problem_type] ?? p.problem_type,
      info: METRIC_INFO[p.metric_id!]!,
    }));

  if (entries.length === 0) return null;

  return (
    <Popover
      opened={opened}
      onChange={setOpened}
      width={320}
      position="bottom-end"
      withArrow
      shadow="md"
      withinPortal
    >
      <Popover.Target>
        <ThemeIcon
          variant="light"
          color="gray"
          size="sm"
          radius="xl"
          style={{ cursor: "pointer" }}
          onClick={() => setOpened((o) => !o)}
          title="How issues are calculated"
        >
          <IconInfoCircle size={14} />
        </ThemeIcon>
      </Popover.Target>
      <Popover.Dropdown>
        <Stack gap="xs">
          <Text fw={600} size="sm">How issues are calculated</Text>
          <Divider />
          {entries.map((entry, i) => (
            <Box key={entry.rank}>
              {i > 0 && <Divider my={4} />}
              <Group gap={6} mb={2}>
                <Badge size="xs" variant="light" color="gray">#{entry.rank}</Badge>
                <Text size="xs" fw={600}>{entry.typeLabel}</Text>
                <Text size="xs" c="dimmed">· {entry.info.label}</Text>
              </Group>
              <Text size="xs" c="dimmed" lh={1.5}>{entry.info.description}</Text>
              {entry.info.threshold && (
                <Text size="xs" c="blue.6" mt={2}>
                  Threshold: sessions exceeding <strong>{entry.info.threshold}</strong> are counted as affected
                </Text>
              )}
            </Box>
          ))}
        </Stack>
      </Popover.Dropdown>
    </Popover>
  );
}

function ProblemCard({ problem, projectId }: { problem: ScreenRcaProblemV2; projectId: string }) {
  const typeLabel = PROBLEM_TYPE_LABELS[problem.problem_type] ??
    problem.problem_type?.replace(/_/g, ' ').replace(/\b\w/g, (l: string) => l.toUpperCase()) ??
    'Unknown';
  const color = PROBLEM_TYPE_COLORS[problem.problem_type] ?? "gray";
  const metrics = problem.metrics;
  const segmentMetrics = problem.segment_metrics;
  // NOTE: segment_metrics is populated by backend ScreenRcaService.computeXxxProblem() after
  // segment is identified. Until backend Java changes are complete, segment_metrics will be null,
  // and VALUE/DELTA columns will show "—" (graceful degradation).
  const issues = problem.specific_issues ?? [];

  // Determine which metric rows to show based on problem type
  const showAffectedUsers = ["crashes", "anr"].includes(problem.problem_type);
  const showRate = true; // all problem types have rate
  const showLatency = ["network_latency", "screen_load_time", "screen_interactive"].includes(problem.problem_type);

  return (
    <Card padding="lg" radius="md" withBorder>
      <Stack gap="xs">
        <Group justify="space-between" align="flex-start" wrap="wrap">
          <Group gap="xs" align="center">
            <Badge size="sm" variant="filled" color="gray" circle>{problem.rank}</Badge>
            <Badge size="sm" variant="light" color={color}>{typeLabel}</Badge>
          </Group>
          {problem.most_affected_segment ? (
            <Text size="xs" c="dimmed">
              Most affected: <Text span fw={500} c="dark">{problem.most_affected_segment}</Text>
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
                  <th style={{ textAlign: 'right', padding: '8px', fontWeight: 600, fontSize: '12px', color: 'var(--mantine-color-gray-6)' }}>Value</th>
                  <th style={{ textAlign: 'right', padding: '8px', fontWeight: 600, fontSize: '12px', color: 'var(--mantine-color-gray-6)' }}>Baseline</th>
                  <th style={{ textAlign: 'right', padding: '8px', fontWeight: 600, fontSize: '12px', color: 'var(--mantine-color-gray-6)' }}>Delta</th>
                </tr>
              </thead>
              <tbody>
                {showAffectedUsers && (() => {
                  const delta = calcDelta(segmentMetrics?.affected_volume, metrics.affected_volume);
                  const deltaColor = delta === null
                    ? 'var(--mantine-color-gray-6)'
                    : delta < 0
                      ? 'var(--mantine-color-green-6)'
                      : 'var(--mantine-color-red-6)';
                  return (
                    <tr style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}>
                      <td style={{ padding: '8px', fontSize: '13px' }}>Affected users</td>
                      <td style={{ textAlign: 'right', padding: '8px', fontWeight: 500 }}>
                        {segmentMetrics?.affected_volume != null
                          ? segmentMetrics.affected_volume.toLocaleString()
                          : '—'}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: 'var(--mantine-color-gray-6)', fontSize: '13px' }}>
                        {metrics.affected_volume != null ? metrics.affected_volume.toLocaleString() : '—'}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: deltaColor }}>
                        {delta == null ? '—' : `${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`}
                      </td>
                    </tr>
                  );
                })()}
                {showRate && (() => {
                  const delta = calcDelta(parseRate(segmentMetrics?.rate), parseRate(metrics.rate));
                  const deltaColor = delta === null
                    ? 'var(--mantine-color-gray-6)'
                    : delta < 0
                      ? 'var(--mantine-color-green-6)'
                      : 'var(--mantine-color-red-6)';
                  return (
                    <tr style={{ borderBottom: '1px solid var(--mantine-color-gray-1)' }}>
                      <td style={{ padding: '8px', fontSize: '13px' }}>
                        <Group gap={4} align="center" wrap="nowrap">
                          <span>Rate</span>
                          {problem.metric_id && METRIC_INFO[problem.metric_id] && (
                            <Tooltip
                              label={METRIC_INFO[problem.metric_id]?.description}
                              position="top"
                              withArrow
                              arrowPosition="center"
                            >
                              <Text
                                size="xs"
                                c="dimmed"
                                style={{ cursor: 'help', textDecoration: 'underline dotted', whiteSpace: 'nowrap' }}
                              >
                                ({METRIC_INFO[problem.metric_id]?.label})
                              </Text>
                            </Tooltip>
                          )}
                        </Group>
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', fontWeight: 500 }}>
                        {segmentMetrics?.rate ?? '—'}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: 'var(--mantine-color-gray-6)', fontSize: '13px' }}>
                        {metrics.rate ?? '—'}
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px', color: deltaColor }}>
                        {delta == null ? '—' : `${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`}
                      </td>
                    </tr>
                  );
                })()}
                {showLatency && metrics.p50_ms != null && (() => {
                  const delta = calcDelta(segmentMetrics?.p50_ms, metrics.p50_ms);
                  const deltaColor = delta === null
                    ? 'var(--mantine-color-gray-6)'
                    : delta < 0
                      ? 'var(--mantine-color-green-6)'
                      : 'var(--mantine-color-red-6)';
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
                        {delta == null ? '—' : `${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`}
                      </td>
                    </tr>
                  );
                })()}
                {showLatency && metrics.p95_ms != null && (() => {
                  const delta = calcDelta(segmentMetrics?.p95_ms, metrics.p95_ms);
                  const deltaColor = delta === null
                    ? 'var(--mantine-color-gray-6)'
                    : delta < 0
                      ? 'var(--mantine-color-green-6)'
                      : 'var(--mantine-color-red-6)';
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
                        {delta == null ? '—' : `${delta > 0 ? '+' : ''}${delta.toFixed(1)}%`}
                      </td>
                    </tr>
                  );
                })()}
              </tbody>
            </table>
          </Box>
        ) : null}

        {issues.length > 0 && (
          <Box>
            <Group gap={4} align="center" wrap="nowrap" mb={4}>
              <Text size="xs" c="dimmed" tt="uppercase" fw={600}>Top issues</Text>
              {problem.metric_id && METRIC_INFO[problem.metric_id] && (
                <Tooltip
                  label={METRIC_INFO[problem.metric_id]?.description}
                  position="top"
                  withArrow
                  arrowPosition="center"
                >
                  <Text
                    size="xs"
                    c="dimmed"
                    style={{ cursor: 'help', textDecoration: 'underline dotted' }}
                  >
                    ({METRIC_INFO[problem.metric_id]?.label})
                  </Text>
                </Tooltip>
              )}
            </Group>
            <Box className={classes.topIssuesRow}>
              {issues.map((issue: ScreenRcaSpecificIssueV2, i: number) => (
                <TopIssueCard
                  key={issue.group_id ?? `issue-${i}`}
                  issue={issue}
                  projectId={projectId}
                  problemType={problem.problem_type}
                  segmentMetadata={problem.most_affected_segment}
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
    structured,
    isLoading,
    isError,
    errorMessage: errMsg,
    staleDetected,
  } = useGetScreenRcaV2Narrative({
    screenName,
    windowEndIso,
    windowStartIso,
    projectId,
    enabled: narrativeEnabled,
  });

  const problems = structured?.problems ?? [];
  const evidences = structured?.evidences ?? null;

  const regenerate = useRegenerateScreenRcaV2Narrative();
  const narrativeBusy = isLoading || regenerate.isPending;

  const executiveSummaryText = structured?.executive_summary?.trim() ?? "";
  const recommendationLines = (structured?.recommendations ?? []).filter(
    (l: string) => String(l).trim() !== "",
  );
  const hasExecutiveSummary = executiveSummaryText !== "";
  const hasRecommendations = recommendationLines.length > 0;

  const regenerateErrMsg =
    regenerate.error instanceof Error ? regenerate.error.message : null;

  const showNarrativeGenerationWait = narrativeEnabled && narrativeBusy && !isError;

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
    if (regenerateDebounceTimerRef.current !== null) {
      window.clearTimeout(regenerateDebounceTimerRef.current);
    }
    regenerateDebounceTimerRef.current = window.setTimeout(() => {
      regenerate.mutate({
        screenName: String(screenName).trim(),
        windowEndIso,
        windowStartIso,
        projectId: trimmedProjectId,
      });
      regenerateDebounceTimerRef.current = null;
    }, REGENERATE_DEBOUNCE_MS);
  }, [screenName, windowEndIso, windowStartIso, trimmedProjectId, regenerate]);

  useEffect(() => {
    return () => {
      if (regenerateDebounceTimerRef.current !== null) {
        window.clearTimeout(regenerateDebounceTimerRef.current);
      }
    };
  }, []);

  if (isLoading) {
    return (
      <Box className={interactionRcaClasses.container}>
        <div className={interactionRcaClasses.skeletonWrapper}>
          <Skeleton height={24} width={200} mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} mb="md" />
          <Skeleton height={120} />
        </div>
      </Box>
    );
  }

  if (isError) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Alert color="red" icon={<IconAlertCircle size={16} />} title="Failed to load">
          {errMsg ?? "An error occurred loading Screen RCA."}
        </Alert>
      </Box>
    );
  }

  if (!isLoading && problems.length === 0) {
    return (
      <Box className={interactionRcaClasses.container}>
        <Text className={interactionRcaClasses.stateMessage}>
          No issues detected for this screen in the selected period.
        </Text>
      </Box>
    );
  }

  const sessions = evidences?.sessions ?? [];
  const heatmapAvailable = evidences?.heatmap_available === true;
  const fromDate = fromDateFromStartInclusiveUtc(windowStartIso);
  const evidenceCount = sessions.length + (heatmapAvailable ? 1 : 0);
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
          {/* Header row with info popover + regenerate button */}
          <Group justify="flex-end" gap="xs">
            <MetricInfoPopover problems={problems} />
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

          {/* Stale report banner */}
          {staleDetected && !narrativeBusy && (
            <Alert color="blue" title={ROOT_CAUSE_MESSAGES.RCA_STALE_REPORT_BANNER}>
              <Group justify="space-between" align="center">
                <Text size="sm">A newer report is available.</Text>
                <Button size="xs" variant="light" onClick={() => window.location.reload()}>
                  {ROOT_CAUSE_MESSAGES.RCA_STALE_REFRESH}
                </Button>
              </Group>
            </Alert>
          )}

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
              <ProblemCard key={p.problem_type} problem={p} projectId={trimmedProjectId} />
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
                {sessions.map((sessionId: string, sessionIdx: number) => (
                  <Box
                    key={sessionId}
                    className={rcaClasses.evidenceCardSlot}
                  >
                    <RcaSessionReplayEvidenceCard
                      sessionId={sessionId}
                      segmentTitle={screenName}
                      projectId={trimmedProjectId}
                      evidenceOrdinal={sessionIdx + 1}
                      evidenceSessionCount={sessions.length}
                    />
                  </Box>
                ))}
                {heatmapAvailable && windowStartIso ? (
                  <RcaRelatedHeatmapCard
                    projectId={trimmedProjectId}
                    screenName={screenName}
                    segmentTitle={`${screenName} — ${fromDate}`}
                    heatmapFilters={buildScreenRcaHeatmapFilters(
                      undefined,
                      windowStartIso,
                      windowEndIso,
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
