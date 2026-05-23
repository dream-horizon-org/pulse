import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { getTimeBucketSize } from "../timeBucket.js";
import {
  buildCommonFilters,
  buildExceptionListBody,
  DEFAULT_LIST_LIMIT,
  formatToolError,
  MAX_LIST_LIMIT,
  postDistribution,
  resolveTimeRange,
  runDistribution,
  type DistributionRequestBody,
  type ExceptionKind,
  type FilterField,
  type SelectField,
} from "./appVitalsHelpers.js";
import { COLUMN_NAME, PULSE_TYPE_SESSION_START } from "./appVitalsConstants.js";

const commonListArgs = {
  projectId: z.string().describe("Project ID"),
  startTime: z
    .string()
    .optional()
    .describe(
      "Range start (ISO or YYYY-MM-DD HH:mm:ss UTC); default last 7 days",
    ),
  endTime: z
    .string()
    .optional()
    .describe("Range end (ISO or YYYY-MM-DD HH:mm:ss UTC); default now"),
  appVersion: z.string().optional().describe('App version or "all"'),
  osVersion: z.string().optional().describe('OS version or "all"'),
  device: z.string().optional().describe('Device model or "all"'),
  platform: z.string().optional().describe('Platform or "all"'),
  networkProvider: z.string().optional().describe('Network provider or "all"'),
  state: z.string().optional().describe('Geo state or "all"'),
  screenName: z.string().optional().describe("Filter by screen name"),
  limit: z
    .number()
    .int()
    .min(1)
    .max(MAX_LIST_LIMIT)
    .default(DEFAULT_LIST_LIMIT)
    .describe(
      `Max issues to return (1–${MAX_LIST_LIMIT}; default ${DEFAULT_LIST_LIMIT}). No server offset — narrow time range to see more.`,
    ),
};

const commonDetailTime = {
  startTime: z.string().optional().describe("Range start; default last 7 days"),
  endTime: z.string().optional().describe("Range end; default now"),
};

function listTool(
  server: McpServer,
  name: string,
  description: string,
  kind: ExceptionKind,
): void {
  server.tool(name, description, commonListArgs, async (args) => {
    const body = buildExceptionListBody({
      kind,
      startTime: args.startTime ?? "",
      endTime: args.endTime ?? "",
      appVersion: args.appVersion,
      osVersion: args.osVersion,
      device: args.device,
      platform: args.platform,
      networkProvider: args.networkProvider,
      state: args.state,
      screenName: args.screenName,
      limit: args.limit,
    });
    return runDistribution(
      args.projectId,
      body,
      "No exception rows for this time range and filters.",
    );
  });
}

