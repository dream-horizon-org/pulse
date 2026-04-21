import {
  Badge,
  Box,
  Button,
  Card,
  Group,
  Stack,
  Table,
  Text,
} from "@mantine/core";
import { IconRefresh, IconSparkles } from "@tabler/icons-react";
import type {
  RcaStructuredMetricRowV1,
  RcaStructuredReportV1,
} from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import { extractStructuredReport } from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import type { RcaReportViewProps } from "./RcaReportView.interface";
import { ROOT_CAUSE_MESSAGES } from "./RootCause.constants";
import { getMetricValueTone } from "./rcaMetricTone";
import rcaClasses from "./RcaReportView.module.css";
import rootCauseClasses from "./RootCause.module.css";
import { RcaRelatedHeatmapCard } from "./RcaRelatedHeatmapCard";
import { RcaSessionReplayEvidenceCard } from "./RcaSessionReplayEvidenceCard";

/** Max heatmap tiles per segment (evidence strip). */
const HEATMAP_EVIDENCE_MAX = 2;

const StructuredMetricRow = ({ row }: { row: RcaStructuredMetricRowV1 }) => {
  const tone = getMetricValueTone(
    row.metric_id,
    row.value_number,
    row.baseline_number,
    {
      valueDisplay: row.value_display,
      baselineDisplay: row.baseline_display,
      deltaDisplay: row.delta_display,
    },
  );
  const valueDeltaColor =
    tone === "good"
      ? ("teal.7" as const)
      : tone === "bad"
        ? ("red.7" as const)
        : undefined;

  return (
    <Table.Tr>
      <Table.Td className={rcaClasses.metricsColMetric}>
        <Text size="sm" w="100%" ta="start">
          {row.metric_label}
        </Text>
      </Table.Td>
      <Table.Td className={rcaClasses.metricsColNumeric}>
        <Text
          size="sm"
          w="100%"
          ta="end"
          fw={600}
          c={valueDeltaColor}
          className={
            tone === "neutral" ? rcaClasses.metricValueNeutral : undefined
          }
        >
          {row.value_display}
        </Text>
      </Table.Td>
      <Table.Td className={rcaClasses.metricsColNumeric}>
        <Text size="sm" w="100%" ta="end" c="dimmed">
          {row.baseline_display}
        </Text>
      </Table.Td>
      <Table.Td className={rcaClasses.metricsColNumericNarrow}>
        <Text
          size="sm"
          w="100%"
          ta="end"
          fw={600}
          c={valueDeltaColor}
          className={
            tone === "neutral" ? rcaClasses.metricValueNeutral : undefined
          }
        >
          {row.delta_display}
        </Text>
      </Table.Td>
    </Table.Tr>
  );
};

