import { Box, Card, Stack, Text } from "@mantine/core";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { AiChartCard } from "../../../AiChat/components/AiChartCard";
import { AiTableCard } from "../../../AiChat/components/AiTableCard";
import type { AiChartConfig, AiTableConfig } from "../../../AiChat/types/chat";
import type {
  RcaReportChartBlock,
  RcaReportTableBlock,
} from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import type { RcaReportViewProps } from "./RcaReportView.interface";
import classes from "./RootCause.module.css";

const CHART_TYPES = ["line", "bar", "pie", "area"] as const;
type ChartType = (typeof CHART_TYPES)[number];

const isChartType = (v: unknown): v is ChartType =>
  typeof v === "string" && CHART_TYPES.includes(v as ChartType);

function reportChartToAiConfig(block: RcaReportChartBlock): AiChartConfig {
  const data = block.data ?? {};
  const typeFromData = (data as { type?: string }).type;
  const chartType = isChartType(typeFromData) ? typeFromData : "bar";
  return {
    type: chartType,
    title: block.title,
    data,
    description: block.description ?? undefined,
  };
}

function reportTableToAiConfig(block: RcaReportTableBlock): AiTableConfig {
  const columns = block.columns ?? [];
  const rows = block.rows ?? [];
  const hasKeyLabel =
    columns.length > 0 &&
    "key" in (columns[0] ?? {}) &&
    "label" in (columns[0] ?? {});

  const aiColumns = hasKeyLabel
    ? (columns as Array<{
        key: string;
        label: string;
        type?: "string" | "number";
      }>)
    : rows.length > 0
      ? Object.keys(rows[0] as Record<string, unknown>).map((key) => ({
          key,
          label: key,
          type: "string" as const,
        }))
      : [];

  return {
    title: block.title,
    columns: aiColumns,
    rows: rows as Record<string, unknown>[],
    description: block.description ?? undefined,
  };
}

export function RcaReportView({
  report,
  rcaInsights,
  cachedAt,
}: RcaReportViewProps) {
  const hasMarkdown = report.markdown != null && report.markdown.trim() !== "";
  const hasCharts = report.charts != null && report.charts.length > 0;
  const hasTables = report.tables != null && report.tables.length > 0;
  const hasContent =
    hasMarkdown ||
    hasCharts ||
    hasTables ||
    (rcaInsights != null && rcaInsights.trim() !== "");

  if (!hasContent) {
    return (
      <Box className={classes.container}>
        <Text className={classes.stateMessage}>
          No report content available.
        </Text>
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      {cachedAt != null && cachedAt !== "" && (
        <Text className={classes.cachedAt} size="sm" c="dimmed">
          Report as of {cachedAt}
        </Text>
      )}
      <Stack gap="lg">
        {rcaInsights != null && rcaInsights.trim() !== "" && (
          <Card withBorder padding="md" className={classes.segmentCard}>
            <Text size="sm" fw={600} mb="xs" c="gray.7">
              Insights
            </Text>
            <div className={classes.markdownBlock}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {rcaInsights}
              </ReactMarkdown>
            </div>
          </Card>
        )}
        {hasMarkdown && (
          <Card withBorder padding="md" className={classes.segmentCard}>
            <div className={classes.markdownBlock}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {report.markdown ?? ""}
              </ReactMarkdown>
            </div>
          </Card>
        )}
        {hasCharts &&
          report.charts.map((chart, index) => (
            <AiChartCard
              key={`rca-chart-${chart.title}-${index}`}
              chart={reportChartToAiConfig(chart)}
            />
          ))}
        {hasTables &&
          report.tables.map((table, index) => (
            <AiTableCard
              key={`rca-table-${table.title}-${index}`}
              table={reportTableToAiConfig(table)}
            />
          ))}
      </Stack>
    </Box>
  );
}