export function registerAppVitalsTools(server: McpServer): void {
  listTool(
    server,
    "list_app_vitals_crash_issues",
    "List crash issues (PulseType device.crash) for App Vitals: group_id, title, occurrences, affected_users, etc. Same query shape as pulse-ui useExceptionListData. Top N only (no offset on backend).",
    "crash",
  );
  listTool(
    server,
    "list_app_vitals_anr_issues",
    "List ANR issues (PulseType device.anr) for App Vitals. Same shape as list_app_vitals_crash_issues.",
    "anr",
  );
  listTool(
    server,
    "list_app_vitals_nonfatal_issues",
    "List non-fatal issues (PulseType non_fatal) for App Vitals.",
    "nonfatal",
  );

  server.tool(
    "get_app_vitals_user_session_totals",
    "Unique users and sessions from session.start logs (denominator for crash-free style metrics). Mirrors pulse-ui useGetAppStats.",
    {
      projectId: z.string().describe("Project ID"),
      ...commonDetailTime,
      appVersion: z.string().optional(),
      osVersion: z.string().optional(),
      device: z.string().optional(),
      platform: z.string().optional(),
      networkProvider: z.string().optional(),
      state: z.string().optional(),
    },
    async (args) => {
      const { start, end } = resolveTimeRange(args.startTime, args.endTime);
      const filters: FilterField[] = [
        {
          field: COLUMN_NAME.PULSE_TYPE,
          operator: "EQ",
          value: [PULSE_TYPE_SESSION_START],
        },
        ...buildCommonFilters(
          args.appVersion ?? "all",
          args.osVersion ?? "all",
          args.device ?? "all",
          args.platform ?? "all",
          args.networkProvider ?? "all",
          args.state ?? "all",
        ),
      ];
      const select: SelectField[] = [
        {
          function: "CUSTOM",
          param: {
            expression: `uniqCombined64(nullIf(${COLUMN_NAME.USER_ID}, ''))`,
          },
          alias: "unique_users",
        },
        {
          function: "CUSTOM",
          param: {
            expression: `uniqCombined64(nullIf(${COLUMN_NAME.SESSION_ID}, ''))`,
          },
          alias: "unique_sessions",
        },
      ];
      const body: DistributionRequestBody = {
        dataType: "LOGS",
        timeRange: { start, end },
        filters,
        select,
      };
      return runDistribution(
        args.projectId,
        body,
        "No session.start rows for this time range and filters.",
      );
    },
  );

  server.tool(
    "get_app_vitals_issue_summary",
    "Summary for one exception GroupId (occurrences, affected users, first/last seen, pulse type). Mirrors pulse-ui useIssueDetailData.",
    {
      projectId: z.string().describe("Project ID"),
      groupId: z.string().describe("Exception GroupId from list tools"),
      ...commonDetailTime,
    },
    async (args) => {
      const { start, end } = resolveTimeRange(args.startTime, args.endTime);
      const filters: FilterField[] = [
        { field: "GroupId", operator: "EQ", value: [args.groupId] },
      ];
      const select: SelectField[] = [
        { function: "COL", param: { field: "GroupId" }, alias: "group_id" },
        {
          function: "CUSTOM",
          param: { expression: "anyLast(PulseType)" },
          alias: "event_name",
        },
        {
          function: "CUSTOM",
          param: { expression: "anyLast(ExceptionMessage)" },
          alias: "error_message",
        },
        {
          function: "COL",
          param: { field: "ExceptionType" },
          alias: "error_type",
        },
        { function: "COL", param: { field: "Title" }, alias: "title" },
        {
          function: "CUSTOM",
          param: {
            expression: `arrayStringConcat(groupUniqArray(${COLUMN_NAME.APP_VERSION}), ', ')`,
          },
          alias: "app_versions",
        },
        {
          function: "CUSTOM",
          param: { expression: "count()" },
          alias: "occurrences",
        },
        {
          function: "CUSTOM",
          param: { expression: "min(Timestamp)" },
          alias: "first_seen",
        },
        {
          function: "CUSTOM",
          param: { expression: "max(Timestamp)" },
          alias: "last_seen",
        },
        {
          function: "CUSTOM",
          param: {
            expression: `uniqCombined64(nullIf(${COLUMN_NAME.USER_ID}, ''))`,
          },
          alias: "affected_users",
        },
      ];
      const body: DistributionRequestBody = {
        dataType: "EXCEPTIONS",
        timeRange: { start, end },
        filters,
        select,
        groupBy: ["group_id", "title", "error_type"],
        orderBy: [{ field: "occurrences", direction: "DESC" }],
        limit: 1,
      };
      return runDistribution(
        args.projectId,
        body,
        "No summary row for this GroupId and time range.",
      );
    },
  );

  server.tool(
    "get_app_vitals_issue_trend",
    "Time-bucketed counts for one GroupId. trendView: aggregated | appVersion | os. Mirrors pulse-ui useIssueTrendData.",
    {
      projectId: z.string(),
      groupId: z.string(),
      trendView: z
        .enum(["aggregated", "appVersion", "os"])
        .default("aggregated")
        .describe(
          "aggregated = counts per bucket only; appVersion/os = breakdown",
        ),
      ...commonDetailTime,
      appVersion: z.string().optional().describe('Telemetry filter or "all"'),
      osVersion: z.string().optional(),
      device: z.string().optional(),
    },
    async (args) => {
      const { start, end } = resolveTimeRange(args.startTime, args.endTime);
      const bucketSize = getTimeBucketSize(start, end);
      const filterArray: FilterField[] = [
        { field: "GroupId", operator: "EQ", value: [args.groupId] },
        ...buildCommonFilters(
          args.appVersion ?? "all",
          args.osVersion ?? "all",
          args.device ?? "all",
          "all",
          "all",
          "all",
        ),
      ];
      const baseSelect: SelectField[] = [
        {
          function: "TIME_BUCKET",
          param: { bucket: bucketSize, field: COLUMN_NAME.TIMESTAMP },
          alias: "t1",
        },
        {
          function: "CUSTOM",
          param: { expression: "count()" },
          alias: "count",
        },
      ];
      if (args.trendView === "appVersion") {
        baseSelect.push({
          function: "COL",
          param: { field: COLUMN_NAME.APP_VERSION },
          alias: "app_version",
        });
      } else if (args.trendView === "os") {
        baseSelect.push({
          function: "COL",
          param: { field: COLUMN_NAME.OS_VERSION },
          alias: "os_version",
        });
      }
      let groupBy: string[];
      if (args.trendView === "aggregated") {
        groupBy = ["t1"];
      } else if (args.trendView === "appVersion") {
        groupBy = ["t1", "app_version"];
      } else {
        groupBy = ["t1", "os_version"];
      }
      const body: DistributionRequestBody = {
        dataType: "EXCEPTIONS",
        timeRange: { start, end },
        filters: filterArray,
        select: baseSelect,
        groupBy,
        orderBy: [{ field: "t1", direction: "ASC" }],
      };
      try {
        const data = await postDistribution(args.projectId, body);
        const empty = !data.rows?.length;
        const text = JSON.stringify(
          {
            ok: true,
            empty,
            hint: empty
              ? "No trend buckets for this GroupId and time range."
              : undefined,
            bucketSize,
            trendView: args.trendView,
            ...data,
          },
          null,
          2,
        );
        return { content: [{ type: "text", text }] };
      } catch (e) {
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(
                { ok: false, error: formatToolError(e) },
                null,
                2,
              ),
            },
          ],
        };
      }
    },
  );

  server.tool(
    "get_app_vitals_issue_stack_traces",
    "Sample raw exception rows (stack traces, device, session) for a GroupId. Mirrors pulse-ui useIssueStackTraces.",
    {
      projectId: z.string(),
      groupId: z.string(),
      ...commonDetailTime,
      limit: z.number().int().min(1).max(50).default(10),
    },
    async (args) => {
      const { start, end } = resolveTimeRange(args.startTime, args.endTime);
      const filters: FilterField[] = [
        { field: "GroupId", operator: "EQ", value: [args.groupId] },
      ];
      const select: SelectField[] = [
        { function: "COL", param: { field: "TraceId" }, alias: "trace_id" },
        { function: "COL", param: { field: "SpanId" }, alias: "span_id" },
        { function: "COL", param: { field: "Timestamp" }, alias: "timestamp" },
        {
          function: "COL",
          param: { field: COLUMN_NAME.DEVICE_MODEL },
          alias: "device",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.OS_VERSION },
          alias: "os_version",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.APP_VERSION },
          alias: "app_version",
        },
        {
          function: "COL",
          param: { field: "ExceptionStackTrace" },
          alias: "stacktrace",
        },
        {
          function: "COL",
          param: { field: "ExceptionStackTraceRaw" },
          alias: "stacktrace_raw",
        },
        {
          function: "COL",
          param: { field: "ExceptionMessage" },
          alias: "error_message",
        },
        {
          function: "COL",
          param: { field: "ExceptionType" },
          alias: "error_type",
        },
        { function: "COL", param: { field: "Title" }, alias: "title" },
        {
          function: "COL",
          param: { field: "ScreenName" },
          alias: "screen_name",
        },
        { function: "COL", param: { field: "Platform" }, alias: "platform" },
        { function: "COL", param: { field: "SessionId" }, alias: "session_id" },
        {
          function: "COL",
          param: { field: "SdkVersion" },
          alias: "sdk_version",
        },
        {
          function: "COL",
          param: { field: "AppVersionCode" },
          alias: "app_version_code",
        },
        {
          function: "CUSTOM",
          param: { expression: "ResourceAttributes['network.carrier.name']" },
          alias: "network_provider",
        },
        { function: "COL", param: { field: "UserId" }, alias: "user_id" },
        {
          function: "CUSTOM",
          param: { expression: "arrayStringConcat(Interactions, ', ')" },
          alias: "interactions",
        },
        { function: "COL", param: { field: "BundleId" }, alias: "bundle_id" },
      ];
      const body: DistributionRequestBody = {
        dataType: "EXCEPTIONS",
        timeRange: { start, end },
        filters,
        select,
        orderBy: [{ field: "timestamp", direction: "DESC" }],
        limit: args.limit,
      };
      return runDistribution(
        args.projectId,
        body,
        "No stack trace rows for this GroupId and time range.",
      );
    },
  );

  server.tool(
    "get_app_vitals_issue_screen_breakdown",
    "Top screens by occurrence count for a GroupId. Mirrors pulse-ui useIssueScreenBreakdown.",
    {
      projectId: z.string(),
      groupId: z.string(),
      ...commonDetailTime,
    },
    async (args) => {
      const { start, end } = resolveTimeRange(args.startTime, args.endTime);
      const filters: FilterField[] = [
        { field: "GroupId", operator: "EQ", value: [args.groupId] },
      ];
      const select: SelectField[] = [
        {
          function: "COL",
          param: { field: "ScreenName" },
          alias: "screen_name",
        },
        {
          function: "CUSTOM",
          param: { expression: "count()" },
          alias: "occurrences",
        },
      ];
      const body: DistributionRequestBody = {
        dataType: "EXCEPTIONS",
        timeRange: { start, end },
        filters,
        select,
        groupBy: ["screen_name"],
        orderBy: [{ field: "occurrences", direction: "DESC" }],
        limit: 10,
      };
      return runDistribution(
        args.projectId,
        body,
        "No screen breakdown rows for this GroupId and time range.",
      );
    },
  );

  const MAX_GROUP_IDS = 50;

  server.tool(
    "get_app_vitals_exception_first_last_seen",
    `First/last Timestamp per GroupId over a fixed ~6-month window (mirrors pulse-ui useExceptionTimestamps). Max ${MAX_GROUP_IDS} group IDs per call.`,
    {
      projectId: z.string(),
      groupIds: z
        .array(z.string())
        .min(1)
        .max(MAX_GROUP_IDS)
        .describe(`GroupId values (max ${MAX_GROUP_IDS})`),
      eventName: z
        .enum(["device.crash", "device.anr"])
        .optional()
        .describe(
          "For crash/ANR enrichment pass device.crash or device.anr. Omit for non-fatal issues (PulseType non_fatal only).",
        ),
      appVersion: z.string().optional(),
      osVersion: z.string().optional(),
      device: z.string().optional(),
      screenName: z.string().optional(),
    },
    async (args) => {
      const now = new Date();
      const sixMonthsAgo = new Date(
        now.getTime() - 6 * 30 * 24 * 60 * 60 * 1000,
      );
      const timeRange = {
        start: sixMonthsAgo.toISOString(),
        end: now.toISOString(),
      };

      const filterArray: FilterField[] = [
        { field: "GroupId", operator: "IN", value: args.groupIds },
      ];
      if (
        args.eventName === "device.crash" ||
        args.eventName === "device.anr"
      ) {
        filterArray.push({
          field: "PulseType",
          operator: "EQ",
          value: [args.eventName],
        });
      } else {
        filterArray.push({
          field: "PulseType",
          operator: "EQ",
          value: ["non_fatal"],
        });
      }

      if (args.appVersion && args.appVersion !== "all") {
        filterArray.push({
          field: COLUMN_NAME.APP_VERSION,
          operator: "EQ",
          value: [args.appVersion],
        });
      }
      if (args.osVersion && args.osVersion !== "all") {
        filterArray.push({
          field: COLUMN_NAME.OS_VERSION,
          operator: "EQ",
          value: [args.osVersion],
        });
      }
      if (args.device && args.device !== "all") {
        filterArray.push({
          field: COLUMN_NAME.DEVICE_MODEL,
          operator: "EQ",
          value: [args.device],
        });
      }
      if (args.screenName) {
        filterArray.push({
          field: "ScreenName",
          operator: "EQ",
          value: [args.screenName],
        });
      }

      const select: SelectField[] = [
        { function: "COL", param: { field: "GroupId" }, alias: "group_id" },
        {
          function: "CUSTOM",
          param: { expression: "min(Timestamp)" },
          alias: "first_seen",
        },
        {
          function: "CUSTOM",
          param: { expression: "max(Timestamp)" },
          alias: "last_seen",
        },
      ];

      const body: DistributionRequestBody = {
        dataType: "EXCEPTIONS",
        timeRange,
        filters: filterArray,
        select,
        groupBy: ["group_id"],
      };

      return runDistribution(
        args.projectId,
        body,
        "No timestamp rows for these GroupIds in the 6-month window.",
      );
    },
  );
}