const RcaStructuredReportV1View = ({
  structured,
  cachedAt,
  relativeGeneratedAt,
  onRegenerate,
  projectId,
}: {
  structured: RcaStructuredReportV1;
  cachedAt?: string | null;
  relativeGeneratedAt?: string | null;
  onRegenerate?: () => void;
  projectId?: string | null;
}) => {
  const executiveSummaryText = structured.executive_summary?.trim() ?? "";
  const hasExecutiveSummary = executiveSummaryText !== "";
  const segments = structured.segments ?? [];
  const segmentCount = segments.length;
  const hasSegments = segmentCount > 0;
  const recommendations = (structured.recommendations ?? []).filter(
    (line) => String(line).trim() !== "",
  );
  const hasRecommendations = recommendations.length > 0;

  const hasRegenerate = typeof onRegenerate === "function";
  const relative =
    relativeGeneratedAt != null && String(relativeGeneratedAt).trim() !== "";
  const showAsOf = !relative && cachedAt != null && cachedAt !== "";
  const trimmedProjectId = projectId != null ? String(projectId).trim() : "";
  const hasProjectForHeatmaps = trimmedProjectId !== "";

  return (
    <Box className={rootCauseClasses.container}>
      <Box className={rcaClasses.reportShell}>
        {(showAsOf || relative || hasRegenerate) && (
          <Group
            className={rcaClasses.reportHeaderRow}
            justify="space-between"
            align="flex-start"
            wrap="wrap"
            gap="sm"
          >
            {relative ? (
              <Text className={rcaClasses.reportCachedAt} size="sm" c="dimmed">
                Generated {relativeGeneratedAt}
              </Text>
            ) : showAsOf ? (
              <Text className={rcaClasses.reportCachedAt} size="sm" c="dimmed">
                Report as of {cachedAt}
              </Text>
            ) : (
              <div />
            )}
            {hasRegenerate ? (
              <Button
                variant="light"
                size="xs"
                leftSection={<IconRefresh size={14} />}
                onClick={onRegenerate}
              >
                {ROOT_CAUSE_MESSAGES.REGENERATE_REPORT}
              </Button>
            ) : null}
          </Group>
        )}
        <Stack gap="lg">
          {hasExecutiveSummary && (
            <Card
              padding="lg"
              radius="md"
              withBorder
              className={rcaClasses.executiveSummaryCard}
            >
              <div className={rcaClasses.executiveSummaryTitleRow}>
                <IconSparkles size={18} color="var(--mantine-color-violet-6)" />
                <Text fw={700} size="sm" c="violet.7">
                  Executive summary
                </Text>
              </div>
              <Text
                className={rcaClasses.executiveSummaryBody}
                size="sm"
                lh={1.65}
              >
                {executiveSummaryText}
              </Text>
            </Card>
          )}

          {hasSegments && (
            <Box>
              <div className={rcaClasses.segmentsSectionTitleRow}>
                <Text fw={700} size="md" tt="uppercase" c="gray.7">
                  Top contributing segments
                </Text>
                <Badge size="sm" variant="light" color="gray">
                  {segmentCount}
                </Badge>
              </div>
              <Stack gap="md">
                {segments.map((segment, index) => {
                  const rank = segment.rank ?? index + 1;
                  const insightsText = segment.insights?.trim() ?? "";
                  const hasInsights = insightsText !== "";
                  const metrics = segment.metrics ?? [];
                  const sessionIds = (segment.affected_sessions ?? []).filter(
                    (id) => String(id).trim() !== "",
                  );
                  const heatmapScreensRaw = (
                    segment.related_heatmaps?.screens ?? []
                  )
                    .map((s) => String(s).trim())
                    .filter((s) => s !== "");
                  const heatmapScreens = heatmapScreensRaw.slice(
                    0,
                    HEATMAP_EVIDENCE_MAX,
                  );
                  const heatmapFilters =
                    segment.related_heatmaps?.heatmap_filters;
                  const heatmapCards =
                    hasProjectForHeatmaps && heatmapScreens.length > 0;
                  const evidenceCount =
                    sessionIds.length +
                    (heatmapCards ? heatmapScreens.length : 0);
                  const showEvidenceStrip = evidenceCount > 0;
                  return (
                    <Card
                      key={`rca-segment-${rank}-${segment.title}-${index}`}
                      withBorder
                      padding="lg"
                      radius="md"
                      className={rcaClasses.segmentCard}
                    >
                      <div className={rcaClasses.segmentHeader}>
                        <div className={rcaClasses.rankBadge}>{rank}</div>
                        <Text fw={600} size="md" style={{ flex: 1 }}>
                          {segment.title}
                        </Text>
                      </div>
                      {metrics.length > 0 ? (
                        <div className={rcaClasses.tableWrap}>
                          <Table.ScrollContainer minWidth={480}>
                            <Table
                              className={rcaClasses.metricsTable}
                              layout="fixed"
                              striped
                              highlightOnHover
                              withTableBorder
                              horizontalSpacing="sm"
                              verticalSpacing="xs"
                            >
                              <colgroup>
                                <col
                                  className={rcaClasses.metricsTableColMetric}
                                />
                                <col
                                  className={rcaClasses.metricsTableColNumeric}
                                />
                                <col
                                  className={rcaClasses.metricsTableColNumeric}
                                />
                                <col
                                  className={rcaClasses.metricsTableColDelta}
                                />
                              </colgroup>
                              <Table.Thead>
                                <Table.Tr>
                                  <Table.Th
                                    className={rcaClasses.metricsColMetric}
                                  >
                                    <span
                                      className={
                                        rcaClasses.metricsThLabelMetric
                                      }
                                    >
                                      Metric
                                    </span>
                                  </Table.Th>
                                  <Table.Th
                                    className={rcaClasses.metricsColNumeric}
                                  >
                                    <span
                                      className={
                                        rcaClasses.metricsThLabelNumeric
                                      }
                                    >
                                      Value
                                    </span>
                                  </Table.Th>
                                  <Table.Th
                                    className={rcaClasses.metricsColNumeric}
                                  >
                                    <span
                                      className={
                                        rcaClasses.metricsThLabelNumeric
                                      }
                                    >
                                      Baseline
                                    </span>
                                  </Table.Th>
                                  <Table.Th
                                    className={
                                      rcaClasses.metricsColNumericNarrow
                                    }
                                  >
                                    <span
                                      className={
                                        rcaClasses.metricsThLabelNumeric
                                      }
                                    >
                                      Delta
                                    </span>
                                  </Table.Th>
                                </Table.Tr>
                              </Table.Thead>
                              <Table.Tbody>
                                {metrics.map((row, rowIndex) => (
                                  <StructuredMetricRow
                                    key={`${row.metric_id}-${rowIndex}`}
                                    row={row}
                                  />
                                ))}
                              </Table.Tbody>
                            </Table>
                          </Table.ScrollContainer>
                        </div>
                      ) : null}
                      {hasInsights && (
                        <div className={rcaClasses.insightsCallout}>
                          <Text size="xs" fw={600} c="dimmed" mb={6}>
                            Insights
                          </Text>
                          <Text size="sm" lh={1.6}>
                            {insightsText}
                          </Text>
                        </div>
                      )}
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
                            {sessionIds.map((sessionId) => (
                              <Box
                                key={sessionId}
                                className={rcaClasses.evidenceCardSlot}
                              >
                                <RcaSessionReplayEvidenceCard
                                  sessionId={sessionId}
                                  segmentTitle={segment.title}
                                  projectId={trimmedProjectId || null}
                                />
                              </Box>
                            ))}
                            {heatmapCards
                              ? heatmapScreens.map((screen) => (
                                  <RcaRelatedHeatmapCard
                                    key={`heatmap-${rank}-${screen}`}
                                    projectId={trimmedProjectId}
                                    screenName={screen}
                                    segmentTitle={segment.title}
                                    heatmapFilters={heatmapFilters}
                                  />
                                ))
                              : null}
                          </Box>
                        </Box>
                      ) : null}
                    </Card>
                  );
                })}
              </Stack>
            </Box>
          )}

          {hasRecommendations && (
            <Card
              padding="lg"
              radius="md"
              withBorder
              className={rcaClasses.recommendationsCard}
            >
              <Text
                className={rcaClasses.recommendationsTitle}
                fw={700}
                size="sm"
                c="teal.8"
              >
                Recommendations
              </Text>
              <ul className={rcaClasses.recommendationsList}>
                {recommendations.map((item, i) => (
                  <li key={`rca-rec-${i}`}>
                    <Text size="sm" lh={1.65}>
                      {item}
                    </Text>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </Stack>
      </Box>
    </Box>
  );
};

export const RcaReportView = ({
  report,
  cachedAt,
  relativeGeneratedAt,
  onRegenerate,
  projectId,
}: RcaReportViewProps) => {
  const structured = extractStructuredReport(report);
  const executiveSummaryText = structured?.executive_summary?.trim() ?? "";
  const hasSegmentOrRec =
    (structured?.segments?.length ?? 0) > 0 ||
    (structured?.recommendations?.length ?? 0) > 0;
  const hasRenderableContent =
    structured != null && (executiveSummaryText !== "" || hasSegmentOrRec);

  if (!hasRenderableContent || structured == null) {
    return (
      <Box className={rootCauseClasses.container}>
        <Text className={rootCauseClasses.stateMessage}>
          No report content available.
        </Text>
      </Box>
    );
  }

  return (
    <RcaStructuredReportV1View
      structured={structured}
      cachedAt={cachedAt}
      relativeGeneratedAt={relativeGeneratedAt}
      onRegenerate={onRegenerate}
      projectId={projectId}
    />
  );
};
