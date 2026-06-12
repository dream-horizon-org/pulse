import {
  Box,
  Text,
  ActionIcon,
  Tooltip,
  Badge,
  Button,
  Group,
  Stack,
  ScrollArea,
  Collapse,
  List,
  ThemeIcon,
} from "@mantine/core";
import {
  IconPinFilled,
  IconPin,
  IconDatabase,
  IconClock,
  IconSparkles,
  IconTable,
  IconChartBar,
  IconChevronDown,
  IconAlertTriangle,
  IconAlertCircle,
  IconCircleCheck,
  IconInfoCircle,
  IconArrowUpRight,
  IconArrowDownRight,
} from "@tabler/icons-react";
import { useState, useMemo, useCallback } from "react";
import { ResponseDetailProps } from "./AiChat.interface";
import { QueryResults } from "../QueryResults";
import { ResponseChart } from "./ResponseChart";
import { AiLoadingAnimation } from "./AiLoadingAnimation";
import classes from "./AiChat.module.css";
import { KeyPoint, KeyPointSeverity } from "../../../../hooks/useAiQuery";

/* ────────────────── severity helpers ────────────────── */

const SEVERITY_ICON: Record<KeyPointSeverity, React.ReactNode> = {
  critical: <IconAlertTriangle size={14} />,
  warning: <IconAlertCircle size={14} />,
  healthy: <IconCircleCheck size={14} />,
  info: <IconInfoCircle size={14} />,
};

const SEVERITY_COLOR: Record<KeyPointSeverity, string> = {
  critical: "red",
  warning: "yellow",
  healthy: "green",
  info: "indigo",
};

const SEVERITY_CSS: Record<KeyPointSeverity, string> = {
  critical: classes.keyPointCritical,
  warning: classes.keyPointWarning,
  healthy: classes.keyPointHealthy,
  info: classes.keyPointInfo,
};

const PILL_CSS: Record<KeyPointSeverity, string> = {
  critical: classes.metricPillCritical,
  warning: classes.metricPillWarning,
  healthy: classes.metricPillHealthy,
  info: classes.metricPillInfo,
};

/* ────────────────── component ────────────────── */

