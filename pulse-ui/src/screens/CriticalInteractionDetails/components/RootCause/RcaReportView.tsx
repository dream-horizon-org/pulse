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
import type { RcaReportViewProps } from "./RcaReportView.interface";
import { ROOT_CAUSE_MESSAGES } from "./RootCause.constants";
import { getMetricValueTone } from "./rcaMetricTone";
import rcaClasses from "./RcaReportView.module.css";
import rootCauseClasses from "./RootCause.module.css";

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
  onRegenerate,
}: {
  structured: RcaStructuredReportV1;
  cachedAt?: string | null;
  onRegenerate?: () => void;
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
  const showAsOf = cachedAt != null && cachedAt !== "";

  return (
    <Box className={rootCauseClasses.container}>
      <Box className={rcaClasses.reportShell}>
        {(showAsOf || hasRegenerate) && (
          <Group
            className={rcaClasses.reportHeaderRow}
            justify="space-between"
            align="flex-start"
            wrap="wrap"
            gap="sm"
          >
            {showAsOf ? (
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
                  const impactText = segment.impact?.trim() ?? "";
                  const hasImpact = impactText !== "";
                  const insightsText = segment.insights?.trim() ?? "";
                  const hasInsights = insightsText !== "";
                  const metrics = segment.metrics ?? [];
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
                      {hasImpact && (
                        <div className={rcaClasses.impactCallout}>
                          <Text size="xs" fw={600} c="dimmed" mb={6}>
                            Impact
                          </Text>
                          <Text size="sm" lh={1.6}>
                            {impactText}
                          </Text>
                        </div>
                      )}
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
                      {segment.affected_sessions &&
                        segment.affected_sessions.length > 0 && (
                          <Box
                            mt="md"
                            pt="md"
                            style={{
                              borderTop:
                                "1px solid var(--mantine-color-gray-2)",
                            }}
                          >
                            <Text size="xs" fw={600} c="dimmed" mb={6}>
                              Affected Sessions
                            </Text>
                            <Group gap="xs" wrap="wrap">
                              {segment.affected_sessions.map((sessionId) => (
                                <Button
                                  key={sessionId}
                                  variant="light"
                                  size="xs"
                                  onClick={() => {
                                    // Navigate to session replay
                                    window.open(
                                      `/sessions/${sessionId}/replay`,
                                      "_blank",
                                    );
                                  }}
                                >
                                  {sessionId}
                                </Button>
                              ))}
                            </Group>
                          </Box>
                        )}
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
  onRegenerate,
}: RcaReportViewProps) => {
  const structured = report.structured;
  const isValidStructured = structured != null && structured.version === 1;
  const executiveSummaryText = structured?.executive_summary?.trim() ?? "";
  const hasSegmentOrRec =
    (structured?.segments?.length ?? 0) > 0 ||
    (structured?.recommendations?.length ?? 0) > 0;
  const hasRenderableContent =
    isValidStructured && (executiveSummaryText !== "" || hasSegmentOrRec);

  if (!hasRenderableContent || structured == null || structured.version !== 1) {
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
      onRegenerate={onRegenerate}
    />
  );
};
