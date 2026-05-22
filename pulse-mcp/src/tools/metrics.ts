import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { getClient } from "../client.js";
import { getTimeBucketSize } from "../timeBucket.js";

/** Matches pulse-ui interaction analytics filters (PulseOtelSemcov). */
const PULSE_TYPE_INTERACTION = "interaction";
const COL_PULSE_TYPE = "PulseType";
const COL_SPAN_NAME = "SpanName";
const COL_TIMESTAMP = "Timestamp";

const DISTRIBUTION_PATH = "/v1/interactions/performance-metric/distribution";

type JsonFilter = { field: string; operator: string; value: unknown[] };
type JsonSelect = {
  function: string;
  param?: Record<string, string>;
  alias: string;
};
type JsonOrderBy = { field: string; direction: string };

function toIsoRange(
  startTime: string,
  endTime: string,
): { start: string; end: string } {
  const start = new Date(startTime);
  const end = new Date(endTime);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    throw new Error(
      "startTime and endTime must be valid dates (ISO 8601 recommended); received unparseable value.",
    );
  }
  return { start: start.toISOString(), end: end.toISOString() };
}

function buildInteractionFilters(params: {
  interactionSpanName: string;
  appVersion?: string;
  platform?: string;
  osVersion?: string;
  networkProvider?: string;
  deviceModel?: string;
  state?: string;
}): JsonFilter[] {
  const filters: JsonFilter[] = [
    { field: COL_PULSE_TYPE, operator: "EQ", value: [PULSE_TYPE_INTERACTION] },
    {
      field: COL_SPAN_NAME,
      operator: "EQ",
      value: [params.interactionSpanName],
    },
  ];
  if (params.appVersion)
    filters.push({
      field: "AppVersion",
      operator: "EQ",
      value: [params.appVersion],
    });
  if (params.platform)
    filters.push({
      field: "Platform",
      operator: "EQ",
      value: [params.platform],
    });
  if (params.osVersion)
    filters.push({
      field: "OsVersion",
      operator: "EQ",
      value: [params.osVersion],
    });
  if (params.networkProvider)
    filters.push({
      field: "NetworkProvider",
      operator: "EQ",
      value: [params.networkProvider],
    });
  if (params.deviceModel)
    filters.push({
      field: "DeviceModel",
      operator: "EQ",
      value: [params.deviceModel],
    });
  if (params.state)
    filters.push({ field: "GeoState", operator: "EQ", value: [params.state] });
  return filters;
}

const MetricsParams = {
  projectId: z.string().describe("Project ID"),
  interactionId: z
    .string()
    .describe(
      "Critical interaction span name (matches pulse-ui interaction name / SpanName), not necessarily the numeric DB id",
    ),
  startTime: z.string().describe("Start time (ISO 8601)"),
  endTime: z.string().describe("End time (ISO 8601)"),
  appVersion: z.string().optional(),
  platform: z.string().optional().describe("Platform e.g. android, ios"),
  osVersion: z.string().optional(),
  networkProvider: z.string().optional(),
  deviceModel: z.string().optional(),
  state: z.string().optional(),
};

export function registerMetricsTools(server: McpServer): void {
  server.tool(
    "get_apdex_score",
    "APDEX time series for one interaction span (POST performance-metric distribution, TIME_BUCKET + APDEX)",
    MetricsParams,
    async ({ projectId, interactionId, startTime, endTime, ...dims }) => {
      const timeRange = toIsoRange(startTime, endTime);
      const bucket = getTimeBucketSize(timeRange.start, timeRange.end);
      const filters = buildInteractionFilters({
        interactionSpanName: interactionId,
        ...dims,
      });
      const select: JsonSelect[] = [
        {
          function: "TIME_BUCKET",
          param: { bucket, field: COL_TIMESTAMP },
          alias: "t1",
        },
        { function: "APDEX", alias: "apdex" },
      ];
      const body = {
        dataType: "TRACES",
        timeRange,
        select,
        filters,
        groupBy: ["t1"],
        orderBy: [{ field: "t1", direction: "ASC" } satisfies JsonOrderBy],
      };
      const data = await getClient().post<unknown>(
        DISTRIBUTION_PATH,
        body,
        projectId,
      );
      return {
        content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
      };
    },
  );

  server.tool(
    "get_error_rate",
    "Error rate time series (TIME_BUCKET + ERROR_RATE) for one interaction span",
    MetricsParams,
    async ({ projectId, interactionId, startTime, endTime, ...dims }) => {
      const timeRange = toIsoRange(startTime, endTime);
      const bucket = getTimeBucketSize(timeRange.start, timeRange.end);
      const filters = buildInteractionFilters({
        interactionSpanName: interactionId,
        ...dims,
      });
      const select: JsonSelect[] = [
        {
          function: "TIME_BUCKET",
          param: { bucket, field: COL_TIMESTAMP },
          alias: "t1",
        },
        { function: "ERROR_RATE", alias: "error_rate" },
      ];
      const body = {
        dataType: "TRACES",
        timeRange,
        select,
        filters,
        groupBy: ["t1"],
        orderBy: [{ field: "t1", direction: "ASC" } satisfies JsonOrderBy],
      };
      const data = await getClient().post<unknown>(
        DISTRIBUTION_PATH,
        body,
        projectId,
      );
      return {
        content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
      };
    },
  );

  server.tool(
    "get_interaction_time",
    "Duration percentiles P50 / P95 / P99 for the window (single aggregate row)",
    MetricsParams,
    async ({ projectId, interactionId, startTime, endTime, ...dims }) => {
      const timeRange = toIsoRange(startTime, endTime);
      const filters = buildInteractionFilters({
        interactionSpanName: interactionId,
        ...dims,
      });
      const select: JsonSelect[] = [
        { function: "DURATION_P50", alias: "p50" },
        { function: "DURATION_P95", alias: "p95" },
        { function: "DURATION_P99", alias: "p99" },
      ];
      const body = {
        dataType: "TRACES",
        timeRange,
        select,
        filters,
      };
      const data = await getClient().post<unknown>(
        DISTRIBUTION_PATH,
        body,
        projectId,
      );
      return {
        content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
      };
    },
  );

  server.tool(
    "get_interaction_categorization",
    "User category counts for the window — excellent / good / average / poor (single aggregate row)",
    MetricsParams,
    async ({ projectId, interactionId, startTime, endTime, ...dims }) => {
      const timeRange = toIsoRange(startTime, endTime);
      const filters = buildInteractionFilters({
        interactionSpanName: interactionId,
        ...dims,
      });
      const select: JsonSelect[] = [
        { function: "USER_CATEGORY_EXCELLENT", alias: "user_excellent" },
        { function: "USER_CATEGORY_GOOD", alias: "user_good" },
        { function: "USER_CATEGORY_AVERAGE", alias: "user_avg" },
        { function: "USER_CATEGORY_POOR", alias: "user_poor" },
      ];
      const body = {
        dataType: "TRACES",
        timeRange,
        select,
        filters,
      };
      const data = await getClient().post<unknown>(
        DISTRIBUTION_PATH,
        body,
        projectId,
      );
      return {
        content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
      };
    },
  );
}