export function ResponseDetail({
  message,
  pinnedFindings,
  onPinFinding,
  onUnpinFinding,
}: ResponseDetailProps) {
  const [showRawData, setShowRawData] = useState(false);

  // Auto-expand critical & warning findings by default
  const initialExpanded = useMemo(() => {
    if (!message?.insights?.keyPoints) return new Set<number>();
    const set = new Set<number>();
    message.insights.keyPoints.forEach((kp, i) => {
      if (kp.severity === "critical" || kp.severity === "warning") set.add(i);
    });
    return set;
  }, [message]);

  const [expandedSet, setExpandedSet] = useState<Set<number>>(initialExpanded);

  // Reset expanded set when the message changes
  const messageId = message?.id;
  const [prevMessageId, setPrevMessageId] = useState(messageId);
  if (messageId !== prevMessageId) {
    setPrevMessageId(messageId);
    setExpandedSet(initialExpanded);
    setShowRawData(false);
  }

  const toggleExpanded = useCallback((idx: number) => {
    setExpandedSet((prev) => {
      const next = new Set(prev);
      if (next.has(idx)) next.delete(idx);
      else next.add(idx);
      return next;
    });
  }, []);

  // Build a set of pinned key point texts for this message
  const pinnedTextsForMessage = useMemo(() => {
    if (!message) return new Set<string>();
    return new Set(
      pinnedFindings
        .filter((f) => f.sourceMessageId === message.id)
        .map((f) => f.text)
    );
  }, [pinnedFindings, message]);

  // Find pinned finding id by text (for unpin)
  const findPinnedId = (text: string): string | null => {
    const found = pinnedFindings.find(
      (f) => f.sourceMessageId === message?.id && f.text === text
    );
    return found?.id || null;
  };

  /* ── empty states ── */

  if (!message) {
    return (
      <Box className={classes.detailEmpty}>
        <IconSparkles
          size={40}
          color="var(--mantine-color-teal-4)"
          style={{ opacity: 0.4 }}
        />
        <Text size="sm" c="dimmed" ta="center" mt="md">
          Select a response to view details
        </Text>
      </Box>
    );
  }

  if (message.role === "user") {
    return (
      <Box className={classes.detailEmpty}>
        <Text size="sm" c="dimmed" ta="center">
          Select an AI response to view its insights
        </Text>
      </Box>
    );
  }

  if (message.isLoading) {
    return (
      <Box className={classes.detailEmpty}>
        <Box style={{ maxWidth: 320 }}>
          <AiLoadingAnimation />
        </Box>
      </Box>
    );
  }

  /* ── data ── */

  const { insights, sourcesAnalyzed, timeRange, result } = message;

  const formatDate = (isoString: string): string => {
    try {
      const date = new Date(isoString);
      return date.toLocaleString("en-US", {
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return isoString;
    }
  };

  // Collect metrics from key points for the summary strip
  const metricsForStrip = (insights?.keyPoints ?? [])
    .filter((kp) => kp.metric)
    .map((kp) => ({
      label: kp.metric!.label,
      value: kp.metric!.value,
      previousValue: kp.metric!.previousValue,
      severity: kp.severity,
    }));

  return (
    <ScrollArea className={classes.detailScroll} type="auto" offsetScrollbars>
      <Box className={classes.detailContent}>
        {/* Answer Card */}
        {insights?.answer && (
          <Box className={classes.answerCard}>
            <Text size="md" lh={1.6}>
              {insights.answer}
            </Text>
          </Box>
        )}

        {/* ── Summary Metric Strip ── */}
        {metricsForStrip.length > 0 && (
          <Box className={classes.summaryStrip}>
            {metricsForStrip.map((m, i) => (
              <Box
                key={i}
                className={`${classes.metricPill} ${PILL_CSS[m.severity] ?? ""}`}
              >
                <ThemeIcon
                  size={18}
                  radius="xl"
                  variant="light"
                  color={SEVERITY_COLOR[m.severity]}
                >
                  {SEVERITY_ICON[m.severity]}
                </ThemeIcon>
                <Text size="xs" c="dimmed" fw={500}>
                  {m.label}
                </Text>
                <Text size="sm" fw={700} c={SEVERITY_COLOR[m.severity]}>
                  {m.value}
                </Text>
                {m.previousValue && (
                  <Group gap={2} wrap="nowrap">
                    {m.severity === "healthy" ? (
                      <IconArrowDownRight size={12} color="var(--mantine-color-green-6)" />
                    ) : (
                      <IconArrowUpRight size={12} color="var(--mantine-color-red-5)" />
                    )}
                    <Text size="xs" c="dimmed">
                      {m.previousValue}
                    </Text>
                  </Group>
                )}
              </Box>
            ))}
          </Box>
        )}

        {/* ── Key Findings ── */}
        {insights?.keyPoints && insights.keyPoints.length > 0 && (
          <Box className={classes.keyPointsSection}>
            <Text size="xs" fw={600} c="dimmed" tt="uppercase" mb="sm">
              Key Findings
            </Text>
            <Stack gap={8}>
              {insights.keyPoints.map((point: KeyPoint, index: number) => {
                const isPinned = pinnedTextsForMessage.has(point.text);
                const pinnedId = isPinned ? findPinnedId(point.text) : null;
                const isExpanded = expandedSet.has(index);
                const severityClass = SEVERITY_CSS[point.severity] ?? "";
                const color = SEVERITY_COLOR[point.severity] ?? "gray";

                return (
                  <Box
                    key={index}
                    className={`${classes.keyPointItem} ${severityClass} ${isPinned ? classes.keyPointPinned : ""} ${isExpanded ? classes.keyPointExpanded : ""}`}
                  >
                    {/* Header row */}
                    <Group gap={8} wrap="nowrap" align="flex-start" w="100%">
                      {/* Severity icon */}
                      <ThemeIcon
                        size={22}
                        radius="xl"
                        variant="light"
                        color={color}
                        style={{ marginTop: 2, cursor: "pointer", flexShrink: 0 }}
                        onClick={() => toggleExpanded(index)}
                      >
                        {isExpanded ? (
                          <IconChevronDown size={13} />
                        ) : (
                          SEVERITY_ICON[point.severity]
                        )}
                      </ThemeIcon>

                      {/* Headline */}
                      <Text
                        size="sm"
                        className={classes.keyPointText}
                        onClick={() => toggleExpanded(index)}
                        style={{ cursor: "pointer" }}
                      >
                        {point.text}
                      </Text>

                      {/* Metric badge */}
                      {point.metric && (
                        <Badge
                          size="md"
                          variant="light"
                          color={color}
                          className={classes.metricBadge}
                          styles={{
                            root: { fontWeight: 700, textTransform: "none" },
                          }}
                        >
                          {point.metric.value}
                        </Badge>
                      )}

                      {/* Action icons */}
                      <Group gap={4} wrap="nowrap" style={{ flexShrink: 0 }}>
                        <Tooltip
                          label={
                            point.chartConfig
                              ? isExpanded
                                ? "Chart visible below"
                                : "Click to view chart"
                              : "Graph not available"
                          }
                        >
                          <ActionIcon
                            size="sm"
                            variant="subtle"
                            color={point.chartConfig ? "teal" : "gray"}
                            onClick={() => toggleExpanded(index)}
                            style={{ opacity: point.chartConfig ? 1 : 0.4 }}
                          >
                            <IconChartBar size={14} />
                          </ActionIcon>
                        </Tooltip>

                        <Tooltip label={isPinned ? "Unpin finding" : "Pin as research note"}>
                          <ActionIcon
                            size="sm"
                            variant={isPinned ? "light" : "subtle"}
                            color={isPinned ? "teal" : "gray"}
                            onClick={() => {
                              if (isPinned && pinnedId) {
                                onUnpinFinding(pinnedId);
                              } else {
                                onPinFinding(message.id, index);
                              }
                            }}
                            className={classes.pinButton}
                          >
                            {isPinned ? (
                              <IconPinFilled size={14} />
                            ) : (
                              <IconPin size={14} />
                            )}
                          </ActionIcon>
                        </Tooltip>
                      </Group>
                    </Group>

                    {/* Expanded content */}
                    <Collapse in={isExpanded}>
                      <Box className={classes.keyPointExpandedContent}>
                        {/* Detail explanation */}
                        {point.detail && (
                          <Text size="sm" className={classes.keyPointDetail}>
                            {point.detail}
                          </Text>
                        )}

                        {/* Evidence list */}
                        {point.evidence && point.evidence.length > 0 && (
                          <Box className={classes.keyPointEvidence}>
                            <Text size="xs" fw={600} c="dimmed" mb={4}>
                              Data Considered
                            </Text>
                            <List size="xs" spacing={2} className={classes.evidenceList}>
                              {point.evidence.map((item, eIdx) => (
                                <List.Item key={eIdx}>
                                  <Text size="xs" c="dimmed">
                                    {item}
                                  </Text>
                                </List.Item>
                              ))}
                            </List>
                          </Box>
                        )}

                        {/* Per-finding chart */}
                        <Box className={classes.findingChartInline}>
                          {point.chartConfig ? (
                            <ResponseChart config={point.chartConfig} />
                          ) : (
                            <Box className={classes.chartUnavailable}>
                              <IconChartBar
                                size={16}
                                color="var(--mantine-color-gray-4)"
                              />
                              <Text size="xs" c="dimmed">
                                Graph not available
                              </Text>
                            </Box>
                          )}
                        </Box>
                      </Box>
                    </Collapse>
                  </Box>
                );
              })}
            </Stack>
          </Box>
        )}

        {/* Sources Footer */}
        {(sourcesAnalyzed || timeRange) && (
          <Box className={classes.sourcesFooter}>
            {sourcesAnalyzed && sourcesAnalyzed.length > 0 && (
              <Group gap="xs" mb="xs">
                <IconDatabase size={14} color="var(--mantine-color-gray-5)" />
                <Text size="xs" c="dimmed">Sources:</Text>
                {sourcesAnalyzed.map((source, i) => (
                  <Badge key={i} size="xs" variant="light" color="teal">
                    {source}
                  </Badge>
                ))}
              </Group>
            )}
            {timeRange && (
              <Group gap="xs">
                <IconClock size={14} color="var(--mantine-color-gray-5)" />
                <Text size="xs" c="dimmed">
                  {formatDate(timeRange.start)} – {formatDate(timeRange.end)}
                </Text>
              </Group>
            )}
          </Box>
        )}

        {/* Raw Data Toggle */}
        {result && (
          <Box className={classes.rawDataSection}>
            <Button
              variant="light"
              color="gray"
              size="xs"
              leftSection={<IconTable size={14} />}
              onClick={() => setShowRawData(!showRawData)}
              fullWidth
            >
              {showRawData
                ? `Hide Raw Data (${result.rows.length} rows)`
                : `Show Raw Data (${result.rows.length} rows)`}
            </Button>
            {showRawData && (
              <Box mt="sm">
                <QueryResults
                  data={result}
                  visualization={{ chartType: "table" }}
                  isLoading={false}
                  isLoadingMore={false}
                  error={null}
                  errorCause={null}
                  isCancelled={false}
                  onRefresh={() => {}}
                  onLoadMore={() => {}}
                />
              </Box>
            )}
          </Box>
        )}
      </Box>
    </ScrollArea>
  );
}
